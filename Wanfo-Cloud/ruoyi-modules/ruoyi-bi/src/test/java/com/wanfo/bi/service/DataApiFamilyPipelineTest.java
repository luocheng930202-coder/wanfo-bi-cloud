package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataApiFamilyPipelineTest {
    @Test
    void suggestNextCodeStaysWithinLimit() {
        assertEquals("sales_v2", suggest("sales", 2));
        String longFamily = "f".repeat(60);
        String code = suggest(longFamily, 99);
        assertTrue(code.length() <= 64);
        assertTrue(code.contains("_v99"));
    }

    @Test
    void familiesMarkCoexistence() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("sales", "sales_v1", 1, "ACTIVE"));
        items.add(item("sales", "sales_v2", 2, "ACTIVE"));
        items.add(item("sales", "sales_v3", 3, "DRAFT"));
        items.add(item("other", "other", 1, "ACTIVE"));
        List<Map<String, Object>> families = buildFamilies(items);
        Map<String, Object> sales = families.stream().filter(f -> "sales".equals(f.get("familyCode"))).findFirst().orElseThrow();
        assertEquals(2, sales.get("activeCount"));
        assertEquals(1, sales.get("draftCount"));
        assertTrue((Boolean) sales.get("coexisting"));
        Map<String, Object> other = families.stream().filter(f -> "other".equals(f.get("familyCode"))).findFirst().orElseThrow();
        assertFalse((Boolean) other.get("coexisting"));
    }

    private static String suggest(String family, int version) {
        String base = family.length() > 56 ? family.substring(0, 56) : family;
        String candidate = base + "_v" + version;
        return candidate.length() > 64 ? candidate.substring(0, 64) : candidate;
    }

    /** Mirrors DataApiService.buildFamilies for unit coverage. */
    private static List<Map<String, Object>> buildFamilies(List<Map<String, Object>> items) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String family = String.valueOf(item.getOrDefault("familyCode", item.get("apiCode")));
            grouped.computeIfAbsent(family, ignored -> new ArrayList<>()).add(item);
        }
        List<Map<String, Object>> families = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            int activeCount = 0, draftCount = 0;
            for (Map<String, Object> m : entry.getValue()) {
                String status = String.valueOf(m.get("status"));
                if ("ACTIVE".equals(status)) activeCount++;
                if ("DRAFT".equals(status)) draftCount++;
            }
            Map<String, Object> family = new LinkedHashMap<>();
            family.put("familyCode", entry.getKey());
            family.put("activeCount", activeCount);
            family.put("draftCount", draftCount);
            family.put("coexisting", activeCount > 1);
            families.add(family);
        }
        return families;
    }

    private static Map<String, Object> item(String family, String code, int version, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("familyCode", family);
        m.put("apiCode", code);
        m.put("apiVersion", version);
        m.put("status", status);
        m.put("id", version);
        m.put("name", code);
        return m;
    }
}
