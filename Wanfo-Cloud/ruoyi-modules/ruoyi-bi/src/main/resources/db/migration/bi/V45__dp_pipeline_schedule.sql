ALTER TABLE dp_pipeline_run
    ADD COLUMN request_snapshot_json json DEFAULT NULL COMMENT '生产运行请求快照，供重试/调度复用' AFTER timeout_seconds;

CREATE TABLE dp_pipeline_schedule (
    id bigint NOT NULL AUTO_INCREMENT,
    pipeline_id bigint NOT NULL,
    revision_id bigint DEFAULT NULL COMMENT '为空则使用当前已发布版本',
    name varchar(160) NOT NULL,
    enabled tinyint NOT NULL DEFAULT 1,
    schedule_json json NOT NULL,
    target_datasource_id bigint NOT NULL,
    max_rows int NOT NULL DEFAULT 100000,
    timeout_seconds int NOT NULL DEFAULT 300,
    owner_user_id bigint NOT NULL,
    actor_json json NOT NULL,
    failure_count int NOT NULL DEFAULT 0,
    last_status varchar(16) DEFAULT NULL,
    last_message varchar(500) DEFAULT NULL,
    last_run_id bigint DEFAULT NULL,
    last_run_at datetime(3) DEFAULT NULL,
    next_run_utc datetime(3) NOT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_pipeline_schedule_pipeline (pipeline_id),
    KEY idx_dp_pipeline_schedule_due (enabled, next_run_utc),
    CONSTRAINT fk_dp_pipeline_schedule_pipeline FOREIGN KEY (pipeline_id) REFERENCES dp_pipeline(id) ON DELETE CASCADE
);
