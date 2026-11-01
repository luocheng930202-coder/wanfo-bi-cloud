-- Preserve the exact non-secret request needed to safely retry a warehouse load.
ALTER TABLE dp_warehouse_load_run
    ADD COLUMN request_snapshot_json JSON NULL AFTER load_mode;
