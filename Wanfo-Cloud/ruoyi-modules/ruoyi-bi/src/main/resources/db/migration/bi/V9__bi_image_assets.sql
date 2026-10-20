CREATE TABLE bi_image_asset (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name varchar(200) NOT NULL,
 data_url mediumtext NOT NULL,
 byte_size int NOT NULL,
 create_by varchar(64) NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 del_flag char(1) NOT NULL DEFAULT '0',
 KEY idx_asset_active(del_flag,id)
);
