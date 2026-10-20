package com.wanfo.bi.controller;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.PrincipalCatalog;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.datasource.CredentialCipher;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.domain.BiDatasourceDO;
import com.wanfo.bi.service.BiDataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源管理接口（S3 阶段）。
 *
 * <p>数据源由数据库管理；功能权限及真实操作人通过宿主适配契约取得。
 *
 * @author zhengliang
 */
@Tag(name = "BI 数据源")
@RestController
@RequestMapping("/bi/datasource")
public class BiDatasourceController
{
    private final DataSourceProvider provider;
    private final CalciteQueryEngine engine;
    private final BiDataSourceService service;
    private final CurrentActorProvider actors;
    private final BiResourceAuthorizer resources;
    private final PrincipalCatalog catalog;

    public BiDatasourceController(DataSourceProvider provider,
                                  CalciteQueryEngine engine,
                                  BiDataSourceService service, CurrentActorProvider actors, BiResourceAuthorizer resources, PrincipalCatalog catalog)
    {
        this.provider = provider;
        this.engine = engine;
        this.service = service;
        this.actors = actors;
        this.resources = resources;
        this.catalog = catalog;
    }

    // ==================== 写操作（S3-T02）====================

    @Operation(summary = "管理列表（密码仅返回是否已设置）")
    @GetMapping("/manage")
    @BiPermission("bi:datasource:list")
    public List<Map<String, Object>> manageList()
    {
        return service.listForManage().stream().filter(row -> resources.allowed("datasource", ((Number) row.get("id")).longValue(), "view"))
                .peek(row -> row.put("resourceActions", java.util.stream.Stream.of("edit", "test", "remove", "grant", "model")
                    .filter(action -> resources.allowed("datasource", ((Number) row.get("id")).longValue(), action)).toList()))
                .toList();
    }

    @Operation(summary = "新增数据源（落库前自动试连，失败回滚）")
    @PostMapping
    @BiPermission("bi:datasource:add")
    public Map<String, Object> create(@RequestBody BiDatasourceDO form)
    {
        return service.create(form, actors.currentActor().username());
    }

    @Operation(summary = "编辑数据源（schemaName 不可改；密码留空表示不改）")
    @PutMapping
    @BiPermission("bi:datasource:edit")
    public Map<String, Object> update(@RequestBody BiDatasourceDO form)
    {
        requireResource(form.getId(), "edit");
        return service.update(form, actors.currentActor().username());
    }

    @Operation(summary = "启用 / 停用")
    @PutMapping("/{id}/status")
    @BiPermission("bi:datasource:edit")
    public Map<String, Object> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body)
    {
        requireResource(id, "edit");
        return service.changeStatus(id, body.get("status"), actors.currentActor().username());
    }

    @Operation(summary = "逻辑删除")
    @DeleteMapping("/{id}")
    @BiPermission("bi:datasource:remove")
    public Map<String, Object> delete(@PathVariable Long id)
    {
        requireResource(id, "remove");
        return service.delete(id, actors.currentActor().username());
    }

    @Operation(summary = "试连（不落库）")
    @PostMapping("/probe-test")
    @BiPermission("bi:datasource:test")
    public Map<String, Object> probeTest(@RequestBody BiDatasourceDO form)
    {
        if (form.getId() != null)
        {
            requireResource(form.getId(), "test");
            requireResource(form.getId(), "edit");
        }
        return service.testFormConnection(form);
    }

    @Operation(summary = "数据源列表（密码脱敏）")
    @GetMapping("/list")
    @BiPermission("bi:datasource:list")
    public List<Map<String, Object>> list()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (BiDatasource ds : provider.listEnabled())
        {
            if (!resources.allowed("datasource", ds.getId(), "view")) { continue; }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ds.getId());
            m.put("name", ds.getName());
            m.put("type", ds.getType());
            m.put("schemaName", ds.getSchemaName());
            m.put("jdbcUrl", ds.getJdbcUrl());
            m.put("driverClass", ds.getDriverClass());
            m.put("username", ds.getUsername());
            // ★ 绝不出password 原文
            m.put("password", CredentialCipher.mask(ds.getPassword()));
            m.put("status", ds.getStatus());
            result.add(m);
        }
        return result;
    }

    @Operation(summary = "连通性测试")
    @PostMapping("/{schemaName}/test")
    @BiPermission("bi:datasource:test")
    public Map<String, Object> test(@PathVariable String schemaName)
    {
        BiDatasource ds = provider.getBySchemaName(schemaName);
        if (ds == null) { throw new IllegalArgumentException("数据源不存在"); }
        requireResource(ds.getId(), "test");
        return service.testBySchema(schemaName);
    }

    @Operation(summary = "从配置同步到数据库（upsert，覆盖同 schemaName）")
    @PostMapping("/reload")
    @BiPermission("bi:datasource:sync")
    public Map<String, Object> reload()
    {
        resources.requireAdministrator();
        return service.syncFromConfig(actors.currentActor().username());
    }

    @Operation(summary = "数据源能力自检")
    @GetMapping("/probe")
    @BiPermission("bi:datasource:test")
    public Map<String, Boolean> probe()
    {
        return engine.probeAll(provider.listEnabled().stream()
                .filter(ds -> resources.allowed("datasource", ds.getId(), "test")).toList());
    }
    private void requireResource(Long id, String action)
    {
        resources.require("datasource", id, action);
        if (service.get(id) == null) { throw new IllegalArgumentException("数据源不存在"); }
    }

    @GetMapping("/principals")
    @BiPermission("bi:datasource:grant")
    public List<Map<String, Object>> principals(@org.springframework.web.bind.annotation.RequestParam(defaultValue="") String query)
    {
        return catalog.search(query);
    }

    @GetMapping("/{id}/grants")
    @BiPermission("bi:datasource:grant")
    public List<Map<String, Object>> grants(@PathVariable Long id)
    {
        requireResource(id, "grant");
        return resources.grants("datasource", id);
    }

    public record GrantRequest(Long principalId, String action) { }

    @PostMapping("/{id}/grants")
    @BiPermission("bi:datasource:grant")
    public Map<String, Object> grant(@PathVariable Long id, @RequestBody GrantRequest request)
    {
        requireResource(id, "grant");
        resources.change("datasource", id, request.principalId(), request.action(), true);
        return Map.of("granted", true);
    }

    @DeleteMapping("/{id}/grants/{principalId}/{action}")
    @BiPermission("bi:datasource:grant")
    public Map<String, Object> revoke(@PathVariable Long id, @PathVariable Long principalId, @PathVariable String action)
    {
        requireResource(id, "grant");
        resources.change("datasource", id, principalId, action, false);
        return Map.of("revoked", true);
    }

}

