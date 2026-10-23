package com.wanfo.bi.datasource;
import java.sql.*;
import java.util.*;

/** Bounded connect/read waits without weakening vendor TLS settings. */
public final class JdbcConnectionSettings {
    private JdbcConnectionSettings(){}
    public static Properties properties(BiDatasource source) {
        var p=new Properties();
        if(source.getUsername()!=null)p.setProperty("user",source.getUsername());
        if(source.getPassword()!=null)p.setProperty("password",source.getPassword());
        String type=source.getType()==null?"":source.getType().toLowerCase(Locale.ROOT);
        switch(type){
            case "postgresql","pg"->{p.setProperty("connectTimeout","5");p.setProperty("socketTimeout","60");}
            case "sqlserver","mssql"->{p.setProperty("loginTimeout","5");p.setProperty("socketTimeout","60000");}
            case "oracle"->{p.setProperty("oracle.net.CONNECT_TIMEOUT","5000");p.setProperty("oracle.jdbc.ReadTimeout","60000");}
            case "clickhouse","ck"->{p.setProperty("connection_timeout","5000");p.setProperty("socket_timeout","60000");}
            case "mysql","mariadb","doris","starrocks","tidb"->{p.setProperty("connectTimeout","5000");p.setProperty("socketTimeout","60000");}
            default->{ }
        }
        return p;
    }
    public static Connection open(BiDatasource source)throws SQLException {
        return DriverManager.getConnection(source.getJdbcUrl(),properties(source));
    }
}
