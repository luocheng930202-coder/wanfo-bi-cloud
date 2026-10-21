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
    @Test void savedDetailRoundTripsChartSpecificStyle() {
        var d=definition();var style=new com.wanfo.bi.domain.chart.ChartVisualStyle();style.setBarMaxWidth(28);style.setBarRadius(6);style.setShowLabels(true);
        var styled=new ChartDefinitionService.Definition(1,d.name(),d.type(),d.query(),style,null);
        doAnswer(c->{((BiChartDO)c.getArgument(0)).setId(7L);return 1;}).when(mapper).insert(any());
        service.create(new ChartDefinitionService.SaveRequest(null,styled,null));
        var capture=org.mockito.ArgumentCaptor.forClass(BiChartDO.class);verify(mapper).insert(capture.capture());
        var row=capture.getValue();row.setId(7L);row.setVersion(0);when(mapper.get(7L)).thenReturn(row);
        var restored=(ChartDefinitionService.Definition)service.detail(7L).get("definition");
        assertEquals(28,restored.style().getBarMaxWidth());assertEquals(6,restored.style().getBarRadius());assertTrue(restored.style().getShowLabels());
    }
    @Test void dashboardOverridesPreserveSavedVisualStyle() {
        var d=definition();var style=new com.wanfo.bi.domain.chart.ChartVisualStyle();style.setBarRadius(8);style.setShowLabels(true);
        var source=new ChartDefinitionService.Definition(1,d.name(),d.type(),d.query(),style,null);
        var q=d.query();
        var config=new ChartDefinitionService.PanelConfig("bar",q.getDimensions(),q.getMetrics(),List.of(),100,null);
        var adjusted=service.configureForDashboard(source,config);
        assertEquals(8,adjusted.style().getBarRadius());assertTrue(adjusted.style().getShowLabels());
        assertNotSame(q,adjusted.query());
    }
    @Test void calendarGrainRoundTripsAndDashboardOverrideIsIndependent() {
        var d=definition();d.query().getDimensions().get(0).setField("event_time");
        d.query().getDimensions().get(0).setTimeGrain("month");d.query().getDimensions().get(0).setTimeFill("zero");
        doAnswer(c->{((BiChartDO)c.getArgument(0)).setId(7L);return 1;}).when(mapper).insert(any());
        service.create(new ChartDefinitionService.SaveRequest(null,d,null));
        var capture=org.mockito.ArgumentCaptor.forClass(BiChartDO.class);verify(mapper).insert(capture.capture());
        var row=capture.getValue();row.setId(7L);row.setVersion(0);when(mapper.get(7L)).thenReturn(row);
        var restored=(ChartDefinitionService.Definition)service.detail(7L).get("definition");
        assertEquals("month",restored.query().getDimensions().get(0).getTimeGrain());assertEquals("zero",restored.query().getDimensions().get(0).getTimeFill());
        var config=new ChartDefinitionService.PanelConfig("line",restored.query().getDimensions(),restored.query().getMetrics(),List.of(),100,null);
        var adjusted=service.configureForDashboard(restored,config);
        assertEquals("month",adjusted.query().getDimensions().get(0).getTimeGrain());
        adjusted.query().getDimensions().get(0).setTimeGrain("year");assertEquals("month",restored.query().getDimensions().get(0).getTimeGrain());
    }
    @Test void calendarBucketsCannotSilentlyBecomeRegionOrEqualityDrillKeys() {
        var d=definition();d.query().getDimensions().get(0).setTimeGrain("month");
        assertThrows(IllegalArgumentException.class,()->service.create(new ChartDefinitionService.SaveRequest(null,new ChartDefinitionService.Definition(1,d.name(),"map",d.query()),null)));
        var advanced=new com.wanfo.bi.domain.chart.ChartAdvancedOptions();advanced.setDrillFields(List.of("city","district"));
        assertThrows(IllegalArgumentException.class,()->service.create(new ChartDefinitionService.SaveRequest(null,new ChartDefinitionService.Definition(1,d.name(),"line",d.query(),null,advanced),null)));
        verify(mapper,never()).insert(any());
    }
    @Test void savedMapQueriesReaggregateAtChildFieldThroughTheSameViewerQueryService() throws Exception {
        var q=new QueryModel();q.setDatasetId(9L);q.addDimension("province","province");q.addMetric("id","COUNT","n");
        var a=new com.wanfo.bi.domain.chart.ChartAdvancedOptions();a.setMapFields(new com.wanfo.bi.domain.chart.ChartAdvancedOptions.MapFields("province","city",null));
        var d=new ChartDefinitionService.Definition(1,"地图","map",q,null,a);
        var row=stored();row.setDefinitionJson(json.writeValueAsString(d));
        when(queries.datasetFields(9L)).thenReturn(List.of(Map.of("name","province","type","VARCHAR"),Map.of("name","city","type","VARCHAR"),Map.of("name","id","type","INT")));
        when(queries.query(any())).thenReturn(Map.of("columns",List.of("city","n"),"rows",List.of()));
        var result=service.data(7L,Map.of("drills",Map.of("chart",List.of(Map.of("field","province","value","浙江省")))));
        var capture=org.mockito.ArgumentCaptor.forClass(QueryModel.class);verify(queries).query(capture.capture());
        assertEquals("city",capture.getValue().getDimensions().get(0).getField());assertEquals(9L,capture.getValue().getDatasetId());
        assertTrue(capture.getValue().getFilters().stream().anyMatch(f->"province".equals(f.getField()) && "浙江省".equals(f.getValue())));
        assertEquals("330000",((Map<?,?>)result.get("mapContext")).get("scope"));
        verify(resources).require("chart",7L,"query");verify(resources).require("dataset",9L,"query");
    }
    @Test void dashboardOverrideDoesNotCarryMetricFormatOntoReusedAlias() {
        var d=definition();var old=d.query().getMetrics().get(0);
        var key=old.getAlias()!=null?old.getAlias():old.getAgg().toLowerCase()+"_"+old.getField();
        var style=new com.wanfo.bi.domain.chart.ChartVisualStyle();
        style.setMetrics(List.of(new com.wanfo.bi.domain.chart.ChartVisualStyle.MetricStyle(key,null,"line","right","#3366FF",null)));
        var source=new ChartDefinitionService.Definition(1,d.name(),d.type(),d.query(),style,null);
        var config=new ChartDefinitionService.PanelConfig("bar",d.query().getDimensions(),List.of(new QueryModel.Metric(old.getField(),"SUM",key)),List.of(),100,null);
        var adjusted=service.configureForDashboard(source,config);
        assertTrue(adjusted.style().getMetrics().isEmpty());assertEquals(1,source.style().getMetrics().size());
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
    @Test void pivotRejectsUnsupportedTotalsAndHiddenGrouping() {
        var q=new QueryModel();q.setDatasetId(9L);q.addDimension("city","city");q.addDimension("province","province");q.addMetric("id","AVG","value");q.setLimit(100);
        when(queries.datasetFields(9L)).thenReturn(List.of(Map.of("name","city","type","VARCHAR"),Map.of("name","province","type","VARCHAR"),Map.of("name","id","type","INT")));
        var advanced=new com.wanfo.bi.domain.chart.ChartAdvancedOptions();advanced.setPivotRowFields(List.of("city"));advanced.setPivotColFields(List.of("province"));
        var style=new com.wanfo.bi.domain.chart.ChartVisualStyle();style.setPivotRowTotals(true);
        var d=new ChartDefinitionService.Definition(1,"透视测试","pivot",q,style,advanced);
        assertThrows(IllegalArgumentException.class,()->service.create(new ChartDefinitionService.SaveRequest(null,d,null)));
        q.getMetrics().get(0).setAgg("COUNT_DISTINCT");assertThrows(IllegalArgumentException.class,()->service.create(new ChartDefinitionService.SaveRequest(null,d,null)));
        style.setPivotRowTotals(false);style.setPivotHierarchy("tree");assertThrows(IllegalArgumentException.class,()->service.create(new ChartDefinitionService.SaveRequest(null,d,null)));style.setPivotHierarchy("grid");
        q.getMetrics().get(0).setAgg("COUNT");style.setPivotRowSubtotals(true);assertThrows(IllegalArgumentException.class,()->service.create(new ChartDefinitionService.SaveRequest(null,d,null)));
        style.setPivotRowSubtotals(false);q.addDimension("id","id");assertThrows(IllegalArgumentException.class,()->service.create(new ChartDefinitionService.SaveRequest(null,d,null)));
        verify(mapper,never()).insert(any());
    }
}
