ALTER TABLE bi_data_form_record
 ADD COLUMN owner_principal BIGINT NULL,
 ADD COLUMN request_key VARCHAR(80) NULL,
 ADD COLUMN payload_hash VARCHAR(64) NULL,
 ADD COLUMN submission_status VARCHAR(16) NOT NULL DEFAULT 'legacy',
 ADD UNIQUE KEY uk_bi_form_submission(form_id,owner_principal,request_key);
