package com.wanfo.bi.datasource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Dev cloud KMS stand-in: single version from env {@code WANFO_CLOUD_KMS_DEV_KEY}.
 * Activate with {@code bi.credential.key-provider=CLOUD_KMS_DEV}.
 */
@Component
@ConditionalOnProperty(name = "bi.credential.key-provider", havingValue = "CLOUD_KMS_DEV")
public class CloudKmsDevCredentialKeyProvider implements CredentialKeyProvider {
    private static final int VERSION = 1;
    private final byte[] material;

    public CloudKmsDevCredentialKeyProvider(Environment env) {
        String raw = env.getProperty("WANFO_CLOUD_KMS_DEV_KEY");
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("CLOUD_KMS_DEV 须配置环境变量 WANFO_CLOUD_KMS_DEV_KEY");
        }
        this.material = digest(raw.trim());
    }

    @Override
    public String providerId() {
        return "CLOUD_KMS_DEV";
    }

    @Override
    public int activeVersion() {
        return VERSION;
    }

    @Override
    public byte[] keyMaterial(int version) {
        if (version != VERSION) throw new IllegalArgumentException("凭据密钥版本不存在: " + version);
        return material;
    }

    @Override
    public boolean supportsVersion(int version) {
        return version == VERSION;
    }

    @Override
    public List<Map<String, Object>> describeVersions() {
        return List.of(Map.of(
                "version", VERSION,
                "alias", "cloud-kms-dev-v1",
                "provider", providerId(),
                "fingerprint", LocalConfigCredentialKeyProvider.fingerprint(material),
                "status", "ACTIVE"));
    }

    private static byte[] digest(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法初始化 CLOUD_KMS_DEV 密钥", e);
        }
    }
}
