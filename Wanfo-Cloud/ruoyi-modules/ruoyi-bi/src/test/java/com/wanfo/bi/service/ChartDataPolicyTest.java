package com.wanfo.bi.service;
import java.util.*;
import com.wanfo.bi.domain.*;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.datasource.*;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.query.*;
import com.wanfo.bi.security.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ChartDataPolicyTest {
    @Test void pivotConsumerRunsOneSecuredStatementAndNeverExposesItsPredicates() throws Exception {
        when(policies.resolve(9L)).thenReturn(new DatasetDataPolicy.Scope(List.of(new DatasetDataPolicy.Rule(1L,9L,1L,"city","EQ","A","admin")),Set.of(),Set.of()));
        var q=model("city");q.addDimension("id","group_id");q.addMetric("id","AVG","mean");q.addMetric("id","COUNT_DISTINCT","buyers");
        q.setPivot(new QueryModel.Pivot(List.of("city"),List.of("id")));q.addFilter("id","GTE",0);
        var raw=new QueryResult();raw.setColumns(List.of("city","group_id","mean","buyers","__bi_pivot_row_depth","__bi_pivot_col_depth"));raw.setRows(List.of(Map.of("__bi_pivot_row_depth",0,"__bi_pivot_col_depth",0,"mean",new java.math.BigDecimal("1.5"),"buyers",2L)));
        when(engine.open(anyList())).thenReturn(mock(java.sql.Connection.class));when(engine.queryWithParams(any(),anyString(),anyList(),anyInt())).thenReturn(raw);
        var result=service.queryForDashboard(q);
        verify(engine,times(1)).queryWithParams(any(),contains("UNION ALL"),eq(List.of("A",java.math.BigDecimal.ZERO,"A",java.math.BigDecimal.ZERO,"A",java.math.BigDecimal.ZERO,"A",java.math.BigDecimal.ZERO)),eq(4004));
        assertEquals(List.of("city","group_id","mean","buyers"),result.get("columns"));assertTrue(result.containsKey("pivotTotals"));assertFalse(result.containsKey("executedSql"));assertFalse(result.containsKey("params"));assertFalse(result.containsKey("datasetName"));
        verify(resources).require("dataset",9L,"consume");verify(caps,never()).require("bi:dataset:query");
    }
    @Test void publishedMetadataUsesConsumeRightsAndStillHidesDeniedOrMaskedDrillFields() {
        when(policies.resolve(9L)).thenReturn(new DatasetDataPolicy.Scope(List.of(),Set.of("city"),Set.of("id")));
        assertTrue(service.datasetFieldsForDashboard(9L).isEmpty());
        verify(resources).require("dataset",9L,"consume");verify(caps).require("bi:dashboard:consume");
        verify(resources,never()).require("dataset",9L,"query");verify(caps,never()).require("bi:dataset:query");
        verifyNoInteractions(engine);
    }
    @Test void revokedConsumptionCannotReadEvenPublishedFieldMetadata() {
        doThrow(new BiAccessException(403,"revoked")).when(resources).require("dataset",9L,"consume");
        assertThrows(BiAccessException.class,()->service.datasetFieldsForDashboard(9L));
        assertThrows(BiAccessException.class,()->service.datasetFieldEntitiesForDashboard(9L));verifyNoInteractions(fields,policies,engine);
    }
    BiDatasetTableMapper datasets=mock(BiDatasetTableMapper.class);
    BiDatasetFieldMapper fields=mock(BiDatasetFieldMapper.class);
    DataSourceProvider sources=mock(DataSourceProvider.class);
    CalciteQueryEngine engine=mock(CalciteQueryEngine.class);
    DatasetDataPolicy policies=mock(DatasetDataPolicy.class);
    BiResourceAuthorizer resources=mock(BiResourceAuthorizer.class);
    CapabilityProvider caps=mock(CapabilityProvider.class);
    ChartQueryService service=new ChartQueryService(datasets,fields,new ChartQueryCompiler(),sources,engine,policies,resources,caps);
    ChartDataPolicyTest() {
        var d=new BiDatasetTableDO();d.setId(9L);d.setType("table");d.setDatasourceId(7L);d.setUnionSql("SELECT city, id FROM demo.orders");when(datasets.selectById(9L)).thenReturn(d);
        var source=new BiDatasource();source.setStatus("0");when(sources.getById(7L)).thenReturn(source);
        var city=new BiDatasetFieldDO();city.setOriginName("city");city.setOriginType("VARCHAR");var id=new BiDatasetFieldDO();id.setOriginName("id");id.setOriginType("INT");when(fields.selectByDatasetId(9L)).thenReturn(List.of(city,id));
    }
    QueryModel model(String field) {var m=new QueryModel();m.setDatasetId(9L);m.addDimension(field,null);return m;}
    @Test void tableQueryDoesNotConnectUnrelatedOfflineSources() throws Exception {
        when(sources.listEnabled()).thenThrow(new IllegalStateException("unrelated source offline"));
        when(policies.resolve(9L)).thenReturn(DatasetDataPolicy.Scope.unrestricted());
        when(engine.open(anyList())).thenReturn(mock(java.sql.Connection.class));
        when(engine.queryWithParams(any(),anyString(),anyList(),anyInt())).thenReturn(new QueryResult());
        service.query(model("city"));
        verify(engine).open(eq(List.of(sources.getById(7L))));
        verify(sources,never()).listEnabled();
    }
    @Test void restrictedColumnsCannotBeGroupedFilteredOrAggregated() {
        when(policies.resolve(9L)).thenReturn(new DatasetDataPolicy.Scope(List.of(),Set.of("city"),Set.of("id")));
        assertThrows(IllegalArgumentException.class,()->service.compileOnly(model("city")));
        assertThrows(IllegalArgumentException.class,()->service.compileOnly(model("id")));
        assertTrue(service.datasetFields(9L).isEmpty());verifyNoInteractions(engine);
    }
    @Test void fixedPredicateIsInsideAggregateInput() {
        when(policies.resolve(9L)).thenReturn(new DatasetDataPolicy.Scope(List.of(new DatasetDataPolicy.Rule(1L,9L,1L,"city","EQ","Shanghai","a")),Set.of(),Set.of()));
        String sql=service.compileOnly(model("city"));assertTrue(sql.contains("WHERE \"city\" = ?"));assertTrue(sql.indexOf("WHERE")<sql.indexOf("GROUP BY"));assertFalse(sql.contains("Shanghai"));
    }
    @Test void authorizationFailurePreventsDefinitionAndQueryAccess() throws Exception {
        doThrow(new BiAccessException(403,"denied")).when(resources).require("dataset",9L,"query");
        assertThrows(BiAccessException.class,()->service.query(model("city")));verifyNoInteractions(engine,policies,fields);
    }
    @Test void dashboardConsumerNeedsConsumeGrantButNotExplorationPermission() throws Exception {
        when(policies.resolve(9L)).thenReturn(new DatasetDataPolicy.Scope(List.of(new DatasetDataPolicy.Rule(1L,9L,1L,"city","EQ","A","admin")),Set.of(),Set.of()));
        var conn=mock(java.sql.Connection.class);when(engine.open(anyList())).thenReturn(conn);when(engine.queryWithParams(any(),anyString(),anyList(),anyInt())).thenReturn(new QueryResult());
        var result=service.queryForDashboard(model("city"));verify(resources).require("dataset",9L,"consume");verify(caps).require("bi:dashboard:consume");verify(caps,never()).require("bi:dataset:query");verify(resources,never()).require("dataset",9L,"query");
        assertFalse(result.containsKey("datasetName"));assertFalse(result.containsKey("executedSql"));assertFalse(result.containsKey("params"));
        verify(engine).queryWithParams(any(),contains("WHERE \"city\" = ?"),eq(List.of("A")),eq(1000));
    }
    @Test void missingConsumeGrantCannotOpenDatabase() {
        doThrow(new BiAccessException(403,"denied")).when(resources).require("dataset",9L,"consume");assertThrows(BiAccessException.class,()->service.queryForDashboard(model("city")));verifyNoInteractions(engine,fields,policies);
    }
    @Test void consumptionErrorsDoNotExposeSqlOrFilterValues() throws Exception {
        when(policies.resolve(9L)).thenReturn(DatasetDataPolicy.Scope.unrestricted());when(engine.open(anyList())).thenThrow(new java.sql.SQLException("SELECT secret WHERE private-filter"));
        var e=assertThrows(BiAccessException.class,()->service.queryForDashboard(model("city")));assertEquals(500,e.getCode());assertFalse(e.getMessage().contains("SELECT"));assertFalse(e.getMessage().contains("private-filter"));
    }
    @Test void revokedColumnPolicyAlsoBlocksDashboardConsumption() {
        when(policies.resolve(9L)).thenReturn(new DatasetDataPolicy.Scope(List.of(),Set.of("city"),Set.of()));
        assertEquals(403,assertThrows(BiAccessException.class,()->service.queryForDashboard(model("city"))).getCode());verifyNoInteractions(engine);
    }
}
