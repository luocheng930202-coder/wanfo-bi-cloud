package com.wanfo.bi.service;

import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.mapper.WarehouseLoadMapper;
import com.wanfo.bi.mapper.WarehouseTableModelMapper;
import com.wanfo.bi.security.BiAccessException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarehouseConsumerGateServiceTest {
    WarehouseTableModelMapper models = mock(WarehouseTableModelMapper.class);
    WarehouseLoadMapper loads = mock(WarehouseLoadMapper.class);
    WarehouseConsumerGateService service = new WarehouseConsumerGateService(models, loads);

    private BiDatasetTableDO dataset(Long modelId) {
        var dataset = new BiDatasetTableDO(); dataset.setWarehouseModelId(modelId); dataset.setDatasourceId(17L); dataset.setSourceTable("orders"); return dataset;
    }

    @Test void unboundDatasetRemainsUnrestricted() {
        assertDoesNotThrow(() -> service.requireConsumable(dataset(null)));
        verifyNoInteractions(models, loads);
    }

    @Test void requiredGateBlocksWhenNoPassingReconciliationExists() {
        when(models.consumerBinding(3L)).thenReturn(Map.of("modelCode", "orders", "tableName", "orders", "status", "ACTIVE", "consumerGate", "REQUIRE_PASS_RECON"));
        when(loads.latestReconciliationForModel(17L, "orders")).thenReturn(null);
        assertThrows(BiAccessException.class, () -> service.requireConsumable(dataset(3L)));
    }

    @Test void requiredGateBlocksStaleDataAndAllowsFreshPassingData() {
        when(models.consumerBinding(3L)).thenReturn(Map.of("modelCode", "orders", "tableName", "orders", "status", "ACTIVE", "consumerGate", "REQUIRE_PASS_RECON", "freshnessSlaMinutes", 60));
        when(loads.latestReconciliationForModel(17L, "orders")).thenReturn(Map.of("status", "PASS", "freshnessAgeSeconds", 3601));
        assertThrows(BiAccessException.class, () -> service.requireConsumable(dataset(3L)));
        when(loads.latestReconciliationForModel(17L, "orders")).thenReturn(Map.of("status", "PASS", "freshnessAgeSeconds", 3600));
        assertDoesNotThrow(() -> service.requireConsumable(dataset(3L)));
    }
}
