package com.wanfo.bi.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetQualityTest {
    @Test void summarizesNullsDistinctValuesAndDuplicateRowsWithoutReturningSamples(){
        var rows=List.<Map<String,Object>>of(Map.of("city","A"),new LinkedHashMap<>(Map.of("city","A")),Collections.singletonMap("city",null));
        var report=DatasetQuality.summarize(List.of("city"),rows);assertEquals(3,report.get("sampledRows"));assertEquals(1,report.get("duplicateRows"));var stat=(Map<?,?>)((List<?>)report.get("columns")).get(0);assertEquals(1,stat.get("nullCount"));assertEquals(1,stat.get("duplicateValueCount"));assertFalse(report.containsKey("rows"));
    }
    @Test void fullScanReportsExactCountsAndRejectsImpossibleStatistics(){
        var report=DatasetQuality.fullScan(List.of("city"),10,new long[]{2},new long[]{3},1,42);
        assertEquals("full",report.get("scanMode"));assertEquals(10L,report.get("scannedRows"));assertEquals(1L,report.get("duplicateRows"));
        var stat=(Map<?,?>)((List<?>)report.get("columns")).get(0);assertEquals(8L,stat.get("nonNullCount"));assertEquals(3L,stat.get("distinctCount"));assertEquals(5L,stat.get("duplicateValueCount"));assertEquals(0.2,stat.get("nullRate"));
        assertThrows(IllegalArgumentException.class,()->DatasetQuality.fullScan(List.of("city"),1,new long[]{2},new long[]{0},0,0));
    }
}
