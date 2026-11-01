package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.mapper.PipelineMapper;
import com.wanfo.bi.mapper.PipelineRunMapper;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.LiveActorResolver;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PipelineProductionSubmitTest {
    PipelineRunMapper runs = mock(PipelineRunMapper.class);
    PipelineMapper pipelines = mock(PipelineMapper.class);
    DatasetService datasets = mock(DatasetService.class);
    BiResourceAuthorizer resources = mock(BiResourceAuthorizer.class);
    CapabilityProvider capabilities = mock(CapabilityProvider.class);
    QualityRuleVersionService qualityRules = mock(QualityRuleVersionService.class);
    DataSourceProvider sources = mock(DataSourceProvider.class);
    BiActor actor = new BiActor("test", 1L, "admin", null, Set.of());
    LiveActorResolver live = ref -> new LiveActorResolver.Snapshot(ref, Set.of("bi:pipeline:run"));
    ExecutorService executor;
    ScheduledExecutorService timer;
    PipelineRunService service;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        TaskPoolGateService poolGate = mock(TaskPoolGateService.class);
        service = new PipelineRunService(runs, pipelines, datasets, resources, capabilities, qualityRules,
                () -> actor, live, sources, mock(FieldStandardService.class), poolGate, new ObjectMapper(), executor,
                timer, 1000);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        timer.shutdownNow();
    }

    @Test
    void productionRequiresWarehouseTarget() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.submitProduction(9L, 3L, "request-key-1", 60, null, 100, "trace"));
        assertTrue(error.getMessage().contains("目标数据源"));
        verify(runs, never()).insert(any());
    }

    @Test
    void productionRejectsNonClickHouseTarget() {
        BiDatasource target = new BiDatasource();
        target.setId(88L);
        target.setStatus("0");
        target.setType("mysql");
        target.setSourceSchema("wanfo_wh_ods");
        when(sources.getById(88L)).thenReturn(target);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.submitProduction(9L, 3L, "request-key-2", 60, 88L, 100, "trace"));
        assertTrue(error.getMessage().contains("ClickHouse"));
    }

    @Test
    void productionCreatesRunWithProductionMode() {
        BiDatasource target = new BiDatasource();
        target.setId(88L);
        target.setStatus("0");
        target.setType("clickhouse");
        target.setSourceSchema("wanfo_wh_ods");
        when(sources.getById(88L)).thenReturn(target);
        when(runs.byRequestKey(9L, "request-key-3")).thenReturn(null);
        when(pipelines.revision(9L, 3L)).thenReturn(Map.of("status", "PUBLISHED", "definitionHash", "abc"));
        when(runs.activeCount(9L)).thenReturn(0);
        when(runs.insert(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = invocation.getArgument(0);
            assertEquals("PRODUCTION", row.get("mode"));
            row.put("id", 41L);
            return 1;
        });
        when(pipelines.nodes(3L)).thenReturn(java.util.List.of());
        when(runs.pipelineId(41L)).thenReturn(9L);
        when(runs.run(41L)).thenReturn(Map.of("id", 41L, "runMode", "PRODUCTION", "status", "PENDING"));
        when(runs.nodes(41L)).thenReturn(java.util.List.of());
        when(runs.start(41L)).thenReturn(0);

        Map<String, Object> detail = service.submitProduction(9L, 3L, "request-key-3", 60, 88L, 100, "trace");
        assertEquals(41L, ((Number) detail.get("id")).longValue());
        assertEquals("PRODUCTION", detail.get("runMode"));
        verify(resources).require("pipeline", 9L, "run");
        verify(capabilities).require("bi:pipeline:run");
        verify(runs).insert(any());
    }

    @Test
    void previewStillUsesPreviewMode() {
        when(runs.byRequestKey(9L, "request-key-4")).thenReturn(null);
        when(pipelines.revision(9L, 3L)).thenReturn(Map.of("status", "PUBLISHED", "definitionHash", "abc"));
        when(runs.activeCount(9L)).thenReturn(0);
        when(runs.insert(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = invocation.getArgument(0);
            assertEquals("PREVIEW", row.get("mode"));
            row.put("id", 42L);
            return 1;
        });
        when(pipelines.nodes(3L)).thenReturn(java.util.List.of());
        when(runs.pipelineId(42L)).thenReturn(9L);
        when(runs.run(42L)).thenReturn(Map.of("id", 42L, "runMode", "PREVIEW", "status", "PENDING"));
        when(runs.nodes(42L)).thenReturn(java.util.List.of());
        when(runs.start(anyLong())).thenReturn(0);

        Map<String, Object> detail = service.submitPreview(9L, 3L, "request-key-4", 30, "trace");
        assertEquals("PREVIEW", detail.get("runMode"));
        verify(sources, never()).getById(anyLong());
    }
}
