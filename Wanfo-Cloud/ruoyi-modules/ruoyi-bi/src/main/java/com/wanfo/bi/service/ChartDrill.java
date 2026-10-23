package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.service.ChartDefinitionService.Definition;

/** In-chart dimension drill: path filters + single display dimension per level. */
public final class ChartDrill {
    public record Segment(String field, Object value) {}

    private ChartDrill() {}

    public static boolean enabled(Definition definition) {
        if(GeoMapQuery.isMap(definition))return GeoMapQuery.enabled(definition);
        return definition != null && fields(definition).size() >= 2;
    }

    public static List<String> fields(Definition definition) {
        if(GeoMapQuery.isMap(definition))return GeoMapQuery.enabled(definition)?GeoMapQuery.fields(definition):List.of();
        if (definition == null || definition.advanced() == null) return List.of();
        List<String> drill = definition.advanced().getDrillFields();
        if (drill == null || drill.isEmpty()) return List.of();
        return List.copyOf(drill);
    }

    public static boolean supportsType(String type) {
        return type != null && !Set.of("table", "pivot", "map", "gauge").contains(type);
    }

    public static void validate(Definition d, Map<String, String> datasetFieldTypes) {
        if(GeoMapQuery.isMap(d)){GeoMapQuery.validate(d,datasetFieldTypes);return;}
        if (d == null || d.advanced() == null) return;
        List<String> drill = d.advanced().getDrillFields();
        if (drill == null || drill.isEmpty() || drill.size() < 2) {
            d.advanced().setDrillFields(null);
            if (drill == null || drill.isEmpty()) return;
            throw new IllegalArgumentException("下钻维度须配置 2～5 个字段");
        }
        if(d.query().getDimensions().size()>1)throw new IllegalArgumentException("使用系列维度时请先移除维度下钻");
        if (!supportsType(d.type())) throw new IllegalArgumentException("当前图表类型不支持下钻");
        if (drill.size() > 5) throw new IllegalArgumentException("下钻维度须配置 2～5 个字段");
        Set<String> seen = new HashSet<>();
        for (String f : drill) {
            if (f == null || f.isBlank() || f.length() > 120 || !seen.add(f))
                throw new IllegalArgumentException("下钻维度字段无效或重复");
            if (!datasetFieldTypes.containsKey(f)) throw new IllegalArgumentException("下钻字段不存在于数据集：" + f);
            DashboardInteraction.family(datasetFieldTypes.get(f));
        }
        if (d.query().getDimensions().isEmpty())
            throw new IllegalArgumentException("下钻图表须配置第一维度");
        if (!Objects.equals(drill.get(0), d.query().getDimensions().get(0).getField()))
            throw new IllegalArgumentException("下钻第一层字段须与图表维度一致");
    }

    public static List<Segment> parsePath(Map<String, Object> body, String panelKey, List<String> drillFields,
            Map<String, String> fieldTypes) {
        if (body == null || drillFields == null || drillFields.isEmpty()) return List.of();
        Object raw = body.get("drills");
        if (raw == null) return List.of();
        if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException("下钻参数格式无效");
        if (map.size() > 12) throw new IllegalArgumentException("下钻状态过多");
        if (map.keySet().stream().anyMatch(k -> !(k instanceof String))) throw new IllegalArgumentException("下钻组件标识无效");
        Object panel = map.get(panelKey);
        if (panel == null) return List.of();
        if (!(panel instanceof List<?> list)) throw new IllegalArgumentException("下钻路径格式无效");
        if (list.isEmpty()) return List.of();
        if (list.size() >= drillFields.size()) throw new IllegalArgumentException("已到达最细下钻层级");
        List<Segment> path = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> seg)) throw new IllegalArgumentException("下钻路径项无效");
            String field = String.valueOf(seg.get("field"));
            if (!Objects.equals(field, drillFields.get(i))) throw new IllegalArgumentException("下钻路径与配置层级不一致");
            if (!fieldTypes.containsKey(field)) throw new IllegalArgumentException("下钻字段无查询权限");
            Object value = seg.get("value");
            path.add(new Segment(field, DashboardInteraction.linkValue(DashboardInteraction.family(fieldTypes.get(field)), value)));
        }
        return path;
    }

    public static void apply(QueryModel q, List<String> drillFields, List<Segment> path) {
        if (drillFields == null || drillFields.isEmpty()) return;
        int level = path == null ? 0 : path.size();
        if (level >= drillFields.size()) throw new IllegalArgumentException("已到达最细下钻层级");
        if (path != null) {
            for (Segment s : path) q.addFilter(s.field(), "EQ", s.value());
        }
        String display = drillFields.get(level);
        q.setDimensions(new ArrayList<>(List.of(new QueryModel.Dimension(display, display))));
        if (q.getOrderBys() != null && !q.getOrderBys().isEmpty()) {
            var order = q.getOrderBys().get(0);
            q.setOrderBys(List.of(new QueryModel.OrderBy(display, order.getOrder() == null ? "asc" : order.getOrder())));
        }
    }

    public static Map<String, Object> presentationMeta(List<String> drillFields, List<Segment> path) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (drillFields == null || drillFields.isEmpty()) return meta;
        int level = path == null ? 0 : path.size();
        meta.put("drillFields", drillFields);
        meta.put("drillLevel", level);
        meta.put("canDrillDown", level < drillFields.size() - 1);
        List<Map<String, Object>> encoded = new ArrayList<>();
        if (path != null) {
            for (Segment s : path) encoded.add(Map.of("field", s.field(), "value", s.value()));
        }
        meta.put("drillPath", encoded);
        return meta;
    }

    public static Definition presentationDefinition(Definition source, List<String> drillFields, List<Segment> path) {
        if (drillFields == null || drillFields.isEmpty()) return source;
        int level = path == null ? 0 : path.size();
        String display = drillFields.get(Math.min(level, drillFields.size() - 1));
        var q = new QueryModel();
        q.setDatasetId(source.query().getDatasetId());
        q.setDimensions(List.of(new QueryModel.Dimension(display, display)));
        q.setMetrics(source.query().getMetrics());
        q.setOrderBys(source.query().getOrderBys());
        q.setLimit(source.query().getLimit());
        return new Definition(source.schemaVersion(), source.name(), source.type(), q, source.style(), source.advanced());
    }
}
