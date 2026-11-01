package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.MetadataScanService;
import com.wanfo.bi.service.CatalogAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据平台元数据扫描")
@RestController
@RequestMapping("/bi/metadata-scans")
public class MetadataScanController
{
    public record CreateRequest(Long datasourceId, String name, Map<String, Object> scope, String status) { }

    private final MetadataScanService service;
    private final CatalogAuditService audit;

    public MetadataScanController(MetadataScanService service, CatalogAuditService audit)
    {
        this.service = service;
        this.audit = audit;
    }

    @Operation(summary = "列出数据源的元数据扫描配置")
    @GetMapping
    @BiPermission("bi:datasource:model")
    public List<Map<String, Object>> list(@RequestParam Long datasourceId)
    {
        List<Map<String, Object>> result = service.listDefinitions(datasourceId);
        audit.record("SCAN_CONFIG_LIST", "DATASOURCE", datasourceId, datasourceId, Map.of("returned", result.size()));
        return result;
    }

    @Operation(summary = "创建元数据扫描配置")
    @PostMapping
    @BiPermission("bi:datasource:model")
    public Map<String, Object> create(@RequestBody CreateRequest request)
    {
        if (request == null) throw new IllegalArgumentException("扫描配置不能为空");
        Map<String, Object> result = service.create(request.datasourceId(),
                new MetadataScanService.SaveRequest(request.name(), request.scope(), request.status()));
        audit.record("SCAN_CONFIG_CREATE", "SCAN", number(result.get("id")), request.datasourceId(), Map.of("status", result.getOrDefault("status", "")));
        return result;
    }

    @Operation(summary = "更新元数据扫描配置")
    @PutMapping("/{id}")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody MetadataScanService.SaveRequest request)
    {
        Map<String, Object> result = service.update(id, request);
        audit.record("SCAN_CONFIG_UPDATE", "SCAN", id, number(result.get("datasourceId")), Map.of("status", result.getOrDefault("status", "")));
        return result;
    }

    @Operation(summary = "删除元数据扫描配置")
    @DeleteMapping("/{id}")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> delete(@PathVariable Long id)
    {
        Map<String, Object> result = service.delete(id);
        audit.record("SCAN_CONFIG_DELETE", "SCAN", id, null, result);
        return result;
    }

    @Operation(summary = "手动触发元数据扫描")
    @PostMapping("/{id}/runs")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> trigger(@PathVariable Long id)
    {
        Map<String, Object> result = service.trigger(id, MDC.get("trace_id"));
        audit.record("SCAN_RUN_TRIGGER", "SCAN_RUN", number(result.get("id")), number(result.get("datasourceId")), Map.of("scanId", id, "status", result.getOrDefault("status", "")));
        return result;
    }

    @Operation(summary = "查询数据源的扫描运行记录")
    @GetMapping("/runs")
    @BiPermission("bi:datasource:model")
    public List<Map<String, Object>> runs(@RequestParam Long datasourceId)
    {
        List<Map<String, Object>> result = service.listRuns(datasourceId);
        audit.record("SCAN_RUN_LIST", "DATASOURCE", datasourceId, datasourceId, Map.of("returned", result.size()));
        return result;
    }

    @Operation(summary = "查看扫描运行详情")
    @GetMapping("/runs/{runId}")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> run(@PathVariable Long runId)
    {
        // Polled by the UI while scans run; the trigger and run-list operations are audited
        // to avoid creating a noisy audit row for every status refresh.
        return service.getRun(runId);
    }

    private Long number(Object value) { return value instanceof Number number ? number.longValue() : null; }
}
