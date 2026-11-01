-- Standard metric cutover (atomic/derived/composite). Clean break: no legacy scalar/table JSON mapping.
-- Wipe dependent consumers first, then metric rows.

DELETE FROM dp_data_api_call;
DELETE FROM dp_data_api_usage_counter;
DELETE FROM dp_data_api_key;
DELETE FROM dp_data_api_revision;
DELETE FROM dp_data_api;

UPDATE dp_field_standard SET metric_id = NULL WHERE metric_id IS NOT NULL;

DELETE FROM dp_metric_set_member;
DELETE FROM dp_metric_publish_request;
DELETE FROM bi_metric_usage;
DELETE FROM bi_metric_history;
DELETE FROM bi_resource_grant WHERE resource_type = 'metric';
DELETE FROM bi_resource WHERE resource_type = 'metric';
DELETE FROM bi_metric;

ALTER TABLE bi_metric
    MODIFY COLUMN kind varchar(16) NOT NULL COMMENT 'atomic|derived|composite',
    MODIFY COLUMN definition_json longtext NOT NULL COMMENT '草稿定义 JSON schemaVersion=2';
