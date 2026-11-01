package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.DependentFieldMigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "依赖字段自动迁移")
@RestController
@RequestMapping("/bi/datasource/field-migrations")
public class DependentFieldMigrationController
{
    private final DependentFieldMigrationService migrations;

    public DependentFieldMigrationController(DependentFieldMigrationService migrations)
    {
        this.migrations = migrations;
    }

    public record SuggestRequest(List<String> oldNames, List<String> newNames) { }

    public record ApplyRequest(Long datasourceId, List<String> newColumnNames) { }

    @Operation(summary = "基于规范化名称建议一对一字段映射")
    @PostMapping("/suggest")
    @BiPermission("bi:resource:admin")
    public Map<String, String> suggest(@RequestBody SuggestRequest request)
    {
        return migrations.suggest(request == null ? null : request.oldNames(),
                request == null ? null : request.newNames());
    }

    @Operation(summary = "对数据源下物理数据集应用依赖字段自动迁移")
    @PostMapping("/apply")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> apply(@RequestBody ApplyRequest request)
    {
        if (request == null) throw new IllegalArgumentException("请求不能为空");
        return migrations.applyForDatasource(request.datasourceId(), request.newColumnNames());
    }

    @Operation(summary = "回滚一次依赖字段迁移")
    @PostMapping("/{migrationId}/rollback")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> rollback(@PathVariable Long migrationId)
    {
        return migrations.rollback(migrationId);
    }

    @Operation(summary = "按数据源查看依赖字段迁移历史")
    @GetMapping
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> history(@RequestParam Long datasourceId)
    {
        return migrations.history(datasourceId);
    }
}
