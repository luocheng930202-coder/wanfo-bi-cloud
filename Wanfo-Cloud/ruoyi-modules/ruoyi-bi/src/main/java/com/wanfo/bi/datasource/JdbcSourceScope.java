package com.wanfo.bi.datasource;
import java.sql.*;
import java.util.*;

/** One explicit database/schema scope per source, shared by JDBC modelling and Calcite registration. */
public record JdbcSourceScope(String catalog,String schema,String name) {
 private static final Set<String> SYSTEM=Set.of("mysql","sys","information_schema","performance_schema","pg_catalog","sysibm","system","sysaux");
 public static JdbcSourceScope resolve(Connection connection,String configured)throws SQLException{
  String product=connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
  String catalog=connection.getCatalog(),schema=null;
  boolean schemaDatabase=product.contains("postgres") || product.contains("sql server") || product.contains("oracle") || product.contains("h2") || product.contains("clickhouse");
  if(schemaDatabase){try{schema=connection.getSchema();}catch(SQLFeatureNotSupportedException | AbstractMethodError ignored){}if(schema==null || schema.isBlank())schema=product.contains("oracle")?connection.getMetaData().getUserName():product.contains("sql server")?"dbo":"public";if(configured!=null && !configured.isBlank())schema=configured.trim();}
  else if(configured!=null && !configured.isBlank() && !configured.equals(catalog))throw new IllegalArgumentException("物理 schema 须与连接串中的数据库一致");
  String name=schemaDatabase?schema:catalog;
  if(name==null || name.isBlank())throw new IllegalArgumentException("请在连接串或物理 schema 中指定建模范围");
  if(SYSTEM.contains(name.toLowerCase(Locale.ROOT)) || name.toLowerCase(Locale.ROOT).startsWith("pg_toast"))throw new com.wanfo.bi.security.BiAccessException(403,"系统库或 schema 不可用于业务建模");
  return new JdbcSourceScope(catalog,schema,name);
 }
 public void require(String requested){if(requested!=null && !requested.isBlank() && !name.equals(requested))throw new com.wanfo.bi.security.BiAccessException(403,"该数据库或 schema 不在数据源建模范围内");}
 public String relation(DatabaseMetaData metadata,String table)throws SQLException{return quote(metadata,name)+"."+quote(metadata,table);}
 public static String quote(DatabaseMetaData metadata,String name)throws SQLException{String q=metadata.getIdentifierQuoteString().trim();if(q.isEmpty())throw new IllegalArgumentException("驱动不支持标识符引用");String end=q.equals("[")?"]":q;return q+name.replace(end,end+end)+end;}
 public static String testSql(String type){return type!=null && type.equalsIgnoreCase("oracle")?"SELECT 1 FROM DUAL":"SELECT 1";}
 public static String limit(String product,String sql,int rows){
  if(rows<1 || rows>100)throw new IllegalArgumentException("预览行数须在 1～100 之间");
  String p=product.toLowerCase(Locale.ROOT);
  if(p.contains("sql server"))return sql.replaceFirst("SELECT ","SELECT TOP ("+rows+") ");
  if(p.contains("h2"))return sql+" FETCH FIRST "+rows+" ROWS ONLY";
  if(p.contains("oracle"))return "SELECT * FROM ("+sql+") WHERE ROWNUM <= "+rows;
  return sql+" LIMIT "+rows;
 }
 public static String columnType(int jdbc,String vendor){
  return switch(jdbc){
   case Types.TINYINT,Types.SMALLINT,Types.INTEGER->"INTEGER";
   case Types.BIGINT->"BIGINT";
   case Types.NUMERIC,Types.DECIMAL->"DECIMAL";
   case Types.FLOAT,Types.REAL,Types.DOUBLE->"DOUBLE";
   case Types.DATE->"DATE";
   case Types.TIME,Types.TIME_WITH_TIMEZONE->"TIME";
   case Types.TIMESTAMP,Types.TIMESTAMP_WITH_TIMEZONE->"TIMESTAMP";
   case Types.BOOLEAN,Types.BIT->"BOOLEAN";
   default->vendor==null?"VARCHAR":vendor;
  };
 }
}
