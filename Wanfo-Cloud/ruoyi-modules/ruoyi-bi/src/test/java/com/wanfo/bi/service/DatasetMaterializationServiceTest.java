package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.mapper.WarehouseLoadMapper;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.LiveActorResolver;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DatasetMaterializationServiceTest {
    CapabilityProvider capabilities = mock(CapabilityProvider.class);
    DatasetMaterializationService service;

    @BeforeEach
    void setUp() {
        BiActor actor = new BiActor("test", 1L, "admin", null, Set.of());
        service = new DatasetMaterializationService(mock(WarehouseLoadMapper.class), mock(BiDatasetTableMapper.class),
                mock(DatasetService.class), mock(DataSourceProvider.class), mock(BiResourceAuthorizer.class),
                capabilities, () -> actor, ref -> new LiveActorResolver.Snapshot(ref, Set.of("*:*:*")),
                mock(WarehouseScopeService.class),
                Runnable::run, mock(DataSource.class), new ObjectMapper(), 1000, 10_000);
    }

    @Test
    void packagesExposeRetailExampleSeparatelyFromGenericMaterialize() {
        List<Map<String, Object>> packages = service.packages();
        assertEquals(1, packages.size());
        assertEquals("retail-v1", packages.get(0).get("code"));
        assertEquals(false, packages.get(0).get("generic"));
        assertEquals("EXAMPLE", packages.get(0).get("kind"));
    }

    @Test
    void materializeRejectsUnsupportedWriteMode() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.trigger(new DatasetMaterializationService.MaterializeRequest(1L, 2L, "DWS", "agg_demo", 100, "APPEND"), "t"));
        assertTrue(error.getMessage().contains("REPLACE"));
    }
}
