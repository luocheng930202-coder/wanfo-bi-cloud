package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.PrincipalCatalog;
import com.wanfo.bi.service.MetricSetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BI 指标集")
@RestController
@RequestMapping("/bi/metric-sets")
public class MetricSetController {
    private final MetricSetService sets;
    private final BiResourceAuthorizer resources;
    private final PrincipalCatalog principals;

    public MetricSetController(MetricSetService sets, BiResourceAuthorizer resources, PrincipalCatalog principals) {
        this.sets = sets;
        this.resources = resources;
        this.principals = principals;
    }

    @GetMapping
    @BiPermission("bi:metric:list")
    public List<Map<String, Object>> list() {
        return sets.list();
    }

    @GetMapping("/{id}")
    @BiPermission("bi:metric:list")
    public Map<String, Object> get(@PathVariable Long id) {
        return sets.get(id);
    }

    @PostMapping
    @BiPermission("bi:metric:add")
    public Map<String, Object> create(@RequestBody MetricSetService.UpsertRequest request) {
        return sets.create(request);
    }

    @PutMapping("/{id}")
    @BiPermission("bi:metric:edit")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody MetricSetService.UpsertRequest request) {
        return sets.update(id, request);
    }

    @DeleteMapping("/{id}")
    @BiPermission("bi:metric:remove")
    public Map<String, Object> delete(@PathVariable Long id) {
        return sets.delete(id);
    }

    @GetMapping("/principals")
    @BiPermission("bi:metric:grant")
    public List<Map<String, Object>> principals(@RequestParam(defaultValue = "") String query) {
        return principals.search(query);
    }

    @GetMapping("/{id}/grants")
    @BiPermission("bi:metric:grant")
    public List<Map<String, Object>> grants(@PathVariable Long id) {
        return resources.grants("metric_set", id);
    }

    public record GrantRequest(Long principalId, String action) { }

    @PostMapping("/{id}/grants")
    @BiPermission("bi:metric:grant")
    public Map<String, Object> grant(@PathVariable Long id, @RequestBody GrantRequest request) {
        resources.change("metric_set", id, request.principalId(), request.action(), true);
        return Map.of("granted", true);
    }

    @DeleteMapping("/{id}/grants/{principalId}/{action}")
    @BiPermission("bi:metric:grant")
    public Map<String, Object> revoke(@PathVariable Long id, @PathVariable Long principalId, @PathVariable String action) {
        resources.change("metric_set", id, principalId, action, false);
        return Map.of("revoked", true);
    }
}
