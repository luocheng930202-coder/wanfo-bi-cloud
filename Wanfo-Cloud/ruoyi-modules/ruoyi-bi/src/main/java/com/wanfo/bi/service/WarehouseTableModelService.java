package com.wanfo.bi.service;

import com.wanfo.bi.mapper.WarehouseLoadMapper;
import com.wanfo.bi.mapper.WarehouseTableModelMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Declares dimension/fact warehouse models; clients cannot submit arbitrary DDL. */
@Service
public class WarehouseTableModelService {
    private static final Set<String> LAYERS = Set.of("ODS", "DWD", "DWS", "ADS");
    private static final Set<String> KINDS = Set.of("DIMENSION", "FACT");
    private static final Set<String> STRATEGIES = Set.of("REPLACE", "MERGE");
    private static final Set<String> GATES = Set.of("OPEN", "REQUIRE_PASS_RECON");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "DEPRECATED");
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{1,95}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");

    public record UpsertRequest(String modelCode, String name, String layer, String tableName, String tableKind,
            List<String> primaryKeys, String partitionField, String updateStrategy, String description, String status,
            String consumerGate, Integer freshnessSlaMinutes) {
        public UpsertRequest(String modelCode, String name, String layer, String tableName, String tableKind,
                List<String> primaryKeys, String partitionField, String updateStrategy, String description, String status) {
            this(modelCode, name, layer, tableName, tableKind, primaryKeys, partitionField, updateStrategy, description,
                    status, null, null);
        }
    }

    private final WarehouseTableModelMapper mapper;
    private final WarehouseLoadMapper loads;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final ObjectMapper json;

    public WarehouseTableModelService(WarehouseTableModelMapper mapper, WarehouseLoadMapper loads,
            CapabilityProvider capabilities, CurrentActorProvider actors, ObjectMapper json) {
        this.mapper = mapper;
        this.loads = loads;
        this.capabilities = capabilities;
        this.actors = actors;
        this.json = json;
    }

    public List<Map<String, Object>> list() {
        capabilities.require("bi:resource:admin");
        return mapper.list().stream().map(this::decode).toList();
    }

    public Map<String, Object> get(Long id) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> row = mapper.find(id);
        if (row == null) throw new IllegalArgumentException("数仓表模型不存在");
        return decode(row);
    }

    /** Read consumer gate plus latest reconciliation row for the model code. */
    public Map<String, Object> consumerGate(Long id, Long targetDatasourceId) {
        Map<String, Object> model = get(id);
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("modelId", model.get("id"));
        gate.put("modelCode", model.get("modelCode"));
        gate.put("layer", model.get("layer"));
        gate.put("tableName", model.get("tableName"));
        gate.put("consumerGate", model.getOrDefault("consumerGate", "OPEN"));
        gate.put("freshnessSlaMinutes", model.get("freshnessSlaMinutes"));
        gate.put("updateStrategy", model.get("updateStrategy"));
        if (targetDatasourceId != null && targetDatasourceId > 0) {
            gate.put("latestReconciliation",
                    loads.latestReconciliationForModel(targetDatasourceId, String.valueOf(model.get("modelCode"))));
        }
        return gate;
    }

    @Transactional
    public Map<String, Object> create(UpsertRequest request) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> row = validate(request, true);
        row.put("createBy", actors.currentActor().username());
        if (mapper.insert(row) != 1 || !(row.get("id") instanceof Number))
            throw new IllegalStateException("数仓表模型创建失败");
        return get(((Number) row.get("id")).longValue());
    }

    @Transactional
    public Map<String, Object> update(Long id, UpsertRequest request) {
        capabilities.require("bi:resource:admin");
        if (mapper.find(id) == null) throw new IllegalArgumentException("数仓表模型不存在");
        Map<String, Object> row = validate(request, false);
        row.put("id", id);
        row.put("updateBy", actors.currentActor().username());
        if (mapper.update(row) != 1) throw new IllegalStateException("数仓表模型更新失败");
        return get(id);
    }

    private Map<String, Object> validate(UpsertRequest request, boolean creating) {
        if (request == null) throw new IllegalArgumentException("请求不能为空");
        String code = request.modelCode() == null ? "" : request.modelCode().trim();
        if (!CODE.matcher(code).matches()) throw new IllegalArgumentException("模型编码须为 2～96 位字母数字下划线");
        if (creating && mapper.findByCode(code) != null) throw new IllegalArgumentException("模型编码已存在");
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank() || name.length() > 160) throw new IllegalArgumentException("名称须为 1～160 字");
        String layer = request.layer() == null ? "" : request.layer().trim().toUpperCase(Locale.ROOT);
        if (!LAYERS.contains(layer)) throw new IllegalArgumentException("逻辑层仅支持 ODS、DWD、DWS、ADS");
        String table = request.tableName() == null ? "" : request.tableName().trim();
        if (!IDENTIFIER.matcher(table).matches()) throw new IllegalArgumentException("表名须为合法标识符");
        String kind = request.tableKind() == null ? "" : request.tableKind().trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("表类型仅支持 DIMENSION 或 FACT");
        if (request.primaryKeys() == null || request.primaryKeys().isEmpty() || request.primaryKeys().size() > 16)
            throw new IllegalArgumentException("主键须包含 1～16 个字段");
        for (String key : request.primaryKeys())
            if (!IDENTIFIER.matcher(key == null ? "" : key.trim()).matches())
                throw new IllegalArgumentException("主键字段名无效: " + key);
        String partition = request.partitionField() == null || request.partitionField().isBlank()
                ? null : request.partitionField().trim();
        if (partition != null && !IDENTIFIER.matcher(partition).matches())
            throw new IllegalArgumentException("分区字段无效");
        String strategy = request.updateStrategy() == null ? "REPLACE" : request.updateStrategy().trim().toUpperCase(Locale.ROOT);
        if (!STRATEGIES.contains(strategy)) throw new IllegalArgumentException("更新策略仅支持 REPLACE 或 MERGE");
        String gate = request.consumerGate() == null || request.consumerGate().isBlank()
                ? "OPEN" : request.consumerGate().trim().toUpperCase(Locale.ROOT);
        if (!GATES.contains(gate)) throw new IllegalArgumentException("消费门禁仅支持 OPEN 或 REQUIRE_PASS_RECON");
        Integer sla = request.freshnessSlaMinutes();
        if (sla != null && (sla < 1 || sla > 10080))
            throw new IllegalArgumentException("新鲜度 SLA 须为 1～10080 分钟");
        String status = request.status() == null ? "DRAFT" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("状态无效");
        String description = request.description() == null ? null : request.description().trim();
        if (description != null && description.length() > 1000) throw new IllegalArgumentException("描述不能超过 1000 字");
        List<String> keys = request.primaryKeys().stream().map(String::trim).toList();
        Map<String, Object> row = new HashMap<>();
        row.put("modelCode", code);
        row.put("name", name);
        row.put("layer", layer);
        row.put("tableName", table);
        row.put("tableKind", kind);
        try {
            row.put("primaryKeysJson", json.writeValueAsString(keys));
        } catch (Exception error) {
            throw new IllegalStateException("主键序列化失败", error);
        }
        row.put("partitionField", partition);
        row.put("updateStrategy", strategy);
        row.put("consumerGate", gate);
        row.put("freshnessSlaMinutes", sla);
        row.put("description", description);
        row.put("status", status);
        return row;
    }

    private Map<String, Object> decode(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        Object raw = row.get("primaryKeysJson");
        try {
            out.put("primaryKeys", raw == null ? List.of() : json.readValue(String.valueOf(raw), List.class));
        } catch (Exception ignored) {
            out.put("primaryKeys", List.of());
        }
        out.remove("primaryKeysJson");
        if (!out.containsKey("consumerGate") || out.get("consumerGate") == null) out.put("consumerGate", "OPEN");
        return out;
    }
}
