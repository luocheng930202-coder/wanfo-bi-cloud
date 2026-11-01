-- S3-06: backup / restore drill registry (evidence for OPS-010; no automatic restore).
CREATE TABLE dp_backup_drill (
    id bigint NOT NULL AUTO_INCREMENT,
    drill_type varchar(32) NOT NULL COMMENT 'METADATA_DUMP|RESTORE_DRY_RUN|FLYWAY_CHECK|WAREHOUSE_NOTE|FULL_DRILL',
    scope_json json NOT NULL COMMENT 'targets covered: metadataDb/redis/warehouse/objectStore',
    result varchar(16) NOT NULL COMMENT 'PASSED|FAILED|PARTIAL',
    rpo_minutes int DEFAULT NULL COMMENT 'observed or declared RPO for this drill',
    rto_minutes int DEFAULT NULL COMMENT 'observed or declared RTO for this drill',
    artifact_path varchar(1000) DEFAULT NULL COMMENT 'backup file path or ticket id; no secrets',
    checklist_json json DEFAULT NULL,
    notes varchar(2000) DEFAULT NULL,
    actor varchar(64) NOT NULL,
    started_at datetime(3) NOT NULL,
    finished_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dp_backup_drill_type (drill_type, finished_at),
    KEY idx_dp_backup_drill_result (result, finished_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Backup and restore drill evidence';
