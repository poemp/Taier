package com.dtstack.lineage.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dtstack.engine.api.domain.Component;
import com.dtstack.engine.api.domain.LineageDataSetInfo;
import com.dtstack.engine.api.domain.LineageDataSource;
import com.dtstack.engine.api.domain.Tenant;
import com.dtstack.engine.api.enums.EComponentApiType;
import com.dtstack.engine.api.pojo.lineage.Column;
import com.dtstack.engine.api.pojo.lineage.Table;
import com.dtstack.engine.api.service.ComponentService;
import com.dtstack.engine.common.client.ClientCache;
import com.dtstack.engine.common.client.IClient;
import com.dtstack.engine.common.enums.EComponentType;
import com.dtstack.engine.common.exception.ClientAccessException;
import com.dtstack.engine.common.exception.RdosDefineException;
import com.dtstack.engine.common.util.PublicUtil;
import com.dtstack.engine.dao.ComponentDao;
import com.dtstack.engine.dao.TenantDao;
import com.dtstack.lineage.dao.LineageDataSetDao;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @Author tengzhen
 * @Description:
 * @Date: Created in 4:18 下午 2020/10/30
 */
@Service
public class LineageDataSetInfoService {


    @Autowired
    private LineageDataSourceService sourceService;

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private LineageDataSetDao lineageDataSetDao;


    @Autowired
    private ComponentDao componentDao;

    /**
     * @author zyd
     * @Description 根据条件查询表信息，如果没有则新增
     * @Date 2020/10/30 4:20 下午
     * @param sourceId:
     * @param dbName:
     * @param tableName:
     * @param schemaName:
     * @return: com.dtstack.lineage.impl.LineageTableInfoService
     **/
    public LineageDataSetInfo getOneBySourceIdAndDbNameAndTableName(Long sourceId, String dbName, String tableName, String schemaName){

        LineageDataSetInfo lineageDataSetInfo = lineageDataSetDao.getOneBySourceIdAndDbNameAndTableName(sourceId,dbName,tableName,schemaName);
        if(null != lineageDataSetInfo){
            return lineageDataSetInfo;
        }
        //如果没有查到，则新增表信息
        //根据sourceId查询数据源信息
        LineageDataSource dataSource = sourceService.getDataSourceById(sourceId);
        if(null == dataSource){
            throw new RdosDefineException("该数据源不存在");
        }
        lineageDataSetInfo = generateDataSet(sourceId, tableName, schemaName, dataSource, dbName);
        lineageDataSetDao.insertTableInfo(lineageDataSetInfo);
        return lineageDataSetInfo;
    }

    private LineageDataSetInfo generateDataSet(Long sourceId, String tableName, String schemaName, LineageDataSource dataSource, String dbName) {
        LineageDataSetInfo dataSetInfo = new LineageDataSetInfo();
        BeanUtils.copyProperties(dataSource,dataSetInfo);
        dataSetInfo.setSourceId(sourceId);
        dataSetInfo.setDbName(dbName);
        dataSetInfo.setIsManual(0);
        if(StringUtils.isNotEmpty(schemaName)){
            dataSetInfo.setSchemaName(schemaName);
        }else {
            dataSetInfo.setSchemaName(dbName);
        }
        dataSetInfo.setSetType(0);
        dataSetInfo.setTableName(tableName);
        //生成tableKey
        String tableKey = generateTableKey(dataSource.getRealSourceId(), dbName, tableName);
        dataSetInfo.setTableKey(tableKey);
        return dataSetInfo;
    }

    private String generateTableKey(Long sourceId, String dbName, String tableName) {

        return sourceId+dbName+tableName;
    }

    public List<Column> getTableColumns(LineageDataSetInfo dataSetInfo){

        //获取数据源信息
        LineageDataSource dataSource = sourceService.getDataSourceById(dataSetInfo.getSourceId());
        if(null == dataSource){
            throw new RdosDefineException("找不到对应的数据源");
        }
        ClientCache clientCache = ClientCache.getInstance();
        IClient iClient ;
        try {
            //TODO kebers配置文件路径放入到pluginInfo中
            String kerberosConf = dataSource.getKerberosConf();
            String dataJson = dataSource.getDataJson();
            JSONObject jsonObject = JSON.parseObject(dataJson);
            JSONObject kerberosJsonObj = new JSONObject();
            if(!"-1".equals(kerberosConf)) {
                kerberosJsonObj = JSON.parseObject(kerberosConf);
            }
            //获取sftp配置
            Long dtUicTenantId = dataSource.getDtUicTenantId();
            Long tenantId = tenantDao.getIdByDtUicTenantId(dtUicTenantId);
            Integer componentId = componentDao.getIdByTenantIdComponentType(tenantId, EComponentType.SFTP.getTypeCode());
            if(null == componentId){
                throw new RdosDefineException("该租户没有绑定集群");
            }
            Component component = componentDao.getOne((long) componentId);
            String componentConfig = component.getComponentConfig();
            if(null == componentConfig){
                throw new RdosDefineException("sftp配置信息为空");
            }
            JSONObject componentJsonObj = JSONObject.parseObject(componentConfig);
            if(dataSource.getOpenKerberos()==1) {
                //开启kerberos
                jsonObject.put("sftpConf", componentJsonObj);
                jsonObject.put("config", kerberosJsonObj);
            }
            String pluginInfo = PublicUtil.objToString(jsonObject);
            iClient = getClient(dataSource, clientCache, pluginInfo);
            return getAllColumns(dataSetInfo, iClient);
        } catch (Exception e) {
            throw new RdosDefineException("获取client异常",e);
        }
    }

    public List<Column> getAllColumns(LineageDataSetInfo dataSetInfo, IClient iClient) {

        if(null == dataSetInfo){
            return new ArrayList<>();
        }
        return iClient.getAllColumns(dataSetInfo.getTableName(), dataSetInfo.getSchemaName(), dataSetInfo.getDbName());
    }

    public IClient getClient(LineageDataSource dataSource, ClientCache clientCache, String pluginInfo) throws ClientAccessException {
        if(null == clientCache || null == dataSource){
            return null;
        }
        return clientCache.getClient(EComponentType.getByCode(dataSource.getSourceType()).getName(), pluginInfo);
    }

    /**
     * @author zyd
     * @Description 根据id查询表信息
     * @Date 2020/11/11 5:11 下午
     * @param id:
     * @return: com.dtstack.engine.api.domain.LineageDataSetInfo
     **/
    public LineageDataSetInfo getOneById(Long id){

        return lineageDataSetDao.getOneById(id);
    }

    /**
     * @author zyd
     * @Description 根据ids批量查询表信息
     * @Date 2020/11/11 5:14 下午
     * @param ids:
     * @return: com.dtstack.engine.api.domain.LineageDataSetInfo
     **/
    public List<LineageDataSetInfo> getDataSetListByIds(List<Long> ids){

        if(CollectionUtils.isEmpty(ids)){
            throw new RdosDefineException("表id列表不能为空");
        }
        return lineageDataSetDao.getDataSetListByIds(ids);
    }


    /**
     * @author zyd
     * @Description 根据数据源id和table列表查询字段信息
     * @Date 2020/11/13 10:57 上午
     * @param sourceId:
     * @param tables:
     * @return: java.util.Map<java.lang.String,java.util.List<com.dtstack.engine.api.pojo.lineage.Column>>
     **/
    public Map<String,List<Column>> getColumnsBySourceIdAndListTable(Long sourceId, List<Table> tables){

        HashMap<String, List<Column>> listHashMap = new HashMap<>(16);
        if(CollectionUtils.isEmpty(tables)){
            return listHashMap;
        }
        for (Table table : tables) {
            LineageDataSetInfo dataSetInfo = new LineageDataSetInfo();
            dataSetInfo.setDbName(table.getName());
            dataSetInfo.setSchemaName(table.getSchemaName());
            dataSetInfo.setDbName(table.getDb());
            dataSetInfo.setSourceId(sourceId);
            List<Column> tableColumns = getTableColumns(dataSetInfo);
            listHashMap.put(table.getName(),tableColumns);
        }
        return listHashMap;
    }

    }