package com.wanfo.bi.query;

import java.util.*;
import java.time.LocalDate;
import com.wanfo.bi.domain.query.QueryModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeComparisonTest {
    QueryModel model(String grain,String mode){var q=new QueryModel();q.setDatasetId(1L);q.addDimension("date","period");q.getDimensions().get(0).setTimeGrain(grain);q.addMetric("amount","AVG","sales");q.setTimeComparison(mode);q.setLimit(100);return q;}
    Map<String,Object> row(String period,Object sales){var row=new LinkedHashMap<String,Object>();row.put("period",period);row.put("sales",sales);return row;}
    @Test void calendarOffsetsCoverLeapDaysQuartersAndWeekYearBoundaries(){
        assertEquals("2023-02-28",TimeComparison.previous(LocalDate.parse("2024-02-29"),"day","year").toString());
        for(var entry:Map.of("day","2025-12-31","week","2025-12-25","month","2025-12-01","quarter","2025-10-01","year","2025-01-01").entrySet())assertEquals(entry.getValue(),TimeComparison.previous(LocalDate.parse("2026-01-01"),entry.getKey(),"previous").toString());
        assertEquals(LocalDate.parse("2025-01-06"),TimeComparison.previous(LocalDate.parse("2026-01-05"),"week","year"));
    }
    @Test void priorRangeShiftsOnlySelectedTimeFiltersAndDoesNotMutateInput(){
        var q=model("month","previous");q.addFilter("date","BETWEEN",List.of("2024-03-01","2024-03-31"));q.addFilter("region","EQ","A");
        var tree=new QueryModel.FilterNode();tree.setLogic("OR");var leaf=new QueryModel.FilterNode();leaf.setField("date");leaf.setOp("EQ");leaf.setValue("2024-03-31 12:34:56");tree.setChildren(List.of(leaf));q.setFilterTree(tree);
        var prior=TimeComparison.baseline(q,List.of(row("2024-03-01",1)));
        assertEquals(List.of("2024-02-01","2024-02-29"),prior.getFilters().get(0).getValue());assertEquals("A",prior.getFilters().get(1).getValue());assertEquals("2024-02-29 12:34:56",prior.getFilterTree().getChildren().get(0).getValue());assertEquals("2024-03-31 12:34:56",leaf.getValue());
        assertEquals("2024-02-01",prior.getFilters().get(2).getValue());assertEquals("2024-03-01",prior.getFilters().get(3).getValue());assertNull(prior.getTimeComparison());assertEquals(1001,prior.getLimit());
        q.getFilters().clear();q.addFilter("date","BETWEEN",List.of("2026-02-01","2026-02-28"));assertEquals(List.of("2026-01-01","2026-01-31"),TimeComparison.baseline(q,List.of(row("2026-02-01",1))).getFilters().get(0).getValue());
    }
    @Test void growthUsesExactValuesAbsoluteNegativeBaseAndNoZeroDivision(){
        var q=model("month","previous");var r=TimeComparison.apply(q,List.of("period","sales"),List.of(row("2026-02-01","-5"),row("2026-03-01",0),row("2026-04-01",10)),List.of(row("2026-01-01","-10"),row("2026-02-01",0)),null,false);
        var f=((List<TimeComparison.Field>)r.metadata().get("fields")).get(0);
        assertEquals("5",r.rows().get(0).get(f.difference()));assertEquals("0.500000000000",r.rows().get(0).get(f.rate()));assertEquals("0",r.rows().get(1).get(f.difference()));assertNull(r.rows().get(1).get(f.rate()));assertNull(r.rows().get(2).get(f.baseline()));assertNull(r.rows().get(2).get(f.rate()));
    }
    @Test void nullDatesNullValuesAndSyntheticZeroDoNotBecomeRealGrowth(){
        var q=model("day","previous");var source=List.of(row(null,10),row("2026-01-02",null),row("2026-01-03",0));var r=TimeComparison.apply(q,List.of("period","sales"),source,List.of(row("2026-01-01",5),row("2026-01-02",5)),Map.of("status","complete","periods",List.of("2026-01-03")),false);
        var f=((List<TimeComparison.Field>)r.metadata().get("fields")).get(0);for(var row:r.rows())assertNull(row.get(f.difference()));assertFalse(source.get(0).containsKey(f.baseline()));assertNull(TimeComparison.baseline(q,List.of(row(null,1))));
    }
    @Test void truncationAndFailedFillNeverShowPartialComparisons(){
        var q=model("day","year");var rows=List.of(row("2026-01-01",5));
        assertEquals("skipped",TimeComparison.apply(q,List.of("period","sales"),rows,List.of(),null,true).metadata().get("status"));
        assertEquals("skipped",TimeComparison.apply(q,List.of("period","sales"),rows,Collections.nCopies(1001,row("2025-01-01",1)),null,false).metadata().get("status"));
        assertEquals("skipped",TimeComparison.apply(q,List.of("period","sales"),rows,List.of(),Map.of("status","skipped"),false).metadata().get("status"));
    }
    @Test void invalidConfigurationsAndUnshiftableFiltersFailEarly(){
        var q=model("month","other");assertThrows(IllegalArgumentException.class,()->TimeComparison.validate(q));q.setTimeComparison("previous");q.setOrderBys(List.of(new QueryModel.OrderBy("sales","desc")));assertThrows(IllegalArgumentException.class,()->TimeComparison.validate(q));
        q.setOrderBys(List.of());q.addFilter("date","LIKE","2026%");assertThrows(IllegalArgumentException.class,()->TimeComparison.validate(q));q.getFilters().clear();q.addFilter("date","EQ","not a date");assertThrows(IllegalArgumentException.class,()->TimeComparison.validate(q));q.getFilters().clear();q.getDimensions().get(0).setTimeGrain(null);assertThrows(IllegalArgumentException.class,()->TimeComparison.validate(q));
    }
    @Test void multipleMetricsAndAliasCollisionsRemainSeparate(){
        var q=model("month","year");q.getDimensions().get(0).setAlias("__cmp_0_base");q.addMetric("id","COUNT_DISTINCT","customers");TimeComparison.validate(q);
        var a=new LinkedHashMap<String,Object>(Map.of("__cmp_0_base","2026-01-01","sales","100000000000000000.125","customers",2));var b=Map.<String,Object>of("__cmp_0_base","2025-01-01","sales","100000000000000000.124","customers",1);
        var r=TimeComparison.apply(q,List.of("__cmp_0_base","sales","customers"),List.of(a),List.of(b),null,false);var fs=(List<TimeComparison.Field>)r.metadata().get("fields");assertNotEquals("__cmp_0_base",fs.get(0).baseline());assertEquals("0.001",r.rows().get(0).get(fs.get(0).difference()));assertEquals("1",r.rows().get(0).get(fs.get(1).difference()));assertEquals(9,r.columns().size());
    }
}
