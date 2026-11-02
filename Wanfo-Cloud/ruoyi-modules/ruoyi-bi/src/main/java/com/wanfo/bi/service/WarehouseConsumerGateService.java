package com.wanfo.bi.service;

import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.mapper.WarehouseLoadMapper;
import com.wanfo.bi.mapper.WarehouseTableModelMapper;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Enforces an explicitly bound warehouse model's reconciliation/freshness policy. */
@Service
public class WarehouseConsumerGateService {
    private final WarehouseTableModelMapper models;
    private final WarehouseLoadMapper loads;

    public WarehouseConsumerGateService(WarehouseTableModelMapper models, WarehouseLoadMapper loads) {
        this.models = models;
        this.loads = loads;
    }

    public void requireConsumable(BiDatasetTableDO dataset) {
        if (dataset.getWarehouseModelId() == null) return;
        Map<String, Object> model = models.consumerBinding(dataset.getWarehouseModelId());
        if (model == null || !"ACTIVE".equalsIgnoreCase(String.valueOf(model.get("status"))))
            throw new com.wanfo.bi.security.BiAccessException(503, "数据集绑定的数仓模型不可用");
        if (dataset.getSourceTable() == null || !dataset.getSourceTable().equalsIgnoreCase(String.valueOf(model.get("tableName"))))
            throw new com.wanfo.bi.security.BiAccessException(503, "数据集来源表与绑定的数仓模型不一致");
        if (!"REQUIRE_PASS_RECON".equalsIgnoreCase(String.valueOf(model.get("consumerGate")))) return;
        Map<String, Object> result = loads.latestReconciliationForModel(dataset.getDatasourceId(), String.valueOf(model.get("modelCode")));
        if (result == null || !"PASS".equalsIgnoreCase(String.valueOf(result.get("status"))))
            throw new com.wanfo.bi.security.BiAccessException(503, "数仓对账未通过，暂不可消费");
        Object slaValue = model.get("freshnessSlaMinutes");
        if (slaValue instanceof Number sla) {
            Object ageValue = result.get("freshnessAgeSeconds");
            if (!(ageValue instanceof Number age) || age.longValue() > sla.longValue() * 60L)
                throw new com.wanfo.bi.security.BiAccessException(503, "数仓数据已超过新鲜度要求，暂不可消费");
        }
    }
}
