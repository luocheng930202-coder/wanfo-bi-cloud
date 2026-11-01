package com.wanfo.bi.service;

import com.wanfo.bi.datasource.CredentialCipher;
import com.wanfo.bi.datasource.CredentialKeyProvider;
import com.wanfo.bi.datasource.DbDataSourceProvider;
import com.wanfo.bi.domain.BiDatasourceDO;
import com.wanfo.bi.mapper.CredentialKeyMapper;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** S3-05: sync key metadata, report envelope status, rotate/rewrap datasource credentials. */
@Service
public class CredentialKeyService {
    private final CredentialKeyProvider keys;
    private final CredentialCipher cipher;
    private final CredentialKeyMapper mapper;
    private final DbDataSourceProvider provider;
    private final BiResourceAuthorizer resources;
    private final CurrentActorProvider actors;
    private final CatalogAuditService audit;
    private final ObjectMapper json;

    public CredentialKeyService(CredentialKeyProvider keys, CredentialCipher cipher, CredentialKeyMapper mapper,
            DbDataSourceProvider provider, BiResourceAuthorizer resources, CurrentActorProvider actors,
            CatalogAuditService audit, ObjectMapper json) {
        this.keys = keys;
        this.cipher = cipher;
        this.mapper = mapper;
        this.provider = provider;
        this.resources = resources;
        this.actors = actors;
        this.audit = audit;
        this.json = json;
    }

    public Map<String, Object> status() {
        resources.requireAdministrator();
        syncMeta(actors.currentActor().username());
        Map<String, Long> envelopes = new LinkedHashMap<>();
        int total = 0;
        for (BiDatasourceDO row : provider.listAll()) {
            total++;
            String kind = String.valueOf(cipher.envelopeInfo(row.getPassword()).getOrDefault("kind", "EMPTY"));
            envelopes.merge(kind, 1L, Long::sum);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", keys.providerId());
        out.put("activeVersion", keys.activeVersion());
        out.put("versions", keys.describeVersions());
        out.put("persistedMeta", mapper.listMeta());
        out.put("datasourceCount", total);
        out.put("envelopeCounts", envelopes);
        out.put("recentRotations", mapper.recentRotations());
        out.put("note", "密钥材料仅来自配置/未来外部 KMS，数据库只存指纹与轮换审计；新密文前缀为 encgv:{version}:");
        return out;
    }

    @Transactional
    public Map<String, Object> rotate(Integer targetVersion, String reason) {
        resources.requireAdministrator();
        String actor = actors.currentActor().username();
        int toVersion = targetVersion == null ? keys.activeVersion() : targetVersion;
        if (!keys.supportsVersion(toVersion)) {
            throw new IllegalArgumentException("目标密钥版本不存在: " + toVersion + "（先在配置中加入 bi.credential.keys." + toVersion + " 并设置 active-key-version）");
        }
        if (toVersion != keys.activeVersion()) {
            throw new IllegalArgumentException("目标版本 " + toVersion + " 不是当前激活版本 "
                    + keys.activeVersion() + "；请先调整 bi.credential.active-key-version 并重启后再轮换");
        }
        syncMeta(actor);
        Map<String, Long> before = new LinkedHashMap<>();
        int scanned = 0, rewritten = 0, skipped = 0, failed = 0;
        List<Map<String, Object>> failures = new java.util.ArrayList<>();
        for (BiDatasourceDO row : provider.listAll()) {
            scanned++;
            String password = row.getPassword();
            String kind = String.valueOf(cipher.envelopeInfo(password).getOrDefault("kind", "EMPTY"));
            before.merge(kind, 1L, Long::sum);
            if (password == null || password.isEmpty()) { skipped++; continue; }
            Map<String, Object> info = cipher.envelopeInfo(password);
            if ("GCM_VERSIONED".equals(info.get("kind")) && Objects.equals(toVersion, info.get("version"))) {
                skipped++;
                continue;
            }
            try {
                String next = cipher.encryptWithVersion(cipher.decrypt(password), toVersion);
                if (Objects.equals(password, next)) { skipped++; continue; }
                row.setPassword(next);
                row.setUpdateBy(actor);
                if (provider.update(row) != 1) {
                    failed++;
                    failures.add(Map.of("id", row.getId(), "reason", "更新失败"));
                    continue;
                }
                rewritten++;
            } catch (RuntimeException error) {
                failed++;
                failures.add(Map.of("id", row.getId(), "reason",
                        error.getMessage() == null ? "轮换失败" : error.getMessage()));
            }
        }
        Map<String, Object> rotation = new LinkedHashMap<>();
        rotation.put("fromVersionsJson", writeJson(before));
        rotation.put("toVersion", toVersion);
        rotation.put("scanned", scanned);
        rotation.put("rewritten", rewritten);
        rotation.put("skipped", skipped);
        rotation.put("failed", failed);
        rotation.put("reason", reason == null || reason.isBlank() ? null : reason.trim());
        rotation.put("actor", actor);
        mapper.insertRotation(rotation);
        audit.record("CREDENTIAL_KEY_ROTATE", "CREDENTIAL_KEY", (long) toVersion, null,
                Map.of("scanned", scanned, "rewritten", rewritten, "failed", failed, "toVersion", toVersion));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toVersion", toVersion);
        result.put("provider", keys.providerId());
        result.put("scanned", scanned);
        result.put("rewritten", rewritten);
        result.put("skipped", skipped);
        result.put("failed", failed);
        result.put("failures", failures);
        result.put("beforeEnvelopeCounts", before);
        return result;
    }

    private void syncMeta(String actor) {
        mapper.clearActive();
        for (Map<String, Object> ver : keys.describeVersions()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", ver.get("version"));
            row.put("alias", ver.get("alias"));
            row.put("provider", ver.get("provider"));
            row.put("fingerprint", ver.get("fingerprint"));
            row.put("status", ver.get("status"));
            row.put("note", "synced from " + keys.providerId());
            row.put("actor", actor);
            mapper.upsertMeta(row);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
