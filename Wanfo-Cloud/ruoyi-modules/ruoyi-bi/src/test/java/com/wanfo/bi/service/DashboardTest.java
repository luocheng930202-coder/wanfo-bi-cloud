package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.*;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.security.*;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardTest {
    BiDashboardMapper mapper=mock(BiDashboardMapper.class);
    ChartDefinitionService charts=mock(ChartDefinitionService.class);
    ChartQueryService queries=mock(ChartQueryService.class);
    BiResourceAuthorizer resources=mock(BiResourceAuthorizer.class);
    CapabilityProvider caps=mock(CapabilityProvider.class);
    JsonMapper json=JsonMapper.builder().build();
    DashboardService service=new DashboardService(mapper,charts,queries,resources,caps,()->new BiActor("test",1L,"author",null,Set.of()),json);
    DashboardService.Panel panel(String key,int x,int y){return new DashboardService.Panel(key,4L,"Card",x,y,6,8);}
    DashboardService.Definition definition(List<DashboardService.Panel> panels){return new DashboardService.Definition(1,"Public",panels);}
    ChartDefinitionService.Definition chart(){var q=new QueryModel();q.setDatasetId(9L);q.addDimension("city","place");q.addMetric("id","COUNT","n");q.addFilter("city","EQ","private-filter");return new ChartDefinitionService.Definition(1,"Chart","bar",q);}
    BiDashboardDO stored(boolean published){
        var c=chart();when(charts.exportForDashboard(4L)).thenReturn(c);
        var row=new BiDashboardDO();row.setId(7L);row.setVersion(2);row.setName("Unpublished secret name");row.setDefinitionJson(json.writeValueAsString(definition(List.of(panel("a",0,0)))));
        if(published){row.setPublishedJson(json.writeValueAsString(new DashboardService.Publication(1,"Public",List.of(new DashboardService.PublishedPanel(panel("a",0,0),c)))));row.setPublishedVersion(1);}
        when(mapper.get(7L)).thenReturn(row);return row;
    }
    @Test void newDraftRecordsActorAndProtectsReferences(){
        when(charts.exportForDashboard(4L)).thenReturn(chart());doAnswer(c->{((BiDashboardDO)c.getArgument(0)).setId(7L);return 1;}).when(mapper).insert(any());
        service.create(new DashboardService.SaveRequest(null,definition(List.of(panel("a",0,0)))));
        verify(resources).register("dashboard",7L);verify(mapper).bind(7L,4L,9L,"draft");var capture=org.mockito.ArgumentCaptor.forClass(BiDashboardDO.class);verify(mapper).insert(capture.capture());assertEquals("author",capture.getValue().getCreateBy());
    }
    @Test void invalidAndOverlappingLayoutsCannotPersist(){
        when(charts.exportForDashboard(4L)).thenReturn(chart());
        for(var panels:List.of(List.of(panel("a",0,0),panel("a",6,0)),List.of(panel("a",0,0),panel("b",3,0)),List.of(panel("a",Integer.MAX_VALUE,0))))
            assertThrows(IllegalArgumentException.class,()->service.create(new DashboardService.SaveRequest(null,definition(panels))));
        verify(mapper,never()).insert(any());
    }
    @Test void inaccessibleChartCannotBeBound(){
        when(charts.exportForDashboard(4L)).thenThrow(new BiAccessException(403,"denied"));assertThrows(BiAccessException.class,()->service.create(new DashboardService.SaveRequest(null,definition(List.of(panel("a",0,0))))));verifyNoInteractions(mapper);
    }
    @Test void staleSaveDoesNotReplaceReferences(){
        stored(false);when(mapper.update(any())).thenReturn(0);var e=assertThrows(BiAccessException.class,()->service.update(7L,new DashboardService.SaveRequest(1,definition(List.of(panel("a",0,0))))));assertEquals(409,e.getCode());verify(mapper,never()).clearBindings(any(),any());
    }
    @Test void publishRevalidatesReferencesAndCopiesDefinition(){
        stored(false);when(mapper.publish(eq(7L),eq(2),eq(3),anyString(),eq("author"))).thenReturn(1);
        service.publish(7L,2);verify(charts).exportForDashboard(4L);verify(mapper).bind(7L,4L,9L,"published");
        var snapshot=org.mockito.ArgumentCaptor.forClass(String.class);verify(mapper).publish(eq(7L),eq(2),eq(3),snapshot.capture(),eq("author"));assertTrue(snapshot.getValue().contains("private-filter"));
    }
    @Test void emptyDraftCannotBePublished(){
        var row=stored(false);row.setDefinitionJson(json.writeValueAsString(definition(List.of())));assertThrows(IllegalArgumentException.class,()->service.publish(7L,2));verify(mapper,never()).publish(any(),anyInt(),anyInt(),anyString(),anyString());
    }
    @Test void stalePublishRejectedBeforeExport(){
        stored(false);assertEquals(409,assertThrows(BiAccessException.class,()->service.publish(7L,1)).getCode());verify(charts,never()).exportForDashboard(any());
    }
    @Test void unsharedConsumptionRejectedBeforeReadingSnapshot() throws Exception {
        doThrow(new BiAccessException(403,"denied")).when(resources).require("dashboard",7L,"consume");assertThrows(BiAccessException.class,()->service.panelData(7L,"a",1));verifyNoInteractions(mapper,queries);
    }
    @Test void unpublicationAndWrongRevisionPreventQueryExecution(){
        var row=stored(false);assertThrows(BiAccessException.class,()->service.panelData(7L,"a",1));row=stored(true);assertEquals(409,assertThrows(BiAccessException.class,()->service.panelData(7L,"a",2)).getCode());verifyNoInteractions(queries);
    }
    @Test void guessedPanelCannotExecuteArbitraryModel(){
        stored(true);assertEquals(403,assertThrows(BiAccessException.class,()->service.panelData(7L,"other",1)).getCode());verifyNoInteractions(queries);
    }
    @Test void consumerManifestContainsNoQueryDatasetOrChartIds(){
        stored(true);String output=json.writeValueAsString(service.view(7L));assertFalse(output.contains("private-filter"));assertFalse(output.contains("datasetId"));assertFalse(output.contains("chartId"));assertFalse(output.contains("query"));assertTrue(output.contains("Public"));
    }
    @Test void consumerDataUsesOnlyFrozenModelAndReturnsPresentation() throws Exception {
        var row=stored(true);var source=chart();source.query().getFilters().get(0).setValue("new-secret");when(charts.exportForDashboard(4L)).thenReturn(source);
        when(queries.queryForDashboard(any())).thenReturn(Map.of("columns",List.of("place","n"),"rows",List.of()));
        var result=service.panelData(7L,"a",1);var capture=org.mockito.ArgumentCaptor.forClass(QueryModel.class);verify(queries).queryForDashboard(capture.capture());assertEquals("private-filter",capture.getValue().getFilters().get(0).getValue());assertFalse(json.writeValueAsString(result).contains("private-filter"));verify(charts,never()).exportForDashboard(any());
    }
    @Test void pureViewerDoesNotSeeDraftNameOrUnpublishedBoards(){
        var row=stored(true);when(mapper.list()).thenReturn(List.of(row));when(resources.allowed("dashboard",7L,"view")).thenReturn(true);
        var list=service.list();assertEquals("Public",list.get(0).get("name"));assertEquals(1,list.get(0).get("version"));row.setPublishedJson(null);assertTrue(service.list().isEmpty());
    }
    @Test void unpublishClearsOnlyPublicationBindings(){
        stored(true);when(mapper.unpublish(7L,2,"author")).thenReturn(1);service.unpublish(7L,2);verify(mapper).clearBindings(7L,"published");verify(mapper,never()).clearBindings(7L,"draft");
    }
    @Test void deletingBoardReleasesBothKindsOfReferences(){
        stored(true);when(mapper.delete(7L,"author")).thenReturn(1);service.delete(7L);verify(mapper).clearBindings(7L,"draft");verify(mapper).clearBindings(7L,"published");
    }
    @Test void overflowVersionCannotBeSaved(){
        stored(false);assertThrows(IllegalArgumentException.class,()->service.update(7L,new DashboardService.SaveRequest(Integer.MAX_VALUE,definition(List.of()))));verify(mapper,never()).update(any());
    }
    @Test void legacyJsonReadsWithDefaultsWithoutRewritingStorage(){
        var row=stored(true);
        row.setDefinitionJson("{\"schemaVersion\":1,\"name\":\"Legacy\",\"panels\":[{\"key\":\"a\",\"chartId\":4,\"title\":\"Old\",\"x\":0,\"y\":0,\"w\":6,\"h\":8}]}");
        String original=row.getDefinitionJson();var draft=(DashboardService.Definition)service.draft(7L).get("definition");
        assertEquals("dashboard",draft.kind());assertEquals("grid",draft.layout());assertEquals("light",draft.appearance().theme());
        assertEquals(new DashboardPresentation.PanelStyle(true,"left"),draft.panels().get(0).style());
        assertEquals(original,row.getDefinitionJson());verify(mapper,never()).update(any());
        assertEquals("light",((DashboardPresentation.Appearance)service.view(7L).get("appearance")).theme());
    }
    @Test void saveRoundTripsControlledAppearanceAndPanelStyle(){
        stored(false);when(mapper.update(any())).thenReturn(1);
        var p=new DashboardService.Panel("a",4L,"Hidden",0,0,6,8,"chart",new DashboardPresentation.PanelStyle(false,"center"));
        var d=new DashboardService.Definition(1,"Styled",List.of(p),"dashboard","grid",DashboardPresentation.defaults("dark"));
        service.update(7L,new DashboardService.SaveRequest(2,d));
        var capture=org.mockito.ArgumentCaptor.forClass(BiDashboardDO.class);verify(mapper).update(capture.capture());
        var saved=json.readValue(capture.getValue().getDefinitionJson(),DashboardService.Definition.class);
        assertEquals(d,saved);verify(mapper).bind(7L,4L,9L,"draft");
    }
    @Test void publicationFreezesAppearanceAndDoesNotUseLaterDraftStyles(){
        var row=stored(true);var dark=DashboardPresentation.defaults("dark");
        row.setDefinitionJson(json.writeValueAsString(new DashboardService.Definition(1,"Dark draft",List.of(panel("a",0,0)),"dashboard","grid",dark)));
        assertEquals("light",((DashboardPresentation.Appearance)service.view(7L).get("appearance")).theme());
        when(mapper.publish(eq(7L),eq(2),eq(3),anyString(),eq("author"))).thenAnswer(inv->{row.setPublishedJson(inv.getArgument(3));row.setPublishedVersion(3);return 1;});
        service.publish(7L,2);assertEquals(dark,service.view(7L).get("appearance"));
        row.setDefinitionJson(json.writeValueAsString(definition(List.of(panel("a",0,0)))));
        assertEquals(dark,service.view(7L).get("appearance"));
    }
    @Test void unsupportedCarrierLayoutComponentAndVersionCannotPersist(){
        var p=panel("a",0,0);
        for(var d:List.of(new DashboardService.Definition(2,"Bad",List.of(p)),
                new DashboardService.Definition(1,"Bad",List.of(p),"screen","grid",null),
                new DashboardService.Definition(1,"Bad",List.of(p),"dashboard","free",null),
                new DashboardService.Definition(1,"Bad",List.of(new DashboardService.Panel("a",4L,"Bad",0,0,6,8,"html",null)))))
            assertThrows(IllegalArgumentException.class,()->service.create(new DashboardService.SaveRequest(null,d)));
        verifyNoInteractions(mapper,charts);
    }
    @Test void unsafeAndOutOfRangePresentationValuesCannotPersist(){
        for(var a:List.of(
                new DashboardPresentation.Appearance("dark","url(https://example.com)",null,null,null,null,null,null,null,null),
                new DashboardPresentation.Appearance("unknown",null,null,null,null,null,null,null,null,null),
                new DashboardPresentation.Appearance(null,null,null,null,null,"injected",null,null,null,null),
                new DashboardPresentation.Appearance(null,null,null,null,null,null,25,null,null,null),
                new DashboardPresentation.Appearance(null,null,null,null,null,null,null,-1,null,null),
                new DashboardPresentation.Appearance(null,null,null,null,null,null,null,null,7,null))) {
            var d=new DashboardService.Definition(1,"Bad",List.of(panel("a",0,0)),null,null,a);
            assertThrows(IllegalArgumentException.class,()->service.create(new DashboardService.SaveRequest(null,d)));
        }
        var p=new DashboardService.Panel("a",4L,"Bad",0,0,6,8,null,new DashboardPresentation.PanelStyle(true,"absolute"));
        assertThrows(IllegalArgumentException.class,()->service.create(new DashboardService.SaveRequest(null,definition(List.of(p)))));
        verifyNoInteractions(mapper,charts);
    }
}
