package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ObjectStorageLoadFormatTest {
    @Test
    void normalizeFormatDefaultsToAuto() {
        assertEquals("AUTO", ObjectStorageLoadService.normalizeFormat(null));
        assertEquals("AUTO", ObjectStorageLoadService.normalizeFormat(" "));
        assertEquals("CSV", ObjectStorageLoadService.normalizeFormat("csv"));
        assertEquals("PARQUET", ObjectStorageLoadService.normalizeFormat("Parquet"));
        assertThrows(IllegalArgumentException.class, () -> ObjectStorageLoadService.normalizeFormat("orc"));
    }

    @Test
    void matchesFormatByExtension() {
        assertTrue(ObjectStorageLoadService.matchesFormat("a/b.csv", "AUTO"));
        assertTrue(ObjectStorageLoadService.matchesFormat("a/b.parquet", "AUTO"));
        assertFalse(ObjectStorageLoadService.matchesFormat("a/b.json", "AUTO"));
        assertTrue(ObjectStorageLoadService.matchesFormat("x.CSV", "CSV"));
        assertFalse(ObjectStorageLoadService.matchesFormat("x.parquet", "CSV"));
        assertTrue(ObjectStorageLoadService.matchesFormat("x.parquet", "PARQUET"));
        assertFalse(ObjectStorageLoadService.matchesFormat("x.csv", "PARQUET"));
    }
}
