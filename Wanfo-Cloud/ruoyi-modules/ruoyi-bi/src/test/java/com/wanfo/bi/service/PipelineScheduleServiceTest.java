package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanfo.bi.mapper.PipelineMapper;
import com.wanfo.bi.mapper.PipelineScheduleMapper;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.LiveActorResolver;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PipelineScheduleServiceTest {
    PipelineScheduleMapper schedules = mock(PipelineScheduleMapper.class);
    PipelineMapper pipelines = mock(PipelineMapper.class);
    PipelineRunService runs = mock(PipelineRunService.class);
    BiResourceAuthorizer resources = mock(BiResourceAuthorizer.class);
    CapabilityProvider capabilities = mock(CapabilityProvider.class);
    BiActor actor = new BiActor("test", 7L, "admin", null, Set.of());
    LiveActorResolver live = ref -> new LiveActorResolver.Snapshot(ref, Set.of("bi:pipeline:run"));
    ObjectMapper json = new ObjectMapper();
    PipelineScheduleService service;

    @BeforeEach
    void setUp() {
        service = new PipelineScheduleService(schedules, pipelines, runs, resources, capabilities, () -> actor, live, json);
    }

    @Test
    void saveRequiresPublishedPipelineAndWarehouseTarget() {
        when(pipelines.pipeline(9L)).thenReturn(Map.of("id", 9L, "name", "demo", "currentRevisionId", 3L));
        when(schedules.byPipeline(9L)).thenReturn(null).thenReturn(Map.of(
                "id", 1L, "pipelineId", 9L, "name", "demo", "enabled", 1,
                "scheduleJson", "{\"kind\":\"interval\",\"intervalMinutes\":60,\"localTime\":\"\",\"weekdays\":[],\"timezone\":\"UTC\"}",
                "targetDatasourceId", 88L, "maxRows", 1000, "timeoutSeconds", 300));
        when(schedules.insert(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = invocation.getArgument(0);
            assertEquals(88L, ((Number) row.get("targetDatasourceId")).longValue());
            row.put("id", 1L);
            return 1;
        });

        Map<String, Object> saved = service.save(9L, new PipelineScheduleService.ScheduleRequest(
                "demo", true, null, 88L, 1000, 300, 60, null));
        assertEquals(1L, ((Number) saved.get("id")).longValue());
        verify(capabilities).require("bi:pipeline:run");
        verify(resources).require("pipeline", 9L, "run");
    }

    @Test
    void saveRejectsUnpublishedPipeline() {
        when(pipelines.pipeline(9L)).thenReturn(Map.of("id", 9L, "name", "demo"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.save(9L, new PipelineScheduleService.ScheduleRequest("demo", true, null, 88L, 1000, 300, 60, null)));
        assertTrue(error.getMessage().contains("尚未发布"));
    }
}
