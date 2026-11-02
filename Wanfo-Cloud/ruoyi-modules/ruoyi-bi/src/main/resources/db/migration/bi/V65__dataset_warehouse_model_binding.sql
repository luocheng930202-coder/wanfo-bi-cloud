-- Bind a BI dataset explicitly to its governed warehouse model for consumer gating.
ALTER TABLE bi_dataset_table
    ADD COLUMN warehouse_model_id bigint DEFAULT NULL AFTER datasource_id,
    ADD KEY idx_bi_dataset_warehouse_model (warehouse_model_id),
    ADD CONSTRAINT fk_bi_dataset_warehouse_model
        FOREIGN KEY (warehouse_model_id) REFERENCES dp_warehouse_table_model(id);
