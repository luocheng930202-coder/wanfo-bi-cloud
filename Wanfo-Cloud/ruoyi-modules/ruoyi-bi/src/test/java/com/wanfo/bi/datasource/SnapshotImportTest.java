package com.wanfo.bi.datasource;

import com.wanfo.bi.datasource.snapshot.*;
import com.wanfo.bi.mapper.BiSourceSnapshotMapper;
import com.wanfo.bi.domain.BiDatasourceDO;
import com.wanfo.bi.security.*;
import com.wanfo.bi.service.SnapshotImportService;
import org.springframework.transaction.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapshotImportTest {
    @Test void refreshPreservesOldSnapshotOnSchemaErrorsAndDetectsConcurrentWrites()throws Exception {
        var provider=mock(DbDataSourceProvider.class);var mapper=mock(BiSourceSnapshotMapper.class);var client=mock(ApiSnapshotClient.class);
        var resources=mock(BiResourceAuthorizer.class);var caps=mock(CapabilityProvider.class);var actors=mock(CurrentActorProvider.class);
        var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var json=new ObjectMapper();var cipher=new CredentialCipher("test-only-key");
        var data=SnapshotParser.csv("region,amount\nA,1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var config=new ApiSnapshotClient.Config("https://example.test/data","",Map.of("Authorization","Bearer private-value"));
        var old=new BiSourceSnapshotMapper.Row(7L,"api",cipher.encrypt(json.writeValueAsString(config)),json.writeValueAsString(data.columns()),json.writeValueAsString(data.rows()),1,3,null,"now");
        when(mapper.get(7L)).thenReturn(old);var registry=new SnapshotRegistry(mapper);
        var source=new BiDatasourceDO();source.setType("api");source.setId(7L);when(provider.getRow(7L)).thenReturn(source);
        var service=new SnapshotImportService(provider,mapper,registry,cipher,client,resources,caps,actors,manager);
        when(client.fetch(any())).thenReturn("[{\"region\":\"B\",\"amount\":\"bad\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,()->service.refresh(7L));verify(mapper,never()).replace(any());
        when(client.fetch(any())).thenReturn("[{\"region\":\"B\",\"amount\":2}]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(mapper.replace(any())).thenReturn(0);assertThrows(IllegalStateException.class,()->service.refresh(7L));verify(manager).rollback(any());
        when(mapper.replace(any())).thenReturn(1);assertEquals(4L,service.refresh(7L).get("version"));verify(manager).commit(any());
        String info=service.info(7L).toString();assertFalse(info.contains("private-value"));assertFalse(info.contains("enc:"));assertTrue(info.contains("headersSet=true"));
        assertThrows(IllegalArgumentException.class,()->service.api(new SnapshotImportService.ApiRequest(7L,null,null,"https://other.test/steal","",null)));
    }
    @Test void permissionDenialOccursBeforeFileParsingOrExternalAccess()throws Exception {
        var provider=mock(DbDataSourceProvider.class);var mapper=mock(BiSourceSnapshotMapper.class);var client=mock(ApiSnapshotClient.class);var resources=mock(BiResourceAuthorizer.class);var caps=mock(CapabilityProvider.class);
        var service=new SnapshotImportService(provider,mapper,new SnapshotRegistry(mapper),new CredentialCipher("test"),client,resources,caps,mock(CurrentActorProvider.class),mock(PlatformTransactionManager.class));
        doThrow(new BiAccessException(403,"denied")).when(caps).require("bi:datasource:add");
        assertThrows(BiAccessException.class,()->service.file(null,"name","alias","bad.csv",null,new byte[0]));verifyNoInteractions(provider,mapper,client);
        doThrow(new BiAccessException(403,"denied")).when(resources).require("datasource",7L,"edit");
        assertThrows(BiAccessException.class,()->service.refresh(7L));verifyNoInteractions(provider,mapper,client);
    }
}
