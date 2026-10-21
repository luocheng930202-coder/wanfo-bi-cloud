package com.wanfo.bi.query;

import java.util.*;
import com.wanfo.bi.domain.query.QueryModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeGrainTest {
    private final ChartQueryCompiler compiler = new ChartQueryCompiler();
    private final String base = "SELECT * FROM (VALUES (TIMESTAMP '2025-12-31 23:59:59', 2),"
        + "(TIMESTAMP '2026-01-01 00:00:00', 3),(TIMESTAMP '2026-01-04 12:00:00', 5),"
        + "(TIMESTAMP '2026-01-05 00:00:00', 7),(TIMESTAMP '2026-04-01 00:00:00', 11)) AS t(\"event_time\",\"amount\")";
    private QueryModel model(String grain) {
        var q = new QueryModel();q.setDatasetId(1L);q.addDimension("event_time","period");
        q.getDimensions().get(0).setTimeGrain(grain);q.addMetric("amount","SUM","total");return q;
    }
    private ChartQueryCompiler.Compiled compile(QueryModel q) {
        return compiler.compile(q,base,Map.of("event_time","TIMESTAMP","amount","DECIMAL"));
    }
    private Map<String,Integer> execute(QueryModel q) throws Exception {
        var result = new LinkedHashMap<String,Integer>();
        try(var connection = new CalciteQueryEngine().open(List.of());var statement=connection.prepareStatement(compile(q).sql)) {
            var c=compile(q);for(int i=0;i<c.params.size();i++)statement.setObject(i+1,c.params.get(i));
            try(var rows=statement.executeQuery()) {while(rows.next())result.put(rows.getString(1),rows.getInt(2));}
        }
        return result;
    }
    @Test void calendarBucketsExecuteAndSortChronologically() throws Exception {
        assertEquals(List.of("2025-12-31","2026-01-01","2026-01-04","2026-01-05","2026-04-01"),new ArrayList<>(execute(model("day")).keySet()));
        assertEquals(Map.of("2025-12-29",10,"2026-01-05",7,"2026-03-30",11),execute(model("week")));
        assertEquals(Map.of("2025-12-01",2,"2026-01-01",15,"2026-04-01",11),execute(model("month")));
        assertEquals(Map.of("2025-10-01",2,"2026-01-01",15,"2026-04-01",11),execute(model("quarter")));
        assertEquals(Map.of("2025-01-01",2,"2026-01-01",26),execute(model("year")));
    }
    @Test void filtersStillApplyToRawDatesBeforeBucketingAndExplicitSortWins() throws Exception {
        var q=model("month");q.addFilter("amount","GTE",5);q.setOrderBys(List.of(new QueryModel.OrderBy("total","desc")));
        assertEquals(Map.of("2026-01-01",12,"2026-04-01",11),execute(q));
        assertTrue(compile(q).sql.contains("ORDER BY \"total\" DESC"));
        q.setOrderBys(new ArrayList<>());q.addFilter("event_time","IS_NOT_NULL",null);assertTrue(compile(q).sql.contains("WHERE \"amount\" >= ? AND \"event_time\" IS NOT NULL"));
    }
    @Test void invalidGrainsTypesAndHiddenFieldsAreRejected() {
        for(String grain:List.of("", "MONTH", "month); DROP TABLE t", "hour"))assertThrows(IllegalArgumentException.class,()->compile(model(grain)));
        for(String type:List.of("VARCHAR","INT","TIME","","TIMESTAMP WITH TIME ZONE"))
            assertThrows(IllegalArgumentException.class,()->compiler.compile(model("month"),base,Map.of("event_time",type,"amount","INT")));
        assertThrows(IllegalArgumentException.class,()->compiler.compile(model("month"),base,Map.of("amount","INT")));
    }
    @Test void legacyQueriesRetainMetricOrderingAndNoBucket() {
        String sql=compile(model(null)).sql;assertFalse(sql.contains("FLOOR"));assertTrue(sql.contains("ORDER BY \"total\" DESC"));
    }
}
