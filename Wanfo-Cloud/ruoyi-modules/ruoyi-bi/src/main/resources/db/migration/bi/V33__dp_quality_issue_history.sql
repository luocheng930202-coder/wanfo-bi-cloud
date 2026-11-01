CREATE TABLE dp_quality_issue_history (
    id bigint NOT NULL AUTO_INCREMENT,
    issue_id bigint NOT NULL,
    actor varchar(64) NOT NULL,
    action varchar(16) NOT NULL,
    note varchar(1000) DEFAULT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dp_quality_issue_history (issue_id,create_time),
    CONSTRAINT fk_dp_quality_issue_history_issue FOREIGN KEY (issue_id)
        REFERENCES dp_quality_issue(id) ON DELETE CASCADE
);
