package com.wanfo.bi.service;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.CredentialCipher;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.datasource.JdbcConnectionSettings;
import com.wanfo.bi.datasource.snapshot.SnapshotData;
import com.wanfo.bi.datasource.snapshot.SnapshotParser;
import com.wanfo.bi.mapper.ObjectStorageLoadMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.warehouse.ClickHouseBatchPublisher;
import com.wanfo.bi.warehouse.WarehousePublisher;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.messages.Item;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Bounded CSV/Parquet batch load from S3-compatible object storage into ClickHouse warehouse layers. */
@Service
public class ObjectStorageLoadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStorageLoadService.class);
    private static final Set<String> LAYERS = Set.of("ODS", "DWD", "DWS", "ADS");
    private static final Set<String> FORMATS = Set.of("AUTO", "CSV", "PARQUET");
    private static final int MAX_OBJECTS = 50;
    private static final long MAX_OBJECT_BYTES = 5L * 1024 * 1024;
    private static final long STREAM_TO_DISK_THRESHOLD = 512 * 1024;
    @Autowired(required = false)
    private LineageRebuildTrigger lineageRebuild;

    public record LoadRequest(Long objectDatasourceId, Long targetDatasourceId, String objectPrefix,
            String targetLayer, String targetTable, Integer maxRows, String fileFormat) {
        public LoadRequest(Long objectDatasourceId, Long targetDatasourceId, String objectPrefix,
                String targetLayer, String targetTable, Integer maxRows) {
            this(objectDatasourceId, targetDatasourceId, objectPrefix, targetLayer, targetTable, maxRows, null);
        }
    }

    private final ObjectStorageLoadMapper mapper;
    private final DataSourceProvider sources;
    private final CredentialCipher cipher;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final Executor executor;
    private final ObjectMapper json;
    private final TaskPoolGateService poolGate;
    private final int defaultMaxRows;
    private final int absoluteMaxRows;

    public ObjectStorageLoadService(ObjectStorageLoadMapper mapper, DataSourceProvider sources, CredentialCipher cipher,
            BiResourceAuthorizer resources, CapabilityProvider capabilities, CurrentActorProvider actors,
            @Qualifier("biWarehouseExecutor") Executor executor, ObjectMapper json, TaskPoolGateService poolGate,
            @Value("${bi.warehouse.default-max-rows:100000}") int defaultMaxRows,
            @Value("${bi.warehouse.absolute-max-rows:1000000}") int absoluteMaxRows) {
        this.mapper = mapper;
        this.sources = sources;
        this.cipher = cipher;
        this.resources = resources;
        this.capabilities = capabilities;
        this.actors = actors;
        this.executor = executor;
        this.json = json;
        this.poolGate = poolGate;
        this.defaultMaxRows = Math.max(1, defaultMaxRows);
        this.absoluteMaxRows = Math.max(this.defaultMaxRows, absoluteMaxRows);
    }

    public Map<String, Object> trigger(LoadRequest request, String traceId) {
        capabilities.require("bi:resource:admin");
        if (request == null || request.objectDatasourceId() == null || request.targetDatasourceId() == null)
            throw new IllegalArgumentException("对象存储源与数仓目标不能为空");
        String format = normalizeFormat(request.fileFormat());
        BiDatasource objectSource = requireObjectSource(request.objectDatasourceId());
        BiDatasource target = requireWarehouseTarget(request.targetDatasourceId());
        resources.require("datasource", objectSource.getId(), "model");
        resources.require("datasource", target.getId(), "model");
        poolGate.assertLaneRunnable("OBJECT_LOAD", mapper.recent().stream()
                .filter(r -> Set.of("PENDING", "RUNNING", "QUEUED").contains(String.valueOf(r.get("status")).toUpperCase(Locale.ROOT)))
                .count());
        String layer = request.targetLayer() == null ? "ODS" : request.targetLayer().trim().toUpperCase(Locale.ROOT);
        if (!LAYERS.contains(layer)) throw new IllegalArgumentException("目标逻辑层无效");
        String table = sanitize(request.targetTable());
        int maxRows = request.maxRows() == null ? defaultMaxRows : request.maxRows();
        if (maxRows < 1 || maxRows > absoluteMaxRows) throw new IllegalArgumentException("行数上限无效");
        String prefix = request.objectPrefix() == null ? "" : request.objectPrefix().trim();
        String database = "wanfo_wh_" + layer.toLowerCase(Locale.ROOT);
        Map<String, Object> snapshot = Map.of("objectDatasourceId", objectSource.getId(), "targetDatasourceId", target.getId(),
                "bucket", objectSource.getSourceSchema(), "prefix", prefix, "targetLayer", layer, "targetTable", table,
                "maxRows", maxRows, "fileFormat", format);
        Map<String, Object> run = new HashMap<>();
        run.put("datasourceId", objectSource.getId());
        run.put("targetDatasourceId", target.getId());
        run.put("bucket", objectSource.getSourceSchema());
        run.put("objectPrefix", prefix);
        run.put("targetDatabase", database);
        run.put("targetTable", table);
        run.put("traceId", bounded(traceId, 64));
        run.put("triggerBy", actors.currentActor().username());
        try {
            run.put("requestSnapshotJson", json.writeValueAsString(snapshot));
        } catch (Exception error) {
            throw new IllegalStateException("对象存储入仓快照保存失败", error);
        }
        if (mapper.insert(run) != 1 || !(run.get("id") instanceof Number id))
            throw new IllegalStateException("对象存储入仓记录创建失败");
        long runId = id.longValue();
        executor.execute(() -> execute(runId, objectSource, target, database, table, prefix, maxRows, format));
        return mapper.find(runId);
    }

    public List<Map<String, Object>> recent() {
        capabilities.require("bi:resource:admin");
        return mapper.recent();
    }

    public Map<String, Object> get(Long id) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> row = mapper.find(id);
        if (row == null) throw new IllegalArgumentException("对象存储入仓记录不存在");
        return row;
    }

    private void execute(long runId, BiDatasource objectSource, BiDatasource target, String database, String table,
            String prefix, int maxRows, String format) {
        try {
            if (mapper.start(runId) != 1) return;
            MinioClient client = MinioClient.builder()
                    .endpoint(normalizeEndpoint(endpointOf(objectSource)))
                    .credentials(objectSource.getUsername(), cipher.decrypt(objectSource.getPassword()))
                    .build();
            String bucket = objectSource.getSourceSchema();
            List<String> keys = new ArrayList<>();
            for (Result<Item> result : client.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build())) {
                Item item = result.get();
                if (item.isDir()) continue;
                String name = item.objectName();
                if (name == null || !matchesFormat(name, format)) continue;
                keys.add(name);
                if (keys.size() >= MAX_OBJECTS) break;
            }
            if (keys.isEmpty()) throw new IllegalArgumentException("前缀下没有可入仓的 " + formatLabel(format) + " 对象");
            List<String> columns = null;
            List<Map<String, Object>> records = new ArrayList<>();
            for (String key : keys) {
                rejectOrcObject(key, format);
                try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
                    byte[] bytes = readObjectPayload(client, bucket, key, in);
                    SnapshotData data = parseObject(bytes, key, format);
                    if (columns == null) columns = data.columns().stream().map(SnapshotData.Column::name).toList();
                    for (List<String> row : data.rows()) {
                        if (records.size() >= maxRows)
                            throw new IllegalArgumentException("对象存储入仓超过行数上限");
                        Map<String, Object> record = new LinkedHashMap<>();
                        for (int i = 0; i < columns.size() && i < row.size(); i++)
                            record.put(columns.get(i), row.get(i));
                        records.add(record);
                    }
                }
            }
            try (Connection connection = JdbcConnectionSettings.open(target)) {
                WarehousePublisher.Engine engine = WarehousePublisher.parseEngine(target.getType());
                var published = WarehousePublisher.publishMaps(connection, engine, database, table, runId, columns, records,
                        ClickHouseBatchPublisher.DEFAULT_BATCH_SIZE, maxRows, null);
                mapper.succeed(runId, keys.size(), published.rows());
                if (lineageRebuild != null) lineageRebuild.afterChange();
            }
        } catch (Exception failure) {
            LOGGER.warn("Object storage load {} failed", runId, failure);
            mapper.fail(runId, bounded(failure.getMessage() == null ? "对象存储入仓失败" : failure.getMessage(), 900));
        }
    }

    static String normalizeFormat(String fileFormat) {
        if (fileFormat == null || fileFormat.isBlank()) return "AUTO";
        String value = fileFormat.trim().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(value)) throw new IllegalArgumentException("fileFormat 须为 AUTO、CSV 或 PARQUET");
        return value;
    }

    static boolean matchesFormat(String objectName, String format) {
        String lower = objectName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".orc")) return false;
        return switch (format) {
            case "CSV" -> lower.endsWith(".csv");
            case "PARQUET" -> lower.endsWith(".parquet");
            default -> lower.endsWith(".csv") || lower.endsWith(".parquet");
        };
    }

    static SnapshotData parseObject(byte[] bytes, String objectName, String format) throws Exception {
        String lower = objectName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".orc")) throw new IllegalArgumentException("暂不支持 ORC 入仓，请转换为 CSV 或 Parquet");
        boolean parquet = "PARQUET".equals(format) || ("AUTO".equals(format) && lower.endsWith(".parquet"));
        return parquet ? SnapshotParser.parquet(bytes) : SnapshotParser.csv(bytes);
    }

    private static void rejectOrcObject(String objectName, String format) {
        String lower = objectName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".orc") || "ORC".equalsIgnoreCase(format))
            throw new IllegalArgumentException("暂不支持 ORC 入仓，请转换为 CSV 或 Parquet");
    }

    private byte[] readObjectPayload(MinioClient client, String bucket, String key, InputStream in) throws Exception {
        long size = -1;
        try {
            size = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build()).size();
        } catch (Exception ignored) {
            // fall back to streaming read
        }
        if (size > MAX_OBJECT_BYTES) throw new IllegalArgumentException("对象过大: " + key);
        if (size >= STREAM_TO_DISK_THRESHOLD || size < 0) {
            Path temp = Files.createTempFile("wanfo-osl-", ".bin");
            try {
                long written = 0;
                try (OutputStream out = Files.newOutputStream(temp)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        written += read;
                        if (written > MAX_OBJECT_BYTES) throw new IllegalArgumentException("对象过大: " + key);
                        out.write(buffer, 0, read);
                    }
                }
                return Files.readAllBytes(temp);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        byte[] bytes = in.readNBytes((int) MAX_OBJECT_BYTES + 1);
        if (bytes.length > MAX_OBJECT_BYTES) throw new IllegalArgumentException("对象过大: " + key);
        return bytes;
    }

    private static String formatLabel(String format) {
        return switch (format) {
            case "CSV" -> "CSV";
            case "PARQUET" -> "Parquet";
            default -> "CSV/Parquet";
        };
    }

    private BiDatasource requireObjectSource(Long id) {
        BiDatasource source = sources.getById(id);
        if (source == null || !"0".equals(source.getStatus()))
            throw new IllegalArgumentException("对象存储数据源不存在或已停用");
        String type = source.getType() == null ? "" : source.getType().toLowerCase(Locale.ROOT);
        if (!Set.of("s3", "oss", "minio").contains(type))
            throw new IllegalArgumentException("对象存储源类型须为 s3、oss 或 minio");
        if (endpointOf(source).isBlank() || source.getSourceSchema() == null
                || source.getSourceSchema().isBlank() || source.getUsername() == null || source.getUsername().isBlank()
                || source.getPassword() == null || source.getPassword().isBlank())
            throw new IllegalArgumentException("请配置 endpoint(jdbcUrl)、bucket(sourceSchema)、accessKey、secretKey");
        return source;
    }

    /** Object-store connectors store the S3 endpoint in jdbcUrl (see ConnectorRegistry). */
    private static String endpointOf(BiDatasource source) {
        return source.getJdbcUrl() == null ? "" : source.getJdbcUrl().trim();
    }

    private BiDatasource requireWarehouseTarget(Long id) {
        BiDatasource target = sources.getById(id);
        if (target == null || !"0".equals(target.getStatus()))
            throw new IllegalArgumentException("数仓目标不存在或已停用");
        WarehousePublisher.Engine engine = WarehousePublisher.parseEngine(target.getType());
        String schema = target.getSourceSchema() == null ? "" : target.getSourceSchema();
        if (engine == WarehousePublisher.Engine.CLICKHOUSE && !"wanfo_wh_ods".equals(schema))
            throw new IllegalArgumentException("ClickHouse 目标 schema 须为 wanfo_wh_ods");
        return target;
    }

    private static String normalizeEndpoint(String host) {
        String value = host.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value;
        return value;
    }

    private static String sanitize(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))
            throw new IllegalArgumentException("目标表名无效");
        return value;
    }

    private static String bounded(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(max, value.length()));
    }
}
