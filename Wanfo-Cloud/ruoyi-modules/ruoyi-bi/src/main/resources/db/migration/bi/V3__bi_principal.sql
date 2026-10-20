-- Host-neutral principal IDs. Host menus belong in adapter install scripts, not core migrations.
CREATE TABLE bi_principal
(
    id bigint NOT NULL AUTO_INCREMENT,
    source varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Identity source: ruoyi / future standalone',
    principal_type varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'user / role / organization',
    external_id varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    display_name varchar(200) DEFAULT NULL,
    create_time datetime NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_principal_external (source, principal_type, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI identity mapping; independent of sys_* tables';

-- Existing permission_rule.role_id and column_permission.role_id remain legacy host IDs.
-- Resource authorization and legacy rule migration are a separate S2-T09/S8 deliverable;
-- never reinterpret those columns as bi_principal IDs without an explicit migration.
