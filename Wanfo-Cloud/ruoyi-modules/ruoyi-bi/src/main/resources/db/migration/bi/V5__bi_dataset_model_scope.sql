ALTER TABLE bi_dataset_table ADD COLUMN source_catalog varchar(200) DEFAULT NULL COMMENT 'Validated physical database for table datasets';
INSERT INTO bi_resource(resource_type, resource_id, owner_id)
SELECT 'dataset', id, NULL FROM bi_dataset_table WHERE del_flag='0';
INSERT INTO bi_resource(resource_type, resource_id, owner_id)
SELECT 'dataset-group', id, NULL FROM bi_dataset_group WHERE del_flag='0';
-- Legacy dataset/group ownership stays administrator-managed. No grants are inferred from host IDs.
