package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DataApiContractGateTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void extractAndPassWithoutBaseline() {
        Map<String, Object> sample = Map.of(
                "columns", List.of("region", "amount"),
                "rows", List.of(Map.of("region", "北京", "amount", 12.5)));
        var schema = DataApiContractGate.extract(sample, 7L, 3L);
        var report = DataApiContractGate.compare(schema, null, 1);
        assertTrue(report.passed());
        assertEquals(2, report.proposed().columns().size());
        assertEquals("NUMBER", report.proposed().columns().get(1).valueKind());
    }

    @Test
    void blocksRemovedColumnAndTypeChange() {
        var baseline = new DataApiContractGate.Schema(List.of(
                new DataApiContractGate.Column("region", "STRING"),
                new DataApiContractGate.Column("amount", "NUMBER")), 1L, 1L);
        var proposed = new DataApiContractGate.Schema(List.of(
                new DataApiContractGate.Column("region", "NUMBER"),
                new DataApiContractGate.Column("extra", "STRING")), 1L, 2L);
        var report = DataApiContractGate.compare(proposed, baseline, 0);
        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(i -> "COLUMN_REMOVED".equals(i.code())));
        assertTrue(report.issues().stream().anyMatch(i -> "TYPE_CHANGED".equals(i.code())));
        assertTrue(report.issues().stream().anyMatch(i -> "COLUMN_ADDED".equals(i.code())));
    }

    @Test
    void roundTripSchemaJson() {
        var schema = new DataApiContractGate.Schema(List.of(
                new DataApiContractGate.Column("value", "NUMBER")), 9L, 2L);
        String raw = json.writeValueAsString(DataApiContractGate.toMap(schema, json));
        var restored = DataApiContractGate.fromJson(raw, json);
        assertEquals(schema.columns(), restored.columns());
        assertEquals(9L, restored.metricId());
    }
}
