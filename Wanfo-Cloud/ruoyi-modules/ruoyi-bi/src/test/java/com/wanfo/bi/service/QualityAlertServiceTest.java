package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class QualityAlertServiceTest {

    @Test
    void dedupeKeyStablePerDatasetRuleSeverity() {
        String a = QualityAlertService.dedupeKey(10, 20, "HIGH");
        String b = QualityAlertService.dedupeKey(10, 20, "high");
        String c = QualityAlertService.dedupeKey(10, 21, "HIGH");
        assertEquals(64, a.length());
        assertEquals(a, b);
        assertNotEquals(a, c);
    }
}
