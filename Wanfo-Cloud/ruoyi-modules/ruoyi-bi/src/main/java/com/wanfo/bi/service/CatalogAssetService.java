package com.wanfo.bi.service;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.mapper.BiMetadataScanMapper;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Permission-filtered search and version history for connector-backed catalog assets. */
@Service
public class CatalogAssetService
{
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> TYPES = List.of("SCHEMA", "TABLE", "COLUMN");
    private static final List<String> BI_RESOURCE_TYPES = List.of("CHART", "DASHBOARD", "DATASET", "DATASET_FIELD", "DATASOURCE", "API_SOURCE", "METRIC");

    private final BiMetadataScanMapper mapper;
    private final DataSourceProvider sources;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final ObjectMapper json;
    private final CurrentActorProvider actors;

    public CatalogAssetService(BiMetadataScanMapper mapper, DataSourceProvider sources,
            BiResourceAuthorizer resources, CapabilityProvider capabilities, ObjectMapper json, CurrentActorProvider actors)
    {
        this.mapper = mapper;
        this.sources = sources;
        this.resources = resources;
        this.capabilities = capabilities;
        this.json = json;
        this.actors = actors;
    }

    public Map<String, Object> backfillPreview()
    {
        capabilities.require("bi:resource:admin");
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("DATASOURCE", mapper.backfillDataSources().size());
        counts.put("API_SOURCE", mapper.backfillApiSources().size());
        counts.put("DATASET", mapper.backfillDatasets().size());
        counts.put("DATASET_FIELD", mapper.backfillDatasetFields().size());
        counts.put("CHART", mapper.backfillCharts().size());
        counts.put("DASHBOARD", mapper.backfillDashboards().size());
        counts.put("METRIC", mapper.backfillMetrics().size());
        counts.put("total", counts.values().stream().mapToInt(value -> ((Number) value).intValue()).sum());
        counts.put("mode", "DRY_RUN");
        counts.put("copiedDefinitions", false);
        return counts;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> backfill()
    {
        capabilities.require("bi:resource:admin");
        int createdOrUpdated = 0;
        int versionsCreated = 0;
        String actor = actors.currentActor().username();
        Map<Long, Long> datasetAssetIds = new LinkedHashMap<>();
        Map<Long, Long> datasourceAssetIds = new LinkedHashMap<>();
        List<Map<String, Object>> resourcesToBackfill = new ArrayList<>();
        addBackfill(resourcesToBackfill, "DATASOURCE", "datasource", mapper.backfillDataSources());
        addBackfill(resourcesToBackfill, "API_SOURCE", "api-source", mapper.backfillApiSources());
        addBackfill(resourcesToBackfill, "DATASET", "dataset", mapper.backfillDatasets());
        addBackfill(resourcesToBackfill, "DATASET_FIELD", "dataset-field", mapper.backfillDatasetFields());
        addBackfill(resourcesToBackfill, "CHART", "chart", mapper.backfillCharts());
        addBackfill(resourcesToBackfill, "DASHBOARD", "dashboard", mapper.backfillDashboards());
        addBackfill(resourcesToBackfill, "METRIC", "metric", mapper.backfillMetrics());
        for (Map<String, Object> item : resourcesToBackfill)
        {
            String urn = String.valueOf(item.get("urn"));
            String metadata = writeJson(item.get("metadata"));
            String urnHash = sha256(urn);
            String schemaHash = sha256(metadata);
            Map<String, Object> command = new LinkedHashMap<>();
            command.put("type", item.get("type")); command.put("sourceId", item.get("sourceId"));
            command.put("urn", urn); command.put("urnHash", urnHash); command.put("name", item.get("name"));
            command.put("qualifiedName", urn); command.put("description", item.get("description")); command.put("actor", actor);
            command.put("ownerPrincipalId", item.get("ownerPrincipalId"));
            Object datasetRef = ((Map<?, ?>) item.get("metadata")).get("datasetId");
            if (datasetRef instanceof Number datasetId) command.put("parentId", datasetAssetIds.get(datasetId.longValue()));
            Object datasourceRef = ((Map<?, ?>) item.get("metadata")).get("datasourceId");
            if ("API_SOURCE".equals(item.get("type")) && datasourceRef instanceof Number datasourceId)
                command.put("parentId", datasourceAssetIds.get(datasourceId.longValue()));
            mapper.upsertBackfillAsset(command);
            Map<String, Object> current = mapper.assetByHash(urnHash);
            Long assetId = ((Number) current.get("id")).longValue();
            if ("DATASET".equals(item.get("type"))) datasetAssetIds.put(((Number) item.get("sourceId")).longValue(), assetId);
            if ("DATASOURCE".equals(item.get("type"))) datasourceAssetIds.put(((Number) item.get("sourceId")).longValue(), assetId);
            Object currentVersionId = current.get("currentVersionId");
            int versionNo = 1;
            boolean unchanged = false;
            if (currentVersionId != null)
            {
                Map<String, Object> oldVersion = mapper.assetVersion(((Number) currentVersionId).longValue());
                versionNo = ((Number) oldVersion.get("versionNo")).intValue() + 1;
                unchanged = schemaHash.equals(oldVersion.get("schemaHash"));
            }
            if (!unchanged)
            {
                Map<String, Object> version = new LinkedHashMap<>();
                version.put("assetId", assetId); version.put("versionNo", versionNo); version.put("schemaHash", schemaHash);
                version.put("metadata", metadata); version.put("changeSummary", "{\"changeType\":\"BI_RESOURCE_BACKFILL\"}");
                version.put("runId", null); version.put("actor", actor);
                mapper.insertAssetVersion(version);
                mapper.setCurrentAssetVersion(assetId, ((Number) version.get("id")).longValue(), actor);
                versionsCreated++;
            }
            createdOrUpdated++;
        }
        return Map.of("mode", "EXECUTED", "assetsProcessed", createdOrUpdated, "versionsCreated", versionsCreated,
                "copiedDefinitions", false, "at", LocalDateTime.now().toString());
    }

    private void addBackfill(List<Map<String, Object>> target, String type, String urnType, List<Map<String, Object>> rows)
    {
        for (Map<String, Object> row : rows)
        {
            Object id = row.get("id");
            String name = String.valueOf(row.getOrDefault("name", type + " " + id));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("resourceType", type); metadata.put("resourceId", id); metadata.put("name", name);
            for (String key : List.of("type", "schemaName", "status", "datasourceId", "sourceTable", "datasetId", "dashboardId", "showName", "dimFlag", "extFlag", "code", "kind", "publishStatus", "ownerPrincipalId", "snapshotKind", "rowCount", "snapshotVersion", "refreshedAt"))
                if (row.containsKey(key) && row.get(key) != null) metadata.put(key, row.get(key));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type); item.put("sourceId", id); item.put("name", name);
            item.put("urn", "wanfo-bi://" + urnType + "/" + id); item.put("metadata", metadata);
            Object description = row.get("remark");
            if (description != null) item.put("description", description);
            target.add(item);
        }
    }

    private String writeJson(Object value)
    {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("无法生成资产元数据", exception); }
    }

    private String sha256(String value)
    {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("无法计算资产指纹", exception); }
    }

    public Map<String, Object> search(Long sourceId, String type, Long parentId, String query, int page, int pageSize)
    {
        return search("CONNECTOR", sourceId, type, parentId, query, page, pageSize);
    }

    public Map<String, Object> search(String sourceKind, Long sourceId, String type, Long parentId, String query, int page, int pageSize)
    {
        capabilities.require("bi:datasource:model");
        if (page < 1 || page > 100_000 || pageSize < 1 || pageSize > MAX_PAGE_SIZE)
            throw new IllegalArgumentException("分页参数无效");
        if (query != null && query.length() > 200) throw new IllegalArgumentException("搜索内容最多 200 字符");
        String normalizedType = type == null || type.isBlank() ? null : type.trim().toUpperCase(java.util.Locale.ROOT);
        if ("BI_RESOURCE".equalsIgnoreCase(sourceKind)) return searchBiResources(normalizedType, query, page, pageSize);
        if (sourceKind != null && !sourceKind.isBlank() && !"CONNECTOR".equalsIgnoreCase(sourceKind))
            throw new IllegalArgumentException("资产来源类型无效");
        if (normalizedType != null && !TYPES.contains(normalizedType)) throw new IllegalArgumentException("资产类型无效");

        List<Long> visibleSources = visibleSourceIds(sourceId);
        if (visibleSources.isEmpty()) return Map.of("items", List.of(), "total", 0L, "page", page, "pageSize", pageSize);
        int offset = Math.multiplyExact(page - 1, pageSize);
        String normalizedQuery = query == null ? "" : query.trim();
        List<Map<String, Object>> items = mapper.searchAssets(visibleSources, normalizedType, parentId,
                normalizedQuery, offset, pageSize);
        long total = mapper.countAssets(visibleSources, normalizedType, parentId, normalizedQuery);
        return Map.of("items", items, "total", total, "page", page, "pageSize", pageSize);
    }

    private Map<String, Object> searchBiResources(String type, String query, int page, int pageSize)
    {
        if (type != null && !BI_RESOURCE_TYPES.contains(type)) throw new IllegalArgumentException("BI 资源类型无效");
        List<String> types = type == null ? BI_RESOURCE_TYPES : List.of(type);
        String normalizedQuery = query == null ? "" : query.trim();
        Map<String, List<Long>> visible = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        for (String assetType : types)
        {
            String permissionType = permissionType(assetType);
            List<Long> resourceIds = resources.visibleIds(permissionType, "view");
            if (resourceIds.isEmpty()) continue;
            visible.put(assetType, resourceIds);
            long count = mapper.countVisibleBiAssets(assetType, resourceIds, normalizedQuery);
            counts.put(assetType, count);
            total += count;
        }

        int offset = Math.multiplyExact(page - 1, pageSize);
        int remainingOffset = offset;
        List<Map<String, Object>> items = new ArrayList<>(pageSize);
        for (String assetType : types)
        {
            Long count = counts.get(assetType);
            if (count == null || count == 0) continue;
            if (remainingOffset >= count) { remainingOffset -= count.intValue(); continue; }
            int requested = pageSize - items.size();
            items.addAll(mapper.searchVisibleBiAssets(assetType, visible.get(assetType), normalizedQuery, remainingOffset, requested));
            remainingOffset = 0;
            if (items.size() >= pageSize) break;
        }
        return Map.of("items", items, "total", total, "page", page, "pageSize", pageSize);
    }

    public Map<String, Object> detail(Long id)
    {
        capabilities.require("bi:datasource:model");
        Map<String, Object> asset = visibleAsset(id);
        return asset;
    }

    /** Deep links for catalog detail CTAs: apply / analyze / generate API. */
    public Map<String, Object> consumeLinks(Long id)
    {
        capabilities.require("bi:datasource:model");
        Map<String, Object> asset = visibleAsset(id);
        String assetType = String.valueOf(asset.getOrDefault("assetType", ""));
        String sourceKind = String.valueOf(asset.getOrDefault("sourceKind", ""));
        Long sourceId = asset.get("sourceId") instanceof Number n ? n.longValue() : null;
        String urn = asset.get("urn") == null ? null : String.valueOf(asset.get("urn"));
        String permissionType = permissionType(assetType);
        boolean hasQuery = sourceId != null && resources.allowed(permissionType, sourceId, "query");
        List<Map<String, Object>> actions = new ArrayList<>();
        if ("BI_RESOURCE".equalsIgnoreCase(sourceKind)
                && List.of("DATASET", "METRIC", "CHART", "DASHBOARD", "DATASOURCE", "API_SOURCE").contains(assetType))
        {
            Map<String, Object> apply = new LinkedHashMap<>();
            apply.put("key", "apply");
            apply.put("label", "申请使用");
            apply.put("path", "/bi-admin/access-requests");
            apply.put("query", Map.of(
                    "resourceType", permissionType,
                    "resourceId", sourceId == null ? 0 : sourceId,
                    "assetUrn", urn == null ? "" : urn,
                    "assetName", String.valueOf(asset.getOrDefault("name", ""))));
            actions.add(apply);
        }
        Map<String, Object> analyze = new LinkedHashMap<>();
        analyze.put("key", "analyze");
        analyze.put("label", "去分析");
        switch (assetType)
        {
            case "DATASET" -> {
                analyze.put("path", "/bi-admin/chart-workspace/new");
                analyze.put("query", Map.of("datasetId", sourceId == null ? 0 : sourceId));
                analyze.put("enabled", hasQuery || sourceId != null);
            }
            case "METRIC" -> {
                analyze.put("path", "/bi-admin/metric");
                analyze.put("query", Map.of("metricId", sourceId == null ? 0 : sourceId));
                analyze.put("enabled", sourceId != null);
            }
            case "CHART" -> {
                analyze.put("path", "/bi-admin/chart-workspace/" + (sourceId == null ? 0 : sourceId) + "/view");
                analyze.put("query", Map.of());
                analyze.put("enabled", sourceId != null);
            }
            case "DASHBOARD" -> {
                analyze.put("path", "/bi-admin/dashboard-workspace/" + (sourceId == null ? 0 : sourceId) + "/view");
                analyze.put("query", Map.of());
                analyze.put("enabled", sourceId != null);
            }
            case "TABLE" -> {
                analyze.put("path", "/bi-admin/dataset");
                analyze.put("query", Map.of("sourceId", sourceId == null ? 0 : sourceId,
                        "table", String.valueOf(asset.getOrDefault("name", ""))));
                analyze.put("enabled", sourceId != null);
            }
            default -> analyze.put("enabled", false);
        }
        if (Boolean.TRUE.equals(analyze.get("enabled"))) actions.add(analyze);
        if ("METRIC".equals(assetType) && sourceId != null)
        {
            Map<String, Object> api = new LinkedHashMap<>();
            api.put("key", "generateApi");
            api.put("label", "生成 API");
            api.put("path", "/bi-admin/data-api");
            api.put("query", Map.of("metricId", sourceId));
            api.put("enabled", true);
            actions.add(api);
        }
        Map<String, Object> result = new LinkedHashMap<>(asset);
        result.put("consumeActions", actions);
        result.put("permissionType", permissionType);
        return result;
    }

    public List<Map<String, Object>> versions(Long id)
    {
        capabilities.require("bi:datasource:model");
        Map<String, Object> asset = visibleAsset(id);
        Long assetId = ((Number) asset.get("id")).longValue();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : mapper.assetVersions(assetId))
        {
            Map<String, Object> version = new LinkedHashMap<>(row);
            version.put("metadata", parseJson(row.get("metadataJson")));
            version.put("changes", parseJson(row.get("changeSummaryJson")));
            version.remove("metadataJson");
            version.remove("changeSummaryJson");
            result.add(version);
        }
        return result;
    }

    private Map<String, Object> visibleAsset(Long id)
    {
        if (id == null || id < 1) throw new IllegalArgumentException("资产 ID 无效");
        Map<String, Object> candidate = mapper.assetById(id);
        if (candidate == null) throw new BiAccessException(404, "资产不存在或无权查看");
        if ("BI_RESOURCE".equals(candidate.get("sourceKind")))
        {
            String assetType = String.valueOf(candidate.get("assetType"));
            String permissionType = permissionType(assetType);
            Object resourceId = assetType.equals("DATASET_FIELD") ? candidate.get("parentSourceId") : candidate.get("sourceId");
            if (!(resourceId instanceof Number number) || !resources.allowed(permissionType, number.longValue(), "view"))
                throw new BiAccessException(404, "资产不存在或无权查看");
            return candidate;
        }
        List<Long> visible = visibleSourceIds(null);
        if (visible.isEmpty()) throw new BiAccessException(404, "资产不存在或无权查看");
        Map<String, Object> asset = mapper.visibleAsset(id, visible);
        if (asset == null) throw new BiAccessException(404, "资产不存在或无权查看");
        return asset;
    }

    private List<Long> visibleSourceIds(Long requested)
    {
        List<Long> ids = new ArrayList<>();
        for (BiDatasource source : sources.listEnabled())
        {
            if (source == null || source.getId() == null || !"0".equals(source.getStatus())) continue;
            if (requested != null && !requested.equals(source.getId())) continue;
            if (resources.allowed("datasource", source.getId(), "view")) ids.add(source.getId());
        }
        if (requested != null && ids.isEmpty()) throw new BiAccessException(403, "没有该数据源的查看权限");
        return List.copyOf(ids);
    }

    private static String permissionType(String assetType)
    {
        if ("DATASET_FIELD".equals(assetType)) return "dataset";
        if ("API_SOURCE".equals(assetType)) return "datasource";
        return assetType.toLowerCase(java.util.Locale.ROOT);
    }

    private Object parseJson(Object value)
    {
        if (value == null) return Map.of();
        try { return json.readValue(String.valueOf(value), Object.class); }
        catch (Exception ignored) { return Map.of(); }
    }
}
