-- Temporary image blobs for designer/template flows; not listed in the persistent asset library.
CREATE TABLE bi_image_staging (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name varchar(200) NOT NULL,
 data_url mediumtext NOT NULL,
 byte_size int NOT NULL,
 owner_principal bigint NOT NULL,
 create_by varchar(64) NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 expire_time datetime NOT NULL,
 KEY idx_staging_owner(owner_principal,expire_time),
 KEY idx_staging_expire(expire_time)
);
