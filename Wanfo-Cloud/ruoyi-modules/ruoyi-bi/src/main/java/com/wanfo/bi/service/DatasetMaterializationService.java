package com.wanfo.bi.service;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.datasource.JdbcConnectionSettings;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.mapper.WarehouseLoadMapper;
import com.wanfo.bi.security.ActorScope;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.LiveActorResolver;
import com.wanfo.bi.warehouse.ClickHouseBatchPublisher;
import com.wanfo.bi.warehouse.WarehousePublisher;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Materializes an authorized dataset snapshot into a warehouse layer table.
 * First cut supports REPLACE only and uses the dataset preview path (bounded by maxRows).
 */
@Service
public class DatasetMaterializationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetMaterializationService.class);
    private static final Set<String> LAYERS = Set.of("ODS", "DWD", "DWS", "ADS");

    public record ScopeBind(Long scopeDatasetId, String modelVersion, String tenantField, String organizationField) {
    }

    public record MaterializeRequest(Long datasetId, Long targetDatasourceId, String targetLayer, String targetTable,
            Integer maxRows, String writeMode, String modelCode, ScopeBind scopeBind) {
        public MaterializeRequest(Long datasetId, Long targetDatasourceId, String targetLayer, String targetTable,
                Integer maxRows, String writeMode) {
            this(datasetId, targetDatasourceId, targetLayer, targetTable, maxRows, writeMode, null, null);
        }
    }

    private final WarehouseLoadMapper mapper;
    private final BiDatasetTableMapper datasets;
    private final DatasetService datasetService;
    private final DataSourceProvider sources;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final LiveActorResolver live;
    private final WarehouseScopeService warehouseScopes;
    private final Executor executor;
    private final DataSource platformDataSource;
    private final ObjectMapper json;
    private final int defaultMaxRows;
    private final int absoluteMaxRows;

    public DatasetMaterializationService(WarehouseLoadMapper mapper, BiDatasetTableMapper datasets,
            DatasetService datasetService, DataSourceProvider sources, BiResourceAuthorizer resources,
            CapabilityProvider capabilities, CurrentActorProvider actors, LiveActorResolver live,
            WarehouseScopeService warehouseScopes,
            @Qualifier("biWarehouseExecutor") Executor executor, DataSource platformDataSource, ObjectMapper json,
            @Value("${bi.warehouse.default-max-rows:100000}") int defaultMaxRows,
            @Value("${bi.warehouse.absolute-max-rows:1000000}") int absoluteMaxRows) {
        this.mapper = mapper;
        this.datasets = datasets;
        this.datasetService = datasetService;
        this.sources = sources;
        this.resources = resources;
        this.capabilities = capabilities;
        this.actors = actors;
        this.live = live;
        this.warehouseScopes = warehouseScopes;
        this.executor = executor;
        this.platformDataSource = platformDataSource;
        this.json = json;
        this.defaultMaxRows = Math.max(1, defaultMaxRows);
        this.absoluteMaxRows = Math.max(this.defaultMaxRows, absoluteMaxRows);
    }

    public List<Map<String, Object>> packages() {
        capabilities.require("bi:resource:admin");
        return List.of(Map.of(
                "code", "retail-v1",
                "name", "零售示例模型",
                "kind", "EXAMPLE",
                "description", "内置演示包：DWD 订单明细 → DWS 月度销售汇总",
                "targetLayer", "DWS",
                "targetTable", "agg_retail_sales_monthly",
                "refreshPath", "/bi/warehouse/models/retail-v1/monthly/refresh",
                "generic", false));
    }

    public Map<String, Object> trigger(MaterializeRequest request, String traceId) {
        capabilities.require("bi:resource:admin");
        if (request == null || request.datasetId() == null || request.targetDatasourceId() == null)
            throw new IllegalArgumentException("数据集与数仓目标不能为空");
        String layer = request.targetLayer() == null ? "DWS" : request.targetLayer().trim().toUpperCase(Locale.ROOT);
        if (!LAYERS.contains(layer)) throw new IllegalArgumentException("目标逻辑层仅支持 ODS、DWD、DWS、ADS");
        String table = sanitizeTable(request.targetTable());
        String writeMode = request.writeMode() == null ? "REPLACE" : request.writeMode().trim().toUpperCase(Locale.ROOT);
        if (!"REPLACE".equals(writeMode)) throw new IllegalArgumentException("数据集物化首期仅支持 REPLACE");
        int maxRows = request.maxRows() == null ? defaultMaxRows : request.maxRows();
        if (maxRows < 1 || maxRows > absoluteMaxRows)
            throw new IllegalArgumentException("物化行数上限须在 1～" + absoluteMaxRows + " 之间");

        BiDatasetTableDO dataset = datasets.selectById(request.datasetId());
        if (dataset == null) throw new IllegalArgumentException("数据集不存在");
        resources.require("dataset", request.datasetId(), "query");
        BiDatasource target = requireTarget(request.targetDatasourceId());
        resources.require("datasource", target.getId(), "model");

        String database = "wanfo_wh_" + layer.toLowerCase(Locale.ROOT);
        BiActor actor = actors.currentActor();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("datasetId", request.datasetId());
        snapshot.put("targetDatasourceId", request.targetDatasourceId());
        snapshot.put("targetLayer", layer);
        snapshot.put("targetTable", table);
        snapshot.put("maxRows", maxRows);
        snapshot.put("writeMode", writeMode);
        String modelCode = request.modelCode() == null || request.modelCode().isBlank()
                ? "dataset-" + request.datasetId() : request.modelCode().trim();
        if (!modelCode.matches("[A-Za-z][A-Za-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("对账模型编码格式无效");
        snapshot.put("modelCode", modelCode);
        if (request.scopeBind() != null) snapshot.put("scopeBind", request.scopeBind());

        Map<String, Object> run = new HashMap<>();
        run.put("sourceId", request.datasetId());
        run.put("targetId", request.targetDatasourceId());
        run.put("sourceTable", "dataset:" + request.datasetId());
        run.put("targetDatabase", database);
        run.put("targetTable", table);
        run.put("traceId", bounded(traceId, 64));
        run.put("actor", bounded(actor.username(), 64));
        run.put("loadMode", "DATASET");
        try {
            run.put("requestSnapshotJson", json.writeValueAsString(snapshot));
        } catch (Exception error) {
            throw new IllegalStateException("物化请求快照保存失败", error);
        }
        if (mapper.insertRun(run) != 1 || !(run.get("id") instanceof Number id))
            throw new IllegalStateException("物化运行记录创建失败");
        long runId = id.longValue();
        try {
            executor.execute(() -> execute(runId, request.datasetId(), target, database, table, maxRows, modelCode,
                    request.scopeBind(), actor));
        } catch (RuntimeException rejected) {
            mapper.fail(runId, "物化任务队列繁忙，请稍后重试");
        }
        return mapper.run(runId);
    }

    public Map<String, Object> latestReconciliation(Long targetDatasourceId, String modelCode) {
        capabilities.require("bi:resource:admin");
        if (targetDatasourceId == null || targetDatasourceId < 1) throw new IllegalArgumentException("请指定数仓目标");
        resources.require("datasource", targetDatasourceId, "model");
        String version = modelCode == null || modelCode.isBlank() ? "dataset" : modelCode.trim();
        Map<String, Object> row = mapper.latestReconciliationForModel(targetDatasourceId, version);
        return row == null ? Map.of() : row;
    }

    private void execute(long runId, Long datasetId, BiDatasource target, String database, String table, int maxRows,
            String modelCode, ScopeBind scopeBind, BiActor actor) {
        try {
            var snapshot = live.resolve(actor);
            snapshot.require("bi:resource:admin");
            try (Connection lockConnection = platformDataSource.getConnection()) {
                String lockName = targetLockName(database, table);
                if (!acquireLock(lockConnection, lockName)) {
                    mapper.fail(runId, "同一数仓目标表已有任务运行，请稍后重试");
                    return;
                }
                try {
                    ActorScope.run(snapshot.actor(), snapshot.capabilities(), () -> {
                        try {
                            materialize(runId, datasetId, target, database, table, maxRows, modelCode, scopeBind);
                        } catch (Exception failure) {
                            throw new MaterializeFailure(failure);
                        }
                    });
                } finally {
                    releaseLock(lockConnection, lockName);
                }
            }
        } catch (Exception failure) {
            LOGGER.warn("Dataset materialization failed for run {}", runId, failure);
            mapper.fail(runId, bounded(failure.getMessage() == null ? "数据集物化失败" : failure.getMessage(), 480));
        }
    }

    private void materialize(long runId, Long datasetId, BiDatasource target, String database, String table, int maxRows,
            String modelCode, ScopeBind scopeBind) throws Exception {
        if (mapper.start(runId) != 1) return;
        resources.require("dataset", datasetId, "query");
        Map<String, Object> preview = datasetService.preview(datasetId, maxRows);
        Object rawColumns = preview.get("columns"), rawRows = preview.get("rows");
        if (!(rawColumns instanceof List<?> columns) || !(rawRows instanceof List<?> rows))
            throw new IllegalArgumentException("数据集预览结果无效");
        if (rows.size() >= maxRows)
            throw new IllegalArgumentException("数据集超过本次物化行数上限，请提高上限或缩小数据范围");
        List<String> columnNames = columns.stream().map(String::valueOf).toList();
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object row : rows)
            if (row instanceof Map<?, ?> map) {
                Map<String, Object> record = new LinkedHashMap<>();
                map.forEach((key, value) -> record.put(String.valueOf(key), value));
                records.add(record);
            }
        long sourceRows = records.size();
        try (Connection connection = JdbcConnectionSettings.open(target)) {
            var result = WarehousePublisher.publishMaps(connection, database, table, runId, columnNames, records,
                    ClickHouseBatchPublisher.DEFAULT_BATCH_SIZE, maxRows, null);
            long targetRows = countTarget(connection, database, table);
            String status = sourceRows == 0 ? "EMPTY"
                    : (sourceRows == targetRows ? "PASS" : "MISMATCH");
            String summary = sourceRows == 0 ? "物化空跑：源数据集无行"
                    : (sourceRows == targetRows
                            ? "物化行数对账一致：源=" + sourceRows + " 目标=" + targetRows
                            : "物化行数不一致：源=" + sourceRows + " 目标=" + targetRows);
            Map<String, Object> reconciliation = new HashMap<>();
            reconciliation.put("runId", runId);
            reconciliation.put("targetId", target.getId());
            reconciliation.put("modelVersion", bounded(modelCode, 64));
            reconciliation.put("sourceTable", "dataset:" + datasetId);
            reconciliation.put("targetTable", database + "." + table);
            reconciliation.put("sourceRows", sourceRows);
            reconciliation.put("targetRows", targetRows);
            reconciliation.put("sourceAmount", 0);
            reconciliation.put("targetAmount", 0);
            reconciliation.put("sourceQuantity", 0);
            reconciliation.put("targetQuantity", 0);
            reconciliation.put("sourceMaxUpdatedAt", null);
            reconciliation.put("freshnessAgeSeconds", null);
            reconciliation.put("status", status);
            reconciliation.put("summary", bounded(summary, 1000));
            if (mapper.saveReconciliation(reconciliation) != 1)
                throw new IllegalStateException("物化对账记录保存失败");
            if (mapper.succeed(runId, result.rows(), result.replaced()) != 1)
                throw new IllegalStateException("物化结果状态更新失败");
        }
        if (scopeBind != null) {
            if (scopeBind.scopeDatasetId() == null)
                throw new IllegalArgumentException("scopeBind.scopeDatasetId 不能为空");
            warehouseScopes.saveBinding(scopeBind.scopeDatasetId(), scopeBind.modelVersion(),
                    scopeBind.tenantField(), scopeBind.organizationField());
        }
    }

    private static long countTarget(Connection connection, String database, String table) throws Exception {
        String sql = "SELECT count() FROM `" + database.replace("`", "") + "`.`" + table.replace("`", "") + "`";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getLong(1) : 0L;
        }
    }

    private BiDatasource requireTarget(Long id) {
        BiDatasource target = sources.getById(id);
        if (target == null || !"0".equals(target.getStatus()))
            throw new IllegalArgumentException("数仓目标数据源不存在或已停用");
        String type = target.getType() == null ? "" : target.getType().toLowerCase(Locale.ROOT);
        if (!type.equals("clickhouse") && !type.equals("ck"))
            throw new IllegalArgumentException("数仓目标必须是 ClickHouse 数据源");
        if (!"wanfo_wh_ods".equals(target.getSourceSchema()))
            throw new IllegalArgumentException("数仓目标数据源 schema 须配置为 wanfo_wh_ods");
        return target;
    }

    private static String sanitizeTable(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))
            throw new IllegalArgumentException("目标表名须为字母开头的标识符");
        if (value.startsWith("__dp_stage_") || value.startsWith("system"))
            throw new IllegalArgumentException("目标表名保留或无效");
        return value;
    }

    private static String targetLockName(String database, String table) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((database + "." + table).toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "dpwh:" + java.util.HexFormat.of().formatHex(digest, 0, 24);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean acquireLock(Connection connection, String name) throws Exception {
        try (var statement = connection.prepareStatement("SELECT GET_LOCK(?,0)")) {
            statement.setString(1, name);
            try (var rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) == 1;
            }
        }
    }

    private static void releaseLock(Connection connection, String name) {
        try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, name);
            try (var ignored = statement.executeQuery()) {
            }
        } catch (Exception ignored) {
        }
    }

    private static String bounded(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(max, value.length()));
    }

    private static class MaterializeFailure extends RuntimeException {
        MaterializeFailure(Throwable cause) {
            super(cause);
        }
    }
}
