package com.wanfo.bi.controller;

import java.util.List;
import java.util.Map;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.WorkbenchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BI 工作台")
@RestController
@RequestMapping("/bi/workbench")
public class WorkbenchController {
    private final WorkbenchService service;
    public WorkbenchController(WorkbenchService service) { this.service = service; }

    @Operation(summary = "资源统计")
    @GetMapping("/summary")
    @BiPermission(anyOf = {"bi:datasource:list", "bi:dataset:list", "bi:chart:list", "bi:dashboard:list", "bi:form:list", "bi:export:list", "bi:template:list"})
    public Map<String, Object> summary() { return service.summary(); }

    @Operation(summary = "最近资源")
    @GetMapping("/recent")
    @BiPermission(anyOf = {"bi:chart:list", "bi:dashboard:list"})
    public List<Map<String, Object>> recent(@RequestParam(defaultValue = "all") String kind,
            @RequestParam(defaultValue = "12") int limit) {
        return service.recent(kind, limit);
    }

    @Operation(summary = "模板市场推荐（仅元数据）")
    @GetMapping("/template-picks")
    @BiPermission(anyOf = {"bi:template:list", "bi:dashboard:add"})
    public List<Map<String, Object>> templatePicks(@RequestParam(defaultValue = "6") int limit) {
        return service.templatePicks(limit);
    }
}
