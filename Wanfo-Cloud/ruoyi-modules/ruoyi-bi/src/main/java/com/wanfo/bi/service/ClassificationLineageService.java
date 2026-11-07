package com.wanfo.bi.service;

import com.wanfo.bi.mapper.ClassificationMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Propagates sensitivity along COLUMN lineage edges: target rank = max(current, upstream).
 * MANUAL classifications are never overwritten.
 */
@Service
public class ClassificationLineageService {
    private final ClassificationMapper mapper;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final CatalogAuditService audit;

    public ClassificationLineageService(ClassificationMapper mapper, CapabilityProvider capabilities,
            CurrentActorProvider actors, CatalogAuditService audit) {
        this.mapper = mapper;
        this.capabilities = capabilities;
        this.actors = actors;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> recomputeInheritance() {
        capabilities.require("bi:resource:admin");
        return recomputeGenerated();
    }

    @Transactional
    Map<String, Object> recomputeGenerated() {
        String actor = actors.currentActor().username();
        List<Map<String, Object>> edges = mapper.activeColumnEdges();
        int updated = 0;
        for (Map<String, Object> edge : edges) {
            Long sourceId = number(edge.get("sourceAssetId"));
            Long targetId = number(edge.get("targetAssetId"));
            if (sourceId == null || targetId == null) continue;
            Map<String, Object> source = mapper.effective(sourceId);
            Map<String, Object> target = mapper.effective(targetId);
            if (source == null || target == null) continue;
            if ("MANUAL".equals(String.valueOf(target.get("source")))) continue;
            int sourceRank = intVal(source.get("rankNo"), 10);
            int targetRank = intVal(target.get("rankNo"), 10);
            String sourceLevel = stringOr(source.get("levelCode"), "PUBLIC");
            if (sourceRank <= targetRank) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("assetId", targetId);
            row.put("levelCode", sourceLevel);
            row.put("source", "INHERITED");
            row.put("inheritedFromAssetId", sourceId);
            row.put("note", "lineage edge " + edge.get("edgeId"));
            row.put("updateBy", actor);
            if (mapper.upsert(row) >= 1) {
                mapper.mirrorAsset(targetId, sourceLevel, actor);
                updated++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("edgesScanned", edges.size());
        summary.put("inheritedUpdated", updated);
        audit.record("CLASSIFICATION_INHERIT_REBUILD", "LINEAGE", null, null, summary);
        return summary;
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static int intVal(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static String stringOr(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }
}
