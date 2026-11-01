-- First controlled warehouse full-refresh run history. This stores control data only.
CREATE TABLE dp_warehouse_load_run (
    id                      bigint NOT NULL AUTO_INCREMENT,
    source_datasource_id    bigint NOT NULL,
    target_datasource_id    bigint NOT NULL,
    source_table            varchar(256) NOT NULL,
    target_database         varchar(64) NOT NULL,
    target_table            varchar(128) NOT NULL,
    load_mode               varchar(16) NOT NULL DEFAULT 'FULL',
    status                  varchar(24) NOT NULL DEFAULT 'PENDING',
    row_count               bigint NOT NULL DEFAULT 0,
    target_replaced         tinyint NOT NULL DEFAULT 0,
    error_summary           varchar(1000) DEFAULT NULL,
    trace_id                varchar(64) DEFAULT NULL,
    trigger_by              varchar(64) DEFAULT '',
    started_at              datetime DEFAULT NULL,
    finished_at             datetime DEFAULT NULL,
    create_time             datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dp_wh_load_source (source_datasource_id, create_time),
    KEY idx_dp_wh_load_target (target_datasource_id, target_database, target_table, create_time),
    KEY idx_dp_wh_load_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Warehouse full-refresh ingestion run history';
