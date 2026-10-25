ALTER TABLE bi_report_subscription
 ADD COLUMN channels_json varchar(120) NOT NULL DEFAULT '["email"]' AFTER recipients_json,
 ADD COLUMN schedule_json varchar(800) DEFAULT NULL AFTER interval_minutes,
 ADD COLUMN next_run_utc datetime DEFAULT NULL AFTER next_run,
 ADD COLUMN template_title varchar(200) NOT NULL DEFAULT '报表订阅：{{reportName}}' AFTER name,
 ADD COLUMN template_body varchar(1000) NOT NULL DEFAULT '报表 {{reportName}} 已生成，数据行数：{{rowCount}}。' AFTER template_title;

UPDATE bi_report_subscription SET schedule_json=JSON_OBJECT('kind','interval','intervalMinutes',interval_minutes,'localTime','','weekdays',JSON_ARRAY(),'timezone','UTC');

ALTER TABLE bi_report_subscription_audit
 DROP FOREIGN KEY fk_bi_report_subscription_audit,
 ADD COLUMN subscription_name varchar(200) NOT NULL DEFAULT '' AFTER subscription_id,
 ADD COLUMN source_type varchar(16) NOT NULL DEFAULT '' AFTER subscription_name,
 ADD COLUMN source_id bigint DEFAULT NULL AFTER source_type;

CREATE TABLE bi_report_notification (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id bigint NOT NULL,
 subscription_id bigint DEFAULT NULL,
 title varchar(200) NOT NULL,
 body varchar(1000) NOT NULL,
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 read_at datetime DEFAULT NULL,
 KEY idx_bi_report_notice_user(user_id,read_at,created_at),
 KEY idx_bi_report_notice_subscription(subscription_id,id)
);
