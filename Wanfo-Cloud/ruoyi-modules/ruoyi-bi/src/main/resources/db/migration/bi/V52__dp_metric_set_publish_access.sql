-- S2 Wave3: metric sets, publish approval, and access requests (grants still via bi_resource_grant).

CREATE TABLE dp_metric_set (
    id bigint NOT NULL AUTO_INCREMENT,
    set_code varchar(96) NOT NULL,
    name varchar(160) NOT NULL,
    theme varchar(64) DEFAULT NULL,
    description varchar(1000) DEFAULT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by varchar(64) DEFAULT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_metric_set_code (set_code),
    KEY idx_dp_metric_set_status (status, theme)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Metric theme packs';

CREATE TABLE dp_metric_set_member (
    set_id bigint NOT NULL,
    metric_id bigint NOT NULL,
    sort_no int NOT NULL DEFAULT 0,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (set_id, metric_id),
    KEY idx_dp_metric_set_member_metric (metric_id),
    CONSTRAINT fk_dp_metric_set_member_set FOREIGN KEY (set_id) REFERENCES dp_metric_set(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Metric set membership';

CREATE TABLE dp_metric_publish_request (
    id bigint NOT NULL AUTO_INCREMENT,
    metric_id bigint NOT NULL,
    status varchar(16) NOT NULL COMMENT 'PENDING|APPROVED|REJECTED|CANCELLED',
    request_note varchar(1000) DEFAULT NULL,
    decision_note varchar(1000) DEFAULT NULL,
    requested_by varchar(64) NOT NULL,
    requested_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reviewed_by varchar(64) DEFAULT NULL,
    reviewed_at datetime(3) DEFAULT NULL,
    metric_version int NOT NULL COMMENT 'Optimistic lock version at submit',
    PRIMARY KEY (id),
    KEY idx_dp_metric_publish_metric (metric_id, status),
    KEY idx_dp_metric_publish_status (status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Metric publish approval queue';

CREATE TABLE dp_access_request (
    id bigint NOT NULL AUTO_INCREMENT,
    status varchar(16) NOT NULL COMMENT 'PENDING|APPROVED|REJECTED|EXPIRED|CANCELLED',
    purpose varchar(500) NOT NULL,
    resource_type varchar(32) NOT NULL,
    resource_id bigint NOT NULL,
    asset_urn varchar(1000) DEFAULT NULL,
    actions_json json NOT NULL,
    valid_days int NOT NULL DEFAULT 30,
    expires_at datetime(3) DEFAULT NULL,
    decision_note varchar(1000) DEFAULT NULL,
    requested_by varchar(64) NOT NULL,
    requester_user_id bigint NOT NULL,
    requested_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reviewed_by varchar(64) DEFAULT NULL,
    reviewed_at datetime(3) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_dp_access_req_status (status, requested_at),
    KEY idx_dp_access_req_resource (resource_type, resource_id),
    KEY idx_dp_access_req_requester (requester_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Catalog/asset access requests';

ALTER TABLE bi_resource_grant
    ADD COLUMN expires_at datetime(3) DEFAULT NULL COMMENT 'Optional grant expiry for access-request approvals' AFTER action,
    ADD KEY idx_bi_resource_grant_expires (expires_at);
