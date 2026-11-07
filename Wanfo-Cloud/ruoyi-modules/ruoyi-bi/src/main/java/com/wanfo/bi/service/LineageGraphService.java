package com.wanfo.bi.service;

import com.wanfo.bi.mapper.LineageMapper;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Permission-filtered, bounded traversal over persisted lineage edges. */
@Service
public class LineageGraphService
{
    private static final int MAX_DEPTH = 6;
    private static final int MAX_NODES = 200;
    private static final int MAX_EDGES = 500;
    private static final int EDGE_SCAN_LIMIT = 1000;
    private final LineageMapper mapper;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;

    public LineageGraphService(LineageMapper mapper, BiResourceAuthorizer resources, CapabilityProvider capabilities)
    {
        this.mapper = mapper;
        this.resources = resources;
        this.capabilities = capabilities;
    }

    public Map<String, Object> graph(Long rootId, String direction, int requestedDepth, boolean includeStale)
    {
        String flow = direction == null || direction.isBlank() ? "both" : direction.trim().toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("both", "upstream", "downstream").contains(flow)) throw new IllegalArgumentException("血缘方向仅支持 both、upstream 或 downstream");
        if (rootId == null || rootId < 1) throw new IllegalArgumentException("资产 ID 无效");
        int depth = requestedDepth < 1 ? 3 : requestedDepth;
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("查询深度不能超过 " + MAX_DEPTH);

        Map<String, Object> root = loadVisible(rootId);
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        nodes.put(rootId, safeNode(root));
        List<Map<String, Object>> graphEdges = new ArrayList<>();
        Set<Long> seenEdges = new LinkedHashSet<>();
        List<Long> frontier = List.of(rootId);
        boolean truncated = false;

        for (int level = 0; level < depth && !frontier.isEmpty(); level++)
        {
            List<Map<String, Object>> candidates = mapper.touchingEdges(frontier, EDGE_SCAN_LIMIT);
            if (candidates.size() >= EDGE_SCAN_LIMIT) truncated = true;
            if (candidates.isEmpty()) break;
            LinkedHashSet<Long> candidateAssetIds = new LinkedHashSet<>();
            for (Map<String, Object> edge : candidates)
            {
                Long source = number(edge.get("sourceAssetId"));
                Long target = number(edge.get("targetAssetId"));
                if (source != null && target != null && follows(flow, frontier, source, target)
                        && (includeStale || "ACTIVE".equals(edge.get("effectiveStatus"))))
                {
                    candidateAssetIds.add(source);
                    candidateAssetIds.add(target);
                }
            }
            if (candidateAssetIds.isEmpty()) break;

            Map<Long, Map<String, Object>> visible = new LinkedHashMap<>();
            for (List<Long> batch : batches(new ArrayList<>(candidateAssetIds), 300))
                for (Map<String, Object> asset : mapper.assets(batch))
                {
                    Long id = number(asset.get("id"));
                    if (id != null && canView(asset)) visible.put(id, asset);
                }

            LinkedHashSet<Long> next = new LinkedHashSet<>();
            for (Map<String, Object> edge : candidates)
            {
                Long source = number(edge.get("sourceAssetId"));
                Long target = number(edge.get("targetAssetId"));
                Long edgeId = number(edge.get("id"));
                if (source == null || target == null || edgeId == null || !follows(flow, frontier, source, target)
                        || (!includeStale && !"ACTIVE".equals(edge.get("effectiveStatus")))
                        || !visible.containsKey(source) || !visible.containsKey(target)) continue;
                if (nodes.size() >= MAX_NODES && (!nodes.containsKey(source) || !nodes.containsKey(target)))
                {
                    truncated = true;
                    continue;
                }
                boolean sourceNew = !nodes.containsKey(source);
                boolean targetNew = !nodes.containsKey(target);
                nodes.computeIfAbsent(source, ignored -> safeNode(visible.get(source)));
                nodes.computeIfAbsent(target, ignored -> safeNode(visible.get(target)));
                if (seenEdges.add(edgeId))
                {
                    if (graphEdges.size() >= MAX_EDGES) { truncated = true; break; }
                    graphEdges.add(safeEdge(edge));
                }
                if (sourceNew && level + 1 < depth) next.add(source);
                if (targetNew && level + 1 < depth) next.add(target);
            }
            next.removeAll(frontier);
            if (level + 1 >= depth) next.clear();
            frontier = List.copyOf(next);
        }

        return Map.of("rootAssetId", rootId, "direction", flow, "depth", depth,
                "nodes", List.copyOf(nodes.values()), "edges", List.copyOf(graphEdges), "truncated", truncated,
                "limits", Map.of("maxDepth", MAX_DEPTH, "maxNodes", MAX_NODES, "maxEdges", MAX_EDGES));
    }

    private Map<String, Object> loadVisible(Long id)
    {
        List<Map<String, Object>> rows = mapper.assets(List.of(id));
        if (rows.isEmpty() || !canView(rows.get(0))) throw new BiAccessException(404, "资产不存在或无权查看");
        return rows.get(0);
    }

    private boolean canView(Map<String, Object> asset)
    {
        if (!capabilities.has("bi:datasource:model")) return false;
        String sourceKind = String.valueOf(asset.get("sourceKind"));
        String type = String.valueOf(asset.get("assetType"));
        Long sourceId = number(asset.get("sourceId"));
        if ("CONNECTOR".equals(sourceKind))
            return sourceId != null && resources.allowed("datasource", sourceId, "view");
        if (!"BI_RESOURCE".equals(sourceKind) || sourceId == null) return false;
        String resourceType;
        switch (type)
        {
            case "DATASOURCE", "API_SOURCE" -> resourceType = "datasource";
            case "DATASET", "DATASET_FIELD" -> resourceType = "dataset";
            case "CHART" -> resourceType = "chart";
            case "DASHBOARD" -> resourceType = "dashboard";
            case "METRIC" -> resourceType = "metric";
            case "DATA_API" -> resourceType = "data_api";
            default -> { return false; }
        }
        Long resourceId = "DATASET_FIELD".equals(type) ? number(asset.get("parentSourceId")) : sourceId;
        return resourceId != null && resources.allowed(resourceType, resourceId, "view");
    }

    private static boolean follows(String direction, List<Long> frontier, Long source, Long target)
    {
        boolean from = frontier.contains(source), to = frontier.contains(target);
        return switch (direction) { case "downstream" -> from; case "upstream" -> to; default -> from || to; };
    }

    private static Map<String, Object> safeNode(Map<String, Object> asset)
    {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", asset.get("id")); node.put("assetType", asset.get("assetType"));
        node.put("name", asset.get("name")); node.put("qualifiedName", asset.get("qualifiedName"));
        node.put("status", asset.get("status")); node.put("currentVersionId", asset.get("currentVersionId"));
        return node;
    }

    private static Map<String, Object> safeEdge(Map<String, Object> edge)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        for (String field : List.of("id", "sourceAssetId", "targetAssetId", "lineageLevel", "edgeKind", "sourceMethod", "confidence", "effectiveStatus"))
            item.put(field, edge.get(field));
        String note = relationNote(edge.get("evidenceJson"));
        if (note != null) item.put("relationNote", note);
        return item;
    }

    static String relationNote(Object evidence) {
        if (evidence == null) return null;
        String text = String.valueOf(evidence);
        boolean incomplete = text.contains("\"completeness\":\"INCOMPLETE\"") || text.contains("\"completeness\": \"INCOMPLETE\"");
        boolean allFields = text.contains("\"fieldScope\":\"DATASET_ALL_FIELDS\"") || text.contains("\"fieldScope\": \"DATASET_ALL_FIELDS\"");
        if (incomplete && allFields) return "解析不完整；该数据集的全部字段";
        if (incomplete) return "解析不完整";
        if (allFields) return "该数据集的全部字段";
        return null;
    }

    private static List<List<Long>> batches(List<Long> values, int size)
    {
        List<List<Long>> result = new ArrayList<>();
        for (int start = 0; start < values.size(); start += size) result.add(values.subList(start, Math.min(values.size(), start + size)));
        return result;
    }

    private static Long number(Object value)
    {
        if (value instanceof Number n) return n.longValue();
        try { return value == null ? null : Long.valueOf(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }
}
