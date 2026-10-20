-- 为全部 BI 元数据表补全表注释与字段注释（不改变类型/默认值/可空性）。
-- 跳过 Flyway 历史表 bi_flyway_history。
SELECT 1;

ALTER TABLE `bi_chart_template_lease` COMMENT 'BI 模板绑定产生的临时图表租约';
ALTER TABLE `bi_chart_template_lease`
  MODIFY COLUMN `chart_id` bigint NOT NULL COMMENT '临时图表ID',
  MODIFY COLUMN `owner_principal` bigint NOT NULL COMMENT '创建者 bi_principal.id',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `expire_time` datetime NOT NULL COMMENT '回收过期时间';

ALTER TABLE `bi_chart_view` COMMENT 'BI 图表定义';
ALTER TABLE `bi_chart_view`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '图表ID',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '图表名称',
  MODIFY COLUMN `dashboard_id` bigint NULL COMMENT '所属看板ID，可空（独立图表）',
  MODIFY COLUMN `dataset_id` bigint NOT NULL COMMENT '所属数据集ID',
  MODIFY COLUMN `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '图表类型：bar/line/pie/table/pivot/map 等',
  MODIFY COLUMN `render` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'echarts' COMMENT '渲染引擎：echarts/antv',
  MODIFY COLUMN `x_axis` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '维度轴配置 JSON',
  MODIFY COLUMN `y_axis` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '度量轴配置 JSON',
  MODIFY COLUMN `x_axis_ext` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '次要维度配置 JSON',
  MODIFY COLUMN `y_axis_ext` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '次要度量配置 JSON',
  MODIFY COLUMN `drill_fields` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '下钻字段路径 JSON',
  MODIFY COLUMN `ext_stack` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '堆叠扩展 JSON',
  MODIFY COLUMN `ext_bubble` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '气泡映射 JSON',
  MODIFY COLUMN `custom_filter` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '过滤器树 JSON',
  MODIFY COLUMN `style_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '样式 JSON',
  MODIFY COLUMN `ui_config` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '交互配置 JSON',
  MODIFY COLUMN `chart_ext` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '图表私有扩展 JSON',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新人',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除',
  MODIFY COLUMN `definition_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '已保存查询定义 JSON',
  MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';

ALTER TABLE `bi_column_permission` COMMENT 'BI 列级权限与脱敏（历史表，现行策略见 bi_dataset_policy）';
ALTER TABLE `bi_column_permission`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '列权限ID',
  MODIFY COLUMN `role_id` bigint NOT NULL COMMENT '适用角色（历史宿主角色ID）',
  MODIFY COLUMN `dataset_id` bigint NOT NULL COMMENT '作用数据集ID',
  MODIFY COLUMN `field_name` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字段名',
  MODIFY COLUMN `field_show_name` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '脱敏后显示名',
  MODIFY COLUMN `visible` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '1' COMMENT '1可见 0不可见（服务端剔除）',
  MODIFY COLUMN `desensitize` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '脱敏类型：mask/hash/all/keep',
  MODIFY COLUMN `mask_prefix` int NULL COMMENT '脱敏保留前缀长度',
  MODIFY COLUMN `mask_suffix` int NULL COMMENT '脱敏保留后缀长度',
  MODIFY COLUMN `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '1' COMMENT '是否启用：1启用 0停用',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_dashboard` COMMENT 'BI 看板/大屏';
ALTER TABLE `bi_dashboard`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '看板/大屏ID',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '看板/大屏名称',
  MODIFY COLUMN `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'dashboard' COMMENT '类型：dashboard 看板 / screen 大屏',
  MODIFY COLUMN `width` int NOT NULL DEFAULT 1920 COMMENT '画布宽度（像素或栅格语义见布局）',
  MODIFY COLUMN `height` int NOT NULL DEFAULT 1080 COMMENT '画布高度',
  MODIFY COLUMN `grid_cols` int NOT NULL DEFAULT 24 COMMENT '栅格列数',
  MODIFY COLUMN `theme` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '主题标识',
  MODIFY COLUMN `background` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '背景配置 JSON',
  MODIFY COLUMN `publish_id` bigint NULL COMMENT '当前发布快照ID（历史字段）',
  MODIFY COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '状态：0可用 1停用',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新人',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除',
  MODIFY COLUMN `definition_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '草稿定义 JSON',
  MODIFY COLUMN `published_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '当前发布快照 JSON',
  MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '草稿乐观版本号',
  MODIFY COLUMN `published_version` int NULL COMMENT '当前发布版本号';

ALTER TABLE `bi_dashboard_binding` COMMENT 'BI 看板与图表/数据集引用绑定';
ALTER TABLE `bi_dashboard_binding`
  MODIFY COLUMN `dashboard_id` bigint NOT NULL COMMENT '看板ID',
  MODIFY COLUMN `chart_id` bigint NOT NULL COMMENT '引用的图表ID',
  MODIFY COLUMN `dataset_id` bigint NOT NULL COMMENT '引用的数据集ID',
  MODIFY COLUMN `binding_kind` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '绑定种类：draft 草稿 / publish 发布';

ALTER TABLE `bi_dashboard_component` COMMENT 'BI 看板组件';
ALTER TABLE `bi_dashboard_component`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '组件ID',
  MODIFY COLUMN `dashboard_id` bigint NOT NULL COMMENT '所属看板ID',
  MODIFY COLUMN `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父组件ID，0 表示顶级',
  MODIFY COLUMN `ancestors` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '祖级组件ID列表',
  MODIFY COLUMN `component_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '组件类型：chart/kpi/text/image/container 等',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '组件名称',
  MODIFY COLUMN `x` int NOT NULL DEFAULT 0 COMMENT '布局 X',
  MODIFY COLUMN `y` int NOT NULL DEFAULT 0 COMMENT '布局 Y',
  MODIFY COLUMN `w` int NOT NULL DEFAULT 6 COMMENT '布局宽',
  MODIFY COLUMN `h` int NOT NULL DEFAULT 4 COMMENT '布局高',
  MODIFY COLUMN `z_index` int NOT NULL DEFAULT 0 COMMENT '图层顺序',
  MODIFY COLUMN `config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '组件配置 JSON',
  MODIFY COLUMN `style_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '组件样式 JSON',
  MODIFY COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '状态：0正常 1停用',
  MODIFY COLUMN `order_num` int NOT NULL DEFAULT 0 COMMENT '显示顺序',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_dashboard_jump` COMMENT 'BI 图表跳转配置';
ALTER TABLE `bi_dashboard_jump`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '跳转ID',
  MODIFY COLUMN `dashboard_id` bigint NOT NULL COMMENT '所属看板ID',
  MODIFY COLUMN `source_component_id` bigint NOT NULL COMMENT '触发组件ID',
  MODIFY COLUMN `jump_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'dashboard' COMMENT '跳转类型：dashboard/link/detail',
  MODIFY COLUMN `target_id` bigint NULL COMMENT '目标看板ID',
  MODIFY COLUMN `target_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '外链地址',
  MODIFY COLUMN `params_tree` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '跳转参数 JSON',
  MODIFY COLUMN `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '1' COMMENT '是否启用：1启用 0停用',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_dashboard_linkage` COMMENT 'BI 图表联动配置';
ALTER TABLE `bi_dashboard_linkage`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '联动ID',
  MODIFY COLUMN `dashboard_id` bigint NOT NULL COMMENT '所属看板ID',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '联动名称',
  MODIFY COLUMN `source_component_id` bigint NOT NULL COMMENT '触发方组件ID',
  MODIFY COLUMN `target_component_id` bigint NOT NULL COMMENT '接收方组件ID',
  MODIFY COLUMN `filter_tree` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '源字段到目标字段的映射 JSON',
  MODIFY COLUMN `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '1' COMMENT '是否启用：1启用 0停用',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_dashboard_outer_params` COMMENT 'BI 外部参数（嵌入宿主系统）';
ALTER TABLE `bi_dashboard_outer_params`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '参数ID',
  MODIFY COLUMN `dashboard_id` bigint NOT NULL COMMENT '所属看板ID',
  MODIFY COLUMN `param_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '外部参数名（URL query key）',
  MODIFY COLUMN `param_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'string' COMMENT '参数类型：string/number/date/dataset',
  MODIFY COLUMN `default_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '默认值',
  MODIFY COLUMN `target_component_id` bigint NULL COMMENT '绑定的目标组件ID，空表示全局生效',
  MODIFY COLUMN `bind_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'filter' COMMENT '绑定方式：filter 过滤器 / sqlvar SQL 变量',
  MODIFY COLUMN `bind_target` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'sqlvar 时的 SQL 变量名',
  MODIFY COLUMN `required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '是否必填：1是 0否',
  MODIFY COLUMN `order_num` int NOT NULL DEFAULT 0 COMMENT '显示顺序',
  MODIFY COLUMN `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '1' COMMENT '是否启用：1启用 0停用',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_dashboard_share` COMMENT 'BI 看板分享链接';
ALTER TABLE `bi_dashboard_share`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分享ID',
  MODIFY COLUMN `dashboard_id` bigint NOT NULL COMMENT '被分享看板ID',
  MODIFY COLUMN `token` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分享令牌',
  MODIFY COLUMN `data_scope` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'viewer' COMMENT '数据范围：viewer 访问者权限 / publisher 发布者权限',
  MODIFY COLUMN `password_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '访问密码哈希',
  MODIFY COLUMN `expire_time` datetime NULL COMMENT '过期时间，空表示永久',
  MODIFY COLUMN `publisher_actor_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'publisher 范围使用的发布者身份快照 JSON',
  MODIFY COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0启用 1停用',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `bi_data_form` COMMENT 'BI 数据填报定义';
ALTER TABLE `bi_data_form`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '填报定义ID',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '填报名称',
  MODIFY COLUMN `dataset_id` bigint NOT NULL COMMENT '目标表数据集ID',
  MODIFY COLUMN `fields_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '填报字段定义 JSON',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '备注',
  MODIFY COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0启用 1停用',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '更新人',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_data_form_record` COMMENT 'BI 数据填报提交记录';
ALTER TABLE `bi_data_form_record`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '提交记录ID',
  MODIFY COLUMN `form_id` bigint NOT NULL COMMENT '所属填报定义ID',
  MODIFY COLUMN `row_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '提交行数据 JSON',
  MODIFY COLUMN `success` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '是否写入成功：1成功 0失败',
  MODIFY COLUMN `error_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '失败原因',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `owner_principal` bigint NULL COMMENT '提交者 bi_principal.id',
  MODIFY COLUMN `request_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '幂等请求键',
  MODIFY COLUMN `payload_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '载荷哈希',
  MODIFY COLUMN `submission_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'legacy' COMMENT '提交状态：pending/succeeded/failed/legacy';

ALTER TABLE `bi_dataset_field` COMMENT 'BI 数据集字段';
ALTER TABLE `bi_dataset_field`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字段ID',
  MODIFY COLUMN `dataset_id` bigint NOT NULL COMMENT '所属数据集ID',
  MODIFY COLUMN `origin_name` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '物理列名/原始字段名',
  MODIFY COLUMN `origin_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '原始数据库类型',
  MODIFY COLUMN `show_name` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '前端显示名',
  MODIFY COLUMN `dim_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '1' COMMENT '1维度 0度量',
  MODIFY COLUMN `ext_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '1计算字段 0物理字段',
  MODIFY COLUMN `ext_formula` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '计算字段表达式',
  MODIFY COLUMN `date_format` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '日期格式',
  MODIFY COLUMN `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '业务分组（维度/度量等）',
  MODIFY COLUMN `order_num` int NOT NULL DEFAULT 0 COMMENT '显示顺序',
  MODIFY COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '状态：0正常 1停用',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_dataset_group` COMMENT 'BI 数据集目录';
ALTER TABLE `bi_dataset_group`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '目录ID',
  MODIFY COLUMN `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父目录ID，0 表示根',
  MODIFY COLUMN `ancestors` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '祖级目录ID列表，逗号分隔',
  MODIFY COLUMN `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目录名称',
  MODIFY COLUMN `order_num` int NOT NULL DEFAULT 0 COMMENT '显示顺序',
  MODIFY COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '状态：0正常 1停用',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新人',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_dataset_policy` COMMENT 'BI 数据集行列数据权限规则';
ALTER TABLE `bi_dataset_policy`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  MODIFY COLUMN `dataset_id` bigint NOT NULL COMMENT '作用数据集ID',
  MODIFY COLUMN `principal_id` bigint NOT NULL COMMENT '适用主体 bi_principal.id',
  MODIFY COLUMN `field_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '约束字段（物理列名）',
  MODIFY COLUMN `rule_kind` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则类型：EQ/NE/GT/GE/LT/LE/IS_NULL/IS_NOT_NULL/DENY/MASK',
  MODIFY COLUMN `rule_value` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '比较常量；DENY/MASK/空值类规则可空',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `bi_dataset_table` COMMENT 'BI 数据集定义';
ALTER TABLE `bi_dataset_table`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据集ID',
  MODIFY COLUMN `group_id` bigint NULL COMMENT '所属目录ID',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '数据集名称',
  MODIFY COLUMN `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'table' COMMENT '类型：table选表 / sql手写 / api / excel',
  MODIFY COLUMN `datasource_id` bigint NULL COMMENT '单源数据集的数据源ID；跨源可空',
  MODIFY COLUMN `source_table` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'type=table 时的物理表名',
  MODIFY COLUMN `sql_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'type=sql 时用户编写的原始 SQL',
  MODIFY COLUMN `union_sql` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '系统生成的最终执行 SQL',
  MODIFY COLUMN `cache_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '是否启用结果缓存：0否 1是',
  MODIFY COLUMN `cache_ttl` int NOT NULL DEFAULT 300 COMMENT '缓存有效期（秒）',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新人',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除',
  MODIFY COLUMN `source_catalog` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '表数据集校验后的物理库名',
  MODIFY COLUMN `model_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '表数据集可选的关联模型 JSON';

ALTER TABLE `bi_datasource` COMMENT 'BI 数据源定义';
ALTER TABLE `bi_datasource`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据源ID',
  MODIFY COLUMN `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '数据源名称',
  MODIFY COLUMN `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '类型：mysql/doris/postgresql/oracle/sqlserver/clickhouse 等',
  MODIFY COLUMN `schema_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Calcite schema 别名，查询中以 schema.table 引用',
  MODIFY COLUMN `jdbc_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'JDBC 连接串',
  MODIFY COLUMN `driver_class` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'JDBC 驱动类名，为空则按 type 自动推导',
  MODIFY COLUMN `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '连接账号',
  MODIFY COLUMN `password` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '连接密码，加密存储，接口不得回传明文',
  MODIFY COLUMN `ssh_host` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'SSH 隧道主机',
  MODIFY COLUMN `ssh_port` int NULL COMMENT 'SSH 隧道端口',
  MODIFY COLUMN `ssh_user` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'SSH 隧道用户名',
  MODIFY COLUMN `ssh_password` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'SSH 隧道密码，加密存储',
  MODIFY COLUMN `ssh_private_key` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'SSH 隧道私钥，加密存储',
  MODIFY COLUMN `pool_max_total` int NOT NULL DEFAULT 8 COMMENT '连接池最大连接数',
  MODIFY COLUMN `pool_min_idle` int NOT NULL DEFAULT 1 COMMENT '连接池最小空闲连接数',
  MODIFY COLUMN `query_timeout` int NOT NULL DEFAULT 60 COMMENT '查询超时（秒）',
  MODIFY COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '状态：0正常 1停用',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新人',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_dict_data` COMMENT 'BI 字典数据';
ALTER TABLE `bi_dict_data`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典数据ID',
  MODIFY COLUMN `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属字典类型',
  MODIFY COLUMN `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典标签',
  MODIFY COLUMN `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典键值',
  MODIFY COLUMN `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '样式 class',
  MODIFY COLUMN `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '回显样式 class',
  MODIFY COLUMN `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'N' COMMENT '是否默认：Y/N',
  MODIFY COLUMN `order_num` int NOT NULL DEFAULT 0 COMMENT '显示顺序',
  MODIFY COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '状态：0正常 1停用',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_dict_type` COMMENT 'BI 字典类型';
ALTER TABLE `bi_dict_type`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
  MODIFY COLUMN `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典类型编码',
  MODIFY COLUMN `dict_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典类型名称',
  MODIFY COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '状态：0正常 1停用',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_export_job` COMMENT 'BI 导出作业';
ALTER TABLE `bi_export_job`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '作业ID',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '作业名称',
  MODIFY COLUMN `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '来源类型：chart/dashboard',
  MODIFY COLUMN `source_id` bigint NOT NULL COMMENT '来源对象ID',
  MODIFY COLUMN `panel_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '看板组件 key；图表导出可空串',
  MODIFY COLUMN `format` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '导出格式：xlsx/csv/pdf',
  MODIFY COLUMN `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '作业状态',
  MODIFY COLUMN `request_json` varchar(8192) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '导出请求快照 JSON',
  MODIFY COLUMN `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '状态说明或失败信息',
  MODIFY COLUMN `row_count` int NOT NULL DEFAULT 0 COMMENT '导出行数',
  MODIFY COLUMN `file_name` varchar(220) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '下载文件名',
  MODIFY COLUMN `content_type` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '文件 MIME 类型',
  MODIFY COLUMN `content` longblob NULL COMMENT '文件二进制内容',
  MODIFY COLUMN `owner_principal` bigint NOT NULL COMMENT '所有者 bi_principal.id',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `finish_time` datetime NULL COMMENT '完成时间',
  MODIFY COLUMN `expire_time` datetime NOT NULL COMMENT '文件过期时间';

ALTER TABLE `bi_export_task` COMMENT 'BI 导出任务（历史表，现行任务见 bi_export_job）';
ALTER TABLE `bi_export_task`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  MODIFY COLUMN `task_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务编号',
  MODIFY COLUMN `biz_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'dashboard' COMMENT '业务类型：dashboard/chart/dataset',
  MODIFY COLUMN `biz_id` bigint NULL COMMENT '业务对象ID',
  MODIFY COLUMN `format` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'xlsx' COMMENT '导出格式：xlsx/csv/pdf',
  MODIFY COLUMN `dataset_id` bigint NULL COMMENT '导出所用数据集ID',
  MODIFY COLUMN `query_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '查询条件快照 JSON',
  MODIFY COLUMN `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'WAITING' COMMENT '任务状态：WAITING/RUNNING/SUCCESS/FAILED',
  MODIFY COLUMN `progress` int NOT NULL DEFAULT 0 COMMENT '进度百分比',
  MODIFY COLUMN `total_rows` bigint NULL COMMENT '导出总行数',
  MODIFY COLUMN `file_name` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '生成文件名',
  MODIFY COLUMN `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件存储路径',
  MODIFY COLUMN `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  MODIFY COLUMN `cost_ms` bigint NULL COMMENT '耗时（毫秒）',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `finish_time` datetime NULL COMMENT '完成时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_image_asset` COMMENT 'BI 图片素材库';
ALTER TABLE `bi_image_asset`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '素材ID',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '素材名称',
  MODIFY COLUMN `data_url` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '内嵌图片 Data URL',
  MODIFY COLUMN `byte_size` int NOT NULL COMMENT '字节大小',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_image_staging` COMMENT 'BI 图片暂存（设计器临时上传）';
ALTER TABLE `bi_image_staging`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '暂存ID',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '临时文件名',
  MODIFY COLUMN `data_url` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '内嵌图片 Data URL',
  MODIFY COLUMN `byte_size` int NOT NULL COMMENT '字节大小',
  MODIFY COLUMN `owner_principal` bigint NOT NULL COMMENT '上传者 bi_principal.id',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `expire_time` datetime NOT NULL COMMENT '过期时间';

ALTER TABLE `bi_oper_log` COMMENT 'BI 操作日志';
ALTER TABLE `bi_oper_log`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  MODIFY COLUMN `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作模块',
  MODIFY COLUMN `biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '对象类型：datasource/dataset/chart/dashboard',
  MODIFY COLUMN `biz_id` bigint NULL COMMENT '对象ID',
  MODIFY COLUMN `biz_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '对象名称',
  MODIFY COLUMN `oper_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作类型：query/export/share/publish/config',
  MODIFY COLUMN `cost_ms` bigint NULL COMMENT '耗时（毫秒）',
  MODIFY COLUMN `row_count` bigint NULL COMMENT '影响或返回行数',
  MODIFY COLUMN `oper_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作人账号',
  MODIFY COLUMN `oper_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作人姓名',
  MODIFY COLUMN `oper_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作 IP',
  MODIFY COLUMN `json_result` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '请求参数或结果摘要 JSON',
  MODIFY COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '结果：0成功 1失败',
  MODIFY COLUMN `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  MODIFY COLUMN `oper_time` datetime NULL COMMENT '操作时间';

ALTER TABLE `bi_permission_rule` COMMENT 'BI 行级权限规则（历史表，现行策略见 bi_dataset_policy）';
ALTER TABLE `bi_permission_rule`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则名称',
  MODIFY COLUMN `role_id` bigint NOT NULL COMMENT '适用角色（历史：sys_role.role_id，不可当作 bi_principal.id）',
  MODIFY COLUMN `dataset_id` bigint NOT NULL COMMENT '作用数据集ID',
  MODIFY COLUMN `rule_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'custom' COMMENT '规则类型：custom 固定规则 / attr 属性映射',
  MODIFY COLUMN `filter_tree` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'custom 类型的过滤树 JSON',
  MODIFY COLUMN `attr_mapping` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'attr 类型的字段映射 JSON',
  MODIFY COLUMN `attr_expression` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '属性表达式，如 user.dept',
  MODIFY COLUMN `priority` int NOT NULL DEFAULT 0 COMMENT '多角色命中时的合并优先级',
  MODIFY COLUMN `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '1' COMMENT '是否启用：1启用 0停用',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新人',
  MODIFY COLUMN `update_time` datetime NULL COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_principal` COMMENT 'BI 身份主体映射（用户/角色/组织，与 sys_* 主键隔离）';
ALTER TABLE `bi_principal`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'BI 主体内部ID',
  MODIFY COLUMN `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '身份来源：ruoyi / 未来独立身份',
  MODIFY COLUMN `principal_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主体类型：user/role/organization',
  MODIFY COLUMN `external_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '宿主侧外部ID（如 sys_user.user_id）',
  MODIFY COLUMN `display_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '显示名称',
  MODIFY COLUMN `create_time` datetime NOT NULL COMMENT '创建时间';

ALTER TABLE `bi_resource` COMMENT 'BI 资源归属';
ALTER TABLE `bi_resource`
  MODIFY COLUMN `resource_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资源类型：datasource/dataset/chart/dashboard 等',
  MODIFY COLUMN `resource_id` bigint NOT NULL COMMENT '资源ID',
  MODIFY COLUMN `owner_id` bigint NULL COMMENT '所有者 bi_principal.id；空表示管理员托管的历史资源';

ALTER TABLE `bi_resource_grant` COMMENT 'BI 资源授权';
ALTER TABLE `bi_resource_grant`
  MODIFY COLUMN `resource_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资源类型',
  MODIFY COLUMN `resource_id` bigint NOT NULL COMMENT '资源ID',
  MODIFY COLUMN `principal_id` bigint NOT NULL COMMENT '被授权主体 bi_principal.id',
  MODIFY COLUMN `action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资源动作，如 view/edit/query/consume/grant';

ALTER TABLE `bi_screen_template` COMMENT 'BI 大屏/看板布局模板';
ALTER TABLE `bi_screen_template`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  MODIFY COLUMN `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板分类',
  MODIFY COLUMN `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '模板说明',
  MODIFY COLUMN `content_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板内容 JSON',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
  MODIFY COLUMN `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '更新人',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_share` COMMENT 'BI 公开分享（历史表，现行分享见 bi_dashboard_share）';
ALTER TABLE `bi_share`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分享ID',
  MODIFY COLUMN `share_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分享码',
  MODIFY COLUMN `biz_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'dashboard' COMMENT '业务类型，默认 dashboard',
  MODIFY COLUMN `biz_id` bigint NOT NULL COMMENT '业务对象ID',
  MODIFY COLUMN `share_scope` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'owner' COMMENT '数据范围：owner/all/viewer',
  MODIFY COLUMN `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '访问密码（加密）',
  MODIFY COLUMN `expire_time` datetime NULL COMMENT '过期时间，空表示永久',
  MODIFY COLUMN `visit_count` int NOT NULL DEFAULT 0 COMMENT '访问次数',
  MODIFY COLUMN `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '状态：0启用 1停用',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

ALTER TABLE `bi_snapshot` COMMENT 'BI 发布快照';
ALTER TABLE `bi_snapshot`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '快照ID',
  MODIFY COLUMN `biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务类型：dashboard/chart',
  MODIFY COLUMN `biz_id` bigint NOT NULL COMMENT '业务对象ID',
  MODIFY COLUMN `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '快照码：draft 草稿 / publish 发布',
  MODIFY COLUMN `version` int NOT NULL DEFAULT 1 COMMENT '快照版本号',
  MODIFY COLUMN `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '完整内容 JSON 快照',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注',
  MODIFY COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN `create_time` datetime NULL COMMENT '创建时间',
  MODIFY COLUMN `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '删除标志：0存在 2删除';

