package com.wanfo.bi.datasource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Single-version key provider for tests and simple local bootstraps. */
public final class SingleKeyCredentialKeyProvider implements CredentialKeyProvider {
    private final byte[] material;
    private final String fingerprint;

    public SingleKeyCredentialKeyProvider(String rawKey) {
        String key = rawKey == null || rawKey.isBlank() ? "zhengliang-bi-2026-secret" : rawKey;
        try {
            this.material = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法初始化凭据加密密钥", e);
        }
        this.fingerprint = HexFormat.of().formatHex(material).substring(0, 16);
    }

    @Override
    public String providerId() {
        return "LOCAL_SINGLE";
    }

    @Override
    public int activeVersion() {
        return 1;
    }

    @Override
    public byte[] keyMaterial(int version) {
        if (version != 1) throw new IllegalArgumentException("凭据密钥版本不存在: " + version);
        return material;
    }

    @Override
    public boolean supportsVersion(int version) {
        return version == 1;
    }

    @Override
    public List<Map<String, Object>> describeVersions() {
        return List.of(Map.of(
                "version", 1,
                "alias", "local-v1",
                "provider", providerId(),
                "fingerprint", fingerprint,
                "status", "ACTIVE"));
    }
}
