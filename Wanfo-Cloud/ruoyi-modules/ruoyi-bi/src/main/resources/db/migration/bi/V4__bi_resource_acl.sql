-- Resource authorization uses BI principal IDs only; no dependency on host sys_* tables.
CREATE TABLE bi_resource (
    resource_type varchar(32) NOT NULL,
    resource_id bigint NOT NULL,
    owner_id bigint DEFAULT NULL COMMENT 'bi_principal.id; NULL means administrator managed legacy resource',
    PRIMARY KEY(resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE bi_resource_grant (
    resource_type varchar(32) NOT NULL,
    resource_id bigint NOT NULL,
    principal_id bigint NOT NULL COMMENT 'bi_principal.id',
    action varchar(32) NOT NULL,
    PRIMARY KEY(resource_type, resource_id, principal_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO bi_resource(resource_type, resource_id, owner_id)
SELECT 'datasource', id, NULL FROM bi_datasource WHERE del_flag='0';
-- Existing datasets/SQL/row rules remain a later delivery; no implicit authorization from legacy create_by.
