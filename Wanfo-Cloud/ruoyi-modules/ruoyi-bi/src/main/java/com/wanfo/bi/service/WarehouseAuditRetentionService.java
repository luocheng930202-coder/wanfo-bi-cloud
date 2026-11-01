package com.wanfo.bi.service;

import com.wanfo.bi.mapper.BiMetadataScanMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Warehouse-scoped audit live retention; falls back to global policy when warehouse key is absent. */
@Service
public class WarehouseAuditRetentionService
{
    private final BiMetadataScanMapper mapper;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final CatalogAuditService audit;

    public WarehouseAuditRetentionService(BiMetadataScanMapper mapper, CapabilityProvider capabilities,
            CurrentActorProvider actors, CatalogAuditService audit)
    {
        this.mapper = mapper;
        this.capabilities = capabilities;
        this.actors = actors;
        this.audit = audit;
    }

    public Map<String, Object> policy()
    {
        capabilities.require("bi:resource:admin");
        Map<String, Object> warehouse = mapper.auditRetentionPolicyByKey("warehouse");
        if (warehouse == null)
        {
            Map<String, Object> global = mapper.auditRetentionPolicy();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("policyKey", "warehouse");
            result.put("liveRetentionDays", global == null ? 365 : global.get("liveRetentionDays"));
            result.put("inheritsGlobal", true);
            result.put("archiveRetention", "indefinite");
            result.put("updatedBy", "system");
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>(warehouse);
        result.put("policyKey", "warehouse");
        result.put("inheritsGlobal", false);
        result.put("archiveRetention", "indefinite");
        return result;
    }

    @Transactional
    public Map<String, Object> update(int days)
    {
        capabilities.require("bi:resource:admin");
        if (days < 365 || days > 3650)
            throw new IllegalArgumentException("仓审计在线保留期限须为 365～3650 天；归档记录长期保留");
        String actor = actors.currentActor().username();
        if (mapper.upsertAuditRetention("warehouse", days, actor) != 1)
            throw new IllegalStateException("仓审计保留策略更新失败");
        audit.record("WAREHOUSE_AUDIT_RETENTION_UPDATE", "AUDIT_POLICY", null, null,
                Map.of("policyKey", "warehouse", "liveRetentionDays", days));
        return policy();
    }
}
