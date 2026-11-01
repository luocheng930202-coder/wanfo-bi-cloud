-- S3-03: persist anonymized contract sample for historical shape replay.
ALTER TABLE dp_data_api
    ADD COLUMN contract_sample_json mediumtext NULL COMMENT 'Anonymized probe sample at publish (kinds + truncated cells)' AFTER canary_percent,
    ADD COLUMN contract_sample_at datetime(3) NULL COMMENT 'When contract_sample_json was last written' AFTER contract_sample_json;
