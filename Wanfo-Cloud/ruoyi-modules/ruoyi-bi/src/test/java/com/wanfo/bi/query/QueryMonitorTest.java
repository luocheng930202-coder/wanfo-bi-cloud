package com.wanfo.bi.query;
import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.controller.QueryMonitorController;
import com.wanfo.bi.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QueryMonitorTest {
 @Test void reportsBoundedPrivacySafeHistoryAndActualRunningState()throws Exception{var props=new BiProperties();try(var fixture=new Fixture(props)){var g=fixture.g;var run=g.admit("private-user-token",List.of(98L));assertEquals(1,g.monitor().get("active"));String snapshot=g.monitor().toString();assertFalse(snapshot.contains("private-user-token"));assertFalse(snapshot.contains("datasourceId"));assertTrue(g.cancelRequest(run.id()));assertEquals(1,g.monitor().get("active"));run.close();assertEquals(0,g.monitor().get("active"));assertTrue(g.monitor().toString().contains("QUERY_CANCELLED"));for(int i=0;i<205;i++)g.admit("user",List.of(1L)).close();assertEquals(200,((List<?>)g.monitor().get("recent")).size());}}
 @Test void administratorPermissionIsRequiredForReadAndStop(){try(var fixture=new Fixture(new BiProperties())){var controller=new QueryMonitorController(fixture.g,cap->{throw new BiAccessException(403,"denied");});assertThrows(BiAccessException.class,controller::status);assertThrows(BiAccessException.class,()->controller.cancel(new QueryMonitorController.Cancellation(UUID.randomUUID().toString())));}}
 @Test void failureAfterResourceCloseIsReflectedInRecentHistory()throws Exception{try(var fixture=new Fixture(new BiProperties())){var r=fixture.g.admit("u",List.of(1L));r.close();r.failure(new AssertionError("sensitive sql"));assertTrue(fixture.g.monitor().toString().contains("failed"));assertFalse(fixture.g.monitor().toString().contains("sensitive sql"));}}
 private static class Fixture implements AutoCloseable{final QueryGovernor g;Fixture(BiProperties p){g=new QueryGovernor(p,()->null);}public void close(){g.shutdown();}}
}
