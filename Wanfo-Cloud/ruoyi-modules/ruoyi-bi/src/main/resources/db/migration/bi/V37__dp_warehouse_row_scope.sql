CREATE TABLE IF NOT EXISTS dp_dataset_scope_binding (
    dataset_id BIGINT NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    tenant_field VARCHAR(128) NOT NULL,
    organization_field VARCHAR(128) NOT NULL,
    update_by VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (dataset_id),
    KEY idx_dp_scope_model (model_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI数据集到数仓行权限模型字段绑定';

CREATE TABLE IF NOT EXISTS dp_warehouse_org_scope_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT,
    model_version VARCHAR(64) NOT NULL,
    principal_id BIGINT NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    warehouse_org_id VARCHAR(128) NOT NULL,
    create_by VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_wh_scope_principal (model_version, principal_id),
    UNIQUE KEY uk_dp_wh_scope_org (model_version, tenant_id, warehouse_org_id),
    KEY idx_dp_wh_scope_lookup (model_version, tenant_id, principal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='若依组织主体到数仓租户和组织节点映射';
