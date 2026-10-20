package com.wanfo.bi.controller;

import java.util.List;
import java.util.Map;

import com.wanfo.bi.service.CatalogService;
import com.wanfo.bi.security.BiPermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 元数据目录接口：数据集创建时的"选数据源 -> 选表 -> 选字段"三级数据源。
 *
 * @author zhengliang
 */
@Tag(name = "BI 元数据目录")
@RestController
@RequestMapping("/bi/catalog")
public class CatalogController
{
    private final CatalogService service;

    public CatalogController(CatalogService service)
    {
        this.service = service;
    }

    @Operation(summary = "数据源概览（库数量）")
    @GetMapping("/{schemaName}/overview")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> overview(@PathVariable String schemaName) throws Exception
    {
        return service.tree(schemaName);
    }

    @Operation(summary = "列出库")
    @GetMapping("/{schemaName}/catalogs")
    @BiPermission("bi:datasource:model")
    public List<String> catalogs(@PathVariable String schemaName) throws Exception
    {
        return service.listCatalogs(schemaName);
    }

    @Operation(summary = "列出表与视图")
    @GetMapping("/{schemaName}/tables")
    @BiPermission("bi:datasource:model")
    public List<Map<String, Object>> tables(@PathVariable String schemaName,
                                           @RequestParam(required = false) String catalog) throws Exception
    {
        return service.listTables(schemaName, catalog);
    }

    @Operation(summary = "列出字段（含类型/注释/主键/维度度量建议）")
    @GetMapping("/{schemaName}/columns")
    @BiPermission("bi:datasource:model")
    public List<Map<String, Object>> columns(@PathVariable String schemaName,
                                            @RequestParam String table,
                                            @RequestParam(required = false) String catalog) throws Exception
    {
        return service.listColumns(schemaName, catalog, table);
    }
    @GetMapping("/sources")
    @BiPermission("bi:datasource:model")
    public List<Map<String,Object>> sources() { return service.sources(); }

    public record PreviewRequest(String catalog, String table, List<String> fields, Integer maxRows) { }

    @PostMapping("/{schemaName}/preview")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> preview(@PathVariable String schemaName, @RequestBody PreviewRequest request) throws Exception
    {
        return service.preview(schemaName, request.catalog(), request.table(), request.fields(),
                request.maxRows() == null ? 100 : request.maxRows());
    }

}

