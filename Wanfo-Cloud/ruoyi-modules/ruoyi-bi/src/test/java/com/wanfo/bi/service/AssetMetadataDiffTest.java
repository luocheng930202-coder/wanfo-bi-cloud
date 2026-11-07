package com.wanfo.bi.service;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssetMetadataDiffTest {
    @Test void commentOnlyColumnIgnoresOtherAttributeChanges() {
        assertTrue(AssetMetadataDiff.commentOnlyColumn(Map.of("name", "id", "type", "INT", "comment", ""), Map.of("name", "id", "type", "INT", "comment", "主键")));
        assertFalse(AssetMetadataDiff.commentOnlyColumn(Map.of("name", "id", "type", "INT"), Map.of("name", "id", "type", "BIGINT", "comment", "主键")));
    }

    @Test void liveApiDoesNotBlockCommentOnlyChanges() {
        assertEquals("COMPATIBLE", AssetMetadataDiff.impactSeverity(false, true, true, false, true));
        assertEquals("BLOCKING", AssetMetadataDiff.impactSeverity(false, false, true, false, false));
        assertEquals("BLOCKING", AssetMetadataDiff.impactSeverity(true, true, false, false, false));
        assertEquals("REVIEW", AssetMetadataDiff.impactSeverity(false, false, false, true, false));
    }
}
