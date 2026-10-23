ALTER TABLE bi_datasource ADD COLUMN source_schema VARCHAR(128) NULL COMMENT 'Physical database schema scope';
CREATE TABLE bi_source_snapshot (
 datasource_id BIGINT NOT NULL PRIMARY KEY,
 kind VARCHAR(16) NOT NULL,
 config_cipher LONGTEXT NULL,
 columns_json LONGTEXT NOT NULL,
 rows_json LONGTEXT NOT NULL,
 row_count INT NOT NULL,
 version BIGINT NOT NULL DEFAULT 1,
 file_name VARCHAR(200) NULL,
 refreshed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bounded API and file source snapshots shared by BI nodes';
