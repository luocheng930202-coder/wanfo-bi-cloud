CREATE TABLE bi_dataset_policy (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 dataset_id bigint NOT NULL,
 principal_id bigint NOT NULL,
 field_name varchar(200) NOT NULL,
 rule_kind varchar(20) NOT NULL,
 rule_value varchar(1000) NULL,
 create_by varchar(64) NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_policy_dataset(dataset_id),
 CONSTRAINT fk_data_policy_principal FOREIGN KEY (principal_id) REFERENCES bi_principal(id)
);
-- Legacy role_id rules are deliberately not reinterpreted as BI principal IDs.
