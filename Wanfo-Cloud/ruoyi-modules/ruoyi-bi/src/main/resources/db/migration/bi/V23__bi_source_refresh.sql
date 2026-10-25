CREATE TABLE bi_source_refresh (
 datasource_id BIGINT NOT NULL PRIMARY KEY,
 interval_minutes INT NOT NULL DEFAULT 0,
 next_run DATETIME NULL,
 lease_until DATETIME NULL,
 lease_token VARCHAR(36) NULL,
 last_status VARCHAR(80) NULL,
 updated_by VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Opt-in bounded API refresh scheduling';
