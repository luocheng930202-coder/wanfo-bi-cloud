-- S3-03: freeze response column contract on publish; gate breaking changes vs successor baseline.
ALTER TABLE dp_data_api
    ADD COLUMN contract_schema_json MEDIUMTEXT NULL COMMENT '发布时冻结的响应列契约 JSON' AFTER deprecation_note,
    ADD COLUMN contract_checked_at DATETIME(3) NULL COMMENT '最近一次契约校验时间' AFTER contract_schema_json;
