-- S3 deepen W7: fair lane priority, field-standard domain + bind plans,
-- caller-weighted canary + call sample, migration override ticket.

ALTER TABLE dp_task_pool_lane
    ADD COLUMN priority int NOT NULL DEFAULT 100 COMMENT 'lower runs first when global soft-cap contended' AFTER max_concurrent,
    ADD COLUMN weight int NOT NULL DEFAULT 1 COMMENT 'relative share weight for fair soft concurrency' AFTER priority;

ALTER TABLE dp_field_standard
    ADD COLUMN domain_code varchar(64) NOT NULL DEFAULT 'DEFAULT' COMMENT 'business domain for standard hierarchy' AFTER code;

CREATE TABLE dp_field_standard_bind_plan (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(160) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVED/APPLIED/REJECTED',
    bindings_json mediumtext NOT NULL COMMENT '[{standardId,assetId,columnName}]',
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    approved_by varchar(64) DEFAULT NULL,
    approved_at datetime(3) DEFAULT NULL,
    applied_by varchar(64) DEFAULT NULL,
    applied_at datetime(3) DEFAULT NULL,
    remark varchar(500) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_fs_bind_plan_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='S3-02 batch field-standard bind approval';

ALTER TABLE dp_data_api_caller
    ADD COLUMN canary_weight int NOT NULL DEFAULT 100 COMMENT '0-1000; scales sticky canary admission for this caller' AFTER daily_quota;

ALTER TABLE dp_data_api_call
    ADD COLUMN sample_json mediumtext NULL COMMENT 'optional redacted response sample for audit replay' AFTER error_code;

ALTER TABLE dp_schema_migration_plan
    ADD COLUMN blocking_override_ticket varchar(128) DEFAULT NULL COMMENT 'required when execute overrideBlocking=true' AFTER approved_at,
    ADD COLUMN blocking_override_by varchar(64) DEFAULT NULL AFTER blocking_override_ticket,
    ADD COLUMN blocking_override_at datetime(3) DEFAULT NULL AFTER blocking_override_by;

CREATE TABLE dp_change_ticket (
    id bigint NOT NULL AUTO_INCREMENT,
    asset_id bigint NOT NULL,
    title varchar(200) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/APPROVED/CLOSED',
    severity varchar(16) NOT NULL DEFAULT 'REVIEW',
    source_type varchar(32) NOT NULL DEFAULT 'IMPACT_PACK' COMMENT 'IMPACT_PACK/MANUAL',
    summary_json mediumtext NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    approved_by varchar(64) DEFAULT NULL,
    approved_at datetime(3) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_change_ticket_asset (asset_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='S3-04 auto/manual change tickets from impact packs';
