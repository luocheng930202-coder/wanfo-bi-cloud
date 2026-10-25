CREATE TABLE bi_dataset_quality_rule (
 dataset_id bigint NOT NULL PRIMARY KEY,
 rules_json longtext NOT NULL,
 update_by varchar(64) NOT NULL,
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CONSTRAINT fk_bi_dataset_quality_rule FOREIGN KEY(dataset_id) REFERENCES bi_dataset_table(id) ON DELETE CASCADE
);
