package com.wanfo.bi.service;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.datasource.JdbcConnectionSettings;
import com.wanfo.bi.mapper.PipelineMapper;
import com.wanfo.bi.mapper.PipelineRunMapper;
import com.wanfo.bi.security.ActorScope;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.LiveActorResolver;
import com.wanfo.bi.warehouse.ClickHouseBatchPublisher;
import com.wanfo.bi.warehouse.WarehousePublisher;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Pipeline DAG runner.
 * PREVIEW is sample-bounded and never writes the warehouse.
 * PRODUCTION materializes WAREHOUSE_PUBLISH nodes into ClickHouse layer tables.
 */
@Service
public class PipelineRunService {
    private static final int SAMPLE_LIMIT = 100;
    private static final Pattern REQUEST_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,96}");
    private static final Set<String> WAREHOUSE_LAYERS = Set.of("DWD", "DWS", "ADS");

    private final PipelineRunMapper runs;
    private final PipelineMapper pipelines;
    private final DatasetService datasets;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final QualityRuleVersionService qualityRules;
    private final CurrentActorProvider actors;
    private final LiveActorResolver live;
    private final DataSourceProvider sources;
    private final FieldStandardService fieldStandards;
    private final TaskPoolGateService poolGate;
    private final ObjectMapper json;
    private final ExecutorService executor;
    private final ScheduledExecutorService timer;
    private final int productionMaxRows;
    @Autowired(required = false)
    private LineageRebuildTrigger lineageRebuild;
    private final Map<Long, Future<?>> futures = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> timeouts = new ConcurrentHashMap<>();

    public PipelineRunService(PipelineRunMapper runs, PipelineMapper pipelines, DatasetService datasets,
            BiResourceAuthorizer resources, CapabilityProvider capabilities, QualityRuleVersionService qualityRules,
            CurrentActorProvider actors, LiveActorResolver live, DataSourceProvider sources,
            FieldStandardService fieldStandards, TaskPoolGateService poolGate, ObjectMapper json,
            @Qualifier("biPipelineExecutor") ExecutorService executor,
            @Qualifier("biPipelineTimer") ScheduledExecutorService timer,
            @Value("${bi.pipeline.production-max-rows:100000}") int productionMaxRows) {
        this.runs = runs;
        this.pipelines = pipelines;
        this.datasets = datasets;
        this.resources = resources;
        this.capabilities = capabilities;
        this.qualityRules = qualityRules;
        this.actors = actors;
        this.live = live;
        this.sources = sources;
        this.fieldStandards = fieldStandards;
        this.poolGate = poolGate;
        this.json = json;
        this.executor = executor;
        this.timer = timer;
        this.productionMaxRows = Math.max(1, Math.min(productionMaxRows, ClickHouseBatchPublisher.MAX_BATCH_ROWS));
    }

    public Map<String, Object> submitPreview(Long pipelineId, Long revisionId, String requestKey, Integer timeoutSeconds, String traceId) {
        return submit(actors.currentActor(), true, pipelineId, revisionId, requestKey, timeoutSeconds, traceId, "PREVIEW", null, null);
    }

    public Map<String, Object> submitProduction(Long pipelineId, Long revisionId, String requestKey, Integer timeoutSeconds,
            Long targetDatasourceId, Integer maxRows, String traceId) {
        return submitProductionAs(actors.currentActor(), true, pipelineId, revisionId, requestKey, timeoutSeconds, targetDatasourceId, maxRows, traceId);
    }

    public Map<String, Object> submitProductionAs(BiActor actor, boolean checkCapabilities, Long pipelineId, Long revisionId,
            String requestKey, Integer timeoutSeconds, Long targetDatasourceId, Integer maxRows, String traceId) {
        if (targetDatasourceId == null) throw new IllegalArgumentException("生产运行必须指定数仓目标数据源");
        return submit(actor, checkCapabilities, pipelineId, revisionId, requestKey, timeoutSeconds, traceId, "PRODUCTION", targetDatasourceId, maxRows);
    }

    public Map<String, Object> retryFromSnapshot(Long runId, String requestKey) {
        capabilities.require("bi:pipeline:run");
        Map<String, Object> previous = detail(runId);
        Long pipelineId = number(previous.get("pipelineId"));
        Long revisionId = number(previous.get("revisionId"));
        resources.require("pipeline", pipelineId, "run");
        String mode = String.valueOf(previous.getOrDefault("runMode", "PREVIEW"));
        if (!"PRODUCTION".equals(mode))
            return submitPreview(pipelineId, revisionId, requestKey, null, null);
        String snapshotText = runs.requestSnapshot(runId);
        Map<String, Object> snapshot = asMap(snapshotText);
        Long targetDatasourceId = number(snapshot.get("targetDatasourceId"));
        Integer maxRows = number(snapshot.get("maxRows")) == null ? null : number(snapshot.get("maxRows")).intValue();
        Integer timeoutSeconds = number(snapshot.get("timeoutSeconds")) == null ? null : number(snapshot.get("timeoutSeconds")).intValue();
        return submitProduction(pipelineId, revisionId, requestKey, timeoutSeconds, targetDatasourceId, maxRows, null);
    }

    private Map<String, Object> submit(BiActor actor, boolean checkCapabilities, Long pipelineId, Long revisionId, String requestKey,
            Integer timeoutSeconds, String traceId, String mode, Long targetDatasourceId, Integer maxRows) {
        if (checkCapabilities) {
            capabilities.require("bi:pipeline:run");
            resources.require("pipeline", pipelineId, "run");
        }
        if (actor == null) throw new IllegalArgumentException("执行身份无效");
        if (requestKey == null || !REQUEST_KEY.matcher(requestKey).matches())
            throw new IllegalArgumentException("幂等请求编号须为 8～96 位字母、数字、点、冒号、下划线或短横线");
        boolean production = "PRODUCTION".equals(mode);
        int timeout = timeoutSeconds == null ? (production ? 300 : 30) : timeoutSeconds;
        if (production) {
            if (timeout < 30 || timeout > 1800) throw new IllegalArgumentException("生产执行超时须在 30～1800 秒之间");
            requireWarehouseTarget(targetDatasourceId);
        } else if (timeout < 10 || timeout > 120) {
            throw new IllegalArgumentException("预览执行超时须在 10～120 秒之间");
        }
        // S3-01: honor PIPELINE lane pause / soft concurrency before enqueue.
        poolGate.assertLaneRunnable("PIPELINE", runs.activeGlobalCount());
        int rowLimit = production
                ? (maxRows == null ? productionMaxRows : Math.max(1, Math.min(maxRows, productionMaxRows)))
                : SAMPLE_LIMIT;
        Map<String, Object> previous = runs.byRequestKey(pipelineId, requestKey);
        if (previous != null) return detail(((Number) previous.get("id")).longValue());
        Map<String, Object> revision = pipelines.revision(pipelineId, revisionId);
        if (revision == null || !"PUBLISHED".equals(String.valueOf(revision.get("status"))))
            throw new IllegalArgumentException(production ? "只能生产运行已发布的流程版本" : "只能预览已发布的流程版本");
        if (runs.activeCount(pipelineId) > 0) throw new IllegalStateException("该流程已有任务运行或排队");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mode", mode);
        snapshot.put("timeoutSeconds", timeout);
        snapshot.put("rowLimit", rowLimit);
        if (production) {
            snapshot.put("targetDatasourceId", targetDatasourceId);
            snapshot.put("maxRows", rowLimit);
        }
        Map<String, Object> row = new HashMap<>();
        row.put("pipelineId", pipelineId);
        row.put("revisionId", revisionId);
        row.put("hash", revision.get("definitionHash"));
        row.put("mode", mode);
        row.put("key", requestKey);
        row.put("actor", actor.username());
        row.put("traceId", bounded(traceId, 64));
        row.put("timeout", timeout);
        row.put("snapshot", write(snapshot));
        try {
            if (runs.insert(row) != 1 || !(row.get("id") instanceof Number id))
                throw new IllegalStateException("创建流程运行记录失败");
            long runId = id.longValue();
            for (Map<String, Object> node : pipelines.nodes(revisionId))
                runs.insertNode(runId, String.valueOf(node.get("nodeKey")), String.valueOf(node.get("nodeType")));
            RunContext context = new RunContext(mode, rowLimit, targetDatasourceId, runId);
            enqueue(runId, pipelineId, revisionId, actor, timeout, context);
            return detail(runId);
        } catch (DuplicateKeyException duplicate) {
            Map<String, Object> raced = runs.byRequestKey(pipelineId, requestKey);
            if (raced != null) return detail(((Number) raced.get("id")).longValue());
            if (runs.activeCount(pipelineId) > 0) throw new IllegalStateException("该流程已有任务运行或排队");
            throw duplicate;
        }
    }

    private void enqueue(long runId, long pipelineId, long revisionId, BiActor actor, int timeout, RunContext context) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            execute(runId, pipelineId, revisionId, actor, context);
            return null;
        });
        ScheduledFuture<?> timerTask = timer.schedule(() -> expire(runId), timeout, TimeUnit.SECONDS);
        timeouts.put(runId, timerTask);
        futures.put(runId, task);
        try {
            executor.execute(task);
        } catch (RuntimeException rejected) {
            futures.remove(runId, task);
            ScheduledFuture<?> pendingTimeout = timeouts.remove(runId);
            if (pendingTimeout != null) pendingTimeout.cancel(false);
            runs.fail(runId, "流程执行队列已满，请稍后重试");
            runs.closePendingNodes(runId, "SKIPPED", "流程未能进入执行队列");
        }
    }

    private void execute(long runId, long pipelineId, long revisionId, BiActor actor, RunContext context) {
        if (runs.start(runId) != 1) {
            futures.remove(runId);
            timeouts.remove(runId);
            return;
        }
        boolean production = context.production();
        try {
            LiveActorResolver.Snapshot snapshot = live.resolve(actor);
            snapshot.require("bi:pipeline:run");
            @SuppressWarnings("unchecked") List<Map<String, Object>>[] sinkResult = new List[]{List.of()};
            ActorScope.run(snapshot.actor(), snapshot.capabilities(), () -> {
                try {
                    sinkResult[0] = runGraph(runId, pipelineId, revisionId, context);
                } catch (RuntimeException error) {
                    throw error;
                } catch (Exception error) {
                    throw new PipelineRunFailure(error);
                }
            });
            if (runs.status(runId).equals("RUNNING")) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("mode", context.mode());
                summary.put("rowLimit", context.rowLimit());
                summary.put("warehouseWrites", production ? sinkResult[0].stream()
                        .mapToLong(sink -> number(sink.get("writtenRows")) == null ? 0L : number(sink.get("writtenRows")))
                        .sum() : 0L);
                summary.put("sinks", sinkResult[0]);
                summary.put("note", production ? "生产运行已按发布定义写入数仓逻辑层" : "预览只处理样本数据，不写入数仓");
                runs.succeed(runId, write(summary));
                if (production && lineageRebuild != null) lineageRebuild.afterChange();
            }
        } catch (PipelineNodeFailure failure) {
            String reason = failure.getCause() instanceof QualityGateRejected rejected
                    ? rejected.getMessage()
                    : (production ? "节点执行失败，检查节点配置、来源授权、字段映射及数仓目标" : "节点预览失败，检查节点配置、来源授权及字段映射");
            if (failure.getCause() != null && failure.getCause().getMessage() != null
                    && !(failure.getCause() instanceof QualityGateRejected))
                reason = bounded(failure.getCause().getMessage(), 400);
            runs.failNode(runId, failure.nodeKey, reason);
            runs.closePendingNodes(runId, "SKIPPED", "上游节点未成功");
            if ("RUNNING".equals(runs.status(runId)))
                runs.fail(runId, "节点 " + failure.nodeKey + " 失败：" + reason + "。流程未完成数仓写入");
        } catch (RuntimeException failure) {
            runs.closePendingNodes(runId, "SKIPPED", "流程未成功完成");
            if ("RUNNING".equals(runs.status(runId)))
                runs.fail(runId, production ? "流程生产运行失败或执行身份已失效；未保证完成数仓写入" : "流程预览失败或执行身份已失效；未写入任何数仓数据");
        } finally {
            futures.remove(runId);
            ScheduledFuture<?> timeout = timeouts.remove(runId);
            if (timeout != null) timeout.cancel(false);
        }
    }

    private List<Map<String, Object>> runGraph(long runId, long pipelineId, long revisionId, RunContext context) throws Exception {
        List<Map<String, Object>> nodeRows = pipelines.nodes(revisionId);
        List<Map<String, Object>> edgeRows = pipelines.edges(revisionId);
        Map<String, Map<String, Object>> nodeByKey = new LinkedHashMap<>();
        for (Map<String, Object> node : nodeRows) nodeByKey.put(String.valueOf(node.get("nodeKey")), node);
        Map<String, List<String>> parents = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (String key : nodeByKey.keySet()) {
            parents.put(key, new ArrayList<>());
            children.put(key, new ArrayList<>());
            indegree.put(key, 0);
        }
        for (Map<String, Object> edge : edgeRows) {
            String source = String.valueOf(edge.get("sourceNodeKey"));
            String target = String.valueOf(edge.get("targetNodeKey"));
            parents.get(target).add(source);
            children.get(source).add(target);
            indegree.compute(target, (key, count) -> count + 1);
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        indegree.forEach((key, count) -> {
            if (count == 0) ready.add(key);
        });
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String key = ready.removeFirst();
            order.add(key);
            for (String child : children.get(key))
                if (indegree.compute(child, (ignored, count) -> count - 1) == 0) ready.add(child);
        }
        if (order.size() != nodeRows.size()) throw new IllegalArgumentException("流程定义已经失效，请重新发布");
        Map<String, Output> outputs = new HashMap<>();
        List<Map<String, Object>> sinks = new ArrayList<>();
        for (String key : order) {
            if (Thread.currentThread().isInterrupted() || !"RUNNING".equals(runs.status(runId))) return sinks;
            Map<String, Object> node = nodeByKey.get(key);
            String type = String.valueOf(node.get("nodeType"));
            if (runs.startNode(runId, key) != 1) return sinks;
            List<String> inputKeys = parents.get(key);
            List<Output> inputs = inputKeys.stream().map(outputs::get).filter(Objects::nonNull).toList();
            long inputRows = inputs.stream().mapToLong(o -> o.rows().size()).sum();
            long started = System.nanoTime();
            try {
                Output output = executeNode(node, type, inputKeys, inputs, sinks, context);
                outputs.put(key, output);
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("fieldCount", output.columns().size());
                summary.put("sampleRows", output.rows().size());
                summary.put("sampleTruncated", output.truncated());
                summary.put("previewOnly", !context.production() && ("WAREHOUSE_PUBLISH".equals(type) || "QUALITY_GATE".equals(type)));
                if ("QUALITY_GATE".equals(type) && !sinks.isEmpty()) summary.putAll(sinks.get(sinks.size() - 1));
                if ("WAREHOUSE_PUBLISH".equals(type) && !sinks.isEmpty()) summary.putAll(sinks.get(sinks.size() - 1));
                if (runs.succeedNode(runId, key, inputRows, output.rows().size(), write(summary)) != 1) return sinks;
            } catch (Exception failure) {
                throw new PipelineNodeFailure(key, failure);
            }
            if (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) > Math.max(120, context.rowLimit() / 100))
                throw new PipelineNodeFailure(key, new IllegalStateException("node timeout"));
        }
        return sinks;
    }

    @SuppressWarnings("unchecked")
    private Output executeNode(Map<String, Object> node, String type, List<String> inputKeys, List<Output> inputs,
            List<Map<String, Object>> sinks, RunContext context) throws Exception {
        Map<String, Object> config = asMap(node.get("config"));
        if ("SOURCE_DATASET".equals(type)) {
            Long datasetId = number(config.get("datasetId"));
            Map<String, Object> preview = context.production()
                    ? datasets.boundedExtract(datasetId, context.rowLimit())
                    : datasets.preview(datasetId, context.rowLimit());
            Object rawColumns = preview.get("columns"), rawRows = preview.get("rows");
            if (!(rawColumns instanceof List<?> columns) || !(rawRows instanceof List<?> rows))
                throw new IllegalArgumentException("数据集预览结果无效");
            List<Map<String, Object>> records = new ArrayList<>();
            for (Object row : rows)
                if (row instanceof Map<?, ?> map) {
                    Map<String, Object> record = new LinkedHashMap<>();
                    map.forEach((field, value) -> record.put(String.valueOf(field), value));
                    records.add(record);
                }
            return new Output(columns.stream().map(String::valueOf).toList(), records, records.size() >= context.rowLimit());
        }
        if (inputs.isEmpty()) throw new IllegalArgumentException("节点缺少输入数据");
        if ("JOIN".equals(type)) return join(node, inputKeys, inputs, config, context.rowLimit());
        Output input = inputs.get(0);
        if ("FILTER".equals(type)) return filter(input, config, context.rowLimit());
        if ("TRANSFORM".equals(type)) return transform(input, config);
        if ("QUALITY_GATE".equals(type)) return qualityGate(node, input, config, sinks, context);
        if ("WAREHOUSE_PUBLISH".equals(type)) return warehousePublish(node, input, config, sinks, context);
        throw new IllegalArgumentException("流程节点类型尚不支持");
    }

    private Output warehousePublish(Map<String, Object> node, Output input, Map<String, Object> config,
            List<Map<String, Object>> sinks, RunContext context) throws Exception {
        Map<String, Object> sink = new LinkedHashMap<>();
        sink.put("nodeKey", node.get("nodeKey"));
        sink.put("modelCode", config.get("modelCode"));
        sink.put("layer", config.get("layer"));
        sink.put("writeMode", config.getOrDefault("writeMode", "REPLACE"));
        sink.put("sampleRows", input.rows().size());
        sink.put("fieldCount", input.columns().size());
        sink.put("sampleTruncated", input.truncated());
        if (!context.production()) {
            sink.put("previewOnly", true);
            sink.put("writtenRows", 0);
            sinks.add(sink);
            return input;
        }
        String layer = String.valueOf(config.get("layer")).toUpperCase(Locale.ROOT);
        if (!WAREHOUSE_LAYERS.contains(layer)) throw new IllegalArgumentException("数仓发布节点只支持 DWD、DWS、ADS 逻辑层");
        String modelCode = String.valueOf(config.get("modelCode"));
        if (!modelCode.matches("[A-Za-z][A-Za-z0-9_]{1,95}")) throw new IllegalArgumentException("数仓模型编码无效");
        String writeMode = String.valueOf(config.getOrDefault("writeMode", "REPLACE")).toUpperCase(Locale.ROOT);
        if (!Set.of("REPLACE", "APPEND", "UPSERT").contains(writeMode))
            throw new IllegalArgumentException("数仓写入模式无效");
        List<String> mergeKeys = mergeKeys(config.get("mergeKeys"), input.columns());
        if ("UPSERT".equals(writeMode) && mergeKeys.isEmpty())
            throw new IllegalArgumentException("UPSERT 写入须提供 mergeKeys");
        if (input.truncated())
            throw new IllegalArgumentException("输入数据超过本次生产行数上限，已截断，拒绝写入数仓");
        fieldStandards.assertPipelineColumns(input.columns(), input.rows());
        String database = "wanfo_wh_" + layer.toLowerCase(Locale.ROOT);
        String table = "pipe_" + modelCode.toLowerCase(Locale.ROOT);
        BiDatasource target = requireWarehouseTarget(context.targetDatasourceId());
        WarehousePublisher.Engine engine = WarehousePublisher.parseEngine(target.getType());
        try (Connection connection = JdbcConnectionSettings.open(target)) {
            String[] promotePhase = { "PENDING" };
            var progress = (java.util.function.Consumer<WarehousePublisher.Progress>) phase -> promotePhase[0] = phase.phase();
            var result = switch (writeMode) {
                case "APPEND" -> WarehousePublisher.appendMaps(connection, engine, database, table, context.runId(),
                        input.columns(), input.rows(), ClickHouseBatchPublisher.DEFAULT_BATCH_SIZE, context.rowLimit(),
                        progress);
                case "UPSERT" -> WarehousePublisher.upsertMaps(connection, engine, database, table, context.runId(),
                        input.columns(), input.rows(), mergeKeys, ClickHouseBatchPublisher.DEFAULT_BATCH_SIZE,
                        context.rowLimit(), progress);
                default -> WarehousePublisher.publishMaps(connection, engine, database, table, context.runId(),
                        input.columns(), input.rows(), ClickHouseBatchPublisher.DEFAULT_BATCH_SIZE, context.rowLimit(),
                        progress);
            };
            sink.put("previewOnly", false);
            sink.put("writtenRows", result.rows());
            sink.put("targetTable", result.table());
            sink.put("targetReplaced", result.replaced());
            sink.put("publishMode", result.mode().name());
            sink.put("engine", engine.name());
            sink.put("promoteStatus", "SUCCEEDED");
            sink.put("promoteLastPhase", promotePhase[0]);
            sink.put("targetDatasourceId", context.targetDatasourceId());
        }
        sinks.add(sink);
        return input;
    }

    private BiDatasource requireWarehouseTarget(Long targetDatasourceId) {
        BiDatasource target = sources.getById(targetDatasourceId);
        if (target == null || !"0".equals(target.getStatus()))
            throw new IllegalArgumentException("数仓目标数据源不存在或已停用");
        WarehousePublisher.Engine engine = WarehousePublisher.parseEngine(target.getType());
        String schema = target.getSourceSchema() == null ? "" : target.getSourceSchema();
        if (engine == WarehousePublisher.Engine.CLICKHOUSE && !"wanfo_wh_ods".equals(schema))
            throw new IllegalArgumentException("ClickHouse 目标 schema 须配置为 wanfo_wh_ods（可写入各逻辑层）");
        return target;
    }

    private Output join(Map<String, Object> node, List<String> inputKeys, List<Output> inputs, Map<String, Object> config, int rowLimit) {
        if (inputs.size() != 2) throw new IllegalArgumentException("关联节点必须连接两路输入");
        List<?> leftKeys = list(config.get("leftKeys")), rightKeys = list(config.get("rightKeys"));
        if (leftKeys.size() != rightKeys.size() || leftKeys.isEmpty()) throw new IllegalArgumentException("关联字段映射无效");
        Output left = inputs.get(0), right = inputs.get(1);
        String joinType = String.valueOf(config.getOrDefault("joinType", "LEFT"));
        List<Map<String, Object>> result = new ArrayList<>();
        boolean[] rightMatches = new boolean[right.rows().size()];
        for (Map<String, Object> l : left.rows()) {
            boolean matched = false;
            for (int rIndex = 0; rIndex < right.rows().size(); rIndex++) {
                Map<String, Object> r = right.rows().get(rIndex);
                boolean equal = true;
                for (int i = 0; i < leftKeys.size(); i++) {
                    if (!keyEquals(l.get(String.valueOf(leftKeys.get(i))), r.get(String.valueOf(rightKeys.get(i))))) {
                        equal = false;
                        break;
                    }
                }
                if (equal) {
                    matched = true;
                    rightMatches[rIndex] = true;
                    result.add(merge(l, r, inputKeys.get(1)));
                    if (result.size() >= rowLimit) break;
                }
            }
            if (!matched && Set.of("LEFT", "FULL").contains(joinType) && result.size() < rowLimit)
                result.add(merge(l, Map.of(), inputKeys.get(1)));
            if (result.size() >= rowLimit) break;
        }
        if (Set.of("RIGHT", "FULL").contains(joinType) && result.size() < rowLimit)
            for (int i = 0; i < right.rows().size() && result.size() < rowLimit; i++)
                if (!rightMatches[i]) result.add(merge(Map.of(), right.rows().get(i), inputKeys.get(1)));
        List<String> cols = new ArrayList<>(left.columns());
        for (String column : right.columns()) cols.add(cols.contains(column) ? inputKeys.get(1) + "." + column : column);
        return new Output(cols, result, left.truncated() || right.truncated() || result.size() >= rowLimit);
    }

    private Output filter(Output input, Map<String, Object> config, int rowLimit) {
        Object raw = config.get("conditions");
        if (!(raw instanceof List<?> conditions) || conditions.isEmpty()) return input;
        List<Map<String, Object>> result = input.rows().stream().filter(row -> conditions.stream().allMatch(c -> matches(row, c))).limit(rowLimit).toList();
        return new Output(input.columns(), result, input.truncated() || result.size() >= rowLimit);
    }

    private Output qualityGate(Map<String, Object> node, Output input, Map<String, Object> config,
            List<Map<String, Object>> sinks, RunContext context) {
        Long datasetId = number(config.get("ruleSetDatasetId")), ruleSetId = number(config.get("ruleSetId"));
        if (datasetId == null || ruleSetId == null) throw new IllegalArgumentException("质量门禁未绑定已发布规则版本");
        if (input.rows().isEmpty()) throw new QualityGateRejected("质量门禁没有可评估的样本行");
        List<Map<String, Object>> rules = qualityRules.publishedRules(datasetId, ruleSetId);
        List<String> severityOrder = List.of("INFO", "WARN", "ERROR", "FATAL");
        String threshold = String.valueOf(config.getOrDefault("severity", "ERROR"));
        int thresholdRank = severityOrder.indexOf(threshold);
        if (thresholdRank < 0) throw new IllegalArgumentException("质量门禁阻断等级无效");
        double minPassRate = decimal(config.getOrDefault("minPassRate", 100));
        if (!Double.isFinite(minPassRate) || minPassRate < 0 || minPassRate > 100)
            throw new IllegalArgumentException("质量门禁通过率须在 0～100 之间");
        int evaluated = 0, passed = 0, failed = 0;
        List<String> failedCodes = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            if (!enabled(rule.get("enabled"))) continue;
            String severity = String.valueOf(rule.getOrDefault("severity", "ERROR"));
            int rank = severityOrder.indexOf(severity);
            if (rank < thresholdRank) continue;
            String type = String.valueOf(rule.get("type")), field = String.valueOf(rule.get("field"));
            if (!input.columns().contains(field)) throw new IllegalArgumentException("质量门禁字段不存在于当前流程输入: " + field);
            Map<String, Object> ruleConfig = asMap(rule.get("config"));
            long bad = switch (type) {
                case "NOT_NULL" -> nullCount(input, field, enabled(ruleConfig.get("emptyStringIsNull")));
                case "UNIQUE" -> duplicateCount(input, field);
                case "NULL_RATE_MAX" -> nullRate(input, field, enabled(ruleConfig.get("emptyStringIsNull"))) > decimal(ruleConfig.get("maxRate")) ? 1 : 0;
                case "RANGE" -> rangeViolations(input, field, ruleConfig);
                case "REGEX" -> regexViolations(input, field, ruleConfig);
                case "FRESHNESS" -> freshnessViolations(input, field, ruleConfig);
                case "RECONCILIATION" -> 0; // 门禁样本无历史基线，生产对账由质量任务执行
                default -> throw new IllegalArgumentException("流程暂不支持该质量规则类型: " + type);
            };
            evaluated++;
            if (bad == 0) passed++;
            else {
                failed++;
                failedCodes.add(bounded(String.valueOf(rule.getOrDefault("code", type)), 96));
            }
        }
        if (evaluated == 0) throw new IllegalArgumentException("阻断等级范围内没有启用的质量规则");
        double passRate = passed * 100.0 / evaluated;
        // Default: PRODUCTION blocks; PREVIEW warns unless blockOnFailure explicitly set.
        boolean blockOnFailure = config.containsKey("blockOnFailure")
                ? enabled(config.get("blockOnFailure"))
                : context.production();
        String previewPolicy = String.valueOf(config.getOrDefault("previewPolicy",
                blockOnFailure ? "BLOCK" : "WARN")).trim().toUpperCase(Locale.ROOT);
        if (!Set.of("WARN", "BLOCK").contains(previewPolicy))
            throw new IllegalArgumentException("预览质量门禁策略仅支持 WARN 或 BLOCK");
        if (!context.production() && "WARN".equals(previewPolicy)) blockOnFailure = false;
        if (!context.production() && "BLOCK".equals(previewPolicy)) blockOnFailure = true;
        boolean failedGate = passRate + 1e-9 < minPassRate;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("nodeKey", node.get("nodeKey"));
        summary.put("nodeType", "QUALITY_GATE");
        summary.put("ruleSetDatasetId", datasetId);
        summary.put("ruleSetId", ruleSetId);
        summary.put("rulesEvaluated", evaluated);
        summary.put("rulesPassed", passed);
        summary.put("rulesFailed", failed);
        summary.put("passRate", Math.round(passRate * 100.0) / 100.0);
        summary.put("minimumPassRate", minPassRate);
        summary.put("blockingSeverity", threshold);
        summary.put("blockOnFailure", blockOnFailure);
        summary.put("previewPolicy", previewPolicy);
        summary.put("warnedOnly", failedGate && !blockOnFailure);
        summary.put("sampleRows", input.rows().size());
        summary.put("sampleTruncated", input.truncated());
        summary.put("previewOnly", !context.production());
        if (failedGate) summary.put("failedRuleCodes", List.copyOf(failedCodes));
        sinks.add(summary);
        if (failedGate && blockOnFailure)
            throw new QualityGateRejected("质量门禁未通过：" + passed + "/" + evaluated + " 条阻断规则通过（"
                    + String.format(Locale.ROOT, "%.2f", passRate) + "%），要求至少 "
                    + String.format(Locale.ROOT, "%.2f", minPassRate) + "%；失败规则 " + String.join(",", failedCodes)
                    + (context.production() ? "" : "。当前仅校验最多 100 行预览样本"));
        return input;
    }

    private static long nullCount(Output input, String field, boolean emptyStringIsNull) {
        return input.rows().stream().filter(row -> {
            Object value = row.get(field);
            return value == null || (emptyStringIsNull && value instanceof String text && text.isEmpty());
        }).count();
    }

    private static long rangeViolations(Output input, String field, Map<String, Object> config) {
        Double min = config.containsKey("min") ? decimal(config.get("min")) : null;
        Double max = config.containsKey("max") ? decimal(config.get("max")) : null;
        long bad = 0, checked = 0;
        for (Map<String, Object> row : input.rows()) {
            Object value = row.get(field);
            if (value == null) continue;
            checked++;
            try {
                double number = new BigDecimal(String.valueOf(value)).doubleValue();
                if (min != null && number < min) bad++;
                else if (max != null && number > max) bad++;
            } catch (Exception ignored) {
                bad++;
            }
        }
        if (checked == 0) throw new IllegalArgumentException("RANGE 规则没有可评估的数值样本");
        return bad;
    }

    private static long regexViolations(Output input, String field, Map<String, Object> config) {
        Pattern pattern = Pattern.compile(String.valueOf(config.get("pattern")));
        long bad = 0, checked = 0;
        for (Map<String, Object> row : input.rows()) {
            Object value = row.get(field);
            if (value == null) continue;
            checked++;
            if (!pattern.matcher(String.valueOf(value)).find()) bad++;
        }
        if (checked == 0) throw new IllegalArgumentException("REGEX 规则没有可评估的非空样本");
        return bad;
    }

    private static long freshnessViolations(Output input, String field, Map<String, Object> config) {
        long maxAge = (long) decimal(config.get("maxAgeSeconds"));
        java.time.Instant newest = null;
        for (Map<String, Object> row : input.rows()) {
            Object value = row.get(field);
            if (value == null) continue;
            java.time.Instant instant = null;
            if (value instanceof java.time.Instant i) instant = i;
            else if (value instanceof java.time.OffsetDateTime t) instant = t.toInstant();
            else if (value instanceof java.time.LocalDateTime t) instant = t.toInstant(java.time.ZoneOffset.UTC);
            else if (value instanceof java.sql.Timestamp t) instant = t.toInstant();
            else try { instant = java.time.Instant.parse(String.valueOf(value)); } catch (Exception ignored) {}
            if (instant != null && (newest == null || instant.isAfter(newest))) newest = instant;
        }
        if (newest == null) throw new IllegalArgumentException("FRESHNESS 规则没有可评估的时间样本");
        return java.time.Instant.now().getEpochSecond() - newest.getEpochSecond() > maxAge ? 1 : 0;
    }

    private static double nullRate(Output input, String field, boolean emptyStringIsNull) {
        return input.rows().isEmpty() ? 0.0 : (double) nullCount(input, field, emptyStringIsNull) / input.rows().size();
    }

    private static long duplicateCount(Output input, String field) {
        Set<String> seen = new HashSet<>();
        long duplicates = 0;
        for (Map<String, Object> row : input.rows()) {
            Object value = row.get(field);
            if (value == null) continue;
            String normalized = value instanceof Number ? new BigDecimal(String.valueOf(value)).stripTrailingZeros().toPlainString() : String.valueOf(value);
            if (!seen.add(normalized)) duplicates++;
        }
        return duplicates;
    }

    private boolean matches(Map<String, Object> row, Object raw) {
        if (!(raw instanceof Map<?, ?> condition)) return false;
        String field = String.valueOf(condition.get("field")), op = String.valueOf(condition.get("operator"));
        Object actual = row.get(field), expected = condition.get("value");
        return switch (op) {
            case "IS_NULL" -> actual == null;
            case "NOT_NULL" -> actual != null;
            case "EQ" -> keyEquals(actual, expected);
            case "NE" -> !keyEquals(actual, expected);
            case "CONTAINS" -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
            case "GT", "GTE", "LT", "LTE" -> {
                if (actual == null || expected == null) yield false;
                int cmp = compare(actual, expected);
                yield switch (op) {
                    case "GT" -> cmp > 0;
                    case "GTE" -> cmp >= 0;
                    case "LT" -> cmp < 0;
                    default -> cmp <= 0;
                };
            }
            default -> false;
        };
    }

    private static int compare(Object left, Object right) {
        if (left == null || right == null) return Integer.MIN_VALUE;
        try {
            return new BigDecimal(String.valueOf(left)).compareTo(new BigDecimal(String.valueOf(right)));
        } catch (NumberFormatException ignored) {
            return String.valueOf(left).compareTo(String.valueOf(right));
        }
    }

    private Output transform(Output input, Map<String, Object> config) {
        Object raw = config.get("mappings");
        if (!(raw instanceof List<?> mappings) || mappings.isEmpty()) return input;
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : mappings) {
            if (!(item instanceof Map<?, ?> map)) continue;
            columns.add(String.valueOf(map.get("target")));
        }
        for (Map<String, Object> row : input.rows()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Object item : mappings)
                if (item instanceof Map<?, ?> map)
                    out.put(String.valueOf(map.get("target")), row.get(String.valueOf(map.get("source"))));
            rows.add(out);
        }
        return new Output(columns, rows, input.truncated());
    }

    private static Map<String, Object> merge(Map<String, Object> left, Map<String, Object> right, String rightKey) {
        Map<String, Object> merged = new LinkedHashMap<>(left);
        right.forEach((key, value) -> merged.put(merged.containsKey(key) ? rightKey + "." + key : key, value));
        return merged;
    }

    private static boolean keyEquals(Object left, Object right) {
        if (left == null || right == null) return left == right;
        if (left instanceof Number || right instanceof Number)
            try {
                return new BigDecimal(String.valueOf(left)).compareTo(new BigDecimal(String.valueOf(right))) == 0;
            } catch (NumberFormatException ignored) {
            }
        return Objects.equals(String.valueOf(left), String.valueOf(right));
    }

    public List<Map<String, Object>> list(Long pipelineId, int limit) {
        resources.require("pipeline", pipelineId, "view");
        return runs.list(pipelineId, Math.max(1, Math.min(limit, 100)));
    }

    public Map<String, Object> detail(Long runId) {
        Long pipelineId = runs.pipelineId(runId);
        if (pipelineId == null) throw new IllegalArgumentException("流程运行记录不存在");
        resources.require("pipeline", pipelineId, "view");
        Map<String, Object> detail = new LinkedHashMap<>(runs.run(runId));
        detail.put("nodes", runs.nodes(runId));
        return detail;
    }

    public Map<String, Object> cancel(Long runId) {
        Long pipelineId = runs.pipelineId(runId);
        if (pipelineId == null) throw new IllegalArgumentException("流程运行记录不存在");
        resources.require("pipeline", pipelineId, "run");
        if (runs.cancel(runId) != 1) throw new IllegalStateException("该运行已结束或无法取消");
        Future<?> future = futures.remove(runId);
        if (future != null) future.cancel(true);
        ScheduledFuture<?> timeout = timeouts.remove(runId);
        if (timeout != null) timeout.cancel(false);
        runs.closePendingNodes(runId, "CANCELLED", "流程已取消");
        return detail(runId);
    }

    @Scheduled(fixedDelayString = "${bi.pipeline.recovery-delay-ms:60000}")
    public void recoverExpiredRuns() {
        for (Long runId : runs.expiredActiveRuns()) expire(runId);
        runs.closeAbandonedNodes();
    }

    private void expire(long runId) {
        ScheduledFuture<?> timeout = timeouts.remove(runId);
        if (timeout != null) timeout.cancel(false);
        if (runs.timeout(runId) == 1) {
            Future<?> future = futures.remove(runId);
            if (future != null) future.cancel(true);
            runs.closePendingNodes(runId, "TIMED_OUT", "流程超过超时时间，或服务中断后由巡检收束");
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("运行摘要无法序列化");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, val) -> out.put(String.valueOf(key), val));
            return out;
        }
        if (value instanceof String text)
            try {
                return json.readValue(text, Map.class);
            } catch (RuntimeException ignored) {
            }
        return Map.of();
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> l ? l : List.of();
    }

    private static List<String> mergeKeys(Object raw, List<String> columns) {
        if (!(raw instanceof List<?> keys) || keys.isEmpty()) return List.of();
        Set<String> available = new java.util.HashSet<>();
        if (columns != null) columns.forEach(c -> available.add(c.toLowerCase(Locale.ROOT)));
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (Object key : keys) {
            String field = String.valueOf(key == null ? "" : key).trim();
            if (field.isBlank()) throw new IllegalArgumentException("mergeKeys 含空字段名");
            String lower = field.toLowerCase(Locale.ROOT);
            if (!available.contains(lower)) throw new IllegalArgumentException("mergeKeys 字段不存在于流程输出: " + field);
            if (!seen.add(lower)) throw new IllegalArgumentException("mergeKeys 字段重复: " + field);
            normalized.add(field);
        }
        if (normalized.size() > 16) throw new IllegalArgumentException("mergeKeys 最多 16 个字段");
        return normalized;
    }

    private static Long number(Object value) {
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double decimal(Object value) {
        try {
            return value instanceof Number ? new BigDecimal(String.valueOf(value)).doubleValue() : Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException error) {
            return Double.NaN;
        }
    }

    private static boolean enabled(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof Number number && number.intValue() == 1) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String bounded(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(max, value.length()));
    }

    private record RunContext(String mode, int rowLimit, Long targetDatasourceId, long runId) {
        boolean production() {
            return "PRODUCTION".equals(mode);
        }
    }

    private record Output(List<String> columns, List<Map<String, Object>> rows, boolean truncated) {
    }

    private static class PipelineNodeFailure extends RuntimeException {
        private final String nodeKey;

        PipelineNodeFailure(String nodeKey, Throwable cause) {
            super(cause);
            this.nodeKey = nodeKey;
        }
    }

    private static class QualityGateRejected extends RuntimeException {
        QualityGateRejected(String message) {
            super(message);
        }
    }

    private static class PipelineRunFailure extends RuntimeException {
        PipelineRunFailure(Throwable cause) {
            super(cause);
        }
    }
}
