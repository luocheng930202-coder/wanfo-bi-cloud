package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAssetService;
import com.wanfo.bi.service.CatalogAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据平台资产目录")
@RestController
@RequestMapping("/bi/catalog/assets")
public class CatalogAssetController
{
    private final CatalogAssetService service;
    private final CatalogAuditService audit;

    public CatalogAssetController(CatalogAssetService service, CatalogAuditService audit) { this.service = service; this.audit = audit; }

    @Operation(summary = "预览现有 BI 资源目录回填数量（不写入）")
    @GetMapping("/backfill/preview")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> backfillPreview() {
        Map<String, Object> result = service.backfillPreview();
        audit.record("BI_RESOURCE_BACKFILL_PREVIEW", "CATALOG", null, null, result);
        return result;
    }

    @Operation(summary = "将现有 BI 资源幂等回填到数据资产目录")
    @org.springframework.web.bind.annotation.PostMapping("/backfill")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> backfill() {
        Map<String, Object> result = service.backfill();
        audit.record("BI_RESOURCE_BACKFILL", "CATALOG", null, null, result);
        return result;
    }

    @Operation(summary = "分页搜索当前用户有权访问的数据资产")
    @GetMapping
    @BiPermission("bi:datasource:model")
    public Map<String, Object> search(@RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String sourceKind, @RequestParam(required = false) String type, @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) String query, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize)
    {
        Map<String, Object> result = service.search(sourceKind, datasourceId, type, parentId, query, page, pageSize);
        audit.record("CATALOG_SEARCH", "CATALOG", null, datasourceId,
                Map.of("sourceKind", sourceKind == null ? "CONNECTOR" : sourceKind, "type", type == null ? "" : type,
                        "page", page, "pageSize", pageSize, "returned", ((List<?>) result.get("items")).size(), "queryPresent", query != null && !query.isBlank()));
        return result;
    }

    @Operation(summary = "查看资产详情")
    @GetMapping("/{id}")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> detail(@PathVariable Long id) {
        Map<String, Object> result = service.detail(id);
        audit.record("CATALOG_ASSET_VIEW", "ASSET", id, number(result.get("sourceId")), Map.of("assetType", result.getOrDefault("assetType", "")));
        return result;
    }

    @Operation(summary = "目录消费深链：申请使用 / 去分析 / 生成 API")
    @GetMapping("/{id}/consume-links")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> consumeLinks(@PathVariable Long id) {
        Map<String, Object> result = service.consumeLinks(id);
        audit.record("CATALOG_CONSUME_LINKS", "ASSET", id, number(result.get("sourceId")),
                Map.of("assetType", result.getOrDefault("assetType", ""),
                        "actions", result.get("consumeActions") instanceof List<?> list ? list.size() : 0));
        return result;
    }

    @Operation(summary = "查看资产元数据版本历史")
    @GetMapping("/{id}/versions")
    @BiPermission("bi:datasource:model")
    public List<Map<String, Object>> versions(@PathVariable Long id) {
        List<Map<String, Object>> result = service.versions(id);
        audit.record("CATALOG_ASSET_VERSIONS", "ASSET", id, null, Map.of("returned", result.size()));
        return result;
    }

    private Long number(Object value) { return value instanceof Number number ? number.longValue() : null; }
}
