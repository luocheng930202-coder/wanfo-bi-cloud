-- V2 bi_export_task remains unused: it stored query_json/dataset_id/file_path and host create_by.
-- Jobs keep only a saved chart or published dashboard reference; files live in content until expire_time.
CREATE TABLE bi_export_job (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name varchar(200) NOT NULL,
 source_type varchar(16) NOT NULL,
 source_id bigint NOT NULL,
 panel_key varchar(64) NOT NULL DEFAULT '',
 format varchar(8) NOT NULL,
 status varchar(16) NOT NULL,
 request_json varchar(8192) NOT NULL,
 message varchar(500) NOT NULL DEFAULT '',
 row_count int NOT NULL DEFAULT 0,
 file_name varchar(220) NOT NULL DEFAULT '',
 content_type varchar(120) NOT NULL DEFAULT '',
 content longblob,
 owner_principal bigint NOT NULL,
 create_by varchar(64) NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 finish_time datetime DEFAULT NULL,
 expire_time datetime NOT NULL,
 KEY idx_export_owner(owner_principal,id),
 KEY idx_export_status(status,expire_time)
);
