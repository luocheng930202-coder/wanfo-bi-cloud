package com.wanfo.bi.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WarehousePublisherModeTest {

    @Test
    void defaultsIncrementalToMergeAndFullToReplace() {
        assertEquals(WarehousePublisher.PublishMode.MERGE, WarehousePublisher.parseMode(null, "INCREMENTAL"));
        assertEquals(WarehousePublisher.PublishMode.REPLACE, WarehousePublisher.parseMode(null, "FULL"));
        assertEquals(WarehousePublisher.PublishMode.REPLACE, WarehousePublisher.parseMode("REPLACE", "INCREMENTAL"));
        assertEquals(WarehousePublisher.PublishMode.MERGE, WarehousePublisher.parseMode("merge", "FULL"));
    }

    @Test
    void rejectsUnknownPublishMode() {
        assertThrows(IllegalArgumentException.class, () -> WarehousePublisher.parseMode("APPEND", "FULL"));
    }
}
