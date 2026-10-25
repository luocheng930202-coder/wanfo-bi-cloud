package com.wanfo.bi.service;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.security.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceWorkflowTest {
    @Test void graphDoesNotRevealUnauthorizedNodesOrDraftBindings(){
        var mapper=mock(BiResourceGraphMapper.class);var resources=mock(BiResourceAuthorizer.class);var caps=mock(CapabilityProvider.class);
        when(caps.has(anyString())).thenReturn(true);when(caps.has("bi:dashboard:edit")).thenReturn(false);when(caps.has("bi:dashboard:publish")).thenReturn(false);
        when(resources.allowed(anyString(),anyLong(),eq("view"))).thenReturn(true);when(resources.allowed("chart",99L,"view")).thenReturn(false);
        when(mapper.nodes()).thenReturn(List.of(new BiResourceGraphMapper.Node("dataset",1L,"Data",null,false),new BiResourceGraphMapper.Node("chart",2L,"Chart",null,false),new BiResourceGraphMapper.Node("chart",99L,"SECRET",null,false),new BiResourceGraphMapper.Node("dashboard",3L,"PRIVATE_DRAFT","Public title",true)));
        when(mapper.edges()).thenReturn(List.of(new BiResourceGraphMapper.Edge("dataset",1L,"chart",2L,"binding"),new BiResourceGraphMapper.Edge("dataset",1L,"chart",99L,"binding"),new BiResourceGraphMapper.Edge("chart",2L,"dashboard",3L,"draft")));
        var service=new ResourceImpactService(mapper,resources,caps);String result=service.graph("dataset",1L).toString();
        assertFalse(result.contains("SECRET"));assertFalse(result.contains("PRIVATE_DRAFT"));assertFalse(result.contains("dashboard:3"));assertTrue(result.contains("chart:2"));
        when(mapper.edges()).thenReturn(List.of(new BiResourceGraphMapper.Edge("dataset",1L,"chart",2L,"binding"),new BiResourceGraphMapper.Edge("chart",2L,"dashboard",3L,"published")));
        result=service.graph("dataset",1L).toString();assertTrue(result.contains("Public title"));assertFalse(result.contains("PRIVATE_DRAFT"));
    }
    @Test void deniedGraphAccessCannotQueryMetadata(){
        var mapper=mock(BiResourceGraphMapper.class);var caps=mock(CapabilityProvider.class);doThrow(new BiAccessException(403,"denied")).when(caps).require("bi:chart:list");
        assertThrows(BiAccessException.class,()->new ResourceImpactService(mapper,mock(BiResourceAuthorizer.class),caps).graph("chart",1L));verifyNoInteractions(mapper);
    }
    @Test void historicalRestoreUsesCurrentValidatedSaveAndDoesNotPublish(){
        var mapper=mock(BiResourceHistoryMapper.class);var charts=mock(ChartDefinitionService.class);var dashboards=mock(DashboardService.class);var resources=mock(BiResourceAuthorizer.class);var caps=mock(CapabilityProvider.class);
        var json=new ObjectMapper();var service=new ResourceHistoryService(mapper,charts,dashboards,resources,caps,json);
        var definition=new DashboardService.Definition(1,"Earlier",List.of());
        when(mapper.get("dashboard",7L,1)).thenReturn(new BiResourceHistoryMapper.Row("dashboard",7L,1,"save",json.writeValueAsString(definition),"author","now"));
        when(dashboards.update(eq(7L),any())).thenReturn(Map.of("version",6));
        assertEquals(6,service.restore("dashboard",7L,1,5).get("version"));
        var argument=org.mockito.ArgumentCaptor.forClass(DashboardService.SaveRequest.class);verify(dashboards).update(eq(7L),argument.capture());assertEquals(5,argument.getValue().version());assertEquals("Earlier",argument.getValue().definition().name());
        verify(dashboards,never()).publish(any(),any());
        doThrow(new BiAccessException(403,"denied")).when(resources).require("dashboard",7L,"edit");
        assertThrows(BiAccessException.class,()->service.detail("dashboard",7L,1));
    }
}
