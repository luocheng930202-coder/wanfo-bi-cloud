package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explains the two catalog surfaces so clients do not conflate JDBC modeling browse with the platform asset catalog.
 */
@Tag(name = "目录叙事")
@RestController
@RequestMapping("/bi/catalog/narrative")
public class CatalogNarrativeController
{
    @Operation(summary = "统一说明平台资产目录与建模 JDBC 目录的边界")
    @GetMapping
    @BiPermission("bi:datasource:model")
    public Map<String, Object> narrative()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "平台目录与建模目录");
        result.put("summary", "平台资产目录面向治理与发现；建模 JDBC 目录面向连接器内 schema/table 浏览与入仓选表。二者不是同一套 catalog。");
        result.put("surfaces", List.of(
                Map.of(
                        "code", "PLATFORM_ASSET",
                        "name", "平台资产目录",
                        "basePath", "/bi/catalog/assets",
                        "purpose", "登记已接入的数据源/数据集/仓表资产、认证状态、术语与血缘入口",
                        "primaryUsers", List.of("数据治理", "分析师发现资产")),
                Map.of(
                        "code", "JDBC_MODELING",
                        "name", "建模 JDBC 目录",
                        "basePath", "/bi/catalog/{schemaName}",
                        "purpose", "按连接实时列出 schema/table/column，供数据集建模与物理入仓选表",
                        "primaryUsers", List.of("数据工程师", "建模员"))));
        result.put("related", List.of(
                Map.of("path", "/bi/catalog/lineage", "role", "血缘图"),
                Map.of("path", "/bi/glossary/terms", "role", "业务术语/指标词典"),
                Map.of("path", "/bi/metadata-scans", "role", "元数据扫描任务")));
        result.put("guidance", List.of(
                "发现与授权请走平台资产目录；选物理表请走建模 JDBC 目录。",
                "仓表出现在资产目录前，须先经装载/物化并完成资产登记或元数据扫描。",
                "不要在产品文案中混用「catalog」一词指代两套接口。"));
        return result;
    }
}
