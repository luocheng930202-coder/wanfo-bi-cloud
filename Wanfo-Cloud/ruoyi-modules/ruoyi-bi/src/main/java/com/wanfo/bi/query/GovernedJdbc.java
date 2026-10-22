package com.wanfo.bi.query;
import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;

/** Capture the run when constructing the schema, including Calcite worker-thread JDBC access. */
public final class GovernedJdbc {
 private GovernedJdbc(){}
 public static DataSource wrap(DataSource source,QueryGovernor.Run run){
  if(run==null)return source;
  return (DataSource)Proxy.newProxyInstance(GovernedJdbc.class.getClassLoader(),new Class<?>[]{DataSource.class},(proxy,method,args)->{
   if(method.getName().equals("getConnection")){run.check();return connection((Connection)invoke(source,method,args),run);}return invoke(source,method,args);
  });
 }
 static Connection connection(Connection connection,QueryGovernor.Run run){
  return (Connection)Proxy.newProxyInstance(GovernedJdbc.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
   String name=method.getName();if(name.startsWith("prepare") || name.equals("createStatement"))run.check();
   Object value=invoke(connection,method,args);
   if(value instanceof Statement statement){try{run.watch(statement);}catch(Exception failure){try{statement.close();}catch(Exception close){failure.addSuppressed(close);}throw failure;}
    Class<?> type=statement instanceof CallableStatement?CallableStatement.class:statement instanceof PreparedStatement?PreparedStatement.class:Statement.class;
    return Proxy.newProxyInstance(GovernedJdbc.class.getClassLoader(),new Class<?>[]{type},(p,m,a)->{if(m.getName().equals("close"))run.unwatch(statement);else if(m.getName().startsWith("execute"))run.check();return invoke(statement,m,a);});
   }return value;
  });
 }
 private static Object invoke(Object target,Method method,Object[] args)throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
}
