package com.wanfo.bi.service;

import com.wanfo.bi.mapper.BiMetadataScanMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Minimal, redacted audit trail for metadata catalog operations. */
@Service
public class CatalogAuditService
{
    private final BiMetadataScanMapper mapper;
    private final CurrentActorProvider actors;
    private final CapabilityProvider capabilities;
    private final ObjectMapper json;
    private final MeterRegistry meters;

    public CatalogAuditService(BiMetadataScanMapper mapper, CurrentActorProvider actors,
            CapabilityProvider capabilities, ObjectMapper json, MeterRegistry meters)
    {
        this.mapper = mapper;
        this.actors = actors;
        this.capabilities = capabilities;
        this.json = json;
        this.meters = meters;
    }

    public void record(String action, String subjectType, Long subjectId, Long sourceId, Map<String, ?> summary)
    {
        var actor = actors.currentActor();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("actorId", actor.userId());
        row.put("actorName", bounded(actor.username(), 64));
        row.put("action", bounded(action, 64));
        row.put("subjectType", bounded(subjectType, 32));
        row.put("subjectId", subjectId);
        row.put("sourceId", sourceId);
        row.put("outcome", "SUCCESS");
        row.put("traceId", bounded(MDC.get("trace_id"), 64));
        row.put("summary", safeJson(summary));
        insertAudit(row, "目录审计记录写入失败");
    }

    public void recordFailure(String method, String path, Throwable failure)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        try
        {
            var actor = actors.currentActor();
            row.put("actorId", actor.userId());
            row.put("actorName", bounded(actor.username(), 64));
        }
        catch (RuntimeException unauthenticated)
        {
            row.put("actorId", null);
            row.put("actorName", "anonymous");
        }
        row.put("action", "REQUEST_FAILED");
        row.put("subjectType", "HTTP");
        row.put("subjectId", null);
        row.put("sourceId", null);
        row.put("outcome", "FAILED");
        row.put("traceId", bounded(MDC.get("trace_id"), 64));
        row.put("summary", safeJson(Map.of("method", bounded(method, 10), "path", bounded(path, 300),
                "exceptionType", failure == null ? "Unknown" : bounded(failure.getClass().getName(), 200))));
        insertAudit(row, "失败请求审计记录写入失败");
    }

    public Map<String, Object> list(int page, int pageSize,String actor,String action,String outcome,String from,String to)
    {
        capabilities.require("bi:resource:admin");
        if (page < 1 || page > 100_000 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数无效");
        int offset = Math.multiplyExact(page - 1, pageSize);
        String actorFilter=filter(actor,64),actionFilter=filter(action,64),outcomeFilter=filter(outcome,16);
        if(!outcomeFilter.isEmpty()&&!List.of("SUCCESS","FAILED","REJECTED").contains(outcomeFilter.toUpperCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("审计结果仅支持 SUCCESS、FAILED 或 REJECTED");
        LocalDateTime fromAt=parseDate(from),toAt=parseDate(to);if(fromAt!=null&&toAt!=null&&!fromAt.isBefore(toAt))throw new IllegalArgumentException("审计时间范围无效");
        if(!outcomeFilter.isEmpty())outcomeFilter=outcomeFilter.toUpperCase(java.util.Locale.ROOT);
        List<Map<String, Object>> items = mapper.catalogAuditPage(offset,pageSize,actorFilter,actionFilter,outcomeFilter,fromAt,toAt);
        return Map.of("items",items,"total",mapper.countCatalogAudit(actorFilter,actionFilter,outcomeFilter,fromAt,toAt),"page",page,"pageSize",pageSize);
    }

    public Map<String,Object> retentionPolicy(){capabilities.require("bi:resource:admin");Map<String,Object> value=mapper.auditRetentionPolicy();if(value==null)return Map.of("liveRetentionDays",365,"archiveRetention","indefinite","updatedBy","system");Map<String,Object> result=new LinkedHashMap<>(value);result.put("archiveRetention","indefinite");return result;}

    @Transactional
    public Map<String,Object> updateRetention(int days){capabilities.require("bi:resource:admin");if(days<365||days>3650)throw new IllegalArgumentException("在线审计保留期限须为 365～3650 天；归档记录长期保留");String actor=actors.currentActor().username();if(mapper.updateAuditRetention(days,actor)!=1)throw new IllegalStateException("审计保留策略更新失败");record("AUDIT_RETENTION_UPDATE","AUDIT_POLICY",null,null,Map.of("liveRetentionDays",days,"archiveRetention","indefinite"));return retentionPolicy();}

    @Scheduled(cron="${bi.audit.archive-cron:0 25 2 * * *}")
    @Transactional
    public void archiveExpired(){Timer.Sample timer=Timer.start(meters);String outcome="success";try{Map<String,Object> policy=mapper.auditRetentionPolicy();int days=policy==null?365:((Number)policy.get("liveRetentionDays")).intValue();LocalDateTime cutoff=LocalDateTime.now().minusDays(Math.max(365,Math.min(3650,days)));int batchSize=5000;mapper.archiveOldCatalogAudit(cutoff,batchSize);mapper.archiveOldSubscriptionAudit(cutoff,batchSize);mapper.archiveOldDataApiAudit(cutoff,batchSize);mapper.purgeArchivedCatalogAudit(batchSize);mapper.purgeArchivedSubscriptionAudit(batchSize);mapper.purgeArchivedDataApiAudit(batchSize);}catch(RuntimeException failure){outcome="failure";Counter.builder("bi.audit.archive.failures").register(meters).increment();throw failure;}finally{timer.stop(Timer.builder("bi.audit.archive.duration").tag("outcome",outcome).register(meters));}}

    private void insertAudit(Map<String,Object> row,String message){try{if(mapper.insertCatalogAudit(row)!=1)throw new IllegalStateException(message);}catch(RuntimeException failure){Counter.builder("bi.audit.write.failures").register(meters).increment();throw failure;}}

    private String filter(String value,int max){if(value==null)return "";String text=value.trim();if(text.length()>max)throw new IllegalArgumentException("审计筛选条件长度超限");return text;}
    private LocalDateTime parseDate(String value){if(value==null||value.isBlank())return null;try{return LocalDateTime.parse(value.trim());}catch(DateTimeParseException e){throw new IllegalArgumentException("时间请使用 ISO 本地时间格式，例如 2026-10-30T00:00:00");}}

    private String safeJson(Map<String, ?> summary)
    {
        try
        {
            String value = json.writeValueAsString(summary == null ? Map.of() : summary);
            return value.length() > 2000 ? value.substring(0, 2000) : value;
        }
        catch (Exception exception) { return "{}"; }
    }

    private String bounded(String value, int max) { return value == null ? "" : value.substring(0, Math.min(max, value.length())); }
}
