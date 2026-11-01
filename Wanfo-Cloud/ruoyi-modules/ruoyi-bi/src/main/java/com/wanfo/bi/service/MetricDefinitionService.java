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
import com.wanfo.bi.security.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Independent metric resource: hand-filled code, publish freeze, usage blocks unpublish. */
@Service
public class MetricDefinitionService {
    public static final String RESOURCE = "metric";
    private static final Set<String> KINDS = Set.of("scalar", "table", "composite");
    public record Definition(Integer schemaVersion, String name, String code, String kind, QueryModel query,
            String businessDefinition, String businessDimensions, String grain, String unit, String timeZone, String owner,
            List<String> componentMetricCodes, String compositeExpression)
    {
        public Definition(Integer schemaVersion,String name,String code,String kind,QueryModel query)
        { this(schemaVersion,name,code,kind,query,null,null,null,null,null,null,null,null); }
        public Definition(Integer schemaVersion,String name,String code,String kind,QueryModel query,
                String businessDefinition,String businessDimensions,String grain,String unit,String timeZone,String owner)
        { this(schemaVersion,name,code,kind,query,businessDefinition,businessDimensions,grain,unit,timeZone,owner,null,null); }
    }
    public record SaveRequest(Integer version, Definition definition) { }
    public record UsageRequest(String refType, String refId, String remark) { }
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

    /** Finds metrics backed by the same dataset measure but documented with different business semantics. */
    public List<Map<String,Object>> conflicts() {
        caps.require("bi:metric:list");
        List<BiMetricDO> visible=mapper.list().stream().filter(row->resources.allowed(RESOURCE,row.getId(),"view")&&dataAllowed(row.getDatasetId())).toList();
        List<Map<String,Object>> result=new ArrayList<>();
        for(int i=0;i<visible.size();i++) for(int j=i+1;j<visible.size();j++) {
            BiMetricDO left=visible.get(i),right=visible.get(j); if(!Objects.equals(left.getDatasetId(),right.getDatasetId()))continue;
            Definition a=decode(left),b=decode(right); if(a.query().getMetrics()==null||b.query().getMetrics()==null)continue;
            for(var ma:a.query().getMetrics()) for(var mb:b.query().getMetrics()) {
                if(!Objects.equals(ma.getField(),mb.getField())||!Objects.equals(norm(ma.getAgg()),norm(mb.getAgg())))continue;
                List<String> differences=new ArrayList<>();
                if(!Objects.equals(norm(a.businessDimensions()),norm(b.businessDimensions())))differences.add("业务维度");
                if(!Objects.equals(norm(a.grain()),norm(b.grain())))differences.add("统计粒度");
                if(!Objects.equals(norm(a.unit()),norm(b.unit())))differences.add("单位");
                if(!Objects.equals(norm(a.timeZone()),norm(b.timeZone())))differences.add("时区");
                if(!Objects.equals(norm(a.businessDefinition()),norm(b.businessDefinition())))differences.add("业务定义");
                if(!differences.isEmpty())result.add(Map.of("leftId",left.getId(),"leftCode",left.getCode(),"leftName",left.getName(),"rightId",right.getId(),"rightCode",right.getCode(),"rightName",right.getName(),"datasetId",left.getDatasetId(),"field",ma.getField(),"aggregation",ma.getAgg()==null?"":ma.getAgg(),"differences",differences));
            }
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

    /** Consumption-side definition: frozen governance fields, glossary chain, and published-version pin. */
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
        out.put("businessDefinition", source.businessDefinition());
        out.put("businessDimensions", source.businessDimensions());
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
        if (request == null || request.version() == null || request.version() < 0) throw new IllegalArgumentException("缺少指标版本，请刷新后重试");
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
        if (definition == null || definition.kind() == null || definition.query() == null)
            throw new IllegalArgumentException("请选择指标类型并配置查询");
        validateKindAndQuery(definition);
        if ("composite".equals(definition.kind())) return evaluateComposite(definition);
        return queries.query(definition.query());
    }

    public Map<String, Object> data(Long id) throws Exception {
        caps.require("bi:metric:query");
        var row = require(id, "query");
        var source = "published".equals(row.getPublishStatus()) ? decodePublished(row) : decode(row);
        if ("composite".equals(source.kind())) return evaluateComposite(source);
        return queries.query(copy(source.query()));
    }

    /** Executes a frozen, published metric as a bounded keyset-independent page. */
    public Map<String,Object> dataPage(Long id,int page,int pageSize,int serviceMaxPageSize,int timeoutSeconds)throws Exception {
        caps.require("bi:metric:query");
        if(page<1||page>100_000)throw new IllegalArgumentException("page 须在 1～100000 之间");
        if(pageSize<1||pageSize>serviceMaxPageSize||pageSize>1000)throw new IllegalArgumentException("pageSize 超出服务限制");
        BiMetricDO row=require(id,"query");
        if(!"published".equals(row.getPublishStatus())||row.getPublishedJson()==null)throw new IllegalArgumentException("指标未发布，不能通过受控数据 API 查询");
        Definition published=decodePublished(row);
        if("composite".equals(published.kind())){
            if(page!=1)throw new IllegalArgumentException("复合指标仅支持单页标量结果");
            return evaluateComposite(published);
        }
        QueryModel query=copy(published.query());
        long offset=(long)(page-1)*pageSize;if(offset>1_000_000)throw new IllegalArgumentException("翻页偏移超过服务限制");
        query.setLimit(pageSize);query.setOffset((int)offset);
        query.setTimeoutSeconds(Math.max(1,Math.min(timeoutSeconds,120)));
        return queries.query(query);
    }

    public Map<String, Object> dataByCode(String code) throws Exception {
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
        Definition published = decodePublished(row);
        if ("composite".equals(published.kind())) {
            Map<String, Object> result = evaluateComposite(published);
            result.remove("executedSql");
            result.remove("params");
            return result;
        }
        var result = queries.query(copy(published.query()));
        result.remove("executedSql");
        result.remove("params");
        return result;
    }

    /** Evaluates a composite metric by querying published scalar components and applying the arithmetic expression. */
    private Map<String, Object> evaluateComposite(Definition definition) throws Exception {
        List<String> components = definition.componentMetricCodes();
        Map<String, Double> values = new LinkedHashMap<>();
        for (String code : components) {
            BiMetricDO component = mapper.list().stream().filter(m -> code.equals(m.getCode())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("复合组件不存在: " + code));
            resources.require(RESOURCE, component.getId(), "query");
            if (!"published".equals(component.getPublishStatus()) || !"scalar".equals(component.getKind()))
                throw new IllegalArgumentException("复合组件须为已发布标量指标: " + code);
            Map<String, Object> raw = queries.query(copy(decodePublished(component).query()));
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
            throw new IllegalArgumentException("复合组件未返回标量行");
        for (Object value : ((Map<String, Object>) row).values()) {
            if (value instanceof Number number) return number.doubleValue();
            if (value != null) {
                try { return Double.parseDouble(String.valueOf(value)); }
                catch (NumberFormatException ignored) { }
            }
        }
        throw new IllegalArgumentException("复合组件结果无法解析为数值");
    }

    /** Safe + - * / () evaluator for rewritten numeric expressions. */
    private static double evalArithmetic(String expression) {
        String cleaned = expression.replaceAll("\\s+", "");
        if (cleaned.isEmpty() || !cleaned.matches("[0-9.+\\-*/()Ee]+"))
            throw new IllegalArgumentException("复合表达式求值失败");
        return new Object() {
            int pos = 0;
            double parse() { double v = parseExpression(); if (pos < cleaned.length()) throw new IllegalArgumentException("复合表达式求值失败"); return v; }
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
                    else if (c == '/') { pos++; double d = parseFactor(); if (d == 0) throw new IllegalArgumentException("复合表达式除零"); v /= d; }
                    else break;
                }
                return v;
            }
            double parseFactor() {
                if (pos < cleaned.length() && cleaned.charAt(pos) == '(') {
                    pos++; double v = parseExpression();
                    if (pos >= cleaned.length() || cleaned.charAt(pos) != ')') throw new IllegalArgumentException("复合表达式括号不匹配");
                    pos++; return v;
                }
                int start = pos;
                if (pos < cleaned.length() && (cleaned.charAt(pos) == '+' || cleaned.charAt(pos) == '-')) pos++;
                while (pos < cleaned.length() && (Character.isDigit(cleaned.charAt(pos)) || cleaned.charAt(pos) == '.' || cleaned.charAt(pos) == 'e' || cleaned.charAt(pos) == 'E')) pos++;
                if (start == pos) throw new IllegalArgumentException("复合表达式求值失败");
                return Double.parseDouble(cleaned.substring(start, pos));
            }
        }.parse();
    }

    /** Submits a draft for steward approval; does not freeze until approvePublish. */
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

    /** Freezes the draft into a published snapshot (called after approval or by tests). */
    @Transactional
    public Map<String, Object> publish(Long id, Integer expected) {
        caps.require("bi:metric:publish");
        var row = require(id, "publish");
        if ("published".equals(row.getPublishStatus())) throw new IllegalArgumentException("指标已发布");
        version(expected);
        Definition definition=decode(row);
        validateGovernance(definition);
        if (mapper.publish(id, expected, actor()) != 1) throw new BiAccessException(409, "指标已被其他人修改，请重新打开后再发布");
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
        if (mapper.unpublish(id, expected, actor()) != 1) throw new BiAccessException(409, "指标已被其他人修改或仍被引用");
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
                Definition frozen=json.readValue(row.getDefinitionJson(),Definition.class);
                item.put("businessDefinition",frozen.businessDefinition()); item.put("businessDimensions",frozen.businessDimensions());
                item.put("grain",frozen.grain()); item.put("unit",frozen.unit()); item.put("timeZone",frozen.timeZone()); item.put("owner",frozen.owner());
            } catch(RuntimeException invalid) { item.put("businessDefinition",""); }
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
        return caps.has("bi:dataset:query") && resources.allowed("dataset", id, "view") && resources.allowed("dataset", id, "query");
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
        if (d == null || d.query() == null || !Objects.equals(row.getDatasetId(), d.query().getDatasetId())
                || !Objects.equals(row.getCode(), d.code()))
            throw new IllegalArgumentException("指标编码或数据集绑定不一致");
        validate(d, false);
        return d;
    }

    private Definition decodePublished(BiMetricDO row) {
        try { return json.readValue(row.getPublishedJson(), Definition.class); }
        catch (RuntimeException e) { throw new IllegalArgumentException("已发布指标定义损坏，请联系管理员"); }
    }

    private QueryModel copy(QueryModel query) {
        return json.readValue(json.writeValueAsString(query), QueryModel.class);
    }

    private void validate(Definition d, boolean creating) {
        if (d == null || !Objects.equals(d.schemaVersion(), 1) || d.name() == null || d.name().isBlank()
                || d.name().trim().length() > 200 || d.kind() == null || !KINDS.contains(d.kind()) || d.query() == null)
            throw new IllegalArgumentException("请选择指标类型并填写有效名称，定义版本须为 1");
        if (creating) {
            if (d.code() == null || !d.code().matches("^[A-Za-z][A-Za-z0-9_]{1,63}$"))
                throw new IllegalArgumentException("指标编码须为 2～64 位字母开头的字母、数字或下划线");
        }
        validateKindAndQuery(d);
    }

    private void validateKindAndQuery(Definition d) {
        if (d.kind() == null || !KINDS.contains(d.kind()) || d.query() == null)
            throw new IllegalArgumentException("请选择指标类型并配置查询");
        if ("composite".equals(d.kind())) {
            List<String> components = d.componentMetricCodes() == null ? List.of() : d.componentMetricCodes();
            if (components.size() < 2 || components.size() > 8)
                throw new IllegalArgumentException("复合指标须引用 2～8 个已发布标量指标编码");
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
                BiMetricDO component = mapper.list().stream().filter(m -> code.equals(m.getCode())).findFirst().orElse(null);
                if (component == null || !"published".equals(component.getPublishStatus()) || !"scalar".equals(component.getKind()))
                    throw new IllegalArgumentException("复合指标组件须为已发布标量指标: " + code);
                resources.require(RESOURCE, component.getId(), "view");
            }
            // 复合指标仍挂接首个组件的数据集以便目录与权限贯通；运行时按表达式组合标量结果（首期预览仅校验定义）。
            var dataset = datasets.selectById(d.query().getDatasetId());
            if (dataset == null) throw new IllegalArgumentException("复合指标须绑定锚点数据集");
            return;
        }
        var q = d.query();
        if (q.getDimensions() == null || q.getMetrics() == null || q.getFilters() == null || q.getOrderBys() == null)
            throw new IllegalArgumentException("查询列表不能为 null");
        if ("scalar".equals(d.kind())) {
            if (!q.getDimensions().isEmpty()) throw new IllegalArgumentException("标量指标不能包含维度");
            if (q.getMetrics().size() != 1) throw new IllegalArgumentException("标量指标须恰好一个聚合度量");
            String agg = q.getMetrics().get(0).getAgg();
            if (agg == null || "NONE".equalsIgnoreCase(agg)) throw new IllegalArgumentException("标量指标须使用聚合函数");
            if (q.getLimit() == null || q.getLimit() != 1) throw new IllegalArgumentException("标量指标行数须为 1");
        } else {
            if (q.getDimensions().isEmpty() && q.getMetrics().isEmpty())
                throw new IllegalArgumentException("表格指标至少包含一个维度或度量");
            if (q.getLimit() != null && q.getLimit() > 1000) throw new IllegalArgumentException("表格指标最多查询 1000 行");
        }
        queries.compileOnly(q);
        var dataset = datasets.selectById(q.getDatasetId());
        if (dataset == null || !"table".equals(dataset.getType())) throw new IllegalArgumentException("本期指标仅支持表数据集");
    }

    private BiMetricDO row(Definition d, boolean creating) {
        validate(d, creating);
        String encoded = json.writeValueAsString(new Definition(1, d.name().trim(), creating ? d.code().trim() : d.code(), d.kind(), d.query(),clean(d.businessDefinition()),clean(d.businessDimensions()),clean(d.grain()),clean(d.unit()),clean(d.timeZone()),clean(d.owner()),
                d.componentMetricCodes()==null?null:List.copyOf(d.componentMetricCodes()), clean(d.compositeExpression())));
        if (encoded.length() > 131072) throw new IllegalArgumentException("指标定义不能超过 128K 字符");
        var row = new BiMetricDO();
        row.setCode(creating ? d.code().trim() : d.code());
        row.setName(d.name().trim());
        row.setKind(d.kind());
        row.setDatasetId(d.query().getDatasetId());
        row.setDefinitionJson(encoded);
        row.setUpdateBy(actor());
        return row;
    }

    private void version(Integer expected) {
        if (expected == null || expected < 0) throw new IllegalArgumentException("缺少指标版本，请刷新后重试");
    }

    private void validateGovernance(Definition d) {
        if(blank(d.businessDefinition())||blank(d.grain())||blank(d.unit())||blank(d.timeZone())||blank(d.owner()))throw new IllegalArgumentException("发布前请补充业务定义、统计粒度、单位、时区和负责人");
        if(d.businessDefinition().trim().length()>2000||d.businessDimensions()!=null&&d.businessDimensions().length()>1000||d.grain().trim().length()>100||d.unit().trim().length()>64||d.owner().trim().length()>128)throw new IllegalArgumentException("指标口径字段超出长度限制");
        String zoneId=d.timeZone().trim();
        if(!java.time.ZoneId.getAvailableZoneIds().contains(zoneId))throw new IllegalArgumentException("请填写 IANA 时区名称，例如 Asia/Shanghai 或 UTC；不接受固定偏移量");
    }

    private boolean blank(String value){return value==null||value.isBlank();}
    private String clean(String value){return value==null?null:value.trim();}
    private String norm(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}

    private String token(String value, String label) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_.:-]{0,63}$"))
            throw new IllegalArgumentException(label + "格式无效");
        return value;
    }

    private String actor() { return actors.currentActor().username(); }
}
