-- S3 deepen W7: fair lane priority, field-standard domain + bind plans,
-- caller-weighted canary + call sample, migration override ticket.
-- Note: dp_task_pool_lane.priority/weight may already exist from a partial prior run.

SET @db := DATABASE();

SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=@db AND table_name='dp_task_pool_lane' AND column_name='priority')=0,
    'ALTER TABLE dp_task_pool_lane ADD COLUMN priority int NOT NULL DEFAULT 100 COMMENT ''lower runs first when global soft-cap contended'' AFTER max_concurrent, ADD COLUMN weight int NOT NULL DEFAULT 1 COMMENT ''relative share weight for fair soft concurrency'' AFTER priority',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=@db AND table_name='dp_field_standard' AND column_name='domain_code')=0,
    'ALTER TABLE dp_field_standard ADD COLUMN domain_code varchar(64) NOT NULL DEFAULT ''DEFAULT'' COMMENT ''business domain for standard hierarchy'' AFTER standard_code',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dp_field_standard_bind_plan (
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

SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=@db AND table_name='dp_data_api_caller' AND column_name='canary_weight')=0,
    'ALTER TABLE dp_data_api_caller ADD COLUMN canary_weight int NOT NULL DEFAULT 100 COMMENT ''0-1000; scales sticky canary admission for this caller'' AFTER daily_quota',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=@db AND table_name='dp_data_api_call' AND column_name='sample_json')=0,
    'ALTER TABLE dp_data_api_call ADD COLUMN sample_json mediumtext NULL COMMENT ''optional redacted response sample for audit replay'' AFTER error_code',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=@db AND table_name='dp_schema_migration_plan' AND column_name='blocking_override_ticket')=0,
    'ALTER TABLE dp_schema_migration_plan ADD COLUMN blocking_override_ticket varchar(128) DEFAULT NULL COMMENT ''required when execute overrideBlocking=true'' AFTER approved_at, ADD COLUMN blocking_override_by varchar(64) DEFAULT NULL AFTER blocking_override_ticket, ADD COLUMN blocking_override_at datetime(3) DEFAULT NULL AFTER blocking_override_by',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dp_change_ticket (
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
