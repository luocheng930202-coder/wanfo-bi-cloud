package com.wanfo.bi.service;

import com.wanfo.bi.domain.BiMetricDO;
import com.wanfo.bi.mapper.BiMetricMapper;
import com.wanfo.bi.mapper.DataApiCallerMapper;
import com.wanfo.bi.mapper.DataApiMapper;
import com.wanfo.bi.security.ActorScope;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.DatasetDataPolicy;
import com.wanfo.bi.security.LiveActorResolver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Controlled read-only service APIs backed by frozen published metrics. */
@Service
public class DataApiService
{
    public record SaveRequest(String code,String name,Long metricId,Integer apiVersion,Integer maxPageSize,Integer timeoutSeconds,Integer callsPerMinute,Integer dailyQuota,String familyCode) {
        public SaveRequest(String code,String name,Long metricId,Integer apiVersion,Integer maxPageSize,Integer timeoutSeconds,Integer callsPerMinute,Integer dailyQuota) {
            this(code,name,metricId,apiVersion,maxPageSize,timeoutSeconds,callsPerMinute,dailyQuota,null);
        }
    }
    public record DeprecateRequest(Integer sunsetDays, String successorApiCode, String note) { }
    public record RotateRequest(Integer expiresInDays, Long callerId) { }
    public record CallerSaveRequest(String orgCode, String orgName, String contactName, String contactEmail, String notes,
            String status, Integer callsPerMinute, Integer dailyQuota, Integer canaryWeight) {
        public CallerSaveRequest(String orgCode, String orgName, String contactName, String contactEmail, String notes, String status) {
            this(orgCode, orgName, contactName, contactEmail, notes, status, null, null, null);
        }
        public CallerSaveRequest(String orgCode, String orgName, String contactName, String contactEmail, String notes,
                String status, Integer callsPerMinute, Integer dailyQuota) {
            this(orgCode, orgName, contactName, contactEmail, notes, status, callsPerMinute, dailyQuota, null);
        }
    }
    public record AssignCallerRequest(Long callerId) { }
    /** Branch a coexisting next contract version under the same family (old ACTIVE remains until deprecate). */
    public record NextVersionRequest(String newApiCode, String name, Long metricId, Integer apiVersion) { }
    public record AutoDeprecateRequest(Integer sunsetDays, String note) { }
    public record CanaryRequest(Integer percent) { }
    private final DataApiMapper mapper; private final DataApiCallerMapper callers; private final BiMetricMapper metricsMapper; private final MetricDefinitionService metrics;
    private final CapabilityProvider capabilities; private final CurrentActorProvider actors; private final LiveActorResolver live;
    private final BiResourceAuthorizer resources; private final CatalogAuditService audit; private final ObjectMapper json;
    @Autowired(required = false)
    private LineageRebuildTrigger lineageRebuild;
    private final SecureRandom random=new SecureRandom();
    private final int callSampleRatePercent;
    public DataApiService(DataApiMapper mapper,DataApiCallerMapper callers,BiMetricMapper metricsMapper,MetricDefinitionService metrics,CapabilityProvider capabilities,
            CurrentActorProvider actors,LiveActorResolver live,BiResourceAuthorizer resources,CatalogAuditService audit,ObjectMapper json,
            @Value("${bi.data-api.call-sample-rate:10}") int callSampleRatePercent)
    { this.mapper=mapper;this.callers=callers;this.metricsMapper=metricsMapper;this.metrics=metrics;this.capabilities=capabilities;this.actors=actors;this.live=live;this.resources=resources;this.audit=audit;this.json=json;
        this.callSampleRatePercent=Math.max(0,Math.min(100,callSampleRatePercent)); }

    public List<Map<String,Object>> list(){
        if(capabilities.has("bi:resource:admin"))return mapper.list().stream().map(this::safe).toList();
        capabilities.require("bi:metric:list");
        return mapper.list().stream().filter(api->{
            Long metricId=number(api.get("metricId"));
            Long apiId=number(api.get("id"));
            return metricId!=null&&resources.allowed("metric",metricId,"view")
                    &&(resources.allowed("data_api",apiId,"view")||resources.allowed("data_api",apiId,"manage"));
        }).map(this::safe).toList();
    }

    /**
     * S3-03: lifecycle directory + 30d call summary + org-level callers + key prefixes.
     */
    public Map<String, Object> portal() {
        List<Map<String, Object>> apis = list();
        Map<Long, Map<String, Object>> stats = new LinkedHashMap<>();
        for (Map<String, Object> row : mapper.callStats30d()) {
            Long apiId = number(row.get("apiId"));
            if (apiId != null) stats.put(apiId, row);
        }
        Map<Long, List<Map<String, Object>>> keyCallers = new LinkedHashMap<>();
        for (Map<String, Object> row : mapper.callerKeyPrefixes30d()) {
            Long apiId = number(row.get("apiId"));
            if (apiId == null) continue;
            keyCallers.computeIfAbsent(apiId, ignored -> new ArrayList<>()).add(Map.of(
                    "keyPrefix", String.valueOf(row.getOrDefault("keyPrefix", "")),
                    "callCount30d", row.getOrDefault("callCount30d", 0),
                    "lastCalledAt", row.get("lastCalledAt") == null ? "" : String.valueOf(row.get("lastCalledAt"))));
        }
        Map<Long, List<Map<String, Object>>> orgCallers = new LinkedHashMap<>();
        for (Map<String, Object> row : callers.callerOrgStats30d()) {
            Long apiId = number(row.get("apiId"));
            if (apiId == null) continue;
            Map<String, Object> org = new LinkedHashMap<>();
            org.put("callerId", row.get("callerId"));
            org.put("orgCode", row.getOrDefault("orgCode", ""));
            org.put("orgName", row.getOrDefault("orgName", ""));
            org.put("callCount30d", row.getOrDefault("callCount30d", 0));
            org.put("keyCount30d", row.getOrDefault("keyCount30d", 0));
            org.put("lastCalledAt", row.get("lastCalledAt") == null ? "" : String.valueOf(row.get("lastCalledAt")));
            orgCallers.computeIfAbsent(apiId, ignored -> new ArrayList<>()).add(org);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        int deprecated = 0, active = 0, draft = 0, disabled = 0, pastSunset = 0;
        for (Map<String, Object> api : apis) {
            Map<String, Object> item = new LinkedHashMap<>(api);
            Long id = number(api.get("id"));
            Map<String, Object> st = id == null ? null : stats.get(id);
            item.put("callCount30d", st == null ? 0 : st.getOrDefault("callCount30d", 0));
            item.put("successCount30d", st == null ? 0 : st.getOrDefault("successCount30d", 0));
            item.put("distinctKeys30d", st == null ? 0 : st.getOrDefault("distinctKeys30d", 0));
            item.put("lastCalledAt", st == null ? null : st.get("lastCalledAt"));
            List<Map<String, Object>> keys = id == null ? List.of() : keyCallers.getOrDefault(id, List.of());
            item.put("callerKeyPrefixes", keys.size() > 8 ? keys.subList(0, 8) : keys);
            List<Map<String, Object>> orgs = id == null ? List.of() : orgCallers.getOrDefault(id, List.of());
            item.put("callerOrgs", orgs.size() > 8 ? orgs.subList(0, 8) : orgs);
            boolean sunsetPassed = pastSunset(api);
            item.put("pastSunset", sunsetPassed);
            String status = String.valueOf(api.get("status"));
            switch (status) {
                case "ACTIVE" -> active++;
                case "DEPRECATED" -> { deprecated++; if (sunsetPassed) pastSunset++; }
                case "DRAFT" -> draft++;
                case "DISABLED" -> disabled++;
                default -> { }
            }
            items.add(item);
        }
        items.sort((a, b) -> {
            int ra = lifecycleRank(String.valueOf(a.get("status")));
            int rb = lifecycleRank(String.valueOf(b.get("status")));
            if (ra != rb) return Integer.compare(ra, rb);
            return Long.compare(number(b.get("id")) == null ? 0 : number(b.get("id")),
                    number(a.get("id")) == null ? 0 : number(a.get("id")));
        });
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", items.size());
        summary.put("active", active);
        summary.put("deprecated", deprecated);
        summary.put("pastSunset", pastSunset);
        summary.put("draft", draft);
        summary.put("disabled", disabled);
        summary.put("registeredCallers", callers.list().size());
        List<Map<String, Object>> families = buildFamilies(items);
        summary.put("families", families.size());
        summary.put("multiActiveFamilies", families.stream()
                .filter(f -> ((Number) f.getOrDefault("activeCount", 0)).intValue() > 1).count());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("items", items);
        result.put("families", families);
        result.put("note", "同 family 可多 ACTIVE 并存；用「下一版本」拉出草稿，旧码 deprecate→sunset。Key 可绑调用方组织。");
        return result;
    }

    private static List<Map<String, Object>> buildFamilies(List<Map<String, Object>> items) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String family = String.valueOf(item.getOrDefault("familyCode", item.get("apiCode")));
            if (family == null || family.isBlank() || "null".equals(family))
                family = String.valueOf(item.get("apiCode"));
            grouped.computeIfAbsent(family, ignored -> new ArrayList<>()).add(item);
        }
        List<Map<String, Object>> families = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<Map<String, Object>> members = entry.getValue();
            int activeCount = 0, draftCount = 0, deprecatedCount = 0;
            List<Map<String, Object>> versions = new ArrayList<>();
            for (Map<String, Object> m : members) {
                String status = String.valueOf(m.get("status"));
                switch (status) {
                    case "ACTIVE" -> activeCount++;
                    case "DRAFT" -> draftCount++;
                    case "DEPRECATED" -> deprecatedCount++;
                    default -> { }
                }
                Map<String, Object> ver = new LinkedHashMap<>();
                ver.put("id", m.get("id"));
                ver.put("apiCode", m.get("apiCode"));
                ver.put("apiVersion", m.get("apiVersion"));
                ver.put("status", status);
                ver.put("name", m.get("name"));
                versions.add(ver);
            }
            Map<String, Object> family = new LinkedHashMap<>();
            family.put("familyCode", entry.getKey());
            family.put("activeCount", activeCount);
            family.put("draftCount", draftCount);
            family.put("deprecatedCount", deprecatedCount);
            family.put("coexisting", activeCount > 1);
            family.put("versions", versions);
            families.add(family);
        }
        families.sort((a, b) -> Integer.compare(
                ((Number) b.getOrDefault("activeCount", 0)).intValue(),
                ((Number) a.getOrDefault("activeCount", 0)).intValue()));
        return families;
    }

    /**
     * S3-03 contract gate: probe frozen metric response shape; if another API named this code as successor
     * and froze a schema, block removed columns / incompatible type kinds.
     */
    public Map<String, Object> contractCheck(Long id) {
        Map<String, Object> api = require(id);
        requireManage(api);
        DataApiContractGate.Report report = runContractCheck(api);
        Map<String, Object> result = DataApiContractGate.reportMap(report, json);
        result.put("apiId", id);
        result.put("apiCode", api.get("apiCode"));
        result.put("status", api.get("status"));
        if (report.baseline() != null) result.put("baselineSource", "successor");
        return result;
    }

    /**
     * Canary / replay: re-probe live metric shape against this API's frozen contract (not successor baseline).
     * Detects silent metric drift after publish.
     */
    public Map<String, Object> contractReplay(Long id) {
        Map<String, Object> api = require(id);
        requireManage(api);
        if (!Set.of("ACTIVE", "DEPRECATED").contains(String.valueOf(api.get("status"))))
            throw new IllegalStateException("仅已发布或废弃期内的 API 可做契约回放");
        Object frozen = api.get("contractSchemaJson");
        if (frozen == null || String.valueOf(frozen).isBlank())
            throw new IllegalStateException("该 API 无冻结契约，请先发布或重新发布");
        DataApiContractGate.Report live = runContractCheck(api);
        DataApiContractGate.Schema baseline = DataApiContractGate.fromJson(String.valueOf(frozen), json);
        DataApiContractGate.Report replay = DataApiContractGate.compare(live.proposed(), baseline, live.sampleRows());
        Map<String, Object> result = DataApiContractGate.reportMap(replay, json);
        result.put("apiId", id);
        result.put("apiCode", api.get("apiCode"));
        result.put("mode", "REPLAY_FROZEN_CONTRACT");
        result.put("baselineSource", "self");
        audit.record("DATA_API_CONTRACT_REPLAY", "DATA_API", id, null,
                Map.of("passed", replay.passed(), "blocking", replay.issues().stream().filter(i -> "BLOCKING".equals(i.severity())).count()));
        return result;
    }

    /**
     * Replay against the anonymized publish-time sample payload (kinds + truncated cells), without live metric probe.
     * Useful when the metric source is temporarily unavailable or to inspect historical shape.
     */
    public Map<String, Object> contractSampleReplay(Long id) {
        Map<String, Object> api = require(id);
        requireManage(api);
        if (!Set.of("ACTIVE", "DEPRECATED").contains(String.valueOf(api.get("status"))))
            throw new IllegalStateException("仅已发布或废弃期内的 API 可回放发布样本");
        Object sampleRaw = api.get("contractSampleJson");
        if (sampleRaw == null || String.valueOf(sampleRaw).isBlank())
            throw new IllegalStateException("该 API 无发布样本；请重新发布或调用 refresh-contract-sample");
        Object frozen = api.get("contractSchemaJson");
        if (frozen == null || String.valueOf(frozen).isBlank())
            throw new IllegalStateException("该 API 无冻结契约");
        Map<String, Object> sample = parseSampleJson(String.valueOf(sampleRaw));
        DataApiContractGate.Schema proposed = schemaFromSample(sample, number(api.get("metricId")),
                number(api.get("metricPublishedVersion")));
        DataApiContractGate.Schema baseline = DataApiContractGate.fromJson(String.valueOf(frozen), json);
        int rows = sample.get("rows") instanceof List<?> list ? list.size() : 0;
        DataApiContractGate.Report replay = DataApiContractGate.compare(proposed, baseline, rows);
        Map<String, Object> result = DataApiContractGate.reportMap(replay, json);
        result.put("apiId", id);
        result.put("apiCode", api.get("apiCode"));
        result.put("mode", "REPLAY_STORED_SAMPLE");
        result.put("baselineSource", "self");
        result.put("sampleAt", api.get("contractSampleAt"));
        result.put("sample", sample);
        audit.record("DATA_API_CONTRACT_SAMPLE_REPLAY", "DATA_API", id, null,
                Map.of("passed", replay.passed(), "sampleRows", rows));
        return result;
    }

    /** Re-probe live metric and refresh anonymized sample without republishing. */
    @Transactional
    public Map<String, Object> refreshContractSample(Long id) {
        Map<String, Object> api = require(id);
        requireManage(api);
        if (!Set.of("ACTIVE", "DEPRECATED").contains(String.valueOf(api.get("status"))))
            throw new IllegalStateException("仅已发布或废弃期内的 API 可刷新样本");
        ProbeBundle probe = probeMetric(api);
        String sampleJson = json.writeValueAsString(anonymizeSample(probe.sample()));
        if (mapper.saveContractSample(id, sampleJson, actors.currentActor().username()) != 1)
            throw new IllegalStateException("API 状态已变化");
        audit.record("DATA_API_CONTRACT_SAMPLE_REFRESH", "DATA_API", id, null,
                Map.of("sampleRows", probe.sampleRows()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("apiId", id);
        out.put("refreshed", true);
        out.put("sampleRows", probe.sampleRows());
        out.put("sample", anonymizeSample(probe.sample()));
        return out;
    }

    /**
     * Mark this ACTIVE API as family canary: {@code percent}% of traffic hitting the stable (lowest-version ACTIVE)
     * code is routed here. Only one canary per family; percent 0 clears.
     */
    @Transactional
    public Map<String, Object> setCanary(Long id, CanaryRequest request) {
        Map<String, Object> api = require(id);
        requireManage(api);
        if (!"ACTIVE".equals(api.get("status"))) throw new IllegalStateException("仅已发布 API 可设置金丝雀");
        int percent = request == null || request.percent() == null ? 0 : request.percent();
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("金丝雀比例须为 0～100");
        String family = String.valueOf(api.get("familyCode"));
        if (family == null || family.isBlank() || "null".equals(family)) family = String.valueOf(api.get("apiCode"));
        String user = actors.currentActor().username();
        mapper.clearOtherCanaries(family, id, user);
        if (mapper.setCanaryPercent(id, percent, user) != 1) throw new IllegalStateException("API 状态已变化");
        audit.record("DATA_API_CANARY", "DATA_API", id, null, Map.of("familyCode", family, "percent", percent));
        Map<String, Object> out = get(id);
        out.put("canaryPercent", percent);
        out.put("familyCode", family);
        out.put("note", percent == 0 ? "已关闭金丝雀" : "命中 family 稳定编码时约 " + percent + "% 流量路由到本版本");
        return out;
    }

    /** After a successor is ACTIVE, deprecate other ACTIVE siblings in the same family pointing here. */
    @Transactional
    public Map<String, Object> autoDeprecateSiblings(Long id, AutoDeprecateRequest request) {
        Map<String, Object> successor = require(id);
        requireManage(successor);
        if (!"ACTIVE".equals(successor.get("status")))
            throw new IllegalStateException("仅已发布 API 可作为自动废弃的继任编码");
        String family = String.valueOf(successor.get("familyCode"));
        if (family == null || family.isBlank() || "null".equals(family))
            family = String.valueOf(successor.get("apiCode"));
        String successorCode = String.valueOf(successor.get("apiCode"));
        int days = request == null || request.sunsetDays() == null ? 90 : request.sunsetDays();
        if (days < 1 || days > 3650) throw new IllegalArgumentException("下线通知期须为 1～3650 天");
        String note = request == null || request.note() == null || request.note().isBlank()
                ? "自动废弃编排：同 family 已切换至 " + successorCode
                : request.note().trim();
        List<Map<String, Object>> deprecated = new ArrayList<>();
        for (Map<String, Object> sibling : mapper.listByFamily(family)) {
            if (!"ACTIVE".equals(sibling.get("status"))) continue;
            if (id.equals(number(sibling.get("id")))) continue;
            Long siblingId = number(sibling.get("id"));
            deprecate(siblingId, new DeprecateRequest(days, successorCode, note));
            deprecated.add(Map.of("id", siblingId, "apiCode", String.valueOf(sibling.get("apiCode")),
                    "apiVersion", sibling.get("apiVersion") == null ? 0 : sibling.get("apiVersion")));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("successorApiId", id);
        out.put("successorApiCode", successorCode);
        out.put("familyCode", family);
        out.put("deprecatedCount", deprecated.size());
        out.put("deprecated", deprecated);
        out.put("sunsetDays", days);
        audit.record("DATA_API_AUTO_DEPRECATE", "DATA_API", id, null,
                Map.of("familyCode", family, "deprecatedCount", deprecated.size(), "sunsetDays", days));
        return out;
    }

    private static int lifecycleRank(String status) {
        return switch (status) {
            case "DEPRECATED" -> 0;
            case "ACTIVE" -> 1;
            case "DRAFT" -> 2;
            case "DISABLED" -> 3;
            default -> 9;
        };
    }
    public Map<String,Object> get(Long id){
        Map<String,Object> api=require(id);requireManage(api);Map<String,Object> result=safe(api);result.put("keys",mapper.keys(id));result.put("calls",mapper.calls(id));result.put("revisions",mapper.revisions(id));return result;
    }

    @Transactional
    public Map<String,Object> create(SaveRequest request){
        requirePublisher();validate(request);BiMetricDO metric=publishedMetric(request.metricId());authorizeMetric(metric);
        String code=request.code().trim();
        String family=resolveFamilyCode(request.familyCode(),code);
        String actor=actors.currentActor().username();
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("code",code);row.put("familyCode",family);row.put("name",request.name().trim());row.put("metricId",request.metricId());
        row.put("apiVersion",bounded(request.apiVersion(),1,1,9999,"API 版本"));
        row.put("pageSize",bounded(request.maxPageSize(),100,1,1000,"单页上限"));
        row.put("timeout",bounded(request.timeoutSeconds(),20,1,120,"超时"));
        row.put("rate",bounded(request.callsPerMinute(),60,1,10000,"每分钟配额"));
        row.put("quota",bounded(request.dailyQuota(),10000,1,1000000,"每日配额"));
        row.put("actor",json.writeValueAsString(actors.currentActor()));row.put("user",actor);
        if(mapper.insert(row)!=1)throw new IllegalStateException("数据 API 创建失败");
        Long id=number(row.get("id"));
        resources.register("data_api",id);
        revision(id,"CREATE");
        audit.record("DATA_API_CREATE","DATA_API",id,null,Map.of("metricId",request.metricId(),"familyCode",family));
        return get(id);
    }

    @Transactional
    public Map<String,Object> update(Long id,SaveRequest request){
        Map<String,Object> current=require(id);requireManage(current);
        if(!"DRAFT".equals(current.get("status")))throw new IllegalStateException("发布或停用的 API 不可修改；请用「下一版本」拉出草稿");
        validate(request);BiMetricDO metric=publishedMetric(request.metricId());authorizeMetric(metric);
        String family=resolveFamilyCode(request.familyCode(),
                request.familyCode()!=null&&!request.familyCode().isBlank()?request.familyCode():String.valueOf(current.get("familyCode")));
        if(family==null||family.isBlank()||"null".equals(family)) family=String.valueOf(current.get("apiCode"));
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("id",id);row.put("name",request.name().trim());row.put("metricId",request.metricId());
        row.put("familyCode",family);
        row.put("apiVersion",bounded(request.apiVersion(),1,1,9999,"API 版本"));
        row.put("pageSize",bounded(request.maxPageSize(),100,1,1000,"单页上限"));
        row.put("timeout",bounded(request.timeoutSeconds(),20,1,120,"超时"));
        row.put("rate",bounded(request.callsPerMinute(),60,1,10000,"每分钟配额"));
        row.put("quota",bounded(request.dailyQuota(),10000,1,1000000,"每日配额"));
        row.put("user",actors.currentActor().username());
        if(mapper.update(row)!=1)throw new IllegalStateException("数据 API 状态已变化");
        revision(id,"UPDATE");
        audit.record("DATA_API_UPDATE","DATA_API",id,null,Map.of("metricId",request.metricId(),"familyCode",family));
        return get(id);
    }

    /**
     * Opens a DRAFT next contract under the same family. Existing ACTIVE versions stay live (coexistence).
     * After publishing the draft, operators may deprecate the previous code with successor pointed here.
     */
    @Transactional
    public Map<String,Object> nextVersion(Long id, NextVersionRequest request){
        Map<String,Object> source=require(id);requireManage(source);
        String status=String.valueOf(source.get("status"));
        if(!Set.of("ACTIVE","DEPRECATED").contains(status))
            throw new IllegalStateException("仅已发布或废弃中的 API 可拉出下一版本");
        String family=String.valueOf(source.get("familyCode"));
        if(family==null||family.isBlank()||"null".equals(family)) family=String.valueOf(source.get("apiCode"));
        int nextVer=request!=null&&request.apiVersion()!=null
                ? bounded(request.apiVersion(),1,1,9999,"API 版本")
                : Math.min(9999, mapper.maxApiVersionInFamily(family)+1);
        String newCode;
        if(request!=null&&request.newApiCode()!=null&&!request.newApiCode().isBlank()){
            newCode=request.newApiCode().trim();
            if(!newCode.matches("^[A-Za-z][A-Za-z0-9_-]{1,63}$")) throw new IllegalArgumentException("新 API 编码无效");
        }else{
            newCode=suggestNextCode(family,nextVer);
        }
        if(mapper.byCode(newCode)!=null) throw new IllegalArgumentException("新 API 编码已存在："+newCode);
        Long metricId=request!=null&&request.metricId()!=null?request.metricId():number(source.get("metricId"));
        BiMetricDO metric=publishedMetric(metricId);authorizeMetric(metric);
        String name=request!=null&&request.name()!=null&&!request.name().isBlank()
                ? request.name().trim()
                : String.valueOf(source.get("name"))+" · v"+nextVer;
        String actor=actors.currentActor().username();
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("code",newCode);row.put("familyCode",family);row.put("name",name);row.put("metricId",metricId);
        row.put("apiVersion",nextVer);
        row.put("pageSize",number(source.get("maxPageSize")));
        row.put("timeout",number(source.get("timeoutSeconds")));
        row.put("rate",number(source.get("callsPerMinute")));
        row.put("quota",number(source.get("dailyQuota")));
        row.put("actor",json.writeValueAsString(actors.currentActor()));row.put("user",actor);
        if(mapper.insert(row)!=1) throw new IllegalStateException("下一版本草稿创建失败");
        Long draftId=number(row.get("id"));
        resources.register("data_api",draftId);
        revision(draftId,"NEXT_VERSION");
        audit.record("DATA_API_NEXT_VERSION","DATA_API",draftId,null,
                Map.of("sourceId",id,"familyCode",family,"apiVersion",nextVer,"newApiCode",newCode));
        Map<String,Object> result=get(draftId);
        result.put("sourceApiId",id);
        result.put("sourceApiCode",source.get("apiCode"));
        result.put("coexistenceHint","发布后可与同 family 旧 ACTIVE 并存；迁移完成后再 deprecate 旧编码并填写 successor="+newCode);
        return result;
    }

    @Transactional
    public Map<String,Object> publish(Long id){
        Map<String,Object> api=require(id);requireManage(api);
        if(!"DRAFT".equals(api.get("status")))throw new IllegalStateException("仅草稿 API 可发布");
        BiMetricDO metric=publishedMetric(number(api.get("metricId")));authorizeMetric(metric);
        ProbeBundle probe=probeMetric(api);
        DataApiContractGate.Report report=compareProbe(api, probe);
        if(!report.passed()){
            String detail=report.issues().stream().filter(i->"BLOCKING".equals(i.severity()))
                    .map(DataApiContractGate.Issue::message).reduce((a,b)->a+"; "+b).orElse("契约校验未通过");
            throw new IllegalArgumentException("契约门禁未通过："+detail);
        }
        String contractJson=json.writeValueAsString(DataApiContractGate.toMap(report.proposed(),json));
        String sampleJson=json.writeValueAsString(anonymizeSample(probe.sample()));
        if(mapper.publish(id,metric.getPublishedVersion(),contractJson,sampleJson,actors.currentActor().username())!=1)
            throw new IllegalStateException("API 状态已变化");
        metrics.bind(metric.getId(),new MetricDefinitionService.UsageRequest("data_api",usageRefId(id),"受控数据 API"));
        revision(id,"PUBLISH");
        String key=createKey(id,null,null);
        audit.record("DATA_API_PUBLISH","DATA_API",id,null,Map.of("metricId",metric.getId(),"metricVersion",metric.getPublishedVersion(),"contractColumns",report.proposed().columns().size(),"sampleRows",probe.sampleRows()));
        if(lineageRebuild!=null)lineageRebuild.afterChange();
        Map<String,Object> result=get(id);result.put("oneTimeKey",key);result.put("contractCheck",DataApiContractGate.reportMap(report,json));
        result.put("hasContractSample",true);
        return result;
    }

    @Transactional
    public Map<String,Object> rotate(Long id,Integer days){return rotate(id,new RotateRequest(days,null));}

    @Transactional
    public Map<String,Object> rotate(Long id, RotateRequest request){
        Map<String,Object> api=require(id);requireManage(api);
        if(!"ACTIVE".equals(api.get("status")) && !"DEPRECATED".equals(api.get("status")))
            throw new IllegalStateException("只有已发布或废弃期内的 API 可轮换 Key");
        Integer days=request==null?null:request.expiresInDays();
        if(days!=null&&(days<1||days>3650))throw new IllegalArgumentException("Key 有效期须为 1～3650 天，留空表示不过期");
        Long callerId=request==null?null:request.callerId();
        if(callerId!=null) requireActiveCaller(callerId);
        String key=createKey(id,days,callerId);
        audit.record("DATA_API_KEY_ROTATE","DATA_API",id,null,Map.of("expiresInDays",days==null?0:days,"callerId",callerId==null?0:callerId));
        return Map.of("apiId",id,"oneTimeKey",key,"callerId",callerId==null?0:callerId,"message","明文 Key 仅展示本次，请立即保存");
    }

    @Transactional
    public Map<String,Object> assignKeyCaller(Long apiId, Long keyId, AssignCallerRequest request){
        requireManage(require(apiId));
        Long callerId=request==null?null:request.callerId();
        if(callerId!=null) requireActiveCaller(callerId);
        if(mapper.assignKeyCaller(apiId,keyId,callerId)!=1)
            throw new IllegalArgumentException("Key 不存在或不属于该 API");
        audit.record("DATA_API_KEY_ASSIGN_CALLER","DATA_API_KEY",keyId,null,Map.of("apiId",apiId,"callerId",callerId==null?0:callerId));
        return get(apiId);
    }

    public List<Map<String,Object>> listCallers(){
        requirePublisher();
        return callers.list();
    }

    public List<Map<String,Object>> listActiveCallers(){
        requirePublisher();
        return callers.listActive();
    }

    @Transactional
    public Map<String,Object> createCaller(CallerSaveRequest request){
        requirePublisher();
        validateCaller(request,true);
        if(callers.byCode(request.orgCode().trim())!=null) throw new IllegalArgumentException("调用方组织编码已存在");
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("orgCode",request.orgCode().trim());
        row.put("orgName",request.orgName().trim());
        row.put("contactName",blankToNull(request.contactName()));
        row.put("contactEmail",blankToNull(request.contactEmail()));
        row.put("notes",blankToNull(request.notes()));
        row.put("callsPerMinute", callerQuotaOrZero(request.callsPerMinute(), "调用方每分钟配额"));
        row.put("dailyQuota", callerQuotaOrZero(request.dailyQuota(), "调用方每日配额"));
        row.put("canaryWeight", callerCanaryWeightOrDefault(request.canaryWeight()));
        row.put("user",actors.currentActor().username());
        if(callers.insert(row)!=1) throw new IllegalStateException("调用方创建失败");
        audit.record("DATA_API_CALLER_CREATE","DATA_API_CALLER",number(row.get("id")),null,Map.of("orgCode",request.orgCode().trim()));
        return callers.get(number(row.get("id")));
    }

    @Transactional
    public Map<String,Object> updateCaller(Long id, CallerSaveRequest request){
        requirePublisher();
        if(callers.get(id)==null) throw new IllegalArgumentException("调用方不存在");
        validateCaller(request,false);
        String status=request.status()==null||request.status().isBlank()?"ACTIVE":request.status().trim().toUpperCase();
        if(!Set.of("ACTIVE","DISABLED").contains(status)) throw new IllegalArgumentException("调用方状态无效");
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("id",id);
        row.put("orgName",request.orgName().trim());
        row.put("contactName",blankToNull(request.contactName()));
        row.put("contactEmail",blankToNull(request.contactEmail()));
        row.put("notes",blankToNull(request.notes()));
        row.put("status",status);
        row.put("callsPerMinute", callerQuotaOrZero(request.callsPerMinute(), "调用方每分钟配额"));
        row.put("dailyQuota", callerQuotaOrZero(request.dailyQuota(), "调用方每日配额"));
        row.put("canaryWeight", callerCanaryWeightOrDefault(request.canaryWeight()));
        row.put("user",actors.currentActor().username());
        if(callers.update(row)!=1) throw new IllegalStateException("调用方更新失败");
        audit.record("DATA_API_CALLER_UPDATE","DATA_API_CALLER",id,null,Map.of("status",status));
        return callers.get(id);
    }

    @Transactional
    public Map<String,Object> revokeKey(Long id,Long keyId){requireManage(require(id));if(mapper.revokeKey(id,keyId)!=1)throw new IllegalArgumentException("Key 不存在、已撤销或不属于该 API");audit.record("DATA_API_KEY_REVOKE","DATA_API_KEY",keyId,null,Map.of("apiId",id));return Map.of("revoked",true);}

    @Transactional
    public Map<String,Object> disable(Long id){Map<String,Object> api=require(id);requireManage(api);if(mapper.disable(id,actors.currentActor().username())!=1)throw new IllegalStateException("仅已发布或已废弃 API 可停用");mapper.revokeActiveKeys(id);metrics.unbind(number(api.get("metricId")),"data_api",usageRefId(id));revision(id,"DISABLE");audit.record("DATA_API_DISABLE","DATA_API",id,null,Map.of());return get(id);}

    /** Marks ACTIVE API as DEPRECATED with optional sunset window; Key system unchanged. */
    @Transactional
    public Map<String,Object> deprecate(Long id, DeprecateRequest request) {
        Map<String,Object> api = require(id);
        requireManage(api);
        if (!"ACTIVE".equals(api.get("status"))) throw new IllegalStateException("仅已发布 API 可标记废弃");
        int days = request == null || request.sunsetDays() == null ? 90 : request.sunsetDays();
        if (days < 1 || days > 3650) throw new IllegalArgumentException("下线通知期须为 1～3650 天");
        String successor = request == null || request.successorApiCode() == null || request.successorApiCode().isBlank()
                ? null : request.successorApiCode().trim();
        if (successor != null) {
            if (!successor.matches("^[A-Za-z][A-Za-z0-9_-]{1,63}$"))
                throw new IllegalArgumentException("继任 API 编码无效");
            if (successor.equals(api.get("apiCode")))
                throw new IllegalArgumentException("继任编码不能与当前 API 相同");
            Map<String,Object> next = mapper.byCode(successor);
            if (next == null || !"ACTIVE".equals(next.get("status")))
                throw new IllegalArgumentException("继任 API 须为已发布编码");
        }
        String note = request == null || request.note() == null ? null : request.note().trim();
        if (note != null && note.length() > 1000) throw new IllegalArgumentException("废弃说明过长");
        LocalDateTime sunset = LocalDateTime.now().plusDays(days);
        if (mapper.deprecate(id, sunset, successor, note, actors.currentActor().username()) != 1)
            throw new IllegalStateException("API 状态已变化");
        revision(id, "DEPRECATE");
        audit.record("DATA_API_DEPRECATE", "DATA_API", id, null,
                Map.of("sunsetDays", days, "successor", successor == null ? "" : successor));
        return get(id);
    }

    @Transactional
    public Map<String,Object> clearDeprecation(Long id) {
        Map<String,Object> api = require(id);
        requireManage(api);
        if (!"DEPRECATED".equals(api.get("status"))) throw new IllegalStateException("仅已废弃 API 可取消废弃");
        if (mapper.clearDeprecation(id, actors.currentActor().username()) != 1)
            throw new IllegalStateException("API 状态已变化");
        revision(id, "UNDEPRECATE");
        audit.record("DATA_API_UNDEPRECATE", "DATA_API", id, null, Map.of());
        return get(id);
    }

    /** OpenAPI-ish description for the published execute endpoint (self-service docs). */
    public Map<String,Object> openApiDoc(Long id){
        Map<String,Object> api=require(id);requireManage(api);
        boolean deprecated = "DEPRECATED".equals(api.get("status")) || api.get("deprecatedAt") != null;
        String contractVersion = String.valueOf(api.getOrDefault("apiVersion", api.getOrDefault("metricPublishedVersion", "1")));
        StringBuilder description = new StringBuilder("受控指标数据 API；使用 wfa_ 前缀 Key 调用 POST /bi/open-data/{code}。");
        description.append(" 契约版本 apiVersion=").append(contractVersion)
                .append("；绑定指标冻结版本 metricPublishedVersion=").append(api.getOrDefault("metricPublishedVersion", "—")).append("。");
        if (deprecated) {
            description.append(" 【已废弃】");
            if (api.get("sunsetAt") != null) description.append("计划下线时间 ").append(api.get("sunsetAt")).append("；");
            if (api.get("successorApiCode") != null) description.append("请迁移至 ").append(api.get("successorApiCode")).append("；");
            if (api.get("deprecationNote") != null) description.append(api.get("deprecationNote"));
        }
        Map<String,Object> post = new LinkedHashMap<>();
        post.put("summary", deprecated ? "分页查询已发布指标（已废弃）" : "分页查询已发布指标");
        post.put("deprecated", deprecated);
        post.put("parameters", List.of(
                Map.of("name","page","in","query","schema",Map.of("type","integer")),
                Map.of("name","pageSize","in","query","schema",Map.of("type","integer"))));
        post.put("security", List.of(Map.of("apiKey", List.of())));
        Map<String,Object> responses = new LinkedHashMap<>();
        responses.put("200", Map.of("description", "rows/columns；含 policyMode=ENFORCED；废弃期内附带 deprecation 对象"));
        responses.put("400", Map.of("description", "Key 无效、API 停用/已过 sunset、配额耗尽或指标版本失效"));
        post.put("responses", responses);
        Map<String,Object> doc=new LinkedHashMap<>();
        doc.put("openapi","3.0.3");
        Map<String,Object> info = new LinkedHashMap<>();
        info.put("title", api.get("name"));
        info.put("version", contractVersion);
        info.put("description", description.toString());
        doc.put("info", info);
        doc.put("paths", Map.of("/bi/open-data/"+api.get("apiCode"), Map.of("post", post)));
        doc.put("components",Map.of("securitySchemes",Map.of("apiKey",Map.of("type","apiKey","in","header","name","X-Wanfo-Api-Key"))));
        doc.put("quota",Map.of("callsPerMinute",api.get("callsPerMinute"),"dailyQuota",api.get("dailyQuota"),"maxPageSize",api.get("maxPageSize")));
        doc.put("lifecycle", Map.of(
                "status", api.get("status"),
                "apiVersion", api.get("apiVersion"),
                "metricPublishedVersion", api.get("metricPublishedVersion"),
                "deprecatedAt", api.get("deprecatedAt"),
                "sunsetAt", api.get("sunsetAt"),
                "successorApiCode", api.get("successorApiCode"),
                "deprecationNote", api.get("deprecationNote"),
                "note", "契约变更请新建 DRAFT 编码并发布；旧编码可 deprecate→sunset→disable，不轮换编码本身"));
        doc.put("audit",Map.of("note","每次调用写入 dp_data_api_call，可在 API 详情 calls 与全局审计归档中查看；关联指标版本见 metricPublishedVersion"));
        return doc;
    }

    public void requireGrantAccess(Long id){
        Map<String,Object> api=require(id);
        if(capabilities.has("bi:resource:admin"))return;
        Long apiId=number(api.get("id"));
        if(apiId==null||(!resources.allowed("data_api",apiId,"grant")&&!resources.allowed("data_api",apiId,"manage")))
            throw new com.wanfo.bi.security.BiAccessException(403,"无权限管理该数据 API 授权");
    }

    public Map<String,Object> execute(String code,String rawKey,int page,int pageSize){
        long started=System.currentTimeMillis();Map<String,Object> key=authenticate(rawKey);if(key==null)throw new IllegalArgumentException("API Key 无效或已撤销");Long keyId=number(key.get("id")),apiId=number(key.get("apiId"));Map<String,Object> api=mapper.get(apiId);
        if(api==null||!code.equals(api.get("apiCode"))||!callableStatus(api)){record(apiId,keyId,"REJECTED",started,0,"API_DISABLED",null);throw new IllegalArgumentException("数据 API 不可用");}
        if(pastSunset(api)){record(apiId,keyId,"REJECTED",started,0,"API_SUNSET",null);throw new IllegalArgumentException("数据 API 已过下线时间，请迁移至继任编码");}
        Long callerId=number(key.get("callerId"));
        Map<String,Object> routed=maybeRouteCanary(api, keyId, callerId);
        final boolean canaryHit=routed!=api;
        final Map<String,Object> served=canaryHit?routed:api;
        Long execApiId=number(served.get("id"));
        try {
            enforceQuota(served,keyId,callerId);
            BiActor reference=json.readValue(String.valueOf(served.get("ownerActorJson")),BiActor.class);LiveActorResolver.Snapshot snapshot=live.resolve(reference);snapshot.require("bi:metric:query");snapshot.require("bi:dataset:query");
            BiMetricDO metric=metricsMapper.get(number(served.get("metricId")));if(metric==null||!"published".equals(metric.getPublishStatus())||!java.util.Objects.equals(number(served.get("metricPublishedVersion")),metric.getPublishedVersion()==null?null:metric.getPublishedVersion().longValue()))throw new IllegalArgumentException("绑定的指标版本已失效，请停用并重新发布 API");
            @SuppressWarnings("unchecked") final Map<String,Object>[] response=new Map[1];
            // Force dataset DataPolicy even when API owner is resource-admin (no plaintext bypass for key callers).
            DatasetDataPolicy.runEnforced(() -> ActorScope.run(snapshot.actor(), snapshot.capabilities(), () -> {
                try {
                    response[0] = metrics.dataPage(number(served.get("metricId")), page, pageSize,
                            number(served.get("maxPageSize")).intValue(), number(served.get("timeoutSeconds")).intValue());
                } catch (Exception e) {
                    throw new IllegalStateException("受控指标查询失败", e);
                }
            }));
            Map<String,Object> result=new LinkedHashMap<>(response[0]);result.remove("executedSql");result.remove("params");result.put("page",page);result.put("pageSize",pageSize);result.put("policyMode","ENFORCED");result.put("apiVersion",served.get("apiVersion"));
            result.put("servedApiCode",served.get("apiCode"));
            if(canaryHit){
                result.put("canary",Map.of("routed",true,"sticky",true,"fromApiCode",code,"toApiCode",served.get("apiCode"),
                        "canaryPercent",served.get("canaryPercent")==null?0:served.get("canaryPercent"),
                        "keyBucket", Math.floorMod(java.util.Objects.hash(keyId, String.valueOf(served.get("familyCode"))), 100)));
            }
            if("DEPRECATED".equals(served.get("status"))||served.get("deprecatedAt")!=null){
                Map<String,Object> dep=new LinkedHashMap<>();
                dep.put("deprecated",true);dep.put("deprecatedAt",served.get("deprecatedAt"));dep.put("sunsetAt",served.get("sunsetAt"));
                dep.put("successorApiCode",served.get("successorApiCode"));dep.put("note",served.get("deprecationNote"));
                result.put("deprecation",dep);
            }
            mapper.touchKey(keyId);record(execApiId,keyId,"SUCCESS",started,((List<?>)result.getOrDefault("rows",List.of())).size(),null,result);return result;
        } catch(RuntimeException failure){boolean quota=failure.getMessage()!=null&&failure.getMessage().contains("配额");String status=quota||failure instanceof IllegalArgumentException?"REJECTED":"FAILED";record(execApiId,keyId,status,started,0,quota?"QUOTA_EXCEEDED":status.equals("FAILED")?"QUERY_FAILED":"QUERY_REJECTED",null);throw failure;}
    }

    /**
     * Route stable (lowest ACTIVE version) family traffic to the canary sibling.
     * Sticky by API Key: {@code hash(keyId, family) % 100 < percent} — same Key always hits the same side.
     */
    private Map<String,Object> maybeRouteCanary(Map<String,Object> requested, Long keyId, Long callerId){
        if(!"ACTIVE".equals(requested.get("status"))) return requested;
        String family=String.valueOf(requested.get("familyCode"));
        if(family==null||family.isBlank()||"null".equals(family)) return requested;
        List<Map<String,Object>> actives=mapper.listByFamily(family).stream()
                .filter(a->"ACTIVE".equals(a.get("status"))).toList();
        if(actives.size()<2) return requested;
        Map<String,Object> stable=actives.stream()
                .min(java.util.Comparator
                        .<Map<String,Object>>comparingInt(a -> number(a.get("apiVersion")) == null ? 0 : number(a.get("apiVersion")).intValue())
                        .thenComparingLong(a -> number(a.get("id")) == null ? 0L : number(a.get("id"))))
                .orElse(requested);
        if(!java.util.Objects.equals(number(stable.get("id")),number(requested.get("id")))) return requested;
        Map<String,Object> canary=actives.stream()
                .filter(a->!java.util.Objects.equals(number(a.get("id")),number(stable.get("id"))))
                .filter(a->{
                    int p=number(a.get("canaryPercent"))==null?0:number(a.get("canaryPercent")).intValue();
                    return p>0&&p<=100;
                })
                .findFirst().orElse(null);
        if(canary==null) return requested;
        int percent=number(canary.get("canaryPercent")).intValue();
        int weight=100;
        if(callerId!=null){
            Map<String,Object> caller=callers.get(callerId);
            if(caller!=null&&number(caller.get("canaryWeight"))!=null)
                weight=Math.max(0,Math.min(1000,number(caller.get("canaryWeight")).intValue()));
        }
        int effectivePercent=Math.min(100,(percent*weight)/100);
        if(effectivePercent<=0) return requested;
        long stickyKey = keyId == null ? random.nextInt(100) : keyId;
        int bucket = Math.floorMod(java.util.Objects.hash(stickyKey, family), 100);
        if(bucket < effectivePercent) return canary;
        return requested;
    }

    private static boolean callableStatus(Map<String,Object> api) {
        String status = String.valueOf(api.get("status"));
        return "ACTIVE".equals(status) || "DEPRECATED".equals(status);
    }

    private static boolean pastSunset(Map<String,Object> api) {
        LocalDateTime sunset = asLocalDateTime(api.get("sunsetAt"));
        return sunset != null && !LocalDateTime.now().isBefore(sunset);
    }

    private static LocalDateTime asLocalDateTime(Object raw) {
        if (raw instanceof LocalDateTime t) return t;
        if (raw instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (raw instanceof java.util.Date d) return LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneId.systemDefault());
        if (raw != null) {
            try { return LocalDateTime.parse(String.valueOf(raw).replace(' ', 'T')); }
            catch (Exception ignored) { }
        }
        return null;
    }

    private void enforceQuota(Map<String,Object> api,Long keyId,Long callerId){
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime minute=now.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime day=LocalDate.now().atStartOfDay();
        Long apiId=number(api.get("id"));
        int rate=number(api.get("callsPerMinute")).intValue();
        int daily=number(api.get("dailyQuota")).intValue();
        if(callerId!=null){
            Map<String,Object> caller=callers.get(callerId);
            if(caller!=null && "ACTIVE".equals(caller.get("status"))){
                int callerRate=number(caller.get("callsPerMinute"))==null?0:number(caller.get("callsPerMinute")).intValue();
                int callerDaily=number(caller.get("dailyQuota"))==null?0:number(caller.get("dailyQuota")).intValue();
                if(callerRate>0) rate=Math.min(rate,callerRate);
                if(callerDaily>0) daily=Math.min(daily,callerDaily);
                // Org-level shared counters: per-API windows + global day budget (api_id=0).
                if(callerRate>0||callerDaily>0){
                    int orgRate=callerRate>0?callerRate:rate;
                    int orgDaily=callerDaily>0?callerDaily:daily;
                    mapper.incrementWindow(apiId,callerId,"CALLER_MINUTE",minute,orgRate);
                    mapper.incrementWindow(apiId,callerId,"CALLER_DAY",day,orgDaily);
                    Integer orgMinute=mapper.windowCount(apiId,callerId,"CALLER_MINUTE",minute);
                    Integer orgDay=mapper.windowCount(apiId,callerId,"CALLER_DAY",day);
                    if(orgMinute==null||orgDay==null||orgMinute>orgRate||orgDay>orgDaily)
                        throw new IllegalArgumentException("调用方组织配额已用尽");
                    if(callerDaily>0){
                        mapper.incrementWindow(0L,callerId,"CALLER_GLOBAL_DAY",day,callerDaily);
                        Integer globalDay=mapper.windowCount(0L,callerId,"CALLER_GLOBAL_DAY",day);
                        if(globalDay==null||globalDay>callerDaily)
                            throw new IllegalArgumentException("调用方全局日配额已用尽");
                    }
                }
            }
        }
        mapper.incrementWindow(apiId,keyId,"MINUTE",minute,rate);
        mapper.incrementWindow(apiId,keyId,"DAY",day,daily);
        Integer minuteCount=mapper.windowCount(apiId,keyId,"MINUTE",minute);
        Integer dayCount=mapper.windowCount(apiId,keyId,"DAY",day);
        if(minuteCount==null||dayCount==null||minuteCount>rate||dayCount>daily)
            throw new IllegalArgumentException("数据 API 调用配额已用尽");
    }
    private static int callerQuotaOrZero(Integer value,String label){
        if(value==null||value==0) return 0;
        if(value<1||value>1_000_000) throw new IllegalArgumentException(label+"无效（0=继承 API，或 1～1000000）");
        return value;
    }
    private String createKey(Long id,Integer days,Long callerId){
        mapper.revokeActiveKeys(id);
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String raw="wfa_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("apiId",id);row.put("hash",sha256(raw));row.put("prefix",raw.substring(0,12));
        row.put("callerId",callerId);row.put("user",actors.currentActor().username());
        row.put("expiresAt",days==null||days<1?null:LocalDateTime.now().plusDays(Math.min(days,3650)));
        if(mapper.insertKey(row)!=1)throw new IllegalStateException("API Key 生成失败");
        return raw;
    }
    private void requireActiveCaller(Long callerId){
        Map<String,Object> caller=callers.get(callerId);
        if(caller==null||!"ACTIVE".equals(caller.get("status")))
            throw new IllegalArgumentException("调用方组织不存在或已停用");
    }
    private void validateCaller(CallerSaveRequest request,boolean creating){
        if(request==null||request.orgName()==null||request.orgName().isBlank()||request.orgName().trim().length()>200)
            throw new IllegalArgumentException("请填写调用方组织名称");
        if(creating){
            if(request.orgCode()==null||!request.orgCode().matches("^[A-Za-z][A-Za-z0-9_-]{1,63}$"))
                throw new IllegalArgumentException("调用方组织编码须为字母开头的 2～64 位标识");
        }
        if(request.contactEmail()!=null&&!request.contactEmail().isBlank()&&request.contactEmail().length()>128)
            throw new IllegalArgumentException("联系邮箱过长");
        if(request.notes()!=null&&request.notes().length()>1000) throw new IllegalArgumentException("备注过长");
        if(request.contactName()!=null&&request.contactName().length()>64) throw new IllegalArgumentException("联系人过长");
    }
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private Map<String,Object> authenticate(String raw){if(raw==null||raw.length()<24||raw.length()>256)return null;return mapper.authenticate(sha256(raw));}
    private String sha256(String raw){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Key 摘要算法不可用",e);}}
    private void record(Long apiId,Long keyId,String status,long started,int rows,String error,Map<String,Object> payload){
        String sampleJson=null;
        if("SUCCESS".equals(status)&&payload!=null&&callSampleRatePercent>0&&random.nextInt(100)<callSampleRatePercent){
            try{sampleJson=json.writeValueAsString(anonymizeSample(payload));}catch(Exception ignored){sampleJson=null;}
        }
        mapper.call(apiId,keyId,status,(int)Math.min(2_147_483_647,System.currentTimeMillis()-started),rows,error,sampleJson);
    }
    private static int callerCanaryWeightOrDefault(Integer value){
        if(value==null) return 100;
        if(value<0||value>1000) throw new IllegalArgumentException("canaryWeight 须在 0～1000");
        return value;
    }
    private void revision(Long id,String kind){Map<String,Object> api=require(id);Map<String,Object> snapshot=new LinkedHashMap<>(safe(api));snapshot.put("changeKind",kind);mapper.insertRevision(id,mapper.nextRevision(id),json.writeValueAsString(snapshot),actors.currentActor().username());}
    private BiMetricDO publishedMetric(Long id){BiMetricDO metric=metricsMapper.get(id);if(metric==null||!"published".equals(metric.getPublishStatus())||metric.getPublishedJson()==null)throw new IllegalArgumentException("请选择已发布指标");return metric;}
    private void authorizeMetric(BiMetricDO metric){capabilities.require("bi:metric:publish");capabilities.require("bi:metric:query");capabilities.require("bi:dataset:query");resources.require("metric",metric.getId(),"publish");resources.require("metric",metric.getId(),"query");resources.require("dataset",metric.getDatasetId(),"view");resources.require("dataset",metric.getDatasetId(),"query");}
    private void validate(SaveRequest r){if(r==null||r.code()==null||!r.code().matches("^[A-Za-z][A-Za-z0-9_-]{1,63}$")||r.name()==null||r.name().isBlank()||r.name().trim().length()>200||r.metricId()==null)throw new IllegalArgumentException("请填写有效编码、名称并绑定已发布指标");
        if(r.familyCode()!=null&&!r.familyCode().isBlank()&&!r.familyCode().matches("^[A-Za-z][A-Za-z0-9_-]{1,63}$"))
            throw new IllegalArgumentException("family 编码无效");
    }
    private static String resolveFamilyCode(String familyCode,String apiCode){
        if(familyCode!=null&&!familyCode.isBlank()) return familyCode.trim();
        return apiCode;
    }
    private static String suggestNextCode(String family,int version){
        String base=family.length()>56?family.substring(0,56):family;
        String candidate=base+"_v"+version;
        if(candidate.length()>64) candidate=candidate.substring(0,64);
        return candidate;
    }
    private int bounded(Integer value,int fallback,int min,int max,String label){int n=value==null?fallback:value;if(n<min||n>max)throw new IllegalArgumentException(label+"范围无效");return n;}
    private void requirePublisher(){if(!capabilities.has("bi:resource:admin"))capabilities.require("bi:metric:publish");}
    private void requireManage(Map<String,Object> api){
        if(capabilities.has("bi:resource:admin"))return;
        Long id=number(api.get("id"));
        if(id==null||!resources.allowed("data_api",id,"manage"))
            throw new com.wanfo.bi.security.BiAccessException(403,"无权限管理该数据 API");
    }
    private Map<String,Object> require(Long id){Map<String,Object> api=mapper.get(id);if(api==null)throw new IllegalArgumentException("数据 API 不存在");return api;}
    /** Metric usage refId must start with a letter (MetricDefinitionService.token). */
    private String usageRefId(Long id){return "api_" + id;}
    private Map<String,Object> safe(Map<String,Object> raw){
        Map<String,Object> result=new LinkedHashMap<>(raw);
        result.remove("ownerActorJson");
        // contract JSON can be large; expose presence + checked time on list/detail, full via contract-check.
        Object schema=result.remove("contractSchemaJson");
        result.put("hasContractSchema", schema!=null && !String.valueOf(schema).isBlank());
        Object sample=result.remove("contractSampleJson");
        result.put("hasContractSample", sample!=null && !String.valueOf(sample).isBlank());
        return result;
    }
    private Long number(Object v){return v instanceof Number n?n.longValue():v==null?null:Long.valueOf(String.valueOf(v));}

    private record ProbeBundle(Map<String, Object> sample, DataApiContractGate.Schema proposed, int sampleRows) {}

    private DataApiContractGate.Report runContractCheck(Map<String,Object> api) {
        return compareProbe(api, probeMetric(api));
    }

    private DataApiContractGate.Report compareProbe(Map<String, Object> api, ProbeBundle probe) {
        Map<String, Object> baselineRow = mapper.baselineBySuccessorCode(String.valueOf(api.get("apiCode")));
        DataApiContractGate.Schema baseline = baselineRow == null ? null
                : DataApiContractGate.fromJson(String.valueOf(baselineRow.get("contractSchemaJson")), json);
        return DataApiContractGate.compare(probe.proposed(), baseline, probe.sampleRows());
    }

    private ProbeBundle probeMetric(Map<String, Object> api) {
        BiMetricDO metric = publishedMetric(number(api.get("metricId")));
        authorizeMetric(metric);
        int pageSize = Math.min(5, Math.max(1, number(api.get("maxPageSize")) == null ? 5 : number(api.get("maxPageSize")).intValue()));
        int timeout = number(api.get("timeoutSeconds")) == null ? 20 : number(api.get("timeoutSeconds")).intValue();
        Map<String, Object> sample;
        try {
            BiActor reference = json.readValue(String.valueOf(api.get("ownerActorJson")), BiActor.class);
            LiveActorResolver.Snapshot snapshot = live.resolve(reference);
            snapshot.require("bi:metric:query");
            snapshot.require("bi:dataset:query");
            final Map<String, Object>[] box = new Map[1];
            DatasetDataPolicy.runEnforced(() -> ActorScope.run(snapshot.actor(), snapshot.capabilities(), () -> {
                try {
                    box[0] = metrics.dataPage(metric.getId(), 1, pageSize, pageSize, timeout);
                } catch (Exception e) {
                    throw new IllegalStateException("契约探测查询失败", e);
                }
            }));
            sample = box[0];
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("契约探测失败", failure);
        }
        DataApiContractGate.Schema proposed = DataApiContractGate.extract(sample, metric.getId(),
                metric.getPublishedVersion() == null ? null : metric.getPublishedVersion().longValue());
        int sampleRows = sample.get("rows") instanceof List<?> rows ? rows.size() : 0;
        return new ProbeBundle(sample, proposed, sampleRows);
    }

    /** Persist kinds + truncated string cells only — never full PII payloads. */
    private Map<String, Object> anonymizeSample(Map<String, Object> sample) {
        Map<String, Object> out = new LinkedHashMap<>();
        Object cols = sample == null ? null : sample.get("columns");
        out.put("columns", cols instanceof List<?> list ? list : List.of());
        List<Map<String, Object>> rowsOut = new ArrayList<>();
        Object rawRows = sample == null ? null : sample.get("rows");
        if (rawRows instanceof List<?> rows) {
            int n = 0;
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> map) || n >= 5) break;
                Map<String, Object> cell = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    String key = String.valueOf(e.getKey());
                    Object val = e.getValue();
                    Map<String, Object> packed = new LinkedHashMap<>();
                    packed.put("kind", DataApiContractGate.inferKind(val));
                    if (val == null) packed.put("preview", null);
                    else {
                        String text = String.valueOf(val);
                        packed.put("preview", text.length() > 32 ? text.substring(0, 32) + "…" : text);
                    }
                    cell.put(key, packed);
                }
                rowsOut.add(cell);
                n++;
            }
        }
        out.put("rows", rowsOut);
        out.put("anonymized", true);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSampleJson(String raw) {
        try {
            return json.readValue(raw, Map.class);
        } catch (Exception error) {
            throw new IllegalStateException("发布样本解析失败", error);
        }
    }

    private DataApiContractGate.Schema schemaFromSample(Map<String, Object> sample, Long metricId, Long metricVersion) {
        Object cols = sample.get("columns");
        if (!(cols instanceof List<?> names) || names.isEmpty())
            throw new IllegalStateException("发布样本缺少 columns");
        Object rawRows = sample.get("rows");
        List<?> rows = rawRows instanceof List<?> list ? list : List.of();
        Map<?, ?> first = null;
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) { first = map; break; }
        }
        List<DataApiContractGate.Column> columns = new ArrayList<>();
        for (Object nameObj : names) {
            String name = String.valueOf(nameObj == null ? "" : nameObj).trim();
            if (name.isEmpty()) continue;
            String kind = "STRING";
            if (first != null && first.get(name) instanceof Map<?, ?> cell) {
                Object k = cell.get("kind");
                if (k != null) kind = String.valueOf(k);
            }
            columns.add(new DataApiContractGate.Column(name, kind));
        }
        if (columns.isEmpty()) throw new IllegalStateException("发布样本列为空");
        return new DataApiContractGate.Schema(List.copyOf(columns), metricId, metricVersion);
    }
}
