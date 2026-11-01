package com.wanfo.bi.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClickHouseBatchPublisherMapColumnsTest {
    @Test
    void infersIntegralAndNullableTypesFromSampleRows() throws Exception {
        Method method = ClickHouseBatchPublisher.class.getDeclaredMethod("mapColumns", List.class, List.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ClickHouseBatchPublisher.Column> columns = (List<ClickHouseBatchPublisher.Column>) method.invoke(
                null,
                List.of("id", "amount", "label"),
                List.of(
                        Map.of("id", 1, "amount", 1.5d, "label", "a"),
                        Map.of("id", 2L, "label", "b")));
        assertEquals(Types.BIGINT, columns.get(0).jdbcType());
        assertEquals(false, columns.get(0).nullable());
        assertEquals(Types.DOUBLE, columns.get(1).jdbcType());
        assertTrue(columns.get(1).nullable());
        assertEquals(Types.VARCHAR, columns.get(2).jdbcType());
    }

    @Test
    void rejectsDuplicateColumnNames() throws Exception {
        Method method = ClickHouseBatchPublisher.class.getDeclaredMethod("mapColumns", List.class, List.class);
        method.setAccessible(true);
        Exception error = assertThrows(Exception.class, () -> method.invoke(null, List.of("Id", "id"), List.of()));
        assertTrue(error.getCause() instanceof IllegalArgumentException);
        assertTrue(error.getCause().getMessage().contains("重复"));
    }
}
