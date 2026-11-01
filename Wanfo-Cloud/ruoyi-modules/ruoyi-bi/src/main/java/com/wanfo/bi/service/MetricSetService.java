package com.wanfo.bi.service;

import com.wanfo.bi.mapper.BiMetricMapper;
import com.wanfo.bi.mapper.MetricSetMapper;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricSetService {
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{1,95}");
    private final MetricSetMapper mapper;
    private final BiMetricMapper metrics;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final CatalogAuditService audit;

    public MetricSetService(MetricSetMapper mapper, BiMetricMapper metrics, BiResourceAuthorizer resources,
            CapabilityProvider capabilities, CurrentActorProvider actors, CatalogAuditService audit) {
        this.mapper = mapper;
        this.metrics = metrics;
        this.resources = resources;
        this.capabilities = capabilities;
        this.actors = actors;
        this.audit = audit;
    }

    public List<Map<String, Object>> list() {
        capabilities.require("bi:metric:list");
        return mapper.list().stream().filter(row -> resources.allowed("metric_set", number(row.get("id")), "view"))
                .map(this::withMembers).toList();
    }

    public Map<String, Object> get(Long id) {
        capabilities.require("bi:metric:list");
        resources.require("metric_set", id, "view");
        Map<String, Object> row = mapper.find(id);
        if (row == null) throw new IllegalArgumentException("指标集不存在");
        return withMembers(row);
    }

    @Transactional
    public Map<String, Object> create(UpsertRequest request) {
        capabilities.require("bi:metric:add");
        Map<String, Object> row = validate(request, true);
        row.put("createBy", actors.currentActor().username());
        if (mapper.insert(row) != 1 || !(row.get("id") instanceof Number id))
            throw new IllegalStateException("指标集创建失败");
        long setId = id.longValue();
        resources.register("metric_set", setId);
        replaceMembers(setId, request.metricIds());
        audit.record("METRIC_SET_CREATE", "METRIC_SET", setId, null, Map.of("setCode", row.get("setCode")));
        return get(setId);
    }

    @Transactional
    public Map<String, Object> update(Long id, UpsertRequest request) {
        capabilities.require("bi:metric:edit");
        resources.require("metric_set", id, "edit");
        if (mapper.find(id) == null) throw new IllegalArgumentException("指标集不存在");
        Map<String, Object> row = validate(request, false);
        row.put("id", id);
        row.put("updateBy", actors.currentActor().username());
        if (mapper.update(row) != 1) throw new IllegalStateException("指标集更新失败");
        replaceMembers(id, request.metricIds());
        audit.record("METRIC_SET_UPDATE", "METRIC_SET", id, null, Map.of("status", row.get("status")));
        return get(id);
    }

    @Transactional
    public Map<String, Object> delete(Long id) {
        capabilities.require("bi:metric:remove");
        resources.require("metric_set", id, "remove");
        if (mapper.delete(id) != 1) throw new IllegalArgumentException("指标集不存在");
        audit.record("METRIC_SET_DELETE", "METRIC_SET", id, null, Map.of());
        return Map.of("deleted", true);
    }

    private Map<String, Object> withMembers(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("members", mapper.members(number(row.get("id"))));
        return out;
    }

    private void replaceMembers(long setId, List<Long> metricIds) {
        mapper.clearMembers(setId);
        if (metricIds == null || metricIds.isEmpty()) return;
        if (metricIds.size() > 100) throw new IllegalArgumentException("单个指标集最多 100 个指标");
        int sort = 0;
        for (Long metricId : metricIds) {
            if (metricId == null || metricId < 1 || metrics.get(metricId) == null)
                throw new IllegalArgumentException("指标成员无效: " + metricId);
            resources.require("metric", metricId, "view");
            mapper.addMember(setId, metricId, sort++);
        }
    }

    private Map<String, Object> validate(UpsertRequest request, boolean creating) {
        if (request == null) throw new IllegalArgumentException("请求不能为空");
        String code = request.setCode() == null ? "" : request.setCode().trim();
        if (!CODE.matcher(code).matches()) throw new IllegalArgumentException("指标集编码无效");
        if (creating && mapper.findIdByCode(code) != null) throw new IllegalArgumentException("指标集编码已存在");
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank() || name.length() > 160) throw new IllegalArgumentException("名称须为 1～160 字");
        String status = request.status() == null ? "ACTIVE" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "ARCHIVED").contains(status)) throw new IllegalArgumentException("状态无效");
        Map<String, Object> row = new HashMap<>();
        row.put("setCode", code);
        row.put("name", name);
        row.put("theme", blankToNull(request.theme(), 64));
        row.put("description", blankToNull(request.description(), 1000));
        row.put("status", status);
        return row;
    }

    private static String blankToNull(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new IllegalArgumentException("字段超长");
        return trimmed;
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    public record UpsertRequest(String setCode, String name, String theme, String description, String status,
            List<Long> metricIds) { }
}
