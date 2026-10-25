package com.wanfo.bi.query;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcResultValuesTest
{
    @Test
    void serializesDriverTimestampsAsIsoLocalDateTime() throws Exception
    {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnType(1)).thenReturn(Types.TIMESTAMP);
        when(resultSet.getTimestamp(1)).thenReturn(Timestamp.valueOf("2026-10-25 08:09:10.125"));

        assertEquals("2026-10-25T08:09:10.125", JdbcResultValues.read(resultSet, 1));
    }

    @Test
    void serializesSqlDatesWithoutInventingATimeZone() throws Exception
    {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnType(1)).thenReturn(Types.DATE);
        when(resultSet.getDate(1)).thenReturn(java.sql.Date.valueOf("2026-10-25"));

        assertEquals("2026-10-25", JdbcResultValues.read(resultSet, 1));
    }

    @Test
    void preservesNullTimestampValues() throws Exception
    {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnType(1)).thenReturn(Types.TIMESTAMP);
        when(resultSet.getTimestamp(1)).thenReturn(null);

        assertNull(JdbcResultValues.read(resultSet, 1));
    }

    @Test
    void serializesOracleTimestampFromLiveInstanceWhenConfigured() throws Exception
    {
        String url = System.getenv("WANFO_ORACLE_TEST_URL");
        String user = System.getenv("WANFO_ORACLE_TEST_USER");
        String password = System.getenv("WANFO_ORACLE_TEST_PASSWORD");
        assumeTrue(url != null && user != null && password != null,
                "Set WANFO_ORACLE_TEST_URL, WANFO_ORACLE_TEST_USER and WANFO_ORACLE_TEST_PASSWORD to enable live Oracle verification");

        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT event_time FROM BI_OWNER.sales_demo WHERE id = 1"))
        {
            assertTrue(resultSet.next());
            Object normalized = JdbcResultValues.read(resultSet, 1);
            assertInstanceOf(String.class, normalized);
            assertTrue(normalized.toString().startsWith("2026-10-01T08:00:00.125"));
            String json = new tools.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("eventTime", normalized));
            assertTrue(json.contains("\"eventTime\":\"2026-10-01T08:00:00.125\""));
        }
    }
}
