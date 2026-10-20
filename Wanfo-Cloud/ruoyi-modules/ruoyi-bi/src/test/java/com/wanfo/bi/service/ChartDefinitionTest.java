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

class ChartDefinitionTest {
    BiChartMapper mapper=mock(BiChartMapper.class);
    BiDatasetTableMapper datasets=mock(BiDatasetTableMapper.class);
    ChartQueryService queries=mock(ChartQueryService.class);
    BiResourceAuthorizer resources=mock(BiResourceAuthorizer.class);
    CapabilityProvider caps=mock(CapabilityProvider.class);
    JsonMapper json=JsonMapper.builder().build();
    ChartTemplateLeaseService templateLeases=mock(ChartTemplateLeaseService.class);
    ChartDefinitionService service=new ChartDefinitionService(mapper,datasets,queries,resources,caps,()->new BiActor("test",1L,"alice",null,Set.of()),json,templateLeases);
    ChartDefinitionTest(){
        var ds=new BiDatasetTableDO();ds.setType("table");when(datasets.selectById(9L)).thenReturn(ds);
        when(queries.datasetFields(9L)).thenReturn(List.of(Map.of("name","city","type","VARCHAR"),Map.of("name","id","type","INT")));
    }
    ChartDefinitionService.Definition definition(){
        var q=new QueryModel();q.setDatasetId(9L);q.addDimension("city","city");q.addMetric("id","COUNT","n");q.setLimit(100);
        return new ChartDefinitionService.Definition(1,"Demo","bar",q);
    }
    BiChartDO stored(){var row=new BiChartDO();row.setId(7L);row.setDatasetId(9L);row.setName("Demo");row.setType("bar");row.setVersion(2);row.setDefinitionJson(json.writeValueAsString(definition()));when(mapper.get(7L)).thenReturn(row);return row;}
    @Test void creationValidatesAndPersistsDefinitionWithRealOwner(){
        doAnswer(c->{((BiChartDO)c.getArgument(0)).setId(7L);return 1;}).when(mapper).insert(any());
        var r=service.create(new ChartDefinitionService.SaveRequest(null,definition(),null));assertEquals(7L,r.get("id"));
        verify(resources).register("chart",7L);verify(queries).compileOnly(any());
        var capture=org.mockito.ArgumentCaptor.forClass(BiChartDO.class);verify(mapper).insert(capture.capture());assertEquals("alice",capture.getValue().getCreateBy());assertFalse(capture.getValue().getDefinitionJson().contains("rows"));
    }
    @Test void invalidSchemaCannotPersist(){
        var d=definition();assertThrows(IllegalArgumentException.class,()->service.create(new ChartDefinitionService.SaveRequest(null,new ChartDefinitionService.Definition(2,d.name(),d.type(),d.query()),null)));verify(mapper,never()).insert(any());
    }
    @Test void savedQueryRequiresChartAccessBeforeReadingDefinition() throws Exception {
        doThrow(new BiAccessException(403,"denied")).when(resources).require("chart",7L,"query");
        assertThrows(BiAccessException.class,()->service.data(7L));verifyNoInteractions(mapper,queries);
    }
    @Test void chartGrantDoesNotImplyDatasetQueryAccess() throws Exception {
        stored();doThrow(new BiAccessException(403,"denied")).when(resources).require("dataset",9L,"query");
        assertThrows(BiAccessException.class,()->service.detail(7L));verify(queries,never()).query(any());
    }
    @Test void savedDataUsesStoredModelAndViewerQueryService() throws Exception {
        stored();when(queries.query(any())).thenReturn(Map.of("rows",List.of()));service.data(7L);
        var capture=org.mockito.ArgumentCaptor.forClass(QueryModel.class);verify(queries).query(capture.capture());assertEquals(9L,capture.getValue().getDatasetId());assertEquals("city",capture.getValue().getDimensions().get(0).getField());
    }
    @Test void staleUpdateFailsWithoutOverwriting(){
        stored();when(mapper.update(any())).thenReturn(0);
        var e=assertThrows(BiAccessException.class,()->service.update(7L,new ChartDefinitionService.SaveRequest(1,definition(),null)));assertEquals(409,e.getCode());
    }
    @Test void successfulUpdateAdvancesVersionAndRecordsActor(){
        stored();when(mapper.update(any())).thenReturn(1);var r=service.update(7L,new ChartDefinitionService.SaveRequest(2,definition(),null));assertEquals(3,r.get("version"));
        var capture=org.mockito.ArgumentCaptor.forClass(BiChartDO.class);verify(mapper).update(capture.capture());assertEquals("alice",capture.getValue().getUpdateBy());assertEquals(2,capture.getValue().getVersion());
    }
    @Test void pieRequiresOneMetric(){
        var d=definition();d.query().addMetric("id","SUM","s");assertThrows(IllegalArgumentException.class,()->service.create(new ChartDefinitionService.SaveRequest(null,new ChartDefinitionService.Definition(1,"Pie","pie",d.query()),null)));verify(mapper,never()).insert(any());
    }
    @Test void textMetricRejectedForGraphButAllowedForTable(){
        var d=definition();d.query().getMetrics().clear();d.query().addMetric("city","NONE","value");
        assertThrows(IllegalArgumentException.class,()->service.create(new ChartDefinitionService.SaveRequest(null,d,null)));verify(mapper,never()).insert(any());
    }
    @Test void tamperedStoredDatasetBindingRejected(){
        var row=stored();row.setDatasetId(99L);assertThrows(IllegalArgumentException.class,()->service.detail(7L));
    }
    @Test void listRequiresChartAndDatasetPermissions(){
        var row=stored();when(mapper.list()).thenReturn(List.of(row));when(resources.allowed("chart",7L,"view")).thenReturn(true);
        assertTrue(service.list().isEmpty());when(caps.has("bi:dataset:query")).thenReturn(true);when(resources.allowed("dataset",9L,"view")).thenReturn(true);when(resources.allowed("dataset",9L,"query")).thenReturn(true);assertEquals(1,service.list().size());
    }
    @Test void referencedChartCannotBeDeleted(){
        stored();when(mapper.delete(7L,"alice")).thenReturn(0);assertThrows(IllegalArgumentException.class,()->service.delete(7L));
    }
    ChartDefinitionService.PanelConfig config(QueryModel.FilterNode tree){return new ChartDefinitionService.PanelConfig("bar",List.of(new QueryModel.Dimension("city","area")),List.of(new QueryModel.Metric("id","SUM","total")),List.of(new QueryModel.OrderBy("total","desc")),20,tree);}
    @Test void componentConfigPreservesSourceDatasetAndBothPredicateFormsWithoutMutation(){
        var d=definition();d.query().addFilter("city","NE","hidden");var original=new QueryModel.FilterNode();original.setField("city");original.setOp("NE");original.setValue("private");d.query().setFilterTree(original);
        var extra=new QueryModel.FilterNode();extra.setField("id");extra.setOp("GT");extra.setValue(2);
        String before=json.writeValueAsString(d);var result=service.configureForDashboard(d,config(extra));
        assertEquals(before,json.writeValueAsString(d));assertEquals(9L,result.query().getDatasetId());assertEquals("hidden",result.query().getFilters().get(0).getValue());
        assertEquals("AND",result.query().getFilterTree().getLogic());assertEquals("private",result.query().getFilterTree().getChildren().get(0).getValue());assertEquals(2,result.query().getFilterTree().getChildren().get(1).getValue());
        assertEquals("SUM",result.query().getMetrics().get(0).getAgg());assertEquals(20,result.query().getLimit());assertEquals("desc",result.query().getOrderBys().get(0).getOrder());verify(queries).compileOnly(result.query());verifyNoInteractions(mapper,resources);
        extra.setValue(99);assertEquals(2,result.query().getFilterTree().getChildren().get(1).getValue());
    }
    @Test void clearingComponentFiltersStillRetainsSourceFilters(){var d=definition();d.query().addFilter("city","EQ","Shanghai");var result=service.configureForDashboard(d,config(null));assertEquals("Shanghai",result.query().getFilters().get(0).getValue());}
    @Test void componentConfigRequiresBoundedCompleteShape(){var d=definition();assertThrows(IllegalArgumentException.class,()->service.configureForDashboard(d,new ChartDefinitionService.PanelConfig("bar",null,List.of(),List.of(),100,null)));assertThrows(IllegalArgumentException.class,()->service.configureForDashboard(d,new ChartDefinitionService.PanelConfig("bar",List.of(),List.of(),List.of(),1001,null)));}
    @Test void componentConfigUsesCurrentVisibleFieldCompiler(){doThrow(new IllegalArgumentException("hidden field")).when(queries).compileOnly(any());assertThrows(IllegalArgumentException.class,()->service.configureForDashboard(definition(),config(null)));}
    @Test void componentGraphShapeIsValidated(){var c=config(null);assertThrows(IllegalArgumentException.class,()->service.configureForDashboard(definition(),new ChartDefinitionService.PanelConfig("bar",List.of(),c.metrics(),c.orderBys(),10,null)));}
    @Test void oldPanelJsonStillUsesSharedSource(){var p=json.readValue("{\"key\":\"old\",\"chartId\":7,\"title\":\"Old\",\"x\":0,\"y\":0,\"w\":6,\"h\":4}",DashboardService.Panel.class);assertNull(p.chartConfig());assertFalse(p.locked());}
}
