package com.wanfo.bi.controller;

import java.util.List;
import java.util.Map;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.security.PrincipalCatalog;
import com.wanfo.bi.service.ChartQueryService;
import com.wanfo.bi.service.MetricDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "BI 数据指标")
@RestController
@RequestMapping("/bi/metric")
public class MetricController {
    private final MetricDefinitionService metrics;
    private final ChartQueryService queries;
    private final PrincipalCatalog principals;

    public MetricController(MetricDefinitionService metrics, ChartQueryService queries, PrincipalCatalog principals) {
        this.metrics = metrics;
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping @BiPermission("bi:metric:list")
    public List<Map<String, Object>> list() { return metrics.list(); }

    @GetMapping("/{id}") @BiPermission("bi:metric:list")
    public Map<String, Object> detail(@PathVariable Long id) { return metrics.detail(id); }

    @PostMapping @BiPermission("bi:metric:add")
    public Map<String, Object> create(@RequestBody MetricDefinitionService.SaveRequest request) { return metrics.create(request); }

    @PutMapping("/{id}") @BiPermission("bi:metric:edit")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody MetricDefinitionService.SaveRequest request) {
        return metrics.update(id, request);
    }

    @DeleteMapping("/{id}") @BiPermission("bi:metric:remove")
    public Map<String, Object> delete(@PathVariable Long id) { return metrics.delete(id); }

    @Operation(summary = "草稿预览取数")
    @PostMapping("/preview") @BiPermission("bi:dataset:query")
    public Map<String, Object> preview(@RequestBody MetricDefinitionService.Definition definition) throws Exception {
        return metrics.preview(definition);
    }

    @Operation(summary = "已保存指标取数（已发布用冻结快照）")
    @PostMapping("/{id}/data") @BiPermission("bi:metric:query")
    public Map<String, Object> data(@PathVariable Long id) throws Exception { return metrics.data(id); }

    @Operation(summary = "按编码取已发布指标，不允许再带条件")
    @PostMapping("/code/{code}/data") @BiPermission("bi:metric:query")
    public Map<String, Object> dataByCode(@PathVariable String code) throws Exception { return metrics.dataByCode(code); }

    public record VersionRequest(Integer version) { }

    @PostMapping("/{id}/publish") @BiPermission("bi:metric:publish")
    public Map<String, Object> publish(@PathVariable Long id, @RequestBody VersionRequest request) {
        return metrics.publish(id, request.version());
    }

    @PostMapping("/{id}/unpublish") @BiPermission("bi:metric:publish")
    public Map<String, Object> unpublish(@PathVariable Long id, @RequestBody VersionRequest request) {
        return metrics.unpublish(id, request.version());
    }

    @GetMapping("/{id}/history") @BiPermission("bi:metric:list")
    public List<Map<String, Object>> history(@PathVariable Long id) { return metrics.history(id); }

    @GetMapping("/{id}/usages") @BiPermission("bi:metric:list")
    public List<Map<String, Object>> usages(@PathVariable Long id) { return metrics.usages(id); }

    @PostMapping("/{id}/usages") @BiPermission("bi:metric:query")
    public Map<String, Object> bind(@PathVariable Long id, @RequestBody MetricDefinitionService.UsageRequest request) {
        return metrics.bind(id, request);
    }

    @DeleteMapping("/{id}/usages/{refType}/{refId}") @BiPermission("bi:metric:query")
    public Map<String, Object> unbind(@PathVariable Long id, @PathVariable String refType, @PathVariable String refId) {
        return metrics.unbind(id, refType, refId);
    }

    @GetMapping("/dataset/{datasetId}/fields") @BiPermission("bi:dataset:query")
    public List<Map<String, Object>> datasetFields(@PathVariable Long datasetId) { return queries.datasetFields(datasetId); }

    @GetMapping("/principals") @BiPermission("bi:metric:grant")
    public List<Map<String, Object>> principals(@RequestParam(defaultValue = "") String query) { return principals.search(query); }

    @GetMapping("/{id}/grants") @BiPermission("bi:metric:grant")
    public List<Map<String, Object>> grants(@PathVariable Long id) { return metrics.grants(id); }

    public record GrantRequest(Long principalId, String action) { }

    @PostMapping("/{id}/grants") @BiPermission("bi:metric:grant")
    public Map<String, Object> grant(@PathVariable Long id, @RequestBody GrantRequest request) {
        metrics.grant(id, request.principalId(), request.action(), true);
        return Map.of("granted", true);
    }

    @DeleteMapping("/{id}/grants/{principalId}/{action}") @BiPermission("bi:metric:grant")
    public Map<String, Object> revoke(@PathVariable Long id, @PathVariable Long principalId, @PathVariable String action) {
        metrics.grant(id, principalId, action, false);
        return Map.of("revoked", true);
    }
}
