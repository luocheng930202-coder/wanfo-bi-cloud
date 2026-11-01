package com.wanfo.bi.service;

import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.datasource.snapshot.SnapshotRegistry;
import com.wanfo.bi.mapper.BiMetadataScanMapper;
import com.wanfo.bi.security.ActorScope;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.LiveActorResolver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Owns manual metadata inventory scans and versioned, metadata-only catalog snapshots. */
@Service
public class MetadataScanService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataScanService.class);
    private static final int MAX_SCOPE_BYTES = 16_000;
    private static final int MAX_SCOPE_TABLES = 500;
    private static final int MAX_SCAN_TABLES = 1_000;
    private static final int MAX_COLUMNS_PER_TABLE = 1_000;

    public record SaveRequest(String name, Map<String, Object> scope, String status) { }

    private final BiMetadataScanMapper mapper;
    private final DataSourceProvider sources;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final LiveActorResolver live;
    private final CatalogService catalog;
    private final TaskPoolGateService poolGate;
    private final ObjectMapper json;
    private final Executor executor;
    private final MeterRegistry meters;
    private final boolean scheduleEnabled;
    private final int scheduleIntervalMinutes;

    public MetadataScanService(BiMetadataScanMapper mapper, DataSourceProvider sources,
            BiResourceAuthorizer resources, CapabilityProvider capabilities, CurrentActorProvider actors,
            LiveActorResolver live, CatalogService catalog, TaskPoolGateService poolGate, ObjectMapper json,
            @Qualifier("biMetadataExecutor") Executor executor, MeterRegistry meters,
            @Value("${bi.metadata-scan.schedule-enabled:true}") boolean scheduleEnabled,
            @Value("${bi.metadata-scan.interval-minutes:1440}") int scheduleIntervalMinutes)
    {
        this.mapper = mapper;
        this.sources = sources;
        this.resources = resources;
        this.capabilities = capabilities;
        this.actors = actors;
        this.live = live;
        this.catalog = catalog;
        this.poolGate = poolGate;
        this.json = json;
        this.executor = executor;
        this.meters = meters;
        this.scheduleEnabled = scheduleEnabled;
        this.scheduleIntervalMinutes = Math.max(15, scheduleIntervalMinutes);
    }

    /** Polls enabled scan definitions and triggers those due by interval. */
    @Scheduled(fixedDelayString = "${bi.metadata-scan.poll-ms:300000}", initialDelayString = "${bi.metadata-scan.poll-ms:300000}")
    public void triggerDueScans()
    {
        if (!scheduleEnabled) return;
        for (Map<String, Object> scan : mapper.enabledScans())
        {
            Long scanId = number(scan.get("id"));
            if (scanId == null) continue;
            try
            {
                LocalDateTime last = mapper.lastSucceededAt(scanId);
                if (last != null && ChronoUnit.MINUTES.between(last, LocalDateTime.now()) < scheduleIntervalMinutes) continue;
                Long sourceId = number(scan.get("datasourceId"));
                if (sourceId == null || mapper.countActiveRuns(sourceId) > 0) continue;
                Map<String, Object> definition = mapper.definition(scanId);
                if (definition == null || !"0".equals(String.valueOf(definition.get("status")))) continue;
                Long adminPrincipal = mapper.platformAdminPrincipalId();
                if (adminPrincipal == null) continue;
                BiActor admin = new BiActor("ruoyi", adminPrincipal, "admin", null, java.util.Set.of());
                LiveActorResolver.Snapshot snapshot = live.resolve(admin);
                ActorScope.run(snapshot.actor(), snapshot.capabilities(), () -> {
                    try { trigger(scanId, "schedule:" + scanId); }
                    catch (RuntimeException ignored) { /* active run / auth / queue — skip until next poll */ }
                });
            }
            catch (RuntimeException failure)
            {
                LOGGER.warn("Scheduled metadata scan {} skipped: {}", scanId, failure.getMessage());
            }
        }
    }

    public List<Map<String, Object>> listDefinitions(Long sourceId)
    {
        capabilities.require("bi:datasource:model");
        if (sourceId == null || sourceId < 1) throw new IllegalArgumentException("请指定数据源");
        resources.require("datasource", sourceId, "model");
        requireJdbcSource(sourceId);
        return mapper.definitions(sourceId);
    }

    public Map<String, Object> create(Long sourceId, SaveRequest request)
    {
        capabilities.require("bi:datasource:model");
        resources.require("datasource", sourceId, "model");
        requireJdbcSource(sourceId);
        ValidatedDefinition definition = validate(request);
        BiActor actor = actors.currentActor();
        Map<String, Object> command = new HashMap<>();
        command.put("sourceId", sourceId);
        command.put("name", definition.name());
        command.put("scope", definition.scopeJson());
        command.put("status", definition.status());
        command.put("actor", actor.username());
        if (mapper.insertDefinition(command) != 1 || !(command.get("id") instanceof Number id))
        {
            throw new IllegalStateException("扫描配置创建失败");
        }
        return mapper.definition(id.longValue());
    }

    public Map<String, Object> update(Long id, SaveRequest request)
    {
        Map<String, Object> current = requireDefinition(id, "edit");
        ValidatedDefinition definition = validate(request);
        int updated = mapper.updateDefinition(id, definition.name(), definition.scopeJson(),
                definition.status(), actors.currentActor().username());
        if (updated != 1) throw new IllegalStateException("扫描配置更新失败");
        return mapper.definition(id);
    }

    @Transactional
    public Map<String, Object> delete(Long id)
    {
        Map<String, Object> definition = requireDefinition(id, "edit");
        Long sourceId = number(definition.get("datasourceId"));
        if (mapper.lockDatasource(sourceId) == null) throw new IllegalArgumentException("数据源不存在或已删除");
        if (mapper.countActiveRuns(sourceId) > 0)
        {
            throw new IllegalStateException("该数据源有正在运行的扫描，暂不能删除配置");
        }
        mapper.deleteDefinition(id);
        return Map.of("deleted", true);
    }

    @Transactional
    public Map<String, Object> trigger(Long id, String traceId)
    {
        capabilities.require("bi:datasource:model");
        Map<String, Object> definition = requireDefinition(id, "edit");
        Long sourceId = number(definition.get("datasourceId"));
        if (!"0".equals(String.valueOf(definition.get("status"))))
        {
            throw new IllegalArgumentException("已停用的扫描配置不能运行");
        }
        poolGate.assertLaneRunnable("METADATA_SCAN", mapper.activeGlobalCount());
        requireJdbcSource(sourceId);
        if (mapper.lockDatasource(sourceId) == null) throw new IllegalArgumentException("数据源不存在或已删除");
        if (mapper.countActiveRuns(sourceId) > 0) throw new IllegalStateException("该数据源已有扫描正在运行");

        BiActor actor = actors.currentActor();
        Map<String, Object> command = new HashMap<>();
        command.put("scanId", id);
        command.put("sourceId", sourceId);
        command.put("actor", actor.username());
        command.put("traceId", traceId == null || traceId.isBlank() ? null : traceId.substring(0, Math.min(64, traceId.length())));
        if (mapper.insertRun(command) != 1 || !(command.get("id") instanceof Number runId))
        {
            throw new IllegalStateException("扫描运行记录创建失败");
        }
        long idValue = runId.longValue();
        try
        {
            executor.execute(() -> execute(idValue, id, sourceId, actor, String.valueOf(definition.get("scopeJson"))));
        }
        catch (RuntimeException rejected)
        {
            mapper.failRun(idValue, "扫描任务队列繁忙，请稍后重试");
            Counter.builder("bi.metadata.scan.runs").tag("outcome", "REJECTED").register(meters).increment();
        }
        return mapper.run(idValue);
    }

    public List<Map<String, Object>> listRuns(Long sourceId)
    {
        capabilities.require("bi:datasource:model");
        if (sourceId == null || sourceId < 1) throw new IllegalArgumentException("请指定数据源");
        resources.require("datasource", sourceId, "view");
        return mapper.runs(sourceId);
    }

    public Map<String, Object> getRun(Long runId)
    {
        capabilities.require("bi:datasource:model");
        Map<String, Object> run = mapper.run(runId);
        if (run == null) throw new IllegalArgumentException("扫描运行记录不存在");
        resources.require("datasource", number(run.get("datasourceId")), "view");
        return run;
    }

    private void execute(long runId, Long scanId, Long sourceId, BiActor actor, String scopeJson)
    {
        long startedAt = System.nanoTime();
        try
        {
            asLive(actor, () -> {
                try { scan(runId, scanId, sourceId, scopeJson); }
                catch (Exception failure) { throw new IllegalStateException("元数据读取失败", failure); }
            });
        }
        catch (RuntimeException failure)
        {
            mapper.failRun(runId, "元数据扫描失败，请检查数据源连接状态和当前授权");
        }
        finally
        {
            Map<String,Object> run = mapper.run(runId);
            String outcome = run == null ? "FAILED" : String.valueOf(run.get("status"));
            if (!List.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED").contains(outcome)) outcome = "FAILED";
            Counter.builder("bi.metadata.scan.runs").tag("outcome", outcome).register(meters).increment();
            Timer.builder("bi.metadata.scan.duration").tag("outcome", outcome).register(meters)
                    .record(Math.max(0, System.nanoTime() - startedAt), java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private void scan(long runId, Long scanId, Long sourceId, String scopeJson) throws Exception
    {
        if (mapper.startRun(runId) != 1) return;
        var scanStartedAt = mapper.databaseNow();
        var source = requireJdbcSource(sourceId);
        resources.require("datasource", sourceId, "model");
        catalog.requireModel(source.getSchemaName());
        Map<String, Object> scope = parseScope(scopeJson);
        List<Map<String, Object>> discovered = catalog.listTables(source.getSchemaName(), null);
        List<String> include = strings(scope.get("includeTables"));
        List<String> exclude = strings(scope.get("excludeTables"));
        List<Map<String, Object>> tables = discovered.stream()
                .filter(table -> include.isEmpty() || include.contains(String.valueOf(table.get("name"))))
                .filter(table -> !exclude.contains(String.valueOf(table.get("name"))))
                .toList();
        if (tables.size() > MAX_SCAN_TABLES) throw new IllegalArgumentException("扫描对象超过单次上限");
        mapper.progress(runId, tables.size(), 0, 0, 0);
        int succeeded = 0;
        int failed = 0;
        int columns = 0;
        int assets = 0;
        int completed = 0;
        String schemaName = source.getSourceSchema() == null || source.getSourceSchema().isBlank()
                ? source.getSchemaName() : source.getSourceSchema();
        Long schemaAssetId = persistAsset(sourceId, "SCHEMA", null, schemaName,
                source.getSchemaName() + "." + schemaName, Map.of("name", schemaName, "sourceType", source.getType()),
                runId, "wanfo:connector:" + sourceId + ":schema:" + segment(schemaName));
        assets++;
        for (Map<String, Object> table : tables)
        {
            try
            {
                String tableName = String.valueOf(table.get("name"));
                List<Map<String, Object>> fields = catalog.listColumns(source.getSchemaName(), null, tableName);
                if (fields.size() > MAX_COLUMNS_PER_TABLE) throw new IllegalArgumentException("字段数量超过上限");
                Map<String, Object> tableMetadata = new LinkedHashMap<>();
                tableMetadata.put("name", tableName);
                tableMetadata.put("type", String.valueOf(table.getOrDefault("type", "TABLE")));
                tableMetadata.put("catalog", String.valueOf(table.getOrDefault("catalog", "")));
                tableMetadata.put("comment", table.get("comment") == null ? "" : String.valueOf(table.get("comment")));
                tableMetadata.put("columns", fields.stream().map(field -> new LinkedHashMap<>(field)).toList());
                Long tableAssetId = persistAsset(sourceId, "TABLE", schemaAssetId, tableName,
                        source.getSchemaName() + "." + tableName,
                        tableMetadata,
                        runId, "wanfo:connector:" + sourceId + ":table:" + segment(schemaName) + ":" + segment(tableName));
                assets++;
                for (Map<String, Object> field : fields)
                {
                    String fieldName = String.valueOf(field.get("name"));
                    persistAsset(sourceId, "COLUMN", tableAssetId, fieldName,
                            source.getSchemaName() + "." + tableName + "." + fieldName,
                            new LinkedHashMap<>(field), runId,
                            "wanfo:connector:" + sourceId + ":column:" + segment(schemaName) + ":"
                                    + segment(tableName) + ":" + segment(fieldName));
                    assets++;
                }
                columns += fields.size();
                succeeded++;
            }
            catch (BiAccessException revoked)
            {
                throw revoked;
            }
            catch (Exception ignored)
            {
                failed++;
            }
            completed++;
            mapper.progress(runId, tables.size(), completed, succeeded, failed);
        }
        // Only a complete, unfiltered scan can infer that previously known objects disappeared.
        if (failed == 0 && include.isEmpty() && exclude.isEmpty())
        {
            mapper.markMissingAssetsStale(sourceId, scanStartedAt, actors.currentActor().username());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scanId", scanId);
        summary.put("tableCount", tables.size());
        summary.put("columnsDiscovered", columns);
        summary.put("assetsUpserted", assets);
        summary.put("snapshotPersisted", true);
        summary.put("note", "仅保存数据库结构元数据，不读取业务数据行或连接凭据");
        String status = failed == 0 ? "SUCCEEDED" : succeeded == 0 ? "FAILED" : "PARTIAL_SUCCESS";
        mapper.finishRun(runId, tables.size(), completed, succeeded, failed, writeJson(summary), status,
                failed == 0 ? null : "部分对象元数据读取失败，请检查数据源权限和结构状态");
    }

    private Long persistAsset(Long sourceId, String type, Long parentId, String name, String qualifiedName,
                              Map<String, Object> metadata, long runId, String urn)
    {
        String encoded = writeJson(metadata);
        String urnHash = sha256(urn);
        String schemaHash = sha256(encoded);
        Map<String, Object> asset = new HashMap<>();
        asset.put("type", type); asset.put("sourceId", sourceId); asset.put("urn", urn);
        asset.put("urnHash", urnHash); asset.put("parentId", parentId); asset.put("name", name);
        asset.put("qualifiedName", qualifiedName); asset.put("description", metadata.get("comment"));
        asset.put("actor", actors.currentActor().username());
        mapper.upsertAsset(asset);
        Map<String, Object> current = mapper.assetByHash(urnHash);
        if (current == null || !(current.get("id") instanceof Number id))
            throw new IllegalStateException("资产元数据保存失败");
        Long assetId = id.longValue();
        Long currentVersionId = current.get("currentVersionId") instanceof Number version
                ? version.longValue() : null;
        Map<String, Object> oldVersion = currentVersionId == null ? null : mapper.assetVersion(currentVersionId);
        if (oldVersion == null || !schemaHash.equals(String.valueOf(oldVersion.get("schemaHash"))))
        {
            int versionNo = oldVersion == null ? 1 : ((Number) oldVersion.get("versionNo")).intValue() + 1;
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("changeType", oldVersion == null ? "CREATED" : "METADATA_CHANGED");
            if (oldVersion != null) change.put("previousHash", oldVersion.get("schemaHash"));
            change.put("currentHash", schemaHash);
            Map<String, Object> fieldDiff = oldVersion == null ? Map.of() : metadataDiff(String.valueOf(oldVersion.get("metadataJson")), metadata);
            if (oldVersion != null) change.put("fields", fieldDiff);
            Map<String, Object> version = new HashMap<>();
            version.put("assetId", assetId); version.put("versionNo", versionNo);
            version.put("schemaHash", schemaHash); version.put("metadata", encoded);
            version.put("changeSummary", writeJson(change)); version.put("runId", runId);
            version.put("actor", actors.currentActor().username());
            if (mapper.insertAssetVersion(version) != 1 || !(version.get("id") instanceof Number versionId))
                throw new IllegalStateException("资产版本保存失败");
            mapper.setCurrentAssetVersion(assetId, versionId.longValue(), actors.currentActor().username());
            if (oldVersion != null && "TABLE".equals(type)
                    && mapper.insertSchemaChangeEvent(assetId, currentVersionId, versionId.longValue(), runId,
                            "SCHEMA_CHANGED", writeJson(change), actors.currentActor().username()) != 1)
                throw new IllegalStateException("结构变化事件保存失败");
        }
        return assetId;
    }

    private Map<String, Object> metadataDiff(String oldJson, Map<String, Object> current)
    {
        try
        {
            Map<String, Object> old = json.readValue(oldJson,
                    json.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            if (old.get("columns") instanceof List<?> oldColumns && current.get("columns") instanceof List<?> newColumns)
            {
                Map<String, Map<String, Object>> oldByName = indexColumns(oldColumns);
                Map<String, Map<String, Object>> newByName = indexColumns(newColumns);
                List<String> added = newByName.keySet().stream().filter(name -> !oldByName.containsKey(name)).sorted().toList();
                List<String> removed = oldByName.keySet().stream().filter(name -> !newByName.containsKey(name)).sorted().toList();
                List<String> changed = newByName.keySet().stream().filter(oldByName::containsKey)
                        .filter(name -> !java.util.Objects.equals(oldByName.get(name), newByName.get(name))).sorted().toList();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("added", added);
                result.put("removed", removed);
                result.put("changed", changed);
                Map<String, Object> oldIndexes = indexColumnPositions(oldColumns);
                Map<String, Object> newIndexes = indexColumnPositions(newColumns);
                List<Map<String, Object>> typeChanges = new ArrayList<>();
                List<Map<String, Object>> renameCandidates = new ArrayList<>();
                for (String name : oldByName.keySet())
                {
                    if (!newByName.containsKey(name)) continue;
                    Object beforeType = oldByName.get(name).get("type");
                    Object afterType = newByName.get(name).get("type");
                    if (!java.util.Objects.equals(beforeType, afterType))
                        typeChanges.add(Map.of("field", name, "beforeType", Objects.toString(beforeType, ""), "afterType", Objects.toString(afterType, "")));
                }
                for (String removedName : removed)
                {
                    for (String addedName : added)
                    {
                        Map<String, Object> oldColumn = oldByName.get(removedName), newColumn = newByName.get(addedName);
                        if (java.util.Objects.equals(oldIndexes.get(removedName), newIndexes.get(addedName))
                                && java.util.Objects.equals(oldColumn.get("type"), newColumn.get("type")))
                            renameCandidates.add(Map.of("from", removedName, "to", addedName, "confidence", "LOW", "reason", "same_position_and_type"));
                    }
                }
                result.put("typeChanges", typeChanges);
                result.put("renameCandidates", renameCandidates);
                List<String> changeKinds = new ArrayList<>();
                if (!added.isEmpty()) changeKinds.add("COLUMN_ADDED");
                if (!removed.isEmpty()) changeKinds.add("COLUMN_REMOVED");
                if (!typeChanges.isEmpty()) changeKinds.add("COLUMN_TYPE_CHANGED");
                if (!changed.isEmpty() && typeChanges.size() < changed.size()) changeKinds.add("COLUMN_ATTRIBUTES_CHANGED");
                result.put("changeKinds", changeKinds);
                List<String> metadataChanged = current.keySet().stream().filter(old::containsKey)
                        .filter(key -> !"columns".equals(key) && !java.util.Objects.equals(old.get(key), current.get(key)))
                        .sorted().toList();
                if (!metadataChanged.isEmpty()) { result.put("metadataChanged", metadataChanged); changeKinds.add("TABLE_METADATA_CHANGED"); }
                return result;
            }
            List<String> added = current.keySet().stream().filter(key -> !old.containsKey(key)).sorted().toList();
            List<String> removed = old.keySet().stream().filter(key -> !current.containsKey(key)).sorted().toList();
            List<String> changed = current.keySet().stream().filter(old::containsKey)
                    .filter(key -> !java.util.Objects.equals(old.get(key), current.get(key))).sorted().toList();
            return Map.of("added", added, "removed", removed, "changed", changed);
        }
        catch (Exception ignored) { return Map.of("summary", "previous_metadata_unavailable"); }
    }

    private static Map<String, Map<String, Object>> indexColumns(List<?> columns)
    {
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        for (Object column : columns)
        {
            if (!(column instanceof Map<?, ?> values) || values.get("name") == null) continue;
            Map<String, Object> normalized = new LinkedHashMap<>();
            values.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            indexed.put(String.valueOf(values.get("name")), normalized);
        }
        return indexed;
    }

    private static Map<String, Object> indexColumnPositions(List<?> columns)
    {
        Map<String, Object> indexed = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++)
        {
            Object column = columns.get(index);
            if (column instanceof Map<?, ?> values && values.get("name") != null)
                indexed.put(String.valueOf(values.get("name")), index);
        }
        return indexed;
    }

    private static String segment(String value)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private void asLive(BiActor actor, Runnable action)
    {
        var current = live.resolve(actor);
        current.require("bi:datasource:model");
        ActorScope.run(current.actor(), current.capabilities(), action);
    }

    private Map<String, Object> requireDefinition(Long id, String action)
    {
        if (id == null || id < 1) throw new IllegalArgumentException("扫描配置 ID 无效");
        Map<String, Object> definition = mapper.definition(id);
        if (definition == null) throw new IllegalArgumentException("扫描配置不存在");
        resources.require("datasource", number(definition.get("datasourceId")), action);
        return definition;
    }

    private com.wanfo.bi.datasource.BiDatasource requireJdbcSource(Long sourceId)
    {
        var source = sources.getById(sourceId);
        if (source == null || !"0".equals(source.getStatus()) || SnapshotRegistry.supports(source.getType()))
        {
            throw new IllegalArgumentException("扫描仅支持已启用的 JDBC 数据源");
        }
        return source;
    }

    private ValidatedDefinition validate(SaveRequest request)
    {
        if (request == null || request.name() == null || request.name().isBlank() || request.name().trim().length() > 200)
        {
            throw new IllegalArgumentException("扫描名称必填且不能超过 200 个字符");
        }
        String status = request.status() == null ? "0" : request.status();
        if (!"0".equals(status) && !"1".equals(status)) throw new IllegalArgumentException("扫描状态无效");
        Map<String, Object> scope = request.scope() == null ? Map.of() : request.scope();
        if (scope.keySet().stream().anyMatch(key -> !List.of("includeTables", "excludeTables").contains(key)))
        {
            throw new IllegalArgumentException("扫描范围仅允许 includeTables 和 excludeTables");
        }
        List<String> include = strings(scope.get("includeTables"));
        List<String> exclude = strings(scope.get("excludeTables"));
        if (include.size() > MAX_SCOPE_TABLES || exclude.size() > MAX_SCOPE_TABLES)
        {
            throw new IllegalArgumentException("单个扫描范围最多配置 500 张表");
        }
        if (include.stream().anyMatch(exclude::contains)) throw new IllegalArgumentException("同一张表不能同时列入和排除范围");
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("includeTables", include);
        normalized.put("excludeTables", exclude);
        String encoded = writeJson(normalized);
        if (encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SCOPE_BYTES)
            throw new IllegalArgumentException("扫描范围配置过大");
        return new ValidatedDefinition(request.name().trim(), encoded, status);
    }

    private Map<String, Object> parseScope(String text)
    {
        try
        {
            return json.readValue(text, json.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        }
        catch (Exception ignored)
        {
            throw new IllegalStateException("扫描范围配置损坏");
        }
    }

    private static List<String> strings(Object value)
    {
        if (value == null) return List.of();
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("扫描表范围必须为数组");
        List<String> result = new ArrayList<>();
        for (Object item : values)
        {
            if (!(item instanceof String name) || name.isBlank() || name.length() > 256
                    || name.chars().anyMatch(Character::isISOControl))
            {
                throw new IllegalArgumentException("扫描范围包含无效表名");
            }
            if (result.contains(name)) throw new IllegalArgumentException("扫描范围不能包含重复表名");
            result.add(name);
        }
        return List.copyOf(result);
    }

    private String writeJson(Object value)
    {
        try { return json.writeValueAsString(value); }
        catch (Exception ignored) { throw new IllegalStateException("扫描配置序列化失败"); }
    }

    private static Long number(Object value)
    {
        if (!(value instanceof Number number)) throw new IllegalStateException("扫描记录关联数据源无效");
        return number.longValue();
    }

    private record ValidatedDefinition(String name, String scopeJson, String status) { }
}
