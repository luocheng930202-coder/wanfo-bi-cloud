CREATE TABLE bi_resource_history (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 resource_type VARCHAR(20) NOT NULL,
 resource_id BIGINT NOT NULL,
 version INT NOT NULL,
 operation VARCHAR(24) NOT NULL,
 definition_json LONGTEXT NOT NULL,
 actor VARCHAR(100) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_resource_version(resource_type,resource_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable chart and dashboard draft history';
