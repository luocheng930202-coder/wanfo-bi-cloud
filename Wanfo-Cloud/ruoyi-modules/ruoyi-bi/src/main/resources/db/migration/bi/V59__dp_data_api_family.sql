-- S3-03: multi-version coexistence — logical family groups concurrent ACTIVE api_codes.
ALTER TABLE dp_data_api
    ADD COLUMN family_code varchar(64) DEFAULT NULL COMMENT 'Logical product family; multiple ACTIVE versions may coexist' AFTER api_code,
    ADD KEY idx_dp_data_api_family (family_code, status, api_version);

UPDATE dp_data_api SET family_code = api_code WHERE family_code IS NULL OR family_code = '';
