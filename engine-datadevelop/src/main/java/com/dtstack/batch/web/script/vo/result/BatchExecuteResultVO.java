/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.batch.web.script.vo.result;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel("运行sql脚本返回信息")
public class BatchExecuteResultVO<T> {

    @ApiModelProperty(value = "任务实例Id", example = "1")
    private String jobId;

    @ApiModelProperty(value = "sql 文本", example = "select * from test")
    private String sqlText;

    @ApiModelProperty(value = "实例信息", example = "msg...")
    private String msg;

    @ApiModelProperty(value = "状态", example = "1")
    private Integer status;

    @ApiModelProperty(value = "结果列表")
    private List<T> result;

    @ApiModelProperty(value = "是否继续", example = "false")
    private Boolean isContinue = false;

    @ApiModelProperty(value = "下载地址", example = "/api")
    private String download;

    @ApiModelProperty(value = "引擎类型", example = "1")
    private Integer engineType;
}