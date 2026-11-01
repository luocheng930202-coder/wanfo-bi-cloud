package com.wanfo.bi.datasource;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Placeholder for cloud KMS. Activated with {@code bi.credential.key-provider=EXTERNAL_KMS_STUB}.
 * Does not hold key material — rotate/decrypt must fail closed until a real adapter is wired.
 */
@Component
@ConditionalOnProperty(name = "bi.credential.key-provider", havingValue = "EXTERNAL_KMS_STUB")
public class ExternalKmsStubCredentialKeyProvider implements CredentialKeyProvider {
    @Override
    public String providerId() {
        return "EXTERNAL_KMS_STUB";
    }

    @Override
    public int activeVersion() {
        return 1;
    }

    @Override
    public byte[] keyMaterial(int version) {
        throw new IllegalStateException("EXTERNAL_KMS_STUB 未配置真实 KMS；请改回 bi.credential.key-provider=LOCAL_CONFIG 或接入云 KMS 适配器");
    }

    @Override
    public boolean supportsVersion(int version) {
        return false;
    }

    @Override
    public List<Map<String, Object>> describeVersions() {
        return List.of(Map.of(
                "version", 1,
                "alias", "kms-stub",
                "provider", providerId(),
                "fingerprint", "unavailable",
                "status", "UNCONFIGURED"));
    }
}
