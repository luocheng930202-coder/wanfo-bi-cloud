package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.ChangeTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "变更工单")
@RestController
@RequestMapping("/bi/catalog/change-tickets")
public class ChangeTicketController {
    private final ChangeTicketService tickets;
    private final CatalogAuditService audit;

    public ChangeTicketController(ChangeTicketService tickets, CatalogAuditService audit) {
        this.tickets = tickets;
        this.audit = audit;
    }

    @Operation(summary = "列出变更工单")
    @GetMapping
    @BiPermission("bi:datasource:model")
    public List<Map<String, Object>> list(@RequestParam(required = false) Long assetId) {
        return tickets.list(assetId);
    }

    @Operation(summary = "查询变更工单")
    @GetMapping("/{id}")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> get(@PathVariable Long id) {
        return tickets.get(id);
    }

    @Operation(summary = "手工创建变更工单")
    @PostMapping
    @BiPermission("bi:resource:admin")
    public Map<String, Object> create(@RequestBody ChangeTicketService.CreateRequest request) {
        return tickets.create(request);
    }

    @Operation(summary = "从运维影响包生成变更工单")
    @PostMapping("/from-impact-pack")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> fromImpactPack(@RequestParam Long assetId) {
        Map<String, Object> row = tickets.createFromImpactPack(assetId);
        audit.record("CHANGE_TICKET_FROM_IMPACT", "CHANGE_TICKET", number(row.get("id")), number(row.get("assetId")),
                Map.of("severity", row.get("severity")));
        return row;
    }

    @Operation(summary = "审批变更工单")
    @PostMapping("/{id}/approve")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> approve(@PathVariable Long id) {
        return tickets.approve(id);
    }

    @Operation(summary = "关闭变更工单")
    @PostMapping("/{id}/close")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> close(@PathVariable Long id) {
        return tickets.close(id);
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
