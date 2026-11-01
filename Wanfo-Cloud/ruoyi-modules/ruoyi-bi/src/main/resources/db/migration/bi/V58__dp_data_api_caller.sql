-- S3-03: organization-level Data API caller registry (bindable to keys).
CREATE TABLE dp_data_api_caller (
    id bigint NOT NULL AUTO_INCREMENT,
    org_code varchar(64) NOT NULL COMMENT 'Stable consumer org code',
    org_name varchar(200) NOT NULL,
    contact_name varchar(64) DEFAULT NULL,
    contact_email varchar(128) DEFAULT NULL,
    notes varchar(1000) DEFAULT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE,DISABLED',
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by varchar(64) NOT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_data_api_caller_code (org_code),
    KEY idx_dp_data_api_caller_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Governed Data API consumer organizations';

ALTER TABLE dp_data_api_key
    ADD COLUMN caller_id bigint DEFAULT NULL COMMENT 'dp_data_api_caller.id' AFTER key_prefix,
    ADD KEY idx_dp_data_api_key_caller (caller_id);
