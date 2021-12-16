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

package com.dtstack.batch.dao;

import com.dtstack.batch.domain.RolePermission;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author toutian
 */
public interface RolePermissionDao {

    List<RolePermission> listByRoleId(@Param("roleId") Long roleId);

    List<Long> listPermissionIdsByRoleId(@Param("roleId") Long roleId);

    Integer deleteByRoleIdAndPermissionIds(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> existPermissionIds, @Param("modifyUserId") Long modifyUserId);

    Integer insert(RolePermission rolePermission);
}