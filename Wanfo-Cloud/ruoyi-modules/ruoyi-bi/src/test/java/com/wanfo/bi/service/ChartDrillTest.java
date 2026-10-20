package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.*;

import com.wanfo.bi.domain.chart.ChartAdvancedOptions;
import com.wanfo.bi.domain.query.QueryModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChartDrillTest {
    @Test
    void applyPathFiltersAndSwitchesDimension() {
        var q = new QueryModel();
        q.setDatasetId(1L);
        q.addDimension("region", "region");
        q.addMetric("amount", "SUM", "amount");
        var path = List.of(new ChartDrill.Segment("region", "华东"));
        ChartDrill.apply(q, List.of("region", "city", "store"), path);
        assertEquals("city", q.getDimensions().get(0).getField());
        assertEquals(1, q.getFilters().size());
        assertEquals("华东", q.getFilters().get(0).getValue());
    }

    @Test
    void parsePathRejectsMismatchedField() {
        var types = Map.of("region", "VARCHAR", "city", "VARCHAR");
        assertThrows(IllegalArgumentException.class,
                () -> ChartDrill.parsePath(Map.of("drills", Map.of("a", List.of(Map.of("field", "city", "value", "华东")))),
                        "a", List.of("region", "city"), types));
        assertThrows(IllegalArgumentException.class,
                () -> DashboardInteraction.validateDrillMap(Map.of("drills", Map.of("forged", List.of())), java.util.Set.of("a")));
    }

    @Test
    void validateRequiresFirstDimensionMatch() {
        var q = new QueryModel();
        q.setDatasetId(1L);
        q.addDimension("region", "region");
        var adv = new ChartAdvancedOptions();
        adv.setDrillFields(List.of("region", "city"));
        var def = new ChartDefinitionService.Definition(1, "x", "bar", q, null, adv);
        assertDoesNotThrow(() -> ChartDrill.validate(def, Map.of("region", "VARCHAR", "city", "VARCHAR")));
        adv.setDrillFields(List.of("city", "store"));
        assertThrows(IllegalArgumentException.class, () -> ChartDrill.validate(def, Map.of("city", "VARCHAR", "store", "VARCHAR")));
    }
}
