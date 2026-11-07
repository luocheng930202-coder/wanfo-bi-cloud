package com.wanfo.bi.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LineageSourcePlannerTest {
    @Test void explicitJoinAndSqlTablesAreKeptWhenTheStatementIsPlain() {
        LineageSourcePlanner.Read read = LineageSourcePlanner.read(null,
                "{\"joins\":[{\"rightTable\":\"orders\"},{\"rightTable\":\"sales.pay\"}],\"sourceSql\":\"select id from campus\"}");
        assertEquals(List.of("orders", "pay", "campus"), read.tables());
        assertFalse(read.incomplete());
        assertEquals(List.of("orders", "pay"), read.joinTables());
    }

    @Test void subqueryAndUnmatchedFunctionCallStayIncomplete() {
        LineageSourcePlanner.Read nested = LineageSourcePlanner.read("select id from (select id from orders) t", null);
        assertTrue(nested.tables().isEmpty());
        assertTrue(nested.incomplete());

        LineageSourcePlanner.Read fn = LineageSourcePlanner.read("select * from read_csv('s3://bucket/a.csv')", null);
        assertTrue(fn.tables().isEmpty());
        assertTrue(fn.incomplete());
    }

    @Test void commentsStringsAndCommaFromDoNotInventTables() {
        LineageSourcePlanner.Read read = LineageSourcePlanner.read(
                "select id from orders -- from secret\njoin payments p on p.id=orders.id where name <> 'from fake'", null);
        assertEquals(List.of("orders", "payments"), read.tables());
        assertFalse(read.incomplete());

        LineageSourcePlanner.Read listed = LineageSourcePlanner.read("select * from orders, `Pay.Detail`", null);
        assertEquals(List.of("orders", "Detail"), listed.tables());
        assertFalse(listed.incomplete());
    }

    @Test void uniqueCatalogMatchIsTheOnlyHighConfidenceTable() {
        assertEquals(7L, LineageSourcePlanner.uniqueMatch(List.of(7L)));
        assertNull(LineageSourcePlanner.uniqueMatch(List.of()));
        assertNull(LineageSourcePlanner.uniqueMatch(List.of(1L, 2L)));
    }

    @Test void tableMayContainItsPhysicalColumn() {
        assertTrue(LineageService.endpointsCompatible("TABLE", "CONTAINS", "TABLE", "COLUMN"));
        assertTrue(LineageService.endpointsCompatible("TABLE", "CONTAINS", "DATASET", "DATASET_FIELD"));
        assertTrue(LineageService.endpointsCompatible("COLUMN", "REFERENCES", "DATASET_FIELD", "METRIC"));
        assertFalse(LineageService.endpointsCompatible("COLUMN", "CONTAINS", "TABLE", "COLUMN"));
        assertFalse(LineageService.endpointsCompatible("COLUMN", "CONTAINS", "DATASET", "DATASET_FIELD"));
        assertTrue(LineageService.endpointsCompatible("COLUMN", "REFERENCES", "COLUMN", "DATASET_FIELD"));
        assertFalse(LineageService.endpointsCompatible("TABLE", "REFERENCES", "COLUMN", "DATASET_FIELD"));
    }

    @Test void graphShowsIncompleteParseAndDatasetWideMetricFields() {
        assertEquals("解析不完整", LineageGraphService.relationNote("{\"completeness\":\"INCOMPLETE\",\"discovery\":\"sql\"}"));
        assertEquals("该数据集的全部字段", LineageGraphService.relationNote("{\"fieldScope\":\"DATASET_ALL_FIELDS\"}"));
        assertNull(LineageGraphService.relationNote("{\"discovery\":\"dataset-source-table-reference\"}"));
    }
}
