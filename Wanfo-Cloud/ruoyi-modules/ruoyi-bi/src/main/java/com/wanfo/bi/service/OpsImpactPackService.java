package com.wanfo.bi.service;

import com.wanfo.bi.mapper.SchemaChangeMapper;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * S3-04: one-shot ops impact pack across warehouse loads, pipelines, metrics, charts, dashboards and Data APIs.
 * Read-only aggregation over existing facts; does not invent missing lineage.
 */
@Service
public class OpsImpactPackService {
    private final SchemaChangeMapper mapper;
    private final CatalogAssetService catalog;
    private final LineageGraphService lineage;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;

    public OpsImpactPackService(SchemaChangeMapper mapper, CatalogAssetService catalog, LineageGraphService lineage,
            BiResourceAuthorizer resources, CapabilityProvider capabilities) {
        this.mapper = mapper;
        this.catalog = catalog;
        this.lineage = lineage;
        this.resources = resources;
        this.capabilities = capabilities;
    }

    public Map<String, Object> packForAsset(Long assetId) {
        capabilities.require("bi:datasource:model");
        Map<String, Object> detail = catalog.detail(assetId);
        Map<String, Object> asset = mapper.assetRow(assetId);
        if (asset == null) throw new IllegalArgumentException("资产不存在");
        Long datasourceId = number(asset.get("sourceId"));
        String tableName = String.valueOf(asset.get("name"));
        Map<String, Object> graph = lineage.graph(assetId, "downstream", 6, true);
        LinkedHashSet<Long> datasetIds = new LinkedHashSet<>();
        List<Map<String, Object>> downstream = new ArrayList<>();
        for (Object value : list(graph.get("nodes"))) {
            if (!(value instanceof Map<?, ?> node)) continue;
            Long nodeId = number(node.get("id"));
            if (nodeId == null || nodeId.equals(assetId)) continue;
            downstream.add(stringMap(node));
            if ("DATASET".equals(node.get("assetType"))) {
                Long datasetId = mapper.datasetIdForAsset(nodeId);
                if (datasetId != null) datasetIds.add(datasetId);
            }
        }
        if (datasourceId != null && tableName != null && !tableName.isBlank()) {
            for (Map<String, Object> ds : mapper.physicalDatasets(datasourceId, tableName)) {
                Long id = number(ds.get("datasetId"));
                if (id != null) datasetIds.add(id);
            }
        }
        datasetIds.remove(null);
        List<Long> allDatasets = List.copyOf(datasetIds);

        RedactSplit physicalSplit = datasourceId == null
                ? RedactSplit.empty()
                : splitByAccess(mapper.physicalDatasets(datasourceId, tableName), "dataset", "datasetId", "datasetName");
        RedactSplit metricSplit = allDatasets.isEmpty() || !capabilities.has("bi:metric:list")
                ? RedactSplit.empty()
                : splitByAccess(mapper.metrics(allDatasets), "metric", "id", "name");
        RedactSplit chartSplit = allDatasets.isEmpty() || !capabilities.has("bi:chart:list")
                ? RedactSplit.empty()
                : splitByAccess(mapper.charts(allDatasets), "chart", "id", "name");
        RedactSplit dashSplit = allDatasets.isEmpty() || !capabilities.has("bi:dashboard:list")
                ? RedactSplit.empty()
                : splitByAccess(mapper.dashboards(allDatasets), "dashboard", "dashboardId", "dashboardName");
        RedactSplit apiSplit = allDatasets.isEmpty()
                ? RedactSplit.empty()
                : splitByAccess(mapper.dataApis(allDatasets), "data_api", "id", "name");
        RedactSplit pipelineSplit = RedactSplit.empty();
        if (!allDatasets.isEmpty() && capabilities.has("bi:pipeline:list")) {
            pipelineSplit = splitByAccess(mapper.pipelines(allDatasets), "pipeline", "pipelineId", "pipelineName");
        }
        List<Map<String, Object>> warehouseLoads = List.of();
        List<Map<String, Object>> cdcJobs = List.of();
        int warehouseRedacted = 0;
        int cdcRedacted = 0;
        if (datasourceId != null) {
            List<Map<String, Object>> rawLoads = mapper.warehouseLoads(datasourceId, tableName);
            List<Map<String, Object>> rawCdc = mapper.cdcJobs(datasourceId, tableName);
            if (capabilities.has("bi:resource:admin")) {
                warehouseLoads = rawLoads;
                cdcJobs = rawCdc;
            } else {
                warehouseRedacted = rawLoads.size();
                cdcRedacted = rawCdc.size();
                warehouseLoads = redactedPlaceholders(warehouseRedacted, "id", "sourceTable", "WAREHOUSE_LOAD");
                cdcJobs = redactedPlaceholders(cdcRedacted, "id", "name", "CDC_JOB");
            }
        }

        List<Map<String, Object>> physicalDatasets = physicalSplit.visible();
        List<Map<String, Object>> metrics = metricSplit.visible();
        List<Map<String, Object>> charts = chartSplit.visible();
        List<Map<String, Object>> dashboards = dashSplit.visible();
        List<Map<String, Object>> dataApis = apiSplit.visible();
        List<Map<String, Object>> pipelines = pipelineSplit.visible();

        List<Map<String, Object>> consumers = new ArrayList<>();
        severityItems(consumers, "DATASET", physicalDatasets, "datasetId", "datasetName", "COMPATIBLE");
        severityItems(consumers, "DATASET", physicalSplit.redacted(), "datasetId", "datasetName", "COMPATIBLE");
        severityItems(consumers, "METRIC", metrics, "id", "name", metricSeverity(metrics));
        severityItems(consumers, "METRIC", metricSplit.redacted(), "id", "name", "REVIEW");
        severityItems(consumers, "CHART", charts, "id", "name", "REVIEW");
        severityItems(consumers, "CHART", chartSplit.redacted(), "id", "name", "REVIEW");
        severityItems(consumers, "DASHBOARD", dashboards, "dashboardId", "dashboardName", dashboardSeverity(dashboards));
        severityItems(consumers, "DASHBOARD", dashSplit.redacted(), "dashboardId", "dashboardName", "REVIEW");
        severityItems(consumers, "DATA_API", dataApis, "id", "name", apiSeverity(dataApis));
        // Unauthorized live APIs still raise REVIEW (not BLOCKING) — no status leaked.
        severityItems(consumers, "DATA_API", apiSplit.redacted(), "id", "name", "REVIEW");
        severityItems(consumers, "PIPELINE", pipelines, "pipelineId", "pipelineName", "REVIEW");
        severityItems(consumers, "PIPELINE", pipelineSplit.redacted(), "pipelineId", "pipelineName", "REVIEW");
        severityItems(consumers, "WAREHOUSE_LOAD", warehouseLoads, "id", "sourceTable", "REVIEW");
        severityItems(consumers, "CDC_JOB", cdcJobs, "id", "name", "REVIEW");

        int redactedCount = physicalSplit.redacted().size() + metricSplit.redacted().size()
                + chartSplit.redacted().size() + dashSplit.redacted().size() + apiSplit.redacted().size()
                + pipelineSplit.redacted().size() + warehouseRedacted + cdcRedacted;

        String overall = overallSeverity(consumers, Boolean.TRUE.equals(graph.get("truncated")));
        if (redactedCount > 0 && "NONE".equals(overall)) overall = "REVIEW";
        List<String> checklist = buildChecklist(overall, metrics, dataApis, pipelines, warehouseLoads, cdcJobs,
                Boolean.TRUE.equals(graph.get("truncated")));
        if (redactedCount > 0) checklist.add("存在无权查看的消费者占位（已脱敏）；请联系资源所有者确认影响面");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("severity", overall);
        summary.put("datasetCount", physicalDatasets.size());
        summary.put("metricCount", metrics.size());
        summary.put("chartCount", charts.size());
        summary.put("dashboardCount", dashboards.size());
        summary.put("dataApiCount", dataApis.size());
        summary.put("pipelineCount", pipelines.size());
        summary.put("warehouseLoadCount", warehouseLoads.size());
        summary.put("cdcJobCount", cdcJobs.size());
        summary.put("downstreamAssetCount", downstream.size());
        summary.put("redactedConsumerCount", redactedCount);
        summary.put("truncated", graph.get("truncated"));
        summary.put("lineageIncompleteHint", Boolean.TRUE.equals(graph.get("truncated"))
                || (downstream.isEmpty() && !allDatasets.isEmpty()));

        Map<String, Object> assetView = new LinkedHashMap<>();
        assetView.put("id", assetId);
        assetView.put("name", asset.get("name") == null ? "" : asset.get("name"));
        assetView.put("assetType", asset.get("assetType") == null ? "" : asset.get("assetType"));
        Object qn = asset.get("qualifiedName");
        if (qn == null) qn = detail.get("qualifiedName");
        assetView.put("qualifiedName", qn == null ? "" : qn);
        assetView.put("sourceId", datasourceId == null ? 0L : datasourceId);
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("asset", assetView);
        pack.put("summary", summary);
        pack.put("checklist", checklist);
        pack.put("datasets", concat(physicalDatasets, physicalSplit.redacted()));
        pack.put("metrics", concat(metrics, metricSplit.redacted()));
        pack.put("charts", concat(charts, chartSplit.redacted()));
        pack.put("dashboards", concat(dashboards, dashSplit.redacted()));
        pack.put("dataApis", concat(dataApis, apiSplit.redacted()));
        pack.put("pipelines", concat(pipelines, pipelineSplit.redacted()));
        pack.put("warehouseLoads", warehouseLoads);
        pack.put("cdcJobs", cdcJobs);
        pack.put("downstreamAssets", downstream);
        pack.put("consumers", consumers);
        pack.put("note", "运维影响包为只读聚合：优先血缘下游 + 同源物理表数据集扇出；无权消费者以脱敏占位保留数量，不泄露名称/状态细节。");
        return pack;
    }

    /** Write gate: BLOCKING consumers refuse structure migration execute unless override is true. */
    public Map<String, Object> changeGate(Long assetId, boolean allowOverride) {
        Map<String, Object> pack = packForAsset(assetId);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = pack.get("summary") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        String severity = String.valueOf(summary.getOrDefault("severity", "REVIEW"));
        boolean blocking = "BLOCKING".equals(severity);
        boolean allowed = !blocking || allowOverride;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assetId", assetId);
        out.put("severity", severity);
        out.put("blocking", blocking);
        out.put("allowed", allowed);
        out.put("override", allowOverride);
        out.put("summary", summary);
        out.put("checklist", pack.get("checklist"));
        out.put("note", blocking
                ? (allowOverride ? "存在 BLOCKING 消费者，已显式覆盖放行" : "存在 BLOCKING 消费者（如活跃 Data API），拒绝写入变更")
                : "未发现 BLOCKING 消费者");
        return out;
    }

    public void assertWritable(Long assetId, boolean allowOverride) {
        Map<String, Object> gate = changeGate(assetId, allowOverride);
        if (!Boolean.TRUE.equals(gate.get("allowed")))
            throw new IllegalStateException("运维影响写入门禁阻断：severity=BLOCKING；请先处理活跃 Data API/指标消费者或带 override=true");
    }

    /** Shared consumer fan-out for schema-change impact enrichment. */
    public Map<String, Object> consumersForDatasets(List<Long> datasetIds) {
        List<Long> visible = datasetIds == null ? List.of()
                : datasetIds.stream().filter(id -> id != null && resources.allowed("dataset", id, "view")).distinct().toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metrics", visible.isEmpty() ? List.of() : filterMetrics(mapper.metrics(visible)));
        out.put("charts", visible.isEmpty() ? List.of() : filterCharts(mapper.charts(visible)));
        out.put("dashboards", visible.isEmpty() ? List.of() : filterDashboards(mapper.dashboards(visible)));
        out.put("dataApis", visible.isEmpty() ? List.of() : mapper.dataApis(visible));
        return out;
    }

    private List<Map<String, Object>> filterMetrics(List<Map<String, Object>> rows) {
        if (!capabilities.has("bi:metric:list")) return List.of();
        return rows.stream().filter(r -> resources.allowed("metric", number(r.get("id")), "view")).toList();
    }

    private List<Map<String, Object>> filterCharts(List<Map<String, Object>> rows) {
        if (!capabilities.has("bi:chart:list")) return List.of();
        return rows.stream().filter(r -> resources.allowed("chart", number(r.get("id")), "view")).toList();
    }

    private List<Map<String, Object>> filterDashboards(List<Map<String, Object>> rows) {
        if (!capabilities.has("bi:dashboard:list")) return List.of();
        return rows.stream().filter(r -> resources.allowed("dashboard", number(r.get("dashboardId")), "view")).toList();
    }

    private record RedactSplit(List<Map<String, Object>> visible, List<Map<String, Object>> redacted) {
        static RedactSplit empty() { return new RedactSplit(List.of(), List.of()); }
    }

    private RedactSplit splitByAccess(List<Map<String, Object>> rows, String resourceType, String idKey, String nameKey) {
        List<Map<String, Object>> visible = new ArrayList<>();
        List<Map<String, Object>> redacted = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long id = number(row.get(idKey));
            if (id != null && resources.allowed(resourceType, id, "view")) {
                visible.add(row);
            } else {
                Map<String, Object> stub = new LinkedHashMap<>();
                stub.put(idKey, 0L);
                stub.put(nameKey, "[无权查看]");
                stub.put("redacted", true);
                stub.put("redactedReason", "NO_RESOURCE_VIEW");
                redacted.add(stub);
            }
        }
        return new RedactSplit(List.copyOf(visible), List.copyOf(redacted));
    }

    private static List<Map<String, Object>> redactedPlaceholders(int count, String idKey, String nameKey, String kind) {
        if (count <= 0) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> stub = new LinkedHashMap<>();
            stub.put(idKey, 0L);
            stub.put(nameKey, "[无权查看]");
            stub.put("redacted", true);
            stub.put("redactedReason", "NO_ADMIN_CAPABILITY");
            stub.put("consumerKind", kind);
            out.add(stub);
        }
        return out;
    }

    private static List<Map<String, Object>> concat(List<Map<String, Object>> a, List<Map<String, Object>> b) {
        if (b == null || b.isEmpty()) return a;
        if (a == null || a.isEmpty()) return b;
        List<Map<String, Object>> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    private static void severityItems(List<Map<String, Object>> sink, String type, List<Map<String, Object>> rows,
            String idKey, String nameKey, String severity) {
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("consumerType", type);
            item.put("id", row.get(idKey));
            item.put("name", row.get(nameKey));
            item.put("severity", severity);
            item.put("redacted", Boolean.TRUE.equals(row.get("redacted")));
            item.put("detail", Boolean.TRUE.equals(row.get("redacted")) ? Map.of("redacted", true) : row);
            sink.add(item);
        }
    }

    private static String metricSeverity(List<Map<String, Object>> metrics) {
        boolean published = metrics.stream().anyMatch(m -> "published".equalsIgnoreCase(String.valueOf(m.get("publishStatus"))));
        return published ? "REVIEW" : "COMPATIBLE";
    }

    private static String dashboardSeverity(List<Map<String, Object>> dashboards) {
        boolean published = dashboards.stream().anyMatch(d -> {
            Object p = d.get("published");
            return p instanceof Number n && n.intValue() == 1 || Boolean.TRUE.equals(p) || "1".equals(String.valueOf(p));
        });
        return published ? "REVIEW" : "COMPATIBLE";
    }

    private static String apiSeverity(List<Map<String, Object>> apis) {
        boolean live = apis.stream().anyMatch(a -> {
            String st = String.valueOf(a.get("status")).toUpperCase(Locale.ROOT);
            return "ACTIVE".equals(st) || "DEPRECATED".equals(st);
        });
        return live ? "BLOCKING" : "REVIEW";
    }

    private static String overallSeverity(List<Map<String, Object>> consumers, boolean truncated) {
        Set<String> levels = new LinkedHashSet<>();
        for (Map<String, Object> c : consumers) levels.add(String.valueOf(c.get("severity")));
        if (levels.contains("BLOCKING")) return "BLOCKING";
        if (levels.contains("REVIEW") || truncated) return "REVIEW";
        if (levels.contains("COMPATIBLE")) return "COMPATIBLE";
        return "NONE";
    }

    private static List<String> buildChecklist(String severity, List<Map<String, Object>> metrics,
            List<Map<String, Object>> apis, List<Map<String, Object>> pipelines,
            List<Map<String, Object>> loads, List<Map<String, Object>> cdc, boolean truncated) {
        List<String> items = new ArrayList<>();
        items.add("确认变更窗口与回滚负责人");
        if ("BLOCKING".equals(severity) || !apis.isEmpty())
            items.add("通知 Data API 调用方；ACTIVE/DEPRECATED 接口需兼容窗口或切换继任版本");
        if (!metrics.isEmpty()) items.add("核对已发布指标口径与字段映射，必要时冻结发布");
        if (!pipelines.isEmpty()) items.add("暂停或观察相关数据加工流程运行");
        if (!loads.isEmpty()) items.add("检查近期数仓装载任务与 checkpoint 是否依赖被改字段");
        if (!cdc.isEmpty()) items.add("确认 CDC 编排的 staging/target 表与水位列仍有效");
        if (truncated) items.add("血缘结果已截断：补充人工排查无权或未登记依赖");
        items.add("变更后复跑落标检查 / 质量任务抽检");
        return items;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private static Long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
