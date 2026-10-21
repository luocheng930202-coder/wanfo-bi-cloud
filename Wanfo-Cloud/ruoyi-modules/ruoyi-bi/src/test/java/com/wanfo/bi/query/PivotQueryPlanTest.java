package com.wanfo.bi.query;

import java.util.*;
import java.math.BigDecimal;
import com.wanfo.bi.domain.query.QueryModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PivotQueryPlanTest {
    private final String base="SELECT * FROM (VALUES ('A','X','P',10,'shared'),('A','X','P',20,'a'),('A','Y','P',100,'shared'),('B','Z','Q',50,'b'),('B','Z','P',CAST(NULL AS INTEGER),CAST(NULL AS VARCHAR))) AS t(\"region\",\"city\",\"kind\",\"amount\",\"customer\")";
    private final Map<String,String> types=Map.of("region","VARCHAR","city","VARCHAR","kind","VARCHAR","amount","INTEGER","customer","VARCHAR");
    private QueryModel model() {
        var q=new QueryModel();q.setDatasetId(1L);q.addDimension("region","r");q.addDimension("city","c");q.addDimension("kind","k");
        q.addMetric("amount","AVG","mean");q.addMetric("customer","COUNT_DISTINCT","buyers");q.addMetric("amount","SUM","sales");
        q.setPivot(new QueryModel.Pivot(List.of("region","city"),List.of("kind")));q.setLimit(100);return q;
    }
    private PivotQueryPlan.Result execute(QueryModel q,String source,List<Object> security) throws Exception {
        var plan=PivotQueryPlan.compile(q,source,types,new ChartQueryCompiler(),security);
        try(var connection=new CalciteQueryEngine().open(List.of())) {
            var data=new CalciteQueryEngine().queryWithParams(connection,plan.query().sql,plan.query().params,plan.maxRows());
            return PivotQueryPlan.decode(q,plan,data.getRows());
        }
    }
    @SuppressWarnings("unchecked") private Map<String,Object> cell(PivotQueryPlan.Result result,Map<String,Object> dimensions) {
        var cells=(List<Map<String,Object>>)result.totals().get("cells");
        return (Map<String,Object>)cells.stream().filter(c->c.get("dimensions").equals(dimensions)).findFirst().orElseThrow().get("values");
    }
    @Test void avgAndDistinctAreRecomputedFromSourceForEveryPrefixAndCrossTotal() throws Exception {
        var r=execute(model(),base,List.of());assertEquals("complete",r.totals().get("status"));
        var grand=cell(r,Map.of());assertEquals(0,new BigDecimal("45").compareTo(new BigDecimal(grand.get("mean").toString())));assertEquals("3",grand.get("buyers"));assertEquals("180",grand.get("sales"));
        var region=cell(r,Map.of("r","A"));assertEquals(130d/3,Double.parseDouble(region.get("mean").toString()),0.000001);assertEquals("2",region.get("buyers"));assertEquals("130",region.get("sales"));
        assertEquals("2",cell(r,Map.of("r","A","k","P")).get("buyers"));
        assertTrue(r.rows().stream().anyMatch(row->row.get("mean")==null && row.get("buyers").equals("0")));
    }
    @Test void permissionsAndUserFiltersAreRepeatedInsideEveryGroupingBranch() throws Exception {
        var q=model();q.addFilter("kind","EQ","P");String secured="SELECT * FROM ("+base+") AS secured WHERE \"region\" <> ?";
        var plan=PivotQueryPlan.compile(q,secured,types,new ChartQueryCompiler(),List.of("B"));
        assertEquals(12,plan.query().params.size());for(int i=0;i<12;i+=2)assertEquals(List.of("B","P"),plan.query().params.subList(i,i+2));
        var r=execute(q,secured,List.of("B"));assertEquals("2",cell(r,Map.of()).get("buyers"));assertEquals("130",cell(r,Map.of()).get("sales"));
    }
    @Test void detailLimitDoesNotChangeFullScopeTotals() throws Exception {
        var q=model();q.setLimit(1);var r=execute(q,base,List.of());assertEquals(1,r.rows().size());assertEquals(true,r.totals().get("detailTruncated"));assertEquals("3",cell(r,Map.of()).get("buyers"));
    }
    @Test void invalidAxesRawMetricsLimitsAndHiddenFieldsCannotCompile() {
        var compiler=new ChartQueryCompiler();var q=model();q.setPivot(new QueryModel.Pivot(List.of("region"),List.of("kind")));assertThrows(IllegalArgumentException.class,()->compiler.compile(q,base,types));
        q.setPivot(new QueryModel.Pivot(List.of("region","city"),List.of("kind")));q.setLimit(1001);assertThrows(IllegalArgumentException.class,()->compiler.compile(q,base,types));
        q.setLimit(100);q.getMetrics().get(0).setAgg("NONE");assertThrows(IllegalArgumentException.class,()->compiler.compile(q,base,types));q.getMetrics().get(0).setAgg("AVG");
        var visible=new HashMap<>(types);visible.remove("customer");assertThrows(IllegalArgumentException.class,()->PivotQueryPlan.compile(q,base,visible,compiler));
    }
    @Test void oversizedSummaryGroupsDisableAllTotalsAndInternalAliasesCannotCollide() {
        var q=model();q.getDimensions().get(0).setAlias("__bi_pivot_row_depth");var plan=PivotQueryPlan.compile(q,base,types,new ChartQueryCompiler());assertNotEquals("__bi_pivot_row_depth",plan.rowDepthKey());
        List<Map<String,Object>> raw=new ArrayList<>();for(int i=0;i<1001;i++)raw.add(Map.of(plan.rowDepthKey(),1,plan.colDepthKey(),0,"__bi_pivot_row_depth","R"+i,"mean",1,"buyers",1,"sales",1));
        var r=PivotQueryPlan.decode(q,plan,raw);assertEquals("unavailable",r.totals().get("status"));assertEquals(List.of(),r.totals().get("cells"));
    }
}
