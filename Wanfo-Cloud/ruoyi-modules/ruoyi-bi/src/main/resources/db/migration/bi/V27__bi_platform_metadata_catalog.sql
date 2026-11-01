-- Data platform foundation: asset catalog and metadata scan history.
-- Additive only. Existing BI resource tables and rows remain unchanged.

CREATE TABLE dp_asset (
    id                  bigint NOT NULL AUTO_INCREMENT,
    asset_type          varchar(32) NOT NULL,
    source_kind         varchar(24) NOT NULL,
    source_id           bigint DEFAULT NULL,
    urn                 varchar(1000) NOT NULL,
    urn_hash            char(64) NOT NULL COMMENT 'SHA-256 of canonical URN; avoids a large text unique index',
    parent_id           bigint DEFAULT NULL,
    name                varchar(300) NOT NULL,
    qualified_name      varchar(1000) DEFAULT NULL,
    description         varchar(1000) DEFAULT NULL,
    owner_principal_id  bigint DEFAULT NULL,
    classification      varchar(32) DEFAULT NULL,
    status              varchar(16) NOT NULL DEFAULT 'ACTIVE',
    last_seen_at        datetime DEFAULT NULL,
    current_version_id  bigint DEFAULT NULL,
    create_by           varchar(64) DEFAULT '',
    create_time         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           varchar(64) DEFAULT '',
    update_time         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_asset_urn_hash (urn_hash),
    KEY idx_dp_asset_type_status (asset_type, status),
    KEY idx_dp_asset_parent (parent_id),
    KEY idx_dp_asset_source (source_kind, source_id),
    KEY idx_dp_asset_name (name(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Data platform asset catalog';

CREATE TABLE dp_metadata_scan (
    id                  bigint NOT NULL AUTO_INCREMENT,
    datasource_id       bigint NOT NULL,
    name                varchar(200) NOT NULL,
    scope_json          longtext NOT NULL,
    status              char(1) NOT NULL DEFAULT '0' COMMENT '0 enabled, 1 disabled',
    create_by           varchar(64) DEFAULT '',
    create_time         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           varchar(64) DEFAULT '',
    update_time         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dp_scan_datasource (datasource_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Metadata scan definitions';

CREATE TABLE dp_metadata_scan_run (
    id                  bigint NOT NULL AUTO_INCREMENT,
    scan_id             bigint DEFAULT NULL,
    datasource_id       bigint NOT NULL,
    trigger_type        varchar(16) NOT NULL DEFAULT 'MANUAL',
    trigger_by          varchar(64) DEFAULT '',
    status              varchar(24) NOT NULL DEFAULT 'PENDING',
    progress_total      int NOT NULL DEFAULT 0,
    progress_completed int NOT NULL DEFAULT 0,
    success_count       int NOT NULL DEFAULT 0,
    failure_count       int NOT NULL DEFAULT 0,
    error_summary       varchar(2000) DEFAULT NULL,
    result_summary_json longtext DEFAULT NULL,
    trace_id            varchar(64) DEFAULT NULL,
    started_at          datetime DEFAULT NULL,
    finished_at         datetime DEFAULT NULL,
    create_time         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dp_scan_run_scan (scan_id, create_time),
    KEY idx_dp_scan_run_source (datasource_id, create_time),
    KEY idx_dp_scan_run_status (status, create_time),
    CONSTRAINT fk_dp_scan_run_scan FOREIGN KEY (scan_id) REFERENCES dp_metadata_scan (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Metadata scan execution history';

CREATE TABLE dp_asset_version (
    id                  bigint NOT NULL AUTO_INCREMENT,
    asset_id            bigint NOT NULL,
    version_no          int NOT NULL,
    schema_hash         varchar(64) NOT NULL,
    metadata_json       longtext NOT NULL,
    change_summary_json longtext DEFAULT NULL,
    scan_run_id         bigint DEFAULT NULL,
    observed_at         datetime NOT NULL,
    create_by           varchar(64) DEFAULT '',
    create_time         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_asset_version (asset_id, version_no),
    KEY idx_dp_asset_version_observed (asset_id, observed_at),
    KEY idx_dp_asset_version_run (scan_run_id),
    CONSTRAINT fk_dp_asset_version_asset FOREIGN KEY (asset_id) REFERENCES dp_asset (id),
    CONSTRAINT fk_dp_asset_version_run FOREIGN KEY (scan_run_id) REFERENCES dp_metadata_scan_run (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Versioned asset metadata snapshots';
