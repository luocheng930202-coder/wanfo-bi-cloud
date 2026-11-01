CREATE TABLE dp_data_api (
    id bigint NOT NULL AUTO_INCREMENT,
    api_code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    metric_id bigint NOT NULL,
    metric_published_version int DEFAULT NULL,
    status varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT,ACTIVE,DISABLED',
    max_page_size int NOT NULL DEFAULT 100,
    timeout_seconds int NOT NULL DEFAULT 20,
    calls_per_minute int NOT NULL DEFAULT 60,
    daily_quota int NOT NULL DEFAULT 10000,
    owner_actor_json longtext NOT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by varchar(64) NOT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_data_api_code(api_code),
    KEY idx_dp_data_api_metric(metric_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Governed read-only metric APIs';

CREATE TABLE dp_data_api_revision (
    id bigint NOT NULL AUTO_INCREMENT,
    api_id bigint NOT NULL,
    revision_no int NOT NULL,
    definition_json longtext NOT NULL,
    created_by varchar(64) NOT NULL,
    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_data_api_revision(api_id,revision_no),
    CONSTRAINT fk_dp_data_api_revision_api FOREIGN KEY(api_id) REFERENCES dp_data_api(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable controlled API versions';

CREATE TABLE dp_data_api_key (
    id bigint NOT NULL AUTO_INCREMENT,
    api_id bigint NOT NULL,
    key_hash char(64) NOT NULL,
    key_prefix varchar(20) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE,REVOKED',
    create_by varchar(64) NOT NULL,
    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at datetime(3) DEFAULT NULL,
    last_used_at datetime(3) DEFAULT NULL,
    revoked_at datetime(3) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_data_api_key_hash(key_hash),
    KEY idx_dp_data_api_key_active(api_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Hashed credentials for governed data APIs';

CREATE TABLE dp_data_api_call (
    id bigint NOT NULL AUTO_INCREMENT,
    api_id bigint NOT NULL,
    api_key_id bigint NOT NULL,
    status varchar(16) NOT NULL COMMENT 'SUCCESS,REJECTED,FAILED',
    duration_ms int NOT NULL DEFAULT 0,
    row_count int NOT NULL DEFAULT 0,
    error_code varchar(64) DEFAULT NULL,
    called_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dp_data_api_call_api(api_id,called_at),
    KEY idx_dp_data_api_call_key(api_key_id,called_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Payload-free API call audit';

CREATE TABLE dp_data_api_usage_counter (
    api_id bigint NOT NULL,
    api_key_id bigint NOT NULL,
    window_kind varchar(8) NOT NULL COMMENT 'MINUTE,DAY',
    window_start datetime NOT NULL,
    call_count int NOT NULL DEFAULT 0,
    PRIMARY KEY(api_id,api_key_id,window_kind,window_start),
    KEY idx_dp_data_api_counter_window(window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Atomic fixed-window API quotas';
