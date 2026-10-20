CREATE TABLE bi_screen_template (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name varchar(200) NOT NULL,
 category varchar(64) NOT NULL,
 description varchar(500) NOT NULL DEFAULT '',
 content_json mediumtext NOT NULL,
 create_by varchar(64) NOT NULL,
 update_by varchar(64) NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 del_flag char(1) NOT NULL DEFAULT '0',
 KEY idx_template_active(del_flag,id)
);
