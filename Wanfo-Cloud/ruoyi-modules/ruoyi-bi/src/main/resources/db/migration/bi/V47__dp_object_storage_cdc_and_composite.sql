-- Object-storage batch ingest runs (S3-compatible / OSS via MinIO client).
CREATE TABLE IF NOT EXISTS dp_object_storage_load_run (
    id bigint NOT NULL AUTO_INCREMENT,
    datasource_id bigint NOT NULL,
    target_datasource_id bigint NOT NULL,
    bucket varchar(255) NOT NULL,
    object_prefix varchar(512) NOT NULL DEFAULT '',
    target_database varchar(64) NOT NULL,
    target_table varchar(128) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    object_count int NOT NULL DEFAULT 0,
    row_count bigint NOT NULL DEFAULT 0,
    error_summary varchar(1000) DEFAULT NULL,
    request_snapshot_json json DEFAULT NULL,
    trace_id varchar(64) DEFAULT NULL,
    trigger_by varchar(64) NOT NULL,
    started_at datetime(3) DEFAULT NULL,
    finished_at datetime(3) DEFAULT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dp_obj_load_target (target_datasource_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Object storage CSV batch loads into warehouse';

-- External CDC / Debezium orchestration (platform schedules poll of staging table).
CREATE TABLE IF NOT EXISTS dp_cdc_ingest_job (
    id bigint NOT NULL AUTO_INCREMENT,
    job_code varchar(96) NOT NULL,
    name varchar(160) NOT NULL,
    source_datasource_id bigint NOT NULL,
    staging_table varchar(128) NOT NULL,
    target_datasource_id bigint NOT NULL,
    target_layer varchar(8) NOT NULL DEFAULT 'ODS',
    target_table varchar(128) NOT NULL,
    watermark_column varchar(128) NOT NULL DEFAULT 'cdc_ts',
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    interval_minutes int NOT NULL DEFAULT 5,
    last_watermark varchar(128) DEFAULT NULL,
    last_run_at datetime(3) DEFAULT NULL,
    last_error varchar(1000) DEFAULT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by varchar(64) DEFAULT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_cdc_job_code (job_code),
    KEY idx_dp_cdc_status (status, last_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDC ingest job definitions over Debezium staging tables';

-- Snapshot-driven dependent dataset field migration rollbacks.
CREATE TABLE IF NOT EXISTS dp_dependent_field_migration (
    id bigint NOT NULL AUTO_INCREMENT,
    datasource_id bigint NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'APPLIED',
    mapping_json json NOT NULL,
    before_snapshot_json json NOT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    rolled_back_by varchar(64) DEFAULT NULL,
    rolled_back_at datetime(3) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_dp_dep_field_mig_ds (datasource_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Auto field remap for datasets dependent on file/API snapshot schema changes';
