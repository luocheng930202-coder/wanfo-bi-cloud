package com.wanfo.bi.service;

import com.wanfo.bi.mapper.PipelineMapper;
import com.wanfo.bi.mapper.PipelineScheduleMapper;
import com.wanfo.bi.security.ActorScope;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.LiveActorResolver;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Interval/calendar schedules that trigger PRODUCTION pipeline runs. */
@Service
public class PipelineScheduleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineScheduleService.class);
    private static final int MAX_FAILURES = 3;

    private final PipelineScheduleMapper schedules;
    private final PipelineMapper pipelines;
    private final PipelineRunService runs;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final LiveActorResolver live;
    private final ObjectMapper json;

    public PipelineScheduleService(PipelineScheduleMapper schedules, PipelineMapper pipelines, PipelineRunService runs,
            BiResourceAuthorizer resources, CapabilityProvider capabilities, CurrentActorProvider actors,
            LiveActorResolver live, ObjectMapper json) {
        this.schedules = schedules;
        this.pipelines = pipelines;
        this.runs = runs;
        this.resources = resources;
        this.capabilities = capabilities;
        this.actors = actors;
        this.live = live;
        this.json = json;
    }

    public Map<String, Object> get(Long pipelineId) {
        capabilities.require("bi:pipeline:list");
        resources.require("pipeline", pipelineId, "view");
        Map<String, Object> row = schedules.byPipeline(pipelineId);
        return row == null ? Map.of() : decorate(row);
    }

    public Map<String, Object> save(Long pipelineId, ScheduleRequest request) {
        capabilities.require("bi:pipeline:run");
        resources.require("pipeline", pipelineId, "run");
        if (request == null) throw new IllegalArgumentException("调度配置不能为空");
        Map<String, Object> pipeline = pipelines.pipeline(pipelineId);
        if (pipeline == null) throw new IllegalArgumentException("流程不存在");
        Long revisionId = request.revisionId();
        if (revisionId != null) {
            Map<String, Object> revision = pipelines.revision(pipelineId, revisionId);
            if (revision == null || !"PUBLISHED".equals(String.valueOf(revision.get("status"))))
                throw new IllegalArgumentException("调度只能绑定已发布版本，或留空使用当前发布版本");
        } else if (pipeline.get("currentRevisionId") == null) {
            throw new IllegalArgumentException("流程尚未发布，不能创建生产调度");
        }
        SubscriptionSchedule schedule = request.schedule() == null
                ? new SubscriptionSchedule("interval", Math.max(15, request.intervalMinutes() == null ? 60 : request.intervalMinutes()), "", java.util.List.of(), "UTC")
                : request.schedule();
        int maxRows = request.maxRows() == null ? 100_000 : request.maxRows();
        if (maxRows < 1 || maxRows > 1_000_000) throw new IllegalArgumentException("调度行数上限须在 1～1000000 之间");
        int timeout = request.timeoutSeconds() == null ? 300 : request.timeoutSeconds();
        if (timeout < 30 || timeout > 1800) throw new IllegalArgumentException("调度超时须在 30～1800 秒之间");
        if (request.targetDatasourceId() == null) throw new IllegalArgumentException("调度必须指定数仓目标数据源");
        BiActor actor = actors.currentActor();
        LiveActorResolver.Snapshot snapshot = live.resolve(actor);
        snapshot.require("bi:pipeline:run");
        String name = request.name() == null || request.name().isBlank() ? String.valueOf(pipeline.get("name")) : request.name().trim();
        if (name.length() > 160) throw new IllegalArgumentException("调度名称过长");
        boolean enabled = request.enabled() == null || request.enabled();
        Instant next = schedule.nextAfter(Instant.now());
        Map<String, Object> existing = schedules.byPipeline(pipelineId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("pipelineId", pipelineId);
        row.put("revisionId", revisionId);
        row.put("name", name);
        row.put("enabled", enabled ? 1 : 0);
        row.put("scheduleJson", write(schedule));
        row.put("targetDatasourceId", request.targetDatasourceId());
        row.put("maxRows", maxRows);
        row.put("timeoutSeconds", timeout);
        row.put("ownerUserId", actor.userId());
        row.put("actorJson", write(actor));
        row.put("nextRunUtc", SubscriptionSchedule.utc(next));
        row.put("createBy", actor.username());
        if (existing == null) {
            if (schedules.insert(row) != 1 || !(row.get("id") instanceof Number)) throw new IllegalStateException("创建流程调度失败");
        } else {
            row.put("id", existing.get("id"));
            if (schedules.update(row) != 1) throw new IllegalStateException("更新流程调度失败");
        }
        return decorate(schedules.byPipeline(pipelineId));
    }

    public Map<String, Object> disable(Long pipelineId) {
        capabilities.require("bi:pipeline:run");
        resources.require("pipeline", pipelineId, "run");
        Map<String, Object> existing = schedules.byPipeline(pipelineId);
        if (existing == null) throw new IllegalArgumentException("流程尚未配置调度");
        schedules.disable(((Number) existing.get("id")).longValue());
        return decorate(schedules.byPipeline(pipelineId));
    }

    @Scheduled(fixedDelayString = "${bi.pipeline.schedule-poll-ms:60000}", initialDelayString = "${bi.pipeline.schedule-poll-ms:60000}")
    public void triggerDue() {
        for (Long id : schedules.dueIds()) {
            if (schedules.claim(id) != 1) continue;
            try {
                triggerOne(id);
            } catch (RuntimeException failure) {
                LOGGER.warn("Pipeline schedule {} trigger failed", id, failure);
                Map<String, Object> row = schedules.get(id);
                if (row != null) completeFailure(row, null, bounded(failure.getMessage(), 480));
            }
        }
    }

    private void triggerOne(Long scheduleId) {
        Map<String, Object> row = schedules.get(scheduleId);
        if (row == null || !enabled(row.get("enabled"))) return;
        BiActor actor = readActor(row.get("actorJson"));
        LiveActorResolver.Snapshot snapshot = live.resolve(actor);
        snapshot.require("bi:pipeline:run");
        Long pipelineId = number(row.get("pipelineId"));
        if (!resources.allowed("pipeline", pipelineId, "run")) {
            Map<String, Object> done = baseComplete(row, null, "DISABLED", "调度所有者已失去流程运行权限，已自动停用", MAX_FAILURES, false);
            done.put("nextRunUtc", SubscriptionSchedule.utc(Instant.now().plusSeconds(3600)));
            schedules.complete(done);
            return;
        }
        Long revisionId = number(row.get("revisionId"));
        if (revisionId == null) {
            Map<String, Object> pipeline = pipelines.pipeline(pipelineId);
            revisionId = pipeline == null ? null : number(pipeline.get("currentRevisionId"));
        }
        if (revisionId == null) {
            completeFailure(row, null, "流程没有可用的已发布版本");
            return;
        }
        final Long publishedRevisionId = revisionId;
        String requestKey = "schedule-" + scheduleId + "-" + UUID.randomUUID();
        try {
            @SuppressWarnings("unchecked") Map<String, Object>[] runHolder = new Map[1];
            ActorScope.run(snapshot.actor(), snapshot.capabilities(), () -> runHolder[0] = runs.submitProductionAs(actor, false, pipelineId, publishedRevisionId, requestKey,
                    number(row.get("timeoutSeconds")) == null ? 300 : number(row.get("timeoutSeconds")).intValue(),
                    number(row.get("targetDatasourceId")),
                    number(row.get("maxRows")) == null ? null : number(row.get("maxRows")).intValue(),
                    "schedule:" + scheduleId));
            Long runId = number(runHolder[0].get("id"));
            SubscriptionSchedule schedule = readSchedule(row.get("scheduleJson"));
            Map<String, Object> done = baseComplete(row, runId, "SUBMITTED", "已提交生产运行", 0, true);
            done.put("nextRunUtc", SubscriptionSchedule.utc(schedule.nextAfter(Instant.now())));
            schedules.complete(done);
        } catch (RuntimeException failure) {
            completeFailure(row, null, failure.getMessage() == null ? "调度触发失败" : failure.getMessage());
        }
    }

    private void completeFailure(Map<String, Object> row, Long runId, String message) {
        int failures = number(row.get("failureCount")) == null ? 1 : number(row.get("failureCount")).intValue() + 1;
        boolean keep = failures < MAX_FAILURES;
        String status = keep ? "FAILED" : "DISABLED";
        String text = bounded(message, 480);
        if (!keep) text = bounded(text + "；连续失败已达上限，调度已停用", 480);
        Map<String, Object> done = baseComplete(row, runId, status, text, failures, keep);
        SubscriptionSchedule schedule = readSchedule(row.get("scheduleJson"));
        Instant retry = Instant.now().plusSeconds(failures == 1 ? 60 : 300);
        done.put("nextRunUtc", SubscriptionSchedule.utc(keep ? retry : schedule.nextAfter(Instant.now().plusSeconds(3600))));
        schedules.complete(done);
    }

    private Map<String, Object> baseComplete(Map<String, Object> row, Long runId, String status, String message, int failures, boolean enabled) {
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("id", row.get("id"));
        done.put("runId", runId);
        done.put("status", status);
        done.put("message", message);
        done.put("failureCount", failures);
        done.put("enabled", enabled ? 1 : 0);
        done.put("nextRunUtc", row.get("nextRunUtc"));
        return done;
    }

    private Map<String, Object> decorate(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        try {
            out.put("schedule", json.readValue(String.valueOf(row.get("scheduleJson")), SubscriptionSchedule.class));
        } catch (Exception ignored) {
            out.put("schedule", null);
        }
        out.put("enabled", enabled(row.get("enabled")));
        return out;
    }

    private SubscriptionSchedule readSchedule(Object raw) {
        try {
            return json.readValue(String.valueOf(raw), SubscriptionSchedule.class);
        } catch (Exception error) {
            return new SubscriptionSchedule("interval", 60, "", java.util.List.of(), "UTC");
        }
    }

    private BiActor readActor(Object raw) {
        try {
            return json.readValue(String.valueOf(raw), BiActor.class);
        } catch (Exception error) {
            throw new IllegalStateException("调度执行身份无法解析");
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("调度配置无法序列化");
        }
    }

    private static boolean enabled(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof Number number && number.intValue() == 1) || "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private static Long number(Object value) {
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String bounded(String value, int max) {
        if (value == null) return null;
        return value.substring(0, Math.min(max, value.length()));
    }

    public record ScheduleRequest(String name, Boolean enabled, Long revisionId, Long targetDatasourceId, Integer maxRows,
            Integer timeoutSeconds, Integer intervalMinutes, SubscriptionSchedule schedule) {
    }
}
