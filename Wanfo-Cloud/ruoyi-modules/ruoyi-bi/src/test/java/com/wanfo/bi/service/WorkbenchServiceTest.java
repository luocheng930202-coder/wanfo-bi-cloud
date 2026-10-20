package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import com.wanfo.bi.mapper.BiDashboardMapper;
import com.wanfo.bi.security.CapabilityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkbenchServiceTest {
    BiDataSourceService datasources = mock(BiDataSourceService.class);
    DatasetService datasets = mock(DatasetService.class);
    ChartDefinitionService charts = mock(ChartDefinitionService.class);
    DashboardService dashboards = mock(DashboardService.class);
    DataFormService forms = mock(DataFormService.class);
    ExportJobService exports = mock(ExportJobService.class);
    TemplateMarketService market = mock(TemplateMarketService.class);
    ScreenTemplateService screenTemplates = mock(ScreenTemplateService.class);
    BiDashboardMapper dashboardMapper = mock(BiDashboardMapper.class);
    CapabilityProvider caps = mock(CapabilityProvider.class);
    WorkbenchService service = new WorkbenchService(datasources, datasets, charts, dashboards, forms, exports, market,
            screenTemplates, dashboardMapper, caps);

    @BeforeEach
    void allow() {
        when(caps.has("bi:dashboard:list")).thenReturn(true);
        when(caps.has("bi:chart:list")).thenReturn(true);
    }

    @Test
    void recentAllMergesAndSortsByIdDesc() {
        when(charts.list()).thenReturn(List.of(Map.of("id", 5L, "name", "c5", "type", "bar")));
        when(dashboards.list()).thenReturn(List.of(Map.of("id", 10L, "name", "d10", "published", true)));
        when(dashboardMapper.get(10L)).thenReturn(null);
        var rows = service.recent("all", 10);
        assertEquals(2, rows.size());
        assertEquals(10L, ((Number) rows.get(0).get("id")).longValue());
        assertEquals("dashboard", rows.get(0).get("kind"));
    }

    @Test
    void templatePicksRequirePermission() {
        when(caps.has("bi:template:list")).thenReturn(false);
        when(caps.has("bi:dashboard:add")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.templatePicks(3));
    }
}
