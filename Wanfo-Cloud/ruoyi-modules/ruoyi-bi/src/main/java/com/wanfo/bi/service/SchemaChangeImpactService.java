package com.wanfo.bi.service;

import com.wanfo.bi.mapper.SchemaChangeMapper;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Permission-filtered impact preview for metadata scan changes. */
@Service
public class SchemaChangeImpactService
{
    private final SchemaChangeMapper mapper;
    private final CatalogAssetService catalog;
    private final LineageGraphService lineage;
    private final OpsImpactPackService impactPack;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final ObjectMapper json;

    public SchemaChangeImpactService(SchemaChangeMapper mapper, CatalogAssetService catalog, LineageGraphService lineage,
            OpsImpactPackService impactPack, BiResourceAuthorizer resources, CapabilityProvider capabilities, ObjectMapper json)
    {
        this.mapper = mapper; this.catalog = catalog; this.lineage = lineage; this.impactPack = impactPack;
        this.resources = resources; this.capabilities = capabilities; this.json = json;
    }

    public List<Map<String,Object>> events(Long assetId)
    {
        catalog.detail(assetId);
        List<Map<String,Object>> result = new ArrayList<>();
        for (Map<String,Object> row : mapper.events(assetId))
        {
            Map<String,Object> item = new LinkedHashMap<>(row);
            item.put("details", parse(row.get("detailsJson")));
            item.remove("detailsJson");
            result.add(item);
        }
        return result;
    }

    public Map<String,Object> impact(Long eventId)
    {
        Map<String,Object> event = mapper.event(eventId);
        if (event == null) throw new IllegalArgumentException("结构变化事件不存在");
        Long assetId = number(event.get("assetId"));
        catalog.detail(assetId);
        Map<String,Object> graph = lineage.graph(assetId, "downstream", 6, true);
        Map<String,Object> details = asMap(parse(event.get("detailsJson")));
        Map<String,Object> fieldDiff = asMap(details.get("fields"));
        Set<String> affectedFields = affectedFieldNames(fieldDiff);
        List<Map<String,Object>> downstream = new ArrayList<>();
        LinkedHashSet<Long> datasetIds = new LinkedHashSet<>();
        for (Object value : list(graph.get("nodes")))
        {
            if (!(value instanceof Map<?,?> node)) continue;
            Long catalogNodeId = number(node.get("id"));
            if (catalogNodeId == null || catalogNodeId.equals(assetId)) continue;
            Map<String,Object> safeNode = stringMap(node);
            downstream.add(safeNode);
            if ("DATASET".equals(node.get("assetType")))
            {
                Long datasetId = mapper.datasetIdForAsset(catalogNodeId);
                if (datasetId != null) datasetIds.add(datasetId);
            }
        }
        datasetIds.remove(null);

        List<Map<String,Object>> qualityRules = new ArrayList<>();
        List<Map<String,Object>> datasetFields = new ArrayList<>();
        List<Map<String,Object>> qualityTasks = new ArrayList<>();
        List<Map<String,Object>> pipelines = new ArrayList<>();
        if (!datasetIds.isEmpty())
        {
            List<Long> visibleDatasetIds = datasetIds.stream().filter(id -> resources.allowed("dataset", id, "view")).toList();
            if (!visibleDatasetIds.isEmpty())
            {
                Map<String,String> fieldStates = fieldStates(fieldDiff);
                Map<Long,List<Map<String,Object>>> fieldsByDataset = new LinkedHashMap<>();
                for (Map<String,Object> field : mapper.datasetFields(visibleDatasetIds))
                    fieldsByDataset.computeIfAbsent(number(field.get("datasetId")), ignored -> new ArrayList<>()).add(field);
                for (Long datasetId : visibleDatasetIds)
                {
                    Set<String> matched = new LinkedHashSet<>();
                    for (Map<String,Object> field : fieldsByDataset.getOrDefault(datasetId, List.of()))
                    {
                        String name = field.get("fieldName") == null ? "" : String.valueOf(field.get("fieldName"));
                        String normalized = name.toLowerCase(java.util.Locale.ROOT);
                        if (!affectedFields.contains(normalized)) continue;
                        Map<String,Object> impactField = new LinkedHashMap<>(field);
                        impactField.put("impactStatus", fieldStates.getOrDefault(normalized, "SOURCE_FIELD_METADATA_CHANGED"));
                        datasetFields.add(impactField);
                        matched.add(normalized);
                    }
                    for (Map.Entry<String,String> state : fieldStates.entrySet())
                    {
                        if (!"SOURCE_FIELD_ADDED".equals(state.getValue()) || matched.contains(state.getKey())) continue;
                        Map<String,Object> missing = new LinkedHashMap<>();
                        missing.put("datasetId", datasetId); missing.put("fieldName", state.getKey());
                        missing.put("displayName", state.getKey()); missing.put("fieldType", "—");
                        missing.put("impactStatus", "NOT_IN_DATASET_MODEL");
                        datasetFields.add(missing);
                    }
                }
                for (Map<String,Object> row : mapper.qualityRules(visibleDatasetIds))
                {
                    Object rawField = row.get("fieldName");
                    if (!affectedFields.isEmpty() && (rawField == null || affectedFields.contains(String.valueOf(rawField).toLowerCase(java.util.Locale.ROOT))))
                        qualityRules.add(row);
                }
                Set<Long> affectedRuleSets = new LinkedHashSet<>();
                qualityRules.forEach(row -> { Long id = number(row.get("ruleSetId")); if (id != null) affectedRuleSets.add(id); });
                qualityTasks.addAll(mapper.qualityTasks(visibleDatasetIds).stream()
                        .filter(row -> affectedRuleSets.contains(number(row.get("ruleSetId")))).toList());
                if (capabilities.has("bi:pipeline:list"))
                    pipelines.addAll(mapper.pipelines(visibleDatasetIds).stream()
                            .filter(row -> resources.allowed("pipeline", number(row.get("pipelineId")), "view")).toList());
            }
        }

        Map<String,Object> eventView = new LinkedHashMap<>(event);
        eventView.put("details", details);
        eventView.remove("detailsJson");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("event", eventView);
        result.put("impactNodes", downstream);
        result.put("impactEdges", graph.get("edges"));
        result.put("datasetFields", datasetFields);
        result.put("qualityRules", qualityRules);
        result.put("qualityTasks", qualityTasks);
        result.put("pipelines", pipelines);
        result.put("affectedFields", affectedFields.stream().sorted().toList());
        result.put("renameCandidates", fieldDiff.getOrDefault("renameCandidates", List.of()));
        List<Map<String,Object>> joinSqlDatasets = new ArrayList<>();
        List<Map<String,Object>> migrationGuidance = new ArrayList<>();
        Long sourceDatasourceId = mapper.assetSourceId(assetId);
        if (sourceDatasourceId != null)
        {
            for (Map<String,Object> row : mapper.joinSqlDatasets(sourceDatasourceId))
            {
                Long dsId = number(row.get("datasetId"));
                if (dsId == null || !resources.allowed("dataset", dsId, "view")) continue;
                Map<String,Object> item = new LinkedHashMap<>(row);
                item.put("autoMigrateSupported", false);
                item.put("guidance", "JOIN/SQL/UNION 数据集不支持自动字段迁移；请人工调整模型后重新发布，或先阻断下游消费");
                item.put("blocked", hasBlockingDiff(fieldDiff));
                joinSqlDatasets.add(item);
                migrationGuidance.add(Map.of("datasetId", dsId, "action", "MANUAL_REMAP",
                        "reason", "非单表物理数据集，迁移计划仅支持 table 类型"));
            }
        }
        for (Object candidate : list(fieldDiff.get("renameCandidates")))
        {
            if (!(candidate instanceof Map<?,?> item)) continue;
            migrationGuidance.add(Map.of(
                    "action", "SEMI_AUTO_REMAP",
                    "from", String.valueOf(item.get("from")),
                    "to", String.valueOf(item.get("to")),
                    "hint", "可在迁移计划中将源字段映射到候选目标字段（类型一致时）"));
        }
        result.put("joinSqlDatasets", joinSqlDatasets);
        result.put("migrationGuidance", migrationGuidance);
        Map<String, Object> consumers = impactPack.consumersForDatasets(new ArrayList<>(datasetIds));
        result.put("metrics", consumers.getOrDefault("metrics", List.of()));
        result.put("charts", consumers.getOrDefault("charts", List.of()));
        result.put("dashboards", consumers.getOrDefault("dashboards", List.of()));
        result.put("dataApis", consumers.getOrDefault("dataApis", List.of()));
        boolean hasLiveApi = list(consumers.get("dataApis")).stream()
                .anyMatch(v -> v instanceof Map<?, ?> m && Set.of("ACTIVE", "DEPRECATED")
                        .contains(String.valueOf(m.get("status")).toUpperCase(java.util.Locale.ROOT)));
        String severity = hasBlockingDiff(fieldDiff) || hasLiveApi ? "BLOCKING"
                : (!joinSqlDatasets.isEmpty() || !list(consumers.get("metrics")).isEmpty() ? "REVIEW" : "COMPATIBLE");
        result.put("severity", severity);
        result.put("reviewRequired", hasBlockingDiff(fieldDiff) || !joinSqlDatasets.isEmpty() || hasLiveApi);
        result.put("truncated", graph.get("truncated"));
        result.put("counts", Map.of("assets", downstream.size(), "datasetFields", datasetFields.size(),
                "qualityRules", qualityRules.size(), "qualityTasks", qualityTasks.size(), "pipelines", pipelines.size(),
                "joinSqlDatasets", joinSqlDatasets.size(),
                "metrics", list(consumers.get("metrics")).size(),
                "charts", list(consumers.get("charts")).size(),
                "dashboards", list(consumers.get("dashboards")).size(),
                "dataApis", list(consumers.get("dataApis")).size()));
        return result;
    }

    private Object parse(Object value)
    {
        if (value == null) return Map.of();
        try { return json.readValue(String.valueOf(value), Object.class); }
        catch (RuntimeException ignored) { return Map.of(); }
    }

    private static Set<String> affectedFieldNames(Map<String,Object> diff)
    {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String key : List.of("added", "removed", "changed")) for (Object value : list(diff.get(key)))
            if (value != null) names.add(String.valueOf(value).toLowerCase(java.util.Locale.ROOT));
        for (Object value : list(diff.get("typeChanges"))) if (value instanceof Map<?,?> item && item.get("field") != null)
            names.add(String.valueOf(item.get("field")).toLowerCase(java.util.Locale.ROOT));
        for (Object value : list(diff.get("renameCandidates"))) if (value instanceof Map<?,?> item)
        {
            if (item.get("from") != null) names.add(String.valueOf(item.get("from")).toLowerCase(java.util.Locale.ROOT));
            if (item.get("to") != null) names.add(String.valueOf(item.get("to")).toLowerCase(java.util.Locale.ROOT));
        }
        return names;
    }

    private static boolean hasBlockingDiff(Map<String,Object> diff)
    {
        return !list(diff.get("removed")).isEmpty() || !list(diff.get("typeChanges")).isEmpty();
    }

    private static Map<String,String> fieldStates(Map<String,Object> diff)
    {
        Map<String,String> states = new LinkedHashMap<>();
        for (Object name : list(diff.get("added"))) if (name != null) states.put(String.valueOf(name).toLowerCase(java.util.Locale.ROOT), "SOURCE_FIELD_ADDED");
        for (Object name : list(diff.get("changed"))) if (name != null) states.putIfAbsent(String.valueOf(name).toLowerCase(java.util.Locale.ROOT), "SOURCE_FIELD_METADATA_CHANGED");
        for (Object change : list(diff.get("typeChanges"))) if (change instanceof Map<?,?> item && item.get("field") != null)
            states.put(String.valueOf(item.get("field")).toLowerCase(java.util.Locale.ROOT), "SOURCE_TYPE_CHANGED");
        for (Object name : list(diff.get("removed"))) if (name != null) states.put(String.valueOf(name).toLowerCase(java.util.Locale.ROOT), "SOURCE_FIELD_REMOVED");
        return states;
    }

    private static List<?> list(Object value) { return value instanceof List<?> items ? items : List.of(); }
    private static Map<String,Object> asMap(Object value) { return value instanceof Map<?,?> map ? stringMap(map) : Map.of(); }
    private static Map<String,Object> stringMap(Map<?,?> source) { Map<String,Object> result = new LinkedHashMap<>(); source.forEach((k,v)->result.put(String.valueOf(k),v)); return result; }
    private static Long number(Object value) { if (value instanceof Number n) return n.longValue(); try { return value == null ? null : Long.valueOf(value.toString()); } catch (NumberFormatException ignored) { return null; } }
}
