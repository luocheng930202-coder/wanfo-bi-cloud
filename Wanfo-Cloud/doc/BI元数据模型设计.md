# BI 报表平台 元数据模型设计

> **版本**：v1.0　|　**日期**：2026-10-15
> **落地状态**：19 张表已通过 Flyway v2 建成并验证
> **参照**：DataEase v3 元数据库实测 111 张表（取其 BI 核心部分重构）

---

## 一、总览

| 分组 | 表数 | 表 |
|---|---:|---|
| 数据源 | 1 | `bi_datasource` |
| 数据集 | 3 | `bi_dataset_group` / `bi_dataset_table` / `bi_dataset_field` |
| 可视化 | 4 | `bi_dashboard` / `bi_dashboard_component` / `bi_snapshot` / `bi_chart_view` |
| **权限** ★ | 2 | **`bi_permission_rule`（行级）** / **`bi_column_permission`（列级+脱敏）** |
| 交互 | 3 | `bi_dashboard_linkage` / `bi_dashboard_jump` / `bi_dashboard_outer_params` |
| 输出 | 3 | `bi_export_task` / `bi_share` / `bi_oper_log` |
| 字典 | 2 | `bi_dict_type` / `bi_dict_data` |
| 迁移 | 1 | `bi_flyway_history` |

---

## 二、ER 图

~~~mermaid
erDiagram
    bi_datasource ||--o{ bi_dataset_table : "被引用"
    bi_dataset_group ||--o{ bi_dataset_table : "包含"
    bi_dataset_table ||--o{ bi_dataset_field : "字段"
    bi_dataset_table ||--o{ bi_chart_view : "被图表使用"
    bi_dashboard ||--o{ bi_dashboard_component : "组件树"
    bi_dashboard_component ||--o{ bi_chart_view : "内嵌图表"
    bi_dashboard ||--o{ bi_snapshot : "草稿/发布"
    bi_chart_view ||--o{ bi_snapshot : "草稿/发布"
    bi_dashboard ||--o{ bi_dashboard_linkage : "联动"
    bi_dashboard ||--o{ bi_dashboard_jump : "跳转"
    bi_dashboard ||--o{ bi_dashboard_outer_params : "外部参数"
    bi_dataset_table ||--o{ bi_permission_rule : "行级权限"
    bi_dataset_table ||--o{ bi_column_permission : "列级权限"
    bi_dashboard ||--o{ bi_share : "分享"
    bi_dashboard ||--o{ bi_export_task : "导出"

    bi_datasource {
        bigint id PK
        varchar name "展示名"
        varchar type "mysql/doris/pg"
        varchar schema_name UK "Calcite schema 别名"
        varchar jdbc_url
        varchar username
        varchar password "加密存储"
        int pool_max_total
        char status
    }

    bi_dataset_table {
        bigint id PK
        bigint group_id FK
        varchar name
        varchar type "table/sql/api/excel"
        bigint datasource_id FK
        varchar source_table "type=table"
        longtext sql_text "type=sql"
        longtext union_sql "系统生成"
    }

    bi_dataset_field {
        bigint id PK
        bigint dataset_id FK
        varchar origin_name
        varchar show_name
        char dim_flag "1维度 0度量"
        char ext_flag "1计算字段"
        varchar ext_formula
    }

    bi_dashboard {
        bigint id PK
        varchar name
        varchar type "dashboard/screen"
        int width
        int height
        int grid_cols
        bigint publish_id "当前发布快照"
    }

    bi_dashboard_component {
        bigint id PK
        bigint dashboard_id FK
        bigint parent_id FK "容器嵌套"
        varchar component_type "chart/filter/text"
        int x
        int y
        int w
        int h
        longtext config_json
    }

    bi_chart_view {
        bigint id PK
        bigint dataset_id FK
        bigint dashboard_id FK
        varchar type "bar/line/pivot"
        longtext x_axis "维度轴JSON"
        longtext y_axis "度量轴JSON"
        longtext custom_filter "过滤器树JSON 权限注入层"
    }

    bi_permission_rule {
        bigint id PK
        bigint role_id FK "sys_role"
        bigint dataset_id FK
        varchar rule_type "custom/attr"
        longtext filter_tree "过滤树JSON"
        longtext attr_mapping "字段映射JSON"
    }

    bi_column_permission {
        bigint id PK
        bigint role_id FK
        bigint dataset_id FK
        varchar field_name
        char visible "0=服务端剔除"
        varchar desensitize "mask/hash/all"
    }

    bi_snapshot {
        bigint id PK
        varchar biz_type "dashboard/chart"
        bigint biz_id
        varchar code "draft/publish"
        longtext content
    }
~~~

---

## 三、关键设计决策

### 3.1 权限表为什么独立成两张

| 表 | 管什么 | 执行时机 | 失败后果 |
|---|---|---|---|
| `bi_permission_rule` | **哪些行**能看到 | 编译 SQL 时注入 WHERE | 看到别人的数据 |
| `bi_column_permission` | **哪些字段**能看到 | 查询后裁剪结果集 | 看到敏感字段 |

两者执行时机不同、失败后果不同，所以不能合并。

### 3.2 行级权限的三种规则形态

| rule_type | 存储 | 适用 | 实现难度 |
|---|---|---|---|
| `custom` | filter_tree（AND/OR 嵌套树） | 固定报表 | 低 |
| **`attr`** | attr_mapping（字段映射） | **主流方案** | 中 |
| 自定义 SQL | —— | 复杂场景 | 高（**安全风险，不建议**） |

`attr` 形态的标准配置：

~~~text
用户 zhangsan  ->  user.dept = "华东区"
数据集 orders.region  ->  映射后生成 WHERE orders.region = '华东区'
~~~

### 3.3 为什么 JSON 用 longtext 不用 json 类型

- 生产若降到 MySQL 5.7 就没有原生 json 类型
- BI 的图表配置是**只读不检索**的，享受不到 json 索引红利
- 代价：失去数据库层 JSON 校验，改由 Java 侧（Jackson）保证

### 3.4 快照表的设计

`bi_dashboard.publish_id` 指向当前发布的 `bi_snapshot`，编辑时只改 `code=draft` 的记录，点发布才写 `code=publish` 并更新指针。

好处：**已发布的大屏不受编辑影响**，访客始终看到稳定版本。

---

## 四、表关系的三个易错点

| # | 易错点 | 正确做法 |
|---|---|---|
| 1 | 图表既能挂在组件下，也能独立存在 | `bi_chart_view.dashboard_id` 可空；挂在组件下时由 `bi_dashboard_component.config_json` 引用图表 ID |
| 2 | 跨源数据集没有单一 datasource_id | `bi_dataset_table.datasource_id` 允许为空，跨源信息存在 union_sql 里 |
| 3 | 分享的权限范围 | `bi_share.share_scope` 三选一：`owner`（仅分享者权限）/ `all`（全部数据）/ `viewer`（访客范围）。**默认 owner**，防止通过分享泄露 |

---

## 五、迁移记录

| 版本 | 脚本 | 内容 | 表数 |
|---|---|---|---:|
| v1 | `V1__bi_init.sql` | 核心表 | 8 |
| v2 | `V2__bi_extend.sql` | 权限/交互/导出/字典/日志 | 10 |

执行记录（服务启动时自动执行）：

~~~text
Successfully applied 1 migration to schema ry_vue, now at version v1 (0.270s)
Successfully applied 1 migration to schema ry_vue, now at version v2 (0.513s)
~~~

---

## 六、后续待补的表（S3~S9 阶段）

| 阶段 | 表 | 说明 |
|---|---|---|
| S3 | `bi_datasource_task` / `bi_datasource_task_log` | 元数据采集任务与日志 |
| S3 | `bi_datasource_schema_cache` | 库表字段缓存 |
| S6 | `bi_chart_template` | 图表模板库 |
| S9 | `bi_schedule_task` | 大屏定时刷新 / 预聚合任务 |
| S9 | `bi_export_download` | 导出文件下载记录 |

**明确不做**（不在 BI 职责内）：数据同步相关表。MySQL 到 Doris 由 Flink CDC 承担，见 WBS 铁律三。

---

## 七、已验证的运行时接口

| 接口 | 方法 | 验证结果 |
|---|---|---|
| ~/bi/health~ | GET | status=UP，引擎版本 1.42.0，2 个数据源全通 |
| ~/bi/datasource/list~ | GET | 返回数据源列表，**password 字段为 \*\*\*\*** |
| ~/bi/datasource/{schema}/test~ | POST | 连通性测试，MySQL 26.7.0，686 ms |
| ~/bi/datasource/probe~ | GET | 批量探测，{demo-a-region:true, demo-b-school:true} |
| ~/bi/datasource/reload~ | POST | 配置热重载（过渡接口） |
| ~/bi/query/cross-source-demo~ | GET | 跨源 JOIN，中文列名+聚合，2.8 s |
| ~/bi/query~ | POST | 任意 SELECT，**服务端用 Calcite 解析器校验必须是 SqlSelect** |

