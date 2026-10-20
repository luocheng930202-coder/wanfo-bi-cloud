-- 数据指标：独立资源，编码手填唯一，发布后冻结定义，引用中禁止改和下线。
SELECT 1;

CREATE TABLE IF NOT EXISTS bi_metric
(
    id                 bigint        NOT NULL AUTO_INCREMENT COMMENT '指标ID',
    code               varchar(64)   NOT NULL                COMMENT '业务编码，创建后不可改，全局唯一',
    name               varchar(200)  NOT NULL                COMMENT '指标名称',
    kind               varchar(16)   NOT NULL                COMMENT '类型 scalar标量 / table表格',
    dataset_id         bigint        NOT NULL                COMMENT '绑定的表数据集ID',
    definition_json    longtext      NOT NULL                COMMENT '草稿定义 JSON（QueryModel）',
    published_json     longtext               DEFAULT NULL   COMMENT '已发布快照 JSON，未发布为空',
    version            int           NOT NULL DEFAULT 0      COMMENT '乐观锁版本',
    published_version  int                    DEFAULT NULL   COMMENT '最近一次发布版本号',
    publish_status     varchar(16)   NOT NULL DEFAULT 'draft' COMMENT 'draft草稿 / published已发布',
    create_by          varchar(64)            DEFAULT ''     COMMENT '创建人',
    create_time        datetime                              COMMENT '创建时间',
    update_by          varchar(64)            DEFAULT ''     COMMENT '更新人',
    update_time        datetime                              COMMENT '更新时间',
    del_flag           char(1)       NOT NULL DEFAULT '0'    COMMENT '删除标志 0存在 2删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_metric_code (code),
    KEY idx_bi_metric_dataset (dataset_id),
    KEY idx_bi_metric_status (publish_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 数据指标定义';

CREATE TABLE IF NOT EXISTS bi_metric_history
(
    id                 bigint        NOT NULL AUTO_INCREMENT COMMENT '历史ID',
    metric_id          bigint        NOT NULL                COMMENT '指标ID',
    published_version  int           NOT NULL                COMMENT '发布版本号',
    code               varchar(64)   NOT NULL                COMMENT '发布时的业务编码',
    name               varchar(200)  NOT NULL                COMMENT '发布时的名称',
    kind               varchar(16)   NOT NULL                COMMENT '发布时的类型',
    dataset_id         bigint        NOT NULL                COMMENT '发布时的数据集ID',
    definition_json    longtext      NOT NULL                COMMENT '该版本发布快照 JSON',
    publish_by         varchar(64)            DEFAULT ''     COMMENT '发布人',
    publish_time       datetime      NOT NULL                COMMENT '发布时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_metric_history_version (metric_id, published_version),
    KEY idx_bi_metric_history_metric (metric_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 数据指标发布历史';

CREATE TABLE IF NOT EXISTS bi_metric_usage
(
    id          bigint       NOT NULL AUTO_INCREMENT COMMENT '引用ID',
    metric_id   bigint       NOT NULL                COMMENT '被引用的指标ID',
    ref_type    varchar(32)  NOT NULL                COMMENT '引用方类型，如 dashboard/screen/api',
    ref_id      varchar(64)  NOT NULL                COMMENT '引用方业务ID',
    remark      varchar(200)          DEFAULT NULL   COMMENT '引用说明',
    create_by   varchar(64)           DEFAULT ''     COMMENT '登记人',
    create_time datetime                             COMMENT '登记时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_metric_usage (metric_id, ref_type, ref_id),
    KEY idx_bi_metric_usage_metric (metric_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 数据指标引用登记';
