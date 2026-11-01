package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.FieldStandardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "字段标准")
@RestController
@RequestMapping("/bi/field-standards")
public class FieldStandardController {
    private final FieldStandardService standards;
    private final CatalogAuditService audit;

    public FieldStandardController(FieldStandardService standards, CatalogAuditService audit) {
        this.standards = standards;
        this.audit = audit;
    }

    @Operation(summary = "列出字段标准字典")
    @GetMapping
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> list() {
        return standards.list();
    }

    @Operation(summary = "查询字段标准")
    @GetMapping("/{id}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> get(@PathVariable Long id) {
        return standards.get(id);
    }

    @Operation(summary = "创建字段标准")
    @PostMapping
    @BiPermission("bi:resource:admin")
    public Map<String, Object> create(@RequestBody FieldStandardService.UpsertRequest request) {
        Map<String, Object> row = standards.create(request);
        audit.record("FIELD_STANDARD_CREATE", "FIELD_STANDARD", number(row.get("id")), null,
                Map.of("standardCode", row.get("standardCode"), "status", row.get("status")));
        return row;
    }

    @Operation(summary = "更新字段标准")
    @PutMapping("/{id}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody FieldStandardService.UpsertRequest request) {
        Map<String, Object> row = standards.update(id, request);
        audit.record("FIELD_STANDARD_UPDATE", "FIELD_STANDARD", id, null,
                Map.of("standardCode", row.get("standardCode"), "status", row.get("status")));
        return row;
    }

    @Operation(summary = "列出字段标准绑定")
    @GetMapping("/bindings")
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> bindings() {
        return standards.listBindings();
    }

    @Operation(summary = "绑定目录 COLUMN 到字段标准")
    @PostMapping("/bindings")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> bind(@RequestBody FieldStandardService.BindRequest request) {
        Map<String, Object> row = standards.bind(request);
        audit.record("FIELD_STANDARD_BIND", "FIELD_STANDARD", number(row.get("standardId")), number(row.get("assetId")),
                Map.of("assetId", row.get("assetId")));
        return row;
    }

    @Operation(summary = "解除字段标准绑定")
    @DeleteMapping("/bindings/{assetId}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> unbind(@PathVariable Long assetId) {
        standards.unbind(assetId);
        audit.record("FIELD_STANDARD_UNBIND", "FIELD_STANDARD", null, assetId, Map.of("assetId", assetId));
        return Map.of("assetId", assetId, "unbound", true);
    }

    @Operation(summary = "列出字段标准批量绑定计划")
    @GetMapping("/bind-plans")
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> bindPlans() {
        return standards.listBindPlans();
    }

    @Operation(summary = "查询批量绑定计划")
    @GetMapping("/bind-plans/{id}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> bindPlan(@PathVariable Long id) {
        return standards.getBindPlan(id);
    }

    @Operation(summary = "创建批量绑定计划（DRAFT）")
    @PostMapping("/bind-plans")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> createBindPlan(@RequestBody FieldStandardService.CreateBindPlanRequest request) {
        Map<String, Object> row = standards.createBindPlan(request);
        audit.record("FIELD_STANDARD_BIND_PLAN_CREATE", "FIELD_STANDARD_BIND_PLAN", number(row.get("id")), null,
                Map.of("name", row.get("name"), "bindings", ((List<?>) row.getOrDefault("bindings", List.of())).size()));
        return row;
    }

    @Operation(summary = "审批批量绑定计划")
    @PostMapping("/bind-plans/{id}/approve")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> approveBindPlan(@PathVariable Long id) {
        Map<String, Object> row = standards.approveBindPlan(id);
        audit.record("FIELD_STANDARD_BIND_PLAN_APPROVE", "FIELD_STANDARD_BIND_PLAN", id, null, Map.of());
        return row;
    }

    @Operation(summary = "应用已审批的批量绑定计划")
    @PostMapping("/bind-plans/{id}/apply")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> applyBindPlan(@PathVariable Long id) {
        Map<String, Object> row = standards.applyBindPlan(id);
        audit.record("FIELD_STANDARD_BIND_PLAN_APPLY", "FIELD_STANDARD_BIND_PLAN", id, null, Map.of());
        return row;
    }

    @Operation(summary = "落标检查：扫描目录字段相对标准字典的合规性")
    @GetMapping("/compliance")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> compliance(@RequestParam(required = false) Integer limit) {
        return standards.complianceCheck(limit);
    }

    @Operation(summary = "强制落标门禁预检（绑定且 enforceGate 的列）")
    @GetMapping("/enforce-gate")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> enforceGate(@RequestParam Long assetId) {
        return standards.enforceGateCheck(assetId);
    }

    @Operation(summary = "值域抽样校验（pattern/enum/maxLength）")
    @PostMapping("/{id}/sample-validate")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> sampleValidate(@PathVariable Long id,
            @RequestBody FieldStandardService.SampleValidateRequest request) {
        return standards.sampleValidate(id, request);
    }

    @Operation(summary = "源表 JDBC 在线抽样校验（COLUMN 资产）")
    @PostMapping("/online-sample-validate")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> onlineSampleValidate(@RequestParam Long assetId,
            @RequestParam(required = false) Integer limit) {
        return standards.onlineSampleValidate(assetId, limit);
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
