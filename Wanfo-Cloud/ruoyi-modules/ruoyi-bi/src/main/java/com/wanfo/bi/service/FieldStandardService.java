package com.wanfo.bi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.datasource.JdbcConnectionSettings;
import com.wanfo.bi.mapper.FieldStandardBindPlanMapper;
import com.wanfo.bi.mapper.FieldStandardMapper;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S3-02: field standards dictionary, column bindings, and lightweight compliance scan. */
@Service
public class FieldStandardService {
    private static final Set<String> FAMILIES = Set.of("STRING", "NUMBER", "DATETIME", "BOOLEAN");
    private static final Set<String> NULLABLE = Set.of("REQUIRED", "OPTIONAL");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DRAFT", "DEPRECATED");
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{1,95}");

    public record UpsertRequest(String standardCode, String domainCode, String name, String dataTypeFamily, Integer maxLength,
            String nullablePolicy, String valuePattern, List<String> enumValues, List<String> matchAliases,
            Long glossaryTermId, Long metricId, String description, String status, Boolean enforceGate) {
        public UpsertRequest(String standardCode, String name, String dataTypeFamily, Integer maxLength,
                String nullablePolicy, String valuePattern, List<String> enumValues, List<String> matchAliases,
                Long glossaryTermId, Long metricId, String description, String status) {
            this(standardCode, null, name, dataTypeFamily, maxLength, nullablePolicy, valuePattern, enumValues,
                    matchAliases, glossaryTermId, metricId, description, status, null);
        }
        public UpsertRequest(String standardCode, String name, String dataTypeFamily, Integer maxLength,
                String nullablePolicy, String valuePattern, List<String> enumValues, List<String> matchAliases,
                Long glossaryTermId, Long metricId, String description, String status, Boolean enforceGate) {
            this(standardCode, null, name, dataTypeFamily, maxLength, nullablePolicy, valuePattern, enumValues,
                    matchAliases, glossaryTermId, metricId, description, status, enforceGate);
        }
    }

    public record BindPlanEntry(Long standardId, Long assetId, String columnName) {
    }

    public record CreateBindPlanRequest(String name, List<BindPlanEntry> bindings, String remark) {
    }

    public record BindRequest(Long standardId, Long assetId, String note) {
    }

    public record SampleValidateRequest(List<String> values) {
    }

    private final FieldStandardMapper mapper;
    private final FieldStandardBindPlanMapper bindPlans;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final DataSourceProvider sources;
    private final BiResourceAuthorizer resources;
    private final ObjectMapper json = new ObjectMapper();

    public FieldStandardService(FieldStandardMapper mapper, FieldStandardBindPlanMapper bindPlans,
            CapabilityProvider capabilities, CurrentActorProvider actors, DataSourceProvider sources,
            BiResourceAuthorizer resources) {
        this.mapper = mapper;
        this.bindPlans = bindPlans;
        this.capabilities = capabilities;
        this.actors = actors;
        this.sources = sources;
        this.resources = resources;
    }

    public List<Map<String, Object>> list() {
        capabilities.require("bi:resource:admin");
        return mapper.list().stream().map(this::enrich).toList();
    }

    public Map<String, Object> get(Long id) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> row = mapper.find(id);
        if (row == null) throw new IllegalArgumentException("字段标准不存在");
        return enrich(row);
    }

    @Transactional
    public Map<String, Object> create(UpsertRequest request) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> row = validate(request, true);
        row.put("createBy", actors.currentActor().username());
        if (mapper.insert(row) != 1 || !(row.get("id") instanceof Number))
            throw new IllegalStateException("字段标准创建失败");
        return get(((Number) row.get("id")).longValue());
    }

    @Transactional
    public Map<String, Object> update(Long id, UpsertRequest request) {
        capabilities.require("bi:resource:admin");
        if (mapper.find(id) == null) throw new IllegalArgumentException("字段标准不存在");
        Map<String, Object> row = validate(request, false);
        row.put("id", id);
        row.put("updateBy", actors.currentActor().username());
        if (mapper.update(row) != 1) throw new IllegalStateException("字段标准更新失败");
        return get(id);
    }

    public List<Map<String, Object>> listBindings() {
        capabilities.require("bi:resource:admin");
        return mapper.listBindings();
    }

    @Transactional
    public Map<String, Object> bind(BindRequest request) {
        capabilities.require("bi:resource:admin");
        if (request == null || request.standardId() == null || request.assetId() == null)
            throw new IllegalArgumentException("标准与字段资产均必填");
        if (mapper.find(request.standardId()) == null) throw new IllegalArgumentException("字段标准不存在");
        Map<String, Object> asset = mapper.asset(request.assetId());
        if (asset == null || !"COLUMN".equalsIgnoreCase(String.valueOf(asset.get("assetType"))))
            throw new IllegalArgumentException("仅支持绑定 COLUMN 资产");
        Map<String, Object> row = new HashMap<>();
        row.put("standardId", request.standardId());
        row.put("assetId", request.assetId());
        row.put("bindSource", "MANUAL");
        row.put("note", blankToNull(request.note(), 500));
        row.put("createBy", actors.currentActor().username());
        mapper.upsertBinding(row);
        return mapper.bindingByAsset(request.assetId());
    }

    @Transactional
    public void unbind(Long assetId) {
        capabilities.require("bi:resource:admin");
        if (assetId == null || assetId < 1) throw new IllegalArgumentException("资产编号无效");
        mapper.deleteBinding(assetId);
    }

    public List<Map<String, Object>> listBindPlans() {
        capabilities.require("bi:resource:admin");
        return bindPlans.list().stream().map(this::enrichBindPlan).toList();
    }

    public Map<String, Object> getBindPlan(Long id) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> row = bindPlans.find(id);
        if (row == null) throw new IllegalArgumentException("绑定计划不存在");
        return enrichBindPlan(row);
    }

    @Transactional
    public Map<String, Object> createBindPlan(CreateBindPlanRequest request) {
        capabilities.require("bi:resource:admin");
        if (request == null || request.name() == null || request.name().isBlank())
            throw new IllegalArgumentException("计划名称必填");
        String name = request.name().trim();
        if (name.length() > 160) throw new IllegalArgumentException("计划名称过长");
        List<BindPlanEntry> bindings = request.bindings() == null ? List.of() : request.bindings();
        if (bindings.isEmpty() || bindings.size() > 500) throw new IllegalArgumentException("请提供 1～500 条绑定");
        validateBindPlanEntries(bindings);
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("bindingsJson", writeBindingsJson(bindings));
        row.put("createBy", actors.currentActor().username());
        row.put("remark", blankToNull(request.remark(), 500));
        if (bindPlans.insert(row) != 1 || !(row.get("id") instanceof Number id))
            throw new IllegalStateException("绑定计划创建失败");
        return getBindPlan(id.longValue());
    }

    @Transactional
    public Map<String, Object> approveBindPlan(Long id) {
        capabilities.require("bi:resource:admin");
        if (bindPlans.find(id) == null) throw new IllegalArgumentException("绑定计划不存在");
        if (bindPlans.approve(id, actors.currentActor().username()) != 1)
            throw new IllegalStateException("仅 DRAFT 计划可审批");
        return getBindPlan(id);
    }

    @Transactional
    public Map<String, Object> applyBindPlan(Long id) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> plan = bindPlans.find(id);
        if (plan == null) throw new IllegalArgumentException("绑定计划不存在");
        if (!"APPROVED".equals(String.valueOf(plan.get("status"))))
            throw new IllegalStateException("绑定计划须先审批");
        List<BindPlanEntry> bindings = readBindPlanEntries(plan.get("bindingsJson"));
        validateBindPlanEntries(bindings);
        String actor = actors.currentActor().username();
        for (BindPlanEntry entry : bindings) {
            Map<String, Object> binding = new HashMap<>();
            binding.put("standardId", entry.standardId());
            binding.put("assetId", entry.assetId());
            binding.put("bindSource", "BIND_PLAN");
            binding.put("note", "plan:" + id);
            binding.put("createBy", actor);
            mapper.upsertBinding(binding);
        }
        if (bindPlans.markApplied(id, actor) != 1) throw new IllegalStateException("绑定计划状态已变化");
        return getBindPlan(id);
    }

    /** Read-time compliance: explicit binding preferred, else name/alias match against ACTIVE standards. */
    public Map<String, Object> complianceCheck(Integer limit) {
        capabilities.require("bi:resource:admin");
        int safeLimit = limit == null ? 500 : Math.max(50, Math.min(limit, 2000));
        List<Map<String, Object>> standards = mapper.list().stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(String.valueOf(s.get("status"))))
                .map(this::enrich)
                .toList();
        List<Map<String, Object>> columns = mapper.recentColumns(safeLimit);
        List<Map<String, Object>> findings = new ArrayList<>();
        int compliant = 0, mismatch = 0, unbound = 0, aliased = 0;
        for (Map<String, Object> col : columns) {
            Long assetId = number(col.get("assetId"));
            String colName = String.valueOf(col.get("columnName"));
            String observedType = extractType(col.get("metadataJson"));
            String observedFamily = familyOf(observedType);
            Map<String, Object> binding = assetId == null ? null : mapper.bindingByAsset(assetId);
            Map<String, Object> matched = null;
            String matchMode = null;
            if (binding != null && binding.get("standardId") != null) {
                matched = standards.stream()
                        .filter(s -> String.valueOf(s.get("id")).equals(String.valueOf(binding.get("standardId"))))
                        .findFirst().orElse(null);
                matchMode = "MANUAL";
            }
            if (matched == null) {
                matched = matchByName(standards, colName);
                if (matched != null) matchMode = "AUTO_NAME";
            }
            Map<String, Object> finding = new LinkedHashMap<>();
            finding.put("assetId", assetId);
            finding.put("columnName", colName);
            finding.put("tableName", col.get("tableName"));
            finding.put("qualifiedName", col.get("qualifiedName"));
            finding.put("observedType", observedType);
            finding.put("observedFamily", observedFamily);
            if (matched == null) {
                finding.put("result", "UNBOUND");
                finding.put("message", "未匹配到字段标准");
                unbound++;
            } else {
                finding.put("standardId", matched.get("id"));
                finding.put("standardCode", matched.get("standardCode"));
                finding.put("standardName", matched.get("name"));
                finding.put("expectedFamily", matched.get("dataTypeFamily"));
                finding.put("matchMode", matchMode);
                finding.put("glossaryTermId", matched.get("glossaryTermId"));
                finding.put("metricId", matched.get("metricId"));
                String expected = String.valueOf(matched.get("dataTypeFamily"));
                if (observedFamily == null || observedFamily.isBlank()) {
                    finding.put("result", "TYPE_UNKNOWN");
                    finding.put("message", "目录字段缺少类型元数据");
                    mismatch++;
                } else if (!expected.equals(observedFamily)) {
                    finding.put("result", "TYPE_MISMATCH");
                    finding.put("message", "类型族不一致：观测=" + observedFamily + " 标准=" + expected);
                    mismatch++;
                } else {
                    finding.put("result", "COMPLIANT");
                    finding.put("message", "AUTO_NAME".equals(matchMode) ? "名称别名命中且类型族一致" : "绑定且类型族一致");
                    compliant++;
                    if ("AUTO_NAME".equals(matchMode)) aliased++;
                }
            }
            findings.add(finding);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scannedColumns", columns.size());
        summary.put("activeStandards", standards.size());
        summary.put("compliant", compliant);
        summary.put("typeMismatch", mismatch);
        summary.put("unbound", unbound);
        summary.put("aliasMatched", aliased);
        summary.put("note", "落标检查为只读扫描：优先手工绑定，其次按标准名称/别名匹配；不自动改写目录。enforceGate=1 的绑定在迁移执行时阻断类型族不一致。");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("findings", findings);
        return out;
    }

    /**
     * Write gate for structure migration: bound columns whose standard has enforce_gate=1 must keep type family.
     */
    public Map<String, Object> enforceGateCheck(Long assetId) {
        capabilities.require("bi:resource:admin");
        if (assetId == null || assetId < 1) throw new IllegalArgumentException("资产编号无效");
        Map<String, Object> asset = mapper.asset(assetId);
        if (asset == null) throw new IllegalArgumentException("资产不存在");
        List<Map<String, Object>> columns = mapper.columnsUnder(assetId);
        if (columns.isEmpty() && "COLUMN".equalsIgnoreCase(String.valueOf(asset.get("assetType")))) {
            // columnsUnder also matches c.id=tableAssetId for COLUMN self
            columns = mapper.columnsUnder(assetId);
        }
        List<Map<String, Object>> blockers = new ArrayList<>();
        for (Map<String, Object> col : columns) {
            Long colId = number(col.get("assetId"));
            Map<String, Object> binding = colId == null ? null : mapper.bindingByAsset(colId);
            if (binding == null || binding.get("standardId") == null) continue;
            Map<String, Object> standard = mapper.find(number(binding.get("standardId")));
            if (standard == null || !"ACTIVE".equalsIgnoreCase(String.valueOf(standard.get("status")))) continue;
            if (!truthy(standard.get("enforceGate"))) continue;
            String observedFamily = familyOf(extractType(col.get("metadataJson")));
            String expected = String.valueOf(standard.get("dataTypeFamily"));
            if (observedFamily == null || !expected.equals(observedFamily)) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("assetId", colId);
                hit.put("columnName", col.get("columnName"));
                hit.put("standardCode", standard.get("standardCode"));
                hit.put("expectedFamily", expected);
                hit.put("observedFamily", observedFamily);
                hit.put("message", "强制落标类型族不一致");
                blockers.add(hit);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assetId", assetId);
        out.put("passed", blockers.isEmpty());
        out.put("blockers", blockers);
        out.put("scannedColumns", columns.size());
        return out;
    }

    public void assertEnforceGate(Long assetId) {
        Map<String, Object> gate = enforceGateCheck(assetId);
        if (!Boolean.TRUE.equals(gate.get("passed"))) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blockers = (List<Map<String, Object>>) gate.get("blockers");
            String detail = blockers == null || blockers.isEmpty() ? "落标门禁未通过"
                    : blockers.stream().map(b -> String.valueOf(b.get("columnName")) + ":" + b.get("message"))
                            .reduce((a, b) -> a + "; " + b).orElse("落标门禁未通过");
            throw new IllegalStateException("字段标准强制门禁阻断：" + detail);
        }
    }

    /**
     * Pipeline publish gate: columns that match ACTIVE enforce_gate standards must pass value-domain
     * checks on the sample rows (pattern/enum/maxLength). Does not require catalog bindings.
     */
    public void assertPipelineColumns(List<String> columns, List<Map<String, Object>> rows) {
        if (columns == null || columns.isEmpty()) return;
        List<Map<String, Object>> enforced = mapper.list().stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(String.valueOf(s.get("status"))))
                .filter(s -> truthy(s.get("enforceGate")))
                .map(this::enrich)
                .toList();
        if (enforced.isEmpty()) return;
        List<String> blockers = new ArrayList<>();
        for (Map<String, Object> standard : enforced) {
            String matchedCol = null;
            for (String col : columns) {
                if (matchByName(List.of(standard), col) != null) {
                    matchedCol = col;
                    break;
                }
            }
            if (matchedCol == null) continue;
            List<String> values = new ArrayList<>();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    if (values.size() >= 200) break;
                    Object v = row.get(matchedCol);
                    values.add(v == null ? "" : String.valueOf(v));
                }
            }
            if (values.isEmpty()) continue;
            Map<String, Object> check = sampleValidateInternal(standard, values);
            if (!Boolean.TRUE.equals(check.get("passed"))) {
                blockers.add(standard.get("standardCode") + "@" + matchedCol);
            }
        }
        if (!blockers.isEmpty())
            throw new IllegalStateException("管道发布字段标准门禁阻断：" + String.join(", ", blockers));
    }

    /** Value-domain sample check against pattern / enum / maxLength (does not scan source tables). */
    public Map<String, Object> sampleValidate(Long standardId, SampleValidateRequest request) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> standard = enrich(get(standardId));
        List<String> values = request == null || request.values() == null ? List.of() : request.values().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .limit(500)
                .toList();
        if (values.isEmpty()) throw new IllegalArgumentException("请提供 1～500 个抽样值");
        Map<String, Object> out = sampleValidateInternal(standard, values);
        out.put("standardId", standardId);
        out.put("note", "值域抽样为调用方提供样本的离线校验；不直连业务源表。");
        return out;
    }

    /**
     * S3-02: pull a bounded JDBC sample from the COLUMN's parent TABLE via its connector datasource,
     * then run the same value-domain checks as offline sample-validate.
     */
    public Map<String, Object> onlineSampleValidate(Long assetId, Integer limit) {
        capabilities.require("bi:resource:admin");
        if (assetId == null || assetId < 1) throw new IllegalArgumentException("请指定列资产");
        int rowLimit = limit == null ? 100 : Math.min(500, Math.max(1, limit));
        Map<String, Object> ctx = mapper.columnSampleContext(assetId);
        if (ctx == null) throw new IllegalArgumentException("资产不存在");
        if (!"COLUMN".equals(String.valueOf(ctx.get("assetType"))))
            throw new IllegalArgumentException("仅 COLUMN 资产支持源表在线抽样");
        String columnName = String.valueOf(ctx.get("columnName") == null ? "" : ctx.get("columnName")).trim();
        String tableName = String.valueOf(ctx.get("tableName") == null ? "" : ctx.get("tableName")).trim();
        if (columnName.isBlank() || tableName.isBlank() || !"TABLE".equals(String.valueOf(ctx.get("parentAssetType"))))
            throw new IllegalStateException("列资产缺少 TABLE 父节点，无法定位源表");
        Long sourceId = number(ctx.get("sourceId"));
        if (sourceId == null || sourceId < 1) throw new IllegalStateException("列资产未绑定数据源");
        resources.require("datasource", sourceId, "model");
        BiDatasource source = sources.getById(sourceId);
        if (source == null) throw new IllegalArgumentException("数据源不存在");
        if (source.getJdbcUrl() == null || source.getJdbcUrl().isBlank())
            throw new IllegalStateException("数据源缺少 JDBC URL");

        Map<String, Object> binding = mapper.bindingByAsset(assetId);
        Map<String, Object> standard;
        if (binding != null && binding.get("standardId") instanceof Number sid) {
            standard = enrich(get(sid.longValue()));
        } else {
            List<Map<String, Object>> standards = mapper.list().stream().map(this::enrich)
                    .filter(s -> "ACTIVE".equals(String.valueOf(s.get("status")))).toList();
            standard = matchByName(standards, columnName);
            if (standard == null)
                throw new IllegalStateException("列未绑定字段标准且名称/别名未命中 ACTIVE 标准");
        }

        List<String> values = fetchJdbcSample(source, tableName, columnName, rowLimit);
        Map<String, Object> out = sampleValidateInternal(standard, values);
        out.put("assetId", assetId);
        out.put("standardId", standard.get("id"));
        out.put("sourceId", sourceId);
        out.put("tableName", tableName);
        out.put("columnName", columnName);
        out.put("mode", "JDBC_ONLINE");
        out.put("note", "从源表在线抽取最多 " + rowLimit + " 行后做值域校验；标识符已安全引用，不执行调用方 SQL。");
        return out;
    }

    private List<String> fetchJdbcSample(BiDatasource source, String tableName, String columnName, int limit) {
        String type = source.getType() == null ? "" : source.getType().toLowerCase(Locale.ROOT);
        String schema = source.getSourceSchema() == null ? "" : source.getSourceSchema().trim();
        String sql = buildSampleSql(type, schema, tableName, columnName, limit);
        List<String> values = new ArrayList<>();
        try (Connection connection = JdbcConnectionSettings.open(source);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(limit);
            statement.setQueryTimeout(30);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next() && values.size() < limit) {
                    Object raw = rs.getObject(1);
                    values.add(raw == null ? "" : String.valueOf(raw));
                }
            }
        } catch (Exception failure) {
            throw new IllegalStateException("源表 JDBC 抽样失败：" + safeMessage(failure), failure);
        }
        return values;
    }

    private static String buildSampleSql(String type, String schema, String table, String column, int limit) {
        String qTable = quoteIdent(type, table);
        String qColumn = quoteIdent(type, column);
        String from = schema.isBlank() ? qTable : quoteIdent(type, schema) + "." + qTable;
        if ("oracle".equals(type)) {
            return "SELECT " + qColumn + " FROM " + from + " WHERE ROWNUM <= " + limit;
        }
        if ("sqlserver".equals(type) || "mssql".equals(type)) {
            return "SELECT TOP " + limit + " " + qColumn + " FROM " + from;
        }
        return "SELECT " + qColumn + " FROM " + from + " LIMIT " + limit;
    }

    private static String quoteIdent(String type, String ident) {
        if (ident == null || ident.isBlank()) throw new IllegalArgumentException("标识符无效");
        if (!ident.matches("[A-Za-z_][A-Za-z0-9_$#]*"))
            throw new IllegalArgumentException("标识符含非法字符，拒绝抽样：" + ident);
        if ("postgresql".equals(type) || "pg".equals(type)) return "\"" + ident.replace("\"", "\"\"") + "\"";
        if ("sqlserver".equals(type) || "mssql".equals(type)) return "[" + ident.replace("]", "]]") + "]";
        if ("oracle".equals(type)) return "\"" + ident.replace("\"", "\"\"") + "\"";
        return "`" + ident.replace("`", "``") + "`";
    }

    private static String safeMessage(Throwable failure) {
        String msg = failure.getMessage();
        if (msg == null || msg.isBlank()) return failure.getClass().getSimpleName();
        return msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
    }

    private Map<String, Object> sampleValidateInternal(Map<String, Object> standard, List<String> values) {
        String pattern = standard.get("valuePattern") == null ? null : String.valueOf(standard.get("valuePattern"));
        Pattern compiled = null;
        if (pattern != null && !pattern.isBlank() && !"null".equals(pattern)) {
            try { compiled = Pattern.compile(pattern); }
            catch (Exception ex) { throw new IllegalArgumentException("标准正则无效"); }
        }
        List<String> enums = standard.get("enumValues") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        Integer maxLength = standard.get("maxLength") instanceof Number n ? n.intValue() : null;
        String nullable = String.valueOf(standard.getOrDefault("nullablePolicy", "OPTIONAL"));
        int ok = 0, fail = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (String raw : values) {
            String value = raw == null ? "" : raw;
            List<String> reasons = new ArrayList<>();
            if (value.isEmpty()) {
                if ("REQUIRED".equalsIgnoreCase(nullable)) reasons.add("空值违反 REQUIRED");
            } else {
                if (maxLength != null && value.length() > maxLength) reasons.add("超过 maxLength=" + maxLength);
                if (compiled != null && !compiled.matcher(value).matches()) reasons.add("不匹配 valuePattern");
                if (!enums.isEmpty() && enums.stream().noneMatch(e -> e.equals(value))) reasons.add("不在枚举域");
            }
            if (reasons.isEmpty()) ok++;
            else {
                fail++;
                if (failures.size() < 20) {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("value", value.length() > 80 ? value.substring(0, 80) + "…" : value);
                    hit.put("reasons", reasons);
                    failures.add(hit);
                }
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sampled", values.size());
        summary.put("passed", ok);
        summary.put("failed", fail);
        summary.put("passRate", values.isEmpty() ? 0 : (ok * 100.0 / values.size()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("standardCode", standard.get("standardCode"));
        out.put("passed", fail == 0);
        out.put("summary", summary);
        out.put("failures", failures);
        return out;
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private Map<String, Object> matchByName(List<Map<String, Object>> standards, String columnName) {
        String needle = columnName == null ? "" : columnName.trim().toLowerCase(Locale.ROOT);
        if (needle.isBlank()) return null;
        for (Map<String, Object> s : standards) {
            if (needle.equals(String.valueOf(s.get("name")).toLowerCase(Locale.ROOT))
                    || needle.equals(String.valueOf(s.get("standardCode")).toLowerCase(Locale.ROOT))) {
                return s;
            }
            Object aliases = s.get("matchAliases");
            if (aliases instanceof List<?> list) {
                for (Object a : list) {
                    if (a != null && needle.equals(String.valueOf(a).trim().toLowerCase(Locale.ROOT))) return s;
                }
            }
        }
        return null;
    }

    private Map<String, Object> validate(UpsertRequest request, boolean creating) {
        if (request == null) throw new IllegalArgumentException("请求不能为空");
        String code = request.standardCode() == null ? "" : request.standardCode().trim();
        if (creating && !CODE.matcher(code).matches()) throw new IllegalArgumentException("标准编码须为 2～96 位字母数字_-");
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank() || name.length() > 160) throw new IllegalArgumentException("名称须为 1～160 字");
        String family = request.dataTypeFamily() == null ? "" : request.dataTypeFamily().trim().toUpperCase(Locale.ROOT);
        if (!FAMILIES.contains(family)) throw new IllegalArgumentException("类型族须为 STRING/NUMBER/DATETIME/BOOLEAN");
        String nullable = request.nullablePolicy() == null ? "OPTIONAL"
                : request.nullablePolicy().trim().toUpperCase(Locale.ROOT);
        if (!NULLABLE.contains(nullable)) throw new IllegalArgumentException("可空策略无效");
        String status = request.status() == null ? "ACTIVE" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("状态无效");
        Integer maxLength = request.maxLength();
        if (maxLength != null && (maxLength < 1 || maxLength > 65535)) throw new IllegalArgumentException("最大长度无效");
        String pattern = blankToNull(request.valuePattern(), 256);
        if (pattern != null) {
            try { Pattern.compile(pattern); } catch (Exception ex) { throw new IllegalArgumentException("正则无效"); }
        }
        Long glossaryTermId = request.glossaryTermId();
        if (glossaryTermId != null && mapper.glossaryExists(glossaryTermId) == null)
            throw new IllegalArgumentException("关联术语不存在");
        Long metricId = request.metricId();
        if (metricId != null && mapper.metricExists(metricId) == null)
            throw new IllegalArgumentException("关联指标不存在");
        Map<String, Object> row = new HashMap<>();
        if (creating) row.put("standardCode", code);
        row.put("domainCode", normalizeDomain(request.domainCode()));
        row.put("name", name);
        row.put("dataTypeFamily", family);
        row.put("maxLength", maxLength);
        row.put("nullablePolicy", nullable);
        row.put("valuePattern", pattern);
        row.put("enumValuesJson", writeList(request.enumValues()));
        row.put("matchAliasesJson", writeList(request.matchAliases()));
        row.put("glossaryTermId", glossaryTermId);
        row.put("metricId", metricId);
        row.put("description", blankToNull(request.description(), 1000));
        row.put("status", status);
        row.put("enforceGate", Boolean.TRUE.equals(request.enforceGate()) ? 1 : 0);
        return row;
    }

    private Map<String, Object> enrich(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("enumValues", readList(row.get("enumValuesJson")));
        out.put("matchAliases", readList(row.get("matchAliasesJson")));
        out.put("enforceGate", truthy(row.get("enforceGate")));
        return out;
    }

    private String writeList(List<String> values) {
        try {
            List<String> cleaned = values == null ? List.of() : values.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .distinct()
                    .limit(50)
                    .toList();
            return json.writeValueAsString(cleaned);
        } catch (Exception ex) {
            throw new IllegalArgumentException("列表字段序列化失败");
        }
    }

    private List<String> readList(Object raw) {
        if (raw == null) return List.of();
        try {
            return json.readValue(String.valueOf(raw), new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String extractType(Object metadataJson) {
        if (metadataJson == null) return null;
        try {
            Map<String, Object> meta = json.readValue(String.valueOf(metadataJson), new TypeReference<>() {});
            Object type = meta.get("type");
            if (type == null) type = meta.get("dataType");
            return type == null ? null : String.valueOf(type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String familyOf(String jdbcOrNativeType) {
        if (jdbcOrNativeType == null || jdbcOrNativeType.isBlank()) return null;
        String t = jdbcOrNativeType.trim().toUpperCase(Locale.ROOT);
        if (t.contains("BOOL") || t.equals("BIT")) return "BOOLEAN";
        if (t.contains("DATE") || t.contains("TIME") || t.contains("TIMESTAMP")) return "DATETIME";
        if (t.contains("INT") || t.contains("DECIMAL") || t.contains("NUMERIC") || t.contains("FLOAT")
                || t.contains("DOUBLE") || t.contains("REAL") || t.contains("MONEY") || t.contains("NUMBER"))
            return "NUMBER";
        return "STRING";
    }

    private static String blankToNull(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new IllegalArgumentException("文本超长");
        return trimmed;
    }

    private static Long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeDomain(String domainCode) {
        String domain = domainCode == null || domainCode.isBlank() ? "DEFAULT" : domainCode.trim().toUpperCase(Locale.ROOT);
        if (!domain.matches("[A-Z0-9][A-Z0-9_-]{0,63}")) throw new IllegalArgumentException("业务域编码无效");
        return domain;
    }

    private void validateBindPlanEntries(List<BindPlanEntry> bindings) {
        Set<Long> assets = new HashSet<>();
        for (BindPlanEntry entry : bindings) {
            if (entry == null || entry.standardId() == null || entry.assetId() == null)
                throw new IllegalArgumentException("绑定项须含 standardId 与 assetId");
            if (!assets.add(entry.assetId())) throw new IllegalArgumentException("同一计划内 assetId 重复");
            if (mapper.find(entry.standardId()) == null) throw new IllegalArgumentException("字段标准不存在");
            Map<String, Object> asset = mapper.asset(entry.assetId());
            if (asset == null || !"COLUMN".equalsIgnoreCase(String.valueOf(asset.get("assetType"))))
                throw new IllegalArgumentException("仅支持绑定 COLUMN 资产");
            if (entry.columnName() != null && !entry.columnName().isBlank()
                    && !entry.columnName().equalsIgnoreCase(String.valueOf(asset.get("name"))))
                throw new IllegalArgumentException("columnName 与资产列名不一致");
        }
    }

    private String writeBindingsJson(List<BindPlanEntry> bindings) {
        try {
            List<Map<String, Object>> payload = new ArrayList<>();
            for (BindPlanEntry entry : bindings) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("standardId", entry.standardId());
                item.put("assetId", entry.assetId());
                if (entry.columnName() != null && !entry.columnName().isBlank())
                    item.put("columnName", entry.columnName().trim());
                payload.add(item);
            }
            return json.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("绑定计划序列化失败");
        }
    }

    private List<BindPlanEntry> readBindPlanEntries(Object raw) {
        if (raw == null) return List.of();
        try {
            List<?> list = json.readValue(String.valueOf(raw), List.class);
            List<BindPlanEntry> entries = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Long standardId = number(map.get("standardId"));
                Long assetId = number(map.get("assetId"));
                String columnName = map.get("columnName") == null ? null : String.valueOf(map.get("columnName"));
                entries.add(new BindPlanEntry(standardId, assetId, columnName));
            }
            return entries;
        } catch (Exception ex) {
            throw new IllegalArgumentException("绑定计划解析失败");
        }
    }

    private Map<String, Object> enrichBindPlan(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("bindings", readBindPlanEntries(row.get("bindingsJson")));
        return out;
    }
}
