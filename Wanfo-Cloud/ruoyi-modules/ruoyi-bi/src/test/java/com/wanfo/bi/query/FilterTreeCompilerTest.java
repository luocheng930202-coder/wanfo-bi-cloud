package com.wanfo.bi.query;

import java.util.*;
import java.math.BigDecimal;
import com.wanfo.bi.domain.query.QueryModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FilterTreeCompilerTest {
    private final ChartQueryCompiler compiler=new ChartQueryCompiler();
    private final Map<String,String> fields=Map.of("id","INT","city","VARCHAR","district","VARCHAR");
    private QueryModel model() {var m=new QueryModel();m.setDatasetId(9L);m.addDimension("city","place");return m;}
    private QueryModel.FilterNode leaf(String field,String op,Object value) {var n=new QueryModel.FilterNode();n.setField(field);n.setOp(op);n.setValue(value);return n;}
    private QueryModel.FilterNode group(String logic,QueryModel.FilterNode...children) {var n=new QueryModel.FilterNode();n.setLogic(logic);n.setChildren(Arrays.asList(children));return n;}
    private ChartQueryCompiler.Compiled compile(QueryModel m) {return compiler.compile(m,"SELECT id,city,district FROM demo.orders",fields);}
    @Test void nestedGroupingAndDepthFirstParameters() {
        var m=model();m.addFilter("id","GTE",1);m.setFilterTree(group("OR",leaf("city","EQ","A"),group("AND",leaf("id","LT",4),leaf("city","EQ","B"))));
        var c=compile(m);assertTrue(c.sql.contains("\"id\" >= ? AND (\"city\" = ? OR (\"id\" < ? AND \"city\" = ?))"));
        assertEquals(List.of(new BigDecimal("1"),"A",new BigDecimal("4"),"B"),c.params);assertTrue(c.sql.endsWith("LIMIT 1000"));
    }
    @Test void negativeOperatorsUseRealSqlKeywords() {
        var m=model();m.setFilterTree(group("AND",leaf("city","NOT_LIKE","A%"),leaf("id","NOT_IN",List.of(1,2))));
        var c=compile(m);assertTrue(c.sql.contains("NOT LIKE ?"));assertTrue(c.sql.contains("NOT IN (?, ?)"));
    }
    @Test void nullOperatorsHaveNoParameters() {
        var m=model();m.setFilterTree(group("OR",leaf("city","IS_NULL",null),leaf("district","IS_NOT_NULL",null)));
        var c=compile(m);assertTrue(c.params.isEmpty());assertTrue(c.sql.contains("IS NULL OR"));
    }
    @Test void malformedGroupsAndLeavesAreRejected() {
        for(var n:List.of(group("OR"),group("X",leaf("id","EQ",1)),leaf("gone","EQ",1))) {
            var m=model();m.setFilterTree(n);assertThrows(IllegalArgumentException.class,()->compile(m));
        }
        var mixed=group("AND",leaf("id","EQ",1));mixed.setField("id");var m=model();m.setFilterTree(mixed);assertThrows(IllegalArgumentException.class,()->compile(m));
        m.setFilterTree(group("AND",(QueryModel.FilterNode)null));assertThrows(IllegalArgumentException.class,()->compile(m));
    }
    @Test void treeDepthAndSharedLegacyNodeBudgetAreBounded() {
        var node=leaf("id","EQ",1);for(int i=0;i<8;i++)node=group("AND",node);
        var m=model();m.setFilterTree(node);assertThrows(IllegalArgumentException.class,()->compile(m));
        m.setFilterTree(leaf("id","EQ",1));for(int i=0;i<100;i++)m.addFilter("id","EQ",1);
        assertThrows(IllegalArgumentException.class,()->compile(m));
    }
    @Test void listAndBetweenShapesAreStrict() {
        for(Object value:List.of(List.of(),List.of(1),List.of(1,2,3),"1,2")) {
            var m=model();m.addFilter("id","BETWEEN",value);assertThrows(IllegalArgumentException.class,()->compile(m));
        }
        var m=model();m.addFilter("id","IN",List.of());assertThrows(IllegalArgumentException.class,()->compile(m));
        m.getFilters().clear();m.addFilter("id","IN",Collections.nCopies(101,1));assertThrows(IllegalArgumentException.class,()->compile(m));
    }
    @Test void onlyScalarNonNullValuesAreBound() {
        for(Object value:Arrays.asList(null,Map.of("sql","1=1"),List.of(1),Double.NaN,"x".repeat(1001))) {
            var m=model();m.addFilter("id","EQ",value);assertThrows(IllegalArgumentException.class,()->compile(m));
        }
        var m=model();m.addFilter("city","IS_NULL","ignored");assertThrows(IllegalArgumentException.class,()->compile(m));
    }
    @Test void injectionStaysInParametersAndNumericValuesAreTyped() {
        var m=model();String payload="x' OR 1=1 --";m.addFilter("city","EQ",payload);m.addFilter("id","BETWEEN",List.of("2","3"));
        var c=compile(m);assertFalse(c.sql.contains(payload));assertEquals(List.of(payload,new BigDecimal("2"),new BigDecimal("3")),c.params);
    }
    @Test void hiddenFieldCannotHideInNestedBranch() {
        var m=model();m.setFilterTree(group("OR",leaf("city","EQ","A"),group("AND",leaf("district","EQ","secret"))));
        assertThrows(IllegalArgumentException.class,()->compiler.compile(m,"SELECT id,city FROM demo.orders",Map.of("id","INT","city","VARCHAR")));
    }
    @Test void totalParameterBudgetIsBounded() {
        var m=model();for(int i=0;i<11;i++)m.addFilter("id","IN",Collections.nCopies(100,1));
        assertThrows(IllegalArgumentException.class,()->compile(m));
    }
    @Test void limitIsEnforcedInSqlAndInvalidValuesRejected() {
        for(int n:new int[]{0,-1,10001,Integer.MAX_VALUE}) {var m=model();m.setLimit(n);assertThrows(IllegalArgumentException.class,()->compile(m));}
        var m=model();m.setLimit(10);assertTrue(compile(m).sql.endsWith("LIMIT 10"));
    }
    @Test void duplicateOrOversizedAliasesRejected() {
        var m=model();m.addMetric("id","COUNT","PLACE");assertThrows(IllegalArgumentException.class,()->compile(m));
        m.getMetrics().clear();m.getDimensions().get(0).setAlias("x".repeat(129));assertThrows(IllegalArgumentException.class,()->compile(m));
    }
    @Test void sortUsesOutputAliasAndRejectsInvalidDirectionOrMissingOutput() {
        var m=model();m.getOrderBys().add(new QueryModel.OrderBy("city","desc"));assertTrue(compile(m).sql.contains("ORDER BY \"place\" DESC"));
        m.getOrderBys().get(0).setOrder("DESC;DROP");assertThrows(IllegalArgumentException.class,()->compile(m));
        m.getOrderBys().get(0).setOrder("asc");m.getOrderBys().get(0).setField("id");assertThrows(IllegalArgumentException.class,()->compile(m));
    }
    @Test void blankAliasesUseIdenticalSelectAndSortNames() {
        var m=model();m.getDimensions().clear();m.addMetric("id","COUNT"," ");assertTrue(compile(m).sql.contains("ORDER BY \"count_id\" DESC"));
    }
    @Test void textCannotBeSummedAndRawMetricsCannotMixWithAggregates() {
        var m=model();m.addMetric("city","SUM",null);assertThrows(IllegalArgumentException.class,()->compile(m));
        m.getMetrics().clear();m.addMetric("id","COUNT","n");m.addMetric("id","NONE","raw");assertThrows(IllegalArgumentException.class,()->compile(m));
    }
    @Test void nullAndOversizedQueryListsRejected() {
        var m=model();m.setDimensions(null);assertThrows(IllegalArgumentException.class,()->compile(m));
        var n=model();n.setFilters(null);assertThrows(IllegalArgumentException.class,()->compile(n));
        var p=model();for(int i=0;i<200;i++)p.addMetric("id","COUNT","m"+i);assertThrows(IllegalArgumentException.class,()->compile(p));
        assertThrows(IllegalArgumentException.class,()->compile(null));
    }
}
