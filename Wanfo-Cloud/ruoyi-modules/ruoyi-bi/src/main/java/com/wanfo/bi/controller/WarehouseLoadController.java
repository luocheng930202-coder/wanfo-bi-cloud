package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.WarehouseLoadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数仓批量入仓")
@RestController
@RequestMapping("/bi/warehouse/loads")
public class WarehouseLoadController
{
    private final WarehouseLoadService loads;
    private final CatalogAuditService audit;

    public WarehouseLoadController(WarehouseLoadService loads, CatalogAuditService audit)
    {
        this.loads = loads;
        this.audit = audit;
    }

    @Operation(summary = "触发源表全量入仓")
    @PostMapping
    @BiPermission("bi:resource:admin")
    public Map<String, Object> trigger(@RequestBody WarehouseLoadService.LoadRequest request)
    {
        Map<String, Object> run = loads.trigger(request, MDC.get("trace_id"));
        audit.record("WAREHOUSE_LOAD_TRIGGER", "WAREHOUSE_LOAD_RUN", number(run.get("id")),
                number(run.get("sourceDatasourceId")), Map.of("targetDatasourceId", run.get("targetDatasourceId"),
                        "targetTable", run.get("targetTable"), "loadMode", run.get("loadMode")));
        return run;
    }

    @Operation(summary = "查询数仓目标的入仓运行记录")
    @GetMapping("/runs")
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> runs(@RequestParam Long targetDatasourceId)
    {
        List<Map<String, Object>> result = loads.runs(targetDatasourceId);
        audit.record("WAREHOUSE_LOAD_RUN_LIST", "DATASOURCE", targetDatasourceId, targetDatasourceId,
                Map.of("returned", result.size()));
        return result;
    }

    @Operation(summary = "查询入仓运行详情")
    @GetMapping("/runs/{id}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> run(@PathVariable Long id)
    {
        return loads.run(id);
    }

    @Operation(summary = "按请求快照重试失败的入仓任务")
    @PostMapping("/runs/{id}/retry")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> retry(@PathVariable Long id)
    {
        Map<String, Object> run = loads.retry(id, MDC.get("trace_id"));
        audit.record("WAREHOUSE_LOAD_RETRY", "WAREHOUSE_LOAD_RUN", id,
                number(run.get("sourceDatasourceId")), Map.of("newRunId", run.getOrDefault("id", 0L)));
        return run;
    }

    @Operation(summary = "装载运维概览：活动/孤儿运行与最近记录")
    @GetMapping("/ops")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> ops()
    {
        Map<String, Object> overview = loads.opsOverview();
        audit.record("WAREHOUSE_LOAD_OPS", "WAREHOUSE_LOAD_RUN", null, null,
                Map.of("activeRuns", overview.get("activeRuns"), "staleRuns", overview.get("staleRuns")));
        return overview;
    }

    private static Long number(Object value) { return value instanceof Number number ? number.longValue() : null; }
}
