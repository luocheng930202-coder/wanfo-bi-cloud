package com.wanfo.bi.datasource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Local key ring from config/env. Default {@code bi.credential.key} is version 1.
 * Additional versions: {@code bi.credential.keys.2=...} … {@code bi.credential.keys.20}.
 * Activate with {@code bi.credential.active-key-version}.
 * Switch off with {@code bi.credential.key-provider=EXTERNAL_KMS_STUB}.
 */
@Component
@ConditionalOnProperty(name = "bi.credential.key-provider", havingValue = "LOCAL_CONFIG", matchIfMissing = true)
public class LocalConfigCredentialKeyProvider implements CredentialKeyProvider {
    private final int activeVersion;
    private final Map<Integer, byte[]> materials = new TreeMap<>();

    public LocalConfigCredentialKeyProvider(
            @Value("${bi.credential.key:zhengliang-bi-2026-secret}") String defaultKey,
            @Value("${bi.credential.active-key-version:1}") int activeVersion,
            Environment env) {
        this.activeVersion = Math.max(1, activeVersion);
        materials.put(1, digest(defaultKey == null || defaultKey.isBlank() ? "zhengliang-bi-2026-secret" : defaultKey));
        for (int ver = 2; ver <= 20; ver++) {
            String raw = env.getProperty("bi.credential.keys." + ver);
            if (raw != null && !raw.isBlank()) materials.put(ver, digest(raw.trim()));
        }
        if (!materials.containsKey(this.activeVersion)) {
            throw new IllegalStateException("bi.credential.active-key-version=" + this.activeVersion
                    + " 在密钥环中不存在；请配置 bi.credential.keys." + this.activeVersion);
        }
    }

    @Override
    public String providerId() {
        return "LOCAL_CONFIG";
    }

    @Override
    public int activeVersion() {
        return activeVersion;
    }

    @Override
    public byte[] keyMaterial(int version) {
        byte[] key = materials.get(version);
        if (key == null) throw new IllegalArgumentException("凭据密钥版本不存在: " + version);
        return key;
    }

    @Override
    public boolean supportsVersion(int version) {
        return materials.containsKey(version);
    }

    @Override
    public List<Map<String, Object>> describeVersions() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Integer, byte[]> e : materials.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", e.getKey());
            row.put("alias", "local-v" + e.getKey());
            row.put("provider", providerId());
            row.put("fingerprint", fingerprint(e.getValue()));
            row.put("status", e.getKey() == activeVersion ? "ACTIVE" : "AVAILABLE");
            rows.add(row);
        }
        return rows;
    }

    static String fingerprint(byte[] material) {
        return HexFormat.of().formatHex(material).substring(0, 16);
    }

    private static byte[] digest(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法初始化凭据加密密钥", e);
        }
    }
}
