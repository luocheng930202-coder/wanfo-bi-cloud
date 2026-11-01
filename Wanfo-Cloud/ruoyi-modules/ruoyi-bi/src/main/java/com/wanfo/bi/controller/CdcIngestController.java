package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.CdcIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CDC 入仓编排")
@RestController
@RequestMapping("/bi/warehouse/cdc-jobs")
public class CdcIngestController
{
    private final CdcIngestService cdc;
    private final CatalogAuditService audit;

    public CdcIngestController(CdcIngestService cdc, CatalogAuditService audit)
    {
        this.cdc = cdc;
        this.audit = audit;
    }

    @Operation(summary = "列出 CDC 编排任务（外部 Debezium staging → 仓）")
    @GetMapping
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> list()
    {
        return cdc.list();
    }

    @Operation(summary = "创建 CDC 编排任务")
    @PostMapping
    @BiPermission("bi:resource:admin")
    public Map<String, Object> create(@RequestBody CdcIngestService.CreateRequest request)
    {
        Map<String, Object> job = cdc.create(request);
        audit.record("CDC_JOB_CREATE", "CDC_INGEST_JOB", number(job.get("id")),
                number(job.get("sourceDatasourceId")), Map.of("jobCode", job.getOrDefault("jobCode", "")));
        return job;
    }

    @Operation(summary = "暂停 CDC 任务")
    @PostMapping("/{id}/pause")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> pause(@PathVariable Long id)
    {
        Map<String, Object> job = cdc.pause(id);
        audit.record("CDC_JOB_PAUSE", "CDC_INGEST_JOB", id, number(job.get("sourceDatasourceId")), Map.of());
        return job;
    }

    @Operation(summary = "恢复 CDC 任务")
    @PostMapping("/{id}/resume")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> resume(@PathVariable Long id)
    {
        Map<String, Object> job = cdc.resume(id);
        audit.record("CDC_JOB_RESUME", "CDC_INGEST_JOB", id, number(job.get("sourceDatasourceId")), Map.of());
        return job;
    }

    private static Long number(Object value)
    {
        return value instanceof Number n ? n.longValue() : null;
    }
}
