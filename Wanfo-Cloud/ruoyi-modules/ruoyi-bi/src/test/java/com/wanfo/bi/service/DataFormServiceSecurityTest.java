package com.wanfo.bi.service;

import java.util.*;
import java.sql.*;
import com.wanfo.bi.datasource.*;
import com.wanfo.bi.domain.*;
import com.wanfo.bi.domain.form.DataFormField;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import tools.jackson.databind.ObjectMapper;

class DataFormServiceSecurityTest {
    BiDataFormMapper forms=mock(BiDataFormMapper.class);
    BiDataFormRecordMapper records=mock(BiDataFormRecordMapper.class);
    BiDatasetTableMapper datasets=mock(BiDatasetTableMapper.class);
    BiDatasetFieldMapper fields=mock(BiDatasetFieldMapper.class);
    DataSourceProvider sources=mock(DataSourceProvider.class);
    CalciteQueryEngine engine=mock(CalciteQueryEngine.class);
    BiResourceAuthorizer resources=mock(BiResourceAuthorizer.class);
    DatasetDataPolicy policies=mock(DatasetDataPolicy.class);
    DataFormAuditService audit=mock(DataFormAuditService.class);
    Connection metadata=mock(Connection.class),connection=mock(Connection.class);
    PreparedStatement statement=mock(PreparedStatement.class);
    BiActor actor=new BiActor("ruoyi",2L,"user",3L,Set.of());
    CurrentActorProvider actors=()->ActorScope.active()?ActorScope.actor():actor;
    CapabilityProvider caps=p->{if(ActorScope.active()&&!ActorScope.allows(p))throw new BiAccessException(403,"denied");};
    DataFormService service=new DataFormService(forms,records,datasets,fields,sources,engine,resources,caps,actors,new ObjectMapper(),policies,audit,r->new LiveActorResolver.Snapshot(actor,Set.of("bi:form:submit","bi:form:list")));
    String key="request_1234567890";
    @BeforeEach void fixture() throws Exception {
        var form=new BiDataFormDO();form.setId(1L);form.setDatasetId(5L);form.setStatus("0");form.setFieldsJson("[{\"field\":\"amount\",\"widget\":\"number\",\"required\":true}]");when(forms.selectById(1L)).thenReturn(form);
        var dataset=new BiDatasetTableDO();dataset.setId(5L);dataset.setType("table");dataset.setDatasourceId(6L);dataset.setSourceCatalog("test");dataset.setSourceTable("input");when(datasets.selectById(5L)).thenReturn(dataset);
        var field=new BiDatasetFieldDO();field.setOriginName("amount");field.setOriginType("decimal");field.setExtFlag("0");when(fields.selectByDatasetId(5L)).thenReturn(List.of(field));
        var source=new BiDatasource();source.setStatus("0");source.setSchemaName("test");when(sources.getById(6L)).thenReturn(source);
        var md=mock(DatabaseMetaData.class);when(metadata.getMetaData()).thenReturn(md);when(md.getIdentifierQuoteString()).thenReturn("`");when(engine.borrowConnection("test")).thenReturn(metadata,connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);when(statement.executeUpdate()).thenReturn(1);
        when(policies.resolve(5L)).thenReturn(DatasetDataPolicy.Scope.unrestricted());
        when(audit.reserve(any())).thenAnswer(i->{BiDataFormRecordDO r=i.getArgument(0);r.setId(9L);return new DataFormAuditService.Reservation(r,true);});
    }
    @Test void queryPermissionDoesNotGrantWrite() throws Exception {
        doThrow(new BiAccessException(403,"write denied")).when(resources).require("dataset",5L,"write");
        assertThrows(BiAccessException.class,()->service.submit(1L,Map.of("amount",10),key));verifyNoInteractions(engine,audit);
    }
    @Test void missingIdempotencyKeyIsRejectedBeforeWriting() throws Exception {
        assertThrows(IllegalArgumentException.class,()->service.submit(1L,Map.of("amount",10),null));verifyNoInteractions(engine,audit);
    }
    @Test void successReservesBeforeExternalWriteAndFinishesAfterCommit() throws Exception {
        assertEquals(true,service.submit(1L,Map.of("amount",10),key).get("submitted"));
        var order=inOrder(audit,connection,statement);order.verify(audit).reserve(any());order.verify(connection).setAutoCommit(false);order.verify(connection).prepareStatement(anyString());order.verify(statement).executeUpdate();order.verify(connection).commit();order.verify(audit).finish(9L,"succeeded",null);
        assertFalse(ActorScope.active());
    }
    @Test void duplicatePendingSubmissionNeverRunsDmlAgain() throws Exception {
        var existing=new BiDataFormRecordDO();existing.setId(9L);existing.setSubmissionStatus("pending");doReturn(new DataFormAuditService.Reservation(existing,false)).when(audit).reserve(any());
        assertEquals("pending",service.submit(1L,Map.of("amount",10),key).get("status"));verify(connection,never()).prepareStatement(anyString());verify(audit,never()).finish(anyLong(),anyString(),any());
    }
    @Test void auditUnavailablePreventsExternalWrite() throws Exception {
        doThrow(new IllegalStateException("audit offline")).when(audit).reserve(any());
        assertThrows(IllegalStateException.class,()->service.submit(1L,Map.of("amount",10),key));verify(connection,never()).prepareStatement(anyString());
    }
    @Test void executionFailureRollsBackAndKeepsFailureAudit() throws Exception {
        when(statement.executeUpdate()).thenThrow(new SQLException("private SQL"));
        var error=assertThrows(BiAccessException.class,()->service.submit(1L,Map.of("amount",10),key));assertFalse(error.getMessage().contains("private"));
        verify(connection).rollback();verify(connection,never()).commit();verify(audit).finish(eq(9L),eq("failed"),anyString());
    }
    @Test void lostCommitAcknowledgementIsUncertainAndNotRetried() throws Exception {
        doThrow(new SQLException("connection lost during commit")).when(connection).commit();
        assertEquals("uncertain",service.submit(1L,Map.of("amount",10),key).get("status"));verify(audit).finish(eq(9L),eq("uncertain"),anyString());verify(statement,times(1)).executeUpdate();
    }
    @Test void auditUpdateFailureAfterCommitKeepsPendingIntent() throws Exception {
        doThrow(new IllegalStateException("audit offline")).when(audit).finish(9L,"succeeded",null);
        assertEquals("pending",service.submit(1L,Map.of("amount",10),key).get("status"));verify(connection).commit();verify(connection,never()).rollback();
    }
    @Test void restrictedColumnRejectedBeforeReservingOrDml() throws Exception {
        when(policies.resolve(5L)).thenReturn(new DatasetDataPolicy.Scope(List.of(),Set.of(),Set.of("amount")));
        assertThrows(BiAccessException.class,()->service.submit(1L,Map.of("amount",10),key));verifyNoInteractions(audit);verify(connection,never()).prepareStatement(anyString());
    }
    @Test void recordQueryUsesCurrentOwnerWithoutAdminBypass() throws Exception {
        when(records.listVisible(1L,2L,false)).thenReturn(List.of());assertTrue(service.records(1L).isEmpty());verify(records).listVisible(1L,2L,false);
    }
}
