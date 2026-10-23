package com.wanfo.bi.datasource;

import com.wanfo.bi.service.*;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.security.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcConnectorTest {
    @Test void productionDriversLoadAndAcceptTheirOwnUrls()throws Exception {
        for(var entry:Map.of("postgresql","jdbc:postgresql://localhost:5432/db","sqlserver","jdbc:sqlserver://localhost:1433;databaseName=db","oracle","jdbc:oracle:thin:@//localhost:1521/service","clickhouse","jdbc:clickhouse://localhost:8123/db").entrySet()) {
            var driver=(Driver)Class.forName(BiDataSourceService.resolveDriver(entry.getKey(),null)).getConstructor().newInstance();
            assertTrue(driver.acceptsURL(entry.getValue()),entry.getKey());
        }
    }
    @Test void vendorScopeAndQuotingDoNotExposeOtherSchemas()throws Exception {
        for(String product:List.of("PostgreSQL","Microsoft SQL Server","Oracle","ClickHouse")) {
            Connection c=mock(Connection.class);DatabaseMetaData md=mock(DatabaseMetaData.class);
            when(c.getMetaData()).thenReturn(md);when(md.getDatabaseProductName()).thenReturn(product);when(c.getCatalog()).thenReturn("business");when(c.getSchema()).thenReturn("sales");
            var scope=JdbcSourceScope.resolve(c,null);assertEquals("sales",scope.schema());assertEquals("business",scope.catalog());
            assertThrows(BiAccessException.class,()->scope.require("secret"));
            assertThrows(BiAccessException.class,()->JdbcSourceScope.resolve(c,"pg_catalog"));
            when(md.getIdentifierQuoteString()).thenReturn("[");assertEquals("[sales].[a]]b]",scope.relation(md,"a]b"));
        }
    }
    @Test void clickHouseDatabaseIsTakenFromSchemaNotCatalog()throws Exception {
        var c=mock(Connection.class);var md=mock(DatabaseMetaData.class);when(c.getMetaData()).thenReturn(md);when(md.getDatabaseProductName()).thenReturn("ClickHouse");when(c.getSchema()).thenReturn("business");
        var scope=JdbcSourceScope.resolve(c,null);assertNull(scope.catalog());assertEquals("business",scope.schema());
    }
    @Test void previewLimitsAreNativeToEachVendor(){
        assertEquals("SELECT TOP (10) x FROM t",JdbcSourceScope.limit("Microsoft SQL Server","SELECT x FROM t",10));
        assertEquals("SELECT * FROM (SELECT x FROM t) WHERE ROWNUM <= 10",JdbcSourceScope.limit("Oracle","SELECT x FROM t",10));
        assertEquals("SELECT x FROM t LIMIT 10",JdbcSourceScope.limit("ClickHouse","SELECT x FROM t",10));
        assertEquals("SELECT 1 FROM DUAL",JdbcSourceScope.testSql("oracle"));
        assertEquals("DECIMAL",JdbcSourceScope.columnType(Types.NUMERIC,"NUMBER"));
        assertEquals("TIMESTAMP",JdbcSourceScope.columnType(Types.TIMESTAMP,"datetime2"));
    }
    @Test void actualJdbcModellingAndCalciteQueryInThreeCompatibilityModes()throws Exception {
        for(String mode:List.of("PostgreSQL","Oracle","MSSQLServer")) {
            var ds=new BiDatasource();ds.setId(1L);ds.setSchemaName("source");ds.setName(mode);ds.setType("h2");ds.setStatus("0");ds.setDriverClass("org.h2.Driver");ds.setUsername("sa");ds.setPassword("");
            ds.setJdbcUrl("jdbc:h2:mem:connector_"+mode+";MODE="+mode+";DB_CLOSE_DELAY=-1");ds.setSourceSchema("SALES");
            try(Connection c=DriverManager.getConnection(ds.getJdbcUrl(),"sa","");Statement st=c.createStatement()){
                st.execute("CREATE SCHEMA SALES");st.execute("CREATE TABLE SALES.ORDERS(ID INT, AMOUNT DECIMAL(12,2))");st.execute("INSERT INTO SALES.ORDERS VALUES(1,10),(2,20)");
                st.execute("CREATE SCHEMA PRIVATE_DATA");st.execute("CREATE TABLE PRIVATE_DATA.SECRET(ID INT)");
            }
            var provider=mock(DataSourceProvider.class);when(provider.getBySchemaName("source")).thenReturn(ds);
            var engine=new CalciteQueryEngine();var service=new CatalogService(engine,provider,mock(BiResourceAuthorizer.class),mock(CapabilityProvider.class));
            try {
                assertEquals(List.of("SALES"),service.listCatalogs("source"));
                assertEquals(1,service.listTables("source","SALES").size());
                assertThrows(BiAccessException.class,()->service.listTables("source","PRIVATE_DATA"));
                var plan=service.plan("source","SALES","ORDERS",List.of("ID","AMOUNT"));
                assertTrue(plan.sql().contains("\"source\".\"ORDERS\""));
                assertEquals(2,service.preview("source","SALES","ORDERS",List.of("AMOUNT"),10).get("rowCount"));
                try(Connection c=engine.open(List.of(ds));Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT SUM(\"AMOUNT\") FROM \"source\".\"ORDERS\"")){assertTrue(rs.next());assertEquals(0,new java.math.BigDecimal("30").compareTo(rs.getBigDecimal(1)));}
                try(Connection c=engine.open(List.of(ds));Statement st=c.createStatement()){assertThrows(SQLException.class,()->st.executeQuery("SELECT * FROM \"source\".\"SECRET\""));}
            } finally {engine.invalidate("source");}
        }
    }
}
