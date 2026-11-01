CREATE TABLE dp_pipeline (
    id bigint NOT NULL AUTO_INCREMENT,
    pipeline_code varchar(96) NOT NULL,
    name varchar(160) NOT NULL,
    description varchar(1000) DEFAULT NULL,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by varchar(64) DEFAULT NULL,
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_pipeline_code (pipeline_code),
    KEY idx_dp_pipeline_status (status,update_time)
);

CREATE TABLE dp_pipeline_revision (
    id bigint NOT NULL AUTO_INCREMENT,
    pipeline_id bigint NOT NULL,
    revision_no int NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    definition_hash char(64) DEFAULT NULL,
    input_schema_json json DEFAULT NULL,
    output_schema_json json DEFAULT NULL,
    parameters_json json DEFAULT NULL,
    create_by varchar(64) NOT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    publish_by varchar(64) DEFAULT NULL,
    publish_time datetime(3) DEFAULT NULL,
    remark varchar(500) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_pipeline_revision (pipeline_id,revision_no),
    KEY idx_dp_pipeline_revision_status (pipeline_id,status),
    CONSTRAINT fk_dp_pipeline_revision_pipeline FOREIGN KEY (pipeline_id)
        REFERENCES dp_pipeline(id) ON DELETE CASCADE
);

CREATE TABLE dp_pipeline_node (
    id bigint NOT NULL AUTO_INCREMENT,
    revision_id bigint NOT NULL,
    node_key varchar(96) NOT NULL,
    node_type varchar(40) NOT NULL,
    name varchar(160) NOT NULL,
    config_json json NOT NULL,
    input_schema_json json DEFAULT NULL,
    output_schema_json json DEFAULT NULL,
    position_x int NOT NULL DEFAULT 0,
    position_y int NOT NULL DEFAULT 0,
    order_num int NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_pipeline_node_key (revision_id,node_key),
    KEY idx_dp_pipeline_node_type (revision_id,node_type,order_num),
    CONSTRAINT fk_dp_pipeline_node_revision FOREIGN KEY (revision_id)
        REFERENCES dp_pipeline_revision(id) ON DELETE CASCADE
);

CREATE TABLE dp_pipeline_edge (
    id bigint NOT NULL AUTO_INCREMENT,
    revision_id bigint NOT NULL,
    source_node_key varchar(96) NOT NULL,
    source_port varchar(96) NOT NULL DEFAULT 'out',
    target_node_key varchar(96) NOT NULL,
    target_port varchar(96) NOT NULL DEFAULT 'in',
    edge_type varchar(16) NOT NULL DEFAULT 'DATA',
    order_num int NOT NULL DEFAULT 0,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_pipeline_edge (revision_id,source_node_key,source_port,target_node_key,target_port),
    KEY idx_dp_pipeline_edge_target (revision_id,target_node_key,target_port),
    CONSTRAINT fk_dp_pipeline_edge_revision FOREIGN KEY (revision_id)
        REFERENCES dp_pipeline_revision(id) ON DELETE CASCADE
);
