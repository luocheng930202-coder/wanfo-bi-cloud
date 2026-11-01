package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.ObjectStorageLoadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "对象存储入仓")
@RestController
@RequestMapping("/bi/warehouse/object-loads")
public class ObjectStorageLoadController
{
    private final ObjectStorageLoadService loads;
    private final CatalogAuditService audit;

    public ObjectStorageLoadController(ObjectStorageLoadService loads, CatalogAuditService audit)
    {
        this.loads = loads;
        this.audit = audit;
    }

    @Operation(summary = "触发 S3/OSS/MinIO CSV/Parquet 批入仓（fileFormat=AUTO|CSV|PARQUET）")
    @PostMapping
    @BiPermission("bi:resource:admin")
    public Map<String, Object> trigger(@RequestBody ObjectStorageLoadService.LoadRequest request)
    {
        Map<String, Object> run = loads.trigger(request, MDC.get("trace_id"));
        audit.record("OBJECT_STORAGE_LOAD_TRIGGER", "OBJECT_STORAGE_LOAD_RUN", number(run.get("id")),
                number(run.get("datasourceId")), Map.of("targetTable", run.getOrDefault("targetTable", ""),
                        "targetDatabase", run.getOrDefault("targetDatabase", "")));
        return run;
    }

    @Operation(summary = "最近对象存储入仓运行")
    @GetMapping("/runs")
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> recent()
    {
        return loads.recent();
    }

    @Operation(summary = "对象存储入仓详情")
    @GetMapping("/runs/{id}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> get(@PathVariable Long id)
    {
        return loads.get(id);
    }

    private static Long number(Object value)
    {
        return value instanceof Number n ? n.longValue() : null;
    }
}
