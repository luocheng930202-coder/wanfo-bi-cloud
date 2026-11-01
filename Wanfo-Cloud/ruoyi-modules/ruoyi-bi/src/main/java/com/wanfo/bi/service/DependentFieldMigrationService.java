package com.wanfo.bi.service;

import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.mapper.DependentFieldMigrationMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * After file/API structure replace, auto-remaps dependent physical dataset fields when names uniquely match
 * (case/whitespace/punctuation insensitive). Supports rollback to pre-migration snapshots.
 */
@Service
public class DependentFieldMigrationService {
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private final DependentFieldMigrationMapper mapper;
    private final BiDatasetTableMapper datasets;
    private final BiDatasetFieldMapper fields;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final CatalogAuditService audit;
    private final ObjectMapper json;

    public DependentFieldMigrationService(DependentFieldMigrationMapper mapper, BiDatasetTableMapper datasets,
            BiDatasetFieldMapper fields, CapabilityProvider capabilities, CurrentActorProvider actors,
            CatalogAuditService audit, ObjectMapper json) {
        this.mapper = mapper;
        this.datasets = datasets;
        this.fields = fields;
        this.capabilities = capabilities;
        this.actors = actors;
        this.audit = audit;
        this.json = json;
    }

    /** Suggest unique normalized mappings from old field names to new schema names. Ambiguous targets are omitted. */
    public Map<String, String> suggest(List<String> oldNames, List<String> newNames) {
        Map<String, List<String>> byNorm = new LinkedHashMap<>();
        for (String name : newNames == null ? List.<String>of() : newNames) {
            if (name == null || name.isBlank()) continue;
            byNorm.computeIfAbsent(normalize(name), ignored -> new ArrayList<>()).add(name);
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String old : oldNames == null ? List.<String>of() : oldNames) {
            if (old == null || old.isBlank()) continue;
            List<String> candidates = byNorm.getOrDefault(normalize(old), List.of());
            if (candidates.size() == 1) mapping.put(old, candidates.get(0));
        }
        return mapping;
    }

    @Transactional
    public Map<String, Object> applyForDatasource(Long datasourceId, List<String> newColumnNames) {
        capabilities.require("bi:resource:admin");
        if (datasourceId == null) throw new IllegalArgumentException("数据源不能为空");
        List<BiDatasetTableDO> dependents = datasets.selectAll(null).stream()
                .filter(ds -> datasourceId.equals(ds.getDatasourceId()) && "table".equals(ds.getType())
                        && (ds.getModelJson() == null || ds.getModelJson().isBlank()))
                .toList();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, String> applied = new LinkedHashMap<>();
        List<Map<String, Object>> changes = new ArrayList<>();
        for (BiDatasetTableDO ds : dependents) {
            List<BiDatasetFieldDO> current = fields.selectByDatasetId(ds.getId());
            List<String> oldNames = current.stream().filter(f -> "0".equals(f.getExtFlag()) && !"2".equals(f.getDelFlag()))
                    .map(f -> f.getSourceName() == null || f.getSourceName().isBlank() ? f.getOriginName() : f.getSourceName())
                    .toList();
            Map<String, String> mapping = suggest(oldNames, newColumnNames);
            if (mapping.isEmpty()) continue;
            List<Map<String, Object>> fieldSnaps = new ArrayList<>();
            for (BiDatasetFieldDO field : current) {
                if (!"0".equals(field.getExtFlag()) || "2".equals(field.getDelFlag())) continue;
                String physical = field.getSourceName() == null || field.getSourceName().isBlank()
                        ? field.getOriginName() : field.getSourceName();
                String target = mapping.get(physical);
                Map<String, Object> snap = new LinkedHashMap<>();
                snap.put("fieldId", field.getId());
                snap.put("originName", field.getOriginName());
                snap.put("sourceName", field.getSourceName());
                snap.put("originType", field.getOriginType());
                snap.put("delFlag", field.getDelFlag());
                fieldSnaps.add(snap);
                if (target == null || target.equalsIgnoreCase(physical)) continue;
                fields.applySourceMapping(ds.getId(), field.getId(), physical, target, field.getOriginType());
                applied.put(ds.getId() + ":" + physical, target);
                changes.add(Map.of("datasetId", ds.getId(), "from", physical, "to", target));
            }
            before.put(String.valueOf(ds.getId()), fieldSnaps);
        }
        if (changes.isEmpty()) return Map.of("applied", false, "changes", List.of());
        Map<String, Object> row = new HashMap<>();
        row.put("datasourceId", datasourceId);
        try {
            row.put("mappingJson", json.writeValueAsString(applied));
            row.put("beforeSnapshotJson", json.writeValueAsString(before));
        } catch (Exception error) {
            throw new IllegalStateException("依赖字段迁移快照保存失败", error);
        }
        row.put("createBy", actors.currentActor().username());
        if (mapper.insert(row) != 1 || !(row.get("id") instanceof Number id))
            throw new IllegalStateException("依赖字段迁移记录创建失败");
        audit.record("DEPENDENT_FIELD_MIGRATE", "DATASOURCE", datasourceId, datasourceId,
                Map.of("migrationId", id.longValue(), "changes", changes.size()));
        return Map.of("applied", true, "migrationId", id.longValue(), "changes", changes);
    }

    @Transactional
    public Map<String, Object> rollback(Long migrationId) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> row = mapper.find(migrationId);
        if (row == null || !"APPLIED".equals(row.get("status")))
            throw new IllegalArgumentException("迁移记录不存在或不可回滚");
        Map<String, Object> before = readMap(row.get("beforeSnapshotJson"));
        for (Map.Entry<String, Object> entry : before.entrySet()) {
            Long datasetId = Long.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof List<?> snaps)) continue;
            for (Object item : snaps) {
                if (!(item instanceof Map<?, ?> snap)) continue;
                Long fieldId = number(snap.get("fieldId"));
                fields.restoreMigrationField(datasetId, fieldId, string(snap.get("sourceName")),
                        string(snap.get("originType")), string(snap.get("delFlag")));
            }
        }
        mapper.markRolledBack(migrationId, actors.currentActor().username());
        audit.record("DEPENDENT_FIELD_MIGRATE_ROLLBACK", "DATASOURCE", number(row.get("datasourceId")),
                number(row.get("datasourceId")), Map.of("migrationId", migrationId));
        return Map.of("rolledBack", true, "migrationId", migrationId);
    }

    public List<Map<String, Object>> history(Long datasourceId) {
        capabilities.require("bi:resource:admin");
        return mapper.listByDatasource(datasourceId);
    }

    private static String normalize(String value) {
        return NON_ALNUM.matcher(value.toLowerCase(Locale.ROOT).trim()).replaceAll("");
    }

    private Map<String, Object> readMap(Object raw) {
        try {
            return json.readValue(String.valueOf(raw), Map.class);
        } catch (Exception error) {
            return Map.of();
        }
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
