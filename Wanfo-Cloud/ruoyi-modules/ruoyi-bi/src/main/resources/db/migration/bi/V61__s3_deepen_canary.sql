-- S3-03 canary traffic share within a family (0=off/legacy exclusive, 1-100=% of stable traffic).
ALTER TABLE dp_data_api
    ADD COLUMN canary_percent int NOT NULL DEFAULT 0
        COMMENT '0=not canary; 1-100=% of family-stable traffic routed here' AFTER contract_checked_at;
