-- Versioned data quality metadata. V25 JSON rules remain intact as a compatibility source.
CREATE TABLE dp_quality_rule_set (
    id bigint NOT NULL AUTO_INCREMENT,
    dataset_id bigint NOT NULL,
    version_no int NOT NULL,
    name varchar(160) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    definition_hash char(64) DEFAULT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    publish_by varchar(64) DEFAULT NULL,
    publish_time datetime(3) DEFAULT NULL,
    remark varchar(500) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_quality_rule_set_version (dataset_id, version_no),
    KEY idx_dp_quality_rule_set_status (dataset_id, status),
    CONSTRAINT fk_dp_quality_rule_set_dataset FOREIGN KEY (dataset_id)
        REFERENCES bi_dataset_table(id) ON DELETE CASCADE
);

CREATE TABLE dp_quality_rule (
    id bigint NOT NULL AUTO_INCREMENT,
    rule_set_id bigint NOT NULL,
    rule_code varchar(96) NOT NULL,
    rule_type varchar(40) NOT NULL,
    field_name varchar(128) DEFAULT NULL,
    severity varchar(16) NOT NULL DEFAULT 'ERROR',
    enabled tinyint(1) NOT NULL DEFAULT 1,
    config_json json NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_quality_rule_code (rule_set_id, rule_code),
    KEY idx_dp_quality_rule_type (rule_set_id, rule_type, enabled),
    CONSTRAINT fk_dp_quality_rule_set FOREIGN KEY (rule_set_id)
        REFERENCES dp_quality_rule_set(id) ON DELETE CASCADE
);

CREATE TABLE dp_quality_task (
    id bigint NOT NULL AUTO_INCREMENT,
    dataset_id bigint NOT NULL,
    rule_set_id bigint NOT NULL,
    name varchar(160) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    schedule_type varchar(16) NOT NULL DEFAULT 'MANUAL',
    schedule_json json DEFAULT NULL,
    scan_scope varchar(16) NOT NULL DEFAULT 'FULL',
    timeout_seconds int NOT NULL DEFAULT 900,
    retry_limit tinyint NOT NULL DEFAULT 0,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by varchar(64) DEFAULT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dp_quality_task_dataset (dataset_id, status),
    KEY idx_dp_quality_task_ruleset (rule_set_id),
    CONSTRAINT fk_dp_quality_task_dataset FOREIGN KEY (dataset_id)
        REFERENCES bi_dataset_table(id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_quality_task_rule_set FOREIGN KEY (rule_set_id)
        REFERENCES dp_quality_rule_set(id) ON DELETE RESTRICT
);

CREATE TABLE dp_quality_run (
    id bigint NOT NULL AUTO_INCREMENT,
    task_id bigint DEFAULT NULL,
    dataset_id bigint NOT NULL,
    rule_set_id bigint NOT NULL,
    rule_set_version int NOT NULL,
    trigger_type varchar(16) NOT NULL DEFAULT 'MANUAL',
    scan_scope varchar(16) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'QUEUED',
    idempotency_key varchar(128) DEFAULT NULL,
    trace_id varchar(64) DEFAULT NULL,
    source_snapshot varchar(128) DEFAULT NULL,
    rows_scanned bigint NOT NULL DEFAULT 0,
    rules_total int NOT NULL DEFAULT 0,
    rules_passed int NOT NULL DEFAULT 0,
    rules_failed int NOT NULL DEFAULT 0,
    issue_count bigint NOT NULL DEFAULT 0,
    started_at datetime(3) DEFAULT NULL,
    completed_at datetime(3) DEFAULT NULL,
    failure_code varchar(64) DEFAULT NULL,
    failure_summary varchar(1000) DEFAULT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_quality_run_idempotency (dataset_id, idempotency_key),
    KEY idx_dp_quality_run_history (dataset_id, create_time),
    KEY idx_dp_quality_run_status (status, create_time),
    CONSTRAINT fk_dp_quality_run_task FOREIGN KEY (task_id)
        REFERENCES dp_quality_task(id) ON DELETE SET NULL,
    CONSTRAINT fk_dp_quality_run_dataset FOREIGN KEY (dataset_id)
        REFERENCES bi_dataset_table(id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_quality_run_rule_set FOREIGN KEY (rule_set_id)
        REFERENCES dp_quality_rule_set(id) ON DELETE RESTRICT
);

CREATE TABLE dp_quality_result (
    id bigint NOT NULL AUTO_INCREMENT,
    run_id bigint NOT NULL,
    rule_id bigint NOT NULL,
    status varchar(16) NOT NULL,
    rows_scanned bigint NOT NULL DEFAULT 0,
    passed_count bigint DEFAULT NULL,
    failed_count bigint DEFAULT NULL,
    metric_value decimal(30,6) DEFAULT NULL,
    expected_value varchar(500) DEFAULT NULL,
    summary varchar(1000) DEFAULT NULL,
    duration_ms bigint NOT NULL DEFAULT 0,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_quality_result_rule (run_id, rule_id),
    CONSTRAINT fk_dp_quality_result_run FOREIGN KEY (run_id)
        REFERENCES dp_quality_run(id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_quality_result_rule FOREIGN KEY (rule_id)
        REFERENCES dp_quality_rule(id) ON DELETE RESTRICT
);

CREATE TABLE dp_quality_issue (
    id bigint NOT NULL AUTO_INCREMENT,
    run_id bigint NOT NULL,
    rule_id bigint NOT NULL,
    issue_key char(64) NOT NULL,
    severity varchar(16) NOT NULL,
    issue_status varchar(16) NOT NULL DEFAULT 'OPEN',
    masked_sample_json json DEFAULT NULL,
    summary varchar(1000) NOT NULL,
    claimed_by varchar(64) DEFAULT NULL,
    claimed_at datetime(3) DEFAULT NULL,
    resolved_by varchar(64) DEFAULT NULL,
    resolved_at datetime(3) DEFAULT NULL,
    resolution varchar(1000) DEFAULT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_quality_issue_key (run_id, rule_id, issue_key),
    KEY idx_dp_quality_issue_status (issue_status, severity, create_time),
    CONSTRAINT fk_dp_quality_issue_run FOREIGN KEY (run_id)
        REFERENCES dp_quality_run(id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_quality_issue_rule FOREIGN KEY (rule_id)
        REFERENCES dp_quality_rule(id) ON DELETE RESTRICT
);
