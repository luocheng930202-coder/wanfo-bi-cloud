-- S2 Wave2: quality alert dedupe/escalation + issue re-verify binding.
CREATE TABLE dp_quality_alert_dedupe (
    dedupe_key char(64) NOT NULL COMMENT 'sha256(datasetId:ruleId:severity)',
    dataset_id bigint NOT NULL,
    rule_id bigint NOT NULL,
    severity varchar(16) NOT NULL,
    last_sent_at datetime(3) NOT NULL,
    last_issue_id bigint DEFAULT NULL,
    escalation_level tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (dedupe_key),
    KEY idx_dp_quality_alert_dedupe_ds (dataset_id, last_sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quality alert send window dedupe';

CREATE TABLE dp_quality_alert_log (
    id bigint NOT NULL AUTO_INCREMENT,
    issue_id bigint NOT NULL,
    dataset_id bigint NOT NULL,
    channel varchar(16) NOT NULL COMMENT 'inbox',
    recipient_user_id bigint DEFAULT NULL,
    escalation_level tinyint NOT NULL DEFAULT 0,
    title varchar(200) NOT NULL,
    body varchar(1000) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dp_quality_alert_log_issue (issue_id, create_time),
    KEY idx_dp_quality_alert_log_ds (dataset_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quality alert delivery log (no row samples)';

ALTER TABLE dp_quality_issue
    ADD COLUMN verify_run_id bigint DEFAULT NULL COMMENT 'Successful revalidation run that closed the issue' AFTER resolution,
    ADD COLUMN fixed_at datetime(3) DEFAULT NULL COMMENT 'When claimant marked FIXED_PENDING' AFTER verify_run_id,
    ADD KEY idx_dp_quality_issue_verify (issue_status, rule_id, verify_run_id);
