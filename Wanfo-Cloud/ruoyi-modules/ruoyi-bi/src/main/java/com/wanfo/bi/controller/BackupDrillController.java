package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.BackupDrillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "备份与演练")
@RestController
@RequestMapping("/bi/ops/backup-drills")
public class BackupDrillController {
    private final BackupDrillService drills;

    public BackupDrillController(BackupDrillService drills) {
        this.drills = drills;
    }

    @Operation(summary = "备份恢复就绪检查包")
    @GetMapping("/readiness")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> readiness() {
        return drills.readiness();
    }

    @Operation(summary = "数仓引擎 JDBC 探活（只读）")
    @GetMapping("/warehouse-probe")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> warehouseProbe() {
        return drills.warehouseEngineProbe();
    }

    @Operation(summary = "列出备份/恢复演练记录")
    @GetMapping
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> list() {
        return drills.list();
    }

    @Operation(summary = "查询演练记录")
    @GetMapping("/{id}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> get(@PathVariable Long id) {
        return drills.get(id);
    }

    @Operation(summary = "登记一次备份或恢复演练结果")
    @PostMapping
    @BiPermission("bi:resource:admin")
    public Map<String, Object> record(@RequestBody BackupDrillService.RecordRequest request) {
        return drills.record(request);
    }

    @Operation(summary = "立即扫描本地 dump 并自动登记 METADATA_DUMP 证据")
    @PostMapping("/sync-artifacts")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> syncArtifacts() {
        return drills.syncArtifactsNow();
    }

    @Operation(summary = "立即执行 metadata dump 脚本（默认 backup-metadata.ps1）")
    @PostMapping("/dump-now")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> dumpNow() {
        return drills.triggerDumpNow();
    }
}
