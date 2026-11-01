package com.wanfo.bi.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/**
 * Structural contract checks for Data API publish gate (S3-03).
 * Compares proposed response columns against an optional frozen baseline (successor migration).
 */
public final class DataApiContractGate {
    private DataApiContractGate() {}

    public record Column(String name, String valueKind) {}
    public record Schema(List<Column> columns, Long metricId, Long metricPublishedVersion) {}
    public record Issue(String severity, String code, String message) {}
    public record Report(boolean passed, Schema proposed, Schema baseline, List<Issue> issues, int sampleRows) {}

    public static Schema extract(Map<String, Object> queryResult, Long metricId, Long metricVersion) {
        Object rawColumns = queryResult == null ? null : queryResult.get("columns");
        if (!(rawColumns instanceof List<?> names) || names.isEmpty())
            throw new IllegalArgumentException("指标响应缺少 columns，无法建立契约");
        Object rawRows = queryResult.get("rows");
        List<?> rows = rawRows instanceof List<?> list ? list : List.of();
        Map<?, ?> sample = null;
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) { sample = map; break; }
        }
        List<Column> columns = new ArrayList<>();
        for (Object nameObj : names) {
            String name = String.valueOf(nameObj == null ? "" : nameObj).trim();
            if (name.isEmpty()) throw new IllegalArgumentException("指标响应含空列名");
            String kind = inferKind(sample == null ? null : sample.get(name));
            columns.add(new Column(name, kind));
        }
        long distinct = columns.stream().map(c -> c.name().toLowerCase(Locale.ROOT)).distinct().count();
        if (distinct != columns.size()) throw new IllegalArgumentException("指标响应列名重复");
        return new Schema(List.copyOf(columns), metricId, metricVersion);
    }

    public static Report compare(Schema proposed, Schema baseline, int sampleRows) {
        List<Issue> issues = new ArrayList<>();
        if (proposed == null || proposed.columns().isEmpty())
            issues.add(new Issue("BLOCKING", "EMPTY_SCHEMA", "契约列为空"));
        else {
            for (Column column : proposed.columns()) {
                if (column.name() == null || column.name().isBlank())
                    issues.add(new Issue("BLOCKING", "BAD_COLUMN", "存在无效列名"));
            }
        }
        if (baseline != null && proposed != null) {
            Map<String, String> proposedKinds = new LinkedHashMap<>();
            for (Column column : proposed.columns())
                proposedKinds.put(column.name().toLowerCase(Locale.ROOT), column.valueKind());
            for (Column baselineColumn : baseline.columns()) {
                String key = baselineColumn.name().toLowerCase(Locale.ROOT);
                String next = proposedKinds.get(key);
                if (next == null)
                    issues.add(new Issue("BLOCKING", "COLUMN_REMOVED",
                            "相对基线缺少列「" + baselineColumn.name() + "」"));
                else if (!compatibleKind(baselineColumn.valueKind(), next))
                    issues.add(new Issue("BLOCKING", "TYPE_CHANGED",
                            "列「" + baselineColumn.name() + "」类型由 " + baselineColumn.valueKind()
                                    + " 变为 " + next));
            }
            for (Column column : proposed.columns()) {
                boolean known = baseline.columns().stream()
                        .anyMatch(b -> b.name().equalsIgnoreCase(column.name()));
                if (!known)
                    issues.add(new Issue("COMPATIBLE", "COLUMN_ADDED", "新增列「" + column.name() + "」"));
            }
            if (baseline.metricId() != null && proposed.metricId() != null
                    && !Objects.equals(baseline.metricId(), proposed.metricId()))
                issues.add(new Issue("REVIEW", "METRIC_CHANGED",
                        "继任 API 绑定指标与基线不同（#" + baseline.metricId() + " → #" + proposed.metricId() + "）"));
        }
        boolean passed = issues.stream().noneMatch(i -> "BLOCKING".equals(i.severity()));
        return new Report(passed, proposed, baseline, List.copyOf(issues), sampleRows);
    }

    public static Map<String, Object> toMap(Schema schema, ObjectMapper json) {
        if (schema == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("columns", schema.columns().stream()
                .map(c -> Map.<String, Object>of("name", c.name(), "valueKind", c.valueKind())).toList());
        map.put("metricId", schema.metricId());
        map.put("metricPublishedVersion", schema.metricPublishedVersion());
        return map;
    }

    public static Schema fromJson(String raw, ObjectMapper json) {
        if (raw == null || raw.isBlank()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = json.readValue(raw, Map.class);
            Object cols = map.get("columns");
            if (!(cols instanceof List<?> list) || list.isEmpty()) return null;
            List<Column> columns = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Object nameRaw = m.get("name");
                Object kindRaw = m.get("valueKind");
                String name = String.valueOf(nameRaw == null ? "" : nameRaw).trim();
                String kind = String.valueOf(kindRaw == null ? "STRING" : kindRaw);
                if (!name.isEmpty()) columns.add(new Column(name, kind));
            }
            if (columns.isEmpty()) return null;
            Long metricId = asLong(map.get("metricId"));
            Long version = asLong(map.get("metricPublishedVersion"));
            return new Schema(List.copyOf(columns), metricId, version);
        } catch (Exception error) {
            throw new IllegalStateException("契约快照解析失败", error);
        }
    }

    public static Map<String, Object> reportMap(Report report, ObjectMapper json) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("passed", report.passed());
        map.put("sampleRows", report.sampleRows());
        map.put("proposed", toMap(report.proposed(), json));
        map.put("baseline", toMap(report.baseline(), json));
        map.put("issues", report.issues().stream()
                .map(i -> Map.<String, Object>of("severity", i.severity(), "code", i.code(), "message", i.message()))
                .toList());
        map.put("blockingCount", report.issues().stream().filter(i -> "BLOCKING".equals(i.severity())).count());
        return map;
    }

    static String inferKind(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof Number) return "NUMBER";
        return "STRING";
    }

    static boolean compatibleKind(String baseline, String proposed) {
        if (baseline == null || proposed == null) return false;
        if (baseline.equals(proposed)) return true;
        if ("NULL".equals(baseline) || "NULL".equals(proposed)) return true;
        return false;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (Exception ignored) { return null; }
    }
}
