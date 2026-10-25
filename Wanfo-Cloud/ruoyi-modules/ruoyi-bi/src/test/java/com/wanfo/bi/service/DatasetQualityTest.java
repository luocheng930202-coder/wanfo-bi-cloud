package com.wanfo.bi.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetQualityTest {
    @Test void summarizesNullsDistinctValuesAndDuplicateRowsWithoutReturningSamples(){
        var rows=List.<Map<String,Object>>of(Map.of("city","A"),new LinkedHashMap<>(Map.of("city","A")),Collections.singletonMap("city",null));
        var report=DatasetQuality.summarize(List.of("city"),rows);assertEquals(3,report.get("sampledRows"));assertEquals(1,report.get("duplicateRows"));var stat=(Map<?,?>)((List<?>)report.get("columns")).get(0);assertEquals(1,stat.get("nullCount"));assertEquals(1,stat.get("duplicateValueCount"));assertFalse(report.containsKey("rows"));
    }
}
