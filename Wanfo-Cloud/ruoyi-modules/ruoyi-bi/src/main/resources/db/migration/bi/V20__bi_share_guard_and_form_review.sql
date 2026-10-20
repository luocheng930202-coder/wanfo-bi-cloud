CREATE TABLE bi_share_password_attempt (
 share_id BIGINT NOT NULL,
 viewer_principal BIGINT NOT NULL,
 attempts INT NOT NULL,
 window_start DATETIME NOT NULL,
 PRIMARY KEY(share_id,viewer_principal),
 KEY idx_bi_share_attempt_time(window_start)
);
CREATE TABLE bi_share_access_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 share_id BIGINT NOT NULL,
 viewer_principal BIGINT NOT NULL,
 event VARCHAR(32) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_bi_share_access_time(share_id,create_time),
 KEY idx_bi_share_log_time(create_time)
);
ALTER TABLE bi_data_form_record
 ADD review_by BIGINT NULL,
 ADD review_note VARCHAR(500) NULL,
 ADD review_time DATETIME NULL;
CREATE TABLE bi_data_form_review_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 record_id BIGINT NOT NULL,
 previous_status VARCHAR(16) NOT NULL,
 resolved_status VARCHAR(16) NOT NULL,
 reviewer_principal BIGINT NOT NULL,
 evidence VARCHAR(500) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_bi_form_review_record(record_id)
);
