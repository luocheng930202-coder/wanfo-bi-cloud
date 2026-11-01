-- Auditable, version-linked schema change events and their impact previews.
CREATE TABLE dp_schema_change_event (
    id bigint NOT NULL AUTO_INCREMENT,
    asset_id bigint NOT NULL,
    previous_version_id bigint DEFAULT NULL,
    current_version_id bigint NOT NULL,
    scan_run_id bigint DEFAULT NULL,
    change_type varchar(32) NOT NULL DEFAULT 'SCHEMA_CHANGED',
    details_json longtext NOT NULL,
    actor varchar(64) NOT NULL,
    observed_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_schema_change_version (current_version_id),
    KEY idx_dp_schema_change_asset (asset_id, observed_at),
    KEY idx_dp_schema_change_scan_run (scan_run_id),
    CONSTRAINT fk_dp_schema_change_asset FOREIGN KEY (asset_id) REFERENCES dp_asset(id),
    CONSTRAINT fk_dp_schema_change_previous_version FOREIGN KEY (previous_version_id) REFERENCES dp_asset_version(id) ON DELETE SET NULL,
    CONSTRAINT fk_dp_schema_change_current_version FOREIGN KEY (current_version_id) REFERENCES dp_asset_version(id),
    CONSTRAINT fk_dp_schema_change_scan_run FOREIGN KEY (scan_run_id) REFERENCES dp_metadata_scan_run(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Detected schema change events for catalog assets';
