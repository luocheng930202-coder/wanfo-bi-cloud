-- S3-05: credential key version metadata + rotation audit (no key material stored).
CREATE TABLE dp_credential_key_meta (
    version_no int NOT NULL,
    key_alias varchar(64) NOT NULL,
    provider varchar(32) NOT NULL DEFAULT 'LOCAL_CONFIG' COMMENT 'LOCAL_CONFIG|EXTERNAL_KMS',
    fingerprint char(16) NOT NULL COMMENT 'SHA-256 hex prefix of key material; not reversible to key',
    status varchar(16) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'ACTIVE|AVAILABLE|RETIRED',
    note varchar(500) DEFAULT NULL,
    update_by varchar(64) DEFAULT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (version_no),
    UNIQUE KEY uk_dp_cred_key_alias (key_alias),
    KEY idx_dp_cred_key_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Credential encryption key versions (metadata only)';

CREATE TABLE dp_credential_key_rotation (
    id bigint NOT NULL AUTO_INCREMENT,
    from_versions_json json DEFAULT NULL COMMENT 'ciphertext prefix counts before rotate',
    to_version int NOT NULL,
    scanned int NOT NULL DEFAULT 0,
    rewritten int NOT NULL DEFAULT 0,
    skipped int NOT NULL DEFAULT 0,
    failed int NOT NULL DEFAULT 0,
    reason varchar(500) DEFAULT NULL,
    actor varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dp_cred_rotation_time (create_time),
    KEY idx_dp_cred_rotation_to (to_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Credential key rotation audit';
