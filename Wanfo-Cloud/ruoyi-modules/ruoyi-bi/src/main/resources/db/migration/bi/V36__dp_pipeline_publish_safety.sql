ALTER TABLE dp_pipeline
    ADD COLUMN current_revision_id bigint DEFAULT NULL;

ALTER TABLE dp_pipeline_revision
    ADD COLUMN lock_version int NOT NULL DEFAULT 0,
    ADD COLUMN rollback_from_revision_id bigint DEFAULT NULL;

UPDATE dp_pipeline p
SET current_revision_id = (
    SELECT r.id FROM dp_pipeline_revision r
    WHERE r.pipeline_id = p.id AND r.status = 'PUBLISHED'
    ORDER BY r.revision_no DESC LIMIT 1
)
WHERE EXISTS (SELECT 1 FROM dp_pipeline_revision r WHERE r.pipeline_id = p.id AND r.status = 'PUBLISHED');

CREATE TABLE dp_pipeline_publish_history (
    id bigint NOT NULL AUTO_INCREMENT,
    pipeline_id bigint NOT NULL,
    previous_revision_id bigint DEFAULT NULL,
    revision_id bigint NOT NULL,
    action_type varchar(16) NOT NULL,
    action_by varchar(64) NOT NULL,
    reason varchar(500) DEFAULT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dp_pipeline_publish_history (pipeline_id,create_time),
    KEY idx_dp_pipeline_publish_revision (revision_id,create_time),
    CONSTRAINT fk_dp_pipeline_publish_history_pipeline FOREIGN KEY (pipeline_id)
        REFERENCES dp_pipeline(id) ON DELETE CASCADE
);
