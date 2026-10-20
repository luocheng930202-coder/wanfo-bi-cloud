CREATE TABLE bi_data_form (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(200) NOT NULL,
    dataset_id bigint NOT NULL,
    fields_json mediumtext NOT NULL COMMENT '填报字段定义 JSON',
    remark varchar(500) DEFAULT NULL,
    status char(1) NOT NULL DEFAULT '0' COMMENT '0 启用 1 停用',
    create_by varchar(64) DEFAULT NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_by varchar(64) DEFAULT NULL,
    update_time datetime DEFAULT NULL,
    del_flag char(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_data_form_dataset (dataset_id)
);

CREATE TABLE bi_data_form_record (
    id bigint NOT NULL AUTO_INCREMENT,
    form_id bigint NOT NULL,
    row_json mediumtext NOT NULL,
    success char(1) NOT NULL DEFAULT '1',
    error_msg varchar(500) DEFAULT NULL,
    create_by varchar(64) DEFAULT NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_bi_data_form_record_form (form_id)
);
