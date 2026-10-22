package com.wanfo.bi.query;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class GovernedJdbcTest {
 @Test void physicalStatementsAreTrackedOutsideRequestThreadAndBindingIsUnchanged() throws Exception {
  var g=new QueryGovernor(new com.wanfo.bi.config.BiProperties(),null);var raw=mock(Connection.class);var statement=mock(PreparedStatement.class);when(raw.prepareStatement("select ?")).thenReturn(statement);var worker=java.util.concurrent.Executors.newSingleThreadExecutor();
  try(var run=g.admit("user",List.of(1L))){var conn=GovernedJdbc.connection(raw,run);var ps=worker.submit(()->conn.prepareStatement("select ?")).get();ps.setObject(1,"bound-value");verify(statement).setObject(1,"bound-value");verify(statement).setQueryTimeout(30);run.stop("QUERY_CANCELLED");assertThrows(QueryGovernanceException.class,ps::executeQuery);verify(statement,never()).executeQuery();ps.close();verify(statement).close();assertEquals(1,g.activeCount());}finally{worker.shutdownNow();g.shutdown();}
 }
 @Test void unsupportedTimeoutClosesNewStatementAndDoesNotExecute() throws Exception {
  var g=new QueryGovernor(new com.wanfo.bi.config.BiProperties(),null);var raw=mock(Connection.class);var statement=mock(PreparedStatement.class);when(raw.prepareStatement("select 1")).thenReturn(statement);doThrow(new SQLFeatureNotSupportedException()).when(statement).setQueryTimeout(anyInt());try(var run=g.admit("user",List.of(1L))){assertThrows(SQLFeatureNotSupportedException.class,()->GovernedJdbc.connection(raw,run).prepareStatement("select 1"));verify(statement).close();verify(statement,never()).executeQuery();}finally{g.shutdown();}
 }
}
