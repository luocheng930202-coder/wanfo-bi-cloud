package com.wanfo.bi.query;

import java.util.List;
import java.util.Map;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.service.MetricDefinitionService.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetricQueryCompilerTest {
    Definition atomic() {
        return new Definition(2, "成交额", "gmv", "atomic", 9L,
                new MeasureSpec("amount", "SUM"),
                List.of(new QueryModel.Filter("status", "EQ", "paid")),
                List.of("city", "channel"), null, null, null, null,
                "def", "day", "CNY", "Asia/Shanghai", "alice");
    }

    @Test
    void atomicCompilesMeasureFixedFiltersAndRuntimeDims() {
        var dim = new QueryModel.Dimension("city", "city");
        var q = MetricQueryCompiler.compileAtomic(atomic(),
                new MetricQueryRequest(List.of(dim), List.of(new QueryModel.Filter("channel", "EQ", "app")), 50, 10),
                new MeasureSpec("amount", "SUM"), "gmv");
        assertEquals(9L, q.getDatasetId());
        assertEquals(1, q.getDimensions().size());
        assertEquals(2, q.getFilters().size());
        assertEquals("SUM", q.getMetrics().get(0).getAgg());
        assertEquals(50, q.getLimit());
        assertEquals(10, q.getTimeoutSeconds());
    }

    @Test
    void rejectsDimensionOutsideAllowlist() {
        var dim = new QueryModel.Dimension("region", "region");
        assertThrows(IllegalArgumentException.class, () -> MetricQueryCompiler.compileAtomic(atomic(),
                new MetricQueryRequest(List.of(dim), List.of(), null, null),
                new MeasureSpec("amount", "SUM"), "gmv"));
    }

    @Test
    void derivedShellAddsBothMeasures() {
        Definition derived = new Definition(2, "客单价", "aov", "derived", 9L, null, List.of(),
                List.of("city"),
                List.of(new ComponentRef("gmv_total", "gmv"), new ComponentRef("order_cnt", "orders")),
                "gmv/orders", null, null, "def", "day", "CNY", "Asia/Shanghai", "alice");
        var q = MetricQueryCompiler.compileDerivedShell(derived, null, Map.of(
                "gmv", new MeasureSpec("amount", "SUM"),
                "orders", new MeasureSpec("id", "COUNT")));
        assertEquals(2, q.getMetrics().size());
        assertEquals(1, q.getLimit());
    }
}
