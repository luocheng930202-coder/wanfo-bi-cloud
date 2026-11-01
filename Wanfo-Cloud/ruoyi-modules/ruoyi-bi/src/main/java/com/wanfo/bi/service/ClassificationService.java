package com.wanfo.bi.service;

import com.wanfo.bi.mapper.ClassificationMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.DatasetDataPolicy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manual sensitivity classification on catalog assets. */
@Service
public class ClassificationService {
    private final ClassificationMapper mapper;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final CatalogAuditService audit;

    public ClassificationService(ClassificationMapper mapper, CapabilityProvider capabilities,
            CurrentActorProvider actors, CatalogAuditService audit) {
        this.mapper = mapper;
        this.capabilities = capabilities;
        this.actors = actors;
        this.audit = audit;
    }

    public List<Map<String, Object>> levels() {
        capabilities.require("bi:resource:admin");
        return mapper.listLevels();
    }

    public Map<String, Object> get(Long assetId) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> row = mapper.effective(assetId);
        if (row == null || row.get("assetId") == null) throw new IllegalArgumentException("资产不存在");
        Map<String, Object> out = new LinkedHashMap<>(row);
        if (out.get("levelCode") == null) {
            out.put("levelCode", "PUBLIC");
            out.put("source", "DEFAULT");
            out.put("rankNo", 10);
            out.put("defaultEnforcement", "NONE");
        }
        return out;
    }

    @Transactional
    public Map<String, Object> setManual(Long assetId, String levelCode, String note) {
        capabilities.require("bi:resource:admin");
        if (mapper.asset(assetId) == null) throw new IllegalArgumentException("资产不存在");
        String code = levelCode == null ? "" : levelCode.trim().toUpperCase(Locale.ROOT);
        Map<String, Object> level = mapper.level(code);
        if (level == null) throw new IllegalArgumentException("敏感级别无效");
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 500) throw new IllegalArgumentException("备注不能超过 500 字");
        String actor = actors.currentActor().username();
        Map<String, Object> row = new HashMap<>();
        row.put("assetId", assetId);
        row.put("levelCode", code);
        row.put("source", "MANUAL");
        row.put("inheritedFromAssetId", null);
        row.put("note", cleanNote);
        row.put("updateBy", actor);
        if (mapper.upsert(row) < 1) throw new IllegalStateException("分级保存失败");
        mapper.mirrorAsset(assetId, code, actor);
        audit.record("CLASSIFICATION_SET", "ASSET", assetId, null,
                Map.of("levelCode", code, "source", "MANUAL"));
        return get(assetId);
    }

    /**
     * Build a DataPolicy scope for connector table preview from classified COLUMN assets.
     * Unknown/unscanned tables stay unrestricted. Admins still receive unrestricted scopes.
     */
    public DatasetDataPolicy.Scope scopeForConnectorPreview(Long datasourceId, String tableName, List<String> fields) {
        if (capabilities.has("bi:resource:admin")) return DatasetDataPolicy.Scope.unrestricted();
        if (datasourceId == null || tableName == null || tableName.isBlank() || fields == null || fields.isEmpty())
            return DatasetDataPolicy.Scope.unrestricted();
        List<Map<String, Object>> levels = mapper.connectorColumnLevels(datasourceId, tableName.trim());
        if (levels.isEmpty()) return DatasetDataPolicy.Scope.unrestricted();
        Set<String> selected = new HashSet<>();
        for (String field : fields) if (field != null && !field.isBlank()) selected.add(field);
        Set<String> denied = new HashSet<>();
        Set<String> masked = new HashSet<>();
        for (Map<String, Object> row : levels) {
            String name = String.valueOf(row.get("fieldName"));
            if (!selected.contains(name)) continue;
            String enforcement = String.valueOf(row.getOrDefault("enforcement", "NONE")).toUpperCase(Locale.ROOT);
            if ("DENY".equals(enforcement)) denied.add(name);
            else if ("MASK".equals(enforcement)) masked.add(name);
        }
        masked.removeAll(denied);
        if (denied.isEmpty() && masked.isEmpty()) return DatasetDataPolicy.Scope.unrestricted();
        return new DatasetDataPolicy.Scope(List.of(), Set.copyOf(denied), Set.copyOf(masked));
    }
}
