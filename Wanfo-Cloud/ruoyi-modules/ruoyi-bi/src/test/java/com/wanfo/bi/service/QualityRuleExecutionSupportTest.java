package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QualityRuleExecutionSupportTest {
    @Test
    void rangeCountsOutOfBoundValues() throws Exception {
        Method method = QualityTaskService.class.getDeclaredMethod("evaluateRange", List.class, String.class, Map.class);
        method.setAccessible(true);
        Object eval = method.invoke(null, List.of(Map.of("amount", 5), Map.of("amount", 15), Map.of("amount", 8)), "amount", Map.of("min", 1, "max", 10));
        assertEquals(1L, ((Number) eval.getClass().getMethod("bad").invoke(eval)).longValue());
    }

    @Test
    void regexCountsNonMatchingValues() throws Exception {
        Method method = QualityTaskService.class.getDeclaredMethod("evaluateRegex", List.class, String.class, Map.class);
        method.setAccessible(true);
        Object eval = method.invoke(null, List.of(Map.of("code", "AB1"), Map.of("code", "xx")), "code", Map.of("pattern", "^[A-Z]{2}\\d$"));
        assertEquals(1L, ((Number) eval.getClass().getMethod("bad").invoke(eval)).longValue());
    }

    @Test
    void reconciliationUsesToleranceAgainstBaseline() throws Exception {
        Method method = QualityTaskService.class.getDeclaredMethod("evaluateReconciliation", long.class, Long.class, Map.class);
        method.setAccessible(true);
        Object pass = method.invoke(null, 100L, 102L, Map.of("tolerance", 5));
        Object fail = method.invoke(null, 100L, 120L, Map.of("tolerance", 5));
        assertEquals(0L, ((Number) pass.getClass().getMethod("bad").invoke(pass)).longValue());
        assertEquals(1L, ((Number) fail.getClass().getMethod("bad").invoke(fail)).longValue());
    }

    @Test
    void rangeRequiresNumericSamples() throws Exception {
        Method method = QualityTaskService.class.getDeclaredMethod("evaluateRange", List.class, String.class, Map.class);
        method.setAccessible(true);
        Exception error = assertThrows(Exception.class, () -> method.invoke(null, List.of(Map.of("amount", "x")), "missing", Map.of("min", 1)));
        assertTrue(error.getCause().getMessage().contains("RANGE"));
    }
}
