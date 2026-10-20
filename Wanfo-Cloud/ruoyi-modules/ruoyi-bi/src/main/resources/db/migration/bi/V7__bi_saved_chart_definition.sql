ALTER TABLE bi_chart_view ADD COLUMN definition_json longtext NULL,
    ADD COLUMN version int NOT NULL DEFAULT 0;
INSERT INTO bi_resource(resource_type,resource_id,owner_id)
SELECT 'chart',id,NULL FROM bi_chart_view WHERE del_flag='0';
