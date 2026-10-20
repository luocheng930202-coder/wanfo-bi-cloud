-- ============================================================================
-- BI 报表平台 元数据表 (第二批：权限 / 联动 / 导出 / 分享 / 字典 / 日志)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 9. bi_permission_rule 行级权限规则
--    ★ BI 数据权限隔离的核心表。规则由管理员配置，运行时编译成 WHERE 条件注入查询 SQL。
--    参照 DataEase: per_dataset_row_permissions_tree
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_permission_rule
(
    id            bigint        NOT NULL AUTO_INCREMENT COMMENT '规则ID',
    name          varchar(200)  NOT NULL                COMMENT '规则名称，便于识别',
    role_id       bigint        NOT NULL                COMMENT '适用角色(sys_role.role_id)',
    dataset_id    bigint        NOT NULL                COMMENT '作用数据集',
    rule_type     varchar(16)   NOT NULL DEFAULT 'custom' COMMENT 'custom 固定规则 / attr 属性映射',
    filter_tree   longtext               DEFAULT NULL   COMMENT 'rule_type=custom 时：过滤树JSON(AND/OR嵌套)',
    attr_mapping  longtext               DEFAULT NULL   COMMENT 'rule_type=attr 时：字段映射JSON',
    attr_expression longtext             DEFAULT NULL   COMMENT 'attr_mapping 中可用的表达式(如 user.dept)',
    priority      int           NOT NULL DEFAULT 0       COMMENT '优先级，多角色命中时的合并顺序',
    enabled       char(1)       NOT NULL DEFAULT '1',
    remark        varchar(500)          DEFAULT NULL,
    create_by     varchar(64)           DEFAULT '',
    create_time   datetime,
    update_by     varchar(64)           DEFAULT '',
    update_time   datetime,
    del_flag      char(1)       NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_perm_role (role_id),
    KEY idx_bi_perm_dataset (dataset_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 行级权限规则';


-- ---------------------------------------------------------------------------
-- 10. bi_column_permission 列级权限与脱敏
--     ★ 无权限字段在服务端就不返回，而不是前端隐藏。
--     参照 DataEase: per_dataset_column_permissions
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_column_permission
(
    id             bigint       NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    role_id        bigint       NOT NULL                COMMENT '适用角色',
    dataset_id     bigint       NOT NULL                COMMENT '作用数据集',
    field_name     varchar(300) NOT NULL                COMMENT '字段名',
    field_show_name varchar(300)        DEFAULT NULL   COMMENT '脱敏后的显示名(可覆盖)',
    visible        char(1)      NOT NULL DEFAULT '1'   COMMENT '1可见 0不可见(服务端直接剔除)',
    desensitize    varchar(16)            DEFAULT NULL  COMMENT '脱敏类型 mask/hash/all/keep',
    mask_prefix    int                   DEFAULT NULL   COMMENT '脱敏保留前缀长度',
    mask_suffix    int                   DEFAULT NULL   COMMENT '脱敏保留后缀长度',
    enabled        char(1)      NOT NULL DEFAULT '1',
    create_by      varchar(64)          DEFAULT '',
    create_time    datetime,
    update_time    datetime,
    del_flag       char(1)      NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_colperm (role_id, dataset_id, field_name),
    KEY idx_bi_colperm_dataset (dataset_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 列级权限与脱敏';


-- ---------------------------------------------------------------------------
-- 11. bi_dashboard_linkage 图表联动
--     一个图表的选择驱动其他图表刷新。参照 visualization_linkage(+field)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_dashboard_linkage
(
    id              bigint       NOT NULL AUTO_INCREMENT COMMENT '联动ID',
    dashboard_id    bigint       NOT NULL                COMMENT '所属大屏',
    name            varchar(200)          DEFAULT NULL,
    source_component_id bigint  NOT NULL                COMMENT '触发方组件ID',
    target_component_id bigint  NOT NULL                COMMENT '接收方组件ID',
    filter_tree     longtext              DEFAULT NULL   COMMENT '字段映射JSON：源字段->目标字段',
    enabled         char(1)      NOT NULL DEFAULT '1',
    create_time     datetime,
    del_flag        char(1)      NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_linkage_dashboard (dashboard_id),
    KEY idx_bi_linkage_source (source_component_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 图表联动';


-- ---------------------------------------------------------------------------
-- 12. bi_dashboard_jump 图表跳转
--     点击图表跳到另一大屏或明细页，可带参数。参照 visualization_link_jump
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_dashboard_jump
(
    id                bigint       NOT NULL AUTO_INCREMENT COMMENT '跳转ID',
    dashboard_id      bigint       NOT NULL                COMMENT '所属大屏',
    source_component_id bigint     NOT NULL                COMMENT '触发组件ID',
    jump_type         varchar(16)  NOT NULL DEFAULT 'dashboard' COMMENT 'dashboard 跳大屏 / link 外链 / detail 明细',
    target_id         bigint                DEFAULT NULL   COMMENT '目标大屏ID',
    target_url        varchar(500)         DEFAULT NULL   COMMENT '外链地址',
    params_tree       longtext              DEFAULT NULL   COMMENT '跳转参数JSON(可含源图表的维度值)',
    enabled           char(1)      NOT NULL DEFAULT '1',
    create_time       datetime,
    del_flag          char(1)      NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_jump_dashboard (dashboard_id),
    KEY idx_bi_jump_source (source_component_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 图表跳转';


-- ---------------------------------------------------------------------------
-- 13. bi_dashboard_outer_params 外部参数
--     ★ 嵌入主系统的关键：从 URL/宿主系统接收参数，注入图表查询。
--     参照 visualization_outer_params(+info+target)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_dashboard_outer_params
(
    id            bigint        NOT NULL AUTO_INCREMENT COMMENT '参数ID',
    dashboard_id  bigint        NOT NULL                COMMENT '所属大屏',
    param_name    varchar(100)  NOT NULL                COMMENT '参数名(URL query key)',
    param_type    varchar(16)   NOT NULL DEFAULT 'string' COMMENT 'string/number/date/dataset',
    default_value varchar(500)           DEFAULT NULL   COMMENT '默认值',
    target_component_id bigint           DEFAULT NULL   COMMENT '绑定的目标组件，空=全局生效',
    bind_type     varchar(16)   NOT NULL DEFAULT 'filter' COMMENT 'filter 注入过滤器 / sqlvar 注入SQL变量',
    bind_target   varchar(300)          DEFAULT NULL   COMMENT 'bind_type=sqlvar 时的 SQL 变量名',
    required      char(1)       NOT NULL DEFAULT '0'   COMMENT '是否必填',
    order_num     int           NOT NULL DEFAULT 0,
    enabled       char(1)       NOT NULL DEFAULT '1',
    create_time   datetime,
    del_flag      char(1)       NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_outer_dashboard (dashboard_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 外部参数(嵌入主系统用)';


-- ---------------------------------------------------------------------------
-- 14. bi_export_task 导出任务
--     异步导出：大结果集不阻塞请求，完成后下载
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_export_task
(
    id           bigint        NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    task_no      varchar(64)   NOT NULL                COMMENT '任务编号',
    biz_type     varchar(16)   NOT NULL DEFAULT 'dashboard' COMMENT 'dashboard/chart/dataset',
    biz_id       bigint                 DEFAULT NULL,
    format       varchar(16)   NOT NULL DEFAULT 'xlsx'  COMMENT 'xlsx/csv/pdf',
    dataset_id   bigint                 DEFAULT NULL   COMMENT '导出底层数据集',
    query_json   longtext               DEFAULT NULL   COMMENT '查询条件快照JSON',
    status       varchar(16)   NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/RUNNING/SUCCESS/FAILED',
    progress     int           NOT NULL DEFAULT 0      COMMENT '进度百分比',
    total_rows   bigint                 DEFAULT NULL,
    file_name    varchar(300)          DEFAULT NULL,
    file_path    varchar(500)          DEFAULT NULL,
    error_msg    varchar(2000)         DEFAULT NULL,
    cost_ms      bigint                 DEFAULT NULL,
    create_by    varchar(64)           DEFAULT '',
    create_time  datetime,
    finish_time  datetime,
    del_flag     char(1)       NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_export_no (task_no),
    KEY idx_bi_export_status (status),
    KEY idx_bi_export_create_by (create_by)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 导出任务';


-- ---------------------------------------------------------------------------
-- 15. bi_share 公开分享
--     ★ share_scope 决定被分享人能看到的数据范围，防泄露
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_share
(
    id           bigint        NOT NULL AUTO_INCREMENT COMMENT '分享ID',
    share_code   varchar(64)   NOT NULL                COMMENT '分享码(URL 用)',
    biz_type     varchar(16)   NOT NULL DEFAULT 'dashboard',
    biz_id       bigint        NOT NULL,
    share_scope  varchar(16)   NOT NULL DEFAULT 'owner' COMMENT 'owner 仅分享者权限 / all 全部数据 / viewer 访客范围',
    password     varchar(100)          DEFAULT NULL   COMMENT '访问密码(加密)',
    expire_time  datetime               DEFAULT NULL   COMMENT '过期时间，NULL=永久',
    visit_count  int           NOT NULL DEFAULT 0,
    status       char(1)       NOT NULL DEFAULT '0'  COMMENT '0启用 1停用',
    create_by    varchar(64)           DEFAULT '',
    create_time  datetime,
    del_flag     char(1)       NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_share_code (share_code),
    KEY idx_bi_share_biz (biz_type, biz_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 公开分享';


-- ---------------------------------------------------------------------------
-- 16/17. bi_dict_type / bi_dict_data BI 专用字典
--     若依的 sys_dict_* 是菜单级字典；BI 需要图表类型、聚合函数等业务字典
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_dict_type
(
    id          bigint       NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
    dict_type   varchar(100) NOT NULL                COMMENT '字典类型',
    dict_name   varchar(100) NOT NULL                COMMENT '字典名称',
    status      char(1)      NOT NULL DEFAULT '0',
    remark      varchar(500)         DEFAULT NULL,
    create_time datetime,
    update_time datetime,
    del_flag    char(1)      NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_dict_type (dict_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 字典类型';

CREATE TABLE IF NOT EXISTS bi_dict_data
(
    id          bigint       NOT NULL AUTO_INCREMENT COMMENT '字典数据ID',
    dict_type   varchar(100) NOT NULL,
    dict_label  varchar(100) NOT NULL                COMMENT '字典标签',
    dict_value  varchar(100) NOT NULL                COMMENT '字典键值',
    css_class   varchar(100)         DEFAULT NULL,
    list_class  varchar(100)         DEFAULT NULL,
    is_default  char(1)      NOT NULL DEFAULT 'N',
    order_num   int          NOT NULL DEFAULT 0,
    status      char(1)      NOT NULL DEFAULT '0',
    create_time datetime,
    update_time datetime,
    del_flag    char(1)      NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY idx_bi_dict_data_type (dict_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 字典数据';


-- ---------------------------------------------------------------------------
-- 18. bi_oper_log BI 操作日志
--     独立于 sys_oper_log：BI 的操作对象是大屏/图表/数据集，需要记录查询与导出行为
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bi_oper_log
(
    id            bigint       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    title         varchar(200)         DEFAULT NULL   COMMENT '操作模块',
    biz_type      varchar(32)          DEFAULT NULL   COMMENT '对象类型 datasource/dataset/chart/dashboard',
    biz_id        bigint                DEFAULT NULL,
    biz_name      varchar(200)         DEFAULT NULL,
    oper_type     varchar(16)          DEFAULT NULL   COMMENT 'query/export/share/publish/config',
    cost_ms       bigint                DEFAULT NULL,
    row_count     bigint                DEFAULT NULL   COMMENT '影响/返回行数',
    oper_by       varchar(64)          DEFAULT NULL,
    oper_name     varchar(100)         DEFAULT NULL,
    oper_ip       varchar(64)          DEFAULT NULL,
    json_result   longtext              DEFAULT NULL   COMMENT '请求参数/结果摘要JSON',
    status        char(1)      NOT NULL DEFAULT '0'  COMMENT '0成功 1失败',
    error_msg     varchar(2000)        DEFAULT NULL,
    oper_time     datetime,
    PRIMARY KEY (id),
    KEY idx_bi_log_biz (biz_type, biz_id),
    KEY idx_bi_log_oper_by (oper_by),
    KEY idx_bi_log_time (oper_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='BI 操作日志';

