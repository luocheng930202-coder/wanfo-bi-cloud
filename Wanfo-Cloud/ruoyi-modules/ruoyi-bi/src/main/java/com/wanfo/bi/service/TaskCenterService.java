package com.wanfo.bi.service;

import com.wanfo.bi.mapper.BiExportJobMapper;
import com.wanfo.bi.mapper.BiMetadataScanMapper;
import com.wanfo.bi.mapper.BiReportSubscriptionMapper;
import com.wanfo.bi.mapper.CdcIngestMapper;
import com.wanfo.bi.mapper.ObjectStorageLoadMapper;
import com.wanfo.bi.mapper.PipelineRunMapper;
import com.wanfo.bi.mapper.QualityTaskMapper;
import com.wanfo.bi.mapper.WarehouseLoadMapper;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Read-time aggregation over existing run facts; no duplicate task state is stored. */
@Service
public class TaskCenterService {
    private final QualityTaskMapper quality;
    private final BiMetadataScanMapper scans;
    private final PipelineRunMapper pipelines;
    private final WarehouseLoadMapper loads;
    private final ObjectStorageLoadMapper objectLoads;
    private final CdcIngestMapper cdcJobs;
    private final BiExportJobMapper exports;
    private final BiReportSubscriptionMapper subscriptions;
    private final PipelineRunService pipelineRuns;
    private final QualityTaskService qualityTasks;
    private final MetadataScanService metadataScans;
    private final ExportJobService exportJobs;
    private final WarehouseLoadService warehouseLoads;
    private final CatalogAuditService audit;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final TaskPoolGateService poolGate;

    public TaskCenterService(QualityTaskMapper quality, BiMetadataScanMapper scans, PipelineRunMapper pipelines,
            WarehouseLoadMapper loads, ObjectStorageLoadMapper objectLoads, CdcIngestMapper cdcJobs,
            BiExportJobMapper exports, BiReportSubscriptionMapper subscriptions,
            PipelineRunService pipelineRuns, QualityTaskService qualityTasks, MetadataScanService metadataScans,
            ExportJobService exportJobs, WarehouseLoadService warehouseLoads, CatalogAuditService audit, BiResourceAuthorizer resources,
            CapabilityProvider capabilities, CurrentActorProvider actors, TaskPoolGateService poolGate) {
        this.quality=quality; this.scans=scans; this.pipelines=pipelines; this.loads=loads;
        this.objectLoads=objectLoads; this.cdcJobs=cdcJobs; this.exports=exports;
        this.subscriptions=subscriptions; this.pipelineRuns=pipelineRuns; this.qualityTasks=qualityTasks; this.metadataScans=metadataScans; this.exportJobs=exportJobs; this.warehouseLoads=warehouseLoads; this.audit=audit;
        this.resources=resources; this.capabilities=capabilities; this.actors=actors; this.poolGate=poolGate;
    }

    public List<Map<String, Object>> listLanes() { return poolGate.listLanes(); }

    public Map<String, Object> updateLane(String lane, TaskPoolGateService.LaneUpdateRequest request) {
        return poolGate.updateLane(lane, request);
    }

    public Map<String,Object> list(String type, String status, String query, int page, int pageSize) {
        return list(type, status, query, page, pageSize, "created");
    }

    /** S3-01: unified pool view — still read-time over module facts; sort=priority surfaces starvation risk. */
    public Map<String,Object> list(String type, String status, String query, int page, int pageSize, String sort) {
        capabilities.require("bi:task:list");
        List<Map<String,Object>> rows=new ArrayList<>();
        if(capabilities.has("bi:dataset:query")) for(var raw:quality.recentRuns()) {
            Long dataset=id(raw.get("datasetId")); if(resources.allowed("dataset",dataset,"query")) rows.add(row("QUALITY",raw.get("id"),raw.get("taskId"),raw.get("taskName"),raw.get("resourceName"),raw.get("status"),raw.get("createTime"),raw.get("startedAt"),raw.get("finishedAt"),raw.get("message"),"/bi-admin/dataset?id="+dataset,capabilities.has("bi:dataset:query"),capabilities.has("bi:dataset:query")));
        }
        if(capabilities.has("bi:datasource:model")) for(var raw:scans.recentRuns()) {
            Long source=id(raw.get("datasourceId")); if(resources.allowed("datasource",source,"view")) rows.add(row("METADATA_SCAN",raw.get("id"),raw.get("scanId"),raw.get("taskName"),raw.get("resourceName"),raw.get("status"),raw.get("createTime"),raw.get("startedAt"),raw.get("finishedAt"),raw.get("message"),"/bi-admin/catalog?datasourceId="+source,false,capabilities.has("bi:datasource:model")&&resources.allowed("datasource",source,"edit")));
        }
        if(capabilities.has("bi:pipeline:list")) for(var raw:pipelines.recentRuns()) {
            Long pipeline=id(raw.get("pipelineId")); if(resources.allowed("pipeline",pipeline,"view")) rows.add(row("PIPELINE",raw.get("id"),raw.get("pipelineId"),raw.get("taskName"),"数据加工流程",raw.get("status"),raw.get("createTime"),raw.get("startedAt"),raw.get("finishedAt"),raw.get("message"),"/bi-admin/pipeline?pipelineId="+pipeline,capabilities.has("bi:pipeline:run")&&resources.allowed("pipeline",pipeline,"run"),capabilities.has("bi:pipeline:run")&&resources.allowed("pipeline",pipeline,"run")));
        }
        if(capabilities.has("bi:resource:admin")) {
            for(var raw:loads.recentRuns()) rows.add(row("WAREHOUSE_LOAD",raw.get("id"),null,raw.get("sourceTable"),String.valueOf(raw.get("sourceName"))+" → "+String.valueOf(raw.get("targetName")),raw.get("status"),raw.get("createTime"),raw.get("startedAt"),raw.get("finishedAt"),raw.get("message"),"/bi-admin/warehouse",false,raw.get("requestSnapshotJson")!=null&&"FAILED".equalsIgnoreCase(String.valueOf(raw.get("status")))));
            for(var raw:objectLoads.recent()) {
                String name = String.valueOf(raw.getOrDefault("bucket", "")) + "/" + String.valueOf(raw.getOrDefault("objectPrefix", ""));
                String resource = "对象入仓 → " + raw.getOrDefault("targetDatabase", "") + "." + raw.getOrDefault("targetTable", "");
                rows.add(row("OBJECT_LOAD", raw.get("id"), null, name, resource, raw.get("status"), raw.get("createTime"),
                        raw.get("createTime"), null, raw.get("errorSummary"), "/bi-admin/warehouse", false,
                        "FAILED".equalsIgnoreCase(String.valueOf(raw.get("status")))));
            }
            for(var raw:cdcJobs.list()) {
                String statusLabel = raw.get("lastError") != null && !String.valueOf(raw.get("lastError")).isBlank()
                        ? "FAILED" : ("ACTIVE".equals(String.valueOf(raw.get("status"))) ? "WAITING" : String.valueOf(raw.get("status")));
                String resource = raw.get("stagingTable") + " → " + raw.get("targetTable");
                rows.add(row("CDC_JOB", raw.get("id"), null, raw.get("name"), resource, statusLabel,
                        raw.get("lastRunAt") != null ? raw.get("lastRunAt") : raw.get("createTime"),
                        raw.get("lastRunAt"), null, raw.get("lastError"), "/bi-admin/warehouse", false, false));
            }
        }
        if(capabilities.has("bi:export:list")) for(var raw:exports.recent(actors.currentActor().userId())) rows.add(row("EXPORT",raw.get("id"),null,raw.get("name"),String.valueOf(raw.get("sourceType"))+" #"+raw.get("sourceId"),raw.get("status"),raw.get("createTime"),null,null,raw.get("message"),"/bi-admin/export",capabilities.has("bi:export:cancel"),capabilities.has("bi:export:add")));
        if(capabilities.has("bi:subscription:list")) {
            for(var raw:subscriptions.recentHistory(actors.currentActor().userId())) rows.add(row("SUBSCRIPTION",raw.get("id"),null,raw.get("taskName"),String.valueOf(raw.get("sourceType"))+" #"+raw.get("sourceId"),raw.get("status"),raw.get("startedAt"),raw.get("startedAt"),raw.get("finishedAt"),raw.get("message"),"/bi-admin/subscription",false,false));
            for(var raw:subscriptions.recentSchedules(actors.currentActor().userId())) if(Boolean.TRUE.equals(raw.get("enabled")) || "1".equals(String.valueOf(raw.get("enabled")))) rows.add(row("SUBSCRIPTION_SCHEDULE",raw.get("id"),null,raw.get("name"),String.valueOf(raw.get("sourceType"))+" #"+raw.get("sourceId"),raw.get("lastStatus"),raw.get("lastRun"),null,null,raw.get("lastMessage"),"/bi-admin/subscription",false,false));
        }
        boolean byPriority = "priority".equalsIgnoreCase(sort == null ? "" : sort.trim());
        if (byPriority) {
            rows.sort(Comparator
                    .comparing((Map<String, Object> r) -> ((Number) r.getOrDefault("priority", 0)).intValue()).reversed()
                    .thenComparing(r -> String.valueOf(r.get("createdAt")), Comparator.reverseOrder()));
        } else {
            rows.sort(Comparator.comparing((Map<String,Object> r)->String.valueOf(r.get("createdAt"))).reversed());
        }
        String selectedType=normalize(type),selectedStatus=normalize(status),needle=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        List<Map<String,Object>> filtered=rows.stream().filter(r->selectedType.isBlank()||selectedType.equals(r.get("type")))
                .filter(r->selectedStatus.isBlank()||selectedStatus.equals(normalize(String.valueOf(r.get("status")))))
                .filter(r->needle.isBlank()||String.valueOf(r.get("name")).toLowerCase(Locale.ROOT).contains(needle)||String.valueOf(r.get("resourceName")).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        int safeSize=Math.max(1,Math.min(pageSize,100)),safePage=Math.max(1,page),from=Math.min(filtered.size(),(safePage-1)*safeSize),to=Math.min(filtered.size(),from+safeSize);
        Map<String,Object> summary=new LinkedHashMap<>();
        summary.put("total",rows.size());
        summary.put("running",rows.stream().filter(r->isActive(r.get("status"))).count());
        summary.put("failed",rows.stream().filter(r->"FAILED".equals(normalize(String.valueOf(r.get("status"))))).count());
        summary.put("succeeded",rows.stream().filter(r->SetHolder.SUCCESS.contains(normalize(String.valueOf(r.get("status"))))).count());
        summary.put("waiting", rows.stream().filter(r -> "WAITING".equals(normalize(String.valueOf(r.get("status"))))).count());
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, Long> byLane = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String t = String.valueOf(r.get("type"));
            byType.merge(t, 1L, Long::sum);
            byLane.merge(String.valueOf(r.get("poolLane")), 1L, Long::sum);
        }
        summary.put("byType", byType);
        summary.put("byLane", byLane);
        summary.put("sort", byPriority ? "priority" : "created");
        summary.put("note", "统一任务池只读聚合各模块运行事实，不另建任务状态表；priority 排序优先展示运行中/失败与仓类任务以降低饿死风险。");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", filtered.subList(from, to));
        result.put("total", filtered.size());
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        result.put("summary", summary);
        return result;
    }

    public Map<String,Object> cancel(String type, Long id) {
        capabilities.require("bi:task:list"); if(id==null||id<1)throw new IllegalArgumentException("任务编号无效");
        if("PIPELINE".equals(normalize(type))) { capabilities.require("bi:pipeline:run"); return pipelineRuns.cancel(id); }
        if("EXPORT".equals(normalize(type))) { capabilities.require("bi:export:cancel"); return exportJobs.cancel(id); }
        if("QUALITY".equals(normalize(type))) { capabilities.require("bi:dataset:query"); Map<String,Object> run=quality.selectRun(id); if(run==null||!resources.allowed("dataset",id(run.get("datasetId")),"query"))throw new com.wanfo.bi.security.BiAccessException(403,"没有该资源的操作权限"); Map<String,Object> result=qualityTasks.cancel(id(run.get("taskId")),id); audit.record("QUALITY_RUN_CANCEL","QUALITY_RUN",id,id(run.get("datasetId")),Map.of("status","CANCELLED")); return result; }
        throw new IllegalArgumentException("该任务类型不支持从任务中心取消");
    }

    public Map<String,Object> retry(String type, Long id) {
        capabilities.require("bi:task:list"); if(id==null||id<1)throw new IllegalArgumentException("任务编号无效");
        String selected=normalize(type);
        if("QUALITY".equals(selected)) {
            capabilities.require("bi:dataset:query"); Map<String,Object> run=quality.recentRuns().stream().filter(r->id.equals(id(r.get("id")))).findFirst().orElseThrow(()->new IllegalArgumentException("质量运行记录不存在或已超出近期记录范围"));
            if(!resources.allowed("dataset",id(run.get("datasetId")),"query"))throw new com.wanfo.bi.security.BiAccessException(403,"没有该资源的操作权限");
            return qualityTasks.trigger(id(run.get("taskId")),null);
        }
        if("METADATA_SCAN".equals(selected)) {
            capabilities.require("bi:datasource:model"); Map<String,Object> run=scans.recentRuns().stream().filter(r->id.equals(id(r.get("id")))).findFirst().orElseThrow(()->new IllegalArgumentException("元数据扫描记录不存在或已超出近期记录范围"));
            return metadataScans.trigger(id(run.get("scanId")),null);
        }
        if("PIPELINE".equals(selected)) {
            capabilities.require("bi:pipeline:run");
            return pipelineRuns.retryFromSnapshot(id,"retry-"+UUID.randomUUID());
        }
        if("EXPORT".equals(selected)) { capabilities.require("bi:export:add"); return exportJobs.retry(id); }
        if("WAREHOUSE_LOAD".equals(selected)) { capabilities.require("bi:resource:admin"); Map<String,Object> run=warehouseLoads.retry(id,null); audit.record("WAREHOUSE_LOAD_RETRY","WAREHOUSE_LOAD_RUN",id,id(run.get("sourceDatasourceId")),Map.of("newRunId",run.getOrDefault("id",0L))); return run; }
        throw new IllegalArgumentException("该任务类型尚不支持重试");
    }

    private static Map<String,Object> row(String type,Object id,Object operationId,Object name,Object resource,Object status,Object created,Object started,Object finished,Object message,String url,boolean canCancel,boolean canRetry) {
        Map<String,Object> item=new LinkedHashMap<>();
        item.put("type",type);item.put("id",id);item.put("name",name);item.put("resourceName",resource);item.put("status",status);
        item.put("createdAt",created);item.put("startedAt",started);item.put("finishedAt",finished);item.put("message",message);
        item.put("detailUrl",url);item.put("cancelable",canCancel&&isActive(status));
        item.put("retryable",canRetry&&"FAILED".equals(normalize(String.valueOf(status))));
        item.put("priority", priorityScore(type, status));
        item.put("poolLane", poolLane(status));
        return item;
    }

    private static int priorityScore(String type, Object status) {
        String st = normalize(String.valueOf(status));
        int base = switch (st) {
            case "RUNNING", "PENDING", "QUEUED" -> 1000;
            case "FAILED" -> 800;
            case "WAITING" -> 400;
            case "SUCCEEDED", "SUCCESS" -> 100;
            case "CANCELLED", "PAUSED", "DISABLED" -> 50;
            default -> 200;
        };
        int typeBoost = switch (normalize(type)) {
            case "WAREHOUSE_LOAD", "OBJECT_LOAD", "CDC_JOB", "PIPELINE" -> 50;
            case "QUALITY", "METADATA_SCAN" -> 30;
            default -> 10;
        };
        return base + typeBoost;
    }

    private static String poolLane(Object status) {
        String st = normalize(String.valueOf(status));
        if (SetHolder.ACTIVE.contains(st)) return "ACTIVE";
        if ("FAILED".equals(st)) return "FAILED";
        if ("WAITING".equals(st)) return "SCHEDULED";
        if (SetHolder.SUCCESS.contains(st)) return "COMPLETED";
        return "OTHER";
    }

    private static boolean isActive(Object status){return SetHolder.ACTIVE.contains(normalize(String.valueOf(status)));}
    private static String normalize(String value){return value==null?"":value.trim().toUpperCase(Locale.ROOT);}
    private static Long id(Object value){try{return value==null?null:Long.valueOf(String.valueOf(value));}catch(NumberFormatException ignored){return null;}}
    private static final class SetHolder {
        static final java.util.Set<String> ACTIVE = java.util.Set.of("QUEUED", "PENDING", "RUNNING");
        static final java.util.Set<String> SUCCESS = java.util.Set.of("SUCCEEDED", "SUCCESS");
    }
}
