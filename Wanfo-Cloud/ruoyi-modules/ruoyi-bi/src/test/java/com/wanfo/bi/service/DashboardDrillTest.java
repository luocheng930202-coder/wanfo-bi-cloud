package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wanfo.bi.domain.BiDashboardDO;
import com.wanfo.bi.domain.chart.ChartAdvancedOptions;
import com.wanfo.bi.domain.query.QueryModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardDrillTest {
    DashboardTest f = new DashboardTest();

    ChartDefinitionService.Definition drillChart() {
        var q = new QueryModel();
        q.setDatasetId(9L);
        q.addDimension("region", "place");
        q.addMetric("id", "COUNT", "n");
        var adv = new ChartAdvancedOptions();
        adv.setDrillFields(List.of("region", "city"));
        return new ChartDefinitionService.Definition(1, "Drill", "bar", q, null, adv);
    }

    void publishedDrill() throws Exception {
        var c = drillChart();
        when(f.charts.exportForDashboard(4L)).thenReturn(c);
        when(f.queries.datasetFieldsForDashboard(9L)).thenReturn(List.of(Map.of("name", "region", "type", "VARCHAR"), Map.of("name", "city", "type", "VARCHAR")));
        var row = new BiDashboardDO();
        row.setId(7L);
        row.setVersion(2);
        row.setDefinitionJson(f.json.writeValueAsString(f.definition(List.of(f.panel("a", 0, 0)))));
        row.setPublishedVersion(1);
        row.setPublishedJson(f.json.writeValueAsString(new DashboardService.Publication(1, "Public",
                List.of(new DashboardService.PublishedPanel(f.panel("a", 0, 0), c)))));
        when(f.mapper.get(7L)).thenReturn(row);
        when(f.queries.queryForDashboard(any())).thenReturn(Map.of("columns", List.of("place", "n"), "rows", List.of()));
    }

    @Test
    void drillPathChangesQueryDimension() throws Exception {
        publishedDrill();
        f.service.panelData(7L, "a", 1, Map.of("drills", Map.of("a", List.of(Map.of("field", "region", "value", "华东")))));
        var capture = org.mockito.ArgumentCaptor.forClass(QueryModel.class);
        verify(f.queries).queryForDashboard(capture.capture());
        assertEquals("city", capture.getValue().getDimensions().get(0).getField());
        assertEquals("华东", capture.getValue().getFilters().get(0).getValue());
    }

    @Test
    void drillAndLinkageSourceAreExclusiveAtValidation() {
        var panels = List.of(new DashboardService.PublishedPanel(f.panel("a", 0, 0), drillChart()),
                new DashboardService.PublishedPanel(f.panel("b", 6, 0), f.chart()));
        var fields = Map.of("a", Map.of("region", "VARCHAR", "city", "VARCHAR"), "b", Map.of("city", "VARCHAR", "id", "INT"));
        var links = new DashboardInteraction.Settings(null,
                List.of(new DashboardInteraction.Link("x", "a", "region", List.of(new DashboardInteraction.Target("b", "city")))));
        assertThrows(IllegalArgumentException.class, () -> DashboardInteraction.validate(links, panels, fields));
    }
    @Test void publishedPresentationPreservesMetricAggregationMetadata() throws Exception {
        publishedDrill();var response=f.service.panelData(7L,"a",1,Map.of());
        var presentation=(Map<?,?>)response.get("presentation");
        var metrics=(List<?>)presentation.get("queryMetrics");var metric=(QueryModel.Metric)metrics.get(0);
        assertEquals("COUNT",metric.getAgg());assertEquals("id",metric.getField());assertEquals("n",metric.getAlias());
        assertFalse(presentation.containsKey("filters"));assertFalse(presentation.containsKey("sql"));
    }
}
