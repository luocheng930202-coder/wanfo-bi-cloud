-- S3 deepen: caller quota split, field-standard enforce flag, task-lane pause/quota.
ALTER TABLE dp_data_api_caller
    ADD COLUMN calls_per_minute int NOT NULL DEFAULT 0 COMMENT '0=inherit API limit; else min(api,caller)' AFTER status,
    ADD COLUMN daily_quota int NOT NULL DEFAULT 0 COMMENT '0=inherit API limit; else min(api,caller)' AFTER calls_per_minute;

ALTER TABLE dp_field_standard
    ADD COLUMN enforce_gate tinyint NOT NULL DEFAULT 0 COMMENT '1=TYPE_MISMATCH on bound columns blocks migration execute' AFTER status;

CREATE TABLE dp_task_pool_lane (
    lane varchar(32) NOT NULL COMMENT 'WAREHOUSE_LOAD/OBJECT_LOAD/PIPELINE/CDC_JOB/QUALITY/...',
    paused tinyint NOT NULL DEFAULT 0,
    max_concurrent int NOT NULL DEFAULT 0 COMMENT '0=unlimited soft cap for running count',
    update_by varchar(64) NOT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (lane)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='S3-01 task pool lane pause and soft concurrency';

INSERT INTO dp_task_pool_lane(lane, paused, max_concurrent, update_by) VALUES
    ('WAREHOUSE_LOAD', 0, 0, 'system'),
    ('OBJECT_LOAD', 0, 0, 'system'),
    ('PIPELINE', 0, 0, 'system'),
    ('CDC_JOB', 0, 0, 'system'),
    ('QUALITY', 0, 0, 'system'),
    ('METADATA_SCAN', 0, 0, 'system');

CREATE TABLE dp_backup_artifact_watch (
    artifact_name varchar(255) NOT NULL,
    artifact_path varchar(1000) NOT NULL,
    size_bytes bigint NOT NULL DEFAULT 0,
    drill_id bigint DEFAULT NULL,
    registered_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (artifact_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='S3-06 auto-register local dump artifacts as METADATA_DUMP drills';
