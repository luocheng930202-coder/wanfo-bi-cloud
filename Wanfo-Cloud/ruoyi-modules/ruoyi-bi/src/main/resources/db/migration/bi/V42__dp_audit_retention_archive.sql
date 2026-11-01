CREATE TABLE dp_audit_archive (
    id bigint NOT NULL AUTO_INCREMENT,
    source_table varchar(40) NOT NULL,
    source_record_id bigint NOT NULL,
    actor_id bigint DEFAULT NULL,
    actor_name varchar(64) NOT NULL DEFAULT '',
    action varchar(64) NOT NULL,
    subject_type varchar(32) NOT NULL,
    subject_id bigint DEFAULT NULL,
    source_id bigint DEFAULT NULL,
    outcome varchar(16) NOT NULL,
    trace_id varchar(64) DEFAULT NULL,
    summary_json varchar(2000) NOT NULL DEFAULT '{}',
    created_at datetime NOT NULL,
    archived_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_audit_archive_record(source_table,source_record_id),
    KEY idx_dp_audit_archive_created(created_at),
    KEY idx_dp_audit_archive_actor(actor_id,created_at),
    KEY idx_dp_audit_archive_action(action,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable archive for catalog audit records';

CREATE TABLE dp_audit_retention_policy (
    policy_key varchar(32) NOT NULL,
    live_retention_days int NOT NULL DEFAULT 365,
    updated_by varchar(64) NOT NULL DEFAULT 'system',
    updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY(policy_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Operational audit retention settings';

INSERT INTO dp_audit_retention_policy(policy_key,live_retention_days,updated_by)
VALUES('global',365,'system');
