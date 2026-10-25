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
    @Test void cachedQueriesRecheckIdentityPoliciesAndRevocationBeforeReturningData()throws Exception {
        var cache=new UserQueryCache();var actors=mock(CurrentActorProvider.class);var snapshots=mock(com.wanfo.bi.datasource.snapshot.SnapshotRegistry.class);
        service.configureCache(cache,actors,snapshots);var dataset=datasets.selectById(9L);dataset.setCacheEnabled("1");dataset.setCacheTtl(60);
        when(actors.currentActor()).thenReturn(new BiActor("test",1L,"a",null,Set.of(1L)));
        when(policies.resolve(9L)).thenReturn(DatasetDataPolicy.Scope.unrestricted());when(engine.open(anyList())).thenReturn(mock(java.sql.Connection.class));when(engine.queryWithParams(any(),anyString(),anyList(),anyInt())).thenReturn(new QueryResult());
        var request=new org.springframework.mock.web.MockHttpServletRequest();org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try{
            var q=model("city");assertEquals(false,((Map<?,?>)service.query(q).get("cache")).get("hit"));assertEquals(true,((Map<?,?>)service.query(q).get("cache")).get("hit"));verify(engine,times(1)).open(anyList());
            when(actors.currentActor()).thenReturn(new BiActor("test",2L,"b",null,Set.of(1L)));service.query(q);verify(engine,times(2)).open(anyList());
            when(policies.resolve(9L)).thenReturn(new DatasetDataPolicy.Scope(List.of(new DatasetDataPolicy.Rule(1L,9L,1L,"city","EQ","A","admin")),Set.of(),Set.of()));service.query(q);verify(engine,times(3)).open(anyList());
            doThrow(new BiAccessException(403,"revoked")).when(resources).require("dataset",9L,"query");assertThrows(BiAccessException.class,()->service.query(q));verify(engine,times(3)).open(anyList());
        }finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
    @Test void snapshotVersionChangesAndManualBypassForceFreshExecution()throws Exception {
        var cache=new UserQueryCache();var actors=mock(CurrentActorProvider.class);var snapshots=mock(com.wanfo.bi.datasource.snapshot.SnapshotRegistry.class);service.configureCache(cache,actors,snapshots);
        datasets.selectById(9L).setCacheEnabled("1");var source=sources.getById(7L);source.setId(7L);source.setType("csv");when(snapshots.version(7L)).thenReturn(1L);when(actors.currentActor()).thenReturn(new BiActor("test",1L,"a",null,Set.of()));
        when(policies.resolve(9L)).thenReturn(DatasetDataPolicy.Scope.unrestricted());when(engine.open(anyList())).thenReturn(mock(java.sql.Connection.class));when(engine.queryWithParams(any(),anyString(),anyList(),anyInt())).thenReturn(new QueryResult());
        var request=new org.springframework.mock.web.MockHttpServletRequest();org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try{
            var q=model("city");service.query(q);service.query(q);verify(engine,times(1)).open(anyList());when(snapshots.version(7L)).thenReturn(2L);service.query(q);verify(engine,times(2)).open(anyList());
            request.addHeader("X-BI-Cache-Bypass","true");service.query(q);verify(engine,times(3)).open(anyList());
        }finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
    @Test void comparisonQueriesKeepSecurityAndShiftOnlyUserTimePredicates() throws Exception {
        when(policies.resolve(9L)).thenReturn(new DatasetDataPolicy.Scope(List.of(new DatasetDataPolicy.Rule(1L,9L,1L,"city","EQ","A","admin")),Set.of(),Set.of()));
        var date=new BiDatasetFieldDO();date.setOriginName("day");date.setOriginType("DATE");var city=new BiDatasetFieldDO();city.setOriginName("city");city.setOriginType("VARCHAR");var id=new BiDatasetFieldDO();id.setOriginName("id");id.setOriginType("INT");when(fields.selectByDatasetId(9L)).thenReturn(List.of(date,city,id));
        var ds=datasets.selectById(9L);ds.setUnionSql("SELECT city,id,day FROM demo.orders");var q=model("day");q.getDimensions().get(0).setTimeGrain("month");q.addMetric("id","AVG","mean");q.addFilter("day","BETWEEN",List.of("2026-02-01","2026-02-28"));q.addFilter("city","EQ","B");q.setTimeComparison("previous");
        var current=new QueryResult();current.setColumns(List.of("day","mean"));current.setRows(List.of(Map.of("day","2026-02-01","mean",15)));var prior=new QueryResult();prior.setColumns(current.getColumns());prior.setRows(List.of(Map.of("day","2026-01-01","mean",10)));
        when(engine.open(anyList())).thenReturn(mock(java.sql.Connection.class));when(engine.queryWithParams(any(),anyString(),anyList(),anyInt())).thenReturn(current,prior);
        var result=service.queryForDashboard(q);var arguments=org.mockito.ArgumentCaptor.forClass(List.class);verify(engine,times(2)).queryWithParams(any(),anyString(),arguments.capture(),anyInt());
        assertEquals("A",arguments.getAllValues().get(0).get(0));assertEquals("A",arguments.getAllValues().get(1).get(0));assertTrue(arguments.getAllValues().get(1).toString().contains("2026-01-01"));assertTrue(arguments.getAllValues().get(1).contains("B"));assertFalse(arguments.getAllValues().get(1).toString().contains("2026-02-28"));
        assertEquals("complete",((Map<?,?>)result.get("timeComparison")).get("status"));assertFalse(result.containsKey("executedSql"));assertFalse(result.containsKey("params"));verify(caps,never()).require("bi:dataset:query");
    }
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
