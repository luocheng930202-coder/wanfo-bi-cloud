package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.mapper.BiDashboardMapper;
import com.wanfo.bi.security.CapabilityProvider;
import org.springframework.stereotype.Service;

/** Aggregated BI home metrics; every count respects resource ACL. */
@Service
public class WorkbenchService {
    private final BiDataSourceService datasources;
    private final DatasetService datasets;
    private final ChartDefinitionService charts;
    private final DashboardService dashboards;
    private final DataFormService forms;
    private final ExportJobService exports;
    private final TemplateMarketService templateMarket;
    private final ScreenTemplateService screenTemplates;
    private final BiDashboardMapper dashboardMapper;
    private final CapabilityProvider caps;

    public WorkbenchService(BiDataSourceService datasources, DatasetService datasets, ChartDefinitionService charts,
            DashboardService dashboards, DataFormService forms, ExportJobService exports,
            TemplateMarketService templateMarket, ScreenTemplateService screenTemplates,
            BiDashboardMapper dashboardMapper, CapabilityProvider caps) {
        this.datasources = datasources;
        this.datasets = datasets;
        this.charts = charts;
        this.dashboards = dashboards;
        this.forms = forms;
        this.exports = exports;
        this.templateMarket = templateMarket;
        this.screenTemplates = screenTemplates;
        this.dashboardMapper = dashboardMapper;
        this.caps = caps;
    }

    public Map<String, Object> summary() {
        requireAnyList();
        int datasource = caps.has("bi:datasource:list") ? safeSize(() -> datasources.listForManage()) : 0;
        int dataset = caps.has("bi:dataset:list") ? safeSize(() -> datasets.list(null)) : 0;
        int chart = caps.has("bi:chart:list") ? charts.list().size() : 0;
        int panel = 0, screen = 0;
        if (caps.has("bi:dashboard:list")) {
            for (var row : dashboards.list()) {
                String kind = kindOf(row.get("id"));
                if ("screen".equals(kind)) screen++;
                else panel++;
            }
        }
        int form = caps.has("bi:form:list") ? safeSize(() -> forms.list()) : 0;
        int exportJob = caps.has("bi:export:list") ? safeSize(() -> exports.list()) : 0;
        int template = caps.has("bi:template:list") ? safeSize(() -> screenTemplates.listSummaries()) : 0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasource", datasource);
        result.put("dataset", dataset);
        result.put("chart", chart);
        result.put("dashboard", panel);
        result.put("screen", screen);
        result.put("form", form);
        result.put("export", exportJob);
        result.put("template", template);
        return result;
    }

    public List<Map<String, Object>> recent(String kind, int limit) {
        requireAnyList();
        int max = Math.min(Math.max(limit, 1), 30);
        String filter = kind == null ? "all" : kind.trim();
        List<Map<String, Object>> items = new ArrayList<>();
        if ("chart".equals(filter) || "all".equals(filter)) {
            if (caps.has("bi:chart:list")) {
                for (var c : charts.list()) {
                    items.add(Map.of("kind", "chart", "id", c.get("id"), "name", c.get("name"), "type", c.get("type")));
                }
            }
        }
        if (!"chart".equals(filter) && caps.has("bi:dashboard:list")) {
            for (var r : dashboards.list()) {
                Long id = (Long) r.get("id");
                String k = kindOf(id);
                if ("screen".equals(filter) && !"screen".equals(k)) continue;
                if ("dashboard".equals(filter) && "screen".equals(k)) continue;
                items.add(Map.of("kind", k, "id", id, "name", r.get("name"), "published", r.get("published")));
            }
        }
        items.sort((a, b) -> Long.compare(((Number) b.get("id")).longValue(), ((Number) a.get("id")).longValue()));
        return items.size() <= max ? items : items.subList(0, max);
    }

    public List<Map<String, Object>> templatePicks(int limit) {
        if (!caps.has("bi:template:list") && !caps.has("bi:dashboard:add"))
            throw new IllegalArgumentException("没有模板或看板创建权限");
        int max = Math.min(Math.max(limit, 1), 12);
        List<Map<String, Object>> picks = new ArrayList<>();
        for (var entry : templateMarket.catalog()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entry.get("id"));
            item.put("name", entry.get("name"));
            item.put("category", entry.get("category"));
            item.put("description", entry.get("description"));
            item.put("kind", entry.get("kind"));
            item.put("provider", entry.get("provider"));
            picks.add(item);
            if (picks.size() >= max) break;
        }
        return picks;
    }

    private void requireAnyList() {
        if (!caps.has("bi:datasource:list") && !caps.has("bi:dataset:list") && !caps.has("bi:chart:list")
                && !caps.has("bi:dashboard:list") && !caps.has("bi:form:list") && !caps.has("bi:export:list")
                && !caps.has("bi:template:list"))
            throw new IllegalArgumentException("没有可访问的 BI 资源列表权限");
    }

    private int safeSize(SupplierWithException supplier) {
        try { return supplier.get().size(); } catch (Exception e) { return 0; }
    }

    private String kindOf(Object id) {
        if (id == null) return "dashboard";
        var row = dashboardMapper.get(((Number) id).longValue());
        if (row == null || row.getDefinitionJson() == null) return "dashboard";
        return row.getDefinitionJson().contains("\"kind\":\"screen\"") ? "screen" : "dashboard";
    }

    @FunctionalInterface
    private interface SupplierWithException { List<?> get() throws Exception; }
}
