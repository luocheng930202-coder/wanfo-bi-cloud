package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.BiMetricDO;
import com.wanfo.bi.domain.BiMetricHistoryDO;
import com.wanfo.bi.domain.BiMetricUsageDO;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.mapper.BiMetricMapper;
import com.wanfo.bi.security.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Independent metric resource: hand-filled code, publish freeze, usage blocks unpublish. */
@Service
public class MetricDefinitionService {
    public static final String RESOURCE = "metric";
    private static final Set<String> KINDS = Set.of("scalar", "table");
    public record Definition(Integer schemaVersion, String name, String code, String kind, QueryModel query) { }
    public record SaveRequest(Integer version, Definition definition) { }
    public record UsageRequest(String refType, String refId, String remark) { }
    private final BiMetricMapper mapper;
    private final BiDatasetTableMapper datasets;
    private final ChartQueryService queries;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider caps;
    private final CurrentActorProvider actors;
    private final ObjectMapper json;

    public MetricDefinitionService(BiMetricMapper mapper, BiDatasetTableMapper datasets, ChartQueryService queries,
            BiResourceAuthorizer resources, CapabilityProvider caps, CurrentActorProvider actors, ObjectMapper json) {
        this.mapper = mapper; this.datasets = datasets; this.queries = queries; this.resources = resources;
        this.caps = caps; this.actors = actors; this.json = json;
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

    public Map<String, Object> detail(Long id) {
        caps.require("bi:metric:list");
        var row = require(id, "view");
        Map<String, Object> result = summary(row);
        result.put("definition", decode(row));
        result.put("published", row.getPublishedJson() == null ? null : decodePublished(row));
        return result;
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
        return queries.query(definition.query());
    }

    public Map<String, Object> data(Long id) throws Exception {
        caps.require("bi:metric:query");
        var row = require(id, "query");
        var source = "published".equals(row.getPublishStatus()) ? decodePublished(row) : decode(row);
        return queries.query(copy(source.query()));
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
        var result = queries.query(copy(decodePublished(row).query()));
        result.remove("executedSql");
        result.remove("params");
        return result;
    }

    @Transactional
    public Map<String, Object> publish(Long id, Integer expected) {
        caps.require("bi:metric:publish");
        var row = require(id, "publish");
        if ("published".equals(row.getPublishStatus())) throw new IllegalArgumentException("指标已发布");
        version(expected);
        decode(row);
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
        return result;
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
        String encoded = json.writeValueAsString(new Definition(1, d.name().trim(), creating ? d.code().trim() : d.code(), d.kind(), d.query()));
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

    private String token(String value, String label) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_.:-]{0,63}$"))
            throw new IllegalArgumentException(label + "格式无效");
        return value;
    }

    private String actor() { return actors.currentActor().username(); }
}
