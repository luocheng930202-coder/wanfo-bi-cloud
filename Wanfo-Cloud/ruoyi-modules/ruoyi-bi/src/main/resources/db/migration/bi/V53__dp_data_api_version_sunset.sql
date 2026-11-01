-- S2 Wave4: Data API version / deprecation / sunset (no Key redesign).

ALTER TABLE dp_data_api
    ADD COLUMN api_version int NOT NULL DEFAULT 1 COMMENT 'Contract version pin for OpenAPI consumers' AFTER metric_published_version,
    ADD COLUMN deprecated_at datetime(3) DEFAULT NULL COMMENT 'Mark ACTIVE as deprecated; calls still allowed until sunset' AFTER status,
    ADD COLUMN sunset_at datetime(3) DEFAULT NULL COMMENT 'After this instant, open-data calls are rejected' AFTER deprecated_at,
    ADD COLUMN successor_api_code varchar(64) DEFAULT NULL COMMENT 'Replacement api_code for clients' AFTER sunset_at,
    ADD COLUMN deprecation_note varchar(1000) DEFAULT NULL COMMENT 'Human-readable sunset guidance' AFTER successor_api_code,
    ADD KEY idx_dp_data_api_sunset (status, sunset_at);
