package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanfo.bi.mapper.WarehouseLoadMapper;
import com.wanfo.bi.mapper.WarehouseTableModelMapper;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.CapabilityProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WarehouseTableModelServiceTest {
    WarehouseTableModelMapper mapper;
    WarehouseLoadMapper loads;
    WarehouseTableModelService service;

    @BeforeEach
    void setUp() {
        mapper = mock(WarehouseTableModelMapper.class);
        loads = mock(WarehouseLoadMapper.class);
        BiActor actor = new BiActor("test", 1L, "admin", null, Set.of());
        CapabilityProvider capabilities = mock(CapabilityProvider.class);
        service = new WarehouseTableModelService(mapper, loads, capabilities, () -> actor, new ObjectMapper());
    }

    @Test
    void createRejectsEmptyPrimaryKeys() {
        when(mapper.findByCode("dim_org_empty_pk")).thenReturn(null);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(new WarehouseTableModelService.UpsertRequest("dim_org_empty_pk", "组织维", "DWD",
                        "dim_org", "DIMENSION", List.of(), null, "REPLACE", null, "DRAFT")));
        assertTrue(error.getMessage().contains("主键"), error.getMessage());
    }

    @Test
    void createPersistsDimensionModel() {
        when(mapper.findByCode("dim_org")).thenReturn(null);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            Map<String, Object> row = invocation.getArgument(0);
            row.put("id", 9L);
            return 1;
        });
        Map<String, Object> stored = new HashMap<>();
        stored.put("id", 9L);
        stored.put("modelCode", "dim_org");
        stored.put("name", "组织维");
        stored.put("layer", "DWD");
        stored.put("tableName", "dim_org");
        stored.put("tableKind", "DIMENSION");
        stored.put("primaryKeysJson", "[\"org_id\"]");
        stored.put("updateStrategy", "REPLACE");
        stored.put("status", "ACTIVE");
        when(mapper.find(9L)).thenReturn(stored);
        Map<String, Object> created = service.create(new WarehouseTableModelService.UpsertRequest("dim_org", "组织维", "DWD",
                "dim_org", "DIMENSION", List.of("org_id"), null, "REPLACE", null, "ACTIVE"));
        assertEquals("DIMENSION", created.get("tableKind"));
        assertEquals(List.of("org_id"), created.get("primaryKeys"));
    }
}
