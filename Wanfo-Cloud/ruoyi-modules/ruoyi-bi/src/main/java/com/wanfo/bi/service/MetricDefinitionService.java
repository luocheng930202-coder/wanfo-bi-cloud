package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.BiMetricDO;
import com.wanfo.bi.domain.BiMetricHistoryDO;
import com.wanfo.bi.domain.BiMetricUsageDO;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.mapper.BiMetricMapper;
import com.wanfo.bi.mapper.GlossaryTermMapper;
import com.wanfo.bi.mapper.MetricPublishRequestMapper;
import com.wanfo.bi.query.MetricQueryCompiler;
import com.wanfo.bi.security.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Standard metrics: atomic / derived / composite. Definition is separate from runtime query. */
@Service
public class MetricDefinitionService {
    public static final String RESOURCE = "metric";
    private static final Set<String> KINDS = Set.of("atomic", "derived", "composite");
    private static final Set<String> AGGS = Set.of("SUM", "AVG", "COUNT", "COUNT_DISTINCT", "MIN", "MAX");

    public record MeasureSpec(String field, String agg) { }
    public record ComponentRef(String code, String alias) { }
    public record MetricQueryRequest(List<QueryModel.Dimension> dimensions, List<QueryModel.Filter> filters,
            Integer limit, Integer timeoutSeconds) { }
    public record Definition(Integer schemaVersion, String name, String code, String kind, Long datasetId,
            MeasureSpec measure, List<QueryModel.Filter> fixedFilters, List<String> allowedDimensions,
            List<ComponentRef> components, String expression,
            List<String> componentMetricCodes, String compositeExpression,
            String businessDefinition, String grain, String unit, String timeZone, String owner) { }
    public record SaveRequest(Integer version, Definition definition) { }
    public record UsageRequest(String refType, String refId, String remark) { }
    public record PreviewRequest(Definition definition, MetricQueryRequest query) { }

    private final BiMetricMapper mapper;
    private final BiDatasetTableMapper datasets;
    private final ChartQueryService queries;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider caps;
    private final CurrentActorProvider actors;
    private final ObjectMapper json;
    private final MetricPublishRequestMapper publishRequests;
    private final GlossaryTermMapper glossary;
    private final CatalogAuditService audit;

    public MetricDefinitionService(BiMetricMapper mapper, BiDatasetTableMapper datasets, ChartQueryService queries,
            BiResourceAuthorizer resources, CapabilityProvider caps, CurrentActorProvider actors, ObjectMapper json,
            MetricPublishRequestMapper publishRequests, GlossaryTermMapper glossary, CatalogAuditService audit) {
        this.mapper = mapper; this.datasets = datasets; this.queries = queries; this.resources = resources;
        this.caps = caps; this.actors = actors; this.json = json;
        this.publishRequests = publishRequests; this.glossary = glossary; this.audit = audit;
    }

    public List<Map<String, Object>> list() {
        caps.require("bi:metric:list");
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : mapper.list()) {
            if (!resources.allowed(RESOURCE, row.getId(), "view") || !dataAllowed(row.getDatasetId())) continue;
            result.add(summary(row));
        }
        return result;
    }

    public List<Map<String, Object>> conflicts() {
        caps.require("bi:metric:list");
        List<BiMetricDO> visible = mapper.list().stream()
                .filter(row -> resources.allowed(RESOURCE, row.getId(), "view") && dataAllowed(row.getDatasetId()))
                .toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) for (int j = i + 1; j < visible.size(); j++) {
            BiMetricDO left = visible.get(i), right = visible.get(j);
            if (!Objects.equals(left.getDatasetId(), right.getDatasetId())) continue;
            if (!"atomic".equals(left.getKind()) || !"atomic".equals(right.getKind())) continue;
            Definition a = decode(left), b = decode(right);
            if (a.measure() == null || b.measure() == null) continue;
            if (!Objects.equals(a.measure().field(), b.measure().field())
                    || !Objects.equals(norm(a.measure().agg()), norm(b.measure().agg()))) continue;
            List<String> differences = new ArrayList<>();
            if (!Objects.equals(norm(joinDims(a.allowedDimensions())), norm(joinDims(b.allowedDimensions()))))
                differences.add("允许维度");
            if (!Objects.equals(norm(a.grain()), norm(b.grain()))) differences.add("统计粒度");
            if (!Objects.equals(norm(a.unit()), norm(b.unit()))) differences.add("单位");
            if (!Objects.equals(norm(a.timeZone()), norm(b.timeZone()))) differences.add("时区");
            if (!Objects.equals(norm(a.businessDefinition()), norm(b.businessDefinition()))) differences.add("业务定义");
            if (!differences.isEmpty()) result.add(Map.of(
                    "leftId", left.getId(), "leftCode", left.getCode(), "leftName", left.getName(),
                    "rightId", right.getId(), "rightCode", right.getCode(), "rightName", right.getName(),
                    "datasetId", left.getDatasetId(), "field", a.measure().field(),
                    "aggregation", a.measure().agg() == null ? "" : a.measure().agg(),
                    "differences", differences));
        }
        return result;
    }

    public Map<String, Object> detail(Long id) {
        caps.require("bi:metric:list");
        var row = require(id, "view");
        Map<String, Object> result = summary(row);
        result.put("definition", decode(row));
        result.put("published", row.getPublishedJson() == null ? null : decodePublished(row));
        result.put("publishRequests", publishRequests.listByMetric(id));
        return result;
    }

    public Map<String, Object> consumerContext(Long id) {
        caps.require("bi:metric:list");
        var row = require(id, "view");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.getId());
        out.put("code", row.getCode());
        out.put("name", row.getName());
        out.put("kind", row.getKind());
        out.put("publishStatus", row.getPublishStatus());
        out.put("publishedVersion", row.getPublishedVersion() == null ? 0 : row.getPublishedVersion());
        out.put("pinNote", "published".equals(row.getPublishStatus())
                ? "消费侧按 publishedVersion=" + row.getPublishedVersion() + " 冻结口径取数，草稿修改不影响在线结果"
                : "指标尚未发布，消费侧暂无冻结口径");
        Definition source = "published".equals(row.getPublishStatus()) && row.getPublishedJson() != null
                ? decodePublished(row) : decode(row);
        out.put("datasetId", source.datasetId());
        out.put("businessDefinition", source.businessDefinition());
        out.put("measure", source.measure());
        out.put("components", source.components() == null ? List.of() : source.components());
        out.put("expression", source.expression());
        out.put("compositeExpression", source.compositeExpression());
        out.put("fixedFilterSummary", source.fixedFilters() == null ? List.of() : source.fixedFilters().stream()
                .map(filter -> Map.of("field", filter.getField(), "op", filter.getOp()))
                .toList());
        out.put("businessDimensions", joinDims(source.allowedDimensions()));
        out.put("allowedDimensions", source.allowedDimensions() == null ? List.of() : source.allowedDimensions());
        out.put("grain", source.grain());
        out.put("unit", source.unit());
        out.put("timeZone", source.timeZone());
        out.put("owner", source.owner());
        out.put("glossaryTerms", glossary.listByMetric(id));
        return out;
    }

    @Transactional
    public Map<String, Object> create(SaveRequest request) {
        caps.require("bi:metric:add");
        if (request == null) throw new IllegalArgumentException("指标定义不能为空");
        var row = row(request.definition(), true);
        row.setCreateBy(actor());
        if (mapper.findByCode(row.getCode()) != null) throw new IllegalArgumentException("指标编码已存在");
        mapper.insert(row);
        resources.register(RESOURCE, row.getId());
        return Map.of("id", row.getId(), "version", 0);
    }

    @Transactional
    public Map<String, Object> update(Long id, SaveRequest request) {
        caps.require("bi:metric:edit");
        var current = require(id, "edit");
        if ("published".equals(current.getPublishStatus())) throw new IllegalArgumentException("已发布指标须先取消发布后才能修改");
        if (inUse(current)) throw new IllegalArgumentException("指标已被引用，不能修改");
        if (request == null || request.version() == null || request.version() < 0)
            throw new IllegalArgumentException("缺少指标版本，请刷新后重试");
        var incoming = request.definition();
        if (incoming == null || incoming.code() == null || !incoming.code().equals(current.getCode()))
            throw new IllegalArgumentException("指标编码创建后不可修改");
        var row = row(incoming, false);
        row.setId(id);
        row.setVersion(request.version());
        if (mapper.update(row) != 1) throw new BiAccessException(409, "指标已被其他人修改，请重新打开后保存");
        return Map.of("id", id, "version", request.version() + 1);
    }

    public Map<String, Object> preview(Definition definition) throws Exception {
        return preview(definition, null);
    }

    public Map<String, Object> preview(Definition definition, MetricQueryRequest query) throws Exception {
        if (definition == null || definition.kind() == null) throw new IllegalArgumentException("请选择指标类型");
        validateKind(definition);
        return executeDefinition(definition, query);
    }

    public Map<String, Object> preview(PreviewRequest request) throws Exception {
        if (request == null) throw new IllegalArgumentException("预览请求不能为空");
        return preview(request.definition(), request.query());
    }

    public Map<String, Object> data(Long id) throws Exception {
        return data(id, null);
    }

    public Map<String, Object> data(Long id, MetricQueryRequest query) throws Exception {
        caps.require("bi:metric:query");
        var row = require(id, "query");
        var source = "published".equals(row.getPublishStatus()) ? decodePublished(row) : decode(row);
        return executeDefinition(source, query);
    }

    public Map<String, Object> dataPage(Long id, int page, int pageSize, int serviceMaxPageSize, int timeoutSeconds)
            throws Exception {
        return dataPage(id, page, pageSize, serviceMaxPageSize, timeoutSeconds, null);
    }

    public Map<String, Object> dataPage(Long id, int page, int pageSize, int serviceMaxPageSize, int timeoutSeconds,
            MetricQueryRequest query) throws Exception {
        caps.require("bi:metric:query");
        if (page < 1 || page > 100_000) throw new IllegalArgumentException("page 须在 1～100000 之间");
        if (pageSize < 1 || pageSize > serviceMaxPageSize || pageSize > 1000)
            throw new IllegalArgumentException("pageSize 超出服务限制");
        BiMetricDO row = require(id, "query");
        if (!"published".equals(row.getPublishStatus()) || row.getPublishedJson() == null)
            throw new IllegalArgumentException("指标未发布，不能通过受控数据 API 查询");
        Definition published = decodePublished(row);
        if ("composite".equals(published.kind())) {
            if (page != 1) throw new IllegalArgumentException("复合指标仅支持单页标量结果");
            if (hasDims(query)) throw new IllegalArgumentException("复合指标不支持运行时维度");
            return evaluateComposite(published);
        }
        MetricQueryRequest req = MetricQueryCompiler.normalize(query);
        long offset = (long) (page - 1) * pageSize;
        if (offset > 1_000_000) throw new IllegalArgumentException("翻页偏移超过服务限制");
        MetricQueryRequest paged = new MetricQueryRequest(req.dimensions(), req.filters(), pageSize,
                Math.max(1, Math.min(timeoutSeconds, 120)));
        QueryModel compiled = compileForExecution(published, paged);
        compiled.setOffset((int) offset);
        compiled.setLimit(pageSize);
        compiled.setTimeoutSeconds(Math.max(1, Math.min(timeoutSeconds, 120)));
        if ("derived".equals(published.kind())) return executeDerived(published, compiled);
        return queries.query(compiled);
    }

    public Map<String, Object> dataByCode(String code) throws Exception {
        return dataByCode(code, null);
    }

    public Map<String, Object> dataByCode(String code, MetricQueryRequest query) throws Exception {
        caps.require("bi:metric:query");
        var row = mapper.findByCode(code == null ? "" : code.trim());
        if (row == null) throw new IllegalArgumentException("指标不存在");
        resources.require(RESOURCE, row.getId(), "view");
        resources.require(RESOURCE, row.getId(), "query");
        caps.require("bi:dataset:query");
        resources.require("dataset", row.getDatasetId(), "view");
        resources.require("dataset", row.getDatasetId(), "query");
        if (!"published".equals(row.getPublishStatus()) || row.getPublishedJson() == null)
            throw new IllegalArgumentException("指标未发布，不能按编码取数");
        Map<String, Object> result = executeDefinition(decodePublished(row), query);
        result.remove("executedSql");
        result.remove("params");
        return result;
    }

    private Map<String, Object> executeDefinition(Definition definition, MetricQueryRequest query) throws Exception {
        if ("composite".equals(definition.kind())) {
            if (hasDims(query)) throw new IllegalArgumentException("复合指标不支持运行时维度");
            return evaluateComposite(definition);
        }
        QueryModel compiled = compileForExecution(definition, query);
        if ("derived".equals(definition.kind())) return executeDerived(definition, compiled);
        return queries.query(compiled);
    }

    private QueryModel compileForExecution(Definition definition, MetricQueryRequest query) {
        if ("atomic".equals(definition.kind())) {
            QueryModel q = MetricQueryCompiler.compileAtomic(definition, query, definition.measure(),
                    definition.code() == null ? "value" : definition.code());
            queries.compileOnly(q);
            return q;
        }
        if ("derived".equals(definition.kind())) {
            Map<String, MeasureSpec> measures = resolveDerivedMeasures(definition);
            List<QueryModel.Filter> mergedFixed = new ArrayList<>(
                    definition.fixedFilters() == null ? List.of() : definition.fixedFilters());
            for (ComponentRef component : definition.components()) {
                Definition atomic = decodePublished(requirePublishedAtomic(component.code()));
                if (atomic.fixedFilters() != null) mergedFixed.addAll(atomic.fixedFilters());
            }
            Definition withFilters = new Definition(definition.schemaVersion(), definition.name(), definition.code(),
                    definition.kind(), definition.datasetId(), definition.measure(), List.copyOf(mergedFixed),
                    definition.allowedDimensions(), definition.components(), definition.expression(),
                    definition.componentMetricCodes(), definition.compositeExpression(),
                    definition.businessDefinition(), definition.grain(), definition.unit(), definition.timeZone(),
                    definition.owner());
            QueryModel q = MetricQueryCompiler.compileDerivedShell(withFilters, query, measures);
            queries.compileOnly(q);
            return q;
        }
        throw new IllegalArgumentException("不支持的指标类型: " + definition.kind());
    }

    private Map<String, MeasureSpec> resolveDerivedMeasures(Definition definition) {
        Map<String, MeasureSpec> out = new LinkedHashMap<>();
        for (ComponentRef component : definition.components()) {
            BiMetricDO row = requirePublishedAtomic(component.code());
            Definition atomic = decodePublished(row);
            out.put(component.alias(), atomic.measure());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeDerived(Definition definition, QueryModel compiled) throws Exception {
        Map<String, Object> raw = queries.query(compiled);
        String expression = definition.expression().trim();
        List<String> aliases = definition.components().stream().map(ComponentRef::alias).toList();
        List<String> ordered = new ArrayList<>(aliases);
        ordered.sort((a, b) -> Integer.compare(b.length(), a.length()));
        List<Map<String, Object>> rows = new ArrayList<>();
        Object rowsObj = raw.get("rows");
        if (rowsObj instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) continue;
                Map<String, Object> src = (Map<String, Object>) row;
                Map<String, Double> values = new LinkedHashMap<>();
                for (String alias : aliases) values.put(alias, numberFrom(src.get(alias)));
                String rewritten = expression;
                for (String alias : ordered) rewritten = rewritten.replace(alias, "(" + values.get(alias) + ")");
                double value = evalArithmetic(rewritten);
                Map<String, Object> out = new LinkedHashMap<>();
                for (QueryModel.Dimension d : compiled.getDimensions()) {
                    String key = d.getAlias() == null || d.getAlias().isBlank() ? d.getField() : d.getAlias();
                    out.put(key, src.get(key));
                }
                out.put("value", value);
                rows.add(out);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> columns = new ArrayList<>();
        for (QueryModel.Dimension d : compiled.getDimensions()) {
            columns.add(d.getAlias() == null || d.getAlias().isBlank() ? d.getField() : d.getAlias());
        }
        columns.add("value");
        result.put("columns", columns);
        result.put("columnLabels", Map.of("value", definition.name() == null ? "value" : definition.name()));
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("derived", Map.of("expression", expression));
        if (raw.containsKey("costMs")) result.put("costMs", raw.get("costMs"));
        return result;
    }

    private Map<String, Object> evaluateComposite(Definition definition) throws Exception {
        List<String> components = definition.componentMetricCodes();
        Map<String, Double> values = new LinkedHashMap<>();
        for (String code : components) {
            BiMetricDO component = mapper.findByCode(code);
            if (component == null) throw new IllegalArgumentException("复合组件不存在: " + code);
            resources.require(RESOURCE, component.getId(), "query");
            if (!"published".equals(component.getPublishStatus()))
                throw new IllegalArgumentException("复合组件须已发布: " + code);
            String kind = component.getKind();
            if (!"atomic".equals(kind) && !"derived".equals(kind))
                throw new IllegalArgumentException("复合组件须为已发布原子或派生指标: " + code);
            Map<String, Object> raw = executeDefinition(decodePublished(component), null);
            values.put(code, scalarValue(raw));
        }
        String expression = definition.compositeExpression().trim();
        List<String> ordered = new ArrayList<>(components);
        ordered.sort((a, b) -> Integer.compare(b.length(), a.length()));
        String rewritten = expression;
        for (String code : ordered) rewritten = rewritten.replace(code, "(" + values.get(code) + ")");
        double value = evalArithmetic(rewritten);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", List.of("value"));
        result.put("columnLabels", Map.of("value", definition.name() == null ? "value" : definition.name()));
        result.put("rows", List.of(Map.of("value", value)));
        result.put("rowCount", 1);
        result.put("composite", Map.of("expression", expression, "components", values));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static double scalarValue(Map<String, Object> queryResult) {
        Object rowsObj = queryResult.get("rows");
        if (!(rowsObj instanceof List<?> rows) || rows.isEmpty() || !(rows.get(0) instanceof Map<?, ?> row))
            throw new IllegalArgumentException("组件未返回标量行");
        Map<String, Object> map = (Map<String, Object>) row;
        if (map.containsKey("value")) return numberFrom(map.get("value"));
        for (Object value : map.values()) {
            try { return numberFrom(value); }
            catch (IllegalArgumentException ignored) { }
        }
        throw new IllegalArgumentException("组件结果无法解析为数值");
    }

    private static double numberFrom(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value != null) {
            try { return Double.parseDouble(String.valueOf(value)); }
            catch (NumberFormatException ignored) { }
        }
        throw new IllegalArgumentException("无法解析数值");
    }

    private static double evalArithmetic(String expression) {
        String cleaned = expression.replaceAll("\\s+", "");
        if (cleaned.isEmpty() || !cleaned.matches("[0-9.+\\-*/()Ee]+"))
            throw new IllegalArgumentException("表达式求值失败");
        return new Object() {
            int pos = 0;
            double parse() {
                double v = parseExpression();
                if (pos < cleaned.length()) throw new IllegalArgumentException("表达式求值失败");
                return v;
            }
            double parseExpression() {
                double v = parseTerm();
                while (pos < cleaned.length()) {
                    char c = cleaned.charAt(pos);
                    if (c == '+') { pos++; v += parseTerm(); }
                    else if (c == '-') { pos++; v -= parseTerm(); }
                    else break;
                }
                return v;
            }
            double parseTerm() {
                double v = parseFactor();
                while (pos < cleaned.length()) {
                    char c = cleaned.charAt(pos);
                    if (c == '*') { pos++; v *= parseFactor(); }
                    else if (c == '/') {
                        pos++;
                        double d = parseFactor();
                        if (d == 0) throw new IllegalArgumentException("表达式除零");
                        v /= d;
                    } else break;
                }
                return v;
            }
            double parseFactor() {
                if (pos < cleaned.length() && cleaned.charAt(pos) == '(') {
                    pos++;
                    double v = parseExpression();
                    if (pos >= cleaned.length() || cleaned.charAt(pos) != ')')
                        throw new IllegalArgumentException("表达式括号不匹配");
                    pos++;
                    return v;
                }
                int start = pos;
                if (pos < cleaned.length() && (cleaned.charAt(pos) == '+' || cleaned.charAt(pos) == '-')) pos++;
                while (pos < cleaned.length() && (Character.isDigit(cleaned.charAt(pos))
                        || cleaned.charAt(pos) == '.' || cleaned.charAt(pos) == 'e' || cleaned.charAt(pos) == 'E')) pos++;
                if (start == pos) throw new IllegalArgumentException("表达式求值失败");
                return Double.parseDouble(cleaned.substring(start, pos));
            }
        }.parse();
    }

    @Transactional
    public Map<String, Object> submitPublish(Long id, Integer expected, String note) {
        caps.require("bi:metric:publish");
        var row = require(id, "publish");
        if ("published".equals(row.getPublishStatus())) throw new IllegalArgumentException("指标已发布");
        version(expected);
        if (!Objects.equals(expected, row.getVersion()))
            throw new BiAccessException(409, "指标已被其他人修改，请重新打开后再提交");
        validateGovernance(decode(row));
        if (publishRequests.countPending(id) > 0) throw new IllegalArgumentException("已有待审批的发布申请");
        String clean = note == null ? null : note.trim();
        if (clean != null && clean.length() > 1000) throw new IllegalArgumentException("申请说明过长");
        Map<String, Object> req = new HashMap<>();
        req.put("metricId", id);
        req.put("requestNote", clean);
        req.put("requestedBy", actor());
        req.put("metricVersion", expected);
        if (publishRequests.insert(req) != 1 || !(req.get("id") instanceof Number rid))
            throw new IllegalStateException("发布申请创建失败");
        audit.record("METRIC_PUBLISH_SUBMIT", "METRIC", id, null, Map.of("requestId", rid.longValue(), "metricVersion", expected));
        Map<String, Object> result = new LinkedHashMap<>(summary(row));
        result.put("publishRequestId", rid.longValue());
        result.put("publishRequestStatus", "PENDING");
        return result;
    }

    public List<Map<String, Object>> pendingPublishRequests() {
        caps.require("bi:metric:publish");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : publishRequests.listPending()) {
            Long metricId = number(row.get("metricId"));
            if (metricId == null) continue;
            if (!resources.allowed(RESOURCE, metricId, "grant") && !caps.has("bi:resource:admin")) continue;
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> decidePublish(Long requestId, boolean approve, String note) {
        caps.require("bi:metric:publish");
        Map<String, Object> req = publishRequests.find(requestId);
        if (req == null) throw new IllegalArgumentException("发布申请不存在");
        if (!"PENDING".equals(String.valueOf(req.get("status")))) throw new IllegalArgumentException("申请已处理");
        Long metricId = number(req.get("metricId"));
        Integer expected = number(req.get("metricVersion")) == null ? null : number(req.get("metricVersion")).intValue();
        resources.require(RESOURCE, metricId, "view");
        if (!resources.allowed(RESOURCE, metricId, "grant") && !caps.has("bi:resource:admin"))
            throw new BiAccessException(403, "无权审批该指标发布");
        String clean = note == null ? null : note.trim();
        if (clean != null && clean.length() > 1000) throw new IllegalArgumentException("审批意见过长");
        if (publishRequests.decide(requestId, approve ? "APPROVED" : "REJECTED", clean, actor()) != 1)
            throw new IllegalStateException("审批落库失败");
        audit.record(approve ? "METRIC_PUBLISH_APPROVE" : "METRIC_PUBLISH_REJECT", "METRIC", metricId, null,
                Map.of("requestId", requestId));
        if (!approve) return Map.of("requestId", requestId, "status", "REJECTED", "metricId", metricId);
        Map<String, Object> published = publish(metricId, expected);
        published.put("requestId", requestId);
        published.put("status", "APPROVED");
        return published;
    }

    @Transactional
    public Map<String, Object> publish(Long id, Integer expected) {
        caps.require("bi:metric:publish");
        var row = require(id, "publish");
        if ("published".equals(row.getPublishStatus())) throw new IllegalArgumentException("指标已发布");
        version(expected);
        Definition definition = decode(row);
        validateGovernance(definition);
        if (mapper.publish(id, expected, actor()) != 1)
            throw new BiAccessException(409, "指标已被其他人修改，请重新打开后再发布");
        var fresh = mapper.get(id);
        var history = new BiMetricHistoryDO();
        history.setMetricId(id);
        history.setPublishedVersion(fresh.getPublishedVersion());
        history.setCode(fresh.getCode());
        history.setName(fresh.getName());
        history.setKind(fresh.getKind());
        history.setDatasetId(fresh.getDatasetId());
        history.setDefinitionJson(fresh.getPublishedJson());
        history.setPublishBy(actor());
        mapper.insertHistory(history);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", expected + 1);
        result.put("publishedVersion", fresh.getPublishedVersion());
        result.put("metricId", id);
        return result;
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    @Transactional
    public Map<String, Object> unpublish(Long id, Integer expected) {
        caps.require("bi:metric:publish");
        var row = require(id, "publish");
        if (!"published".equals(row.getPublishStatus())) throw new IllegalArgumentException("指标未发布");
        if (inUse(row)) throw new IllegalArgumentException("指标已被引用，不能取消发布");
        version(expected);
        if (mapper.unpublish(id, expected, actor()) != 1)
            throw new BiAccessException(409, "指标已被其他人修改或仍被引用");
        return Map.of("version", expected + 1);
    }

    @Transactional
    public Map<String, Object> delete(Long id) {
        caps.require("bi:metric:remove");
        var row = require(id, "remove");
        if ("published".equals(row.getPublishStatus())) throw new IllegalArgumentException("已发布指标不能删除，请先取消发布");
        if (inUse(row)) throw new IllegalArgumentException("指标已被引用，不能删除");
        if (mapper.delete(id, actor()) != 1) throw new IllegalArgumentException("指标已被引用或已删除，不能删除");
        return Map.of("deleted", true);
    }

    public List<Map<String, Object>> history(Long id) {
        caps.require("bi:metric:list");
        require(id, "view");
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : mapper.history(id)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("publishedVersion", row.getPublishedVersion());
            item.put("code", row.getCode());
            item.put("name", row.getName());
            item.put("kind", row.getKind());
            item.put("datasetId", row.getDatasetId());
            item.put("publishBy", row.getPublishBy());
            item.put("publishTime", row.getPublishTime());
            try {
                Definition frozen = json.readValue(row.getDefinitionJson(), Definition.class);
                item.put("businessDefinition", frozen.businessDefinition());
                item.put("businessDimensions", joinDims(frozen.allowedDimensions()));
                item.put("grain", frozen.grain());
                item.put("unit", frozen.unit());
                item.put("timeZone", frozen.timeZone());
                item.put("owner", frozen.owner());
            } catch (RuntimeException invalid) {
                item.put("businessDefinition", "");
            }
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> usages(Long id) {
        caps.require("bi:metric:list");
        require(id, "view");
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : mapper.usages(id)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("refType", row.getRefType());
            item.put("refId", row.getRefId());
            item.put("remark", row.getRemark());
            item.put("createBy", row.getCreateBy());
            item.put("createTime", row.getCreateTime());
            result.add(item);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> bind(Long id, UsageRequest request) {
        caps.require("bi:metric:query");
        var row = require(id, "query");
        if (!"published".equals(row.getPublishStatus())) throw new IllegalArgumentException("仅已发布指标可被引用");
        var usage = new BiMetricUsageDO();
        usage.setMetricId(id);
        usage.setRefType(token(request == null ? null : request.refType(), "引用类型"));
        usage.setRefId(token(request == null ? null : request.refId(), "引用ID"));
        String remark = request == null || request.remark() == null ? "" : request.remark().trim();
        if (remark.length() > 200) throw new IllegalArgumentException("引用说明不能超过 200 字");
        usage.setRemark(remark.isEmpty() ? null : remark);
        usage.setCreateBy(actor());
        mapper.bind(usage);
        return Map.of("bound", true);
    }

    @Transactional
    public Map<String, Object> unbind(Long id, String refType, String refId) {
        caps.require("bi:metric:query");
        require(id, "query");
        if (mapper.unbind(id, token(refType, "引用类型"), token(refId, "引用ID")) != 1)
            throw new IllegalArgumentException("引用登记不存在");
        return Map.of("unbound", true);
    }

    public List<Map<String, Object>> grants(Long id) {
        caps.require("bi:metric:grant");
        require(id, "grant");
        return resources.grants(RESOURCE, id);
    }

    public void grant(Long id, Long principal, String action, boolean add) {
        caps.require("bi:metric:grant");
        require(id, "grant");
        resources.change(RESOURCE, id, principal, action, add);
    }

    private Map<String, Object> summary(BiMetricDO row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getId());
        item.put("code", row.getCode());
        item.put("name", row.getName());
        item.put("kind", row.getKind());
        item.put("datasetId", row.getDatasetId());
        item.put("version", row.getVersion());
        item.put("publishStatus", row.getPublishStatus());
        item.put("publishedVersion", row.getPublishedVersion() == null ? 0 : row.getPublishedVersion());
        item.put("inUse", inUse(row));
        item.put("pendingPublish", publishRequests.countPending(row.getId()) > 0);
        item.put("resourceActions", java.util.stream.Stream.of("edit", "query", "publish", "remove", "grant")
                .filter(a -> resources.allowed(RESOURCE, row.getId(), a)).toList());
        return item;
    }

    private boolean dataAllowed(Long id) {
        return caps.has("bi:dataset:query") && resources.allowed("dataset", id, "view")
                && resources.allowed("dataset", id, "query");
    }

    private boolean inUse(BiMetricDO row) {
        return row.getUsageCount() != null && row.getUsageCount() > 0;
    }

    private BiMetricDO require(Long id, String action) {
        resources.require(RESOURCE, id, "view");
        resources.require(RESOURCE, id, action);
        BiMetricDO row = mapper.get(id);
        if (row == null || row.getDefinitionJson() == null) throw new IllegalArgumentException("指标不存在");
        caps.require("bi:dataset:query");
        resources.require("dataset", row.getDatasetId(), "view");
        resources.require("dataset", row.getDatasetId(), "query");
        return row;
    }

    private Definition decode(BiMetricDO row) {
        Definition d;
        try { d = json.readValue(row.getDefinitionJson(), Definition.class); }
        catch (RuntimeException e) { throw new IllegalArgumentException("指标定义损坏，请联系管理员"); }
        if (d == null || d.datasetId() == null || !Objects.equals(row.getDatasetId(), d.datasetId())
                || !Objects.equals(row.getCode(), d.code()))
            throw new IllegalArgumentException("指标编码或数据集绑定不一致");
        validate(d, false);
        return d;
    }

    private Definition decodePublished(BiMetricDO row) {
        try { return json.readValue(row.getPublishedJson(), Definition.class); }
        catch (RuntimeException e) { throw new IllegalArgumentException("已发布指标定义损坏，请联系管理员"); }
    }

    private void validate(Definition d, boolean creating) {
        if (d == null || !Objects.equals(d.schemaVersion(), 2) || d.name() == null || d.name().isBlank()
                || d.name().trim().length() > 200 || d.kind() == null || !KINDS.contains(d.kind())
                || d.datasetId() == null)
            throw new IllegalArgumentException("请选择指标类型并填写有效名称，定义版本须为 2");
        if (creating) {
            if (d.code() == null || !d.code().matches("^[A-Za-z][A-Za-z0-9_]{1,63}$"))
                throw new IllegalArgumentException("指标编码须为 2～64 位字母开头的字母、数字或下划线");
        }
        validateKind(d);
    }

    private void validateKind(Definition d) {
        if (d.kind() == null || !KINDS.contains(d.kind())) throw new IllegalArgumentException("请选择指标类型");
        var dataset = datasets.selectById(d.datasetId());
        if (dataset == null || !"table".equals(dataset.getType()))
            throw new IllegalArgumentException("本期指标仅支持表数据集");
        List<String> allowed = d.allowedDimensions() == null ? List.of() : d.allowedDimensions();
        for (String dim : allowed) {
            if (dim == null || dim.isBlank() || dim.length() > 128)
                throw new IllegalArgumentException("允许维度无效");
        }
        if (d.fixedFilters() != null) {
            for (QueryModel.Filter f : d.fixedFilters()) {
                if (f == null || f.getField() == null || f.getField().isBlank() || f.getOp() == null)
                    throw new IllegalArgumentException("固定过滤无效");
                if (!QueryModel.OP_WHITELIST.contains(f.getOp().toUpperCase(Locale.ROOT)))
                    throw new IllegalArgumentException("固定过滤操作符不支持");
            }
        }
        if ("atomic".equals(d.kind())) {
            if (d.measure() == null || blank(d.measure().field()) || blank(d.measure().agg()))
                throw new IllegalArgumentException("原子指标须配置度量字段与聚合");
            String agg = d.measure().agg().trim().toUpperCase(Locale.ROOT);
            if (!AGGS.contains(agg)) throw new IllegalArgumentException("聚合函数不支持: " + agg);
            QueryModel probe = MetricQueryCompiler.compileAtomic(
                    normalizeDefinition(d, agg), null,
                    new MeasureSpec(d.measure().field().trim(), agg), "value");
            queries.compileOnly(probe);
            return;
        }
        if ("derived".equals(d.kind())) {
            List<ComponentRef> components = d.components() == null ? List.of() : d.components();
            if (components.size() != 2) throw new IllegalArgumentException("派生指标须引用恰好 2 个已发布原子指标");
            String expression = d.expression() == null ? "" : d.expression().trim();
            if (expression.isBlank() || expression.length() > 500)
                throw new IllegalArgumentException("派生表达式须为 1～500 字");
            if (!expression.matches("[A-Za-z0-9_+\\-*/().\\s]+"))
                throw new IllegalArgumentException("派生表达式含非法字符");
            Set<String> aliases = new HashSet<>();
            for (ComponentRef component : components) {
                if (component == null || blank(component.code()) || blank(component.alias()))
                    throw new IllegalArgumentException("派生组件编码与别名必填");
                if (!component.alias().matches("^[A-Za-z][A-Za-z0-9_]{0,31}$"))
                    throw new IllegalArgumentException("派生别名无效: " + component.alias());
                if (!aliases.add(component.alias())) throw new IllegalArgumentException("派生别名重复");
                if (!expression.contains(component.alias()))
                    throw new IllegalArgumentException("派生表达式须包含别名: " + component.alias());
                BiMetricDO atomic = requirePublishedAtomic(component.code());
                if (!Objects.equals(atomic.getDatasetId(), d.datasetId()))
                    throw new IllegalArgumentException("派生组件须与指标同属一个数据集: " + component.code());
            }
            return;
        }
        // composite
        List<String> components = d.componentMetricCodes() == null ? List.of() : d.componentMetricCodes();
        if (components.size() < 2 || components.size() > 8)
            throw new IllegalArgumentException("复合指标须引用 2～8 个已发布原子或派生指标编码");
        String expression = d.compositeExpression() == null ? "" : d.compositeExpression().trim();
        if (expression.isBlank() || expression.length() > 500)
            throw new IllegalArgumentException("复合表达式须为 1～500 字（仅允许编码占位与 + - * / ()）");
        if (!expression.matches("[A-Za-z0-9_+\\-*/().\\s]+"))
            throw new IllegalArgumentException("复合表达式含非法字符");
        for (String code : components) {
            if (code == null || !code.matches("^[A-Za-z][A-Za-z0-9_]{1,63}$"))
                throw new IllegalArgumentException("组件指标编码无效: " + code);
            if (!expression.contains(code))
                throw new IllegalArgumentException("复合表达式须包含组件编码: " + code);
            BiMetricDO component = mapper.findByCode(code);
            if (component == null || !"published".equals(component.getPublishStatus())
                    || (!"atomic".equals(component.getKind()) && !"derived".equals(component.getKind())))
                throw new IllegalArgumentException("复合指标组件须为已发布原子或派生指标: " + code);
            resources.require(RESOURCE, component.getId(), "view");
        }
    }

    private Definition normalizeDefinition(Definition d, String agg) {
        return new Definition(d.schemaVersion(), d.name(), d.code(), d.kind(), d.datasetId(),
                new MeasureSpec(d.measure().field().trim(), agg),
                d.fixedFilters() == null ? List.of() : List.copyOf(d.fixedFilters()),
                d.allowedDimensions() == null ? List.of() : List.copyOf(d.allowedDimensions()),
                d.components(), d.expression(), d.componentMetricCodes(), d.compositeExpression(),
                d.businessDefinition(), d.grain(), d.unit(), d.timeZone(), d.owner());
    }

    private BiMetricDO requirePublishedAtomic(String code) {
        BiMetricDO row = mapper.findByCode(code);
        if (row == null || !"published".equals(row.getPublishStatus()) || !"atomic".equals(row.getKind()))
            throw new IllegalArgumentException("须引用已发布原子指标: " + code);
        resources.require(RESOURCE, row.getId(), "view");
        return row;
    }

    private BiMetricDO row(Definition d, boolean creating) {
        validate(d, creating);
        Definition normalized = canonicalize(d, creating);
        String encoded = json.writeValueAsString(normalized);
        if (encoded.length() > 131072) throw new IllegalArgumentException("指标定义不能超过 128K 字符");
        var row = new BiMetricDO();
        row.setCode(normalized.code());
        row.setName(normalized.name());
        row.setKind(normalized.kind());
        row.setDatasetId(normalized.datasetId());
        row.setDefinitionJson(encoded);
        row.setUpdateBy(actor());
        return row;
    }

    private Definition canonicalize(Definition d, boolean creating) {
        String code = creating ? d.code().trim() : d.code();
        MeasureSpec measure = null;
        if ("atomic".equals(d.kind())) {
            measure = new MeasureSpec(d.measure().field().trim(), d.measure().agg().trim().toUpperCase(Locale.ROOT));
        }
        List<QueryModel.Filter> fixed = d.fixedFilters() == null ? List.of() : List.copyOf(d.fixedFilters());
        List<String> allowed = d.allowedDimensions() == null ? List.of() : List.copyOf(d.allowedDimensions());
        if ("derived".equals(d.kind()) && allowed.isEmpty()) {
            Set<String> intersection = null;
            for (ComponentRef component : d.components()) {
                Definition atomic = decodePublished(requirePublishedAtomic(component.code()));
                Set<String> dims = new LinkedHashSet<>(
                        atomic.allowedDimensions() == null ? List.of() : atomic.allowedDimensions());
                if (intersection == null) intersection = dims;
                else intersection.retainAll(dims);
            }
            allowed = intersection == null ? List.of() : List.copyOf(intersection);
        }
        return new Definition(2, d.name().trim(), code, d.kind(), d.datasetId(), measure, fixed, allowed,
                d.components() == null ? null : List.copyOf(d.components()), clean(d.expression()),
                d.componentMetricCodes() == null ? null : List.copyOf(d.componentMetricCodes()),
                clean(d.compositeExpression()),
                clean(d.businessDefinition()), clean(d.grain()), clean(d.unit()), clean(d.timeZone()), clean(d.owner()));
    }

    private void version(Integer expected) {
        if (expected == null || expected < 0) throw new IllegalArgumentException("缺少指标版本，请刷新后重试");
    }

    private void validateGovernance(Definition d) {
        if (blank(d.businessDefinition()) || blank(d.grain()) || blank(d.unit()) || blank(d.timeZone()) || blank(d.owner()))
            throw new IllegalArgumentException("发布前请补充业务定义、统计粒度、单位、时区和负责人");
        if (d.businessDefinition().trim().length() > 2000 || d.grain().trim().length() > 100
                || d.unit().trim().length() > 64 || d.owner().trim().length() > 128)
            throw new IllegalArgumentException("指标口径字段超出长度限制");
        String zoneId = d.timeZone().trim();
        if (!java.time.ZoneId.getAvailableZoneIds().contains(zoneId))
            throw new IllegalArgumentException("请填写 IANA 时区名称，例如 Asia/Shanghai 或 UTC；不接受固定偏移量");
    }

    private static boolean hasDims(MetricQueryRequest query) {
        return query != null && query.dimensions() != null && !query.dimensions().isEmpty();
    }

    private static String joinDims(List<String> dims) {
        if (dims == null || dims.isEmpty()) return "";
        return String.join(",", dims);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String clean(String value) { return value == null ? null : value.trim(); }
    private String norm(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String token(String value, String label) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_.:-]{0,63}$"))
            throw new IllegalArgumentException(label + "格式无效");
        return value;
    }

    private String actor() { return actors.currentActor().username(); }
}
