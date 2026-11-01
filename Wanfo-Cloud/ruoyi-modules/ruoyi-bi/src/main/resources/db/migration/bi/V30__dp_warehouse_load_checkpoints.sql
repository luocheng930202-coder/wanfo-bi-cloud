-- Per-table control state for bounded, manually triggered incremental warehouse loads.
CREATE TABLE dp_warehouse_load_checkpoint (
    id                       bigint NOT NULL AUTO_INCREMENT,
    source_datasource_id     bigint NOT NULL,
    source_table             varchar(256) NOT NULL,
    target_database          varchar(64) NOT NULL,
    target_table             varchar(128) NOT NULL,
    watermark_column         varchar(128) NOT NULL,
    watermark_jdbc_type      int NOT NULL,
    key_columns              varchar(1000) NOT NULL,
    watermark_value          varchar(512) NOT NULL,
    last_successful_run_id   bigint NOT NULL,
    updated_at               datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_wh_checkpoint_scope (source_datasource_id, source_table, target_database, target_table),
    KEY idx_dp_wh_checkpoint_run (last_successful_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Incremental warehouse load checkpoints';
