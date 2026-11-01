-- Bounded retry lineage for quality runs. Existing runs remain first attempts.
ALTER TABLE dp_quality_run
    ADD COLUMN retry_of_run_id bigint DEFAULT NULL AFTER task_id,
    ADD COLUMN attempt_no tinyint unsigned NOT NULL DEFAULT 1 AFTER retry_of_run_id,
    ADD UNIQUE KEY uk_dp_quality_run_retry_parent (retry_of_run_id),
    ADD KEY idx_dp_quality_run_task_attempt (task_id,attempt_no,create_time),
    ADD CONSTRAINT fk_dp_quality_run_retry_parent FOREIGN KEY (retry_of_run_id)
        REFERENCES dp_quality_run(id) ON DELETE SET NULL;
