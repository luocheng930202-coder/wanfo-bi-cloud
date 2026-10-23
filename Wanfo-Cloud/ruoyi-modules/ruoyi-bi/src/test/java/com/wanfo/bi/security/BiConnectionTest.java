package com.wanfo.bi.security;

import java.sql.*;
import java.util.Map;
import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.datasource.CredentialCipher;
import com.wanfo.bi.datasource.DbDataSourceProvider;
import com.wanfo.bi.domain.BiDatasourceDO;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.service.BiDataSourceService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiConnectionTest
{
    private final DbDataSourceProvider provider = mock(DbDataSourceProvider.class);
    private final CredentialCipher cipher = new CredentialCipher("test-key");
    private final CalciteQueryEngine engine = mock(CalciteQueryEngine.class);
    private final BiResourceAuthorizer resources = mock(BiResourceAuthorizer.class);
    private final BiDataSourceService service = new BiDataSourceService(provider, cipher, engine, new BiProperties(), resources);

    private BiDatasourceDO form()
    {
        BiDatasourceDO form = new BiDatasourceDO();
        form.setName("demo"); form.setSchemaName("demo"); form.setType("mysql");
        form.setJdbcUrl("jdbc:bi-test:local"); form.setUsername("testuser");
        form.setPassword("saved-secret"); form.setDriverClass("java.lang.String");
        return form;
    }

    @Test void newFormTestsWithoutPersistingOrReturningPassword() throws Exception
    {
        Connection conn = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rows = mock(ResultSet.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(conn.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("TestDB");
        when(meta.getDatabaseProductVersion()).thenReturn("1");
        try (var driver = mockStatic(DriverManager.class))
        {
            driver.when(() -> DriverManager.getConnection(eq("jdbc:bi-test:local"), argThat((java.util.Properties p) -> "testuser".equals(p.getProperty("user")) && "saved-secret".equals(p.getProperty("password"))))).thenReturn(conn);
            Map<String,Object> result = service.testFormConnection(form());
            assertEquals(true, result.get("ok"));
            assertFalse(result.toString().contains("saved-secret"));
            verify(conn).close(); verify(statement).close(); verify(rows).close();
            verifyNoInteractions(provider, engine, resources);
        }
    }

    @Test void editBlankPasswordUsesSavedSecretAndDoesNotMutateStoredObject() throws Exception
    {
        BiDatasourceDO saved = form(); saved.setId(7L); saved.setPassword(cipher.encrypt("saved-secret"));
        when(provider.getRow(7L)).thenReturn(saved);
        BiDatasourceDO edit = form(); edit.setId(7L); edit.setPassword("");
        try (var driver = mockStatic(DriverManager.class))
        {
            SQLException failure = new SQLException("sensitive details", "28000");
            driver.when(() -> DriverManager.getConnection(anyString(), any(java.util.Properties.class)))
                    .thenThrow(failure);
            Map<String,Object> result = service.testFormConnection(edit);
            driver.verify(() -> DriverManager.getConnection(eq("jdbc:bi-test:local"), argThat((java.util.Properties p) -> "testuser".equals(p.getProperty("user")) && "saved-secret".equals(p.getProperty("password")))));
            assertEquals(false, result.get("ok"));
            assertFalse(result.toString().contains("sensitive details"));
            assertTrue(saved.getPassword().startsWith("enc:"));
            assertEquals("", edit.getPassword());
            verify(provider, never()).update(any());
        }
    }

    @Test void changedDestinationCannotReuseStoredPassword()
    {
        BiDatasourceDO saved = form(); saved.setId(7L); saved.setPassword(cipher.encrypt("saved-secret"));
        when(provider.getRow(7L)).thenReturn(saved);
        BiDatasourceDO edit = form(); edit.setId(7L); edit.setPassword(""); edit.setJdbcUrl("jdbc:bi-test:elsewhere");
        assertThrows(IllegalArgumentException.class, () -> service.testFormConnection(edit));
    }

    @Test void createCannotReuseAnExistingDatasourcesSecretBySupplyingItsId()
    {
        BiDatasourceDO form = form(); form.setId(7L); form.setPassword("");
        assertThrows(IllegalArgumentException.class, () -> service.create(form, "alice"));
        verifyNoInteractions(provider);
    }

    @Test void failedCreateAndEditDoNotWriteMetadata() throws Exception
    {
        BiDatasourceDO saved = form(); saved.setId(7L); when(provider.getRow(7L)).thenReturn(saved);
        try (var driver = mockStatic(DriverManager.class))
        {
            SQLException failure = new SQLException("connection failed");
            driver.when(() -> DriverManager.getConnection(anyString(), any(java.util.Properties.class)))
                    .thenThrow(failure);
            assertEquals(true, service.create(form(), "alice").get("rolledBack"));
            BiDatasourceDO edit = form(); edit.setId(7L);
            assertEquals(true, service.update(edit, "alice").get("rolledBack"));
            verify(provider, never()).insert(any()); verify(provider, never()).update(any());
            verifyNoInteractions(engine, resources);
        }
    }
}
