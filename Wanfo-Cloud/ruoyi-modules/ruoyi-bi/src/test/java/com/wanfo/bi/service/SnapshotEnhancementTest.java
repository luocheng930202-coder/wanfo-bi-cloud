package com.wanfo.bi.service;
import com.wanfo.bi.datasource.*;
import com.wanfo.bi.datasource.snapshot.*;
import com.wanfo.bi.domain.BiDatasourceDO;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapshotEnhancementTest {
    @Test void filePreviewNeverPublishesAndAppendValidatesAllRowsAgainstSavedTypes()throws Exception {
        var provider=mock(DbDataSourceProvider.class);var mapper=mock(BiSourceSnapshotMapper.class);var registry=new SnapshotRegistry(mapper);var source=new BiDatasourceDO();source.setType("csv");when(provider.getRow(7L)).thenReturn(source);
        var data=SnapshotParser.csv("code,amount\n001,12.5".getBytes(StandardCharsets.UTF_8));var json=new ObjectMapper();var old=new BiSourceSnapshotMapper.Row(7L,"csv",null,json.writeValueAsString(data.columns()),json.writeValueAsString(data.rows()),1,1,"a.csv","now");when(mapper.get(7L)).thenReturn(old);when(mapper.replace(any())).thenReturn(1);
        var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));var service=new SnapshotImportService(provider,mapper,registry,new CredentialCipher("test"),mock(ApiSnapshotClient.class),mock(BiResourceAuthorizer.class),mock(CapabilityProvider.class),mock(CurrentActorProvider.class),manager);
        byte[] content="code,amount\n002,20.5".getBytes(StandardCharsets.UTF_8);assertEquals(1,service.previewFile(7L,"new.csv",null,content).get("rowCount"));verify(mapper,never()).replace(any());
        var result=service.file(7L,null,null,"new.csv",null,content,"append",null);assertEquals(2,result.get("rowCount"));var captor=org.mockito.ArgumentCaptor.forClass(BiSourceSnapshotMapper.Row.class);verify(mapper).replace(captor.capture());assertTrue(captor.getValue().rowsJson().contains("001"));assertTrue(captor.getValue().rowsJson().contains("002"));
        assertThrows(IllegalArgumentException.class,()->service.file(7L,null,null,"new.csv",null,content,"replace","{\"amount\":\"DATE\"}"));
    }
    @Test void invalidRowsCanBeReportedAndStructureReplacementMustBeExplicit()throws Exception {
        var provider=mock(DbDataSourceProvider.class);var mapper=mock(BiSourceSnapshotMapper.class);var registry=new SnapshotRegistry(mapper);var source=new BiDatasourceDO();source.setType("csv");when(provider.getRow(7L)).thenReturn(source);var json=new ObjectMapper();var oldData=SnapshotParser.csv("code,amount\n001,12.5".getBytes(StandardCharsets.UTF_8));var old=new BiSourceSnapshotMapper.Row(7L,"csv",null,json.writeValueAsString(oldData.columns()),json.writeValueAsString(oldData.rows()),1,1,"a.csv","now");when(mapper.get(7L)).thenReturn(old);when(mapper.replace(any())).thenReturn(1);
        var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));var service=new SnapshotImportService(provider,mapper,registry,new CredentialCipher("test"),mock(ApiSnapshotClient.class),mock(BiResourceAuthorizer.class),mock(CapabilityProvider.class),mock(CurrentActorProvider.class),manager);
        byte[] csv="code,amount\n002,20.5\n003,nope".getBytes(StandardCharsets.UTF_8);var report=service.file(7L,null,null,"new.csv",null,csv,"append","{\"amount\":\"DECIMAL\"}","skip");assertEquals(1,report.get("rejectedRows"));assertEquals(1,((List<?>)report.get("rowErrors")).size());
        byte[] changed="name\nnew".getBytes(StandardCharsets.UTF_8);assertThrows(IllegalArgumentException.class,()->service.file(7L,null,null,"new.csv",null,changed,"replace",null));var replaced=service.file(7L,null,null,"new.csv",null,changed,"replace-structure",null);assertEquals(1,replaced.get("rowCount"));
    }
    @Test void scheduledRefreshRequiresCurrentLeaseAndUsesOptimisticSnapshotVersion()throws Exception {
        var schedules=mock(BiSourceRefreshMapper.class);var mapper=mock(BiSourceSnapshotMapper.class);var registry=new SnapshotRegistry(mapper);var provider=mock(DbDataSourceProvider.class);var cipher=new CredentialCipher("test");var client=mock(ApiSnapshotClient.class);
        var source=new BiDatasourceDO();source.setType("api");source.setStatus("0");when(provider.getRow(7L)).thenReturn(source);var json=new ObjectMapper();var data=SnapshotParser.csv("amount\n1".getBytes(StandardCharsets.UTF_8));
        when(mapper.get(7L)).thenReturn(new BiSourceSnapshotMapper.Row(7L,"api",cipher.encrypt(json.writeValueAsString(new ApiSnapshotClient.Config("https://example.test/data","",Map.of()))),json.writeValueAsString(data.columns()),json.writeValueAsString(data.rows()),1,3,null,"now"));
        when(client.fetch(any())).thenReturn("[{\"amount\":2}]".getBytes(StandardCharsets.UTF_8));var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var service=new SourceRefreshService(schedules,mapper,registry,provider,cipher,client,manager);
        try{
            assertThrows(IllegalStateException.class,()->service.refresh(7L,"expired"));verify(mapper,never()).replace(any());
            when(schedules.owns(7L,"current")).thenReturn(1);when(mapper.replace(any())).thenReturn(1);service.refresh(7L,"current");verify(mapper).replace(argThat(row->row.version()==3 && row.rowsJson().contains("2")));
            assertThrows(IllegalArgumentException.class,()->SourceRefreshService.validate(1));assertDoesNotThrow(()->SourceRefreshService.validate(0));
        }finally{service.close();}
    }
}
