package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.WarehouseTableModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数仓维表/事实表声明")
@RestController
@RequestMapping("/bi/warehouse/table-models")
public class WarehouseTableModelController {
    private final WarehouseTableModelService models;
    private final CatalogAuditService audit;

    public WarehouseTableModelController(WarehouseTableModelService models, CatalogAuditService audit) {
        this.models = models;
        this.audit = audit;
    }

    @Operation(summary = "列出已声明的维表/事实表模型")
    @GetMapping
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> list() {
        return models.list();
    }

    @Operation(summary = "查询维表/事实表模型")
    @GetMapping("/{id}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> get(@PathVariable Long id) {
        return models.get(id);
    }

    @Operation(summary = "查询表模型消费门禁与最近对账")
    @GetMapping("/{id}/consumer-gate")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> consumerGate(@PathVariable Long id,
            @RequestParam(required = false) Long targetDatasourceId) {
        Map<String, Object> gate = models.consumerGate(id, targetDatasourceId);
        audit.record("WAREHOUSE_TABLE_MODEL_GATE", "WAREHOUSE_TABLE_MODEL", id, targetDatasourceId,
                Map.of("consumerGate", gate.getOrDefault("consumerGate", "OPEN")));
        return gate;
    }

    @Operation(summary = "声明维表或事实表（禁止客户端任意 DDL）")
    @PostMapping
    @BiPermission("bi:resource:admin")
    public Map<String, Object> create(@RequestBody WarehouseTableModelService.UpsertRequest request) {
        Map<String, Object> row = models.create(request);
        audit.record("WAREHOUSE_TABLE_MODEL_CREATE", "WAREHOUSE_TABLE_MODEL", number(row.get("id")), null,
                Map.of("modelCode", row.get("modelCode"), "tableKind", row.get("tableKind"), "layer", row.get("layer")));
        return row;
    }

    @Operation(summary = "更新维表/事实表声明")
    @PutMapping("/{id}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody WarehouseTableModelService.UpsertRequest request) {
        Map<String, Object> row = models.update(id, request);
        audit.record("WAREHOUSE_TABLE_MODEL_UPDATE", "WAREHOUSE_TABLE_MODEL", id, null,
                Map.of("modelCode", row.get("modelCode"), "status", row.get("status")));
        return row;
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
