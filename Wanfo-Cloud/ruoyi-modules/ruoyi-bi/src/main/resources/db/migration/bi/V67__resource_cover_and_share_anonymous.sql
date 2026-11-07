CREATE TABLE bi_resource_cover (
  resource_kind VARCHAR(16) NOT NULL,
  resource_id BIGINT NOT NULL,
  version INT NOT NULL,
  media_type VARCHAR(32) NOT NULL,
  data_url MEDIUMTEXT NOT NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (resource_kind, resource_id)
);

ALTER TABLE bi_share_access_log MODIFY viewer_principal BIGINT NULL;
