-- Versioned, approved and reversible platform reference migrations.
ALTER TABLE bi_dataset_field
    ADD COLUMN source_name varchar(300) DEFAULT NULL COMMENT 'Physical source column for a stable BI logical field name' AFTER origin_name;

CREATE TABLE dp_schema_migration_plan (
    id bigint NOT NULL AUTO_INCREMENT,
    schema_change_event_id bigint NOT NULL,
    plan_no int NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, BLOCKED, APPROVED, EXECUTED, FAILED, ROLLED_BACK',
    definition_json longtext NOT NULL,
    blockers_json longtext DEFAULT NULL,
    before_snapshot_json longtext DEFAULT NULL,
    execution_error varchar(1000) DEFAULT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    approved_by varchar(64) DEFAULT NULL,
    approved_at datetime(3) DEFAULT NULL,
    executed_by varchar(64) DEFAULT NULL,
    executed_at datetime(3) DEFAULT NULL,
    rolled_back_by varchar(64) DEFAULT NULL,
    rolled_back_at datetime(3) DEFAULT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_schema_migration_plan_no (schema_change_event_id,plan_no),
    KEY idx_dp_schema_migration_status (status,update_time),
    CONSTRAINT fk_dp_schema_migration_event FOREIGN KEY (schema_change_event_id) REFERENCES dp_schema_change_event(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Approved and reversible schema migration plans';

CREATE TABLE dp_schema_migration_step (
    id bigint NOT NULL AUTO_INCREMENT,
    plan_id bigint NOT NULL,
    step_order int NOT NULL,
    dataset_id bigint NOT NULL,
    dataset_field_id bigint NOT NULL,
    operation varchar(16) NOT NULL COMMENT 'RENAME or DROP',
    source_field varchar(300) NOT NULL,
    target_field varchar(300) DEFAULT NULL,
    target_type varchar(32) DEFAULT NULL,
    before_json longtext NOT NULL,
    after_json longtext NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, APPLIED, ROLLED_BACK',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_schema_migration_step (plan_id,step_order),
    KEY idx_dp_schema_migration_step_dataset (dataset_id,dataset_field_id),
    CONSTRAINT fk_dp_schema_migration_step_plan FOREIGN KEY (plan_id) REFERENCES dp_schema_migration_plan(id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_schema_migration_step_dataset FOREIGN KEY (dataset_id) REFERENCES bi_dataset_table(id),
    CONSTRAINT fk_dp_schema_migration_step_field FOREIGN KEY (dataset_field_id) REFERENCES bi_dataset_field(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Explicit field mappings and reversible migration actions';

CREATE TABLE dp_schema_migration_revision (
    id bigint NOT NULL AUTO_INCREMENT,
    plan_id bigint NOT NULL,
    revision_no int NOT NULL,
    plan_snapshot_json longtext NOT NULL,
    change_kind varchar(24) NOT NULL,
    changed_by varchar(64) NOT NULL,
    changed_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_schema_migration_revision (plan_id,revision_no),
    KEY idx_dp_schema_migration_revision_changed (changed_at),
    CONSTRAINT fk_dp_schema_migration_revision_plan FOREIGN KEY (plan_id) REFERENCES dp_schema_migration_plan(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable schema migration plan state history';
