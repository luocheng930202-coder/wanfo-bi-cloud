package com.wanfo.bi.service;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.datasource.JdbcConnectionSettings;
import com.wanfo.bi.datasource.JdbcSourceScope;
import com.wanfo.bi.datasource.snapshot.SnapshotRegistry;
import com.wanfo.bi.mapper.WarehouseLoadMapper;
import com.wanfo.bi.security.ActorScope;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.LiveActorResolver;
import com.wanfo.bi.warehouse.ClickHouseBatchPublisher;
import com.wanfo.bi.warehouse.WarehousePublisher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.sql.Timestamp;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Bounded full-refresh ingestion from an authorized JDBC source into the isolated ClickHouse ODS layer. */
@Service
public class WarehouseLoadService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WarehouseLoadService.class);

    public record LoadRequest(Long sourceDatasourceId, Long targetDatasourceId, String sourceTable,
            String targetTable, Integer maxRows, String loadMode, String watermarkColumn, List<String> keyColumns,
            String targetLayer, String partitionColumn, String partitionFrom, String partitionTo, Long filterDatasetId,
            Integer parallelSlices, String publishMode) {
        public LoadRequest(Long sourceDatasourceId, Long targetDatasourceId, String sourceTable,
                String targetTable, Integer maxRows, String loadMode, String watermarkColumn, List<String> keyColumns) {
            this(sourceDatasourceId, targetDatasourceId, sourceTable, targetTable, maxRows, loadMode, watermarkColumn, keyColumns, null, null, null, null, null, null, null);
        }
        public LoadRequest(Long sourceDatasourceId, Long targetDatasourceId, String sourceTable,
                String targetTable, Integer maxRows, String loadMode, String watermarkColumn, List<String> keyColumns,
                String targetLayer) {
            this(sourceDatasourceId, targetDatasourceId, sourceTable, targetTable, maxRows, loadMode, watermarkColumn, keyColumns, targetLayer, null, null, null, null, null, null);
        }
        public LoadRequest(Long sourceDatasourceId, Long targetDatasourceId, String sourceTable,
                String targetTable, Integer maxRows, String loadMode, String watermarkColumn, List<String> keyColumns,
                String targetLayer, String partitionColumn, String partitionFrom, String partitionTo, Long filterDatasetId) {
            this(sourceDatasourceId, targetDatasourceId, sourceTable, targetTable, maxRows, loadMode, watermarkColumn, keyColumns, targetLayer, partitionColumn, partitionFrom, partitionTo, filterDatasetId, null, null);
        }
        public LoadRequest(Long sourceDatasourceId, Long targetDatasourceId, String sourceTable,
                String targetTable, Integer maxRows, String loadMode, String watermarkColumn, List<String> keyColumns,
                String targetLayer, String partitionColumn, String partitionFrom, String partitionTo, Long filterDatasetId,
                Integer parallelSlices) {
            this(sourceDatasourceId, targetDatasourceId, sourceTable, targetTable, maxRows, loadMode, watermarkColumn, keyColumns, targetLayer, partitionColumn, partitionFrom, partitionTo, filterDatasetId, parallelSlices, null);
        }
    }

    private final WarehouseLoadMapper mapper;
    private final DataSourceProvider sources;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final LiveActorResolver live;
    private final DatasetService datasetService;
    private final Executor executor;
    private final DataSource platformDataSource;
    private final ObjectMapper json;
    private final TaskPoolGateService poolGate;
    private final int defaultMaxRows;
    private final int absoluteMaxRows;

    public WarehouseLoadService(WarehouseLoadMapper mapper, DataSourceProvider sources,
            BiResourceAuthorizer resources, CapabilityProvider capabilities, CurrentActorProvider actors,
            LiveActorResolver live, DatasetService datasetService,
            @Qualifier("biWarehouseExecutor") Executor executor, DataSource platformDataSource,
            ObjectMapper json, TaskPoolGateService poolGate,
            @org.springframework.beans.factory.annotation.Value("${bi.warehouse.default-max-rows:100000}") int defaultMaxRows,
            @org.springframework.beans.factory.annotation.Value("${bi.warehouse.absolute-max-rows:1000000}") int absoluteMaxRows)
    {
        this.mapper = mapper;
        this.sources = sources;
        this.resources = resources;
        this.capabilities = capabilities;
        this.actors = actors;
        this.live = live;
        this.datasetService = datasetService;
        this.executor = executor;
        this.platformDataSource = platformDataSource;
        this.json = json;
        this.poolGate = poolGate;
        this.defaultMaxRows = Math.max(1, defaultMaxRows);
        this.absoluteMaxRows = Math.max(this.defaultMaxRows, Math.min(absoluteMaxRows, ClickHouseBatchPublisher.MAX_BATCH_ROWS));
    }

    /** Marks abandoned runs and removes their private staging table while holding the shared target lock. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOrphanedRuns()
    {
        List<Map<String, Object>> candidates = mapper.staleCandidates();
        if (candidates.isEmpty()) return;
        try (Connection connection = platformDataSource.getConnection())
        {
            for (Map<String, Object> candidate : candidates)
            {
                long runId = number(candidate.get("id"));
                String database = String.valueOf(candidate.get("targetDatabase"));
                String lockName = targetLockName(database, String.valueOf(candidate.get("targetTable")));
                if (!acquireTargetLock(connection, lockName)) continue;
                try
                {
                    if (mapper.interrupt(runId, "服务启动恢复：任务超过 5 分钟且目标锁未持有，已标记中断；可手动重新运行") != 1)
                        continue;
                    try
                    {
                        BiDatasource target = requireEnabled(number(candidate.get("targetDatasourceId")));
                        if (!validWarehouseLayer(target, database)) continue;
                        try (Connection targetConnection = JdbcConnectionSettings.open(target))
                        {
                            WarehousePublisher.dropRunStage(targetConnection,
                                    WarehousePublisher.parseEngine(target.getType()), database, runId);
                        }
                    }
                    catch (Exception cleanupFailure)
                    {
                        LOGGER.warn("Warehouse interrupted-run stage cleanup failed for run {}; manual cleanup may be required", runId);
                    }
                }
                finally { releaseTargetLock(connection, lockName); }
            }
        }
        catch (Exception failure)
        {
            LOGGER.warn("Warehouse stale-run recovery could not complete; pending runs remain visible for manual review", failure);
        }
    }

    public Map<String, Object> trigger(LoadRequest request, String traceId)
    {
        // Until ingestion is bound to a row-policy-aware published dataset, only
        // platform admins may launch this unfiltered physical-table copy.
        capabilities.require("bi:resource:admin");
        if (request == null) throw new IllegalArgumentException("入仓请求不能为空");
        validateTableName(request.sourceTable(), "源表");
        validateTargetTable(request.targetTable());
        String loadMode = request.loadMode() == null ? "FULL" : request.loadMode().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("FULL", "INCREMENTAL").contains(loadMode)) throw new IllegalArgumentException("入仓模式无效");
        WarehousePublisher.PublishMode publishMode = WarehousePublisher.parseMode(request.publishMode(), loadMode);
        if (publishMode == WarehousePublisher.PublishMode.MERGE || "INCREMENTAL".equals(loadMode))
        {
            validateColumnName(request.watermarkColumn(), "水位字段");
            if (request.keyColumns() == null || request.keyColumns().isEmpty() || request.keyColumns().size() > 16)
                throw new IllegalArgumentException("MERGE/增量业务键须包含 1～16 个字段");
            request.keyColumns().forEach(key -> validateColumnName(key, "业务键字段"));
            if (request.partitionColumn() != null && !request.partitionColumn().isBlank())
                throw new IllegalArgumentException("MERGE/增量模式暂不支持分区切分，请使用全量 REPLACE 或去掉分区参数");
            if (request.filterDatasetId() != null)
                throw new IllegalArgumentException("MERGE/增量模式暂不支持按数据集策略过滤");
        }
        boolean partitioned = request.partitionColumn() != null && !request.partitionColumn().isBlank();
        if (partitioned)
        {
            validateColumnName(request.partitionColumn(), "分区字段");
            if (request.partitionFrom() == null || request.partitionFrom().isBlank()
                    || request.partitionTo() == null || request.partitionTo().isBlank())
                throw new IllegalArgumentException("分区装载须同时指定 partitionFrom 与 partitionTo");
            try
            {
                LocalDate from = LocalDate.parse(request.partitionFrom().trim());
                LocalDate to = LocalDate.parse(request.partitionTo().trim());
                if (to.isBefore(from)) throw new IllegalArgumentException("partitionTo 不能早于 partitionFrom");
                if (from.plusDays(366).isBefore(to)) throw new IllegalArgumentException("单次分区区间最多 366 天");
            }
            catch (DateTimeParseException invalid)
            {
                throw new IllegalArgumentException("分区边界须为 ISO 日期（yyyy-MM-dd）", invalid);
            }
        }
        if (request.parallelSlices() != null)
        {
            if (request.parallelSlices() < 1 || request.parallelSlices() > 8)
                throw new IllegalArgumentException("并行切片数须为 1～8");
            if (!partitioned || !"FULL".equals(loadMode))
                throw new IllegalArgumentException("并行切片仅支持全量分区装载");
        }
        if (request.filterDatasetId() != null)
        {
            resources.require("dataset", request.filterDatasetId(), "query");
            if (partitioned) throw new IllegalArgumentException("数据集策略过滤与分区装载请分两次任务执行");
        }
        int maxRows = request.maxRows() == null ? defaultMaxRows : request.maxRows();
        if (maxRows < 1 || maxRows > absoluteMaxRows)
            throw new IllegalArgumentException("单次入仓上限须在 1～" + absoluteMaxRows + " 行之间（默认 " + defaultMaxRows + "）");
        if (request.sourceDatasourceId() == null || request.targetDatasourceId() == null
                || request.sourceDatasourceId().equals(request.targetDatasourceId()))
            throw new IllegalArgumentException("源数据源与数仓目标必须是不同的数据源");

        BiDatasource source = requireEnabled(request.sourceDatasourceId());
        BiDatasource target = requireEnabled(request.targetDatasourceId());
        resources.require("datasource", source.getId(), "model");
        resources.require("datasource", target.getId(), "model");
        validateSource(source);
        poolGate.assertLaneRunnable("WAREHOUSE_LOAD", mapper.activeRunCount());
        String database = resolveTargetDatabase(target, request.targetLayer());

        BiActor actor = actors.currentActor();
        Map<String, Object> run = new HashMap<>();
        run.put("sourceId", source.getId()); run.put("targetId", target.getId());
        run.put("sourceTable", request.sourceTable().trim()); run.put("targetDatabase", database);
        run.put("targetTable", request.targetTable().trim()); run.put("traceId", bounded(traceId, 64));
        run.put("actor", bounded(actor.username(), 64)); run.put("loadMode", loadMode);
        run.put("publishMode", publishMode.name());
        boolean mergeKeys = publishMode == WarehousePublisher.PublishMode.MERGE || "INCREMENTAL".equals(loadMode);
        LoadRequest snapshot = new LoadRequest(source.getId(), target.getId(), request.sourceTable().trim(),
                request.targetTable().trim(), maxRows, loadMode,
                mergeKeys ? request.watermarkColumn().trim() : null,
                mergeKeys ? List.copyOf(request.keyColumns()) : List.of(),
                layerName(database),
                partitioned ? request.partitionColumn().trim() : null,
                partitioned ? request.partitionFrom().trim() : null,
                partitioned ? request.partitionTo().trim() : null,
                request.filterDatasetId(),
                request.parallelSlices(),
                publishMode.name());
        try { run.put("requestSnapshotJson", json.writeValueAsString(snapshot)); }
        catch (Exception serializationFailure) { throw new IllegalStateException("入仓请求快照保存失败，任务未提交", serializationFailure); }
        if (mapper.insertRun(run) != 1 || !(run.get("id") instanceof Number runId))
            throw new IllegalStateException("入仓运行记录创建失败");
        long id = runId.longValue();
        try
        {
            executor.execute(() -> execute(id, source, target, snapshot, actor));
        }
        catch (RuntimeException rejected)
        {
            mapper.fail(id, "入仓任务队列繁忙，请稍后重试");
        }
        return mapper.run(id);
    }

    public Map<String, Object> opsOverview()
    {
        capabilities.require("bi:resource:admin");
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("activeRuns", mapper.activeRunCount());
        overview.put("staleRuns", mapper.staleRunCount());
        overview.put("recentRuns", mapper.recentRuns().stream().limit(20).toList());
        overview.put("note", "吞吐与锁持有者细节依赖运行中 JDBC 会话；孤儿 staging 由启动恢复清理");
        return overview;
    }

    public List<Map<String, Object>> runs(Long targetDatasourceId)
    {
        capabilities.require("bi:resource:admin");
        if (targetDatasourceId == null || targetDatasourceId < 1) throw new IllegalArgumentException("请指定数仓目标数据源");
        resources.require("datasource", targetDatasourceId, "model");
        return mapper.runs(targetDatasourceId).stream()
                .filter(row -> resources.allowed("datasource", number(row.get("sourceDatasourceId")), "model"))
                .toList();
    }

    public Map<String, Object> run(Long id)
    {
        capabilities.require("bi:resource:admin");
        Map<String, Object> run = mapper.run(id);
        if (run == null) throw new IllegalArgumentException("入仓运行记录不存在");
        resources.require("datasource", number(run.get("sourceDatasourceId")), "model");
        resources.require("datasource", number(run.get("targetDatasourceId")), "model");
        run.remove("requestSnapshotJson");
        return run;
    }

    /** Retry a failed run using its persisted non-secret request snapshot. */
    public Map<String, Object> retry(Long id, String traceId)
    {
        capabilities.require("bi:resource:admin");
        Map<String, Object> previous = mapper.run(id);
        if (previous == null) throw new IllegalArgumentException("入仓运行记录不存在");
        if (!"FAILED".equalsIgnoreCase(String.valueOf(previous.get("status"))))
            throw new IllegalArgumentException("只有失败的入仓任务可以重试");
        resources.require("datasource", number(previous.get("sourceDatasourceId")), "model");
        resources.require("datasource", number(previous.get("targetDatasourceId")), "model");
        Object encoded = previous.get("requestSnapshotJson");
        if (!(encoded instanceof String requestJson) || requestJson.isBlank())
            throw new IllegalArgumentException("该历史运行没有请求快照，请从入仓页面重新配置");
        try { return trigger(json.readValue(requestJson, LoadRequest.class), traceId); }
        catch (IllegalArgumentException invalid) { throw invalid; }
        catch (Exception invalid) { throw new IllegalArgumentException("原入仓请求快照无法读取，请从入仓页面重新配置", invalid); }
    }

    private void execute(long runId, BiDatasource source, BiDatasource target, LoadRequest request, BiActor actor)
    {
        String sourceTable = request.sourceTable();
        String targetDatabase = "wanfo_wh_" + (request.targetLayer() == null ? "ods" : request.targetLayer().toLowerCase(Locale.ROOT));
        String targetTable = request.targetTable();
        try
        {
            var current = live.resolve(actor);
            current.require("bi:resource:admin");
            targetDatabase = resolveTargetDatabase(target, request.targetLayer());
            try (Connection lockConnection = platformDataSource.getConnection())
            {
                String lockName = targetLockName(targetDatabase, targetTable);
                if (!acquireTargetLock(lockConnection, lockName))
                {
                    mapper.fail(runId, "同一数仓目标表已有入仓任务运行，请稍后重试");
                    return;
                }
                String database = targetDatabase;
                try
                {
                    ActorScope.run(current.actor(), current.capabilities(), () -> {
                        try { load(runId, source.getId(), target.getId(), request, database); }
                        catch (Exception failure) { throw new LoadFailure(failure); }
                    });
                }
                finally { releaseTargetLock(lockConnection, lockName); }
            }
        }
        catch (RuntimeException | SQLException failure)
        {
            LOGGER.warn("Warehouse load failed for run {} (source datasource {}, table {}; target datasource {}, table {})",
                    runId, source.getId(), sourceTable, target.getId(), targetTable, failure);
            mapper.fail(runId, "入仓失败，目标表保持原样；请检查当前授权、源/目标连接和字段类型");
        }
    }

    private static boolean acquireTargetLock(Connection connection, String name) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)"))
        {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) { return result.next() && result.getInt(1) == 1; }
        }
    }

    private static void releaseTargetLock(Connection connection, String name) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)"))
        {
            statement.setString(1, name);
            try (ResultSet ignored = statement.executeQuery()) { /* consume result and release session lock */ }
        }
    }

    private static String targetLockName(String database, String table)
    {
        try
        {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((database + "." + table).toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "dpwh:" + java.util.HexFormat.of().formatHex(digest, 0, 24);
        }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private void load(long runId, Long sourceId, Long targetId, LoadRequest request, String targetDatabase) throws Exception
    {
        if (mapper.start(runId) != 1) return;
        String sourceTable = request.sourceTable();
        String targetTable = request.targetTable();
        int maxRows = request.maxRows();
        String loadMode = request.loadMode();
        WarehousePublisher.PublishMode publishMode = WarehousePublisher.parseMode(request.publishMode(), loadMode);
        String watermarkColumn = request.watermarkColumn();
        List<String> keyColumns = request.keyColumns() == null ? List.of() : request.keyColumns();
        BiDatasource sourceDefinition = requireEnabled(sourceId);
        BiDatasource targetDefinition = requireEnabled(targetId);
        resources.require("datasource", sourceId, "model");
        resources.require("datasource", targetId, "model");
        validateSource(sourceDefinition);
        if (!validWarehouseLayer(targetDefinition, targetDatabase))
            throw new IllegalArgumentException("数仓目标层无效或目标数据源 schema 配置不正确");
        WarehousePublisher.Engine engine = WarehousePublisher.parseEngine(targetDefinition.getType());

        if (request.filterDatasetId() != null)
        {
            loadFromDataset(runId, request.filterDatasetId(), targetDefinition, engine, targetDatabase, targetTable, maxRows);
            return;
        }

        try (Connection source = JdbcConnectionSettings.open(sourceDefinition);
             Connection target = JdbcConnectionSettings.open(targetDefinition))
        {
            try { source.setReadOnly(true); }
            catch (SQLException unsupported) { /* SQL below is generated as a SELECT only; some drivers reject readOnly hints. */ }
            String sourceType = sourceDefinition.getType().toLowerCase(Locale.ROOT);
            if ((sourceType.equals("postgresql") || sourceType.equals("pg")) && source.getAutoCommit()) source.setAutoCommit(false);
            JdbcSourceScope scope = JdbcSourceScope.resolve(source, sourceDefinition.getSourceSchema());
            requireSourceTable(source, scope, sourceTable);
            String select = "SELECT * FROM " + scope.relation(source.getMetaData(), sourceTable);
            String product = source.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            Map<String, Object> checkpoint = null;
            int watermarkJdbcType = Types.OTHER;
            String normalizedKeys = null;
            String quotedPartition = null;
            if (request.partitionColumn() != null && !request.partitionColumn().isBlank())
            {
                quotedPartition = sourceColumn(source, scope, sourceTable, request.partitionColumn(), new int[]{ Types.OTHER });
                select += " WHERE " + quotedPartition + " >= ? AND " + quotedPartition + " <= ?";
            }
            if (publishMode == WarehousePublisher.PublishMode.MERGE || "INCREMENTAL".equals(loadMode))
            {
                int[] watermarkType = { Types.OTHER };
                String quotedWatermark = sourceColumn(source, scope, sourceTable, watermarkColumn, watermarkType);
                watermarkJdbcType = watermarkType[0];
                normalizedKeys = keyColumns.stream().map(value -> value.toLowerCase(Locale.ROOT)).sorted().reduce((a, b) -> a + "," + b).orElseThrow();
                checkpoint = mapper.checkpoint(sourceId, sourceTable, targetDatabase, targetTable);
                if (checkpoint != null)
                {
                    String savedWatermark = String.valueOf(checkpoint.get("watermarkColumn"));
                    String savedKeys = String.valueOf(checkpoint.get("keyColumns"));
                    if (!savedWatermark.equalsIgnoreCase(watermarkColumn) || !savedKeys.equals(normalizedKeys)
                            || ((Number) checkpoint.get("watermarkJdbcType")).intValue() != watermarkJdbcType)
                        throw new IllegalArgumentException("增量水位或业务键配置与已有检查点不一致；请先执行全量刷新以重置检查点");
                    select += (quotedPartition == null ? " WHERE " : " AND ") + quotedWatermark + " >= ? ORDER BY " + quotedWatermark + " ASC";
                }
                else select += " ORDER BY " + quotedWatermark + " ASC";
            }
            int parallel = request.parallelSlices() == null ? 1 : request.parallelSlices();
            if (parallel > 1 && quotedPartition != null && "FULL".equals(loadMode))
            {
                loadPartitionParallel(runId, sourceDefinition, target, engine, sourceTable, targetDatabase, targetTable,
                        quotedPartition, request.partitionFrom().trim(), request.partitionTo().trim(), maxRows, parallel, product, scope);
                return;
            }
            select = limitedSelect(product, select, maxRows + 1);
            try (var query = source.prepareStatement(select))
            {
                int index = 1;
                if (quotedPartition != null)
                {
                    query.setObject(index++, Date.valueOf(LocalDate.parse(request.partitionFrom().trim())));
                    query.setObject(index++, Date.valueOf(LocalDate.parse(request.partitionTo().trim())));
                }
                if (checkpoint != null)
                    query.setObject(index, parseWatermark(String.valueOf(checkpoint.get("watermarkValue")), watermarkJdbcType));
                query.setFetchSize(Math.min(ClickHouseBatchPublisher.DEFAULT_BATCH_SIZE, maxRows));
                query.setMaxRows(maxRows + 1);
                query.setQueryTimeout(600);
                try (ResultSet rows = query.executeQuery())
                {
                    var result = WarehousePublisher.publishResultSet(target, engine, rows, targetDatabase, targetTable, runId,
                            ClickHouseBatchPublisher.DEFAULT_BATCH_SIZE, maxRows, publishMode, watermarkColumn, keyColumns,
                            phase -> {
                                if (!"SUCCEEDED".equals(phase.phase())) mapper.markPromote(runId, phase.phase());
                            });
                    if (publishMode == WarehousePublisher.PublishMode.MERGE)
                    {
                        // MySQL upsert returns 1 (insert), 2 (update) or 0 (unchanged) — all are success.
                        if (result.rows() > 0 && result.watermarkValue() != null
                                && mapper.saveCheckpoint(sourceId, sourceTable, targetDatabase, targetTable,
                                watermarkColumn, watermarkJdbcType, normalizedKeys, result.watermarkValue(), runId) < 0)
                            throw new IllegalStateException("入仓检查点保存失败；可用相同配置安全重试");
                    }
                    else mapper.deleteCheckpoint(sourceId, sourceTable, targetDatabase, targetTable);
                    if (mapper.succeed(runId, result.rows(), result.replaced()) != 1) throw new IllegalStateException("入仓结果状态更新失败");
                }
            }
        }
    }

    /** Parallel date-slice reads into memory, then a single REPLACE publish (bounded by maxRows / 8 workers). */
    private void loadPartitionParallel(long runId, BiDatasource sourceDefinition, Connection target,
            WarehousePublisher.Engine engine, String sourceTable, String targetDatabase, String targetTable,
            String quotedPartition, String fromText, String toText, int maxRows, int parallelSlices, String product,
            JdbcSourceScope scope) throws Exception
    {
        LocalDate from = LocalDate.parse(fromText);
        LocalDate to = LocalDate.parse(toText);
        long dayCount = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        int workers = Math.min(Math.max(1, parallelSlices), 8);
        int sliceDays = (int) Math.max(1, (dayCount + workers - 1) / workers);
        List<LocalDate[]> windows = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); )
        {
            LocalDate end = cursor.plusDays(sliceDays - 1L);
            if (end.isAfter(to)) end = to;
            windows.add(new LocalDate[]{ cursor, end });
            cursor = end.plusDays(1);
        }
        List<Map<String, Object>> records = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> columnNames = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(workers, windows.size()));
        try
        {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (LocalDate[] window : windows)
            {
                tasks.add(() -> {
                    try (Connection sliceSource = JdbcConnectionSettings.open(sourceDefinition))
                    {
                        try { sliceSource.setReadOnly(true); }
                        catch (SQLException ignored) { }
                        String base = "SELECT * FROM " + scope.relation(sliceSource.getMetaData(), sourceTable)
                                + " WHERE " + quotedPartition + " >= ? AND " + quotedPartition + " <= ?";
                        String sql = limitedSelect(product, base, maxRows + 1);
                        try (PreparedStatement query = sliceSource.prepareStatement(sql))
                        {
                            query.setObject(1, Date.valueOf(window[0]));
                            query.setObject(2, Date.valueOf(window[1]));
                            query.setFetchSize(Math.min(ClickHouseBatchPublisher.DEFAULT_BATCH_SIZE, maxRows));
                            query.setMaxRows(maxRows + 1);
                            query.setQueryTimeout(600);
                            try (ResultSet rows = query.executeQuery())
                            {
                                int columnCount = rows.getMetaData().getColumnCount();
                                synchronized (columnNames)
                                {
                                    if (columnNames.isEmpty())
                                        for (int i = 1; i <= columnCount; i++)
                                            columnNames.add(rows.getMetaData().getColumnLabel(i));
                                }
                                while (rows.next())
                                {
                                    if (records.size() >= maxRows)
                                        throw new IllegalArgumentException("并行分区拉取超过本次入仓行数上限");
                                    Map<String, Object> record = new LinkedHashMap<>();
                                    for (int i = 1; i <= columnCount; i++)
                                        record.put(rows.getMetaData().getColumnLabel(i), rows.getObject(i));
                                    records.add(record);
                                }
                            }
                        }
                    }
                    return null;
                });
            }
            for (Future<Void> future : pool.invokeAll(tasks)) future.get();
        }
        finally
        {
            pool.shutdownNow();
        }
        if (columnNames.isEmpty())
        {
            if (mapper.succeed(runId, 0, false) != 1) throw new IllegalStateException("入仓结果状态更新失败");
            return;
        }
        var result = WarehousePublisher.publishMaps(target, engine, targetDatabase, targetTable, runId, columnNames, records,
                ClickHouseBatchPublisher.DEFAULT_BATCH_SIZE, maxRows,
                phase -> {
                    if (!"SUCCEEDED".equals(phase.phase())) mapper.markPromote(runId, phase.phase());
                });
        mapper.deleteCheckpoint(sourceDefinition.getId(), sourceTable, targetDatabase, targetTable);
        if (mapper.succeed(runId, result.rows(), result.replaced()) != 1) throw new IllegalStateException("入仓结果状态更新失败");
    }

    private void loadFromDataset(long runId, Long datasetId, BiDatasource target, WarehousePublisher.Engine engine,
            String database, String table, int maxRows) throws Exception
    {
        resources.require("dataset", datasetId, "query");
        Map<String, Object> preview = datasetService.preview(datasetId, maxRows);
        Object rawColumns = preview.get("columns"), rawRows = preview.get("rows");
        if (!(rawColumns instanceof List<?> columns) || !(rawRows instanceof List<?> rows))
            throw new IllegalArgumentException("数据集预览结果无效");
        if (rows.size() >= maxRows)
            throw new IllegalArgumentException("按策略过滤后的数据集超过本次入仓行数上限");
        List<String> columnNames = columns.stream().map(String::valueOf).toList();
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object row : rows)
            if (row instanceof Map<?, ?> map)
            {
                Map<String, Object> record = new LinkedHashMap<>();
                map.forEach((key, value) -> record.put(String.valueOf(key), value));
                records.add(record);
            }
        try (Connection connection = JdbcConnectionSettings.open(target))
        {
            var result = WarehousePublisher.publishMaps(connection, engine, database, table, runId, columnNames, records,
                    ClickHouseBatchPublisher.DEFAULT_BATCH_SIZE, maxRows,
                    phase -> {
                        if (!"SUCCEEDED".equals(phase.phase())) mapper.markPromote(runId, phase.phase());
                    });
            if (mapper.succeed(runId, result.rows(), result.replaced()) != 1)
                throw new IllegalStateException("入仓结果状态更新失败");
        }
    }

    private BiDatasource requireEnabled(Long id)
    {
        BiDatasource source = sources.getById(id);
        if (source == null || !"0".equals(source.getStatus()) || SnapshotRegistry.supports(source.getType()))
            throw new IllegalArgumentException("数据源不存在、已停用或不支持 JDBC 入仓");
        return source;
    }

    private void validateSource(BiDatasource source)
    {
        String type = source.getType() == null ? "" : source.getType().toLowerCase(Locale.ROOT);
        if (!List.of("mysql", "mariadb", "postgresql", "pg", "sqlserver", "mssql", "oracle", "clickhouse", "ck", "doris", "starrocks", "tidb").contains(type))
            throw new IllegalArgumentException("当前连接器不在入仓支持范围内");
        if ("clickhouse".equals(type) || "ck".equals(type)) throw new IllegalArgumentException("ClickHouse 仅作为数仓目标，不能作为本期业务源");
    }

    private String resolveTargetDatabase(BiDatasource target, String targetLayer)
    {
        String type = target.getType() == null ? "" : target.getType().toLowerCase(Locale.ROOT);
        boolean clickhouse = type.equals("clickhouse") || type.equals("ck");
        boolean mysqlProtocolWh = type.equals("doris") || type.equals("starrocks");
        if (!clickhouse && !mysqlProtocolWh)
            throw new IllegalArgumentException("数仓目标须为 ClickHouse，或已评估可用的 Doris/StarRocks");
        if (clickhouse && !"wanfo_wh_ods".equals(target.getSourceSchema()))
            throw new IllegalArgumentException("ClickHouse 数仓目标 schema 须配置为 wanfo_wh_ods（可写入 ODS/DWD/DWS/ADS）");
        if (mysqlProtocolWh && (target.getSourceSchema() == null || target.getSourceSchema().isBlank()))
            throw new IllegalArgumentException("Doris/StarRocks 目标须配置默认库（sourceSchema）");
        String layer = targetLayer == null || targetLayer.isBlank() ? "ODS" : targetLayer.trim().toUpperCase(Locale.ROOT);
        if (mysqlProtocolWh)
        {
            if (!Set.of("ODS", "DWD", "DWS", "ADS").contains(layer))
                throw new IllegalArgumentException("目标逻辑层仅支持 ODS、DWD、DWS、ADS");
            // Doris/StarRocks：逻辑层映射为同名库前缀，物理写入默认库下的 {layer_lower}_{table} 由调用方表名约定；
            // 此处返回连接默认库，表名仍用 request.targetTable。
            return target.getSourceSchema();
        }
        String database = switch (layer) {
            case "ODS" -> "wanfo_wh_ods";
            case "DWD" -> "wanfo_wh_dwd";
            case "DWS" -> "wanfo_wh_dws";
            case "ADS" -> "wanfo_wh_ads";
            default -> throw new IllegalArgumentException("目标逻辑层仅支持 ODS、DWD、DWS、ADS");
        };
        if (!validWarehouseLayer(target, database)) throw new IllegalArgumentException("数仓目标层无效");
        return database;
    }

    private static String layerName(String database)
    {
        return switch (database) {
            case "wanfo_wh_ods" -> "ODS";
            case "wanfo_wh_dwd" -> "DWD";
            case "wanfo_wh_dws" -> "DWS";
            case "wanfo_wh_ads" -> "ADS";
            default -> "ODS";
        };
    }

    private static boolean validWarehouseLayer(BiDatasource target, String database)
    {
        String type = target.getType() == null ? "" : target.getType().toLowerCase(Locale.ROOT);
        if (type.equals("doris") || type.equals("starrocks"))
            return database != null && !database.isBlank() && database.equals(target.getSourceSchema());
        return (type.equals("clickhouse") || type.equals("ck"))
                && "wanfo_wh_ods".equals(target.getSourceSchema())
                && List.of("wanfo_wh_ods", "wanfo_wh_dwd", "wanfo_wh_dws", "wanfo_wh_ads").contains(database);
    }

    private static void requireSourceTable(Connection source, JdbcSourceScope scope, String table) throws SQLException
    {
        var metadata = source.getMetaData();
        String schemaPattern = scope.schema();
        String tablePattern = table;
        String escape = metadata.getSearchStringEscape();
        if (escape != null && !escape.isEmpty())
        {
            schemaPattern = schemaPattern == null ? null : escapePattern(schemaPattern, escape);
            tablePattern = escapePattern(table, escape);
        }
        try (ResultSet tables = metadata.getTables(scope.catalog(), schemaPattern, tablePattern, new String[]{"TABLE", "VIEW"}))
        {
            while (tables.next()) if (table.equals(tables.getString("TABLE_NAME"))) return;
        }
        throw new IllegalArgumentException("源表不存在或超出已配置 schema 范围");
    }

    private static String escapePattern(String value, String escape)
    {
        return value.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }

    private static String limitedSelect(String product, String sql, int rows)
    {
        if (product.contains("sql server")) return sql.replaceFirst("SELECT ", "SELECT TOP (" + rows + ") ");
        if (product.contains("oracle")) return "SELECT * FROM (" + sql + ") WHERE ROWNUM <= " + rows;
        return sql + " LIMIT " + rows;
    }

    private static String sourceColumn(Connection connection, JdbcSourceScope scope, String table,
            String requested, int[] jdbcType) throws SQLException
    {
        String actual = null;
        try (ResultSet columns = connection.getMetaData().getColumns(scope.catalog(), scope.schema(), table, null))
        {
            while (columns.next())
            {
                String candidate = columns.getString("COLUMN_NAME");
                if (candidate != null && candidate.equalsIgnoreCase(requested))
                {
                    if (actual != null) throw new IllegalArgumentException("源表字段名大小写不唯一，无法安全选择");
                    actual = candidate;
                    jdbcType[0] = columns.getInt("DATA_TYPE");
                }
            }
        }
        if (actual == null) throw new IllegalArgumentException("源字段不存在或超出已配置 schema 范围");
        String quote = connection.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) return actual;
        quote = quote.trim();
        return quote + actual.replace(quote, quote + quote) + quote;
    }

    private static Object parseWatermark(String value, int jdbcType)
    {
        try
        {
            return switch (jdbcType)
            {
                case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.NUMERIC, Types.DECIMAL -> new BigDecimal(value);
                case Types.DATE -> Date.valueOf(LocalDate.parse(value));
                case Types.TIME -> Time.valueOf(LocalTime.parse(value));
                case Types.TIMESTAMP -> {
                    try { yield Timestamp.from(OffsetDateTime.parse(value).toInstant()); }
                    catch (DateTimeParseException ignored) { yield Timestamp.valueOf(LocalDateTime.parse(value.replace(' ', 'T'))); }
                }
                case Types.TIMESTAMP_WITH_TIMEZONE -> OffsetDateTime.parse(value);
                default -> throw new IllegalArgumentException("水位字段类型不支持增量比较");
            };
        }
        catch (RuntimeException invalid)
        {
            if (invalid instanceof IllegalArgumentException && "水位字段类型不支持增量比较".equals(invalid.getMessage())) throw invalid;
            throw new IllegalArgumentException("已保存的增量水位无法按源字段类型解析，请执行全量刷新重置检查点");
        }
    }

    private static void validateColumnName(String value, String label)
    {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_$#]{0,127}")) throw new IllegalArgumentException(label + "名称无效");
    }

    private static void validateTableName(String table, String label)
    {
        if (table == null || !table.matches("[A-Za-z_][A-Za-z0-9_$#]{0,127}"))
            throw new IllegalArgumentException(label + "名称无效");
    }

    private static void validateTargetTable(String table)
    {
        validateTableName(table, "目标表");
        if (table.startsWith("__dp_stage_") || table.startsWith("system")) throw new IllegalArgumentException("目标表名保留或无效");
    }

    private static Long number(Object value) { return value instanceof Number number ? number.longValue() : null; }
    private static String bounded(String value, int max) { return value == null ? "" : value.substring(0, Math.min(max, value.length())); }

    private static final class LoadFailure extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        private LoadFailure(Throwable cause) { super(cause); }
    }
}
