package com.wanfo.bi.query;

import java.util.*;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.service.MetricDefinitionService.ComponentRef;
import com.wanfo.bi.service.MetricDefinitionService.Definition;
import com.wanfo.bi.service.MetricDefinitionService.MeasureSpec;
import com.wanfo.bi.service.MetricDefinitionService.MetricQueryRequest;

/**
 * Compiles a schemaVersion=2 metric definition plus runtime request into a QueryModel.
 * Derived metrics compile both atomic measures; the service evaluates the expression per row.
 */
public final class MetricQueryCompiler {
    private MetricQueryCompiler() {}

    public static QueryModel compileAtomic(Definition definition, MetricQueryRequest request, MeasureSpec measure, String measureAlias) {
        requireAtomicShape(definition);
        QueryModel q = base(definition.datasetId(), request, definition.allowedDimensions(), definition.fixedFilters());
        String alias = measureAlias == null || measureAlias.isBlank() ? measure.field() : measureAlias;
        q.addMetric(measure.field(), measure.agg(), alias);
        if (q.getDimensions().isEmpty()) q.setLimit(1);
        return q;
    }

    /** Builds a query with one metric column per derived component (alias = component alias). */
    public static QueryModel compileDerivedShell(Definition definition, MetricQueryRequest request,
            Map<String, MeasureSpec> aliasToMeasure) {
        if (!"derived".equals(definition.kind())) throw new IllegalArgumentException("须为派生指标");
        QueryModel q = base(definition.datasetId(), request, definition.allowedDimensions(), definition.fixedFilters());
        for (ComponentRef component : definition.components()) {
            MeasureSpec measure = aliasToMeasure.get(component.alias());
            if (measure == null) throw new IllegalArgumentException("派生组件度量缺失: " + component.alias());
            q.addMetric(measure.field(), measure.agg(), component.alias());
        }
        if (q.getDimensions().isEmpty()) q.setLimit(1);
        return q;
    }

    public static MetricQueryRequest normalize(MetricQueryRequest request) {
        if (request == null) {
            return new MetricQueryRequest(List.of(), List.of(), null, null);
        }
        List<QueryModel.Dimension> dims = request.dimensions() == null ? List.of() : List.copyOf(request.dimensions());
        List<QueryModel.Filter> filters = request.filters() == null ? List.of() : List.copyOf(request.filters());
        return new MetricQueryRequest(dims, filters, request.limit(), request.timeoutSeconds());
    }

    private static QueryModel base(Long datasetId, MetricQueryRequest request,
            List<String> allowedDimensions, List<QueryModel.Filter> fixedFilters) {
        MetricQueryRequest req = normalize(request);
        Set<String> allowed = new LinkedHashSet<>(allowedDimensions == null ? List.of() : allowedDimensions);
        QueryModel q = new QueryModel();
        q.setDatasetId(datasetId);
        q.setOrderBys(new ArrayList<>());
        List<QueryModel.Dimension> dims = new ArrayList<>();
        for (QueryModel.Dimension d : req.dimensions()) {
            if (d == null || d.getField() == null || d.getField().isBlank())
                throw new IllegalArgumentException("查询维度无效");
            if (!allowed.contains(d.getField()))
                throw new IllegalArgumentException("维度不在指标允许范围内: " + d.getField());
            dims.add(d);
        }
        q.setDimensions(dims);
        List<QueryModel.Filter> filters = new ArrayList<>();
        if (fixedFilters != null) filters.addAll(fixedFilters);
        for (QueryModel.Filter f : req.filters()) {
            if (f == null || f.getField() == null || f.getField().isBlank())
                throw new IllegalArgumentException("查询过滤无效");
            filters.add(f);
        }
        q.setFilters(filters);
        if (req.limit() != null) {
            if (req.limit() < 1 || req.limit() > 1000) throw new IllegalArgumentException("limit 须在 1～1000");
            q.setLimit(req.limit());
        } else if (!dims.isEmpty()) {
            q.setLimit(1000);
        }
        if (req.timeoutSeconds() != null) q.setTimeoutSeconds(req.timeoutSeconds());
        return q;
    }

    private static void requireAtomicShape(Definition definition) {
        if (!"atomic".equals(definition.kind())) throw new IllegalArgumentException("须为原子指标");
        if (definition.measure() == null) throw new IllegalArgumentException("原子指标须配置度量");
    }
}
