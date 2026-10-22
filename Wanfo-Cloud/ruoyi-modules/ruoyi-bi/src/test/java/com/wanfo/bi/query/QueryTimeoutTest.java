package com.wanfo.bi.query;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class QueryTimeoutTest {
 @Test void preparedTimeoutClosesStatementAndPreservesParameters() throws Exception {
  var conn=mock(Connection.class);var ps=mock(PreparedStatement.class);when(conn.prepareStatement("select ?")).thenReturn(ps);when(ps.executeQuery()).thenThrow(new SQLTimeoutException());
  assertThrows(SQLTimeoutException.class,()->new CalciteQueryEngine().queryWithParams(conn,"select ?",List.of("bound-value"),10));verify(ps).setQueryTimeout(30);verify(ps).setObject(1,"bound-value");verify(ps).close();verify(conn,never()).close();
 }
 @Test void ordinaryStatementGetsTimeoutAndClosesAfterReadFailure() throws Exception {
  var conn=mock(Connection.class);var st=mock(Statement.class);var rs=mock(ResultSet.class);var meta=mock(ResultSetMetaData.class);when(conn.createStatement()).thenReturn(st);when(st.executeQuery("select 1")).thenReturn(rs);when(rs.getMetaData()).thenReturn(meta);when(meta.getColumnCount()).thenReturn(1);when(meta.getColumnLabel(1)).thenReturn("v");when(rs.next()).thenThrow(new SQLException());
  assertThrows(SQLException.class,()->new CalciteQueryEngine().query(conn,"select 1",10));verify(st).setQueryTimeout(30);verify(rs).close();verify(st).close();
 }
 @Test void timeLimitsCannotBeDisabledOrMadeUnbounded(){var p=new com.wanfo.bi.config.BiProperties();p.setQueryTimeoutSeconds(0);assertEquals(1,p.getQueryTimeoutSeconds());p.setQueryTimeoutSeconds(999);assertEquals(120,p.getQueryTimeoutSeconds());}
}
