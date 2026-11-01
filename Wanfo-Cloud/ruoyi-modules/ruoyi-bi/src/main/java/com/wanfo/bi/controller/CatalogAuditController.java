package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据平台审计")
@RestController
@RequestMapping("/bi/catalog/audit")
public class CatalogAuditController
{
    private final CatalogAuditService service;
    public CatalogAuditController(CatalogAuditService service) { this.service = service; }

    @Operation(summary = "管理员查看全局数据目录审计记录")
    @GetMapping
    @BiPermission("bi:resource:admin")
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required=false) String actor,@RequestParam(required=false) String action,
            @RequestParam(required=false) String outcome,@RequestParam(required=false) String from,
            @RequestParam(required=false) String to)
    {
        return service.list(page,pageSize,actor,action,outcome,from,to);
    }

    @Operation(summary="查看全局审计在线保留策略")
    @GetMapping("/retention") @BiPermission("bi:resource:admin")
    public Map<String,Object> retention(){return service.retentionPolicy();}

    @Operation(summary="更新全局审计在线保留天数，归档记录长期保存")
    @org.springframework.web.bind.annotation.PutMapping("/retention") @BiPermission("bi:resource:admin")
    public Map<String,Object> updateRetention(@RequestParam int liveRetentionDays){return service.updateRetention(liveRetentionDays);}
}
