-- Append-only audit trail for data-platform catalog operations.
-- Deliberately stores identifiers and bounded summaries only: never SQL, credentials, payloads, or samples.
CREATE TABLE dp_catalog_audit (
    id              bigint NOT NULL AUTO_INCREMENT,
    actor_id        bigint DEFAULT NULL,
    actor_name      varchar(64) NOT NULL DEFAULT '',
    action          varchar(64) NOT NULL,
    subject_type    varchar(32) NOT NULL,
    subject_id      bigint DEFAULT NULL,
    source_id       bigint DEFAULT NULL,
    outcome         varchar(16) NOT NULL DEFAULT 'SUCCESS',
    trace_id        varchar(64) DEFAULT NULL,
    summary_json    varchar(2000) NOT NULL DEFAULT '{}',
    created_at      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dp_audit_created (created_at),
    KEY idx_dp_audit_actor_created (actor_id, created_at),
    KEY idx_dp_audit_subject (subject_type, subject_id, created_at),
    KEY idx_dp_audit_action_created (action, created_at),
    KEY idx_dp_audit_source_created (source_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Data platform catalog operation audit';
