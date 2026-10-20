package com.wanfo.bi.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.wanfo.bi.domain.BiDashboardShareDO;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.security.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import tools.jackson.databind.ObjectMapper;

class SensitiveIdentityRegressionTest {
    final BiActor stale=new BiActor("ruoyi",1L,"old",3L,Set.of(7L));
    final BiActor fresh=new BiActor("ruoyi",1L,"current",4L,Set.of(8L));
    final CurrentActorProvider actors=()->ActorScope.active()?ActorScope.actor():stale;
    final CapabilityProvider caps=p->{if(ActorScope.active()&&!ActorScope.allows(p))throw new BiAccessException(403,"denied");};
    final ObjectMapper json=new ObjectMapper();
    final LiveActorResolver resolver=mock(LiveActorResolver.class);
    final BiExportJobMapper jobs=mock(BiExportJobMapper.class);
    final ChartDefinitionService charts=mock(ChartDefinitionService.class);
    final DashboardService boards=mock(DashboardService.class);
    ExportJobService exports(java.util.concurrent.Executor executor) {return new ExportJobService(jobs,charts,boards,caps,actors,json,executor,resolver);}
    void chart() throws Exception {
        when(charts.detail(9L)).thenReturn(Map.of("definition",new ChartDefinitionService.Definition(1,"test",null,null)));
        when(charts.data(9L)).thenReturn(Map.of("columns",List.of("amount"),"rows",List.of(Map.of("amount",10))));
    }
    Map<String,Object> complete(ExportJobService service) throws Exception {
        chart();var inserted=new AtomicReference<Map<String,Object>>();var finished=new AtomicReference<Map<String,Object>>();
        doAnswer(i->{Map<String,Object> r=i.getArgument(0);r.put("id",2L);inserted.set(r);return 1;}).when(jobs).insert(any());
        when(jobs.get(2L)).thenAnswer(i->Map.of("name","test","requestJson",inserted.get().get("requestJson")));
        when(jobs.claim(2L)).thenReturn(1);
        doAnswer(i->{finished.set(i.getArgument(0));return 1;}).when(jobs).finish(any());
        service.submit(new ExportJobService.Submit("chart",9L,null,null,"csv","test",null));
        var output=finished.get();assertEquals("succeeded",output.get("status"));
        var file=new HashMap<String,Object>(output);file.put("ownerPrincipal",1L);file.put("name","test");file.put("requestJson",output.get("validationJson"));file.put("expireTime",new Date(System.currentTimeMillis()+60000));
        when(jobs.getFile(2L)).thenReturn(file);return file;
    }
    @Test void unchangedExportDownloadsButChangedDataOrRolesRequireNewExport() throws Exception {
        when(resolver.resolve(any())).thenReturn(new LiveActorResolver.Snapshot(fresh,Set.of("*:*:*")));
        var service=exports(Runnable::run);complete(service);assertTrue(service.download(2L).bytes().length>0);
        when(charts.data(9L)).thenReturn(Map.of("columns",List.of("amount"),"rows",List.of(Map.of("amount",11))));
        assertEquals(409,assertThrows(BiAccessException.class,()->service.download(2L)).getCode());
        chart();when(resolver.resolve(any())).thenReturn(new LiveActorResolver.Snapshot(stale,Set.of("*:*:*")));
        assertEquals(409,assertThrows(BiAccessException.class,()->service.download(2L)).getCode());assertFalse(ActorScope.active());
    }
    @Test void sourceRevocationAndDisabledIdentityBlockExistingFile() throws Exception {
        when(resolver.resolve(any())).thenReturn(new LiveActorResolver.Snapshot(fresh,Set.of("*:*:*")));
        var service=exports(Runnable::run);complete(service);
        when(charts.data(9L)).thenThrow(new BiAccessException(403,"revoked"));
        assertEquals(403,assertThrows(BiAccessException.class,()->service.download(2L)).getCode());
        clearInvocations(jobs,charts);when(resolver.resolve(any())).thenThrow(new BiAccessException(403,"disabled"));
        assertThrows(BiAccessException.class,()->service.download(2L));verifyNoInteractions(jobs,charts);
    }
    @Test void legacyFilesWithoutValidationAreRejected() throws Exception {
        when(resolver.resolve(any())).thenReturn(new LiveActorResolver.Snapshot(fresh,Set.of("*:*:*")));
        var service=exports(Runnable::run);var file=complete(service);file.put("requestJson","{}");
        assertEquals(409,assertThrows(BiAccessException.class,()->service.download(2L)).getCode());
    }
    @Test void queuedWorkerResolvesIdentityAtExecutionTime() throws Exception {
        chart();var task=new AtomicReference<Runnable>();var service=exports(task::set);
        doAnswer(i->{((Map<String,Object>)i.getArgument(0)).put("id",2L);return 1;}).when(jobs).insert(any());
        when(resolver.resolve(stale)).thenReturn(new LiveActorResolver.Snapshot(stale,Set.of("*:*:*")));
        service.submit(new ExportJobService.Submit("chart",9L,null,null,"csv","test",null));clearInvocations(resolver);
        when(resolver.resolve(stale)).thenThrow(new BiAccessException(403,"disabled"));when(jobs.claim(2L)).thenReturn(1);task.get().run();
        verify(charts,never()).data(anyLong());verify(jobs).finish(argThat(r->"failed".equals(r.get("status"))));assertFalse(ActorScope.active());
    }
    DashboardShareService shares(String scope) {
        var mapper=mock(BiDashboardShareMapper.class);var row=new BiDashboardShareDO();row.setDashboardId(5L);row.setDataScope(scope);row.setPublisherActorJson(json.writeValueAsString(stale));
        when(mapper.selectByToken("token")).thenReturn(row);
        return new DashboardShareService(mapper,boards,mock(BiResourceAuthorizer.class),caps,actors,json,resolver);
    }
    @Test void publisherShareUsesCurrentRolesAndRestoresCallerAfterFailure() throws Exception {
        var service=shares("publisher");when(resolver.resolve(stale)).thenReturn(new LiveActorResolver.Snapshot(fresh,Set.of("bi:dashboard:publish","bi:dashboard:consume")));
        when(boards.shareView(5L)).thenAnswer(i->{assertEquals(fresh,actors.currentActor());throw new BiAccessException(403,"revoked dataset");});
        assertThrows(BiAccessException.class,()->service.view("token",null));assertEquals(stale,actors.currentActor());assertFalse(ActorScope.active());
    }
    @Test void publisherLosingPublishCapabilityCannotServeShare() throws Exception {
        var service=shares("publisher");when(resolver.resolve(stale)).thenReturn(new LiveActorResolver.Snapshot(fresh,Set.of("bi:dashboard:consume")));
        assertThrows(BiAccessException.class,()->service.view("token",null));verifyNoInteractions(boards);
    }
    @Test void viewerShareKeepsCallerIdentity() throws Exception {
        var service=shares("viewer");when(resolver.resolve(stale)).thenReturn(new LiveActorResolver.Snapshot(fresh,Set.of("bi:dashboard:consume")));when(boards.shareView(5L)).thenAnswer(i->{assertEquals(fresh,actors.currentActor());return new LinkedHashMap<>();});
        service.view("token",null);verify(resolver,atLeastOnce()).resolve(stale);assertFalse(ActorScope.active());
    }
}
