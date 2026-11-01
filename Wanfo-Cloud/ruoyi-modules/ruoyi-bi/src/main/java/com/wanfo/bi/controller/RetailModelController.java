package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.DatasetMaterializationService;
import com.wanfo.bi.service.RetailModelMaterializationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数仓模型物化")
@RestController
@RequestMapping("/bi/warehouse/models")
public class RetailModelController
{
    private final RetailModelMaterializationService models;
    private final DatasetMaterializationService materializations;
    private final CatalogAuditService audit;

    public RetailModelController(RetailModelMaterializationService models, DatasetMaterializationService materializations,
            CatalogAuditService audit)
    {
        this.models = models;
        this.materializations = materializations;
        this.audit = audit;
    }

    @Operation(summary = "列出内置/示例模型包")
    @GetMapping("/packages")
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> packages()
    {
        return materializations.packages();
    }

    @Operation(summary = "将授权数据集物化到数仓逻辑层（REPLACE）")
    @PostMapping("/materialize")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> materialize(@org.springframework.web.bind.annotation.RequestBody DatasetMaterializationService.MaterializeRequest request)
    {
        Map<String, Object> run = materializations.trigger(request, MDC.get("trace_id"));
        audit.record("WAREHOUSE_DATASET_MATERIALIZE", "WAREHOUSE_LOAD_RUN", number(run.get("id")),
                number(run.get("targetDatasourceId")), Map.of("datasetId", request.datasetId(), "targetTable",
                        run.get("targetDatabase") + "." + run.get("targetTable"), "loadMode", "DATASET"));
        return run;
    }

    @Operation(summary = "刷新 retail-v1 示例模型月度销售汇总")
    @PostMapping("/retail-v1/monthly/refresh")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> refreshRetailMonthly(@RequestParam Long targetDatasourceId)
    {
        Map<String, Object> run = models.trigger(targetDatasourceId, MDC.get("trace_id"));
        audit.record("WAREHOUSE_MODEL_REFRESH", "WAREHOUSE_LOAD_RUN", number(run.get("id")), targetDatasourceId,
                Map.of("modelVersion", "retail-v1", "kind", "EXAMPLE", "targetTable", "wanfo_wh_dws.agg_retail_sales_monthly"));
        return run;
    }

    @Operation(summary = "查询 retail-v1 最近一次全量对账与新鲜度")
    @GetMapping("/retail-v1/reconciliation")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> latestRetailReconciliation(@RequestParam Long targetDatasourceId)
    {
        Map<String, Object> raw = models.latestReconciliation(targetDatasourceId);
        Map<String, Object> result = normalizeReconciliation(raw);
        audit.record("WAREHOUSE_RECONCILIATION_VIEW", "DATASOURCE", targetDatasourceId, targetDatasourceId,
                Map.of("modelVersion", "retail-v1", "hasResult", raw != null && !raw.isEmpty()));
        return result;
    }

    @Operation(summary = "查询通用物化对账结果")
    @GetMapping("/reconciliation")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> latestMaterializeReconciliation(@RequestParam Long targetDatasourceId,
            @RequestParam String modelCode)
    {
        Map<String, Object> raw = materializations.latestReconciliation(targetDatasourceId, modelCode);
        Map<String, Object> result = normalizeReconciliation(raw);
        audit.record("WAREHOUSE_RECONCILIATION_VIEW", "DATASOURCE", targetDatasourceId, targetDatasourceId,
                Map.of("modelVersion", modelCode, "hasResult", raw != null && !raw.isEmpty()));
        return result;
    }

    private static Map<String, Object> normalizeReconciliation(Map<String, Object> row)
    {
        if (row == null || row.isEmpty()) return Map.of();
        List<String> fields = List.of("runId", "targetDatasourceId", "modelVersion", "sourceTable", "targetTable",
                "checkScope", "sourceRowCount", "targetRowCount", "sourceSalesAmount", "targetSalesAmount",
                "sourceNetQuantity", "targetNetQuantity", "sourceMaxUpdatedAt", "observedAt",
                "freshnessAgeSeconds", "status", "summary");
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (String field : fields)
        {
            String expected = compact(field);
            Object value = row.entrySet().stream()
                    .filter(entry -> compact(entry.getKey()).equals(expected))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            normalized.put(field, value);
        }
        return normalized;
    }

    private static String compact(String value)
    {
        return value == null ? "" : value.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static Long number(Object value) { return value instanceof Number number ? number.longValue() : null; }
}
