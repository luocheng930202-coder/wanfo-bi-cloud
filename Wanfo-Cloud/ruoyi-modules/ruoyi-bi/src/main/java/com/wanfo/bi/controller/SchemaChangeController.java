package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.OpsImpactPackService;
import com.wanfo.bi.service.SchemaChangeImpactService;
import com.wanfo.bi.service.SchemaMigrationPlanService;
import com.wanfo.bi.service.SchemaMigrationPlanService.FieldMapping;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/bi/catalog")
public class SchemaChangeController
{
    private final SchemaChangeImpactService changes;
    private final OpsImpactPackService impactPack;
    private final CatalogAuditService audit;
    private final SchemaMigrationPlanService migrations;

    public SchemaChangeController(SchemaChangeImpactService changes, OpsImpactPackService impactPack,
            CatalogAuditService audit, SchemaMigrationPlanService migrations)
    {
        this.changes = changes; this.impactPack = impactPack; this.audit = audit; this.migrations=migrations;
    }

    @GetMapping("/assets/{assetId}/changes")
    @BiPermission("bi:datasource:model")
    public List<Map<String,Object>> events(@PathVariable Long assetId)
    {
        List<Map<String,Object>> result = changes.events(assetId);
        audit.record("SCHEMA_CHANGE_LIST", "ASSET", assetId, null, Map.of("returned", result.size()));
        return result;
    }

    @GetMapping("/assets/{assetId}/impact-pack")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> impactPack(@PathVariable Long assetId)
    {
        Map<String,Object> result = impactPack.packForAsset(assetId);
        Object summary = result.get("summary");
        audit.record("OPS_IMPACT_PACK", "ASSET", assetId, null,
                Map.of("summary", summary == null ? Map.of() : summary));
        return result;
    }

    @GetMapping("/assets/{assetId}/change-gate")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> changeGate(@PathVariable Long assetId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean override)
    {
        Map<String,Object> result = impactPack.changeGate(assetId, override);
        audit.record("OPS_CHANGE_GATE", "ASSET", assetId, null,
                Map.of("allowed", result.get("allowed"), "severity", result.get("severity"), "override", override));
        return result;
    }

    @GetMapping("/schema-changes/{eventId}/impact")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> impact(@PathVariable Long eventId)
    {
        Map<String,Object> result = changes.impact(eventId);
        audit.record("SCHEMA_CHANGE_IMPACT_PREVIEW", "SCHEMA_CHANGE_EVENT", eventId, null,
                Map.of("counts", result.get("counts"), "reviewRequired", result.get("reviewRequired"), "truncated", result.get("truncated")));
        return result;
    }

    public record CreateMigrationPlanRequest(List<FieldMapping> mappings) { }

    @GetMapping("/schema-changes/{eventId}/migration-plans")
    @BiPermission("bi:resource:admin")
    public List<Map<String,Object>> plans(@PathVariable Long eventId) { return migrations.list(eventId); }

    @PostMapping("/schema-changes/{eventId}/migration-plans")
    @BiPermission("bi:resource:admin")
    public Map<String,Object> createPlan(@PathVariable Long eventId,@RequestBody CreateMigrationPlanRequest request)
    { return migrations.create(eventId,request==null?null:request.mappings()); }

    @GetMapping("/schema-migration-plans/{id}")
    @BiPermission("bi:resource:admin")
    public Map<String,Object> plan(@PathVariable Long id) { return migrations.detail(id); }

    @GetMapping("/schema-migration-plans/{id}/revisions")
    @BiPermission("bi:resource:admin")
    public List<Map<String,Object>> planRevisions(@PathVariable Long id) { return migrations.revisions(id); }

    @PostMapping("/schema-migration-plans/{id}/approve")
    @BiPermission("bi:resource:admin")
    public Map<String,Object> approve(@PathVariable Long id) { return migrations.approve(id); }

    @PostMapping("/schema-migration-plans/{id}/execute")
    @BiPermission("bi:resource:admin")
    public Map<String,Object> execute(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean overrideBlocking,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String overrideTicket)
    {
        try { return migrations.execute(id, overrideBlocking, overrideTicket); }
        catch(Exception failure) { try { migrations.recordFailure(id,failure.getMessage()); } catch(RuntimeException ignored) { } if(failure instanceof RuntimeException runtime) throw runtime; throw new IllegalStateException("迁移执行失败",failure); }
    }

    @PostMapping("/schema-migration-plans/{id}/rollback")
    @BiPermission("bi:resource:admin")
    public Map<String,Object> rollback(@PathVariable Long id) { return migrations.rollback(id); }
}
