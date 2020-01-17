package com.dtstack.engine.rdbs.oracle;

import com.dtstack.engine.rdbs.common.executor.ConnFactory;

public class OracleConnFactory extends ConnFactory {

    public OracleConnFactory() {
        driverName = "oracle.jdbc.driver.OracleDriver";
        testSql = "select 1111 from dual";
    }

    @Override
    public String getCreateProcedureHeader(String procName) {
        return String.format("create  procedure \"%s\" Authid Current_User as\n", procName);
    }
}