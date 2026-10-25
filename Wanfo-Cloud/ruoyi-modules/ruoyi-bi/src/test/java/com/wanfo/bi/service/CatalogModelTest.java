package com.wanfo.bi.service;

import java.sql.*;
import java.util.List;
import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogModelTest
{
    private final CalciteQueryEngine engine = mock(CalciteQueryEngine.class);
    private final DataSourceProvider sources = mock(DataSourceProvider.class);
    private final BiResourceAuthorizer resources = mock(BiResourceAuthorizer.class);
    private final CapabilityProvider capabilities = mock(CapabilityProvider.class);
    private final Connection conn = mock(Connection.class);
    private final DatabaseMetaData md = mock(DatabaseMetaData.class);
    private CatalogService service;

    @BeforeEach void setup() throws Exception
    {
        service = new CatalogService(engine,sources,resources,capabilities);
        BiDatasource ds = new BiDatasource(); ds.setId(7L); ds.setSchemaName("demo"); ds.setStatus("0");
        ds.setName("Demo"); ds.setType("mysql"); ds.setJdbcUrl("secret-url"); ds.setUsername("secret-user");
        when(sources.getBySchemaName("demo")).thenReturn(ds); when(sources.listEnabled()).thenReturn(List.of(ds));
        when(engine.borrowConnection("demo")).thenReturn(conn);
        when(conn.getCatalog()).thenReturn("poc_a"); when(conn.getMetaData()).thenReturn(md);
        when(md.getDatabaseProductName()).thenReturn("MySQL"); when(md.getSearchStringEscape()).thenReturn("\\");
        when(md.getIdentifierQuoteString()).thenReturn("`");
    }

    private void metadata() throws Exception
    {
        ResultSet tables=mock(ResultSet.class), columns=mock(ResultSet.class), keys=mock(ResultSet.class);
        when(md.getTables(anyString(),isNull(),anyString(),any(String[].class))).thenReturn(tables);
        when(tables.next()).thenReturn(true,false); when(tables.getString("TABLE_NAME")).thenReturn("orders_2026");
        when(md.getPrimaryKeys(anyString(),isNull(),anyString())).thenReturn(keys);
        when(md.getColumns(anyString(),isNull(),anyString(),eq("%"))).thenReturn(columns);
        when(columns.next()).thenReturn(true,true,false);
        when(columns.getString("TABLE_NAME")).thenReturn("orders_2026");
        when(columns.getString("COLUMN_NAME")).thenReturn("id","city");
        when(columns.getString("TYPE_NAME")).thenReturn("BIGINT","VARCHAR");
    }

    @Test void viewingDatasourceDoesNotAllowModelling() throws Exception
    {
        doThrow(new BiAccessException(403,"denied")).when(resources).require("datasource",7L,"model");
        assertThrows(BiAccessException.class,()->service.listCatalogs("demo"));
        verify(engine,never()).borrowConnection(anyString());
    }

    @Test void crossDatabaseAndSystemDatabasesAreRejectedBeforeMetadataRead() throws Exception
    {
        assertThrows(BiAccessException.class,()->service.listTables("demo","ry_vue"));
        when(conn.getCatalog()).thenReturn("mysql");
        assertThrows(BiAccessException.class,()->service.listCatalogs("demo"));
        verify(md,never()).getTables(any(),any(),any(),any());
    }

    @Test void tableAndFieldInputsMustMatchMetadata() throws Exception
    {
        metadata();
        assertThrows(IllegalArgumentException.class,()->service.plan("demo","poc_a","orders_2026",List.of("id FROM mysql.user")));
        verify(md).getTables(eq("poc_a"),isNull(),eq("orders\\_2026"),any(String[].class));
        verify(conn,never()).createStatement();
    }

    @Test void fabricatedTableCannotReachExecution() throws Exception
    {
        metadata();
        assertThrows(IllegalArgumentException.class,()->service.preview("demo","poc_a","orders_2026; DROP TABLE x",List.of("id"),10));
        verify(conn,never()).createStatement();
    }

    @Test void previewHasServerLimitTimeoutAndOnlySelectedColumns() throws Exception
    {
        metadata();
        PreparedStatement st=mock(PreparedStatement.class); ResultSet rows=mock(ResultSet.class); ResultSetMetaData rowMetadata=mock(ResultSetMetaData.class);
        when(conn.prepareStatement(anyString())).thenReturn(st); when(st.executeQuery()).thenReturn(rows);
        when(rows.getMetaData()).thenReturn(rowMetadata); when(rowMetadata.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(rows.next()).thenReturn(true,false); when(rows.getObject(1)).thenReturn("Shanghai");
        var result=service.preview("demo","poc_a","orders_2026",List.of("city"),10);
        assertEquals(List.of("city"),result.get("columns")); assertEquals(1,result.get("rowCount"));
        verify(st).setMaxRows(10); verify(st).setQueryTimeout(10);
        verify(conn).prepareStatement("SELECT `city` FROM `poc_a`.`orders_2026` LIMIT 10");
        verify(st).executeQuery();
        verify(st).close(); verify(rows).close();
    }

    @Test void oversizedPreviewCannotOpenConnection() throws Exception
    {
        assertThrows(IllegalArgumentException.class,()->service.preview("demo","poc_a","orders_2026",List.of("id"),101));
        assertThrows(IllegalArgumentException.class,()->service.preview("demo","poc_a","orders_2026",List.of("id"),0));
        verify(engine,never()).borrowConnection(anyString());
    }

    @Test void sourcePickerDoesNotExposeConnectionCredentials()
    {
        when(resources.allowed("datasource",7L,"model")).thenReturn(true);
        String picker=service.sources().toString();
        assertFalse(picker.contains("secret")); assertTrue(picker.contains("Demo"));
    }
}
