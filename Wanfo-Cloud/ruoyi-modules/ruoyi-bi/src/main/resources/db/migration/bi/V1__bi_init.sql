-- ============================================================================
-- BI 报表平台 元数据表 (第一批 8 张核心表)
-- 设计参照：DataEase v3 元数据库实测 111 张表中抽取的 BI 核心部分
-- 库      : ry_vue (与若依业务表同库)
-- 引擎    : MySQL 8.x / 5.7 兼容语法（不使用 json 列类型，保证 5.7 可用）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. bi_datasource 数据源定义
--    对应 DataEase: core_datasource
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_datasource
(
    id             bigint         NOT NULL AUTO_INCREMENT COMMENT '数据源ID',
    name           varchar(100)   NOT NULL                COMMENT '数据源名称',
    type           varchar(32)    NOT NULL                COMMENT '类型 mysql/doris/postgresql/oracle/sqlserver/clickhouse/...',
    schema_name    varchar(64)    NOT NULL                COMMENT 'Calcite schema 别名，SQL 中以 schema.table 引用',
    jdbc_url       varchar(500)   NOT NULL                COMMENT 'JDBC 连接串',
    driver_class   varchar(200)            DEFAULT NULL   COMMENT '驱动类名，为空则按 type 取默认',
    username       varchar(100)            DEFAULT NULL   COMMENT '连接账号',
    password       varchar(500)            DEFAULT NULL   COMMENT '连接密码，加密存储，接口输出必须脱敏',
    ssh_host       varchar(100)            DEFAULT NULL   COMMENT 'SSH 隧道-主机',
    ssh_port       int                     DEFAULT NULL   COMMENT 'SSH 隧道-端口',
    ssh_user       varchar(100)            DEFAULT NULL   COMMENT 'SSH 隧道-用户名',
    ssh_password   varchar(500)            DEFAULT NULL   COMMENT 'SSH 隧道-密码，加密',
    ssh_private_key text                            DEFAULT NULL COMMENT 'SSH 隧道-私钥，加密',
    pool_max_total int            NOT NULL DEFAULT 8    COMMENT '连接池最大连接数',
    pool_min_idle  int            NOT NULL DEFAULT 1    COMMENT '连接池最小空闲',
    query_timeout  int            NOT NULL DEFAULT 60   COMMENT '查询超时(秒)',
    status         char(1)        NOT NULL DEFAULT '0'  COMMENT '状态 0正常 1停用',
    remark         varchar(500)            DEFAULT NULL,
    create_by      varchar(64)             DEFAULT '',
    create_time    datetime,
    update_by      varchar(64)             DEFAULT '',
    update_time    datetime,
    del_flag       char(1)        NOT NULL DEFAULT '0' COMMENT '删除标志 0存在 2删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_datasource_schema (schema_name),
    KEY idx_bi_datasource_type (type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 数据源定义';


-- ---------------------------------------------------------------------------
-- 2. bi_dataset_group 数据集目录树
--    对应 DataEase: core_dataset_group
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_dataset_group
(
    id          bigint       NOT NULL AUTO_INCREMENT COMMENT '目录ID',
    parent_id   bigint       NOT NULL DEFAULT 0       COMMENT '父目录ID',
    ancestors   varchar(500)         DEFAULT ''      COMMENT '祖级列表，逗号分隔',
    name        varchar(100) NOT NULL                  COMMENT '目录名称',
    order_num   int          NOT NULL DEFAULT 0       COMMENT '显示顺序',
    status      char(1)      NOT NULL DEFAULT '0'     COMMENT '状态 0正常 1停用',
    create_by   varchar(64)           DEFAULT '',
    create_time datetime,
    update_by   varchar(64)           DEFAULT '',
    update_time datetime,
    del_flag    char(1)      NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_dataset_group_parent (parent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 数据集目录';


-- ---------------------------------------------------------------------------
-- 3. bi_dataset_table 数据集定义
--    对应 DataEase: core_dataset_table
--    type: table(选表) / sql(手写SQL) / api / excel
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_dataset_table
(
    id            bigint        NOT NULL AUTO_INCREMENT COMMENT '数据集ID',
    group_id      bigint                 DEFAULT NULL   COMMENT '所属目录',
    name          varchar(200)  NOT NULL                COMMENT '数据集名称',
    type          varchar(16)   NOT NULL DEFAULT 'table' COMMENT '类型 table/sql/api/excel',
    datasource_id bigint                 DEFAULT NULL   COMMENT '单源数据集的数据源ID；跨源可空',
    source_table  varchar(300)           DEFAULT NULL   COMMENT 'type=table 时的物理表名，可含库名',
    sql_text      longtext               DEFAULT NULL   COMMENT 'type=sql 时的原始SQL(用户编写)',
    union_sql     longtext               DEFAULT NULL   COMMENT '系统生成的最终执行SQL(含跨源union)',
    cache_enabled char(1)       NOT NULL DEFAULT '0'   COMMENT '是否启用结果缓存',
    cache_ttl     int           NOT NULL DEFAULT 300   COMMENT '缓存有效期(秒)',
    remark        varchar(500)          DEFAULT NULL,
    create_by     varchar(64)           DEFAULT '',
    create_time   datetime,
    update_by     varchar(64)           DEFAULT '',
    update_time   datetime,
    del_flag      char(1)       NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_dataset_table_group (group_id),
    KEY idx_bi_dataset_table_ds (datasource_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 数据集定义';


-- ---------------------------------------------------------------------------
-- 4. bi_dataset_field 数据集字段
--    对应 DataEase: core_dataset_table_field
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_dataset_field
(
    id           bigint        NOT NULL AUTO_INCREMENT COMMENT '字段ID',
    dataset_id   bigint        NOT NULL                COMMENT '所属数据集',
    origin_name  varchar(300)  NOT NULL                COMMENT '原始字段名/列名',
    origin_type  varchar(32)            DEFAULT NULL   COMMENT '原始数据类型',
    show_name    varchar(300)          DEFAULT NULL   COMMENT '前端显示名',
    dim_flag     char(1)       NOT NULL DEFAULT '1'   COMMENT '1维度 0度量',
    ext_flag     char(1)       NOT NULL DEFAULT '0'   COMMENT '1计算字段 0物理字段',
    ext_formula  varchar(1000)         DEFAULT NULL   COMMENT '计算表达式，如 amount * price',
    date_format  varchar(32)            DEFAULT NULL   COMMENT '日期格式化',
    group_name   varchar(100)          DEFAULT NULL   COMMENT '业务分组(维度/度量等)',
    order_num    int           NOT NULL DEFAULT 0,
    status       char(1)       NOT NULL DEFAULT '0',
    create_time  datetime,
    update_time  datetime,
    del_flag     char(1)       NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_dataset_field_ds (dataset_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 数据集字段';


-- ---------------------------------------------------------------------------
-- 5. bi_dashboard 仪表板/大屏
--    对应 DataEase: data_visualization_info
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_dashboard
(
    id            bigint        NOT NULL AUTO_INCREMENT COMMENT '大屏ID',
    name          varchar(200)  NOT NULL                COMMENT '大屏名称',
    type          varchar(16)   NOT NULL DEFAULT 'dashboard' COMMENT 'dashboard 仪表板 / screen 大屏',
    width         int           NOT NULL DEFAULT 1920  COMMENT '画布宽',
    height        int           NOT NULL DEFAULT 1080  COMMENT '画布高',
    grid_cols     int           NOT NULL DEFAULT 24    COMMENT '栅格列数',
    theme         varchar(64)            DEFAULT NULL   COMMENT '主题',
    background    varchar(500)          DEFAULT NULL   COMMENT '背景配置JSON',
    publish_id    bigint                 DEFAULT NULL   COMMENT '当前发布的快照ID',
    status        char(1)       NOT NULL DEFAULT '0'   COMMENT '0草稿可用 1停用',
    remark        varchar(500)          DEFAULT NULL,
    create_by     varchar(64)           DEFAULT '',
    create_time   datetime,
    update_by     varchar(64)           DEFAULT '',
    update_time   datetime,
    del_flag      char(1)       NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_dashboard_type (type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 仪表板/大屏';


-- ---------------------------------------------------------------------------
-- 6. bi_dashboard_component 大屏组件树
--    对应 DataEase: 大屏组件表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_dashboard_component
(
    id             bigint        NOT NULL AUTO_INCREMENT COMMENT '组件ID',
    dashboard_id   bigint        NOT NULL                COMMENT '所属大屏',
    parent_id      bigint        NOT NULL DEFAULT 0       COMMENT '父组件ID(容器嵌套)',
    ancestors      varchar(500)          DEFAULT ''      COMMENT '祖级列表',
    component_type varchar(32)   NOT NULL                COMMENT 'chart/filter/text/image/video/tab/container/button',
    name           varchar(200)          DEFAULT NULL,
    x              int           NOT NULL DEFAULT 0    COMMENT '栅格X',
    y              int           NOT NULL DEFAULT 0    COMMENT '栅格Y',
    w              int           NOT NULL DEFAULT 6    COMMENT '栅格宽',
    h              int           NOT NULL DEFAULT 4    COMMENT '栅格高',
    z_index        int           NOT NULL DEFAULT 0    COMMENT '层级',
    config_json    longtext               DEFAULT NULL   COMMENT '组件配置JSON(图表/过滤器各自的配置)',
    style_json     longtext               DEFAULT NULL   COMMENT '样式JSON',
    status         char(1)       NOT NULL DEFAULT '0',
    order_num      int           NOT NULL DEFAULT 0,
    create_time    datetime,
    update_time    datetime,
    del_flag       char(1)       NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_comp_dashboard (dashboard_id),
    KEY idx_bi_comp_parent (parent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 大屏组件';


-- ---------------------------------------------------------------------------
-- 7. bi_chart_view 图表定义
--    对应 DataEase: core_chart_view
--    设计要点：列是运行期决定的(JSON)，只有需要检索/排序的列才建物理列
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_chart_view
(
    id             bigint        NOT NULL AUTO_INCREMENT COMMENT '图表ID',
    name           varchar(200)  NOT NULL                COMMENT '图表名称',
    dashboard_id   bigint                 DEFAULT NULL   COMMENT '所属大屏，可空(独立图表)',
    dataset_id     bigint        NOT NULL                COMMENT '所属数据集',
    type           varchar(32)   NOT NULL                COMMENT 'bar/line/pie/table/pivot/heat-map/scatter/radar/box-plot/map/indicator...',
    render         varchar(16)   NOT NULL DEFAULT 'echarts' COMMENT '渲染引擎 echarts/antv',
    x_axis         longtext               DEFAULT NULL   COMMENT '维度轴配置JSON',
    y_axis         longtext               DEFAULT NULL   COMMENT '度量轴配置JSON',
    x_axis_ext     longtext               DEFAULT NULL   COMMENT '次要维度(堆叠/拆线)',
    y_axis_ext     longtext               DEFAULT NULL   COMMENT '次要度量(双轴)',
    drill_fields   longtext               DEFAULT NULL   COMMENT '下钻路径JSON',
    ext_stack      longtext               DEFAULT NULL   COMMENT '堆叠扩展JSON',
    ext_bubble     longtext               DEFAULT NULL   COMMENT '气泡大小/颜色映射JSON',
    custom_filter  longtext               DEFAULT NULL   COMMENT '过滤器树JSON(★ 行级权限在此层叠加)',
    style_json     longtext               DEFAULT NULL   COMMENT '样式JSON',
    ui_config      longtext               DEFAULT NULL   COMMENT '交互配置JSON',
    chart_ext      longtext               DEFAULT NULL   COMMENT '图表私有扩展JSON',
    create_by      varchar(64)           DEFAULT '',
    create_time    datetime,
    update_by      varchar(64)           DEFAULT '',
    update_time    datetime,
    del_flag       char(1)       NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_chart_dashboard (dashboard_id),
    KEY idx_bi_chart_dataset (dataset_id),
    KEY idx_bi_chart_type (type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 图表定义';


-- ---------------------------------------------------------------------------
-- 8. bi_snapshot 发布快照
--    对应 DataEase: snapshot_* 共 11 张，此处合并为一张通用快照表
--    草稿/发布双版本分离，避免已发布内容被编辑影响
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_snapshot
(
    id           bigint        NOT NULL AUTO_INCREMENT COMMENT '快照ID',
    biz_type     varchar(32)   NOT NULL                COMMENT 'dashboard/chart',
    biz_id       bigint        NOT NULL                COMMENT '业务对象ID',
    code         varchar(64)   NOT NULL                COMMENT '快照码(draft草稿/publish发布)',
    version      int           NOT NULL DEFAULT 1,
    content      longtext      NOT NULL                COMMENT '完整内容JSON快照',
    remark       varchar(500)          DEFAULT NULL,
    create_by    varchar(64)           DEFAULT '',
    create_time  datetime,
    del_flag     char(1)       NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_snapshot_biz (biz_type, biz_id, code),
    KEY idx_bi_snapshot_biz (biz_type, biz_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 发布快照';

