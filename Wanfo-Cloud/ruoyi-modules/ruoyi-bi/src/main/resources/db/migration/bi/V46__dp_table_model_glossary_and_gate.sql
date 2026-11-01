-- Declared warehouse table models (dimension/fact) — no client DDL; publish uses controlled materialize/load only.
CREATE TABLE dp_warehouse_table_model (
    id bigint NOT NULL AUTO_INCREMENT,
    model_code varchar(96) NOT NULL,
    name varchar(160) NOT NULL,
    layer varchar(8) NOT NULL,
    table_name varchar(128) NOT NULL,
    table_kind varchar(16) NOT NULL COMMENT 'DIMENSION or FACT',
    primary_keys_json json NOT NULL,
    partition_field varchar(128) DEFAULT NULL,
    update_strategy varchar(16) NOT NULL DEFAULT 'REPLACE' COMMENT 'REPLACE or MERGE',
    description varchar(1000) DEFAULT NULL,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by varchar(64) DEFAULT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_wh_table_model_code (model_code),
    UNIQUE KEY uk_dp_wh_table_model_phys (layer, table_name),
    KEY idx_dp_wh_table_model_status (status, layer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Declared warehouse dim/fact models';

-- Quality issue claim SLA deadline (hours from claim; configured in app).
ALTER TABLE dp_quality_issue
    ADD COLUMN sla_due_at datetime(3) DEFAULT NULL AFTER claimed_at,
    ADD KEY idx_dp_quality_issue_sla (issue_status, sla_due_at);

-- Business glossary / metric dictionary terms (optional bind to bi_metric).
CREATE TABLE dp_glossary_term (
    id bigint NOT NULL AUTO_INCREMENT,
    term_code varchar(96) NOT NULL,
    name varchar(160) NOT NULL,
    category varchar(64) DEFAULT NULL,
    definition_text varchar(2000) NOT NULL,
    owner_name varchar(64) DEFAULT NULL,
    certification_status varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/REVIEW/CERTIFIED/DEPRECATED',
    metric_id bigint DEFAULT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by varchar(64) DEFAULT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_glossary_term_code (term_code),
    KEY idx_dp_glossary_metric (metric_id),
    KEY idx_dp_glossary_status (certification_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Business glossary and metric dictionary';
