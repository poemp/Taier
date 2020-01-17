package com.dtstack.task.server.node;

import com.alibaba.fastjson.JSONObject;
import com.dtstack.dtcenter.common.constant.TaskStatusConstrant;
import com.dtstack.dtcenter.common.engine.EngineSend;
import com.dtstack.dtcenter.common.enums.TaskStatus;
import com.dtstack.sql.Twins;
import com.dtstack.engine.common.constrant.JobFieldInfo;
import com.dtstack.engine.common.enums.EScheduleType;
import com.dtstack.engine.common.env.EnvironmentContext;
import com.dtstack.engine.dao.BatchJobDao;
import com.dtstack.engine.domain.po.SimpleBatchJobPO;
import com.dtstack.task.server.executor.AbstractJobExecutor;
import com.dtstack.task.server.executor.CronJobExecutor;
import com.dtstack.task.server.executor.FillJobExecutor;
import com.dtstack.task.server.queue.QueueInfo;
import com.dtstack.task.server.scheduler.JobRichOperator;
import com.dtstack.task.server.zk.ZkService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * company: www.dtstack.com
 * author: toutian
 * create: 2019/10/22
 */
@Component
public class WorkNode implements InitializingBean, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(WorkNode.class);

    private static final AtomicBoolean INIT = new AtomicBoolean(true);

    /**
     * 已经提交到的job的status
     */
    private static final List<Integer> SUBMIT_ENGINE_STATUSES = new ArrayList<>();

    static {
        SUBMIT_ENGINE_STATUSES.addAll(TaskStatusConstrant.RUNNING_STATUS);
        SUBMIT_ENGINE_STATUSES.addAll(TaskStatusConstrant.WAIT_STATUS);
        SUBMIT_ENGINE_STATUSES.add(TaskStatus.SUBMITTING.getStatus());
    }

    @Autowired
    private EnvironmentContext environmentContext;

    @Autowired
    private ZkService zkService;

    @Autowired
    private BatchJobDao batchJobDao;

    @Autowired
    private CronJobExecutor cronJobExecutor;

    @Autowired
    private FillJobExecutor fillJobExecutor;

    @Autowired
    private JobRichOperator jobRichOperator;

    @Autowired
    private EngineSend engineSend;

    private List<AbstractJobExecutor> executors = new ArrayList<>(EScheduleType.values().length);

    private ExecutorService executorService;

    private ScheduledExecutorService scheduledService;


    @Override
    public void afterPropertiesSet() throws Exception {
        executors.add(fillJobExecutor);
        executors.add(cronJobExecutor);

        executorService = new ThreadPoolExecutor(executors.size(), executors.size(), 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), CustomThreadFactory("ExecutorDealer"));
        for (AbstractJobExecutor executor : executors) {
            executorService.submit(executor);
        }

        scheduledService = new ScheduledThreadPoolExecutor(1, new Cust("JobStatusDealer"));
        scheduledService.scheduleWithFixedDelay(
                new JobStatusDealer(),
                0,
                environmentContext.getJobStatusDealerInterval(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 获取当前节点的 type类型下的 job实例信息
     */
    public Map<Integer, QueueInfo> getNodeQueueInfo() {
        String localAddress = zkService.getLocalAddress();
        Twins<String, String> cycTime = jobRichOperator.getCycTimeLimit();
        Map<Integer, QueueInfo> nodeQueueInfo = Maps.newHashMap();
        executors.forEach(executor -> nodeQueueInfo.computeIfAbsent(executor.getScheduleType(), k -> {
            int queueSize = batchJobDao.countTasksByCycTimeTypeAndAddress(localAddress, executor.getScheduleType(), cycTime.getKey(), cycTime.getType());
            QueueInfo queueInfo = new QueueInfo();
            queueInfo.setSize(queueSize);
            return queueInfo;
        }));
        return nodeQueueInfo;
    }

    @Override
    public void destroy() throws Exception {
        for (AbstractJobExecutor executor : executors) {
            executor.stop();
        }

        executorService.shutdownNow();
    }

    public void recoverOtherNode() {
        for (AbstractJobExecutor executor : executors) {
            executor.recoverOtherNode();
        }
    }

    class JobStatusDealer implements Runnable {

        private long lastSyncTime = 0;

        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        @Override
        public void run() {
            if (StringUtils.isBlank(zkService.getLocalAddress())) {
                return;
            }

            LOG.info("-----start JobStatusDealer----");
            long syncStartTime = System.currentTimeMillis();
            try {
                long syncJobCount = syncStatus();
                long syncTime = System.currentTimeMillis() - syncStartTime;
                String lastSyncTimeStr = null;
                if (lastSyncTime != 0) {
                    lastSyncTimeStr = sdf.format(new Date(lastSyncTime));
                }
                lastSyncTime = syncStartTime;
                LOG.info("-----end JobStatusDealer, syncJobCount:{} syncStatusTimeUsed（ms）:{} lastSyncTime: {} -----", syncJobCount, syncTime, lastSyncTimeStr);
            } catch (Exception e) {
                LOG.error("----syncStatus happens error:{}", e);
            }
        }

        private Long syncStatus() {
            if (INIT.compareAndSet(true, false)) {
                return syncAllStatus();
            }
            return syncBulkStatus();
        }


        private Long syncAllStatus() {
            long jobCount = 0L;
            try {
                long startId = 0L;
                while (true) {
                    List<SimpleBatchJobPO> jobs = batchJobDao.listSimpleJobByStatusAddress(startId, SUBMIT_ENGINE_STATUSES, zkService.getLocalAddress());
                    if (CollectionUtils.isEmpty(jobs)) {
                        break;
                    }
                    List<String> jobIds = Lists.newArrayList();
                    for (SimpleBatchJobPO batchJob : jobs) {
                        jobIds.add(batchJob.getJobId());
                        startId = batchJob.getId();
                    }
                    JSONObject jobIdsJson = new JSONObject();
                    jobIdsJson.put("jobIds", jobIds);
                    List<Map<String, Object>> jobStatusInfos = engineSend.listJobStatusByJobIds(jobIdsJson.toJSONString(), null, 3);
                    batchUpdateJobStatusInfo(jobStatusInfos);

                    jobCount += jobs.size();
                }
            } catch (Exception e) {
                INIT.compareAndSet(true, false);
                LOG.error("----nodeAddress:{} syncAllStatus error:{}", zkService.getLocalAddress(), e);
                throw e;
            }
            return jobCount;
        }

        private Long syncBulkStatus() {
            long jobCount = 0L;
            try {
                JSONObject timeJson = new JSONObject();
                //多同步10分钟数据，以免时钟不一致
                lastSyncTime -= 600000;
                timeJson.put("time", lastSyncTime);
                List<Map<String, Object>> jobStatusInfos = engineSend.listJobStatus(timeJson.toJSONString(), null, 3);
                batchUpdateJobStatusInfo(jobStatusInfos);
                jobCount = jobStatusInfos.size();
            } catch (Exception e) {
                LOG.error("----nodeAddress:{} syncBulkStatus error:{}", zkService.getLocalAddress(), e);
                throw e;
            }
            return jobCount;
        }

        private void batchUpdateJobStatusInfo(List<Map<String, Object>> jobStatusInfos) {
            if (CollectionUtils.isEmpty(jobStatusInfos)) {
                return;
            }
            for (Map<String, Object> jobStatusInfo : jobStatusInfos) {
                String jobId = MapUtils.getString(jobStatusInfo, JobFieldInfo.JOB_ID);
                Integer status = MapUtils.getInteger(jobStatusInfo, JobFieldInfo.STATUS);
                Timestamp execStartTimestamp = null;
                Timestamp execEndTimestamp = null;
                Long execStartTime = MapUtils.getLong(jobStatusInfo, JobFieldInfo.EXEC_START_TIME);
                Long execEndTime = MapUtils.getLong(jobStatusInfo, JobFieldInfo.EXEC_END_TIME);
                if (execStartTime != null && execStartTime != 0) {
                    execStartTimestamp = new Timestamp(execStartTime);
                }
                if (execEndTime != null && execEndTime != 0) {
                    execEndTimestamp = new Timestamp(execEndTime);
                }
                Long execTime = MapUtils.getLong(jobStatusInfo, JobFieldInfo.EXEC_TIME);
                Integer retryNum = MapUtils.getInteger(jobStatusInfo, JobFieldInfo.RETRY_NUM);

                batchJobDao.updateJobInfoByJobId(jobId, status, execStartTimestamp, execEndTimestamp, execTime, retryNum);
            }
        }
    }

}