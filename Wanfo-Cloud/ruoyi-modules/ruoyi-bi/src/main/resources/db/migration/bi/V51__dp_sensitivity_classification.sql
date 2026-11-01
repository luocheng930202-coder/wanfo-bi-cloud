-- S2 Wave2: sensitivity levels + per-asset classification with inheritance provenance.
CREATE TABLE dp_sensitivity_level (
    code varchar(32) NOT NULL,
    name varchar(64) NOT NULL,
    rank_no int NOT NULL COMMENT 'Higher wins on lineage inheritance',
    default_enforcement varchar(16) NOT NULL COMMENT 'MASK|DENY|NONE',
    description varchar(200) DEFAULT NULL,
    PRIMARY KEY (code),
    UNIQUE KEY uk_dp_sensitivity_rank (rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Sensitivity classification levels';

INSERT INTO dp_sensitivity_level(code,name,rank_no,default_enforcement,description) VALUES
('PUBLIC','公开',10,'NONE','可公开查询'),
('INTERNAL','内部',20,'NONE','仅授权主体'),
('CONFIDENTIAL','机密',30,'MASK','默认脱敏展示'),
('SECRET','秘密',40,'DENY','默认禁止投影');

CREATE TABLE dp_asset_classification (
    asset_id bigint NOT NULL,
    level_code varchar(32) NOT NULL,
    source varchar(16) NOT NULL COMMENT 'MANUAL|INHERITED',
    inherited_from_asset_id bigint DEFAULT NULL,
    note varchar(500) DEFAULT NULL,
    update_by varchar(64) NOT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (asset_id),
    KEY idx_dp_asset_class_level (level_code),
    KEY idx_dp_asset_class_source (source, update_time),
    CONSTRAINT fk_dp_asset_class_asset FOREIGN KEY (asset_id) REFERENCES dp_asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_asset_class_level FOREIGN KEY (level_code) REFERENCES dp_sensitivity_level(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Effective asset sensitivity with provenance';

-- Mirror legacy column for catalog display when row exists.
UPDATE dp_asset a
INNER JOIN dp_asset_classification c ON c.asset_id = a.id
SET a.classification = c.level_code
WHERE a.classification IS NULL OR a.classification = '';
