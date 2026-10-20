ALTER TABLE bi_dashboard ADD COLUMN definition_json longtext NULL,
    ADD COLUMN published_json longtext NULL,
    ADD COLUMN version int NOT NULL DEFAULT 0,
    ADD COLUMN published_version int NULL;
CREATE TABLE bi_dashboard_binding (
    dashboard_id bigint NOT NULL,
    chart_id bigint NOT NULL,
    dataset_id bigint NOT NULL,
    binding_kind varchar(16) NOT NULL,
    PRIMARY KEY(dashboard_id,chart_id,binding_kind),
    KEY idx_dashboard_binding_chart(chart_id),
    KEY idx_dashboard_binding_dataset(dataset_id)
);
INSERT INTO bi_resource(resource_type,resource_id,owner_id)
SELECT 'dashboard',id,NULL FROM bi_dashboard WHERE del_flag='0';
