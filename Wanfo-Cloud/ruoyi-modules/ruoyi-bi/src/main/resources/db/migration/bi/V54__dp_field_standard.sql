-- S3-02: field standard dictionary + catalog column bindings (weak glossary/metric links).
CREATE TABLE dp_field_standard (
    id bigint NOT NULL AUTO_INCREMENT,
    standard_code varchar(96) NOT NULL,
    name varchar(160) NOT NULL,
    data_type_family varchar(16) NOT NULL COMMENT 'STRING/NUMBER/DATETIME/BOOLEAN',
    max_length int DEFAULT NULL,
    nullable_policy varchar(16) NOT NULL DEFAULT 'OPTIONAL' COMMENT 'REQUIRED/OPTIONAL',
    value_pattern varchar(256) DEFAULT NULL,
    enum_values_json json DEFAULT NULL,
    match_aliases_json json DEFAULT NULL COMMENT 'extra column names that map to this standard',
    glossary_term_id bigint DEFAULT NULL,
    metric_id bigint DEFAULT NULL,
    description varchar(1000) DEFAULT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DRAFT/DEPRECATED',
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by varchar(64) DEFAULT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_field_standard_code (standard_code),
    KEY idx_dp_field_standard_status (status, data_type_family),
    KEY idx_dp_field_standard_glossary (glossary_term_id),
    KEY idx_dp_field_standard_metric (metric_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Enterprise field standards dictionary';

CREATE TABLE dp_field_standard_binding (
    id bigint NOT NULL AUTO_INCREMENT,
    standard_id bigint NOT NULL,
    asset_id bigint NOT NULL COMMENT 'dp_asset COLUMN id',
    bind_source varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL|AUTO_NAME',
    note varchar(500) DEFAULT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_field_std_bind_asset (asset_id),
    KEY idx_dp_field_std_bind_std (standard_id),
    CONSTRAINT fk_dp_field_std_bind_std FOREIGN KEY (standard_id) REFERENCES dp_field_standard (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_field_std_bind_asset FOREIGN KEY (asset_id) REFERENCES dp_asset (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Column asset to field standard binding';

INSERT INTO dp_field_standard(standard_code,name,data_type_family,max_length,nullable_policy,value_pattern,match_aliases_json,description,status,create_by)
VALUES
('STD_CUSTOMER_ID','客户标识','STRING',64,'REQUIRED',NULL,CAST('["customer_id","cust_id","user_id"]' AS JSON),'统一客户主键/外键字段','ACTIVE','system'),
('STD_AMOUNT','金额','NUMBER',NULL,'OPTIONAL',NULL,CAST('["amount","amt","pay_amount","order_amount"]' AS JSON),'货币金额，建议 NUMBER/DECIMAL','ACTIVE','system'),
('STD_EVENT_TIME','业务发生时间','DATETIME',NULL,'REQUIRED',NULL,CAST('["event_time","biz_time","occurred_at","order_time"]' AS JSON),'事件/单据发生时间','ACTIVE','system'),
('STD_MOBILE','手机号','STRING',20,'OPTIONAL','^1[3-9]\\d{9}$',CAST('["mobile","phone","cellphone"]' AS JSON),'中国大陆手机号形态（落标仅校验类型族与绑定）','ACTIVE','system');
