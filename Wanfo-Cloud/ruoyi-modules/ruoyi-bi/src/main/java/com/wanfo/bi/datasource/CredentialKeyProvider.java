package com.wanfo.bi.datasource;

import java.util.List;
import java.util.Map;

/** Pluggable key material source for credential envelopes. Cloud KMS adapters implement this later. */
public interface CredentialKeyProvider {
    String providerId();

    int activeVersion();

    /** Raw AES key bytes for a version; missing version throws. */
    byte[] keyMaterial(int version);

    /** Known versions with fingerprint (hex prefix) and whether active. */
    List<Map<String, Object>> describeVersions();

    boolean supportsVersion(int version);
}
