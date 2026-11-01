package com.wanfo.bi.service;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.datasource.JdbcConnectionSettings;
import com.wanfo.bi.mapper.BackupDrillMapper;
import com.wanfo.bi.warehouse.WarehousePublisher;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** S3-06: backup/restore drill registry + readiness pack (no automatic destructive restore). */
@Service
public class BackupDrillService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackupDrillService.class);
    private static final Set<String> TYPES = Set.of(
            "METADATA_DUMP", "RESTORE_DRY_RUN", "FLYWAY_CHECK", "WAREHOUSE_NOTE", "FULL_DRILL");
    private static final Set<String> RESULTS = Set.of("PASSED", "FAILED", "PARTIAL");
    private final boolean autoRegisterArtifacts;
    private final boolean autoDumpEnabled;
    private final Path dumpScript;

    public record RecordRequest(String drillType, Map<String, Object> scope, String result, Integer rpoMinutes,
            Integer rtoMinutes, String artifactPath, List<String> checklist, String notes,
            LocalDateTime startedAt, LocalDateTime finishedAt) {
    }

    private final BackupDrillMapper mapper;
    private final DataSourceProvider sources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final CatalogAuditService audit;
    private final ObjectMapper json;
    private final Path backupDir;
    private final int declaredRpoMinutes;
    private final int declaredRtoMinutes;

    public BackupDrillService(BackupDrillMapper mapper, DataSourceProvider sources, CapabilityProvider capabilities,
            CurrentActorProvider actors, CatalogAuditService audit, ObjectMapper json,
            @Value("${bi.backup.dir:D:/datacenter/.local/backups}") String backupDir,
            @Value("${bi.backup.declared-rpo-minutes:1440}") int declaredRpoMinutes,
            @Value("${bi.backup.declared-rto-minutes:240}") int declaredRtoMinutes,
            @Value("${bi.backup.auto-register-artifacts:true}") boolean autoRegisterArtifacts,
            @Value("${bi.backup.auto-dump-enabled:false}") boolean autoDumpEnabled,
            @Value("${bi.backup.dump-script:D:/datacenter/scripts/backup-metadata.ps1}") String dumpScript) {
        this.mapper = mapper;
        this.sources = sources;
        this.capabilities = capabilities;
        this.actors = actors;
        this.audit = audit;
        this.json = json;
        this.backupDir = Path.of(backupDir);
        this.declaredRpoMinutes = Math.max(60, declaredRpoMinutes);
        this.declaredRtoMinutes = Math.max(30, declaredRtoMinutes);
        this.autoRegisterArtifacts = autoRegisterArtifacts;
        this.autoDumpEnabled = autoDumpEnabled;
        this.dumpScript = Path.of(dumpScript);
    }

    /** Periodically register new local dump files as METADATA_DUMP evidence (does not restore). */
    @Scheduled(fixedDelayString = "${bi.backup.artifact-scan-ms:300000}", initialDelayString = "60000")
    public void syncLocalArtifacts() {
        if (!autoRegisterArtifacts) return;
        try {
            int registered = registerNewArtifactsInternal("system-scheduler");
            if (registered > 0) LOGGER.info("Auto-registered {} metadata dump artifact(s)", registered);
        } catch (Exception ex) {
            LOGGER.warn("Backup artifact scan failed: {}", ex.getMessage());
        }
    }

    /** Optional scheduled mysqldump via PowerShell script (off by default). */
    @Scheduled(cron = "${bi.backup.auto-dump-cron:0 0 2 * * *}")
    public void scheduledMetadataDump() {
        if (!autoDumpEnabled) return;
        try {
            Map<String, Object> result = runDumpScriptInternal("system-scheduler");
            LOGGER.info("Scheduled metadata dump: {}", result.get("message"));
        } catch (Exception ex) {
            LOGGER.warn("Scheduled metadata dump failed: {}", ex.getMessage());
        }
    }

    public Map<String, Object> triggerDumpNow() {
        capabilities.require("bi:resource:admin");
        Map<String, Object> result = runDumpScriptInternal(actors.currentActor().username());
        audit.record("BACKUP_METADATA_DUMP", "BACKUP_DRILL", null, null,
                Map.of("exitCode", result.getOrDefault("exitCode", -1), "ok", result.getOrDefault("ok", false)));
        return result;
    }

    private Map<String, Object> runDumpScriptInternal(String actor) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("script", dumpScript.toAbsolutePath().toString());
        out.put("actor", actor);
        if (!Files.isRegularFile(dumpScript)) {
            out.put("ok", false);
            out.put("message", "dump 脚本不存在: " + dumpScript);
            return out;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", dumpScript.toAbsolutePath().toString(),
                    "-OutDir", backupDir.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String log = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            boolean finished = process.waitFor(15, java.util.concurrent.TimeUnit.MINUTES);
            int code = finished ? process.exitValue() : -1;
            if (!finished) process.destroyForcibly();
            out.put("exitCode", code);
            out.put("ok", finished && code == 0);
            out.put("output", log.length() > 2000 ? log.substring(0, 2000) : log);
            out.put("message", finished && code == 0 ? "dump 完成" : "dump 失败或超时");
            if (finished && code == 0 && autoRegisterArtifacts) {
                out.put("registered", registerNewArtifactsInternal(actor));
            }
            return out;
        } catch (Exception ex) {
            out.put("ok", false);
            out.put("exitCode", -1);
            out.put("message", "启动 dump 失败: " + ex.getMessage());
            return out;
        }
    }

    @Transactional
    public Map<String, Object> syncArtifactsNow() {
        capabilities.require("bi:resource:admin");
        int registered = registerNewArtifactsInternal(actors.currentActor().username());
        return Map.of("registered", registered, "backupDir", backupDir.toAbsolutePath().toString());
    }

    private int registerNewArtifactsInternal(String actor) {
        List<Map<String, Object>> artifacts = listLocalArtifacts();
        int count = 0;
        for (Map<String, Object> art : artifacts) {
            String name = String.valueOf(art.get("name"));
            if (mapper.watchedArtifact(name) != null) continue;
            LocalDateTime now = LocalDateTime.now();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("drillType", "METADATA_DUMP");
            row.put("scopeJson", writeJson(Map.of("metadataDb", true, "auto", true, "artifact", name)));
            row.put("result", "PASSED");
            row.put("rpoMinutes", declaredRpoMinutes);
            row.put("rtoMinutes", declaredRtoMinutes);
            row.put("artifactPath", String.valueOf(art.get("path")));
            row.put("checklistJson", writeJson(List.of("本地 dump 文件出现", "未执行自动恢复")));
            row.put("notes", "定时扫描自动登记；请按 runbook 在隔离副本验证恢复");
            row.put("actor", actor == null ? "system" : actor);
            row.put("startedAt", now.minusMinutes(1));
            row.put("finishedAt", now);
            if (mapper.insert(row) != 1 || !(row.get("id") instanceof Number id)) continue;
            Map<String, Object> watch = new LinkedHashMap<>();
            watch.put("artifactName", name);
            watch.put("artifactPath", String.valueOf(art.get("path")));
            watch.put("sizeBytes", art.get("sizeBytes") instanceof Number n ? n.longValue() : 0L);
            watch.put("drillId", id.longValue());
            mapper.watchArtifact(watch);
            count++;
        }
        return count;
    }

    public List<Map<String, Object>> list() {
        capabilities.require("bi:resource:admin");
        return mapper.list().stream().map(this::enrich).toList();
    }

    public Map<String, Object> get(Long id) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> row = mapper.find(id);
        if (row == null) throw new IllegalArgumentException("演练记录不存在");
        return enrich(row);
    }

    @Transactional
    public Map<String, Object> record(RecordRequest request) {
        capabilities.require("bi:resource:admin");
        if (request == null) throw new IllegalArgumentException("请求不能为空");
        String type = normalize(request.drillType());
        if (!TYPES.contains(type)) throw new IllegalArgumentException("演练类型无效");
        String result = normalize(request.result());
        if (!RESULTS.contains(result)) throw new IllegalArgumentException("结果须为 PASSED/FAILED/PARTIAL");
        LocalDateTime started = request.startedAt() == null ? LocalDateTime.now().minusMinutes(5) : request.startedAt();
        LocalDateTime finished = request.finishedAt() == null ? LocalDateTime.now() : request.finishedAt();
        if (finished.isBefore(started)) throw new IllegalArgumentException("结束时间不能早于开始时间");
        Map<String, Object> scope = request.scope() == null || request.scope().isEmpty()
                ? Map.of("metadataDb", true, "redis", false, "warehouse", false, "objectStore", false)
                : request.scope();
        String artifact = blankToNull(request.artifactPath(), 1000);
        String notes = blankToNull(request.notes(), 2000);
        List<String> checklist = request.checklist() == null ? List.of() : request.checklist().stream()
                .filter(s -> s != null && !s.isBlank()).map(String::trim).limit(50).toList();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("drillType", type);
        row.put("scopeJson", writeJson(scope));
        row.put("result", result);
        row.put("rpoMinutes", request.rpoMinutes());
        row.put("rtoMinutes", request.rtoMinutes());
        row.put("artifactPath", artifact);
        row.put("checklistJson", writeJson(checklist));
        row.put("notes", notes);
        row.put("actor", actors.currentActor().username());
        row.put("startedAt", started);
        row.put("finishedAt", finished);
        if (mapper.insert(row) != 1 || !(row.get("id") instanceof Number))
            throw new IllegalStateException("演练记录写入失败");
        Map<String, Object> saved = get(((Number) row.get("id")).longValue());
        audit.record("BACKUP_DRILL_RECORD", "BACKUP_DRILL", ((Number) row.get("id")).longValue(), null,
                Map.of("drillType", type, "result", result));
        return saved;
    }

    /** Read-only JDBC probe for configured warehouse engines (ClickHouse/Doris/StarRocks). */
    public Map<String, Object> warehouseEngineProbe() {
        capabilities.require("bi:resource:admin");
        List<Map<String, Object>> probes = new ArrayList<>();
        int ok = 0, failed = 0;
        for (BiDatasource source : sources.listEnabled()) {
            if (source == null || !"0".equals(source.getStatus())) continue;
            WarehousePublisher.Engine engine;
            try {
                engine = WarehousePublisher.parseEngine(source.getType());
            } catch (RuntimeException ignored) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("datasourceId", source.getId());
            item.put("name", source.getName());
            item.put("engine", engine.name());
            item.put("schema", source.getSourceSchema());
            try (Connection connection = JdbcConnectionSettings.open(source);
                 Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                String sql = engine == WarehousePublisher.Engine.CLICKHOUSE ? "SELECT 1" : "SELECT 1";
                try (ResultSet rs = statement.executeQuery(sql)) {
                    rs.next();
                }
                item.put("reachable", true);
                item.put("message", "JDBC 探活成功");
                ok++;
            } catch (Exception ex) {
                item.put("reachable", false);
                item.put("message", ex.getMessage() == null ? "探活失败" : ex.getMessage());
                failed++;
            }
            probes.add(item);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("probed", probes.size());
        summary.put("reachable", ok);
        summary.put("failed", failed);
        summary.put("note", "只读 SELECT 1 探活，不执行备份或恢复");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("items", probes);
        return out;
    }

    public Map<String, Object> readiness() {
        capabilities.require("bi:resource:admin");
        List<Map<String, Object>> drills = mapper.list();
        Map<String, Object> lastPassed = drills.stream()
                .filter(d -> "PASSED".equals(String.valueOf(d.get("result"))))
                .findFirst().map(this::enrich).orElse(null);
        Map<String, Object> lastAny = drills.isEmpty() ? null : enrich(drills.get(0));

        List<Map<String, Object>> artifacts = listLocalArtifacts();
        Map<String, Object> newestArtifact = artifacts.isEmpty() ? null : artifacts.get(0);
        Long ageMinutes = null;
        if (newestArtifact != null && newestArtifact.get("modifiedAt") instanceof String ts) {
            try {
                LocalDateTime modified = LocalDateTime.parse(ts, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                ageMinutes = Duration.between(modified, LocalDateTime.now()).toMinutes();
            } catch (Exception ignored) {
                // ignore parse issues
            }
        }

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (lastPassed == null) blockers.add("尚无 PASSED 演练记录，容灾验收不完整");
        else {
            Object finished = lastPassed.get("finishedAt");
            if (finished != null) {
                try {
                    LocalDateTime t = LocalDateTime.parse(String.valueOf(finished).replace(' ', 'T').substring(0, 19));
                    long days = Duration.between(t, LocalDateTime.now()).toDays();
                    if (days > 90) warnings.add("最近一次通过演练已超过 90 天");
                } catch (Exception ignored) {
                }
            }
        }
        if (artifacts.isEmpty()) warnings.add("本地备份目录暂无 dump 文件；请运行 scripts/backup-metadata.ps1");
        else if (ageMinutes != null && ageMinutes > declaredRpoMinutes)
            warnings.add("最新本地 dump 早于声明 RPO（" + declaredRpoMinutes + " 分钟）");

        String flyway = null;
        try { flyway = mapper.latestFlywayVersion(); } catch (Exception ex) { warnings.add("无法读取 bi_flyway_history"); }
        Map<String, Object> retention = null;
        try { retention = mapper.auditRetention(); } catch (Exception ignored) { }
        Long archiveCount = 0L;
        try { archiveCount = mapper.archiveCount(); } catch (Exception ignored) { }

        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("metadataDb", Map.of(
                "declaredRpoMinutes", declaredRpoMinutes,
                "declaredRtoMinutes", declaredRtoMinutes,
                "backupDir", backupDir.toAbsolutePath().toString(),
                "script", "scripts/backup-metadata.ps1",
                "runbook", "docs/BACKUP-RESTORE-DRILL.md"));
        targets.put("auditArchive", Map.of("count", archiveCount == null ? 0 : archiveCount, "retention", retention == null ? Map.of() : retention));
        targets.put("redis", Map.of("note", "协调状态可重建；演练时记录是否需 flush/rebuild"));
        targets.put("warehouse", Map.of("note", "ClickHouse/ODS 备份由仓引擎责任人按 ADR 演练，本包仅登记证据"));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ready", blockers.isEmpty());
        summary.put("blockers", blockers);
        summary.put("warnings", warnings);
        summary.put("flywayVersion", flyway);
        summary.put("declaredRpoMinutes", declaredRpoMinutes);
        summary.put("declaredRtoMinutes", declaredRtoMinutes);
        summary.put("lastPassedDrill", lastPassed);
        summary.put("lastDrill", lastAny);
        summary.put("localArtifactCount", artifacts.size());
        summary.put("newestLocalArtifact", newestArtifact);
        summary.put("newestArtifactAgeMinutes", ageMinutes);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("targets", targets);
        out.put("checklist", defaultChecklist());
        out.put("localArtifacts", artifacts);
        out.put("note", "平台不自动执行破坏性恢复；恢复须在副本库按 runbook 人工确认后再登记演练结果。");
        return out;
    }

    private List<Map<String, Object>> listLocalArtifacts() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            if (!Files.isDirectory(backupDir)) return rows;
            try (var stream = Files.list(backupDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return n.endsWith(".sql") || n.endsWith(".sql.gz") || n.endsWith(".dump");
                        })
                        .sorted((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()))
                        .limit(20)
                        .forEach(p -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("name", p.getFileName().toString());
                            item.put("path", p.toAbsolutePath().toString());
                            item.put("sizeBytes", p.toFile().length());
                            item.put("modifiedAt", LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(p.toFile().lastModified()),
                                    java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                            rows.add(item);
                        });
            }
        } catch (Exception ignored) {
            // readiness still returns without artifacts
        }
        return rows;
    }

    private static List<String> defaultChecklist() {
        return List.of(
                "确认元数据库备份脚本可在运维主机无交互执行",
                "在隔离副本恢复 dump 并核对表数量/Flyway 版本",
                "验证审计归档表可查询且保留策略符合要求",
                "记录观测 RPO/RTO 并与声明值对比",
                "将证据（路径/工单号）写入本演练登记，禁止粘贴凭据");
    }

    private Map<String, Object> enrich(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("scope", readJson(row.get("scopeJson")));
        out.put("checklist", readJson(row.get("checklistJson")));
        return out;
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Object readJson(Object raw) {
        if (raw == null) return List.of();
        try {
            return json.readValue(String.valueOf(raw), Object.class);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new IllegalArgumentException("文本超长");
        return trimmed;
    }
}
