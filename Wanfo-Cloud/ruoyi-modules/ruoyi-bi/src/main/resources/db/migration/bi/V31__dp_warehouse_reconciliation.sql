-- Reconciliation facts for published warehouse model runs; no business rows are stored here.
CREATE TABLE dp_warehouse_reconciliation (
    run_id                    bigint NOT NULL,
    target_datasource_id      bigint NOT NULL,
    model_version            varchar(64) NOT NULL,
    source_table             varchar(256) NOT NULL,
    target_table             varchar(256) NOT NULL,
    check_scope              varchar(16) NOT NULL DEFAULT 'FULL',
    source_row_count         bigint NOT NULL DEFAULT 0,
    target_row_count         bigint NOT NULL DEFAULT 0,
    source_sales_amount      decimal(30,2) NOT NULL DEFAULT 0,
    target_sales_amount      decimal(30,2) NOT NULL DEFAULT 0,
    source_net_quantity      decimal(30,3) NOT NULL DEFAULT 0,
    target_net_quantity      decimal(30,3) NOT NULL DEFAULT 0,
    source_max_updated_at    datetime(3) DEFAULT NULL,
    observed_at              datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    freshness_age_seconds    bigint DEFAULT NULL,
    status                   varchar(16) NOT NULL,
    summary                  varchar(1000) DEFAULT NULL,
    PRIMARY KEY (run_id),
    KEY idx_dp_wh_reconcile_target_time (target_datasource_id, model_version, observed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Warehouse reconciliation and freshness results';
