# 数据平台现有表结构与引用盘点

> 盘点日期：2026-10-27  
> 盘点范围：BI 模块 Flyway V1–V26、BI 后端实体/Mapper/服务代码、当前本地开发数据库只读元数据。  
> 结论适用范围：代码仓库结构可复核；运行库结果只代表本机 `ry_vue` 开发数据库，不代表生产库。

## 1. 摘要结论

1. **没有必要重建 BI 核心表。** `bi_datasource`、`bi_dataset_table`、`bi_dataset_field`、`bi_chart_view`、`bi_dashboard`、指标、权限和订阅等已有功能模型，应继续由现有 BI 模块持有。
2. 数据平台扩展的主要缺口是**新增平台治理实体**：统一资产目录/元数据版本、采集运行、血缘、质量任务历史、流程版本与运行、结构变更计划。建议新增 `dp_*` 表，通过稳定 URN 和来源资源 ID 引用 BI 资产；不复制数据集、图表或数据源定义。
3. **数据源分类目前不持久化**：数据库只存连接器 `type`，前端的 OLTP/OLAP/API/文件分组来自本地 `DB_TYPES` 常量。短期应该将“连接器类型 → 分类/能力”移至后端连接器注册表；只有分类需要管理员自定义、持久化授权或治理标签时才增加数据库字段。
4. `bi_dataset_quality_rule` 是“dataset_id → rules_json”的单行配置，不能独立表达规则版本、任务计划、历史运行、趋势和问题闭环。保留现表以兼容现有质量检查，平台化质量能力另建规则/任务/运行/结果表。
5. 现有字段表能满足 BI 查询建模，但缺少完整源元数据版本、源端原生类型精度、字段稳定映射和结构变化历史。优先在平台元数据表记录快照，暂不直接破坏性扩展 `bi_dataset_field`。
6. 本地运行库的相关引用核对未发现数据集字段、数据集数据源、看板绑定和资源 owner 的孤儿记录。代码所定义的跨表外键很少，主要完整性目前依赖服务层。
7. 迁移注释明确标为历史的旧表仍保留，当前本地记录数为 0。需先核实生产数据与运行代码，再决定是否长期归档；**当前不删除、不重命名、不直接合并这些表**。

## 2. 盘点范围和证据口径

### 2.1 代码迁移

BI 迁移目录为 `Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/db/migration/bi/`，版本从 `V1__bi_init.sql` 到 `V26__bi_report_subscription_channels.sql`。本地 `ry_vue.bi_flyway_history` 显示最新版本 V26 成功。

关键迁移：

| 迁移 | 内容 |
|---|---|
| V1 | 数据源、数据集目录/定义/字段、看板组件、图表、发布快照等 BI 核心表 |
| V3–V6 | BI 主体、资源 owner/grant、数据集访问策略 |
| V7–V8 | 图表定义 JSON、看板草稿/发布定义 JSON 和 binding |
| V14 | 数据集关联模型 `model_json` |
| V19 | 指标定义、发布历史、使用引用 |
| V21–V23 | API/文件快照、图表/看板资源历史、API 快照刷新计划 |
| V24–V26 | 报表订阅、执行审计、通知渠道和站内通知 |
| V25 | 数据集质量规则 JSON 配置 |

### 2.2 本地数据库实况

通过 Docker 中运行的 `mysql-dev` 对 `ry_vue` 做了只读查询，只查看 `information_schema`、迁移状态、类型计数、表记录数和若干引用计数，没有读取 JDBC 密码、源表业务行、图表配置或凭据内容，也没有更改表/数据。

- 本机库迁移最新为 V26，状态成功。
- `bi_datasource` 共 18 行，其中 9 行 `del_flag='0'`、9 行 `del_flag='2'`。当前有效的连接器类型计数为 ClickHouse 1、Doris 1、MySQL 4、Oracle 1、PostgreSQL 1、SQL Server 1；API/CSV/Excel 数据源记录在本机均为软删除状态。
- 旧表 `bi_permission_rule`、`bi_column_permission`、`bi_export_task`、`bi_share` 本地记录数为 0；对应当前策略/作业/分享表本地存在非零数据。`bi_snapshot` 和 `bi_dashboard_component` 当前记录数也为 0。
- 本地查询到：孤儿数据集字段 0、指向不存在数据源的数据集 0、孤儿看板 binding 0、无效资源 owner 0。该结论只覆盖这些明确执行的检查，不代表所有多态引用都无问题。
- `bi_dataset_quality_rule` 和 `bi_metric_usage` 本地目前无记录；不据此推断生产库一定为空。
- 当前 schema 中可见的 BI 外键只有 `bi_dataset_policy → bi_principal` 和 `bi_dataset_quality_rule → bi_dataset_table`。资产、图表/看板 binding 等其他关系主要由服务代码维护。

## 3. 现有表的所有权与建议

| 表/表族 | 当前责任与事实来源 | 结构观察 | 建议 |
|---|---|---|---|
| `bi_datasource` | BI 数据源、连接器类型、连接信息和连接池配置 | 保存 `type`、连接串、账号及加密凭据；V21 增 `source_schema`；没有独立 `category` 列 | **继续由 BI 持有**。不要复制到平台表；后端连接器注册表按 type 返回分类/能力。扩展认证/网络元数据需要先划分机密字段与非机密字段 |
| `bi_dataset_group` | BI 数据集目录树 | 只组织数据集 | 不替代面向所有资产的业务域/标签目录；新目录引用现有 ID |
| `bi_dataset_table` | BI 数据集定义 | 表/SQL/API/文件等设置；含 `source_catalog`、关联 `model_json`；当前还有长 SQL/JSON配置 | 继续作 BI 查询定义事实来源；不要把调度 DAG、运行状态或治理快照塞进 `model_json` |
| `bi_dataset_field` | BI 消费侧字段定义 | `origin_name`、`origin_type varchar(32)`、展示名和计算公式；无源端字段快照/精度/可空/完整历史 | 继续服务图表字段配置。新建元数据字段快照表并保存采集时间/来源；字段重命名先做显式映射，不按名称盲目覆盖 |
| `bi_dataset_policy` / `bi_principal` | 当前 BI 行列策略所用主体 | 主体 ID 与 RuoYi 主键隔离；策略中 principal 有外键，dataset 引用无外键 | 沿用已有授权服务；治理任务、数据服务必须调用服务端授权，不自行复制角色表。生产数据迁移前校验 principal 对应关系 |
| `bi_chart_view` | 图表草稿定义 | 活跃 Mapper 以 `definition_json` 为读写定义；V1 的 `x_axis/y_axis/style_json/chart_ext` 等列仍保留 | 当前运行路径已较明确以 JSON 为主。旧列是否可删必须先查历史读者、生产数据和回滚需求；先不做清理 |
| `bi_dashboard` | 看板草稿和发布定义 | 活跃 Mapper 使用 `definition_json`、`published_json`、版本号；binding 表服务消费者关系 | 继续作为定义事实来源；binding 是可查询的引用索引，保存时应与 JSON 定义同事务更新并可重建 |
| `bi_dashboard_component` / `bi_snapshot` | V1 初始规范化组件/通用快照模型 | 当前本机 0 行；现行看板/图表代码路径主要使用 definition/published JSON 与资源历史 | 暂按兼容/历史表保留。只有确认所有环境及旧数据均不再读取后，才另行归档或弃用 |
| `bi_resource_history` | 现有图表/看板草稿历史 | 保存 resource type/id/version/operation/definition JSON，Mapper 有保留上限清理；不覆盖源表/数据集/质量/流程资产版本 | 不要直接扩成所有治理历史的混合表。新资产与流程应有领域版本/运行表；需要跨域审计时只用通用事件引用 URN |
| `bi_metric` / `bi_metric_history` / `bi_metric_usage` | BI 指标定义和已发布快照 | 指标主表有草稿 JSON与发布 JSON；历史表持有不可变发布版本；usage 是 polymorphic `(ref_type, ref_id)` | 复用为当前指标定义的事实来源；新增指标目录属性可用扩展表/治理资产引用。usage 需要写入/删除时同步验证消费者存在，定期检查孤儿引用 |
| `bi_dataset_quality_rule` | 当前数据集字段质量规则配置 | 每 dataset 一条 `rules_json`，没有 rule ID、版本、调度、运行与结果明细 | 兼容保留；平台化新增独立规则/任务/运行/结果，迁移现有 JSON 作为规则初始版本并做可重复校验 |
| `bi_source_snapshot` / `bi_source_refresh` | 有界 API/文件数据快照及刷新计划 | 快照每数据源一行，受既有体积/行数上限约束；`rows_json` 是查询快照而非通用存储层 | 不增大限制来充当数据湖/加工存储。大文件/历史版本应评估对象存储；规则/流程版本不要放在快照字段里 |
| `bi_report_subscription*` / `bi_export_job` | BI 订阅、运行审计、通知、导出 | 已有任务/审计字段和索引；渠道/计划在 JSON 列中 | 统一运行中心应适配这些现存运行实体，而不是复制新的订阅/导出事实表；如果统一任务目录，优先增通用 run 索引/接口，不强迫业务表合并 |
| 旧权限/分享/导出表 | V2 初始方案遗留 | V18 迁移备注标记 `bi_permission_rule`、`bi_column_permission`、`bi_export_task`、`bi_share` 为历史实现，指向现行表 | 本地计数为 0 且当前服务查询主要未引用，但删除前必须检查生产数据、外部 SQL/报表、回滚版本和恢复策略 |

## 4. 数据源分类结论

数据库中的 `bi_datasource.type` 保存 `mysql`、`postgresql`、`clickhouse`、`api`、`csv` 等连接器类型。当前 OLTP/OLAP/API/文件分组定义在前端 `src/views/bi/datasource/index.vue` 的 `DB_TYPES` 常量中，没有落入数据库；数据湖也只是一个前端分组选项，不代表后端已有可执行的 Lake Connector。

建议分两步：

1. 建立**后端权威 Connector Registry**：每种 `type` 声明 category、驱动/方言、是否 JDBC、元数据/查询/写入能力、验证状态。API 输出可携带 category，但不用重复存一份静态值。
2. 若产品以后允许管理员创建自定义分类、按分类授权/计费/生命周期策略，再通过新增 nullable/有默认值的 category code 字段或分类扩展表持久化；迁移时由 type 映射回填并校验未知类型。

因此，当前阶段不建议为了前端分类筛选立即给 `bi_datasource` 加列；应先把分类语义和 connector capabilities 收敛在后端。

## 5. 引用关系和约束现状

### 5.1 代码显式使用

- 图表和看板定义使用 `definition_json`；看板发布使用 `published_json`；图表/看板历史使用 `bi_resource_history`。
- 看板 binding 由 `bi_dashboard_binding` 保存图表/数据集引用；代码在更新看板时同步维护 binding。
- 数据源快照与刷新计划分别由 `bi_source_snapshot` 和 `bi_source_refresh` 维护。
- 质量检查配置由 `BiDatasetQualityRuleMapper` 读写 `rules_json`；这是当前持久化结构，没有质量 run/result 表。
- 主体/授权使用 `bi_principal`、`bi_resource`、`bi_resource_grant` 和 `bi_dataset_policy`。
- 指标消费者用 `bi_metric_usage`；由于引用对象类型多态，数据库不设指向 chart/dashboard 的固定外键。

### 5.2 需要保持的引用规则

- 新目录资产通过 `source_kind + source_id + asset_type + stable URN` 指向 BI 资源；新治理表不拥有 BI 图表、数据源、数据集事实。
- 同一资源的 JSON 定义与 binding 索引需要事务一致；新增、改名、删除和软删除均需重新计算依赖。
- 多态引用不强求传统外键，但服务层必须验证目标存在、目标有效、调用者有权限；增加 orphan 检查任务和审计。
- 关系型同域表在确认软删除/历史保留语义后可逐步加外键；不要对 `del_flag` 生命周期冲突或 polymorphic resource ID 盲目加 FK。

## 6. 首批平台表模型建议

首批限定为“统一目录 + 元数据快照 + 采集状态”，不一次性把血缘、质量、DAG、API 服务全塞进 V27。建议分版本、每版能独立回滚/重放：

### 6.1 目录和版本（首个迁移批次）

| 表 | 关键字段/约束 | 说明 |
|---|---|---|
| `dp_asset` | `id`、`asset_type`、`source_kind`、`source_id`、`urn` 唯一、`parent_id`、`name`、`description`、`owner_principal`、`classification`、`status`、`last_seen_at`、审计字段 | 当前可见资产索引；BI 资源是外部事实引用，目录不复制连接密码/完整定义 JSON |
| `dp_asset_version` | `asset_id`、`version` 唯一、`metadata_json`、`schema_hash`、`source_kind`、`observed_at`、`scan_run_id` | 保存不可变技术元数据版本；字段明细可独立列为资产，完整采集快照用于比较与回滚预览 |
| `dp_asset_tag`（可与 asset_version 分批） | `asset_id`、`tag_key`、`tag_value`、创建人/时间；必要唯一键 | 可搜索的业务标签；业务描述和责任人需区分用户维护信息与扫描同步信息 |

### 6.2 采集运行（紧随目录）

| 表 | 关键字段/约束 | 说明 |
|---|---|---|
| `dp_metadata_scan` | 数据源 ID 引用、范围 JSON、周期、enabled、owner、更新时间 | 采集定义，不复制数据源凭据 |
| `dp_metadata_scan_run` | scan ID、run ID/幂等键、状态、开始结束、对象计数、错误摘要、trigger、trace ID | 每次采集运行事实；成功才更新资产快照指针 |

### 6.3 后续表组

- 血缘：`dp_lineage_edge` + 来源/置信度/版本字段。
- 质量：`dp_dq_rule`、`dp_dq_rule_version`、`dp_dq_task`、`dp_dq_run`、`dp_dq_result`。
- 流程：`dp_pipeline`、`dp_pipeline_revision`、`dp_pipeline_node/edge`、`dp_pipeline_run/node_run`。
- 结构变更：`dp_schema_change`、`dp_impact_ref`、`dp_migration_plan/step`。

这些后续表应各自独立 migration 和业务 owner，并先定义 retention、软删除、外键、状态机、配额与幂等键。

## 7. 建议迁移策略

1. **所有 schema 变更只追加 Flyway migration**，不修改已应用的 V1–V26；先用新的 V27+，不要依赖 `CREATE TABLE IF NOT EXISTS` 静默吞掉不兼容结构。
2. 新表先创建并以服务层双读/单写或只读回填接入；完成数据对账和权限回归后再切换 UI/查询路径。
3. 老数据转新模型要可重跑：保存源 ID、映射数量、跳过原因；回填前后核对数量、URN 唯一、owner、授权和 orphan。
4. 结构变化支持“新增列/表先上线、服务兼容、数据回填、写入切换、观察、最后弃用”顺序；禁止一版迁移同时删旧列/旧表并切换全部代码。
5. 生产前在与目标版本接近的数据库备份恢复副本上执行；V26 当前只读审计不能代替生产 schema introspection。

## 8. 第一阶段待办及验收

| 顺序 | 任务 | 完成条件 |
|---|---|---|
| 1 | 确认目标环境结构与 V26 差异 | 对生产/验收库导出只含表结构的 DDL，与 Flyway 迁移及本机 V26 做差异报告；不导出业务行或秘密 |
| 2 | 后端 Connector Registry | 同一 type 的 category/capability 由服务端返回；UI 不再维护另一套可漂移分类映射；未知 type fail-closed |
| 3 | 评审 `dp_asset`/版本设计 | URN 规则、来源 ID、字段粒度、metadata ownership、权限过滤、删除/软删除、索引及保留策略通过评审 |
| 4 | 编写 V27 首批 additive migration | 空库和 V26 数据库均可执行；重跑由 Flyway 正确判定；不修改或删除任意 BI 既有表/记录 |
| 5 | 元数据采集试点 | 至少 MySQL 与 PostgreSQL/ClickHouse 之一真实采集；重复扫描幂等；采集失败保留旧成功快照；字段变化可查询历史版本 |
| 6 | 目录 API 与权限 | 对授权/未授权、资源撤权、软删除、跨 schema 和源连接禁用做服务端正/负测试；不泄露密码/SQL/业务行 |

## 9. 尚未证明的边界

- 本地 `ry_vue` 的表结构和行计数不能证明生产数据库一致；需在目标环境执行只读 schema 导出比对。
- 本地 orphan 检查只覆盖数据集字段、数据源、dashboard binding 和 resource owner；尚未检查 metric usage、资源授权、json 内嵌图表引用、订阅资源 ID 与所有软删除引用。
- `TABLE_ROWS` 对 InnoDB 通常是估值；本文件只报告通过 `COUNT(*)` 明确查询的旧表/引用和数据源状态，不用估计行数证明容量。
- 业务数据库记录和敏感配置未在本盘点读取或展示。

