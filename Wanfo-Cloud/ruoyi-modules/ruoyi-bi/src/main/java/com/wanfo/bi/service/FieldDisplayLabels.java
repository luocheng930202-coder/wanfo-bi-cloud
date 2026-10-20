package com.wanfo.bi.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.service.ChartDefinitionService.Definition;

/** Map query output column keys to dataset show names for UI. */
public final class FieldDisplayLabels {
    private FieldDisplayLabels() {}

    public static String catalogShowName(Map<String, Object> column) {
        Object comment = column.get("comment");
        if (comment != null) {
            String text = String.valueOf(comment).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return String.valueOf(column.get("name"));
    }

    public static Map<String, String> showNamesByOrigin(List<BiDatasetFieldDO> fields) {
        Map<String, String> map = new LinkedHashMap<>();
        for (BiDatasetFieldDO f : fields) {
            String show = f.getShowName();
            if (show == null || show.isBlank()) {
                show = f.getOriginName();
            }
            map.put(f.getOriginName(), show.trim());
        }
        return map;
    }

    public static Map<String, String> forChartResult(Definition chart, List<BiDatasetFieldDO> fields, List<String> columns) {
        Map<String, String> byOrigin = showNamesByOrigin(fields);
        Map<String, String> labels = new LinkedHashMap<>();
        if (chart == null || chart.query() == null) {
            for (String col : columns) {
                labels.put(col, byOrigin.getOrDefault(col, col));
            }
            return labels;
        }
        QueryModel q = chart.query();
        if (q.getDimensions() != null) {
            for (QueryModel.Dimension dim : q.getDimensions()) {
                String out = outputKey(dim.getAlias(), dim.getField());
                labels.put(out, byOrigin.getOrDefault(dim.getField(), dim.getField()));
            }
        }
        if (q.getMetrics() != null) {
            for (QueryModel.Metric m : q.getMetrics()) {
                String out = metricColumn(m);
                String base = byOrigin.getOrDefault(m.getField(), m.getField());
                String agg = aggLabel(m.getAgg());
                labels.put(out, agg.isEmpty() ? base : base + " · " + agg);
            }
        }
        for (String col : columns) {
            labels.putIfAbsent(col, byOrigin.getOrDefault(col, col));
        }
        return labels;
    }

    private static String outputKey(String alias, String field) {
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        return field;
    }

    static String metricColumn(QueryModel.Metric m) {
        if (m.getAlias() != null && !m.getAlias().isBlank()) {
            return m.getAlias();
        }
        String agg = m.getAgg();
        if (agg == null || agg.equalsIgnoreCase("NONE")) {
            return m.getField();
        }
        return agg.toLowerCase(Locale.ROOT) + "_" + m.getField();
    }

    private static String aggLabel(String agg) {
        if (agg == null) {
            return "";
        }
        return switch (agg.toUpperCase(Locale.ROOT)) {
            case "SUM" -> "求和";
            case "AVG" -> "平均";
            case "COUNT" -> "计数";
            case "COUNT_DISTINCT" -> "去重计数";
            case "MAX" -> "最大";
            case "MIN" -> "最小";
            default -> agg;
        };
    }
}
