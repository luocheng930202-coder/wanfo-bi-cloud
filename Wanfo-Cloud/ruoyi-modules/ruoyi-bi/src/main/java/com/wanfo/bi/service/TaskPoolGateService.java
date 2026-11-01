package com.wanfo.bi.service;

import com.wanfo.bi.mapper.TaskPoolLaneMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S3-01 deepen: lane pause + soft concurrency + global fair soft-cap by priority/weight. */
@Service
public class TaskPoolGateService {
    public record LaneUpdateRequest(Boolean paused, Integer maxConcurrent, Integer priority, Integer weight) { }

    private final TaskPoolLaneMapper lanes;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final CatalogAuditService audit;
    private final int globalMaxConcurrent;
    /** Approximate cross-lane active count for fair soft-cap (updated by callers optionally). */
    private final AtomicLong observedGlobalRunning = new AtomicLong(0);
    private LongSupplier globalRunningSupplier = observedGlobalRunning::get;

    public TaskPoolGateService(TaskPoolLaneMapper lanes, CapabilityProvider capabilities,
            CurrentActorProvider actors, CatalogAuditService audit,
            @Value("${bi.task-pool.global-max-concurrent:0}") int globalMaxConcurrent) {
        this.lanes = lanes;
        this.capabilities = capabilities;
        this.actors = actors;
        this.audit = audit;
        this.globalMaxConcurrent = Math.max(0, Math.min(globalMaxConcurrent, 10_000));
    }

    /** Test/hook: override how global running is measured (e.g. sum of domain counters). */
    public void setGlobalRunningSupplier(LongSupplier supplier) {
        this.globalRunningSupplier = supplier == null ? observedGlobalRunning::get : supplier;
    }

    public void noteGlobalRunning(long count) {
        observedGlobalRunning.set(Math.max(0, count));
    }

    public List<Map<String, Object>> listLanes() {
        capabilities.require("bi:task:list");
        return lanes.list();
    }

    @Transactional
    public Map<String, Object> updateLane(String lane, LaneUpdateRequest request) {
        capabilities.require("bi:resource:admin");
        if (lane == null || lane.isBlank()) throw new IllegalArgumentException("分池标识无效");
        String key = lane.trim().toUpperCase(Locale.ROOT);
        Map<String, Object> existing = lanes.find(key);
        if (existing == null) throw new IllegalArgumentException("未知任务分池：" + key);
        boolean paused = request != null && Boolean.TRUE.equals(request.paused());
        int max = request == null || request.maxConcurrent() == null
                ? ((Number) existing.getOrDefault("maxConcurrent", 0)).intValue()
                : request.maxConcurrent();
        if (max < 0 || max > 1000) throw new IllegalArgumentException("并发上限须为 0～1000（0=不限）");
        int priority = request == null || request.priority() == null
                ? ((Number) existing.getOrDefault("priority", 100)).intValue()
                : request.priority();
        if (priority < 1 || priority > 1000) throw new IllegalArgumentException("优先级须为 1～1000（越小越高）");
        int weight = request == null || request.weight() == null
                ? ((Number) existing.getOrDefault("weight", 1)).intValue()
                : request.weight();
        if (weight < 1 || weight > 100) throw new IllegalArgumentException("权重须为 1～100");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("lane", key);
        row.put("paused", paused ? 1 : 0);
        row.put("maxConcurrent", max);
        row.put("priority", priority);
        row.put("weight", weight);
        row.put("user", actors.currentActor().username());
        if (lanes.update(row) != 1) throw new IllegalStateException("分池更新失败");
        audit.record("TASK_POOL_LANE_UPDATE", "TASK_POOL_LANE", null, null,
                Map.of("lane", key, "paused", paused, "maxConcurrent", max, "priority", priority, "weight", weight));
        return lanes.find(key);
    }

    public void assertLaneRunnable(String lane, long currentRunning) {
        if (lane == null || lane.isBlank()) return;
        Map<String, Object> row = lanes.find(lane.trim().toUpperCase(Locale.ROOT));
        if (row == null) return;
        Object paused = row.get("paused");
        boolean isPaused = paused instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(paused);
        if (isPaused) throw new IllegalStateException("任务分池已暂停：" + lane + "；请在任务中心恢复后再提交");
        int max = row.get("maxConcurrent") instanceof Number n ? n.intValue() : 0;
        if (max > 0 && currentRunning >= max)
            throw new IllegalStateException("任务分池并发已满：" + lane + "（上限 " + max + "）");
        assertGlobalFairShare(lane, row, currentRunning);
    }

    /**
     * When {@code bi.task-pool.global-max-concurrent > 0}, reject low-priority lanes that would
     * exceed their weighted fair share while higher-priority lanes are still under pressure.
     */
    private void assertGlobalFairShare(String lane, Map<String, Object> row, long currentRunning) {
        if (globalMaxConcurrent <= 0) return;
        long global = globalRunningSupplier.getAsLong();
        if (global < 0) global = 0;
        if (global >= globalMaxConcurrent)
            throw new IllegalStateException("任务池全局并发已满（上限 " + globalMaxConcurrent + "）");
        int weight = row.get("weight") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        int priority = row.get("priority") instanceof Number n ? n.intValue() : 100;
        int totalWeight = lanes.list().stream()
                .mapToInt(l -> l.get("weight") instanceof Number n ? Math.max(1, n.intValue()) : 1)
                .sum();
        if (totalWeight < 1) totalWeight = 1;
        long fairShare = Math.max(1, (globalMaxConcurrent * (long) weight) / totalWeight);
        if (currentRunning >= fairShare) {
            boolean higherHungry = lanes.list().stream().anyMatch(l -> {
                int p = l.get("priority") instanceof Number n ? n.intValue() : 100;
                if (p >= priority) return false;
                Object paused = l.get("paused");
                boolean isPaused = paused instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(paused);
                return !isPaused;
            });
            if (higherHungry)
                throw new IllegalStateException("任务分池公平份额已满：" + lane
                        + "（份额 " + fairShare + "，优先保障更高优先级分池）");
        }
    }
}
