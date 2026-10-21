package com.wanfo.bi.query;

import java.util.*;
import com.wanfo.bi.domain.query.QueryModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeGapFillTest {
    private QueryModel model(String grain,String mode) {
        var q=new QueryModel();q.setDatasetId(1L);q.addDimension("date","period");q.getDimensions().get(0).setTimeGrain(grain);q.getDimensions().get(0).setTimeFill(mode);
        q.addMetric("amount","SUM","sales");q.setLimit(100);return q;
    }
    private Map<String,Object> row(String date,Object value) {var row=new LinkedHashMap<String,Object>();row.put("period",date);row.put("sales",value);return row;}
    @Test void defaultAndNoneKeepTheOriginalRowsAndMetadataAbsent() {
        var rows=List.of(row("2026-01-01",1),row("2026-03-01",2));
        for(String mode:new String[]{null,"none"}){var r=TimeGapFill.apply(model("month",mode),rows);assertSame(rows,r.rows());assertNull(r.metadata());}
    }
    @Test void leapDayIsFilledButOriginalNullAndZeroAreNeverOverwritten() {
        var rows=List.of(row("2024-02-28",null),row("2024-03-01",0));
        var r=TimeGapFill.apply(model("day","zero"),rows);
        assertEquals(List.of("2024-02-28","2024-02-29","2024-03-01"),r.rows().stream().map(x->x.get("period")).toList());
        assertNull(r.rows().get(0).get("sales"));assertEquals(0,r.rows().get(1).get("sales"));assertEquals(0,r.rows().get(2).get("sales"));
        assertEquals(2,rows.size());assertEquals(1,r.metadata().get("addedRows"));assertEquals(2,r.metadata().get("sourceRows"));
        assertEquals(List.of("2024-02-29"),r.metadata().get("periods"));
    }
    @Test void weeksQuartersMonthsAndYearsUseTheCalendarGrain() {
        for(var sample:List.of(List.of("week","2025-12-29","2026-01-12","2026-01-05"),List.of("month","2025-12-01","2026-02-01","2026-01-01"),List.of("quarter","2025-10-01","2026-04-01","2026-01-01"),List.of("year","2024-01-01","2026-01-01","2025-01-01"))) {
            var r=TimeGapFill.apply(model(sample.get(0),"null"),List.of(row(sample.get(1),1),row(sample.get(2),2)));
            assertEquals(3,r.rows().size());assertEquals(sample.get(3),r.rows().get(1).get("period"));assertNull(r.rows().get(1).get("sales"));
        }
    }
    @Test void descendingAndNullDateRowsKeepTheirOrderingAndAllMetricAliases() {
        var q=model("month","zero");q.addMetric("count","COUNT",null);q.setOrderBys(List.of(new QueryModel.OrderBy("date","desc")));
        var r=TimeGapFill.apply(q,List.of(row("2026-03-01",3),row("2026-01-01",1),row(null,5)));
        assertEquals("2026-03-01",r.rows().get(0).get("period"));assertNull(r.rows().get(3).get("period"));assertEquals(0,r.rows().get(1).get("count_count"));
    }
    @Test void emptySingletonAndUndatedResultsDoNotInventAnOutsideRange() {
        var q=model("day","zero");
        for(var rows:List.<List<Map<String,Object>>>of(List.of(),List.of(row("2026-01-01",1)),List.of(row(null,1)))) {
            var r=TimeGapFill.apply(q,rows);assertSame(rows,r.rows());assertEquals(0,r.metadata().get("addedRows"));
        }
    }
    @Test void truncatedOversizedAndDuplicateOutputsAreReturnedWithoutPartialFilling() {
        var rows=List.of(row("2026-01-01",1),row("2026-01-10",2));var q=model("day","zero");q.setLimit(2);
        var r=TimeGapFill.apply(q,rows);assertSame(rows,r.rows());assertEquals("skipped",r.metadata().get("status"));
        q.setLimit(5);r=TimeGapFill.apply(q,rows);assertSame(rows,r.rows());assertEquals(0,r.metadata().get("addedRows"));
        r=TimeGapFill.apply(q,List.of(row("2026-01-01",1),row("2026-01-01",2)));assertEquals("skipped",r.metadata().get("status"));
    }
    @Test void invalidModesShapesAndRankingSortsAreRejectedBeforeQuerying() {
        var q=model("month","bad");assertThrows(IllegalArgumentException.class,()->TimeGapFill.validate(q));
        q.getDimensions().get(0).setTimeFill("zero");q.getDimensions().get(0).setTimeGrain(null);assertThrows(IllegalArgumentException.class,()->TimeGapFill.validate(q));
        q.getDimensions().get(0).setTimeGrain("month");q.getMetrics().get(0).setAgg("NONE");assertThrows(IllegalArgumentException.class,()->TimeGapFill.validate(q));
        q.getMetrics().get(0).setAgg("SUM");q.setOrderBys(List.of(new QueryModel.OrderBy("sales","desc")));assertThrows(IllegalArgumentException.class,()->TimeGapFill.validate(q));
        q.setOrderBys(List.of());q.addDimension("city","city");assertThrows(IllegalArgumentException.class,()->TimeGapFill.validate(q));
    }
}
