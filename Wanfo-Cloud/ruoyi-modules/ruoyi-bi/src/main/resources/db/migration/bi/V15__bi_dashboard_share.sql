CREATE TABLE bi_dashboard_share (
    id bigint NOT NULL AUTO_INCREMENT,
    dashboard_id bigint NOT NULL,
    token varchar(64) NOT NULL,
    data_scope varchar(16) NOT NULL DEFAULT 'viewer' COMMENT 'viewer=访问者数据权限, publisher=创建者数据权限',
    password_hash varchar(128) NULL,
    expire_time datetime NULL,
    publisher_actor_json mediumtext NULL COMMENT 'publisher 范围查询时使用的身份快照',
    status char(1) NOT NULL DEFAULT '0' COMMENT '0 启用 1 停用',
    create_by varchar(64) DEFAULT NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_dashboard_share_token (token),
    KEY idx_bi_dashboard_share_dashboard (dashboard_id)
);
