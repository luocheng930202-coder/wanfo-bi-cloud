package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.WarehouseAuditRetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "仓操作审计")
@RestController
@RequestMapping("/bi/warehouse/audit")
public class WarehouseAuditController
{
    private final CatalogAuditService audit;
    private final WarehouseAuditRetentionService retention;

    public WarehouseAuditController(CatalogAuditService audit, WarehouseAuditRetentionService retention)
    {
        this.audit = audit;
        this.retention = retention;
    }

    @Operation(summary = "仓相关操作审计（动作前缀 WAREHOUSE_/OBJECT_STORAGE_/CDC_）")
    @GetMapping
    @BiPermission("bi:resource:admin")
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to)
    {
        // 未指定 action 时按仓相关前缀筛选（以 _ 结尾触发 LIKE）；也可传精确动作名。
        String actionFilter = action == null || action.isBlank() ? "WAREHOUSE_" : action.trim();
        if ("WAREHOUSE_OPS".equalsIgnoreCase(actionFilter))
            actionFilter = "WAREHOUSE_";
        return audit.list(page, pageSize, actor, actionFilter, outcome, from, to);
    }

    @Operation(summary = "仓审计在线保留策略")
    @GetMapping("/retention")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> retention()
    {
        return retention.policy();
    }

    @Operation(summary = "更新仓审计在线保留天数")
    @PutMapping("/retention")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> updateRetention(@RequestParam int liveRetentionDays)
    {
        return retention.update(liveRetentionDays);
    }
}
