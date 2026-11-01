package com.wanfo.bi.service;

import com.wanfo.bi.mapper.CdcIngestMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.BiResourceAuthorizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates CDC ingest from an external Debezium (or equivalent) staging table into the warehouse.
 * The platform does not capture binlog itself; it periodically FULL/INCREMENTAL loads the staging table.
 */
@Service
public class CdcIngestService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CdcIngestService.class);
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{1,95}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");

    public record CreateRequest(String jobCode, String name, Long sourceDatasourceId, String stagingTable,
            Long targetDatasourceId, String targetLayer, String targetTable, String watermarkColumn,
            Integer intervalMinutes) {
    }

    private final CdcIngestMapper mapper;
    private final WarehouseLoadService loads;
    private final com.wanfo.bi.mapper.WarehouseLoadMapper loadRuns;
    private final TaskPoolGateService poolGate;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final BiResourceAuthorizer resources;

    public CdcIngestService(CdcIngestMapper mapper, WarehouseLoadService loads,
            com.wanfo.bi.mapper.WarehouseLoadMapper loadRuns, TaskPoolGateService poolGate,
            CapabilityProvider capabilities, CurrentActorProvider actors, BiResourceAuthorizer resources) {
        this.mapper = mapper;
        this.loads = loads;
        this.loadRuns = loadRuns;
        this.poolGate = poolGate;
        this.capabilities = capabilities;
        this.actors = actors;
        this.resources = resources;
    }

    public List<Map<String, Object>> list() {
        capabilities.require("bi:resource:admin");
        return mapper.list();
    }

    @Transactional
    public Map<String, Object> create(CreateRequest request) {
        capabilities.require("bi:resource:admin");
        if (request == null || request.jobCode() == null || !CODE.matcher(request.jobCode().trim()).matches())
            throw new IllegalArgumentException("任务编码无效");
        if (request.name() == null || request.name().isBlank() || request.name().trim().length() > 160)
            throw new IllegalArgumentException("名称须为 1～160 字");
        if (request.sourceDatasourceId() == null || request.targetDatasourceId() == null)
            throw new IllegalArgumentException("源与目标数据源不能为空");
        if (!IDENTIFIER.matcher(request.stagingTable() == null ? "" : request.stagingTable().trim()).matches())
            throw new IllegalArgumentException("staging 表名无效");
        if (!IDENTIFIER.matcher(request.targetTable() == null ? "" : request.targetTable().trim()).matches())
            throw new IllegalArgumentException("目标表名无效");
        String layer = request.targetLayer() == null ? "ODS" : request.targetLayer().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ODS", "DWD", "DWS", "ADS").contains(layer)) throw new IllegalArgumentException("目标层无效");
        String watermark = request.watermarkColumn() == null || request.watermarkColumn().isBlank()
                ? "cdc_ts" : request.watermarkColumn().trim();
        if (!IDENTIFIER.matcher(watermark).matches()) throw new IllegalArgumentException("水位字段无效");
        int interval = request.intervalMinutes() == null ? 5 : request.intervalMinutes();
        if (interval < 1 || interval > 1440) throw new IllegalArgumentException("调度间隔须为 1～1440 分钟");
        resources.require("datasource", request.sourceDatasourceId(), "model");
        resources.require("datasource", request.targetDatasourceId(), "model");
        Map<String, Object> row = new HashMap<>();
        row.put("jobCode", request.jobCode().trim());
        row.put("name", request.name().trim());
        row.put("sourceDatasourceId", request.sourceDatasourceId());
        row.put("stagingTable", request.stagingTable().trim());
        row.put("targetDatasourceId", request.targetDatasourceId());
        row.put("targetLayer", layer);
        row.put("targetTable", request.targetTable().trim());
        row.put("watermarkColumn", watermark);
        row.put("intervalMinutes", interval);
        row.put("createBy", actors.currentActor().username());
        if (mapper.insert(row) != 1 || !(row.get("id") instanceof Number))
            throw new IllegalStateException("CDC 任务创建失败");
        return mapper.find(((Number) row.get("id")).longValue());
    }

    @Transactional
    public Map<String, Object> pause(Long id) {
        capabilities.require("bi:resource:admin");
        if (mapper.find(id) == null) throw new IllegalArgumentException("CDC 任务不存在");
        mapper.updateStatus(id, "PAUSED", actors.currentActor().username());
        return mapper.find(id);
    }

    @Transactional
    public Map<String, Object> resume(Long id) {
        capabilities.require("bi:resource:admin");
        if (mapper.find(id) == null) throw new IllegalArgumentException("CDC 任务不存在");
        mapper.updateStatus(id, "ACTIVE", actors.currentActor().username());
        return mapper.find(id);
    }

    @Scheduled(fixedDelayString = "${bi.cdc.scheduler-delay-ms:60000}")
    public void pollDueJobs() {
        List<Map<String, Object>> due = mapper.due();
        for (Map<String, Object> job : due) {
            Long id = number(job.get("id"));
            try {
                runOnce(job);
            } catch (IllegalStateException laneBlocked) {
                String message = laneBlocked.getMessage() == null ? "" : laneBlocked.getMessage();
                if (message.contains("任务分池已暂停") || message.contains("任务分池并发已满")) {
                    LOGGER.info("CDC job {} skipped: {}", id, message);
                    continue;
                }
                LOGGER.warn("CDC job {} failed", id, laneBlocked);
                mapper.markFailure(id, bounded(message, 900), "system");
            } catch (Exception failure) {
                LOGGER.warn("CDC job {} failed", id, failure);
                mapper.markFailure(id, bounded(failure.getMessage(), 900), "system");
            }
        }
    }

    private void runOnce(Map<String, Object> job) {
        poolGate.assertLaneRunnable("CDC_JOB", loadRuns.activeCdcRunCount());
        Long sourceId = number(job.get("sourceDatasourceId"));
        Long targetId = number(job.get("targetDatasourceId"));
        String staging = String.valueOf(job.get("stagingTable"));
        String targetTable = String.valueOf(job.get("targetTable"));
        String layer = String.valueOf(job.get("targetLayer"));
        String watermark = String.valueOf(job.get("watermarkColumn"));
        boolean first = job.get("lastWatermark") == null || String.valueOf(job.get("lastWatermark")).isBlank();
        WarehouseLoadService.LoadRequest request = first
                ? new WarehouseLoadService.LoadRequest(sourceId, targetId, staging, targetTable, null, "FULL", null, List.of(), layer)
                : new WarehouseLoadService.LoadRequest(sourceId, targetId, staging, targetTable, null, "INCREMENTAL",
                        watermark, List.of(watermark), layer);
        Map<String, Object> run = loads.trigger(request, "cdc-" + job.get("jobCode"));
        mapper.markSuccess(number(job.get("id")), String.valueOf(run.getOrDefault("id", "")), "system");
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static String bounded(String value, int max) {
        return value == null ? "CDC 失败" : value.substring(0, Math.min(max, value.length()));
    }
}
