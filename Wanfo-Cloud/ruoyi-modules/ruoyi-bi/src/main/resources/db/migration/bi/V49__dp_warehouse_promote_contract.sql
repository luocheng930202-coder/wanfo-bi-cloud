-- S2 Wave1: control-plane contract for staging → validate → promote and consumer gates.
ALTER TABLE dp_warehouse_load_run
    ADD COLUMN publish_mode varchar(16) NOT NULL DEFAULT 'REPLACE' COMMENT 'REPLACE or MERGE' AFTER load_mode,
    ADD COLUMN promote_status varchar(24) DEFAULT NULL COMMENT 'STAGED|VALIDATING|PROMOTING|SUCCEEDED|FAILED' AFTER status,
    ADD KEY idx_dp_wh_load_promote (promote_status, create_time);

ALTER TABLE dp_warehouse_table_model
    ADD COLUMN consumer_gate varchar(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN or REQUIRE_PASS_RECON' AFTER update_strategy,
    ADD COLUMN freshness_sla_minutes int DEFAULT NULL COMMENT 'Optional freshness SLA for gate checks' AFTER consumer_gate;

-- Backfill: successful historical runs look like promoted REPLACE.
UPDATE dp_warehouse_load_run
SET promote_status = 'SUCCEEDED'
WHERE status = 'SUCCEEDED' AND promote_status IS NULL;
