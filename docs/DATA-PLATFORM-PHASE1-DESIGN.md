# 数据平台第一阶段实施设计：统一资产目录与元数据采集

> 日期：2026-10-28  
> 状态：实施中；连接器注册目录 API/UI、未知类型 fail-closed、扫描配置 CRUD 和异步手动扫描 API 已接入代码；BI 模块跳过测试的 Maven 编译通过；V27 additive migration 尚未在目标环境执行或验收  
> 依据：[系统架构](DATA-PLATFORM-ARCHITECTURE.md)、[功能验收清单](DATA-PLATFORM-FUNCTION-CHECKLIST.md)、[表结构盘点](DATA-PLATFORM-SCHEMA-INVENTORY.md)、[开发任务清单](DATA-PLATFORM-TASK-LIST.md)、[开发计划](DATA-PLATFORM-DEVELOPMENT-PLAN.md)

## 1. 阶段目标与边界

第一阶段建立“发现资产—采集结构—查看历史—识别变化”的可运行闭环，覆盖 BI 已有资源和连接器可读取的数据库表/字段。目录是治理元数据的索引与快照，不承载业务行，也不复制数据源密码、SQL、图表定义或数据集事实。

### 本阶段包含

- 后端连接器注册表统一提供数据源类别和能力，消除前端 `DB_TYPES` 分类漂移。
- 资产目录：搜索/筛选、详情、来源跳转、负责人/描述/标签等治理元数据。
- 元数据扫描：手动触发，异步执行；按数据源和 schema/table 范围采集数据库、表、字段结构。
- 资产元数据版本：记录每次成功观测的 schema 快照和变化摘要，可查看版本差异。
- 扫描运行列表、进度、耗时、失败原因和审计；失败扫描不覆盖最近一次成功快照。

### 本阶段暂不包含

- 质量任务调度、数据加工 DAG、血缘自动推导、文件/API 的完整元数据采集器、告警、多实例 Worker、分布式锁。
- 读取或保存源表业务数据、字段样本值、SQL 文本和凭据。
- 自动改写数据集字段或报表依赖。变化只识别并提示，迁移和回滚在后续阶段设计。

## 2. 核心概念与标识

| 概念 | 说明 | 示例 |
|---|---|---|
| Asset | 目录中的一个数据/BI 资源 | 数据源、schema、table、column、dataset、metric、chart、dashboard |
| URN | 跨表稳定身份；大小写/引用符规则须由连接器规范化 | `wanfo:db:mysql:42:catalog.schema.table` |
| Asset version | 一次成功采集到的元数据版本 | 同一 table 的第 8 个 schema 快照 |
| Scan run | 一次扫描的执行与审计单元 | 手动发起，包含起止时间、状态和摘要 |

URN 编码必须转义各段并明确连接器大小写规则，不能简单拼接用户输入。当前 BI 主表没有租户隔离字段，首版沿用单实例边界；URN 包含来源数据源 ID 或 BI 资源 ID，在平台实例内稳定唯一。若后续引入租户隔离，再经统一身份/数据隔离方案评审后添加 tenant scope。

## 3. 建议新增模型

以下为逻辑模型，最终 SQL 类型、主键和审计列遵循项目现有 MyBatis/Flyway 规范。盘点时版本为 V26；本工作区已起草 `V27__bi_platform_metadata_catalog.sql`，落库前仍要复核部署目标的实际最高迁移和 schema 差异。该迁移仅新增表和索引。

### 3.1 `dp_asset`

| 字段 | 用途 |
|---|---|
| `id` | 平台主键 |
| `asset_type` | `DATASOURCE/SCHEMA/TABLE/COLUMN/DATASET/METRIC/CHART/DASHBOARD/API` |
| `source_kind` | `BI` 或 `CONNECTOR`，区分引用来源 |
| `source_id` | 原资源 ID；不复制原资源定义 |
| `urn`, `urn_hash` | 规范化稳定身份及 SHA-256；唯一索引用于兼容超长 UTF-8 URN，写入时还需校验原始 URN |
| `parent_id` | 目录树父资产，如 schema→table→column |
| `name`, `qualified_name` | 显示名与限定名 |
| `description`, `owner_principal_id`, `classification` | 可治理属性；不包含秘密 |
| `status` | `ACTIVE/STALE/REMOVED`；软删除/失联策略 |
| `last_seen_at`, `current_version_id` | 最近发现时间与当前成功版本 |
| `create_by`, `create_time`, `update_by`, `update_time` | 审计列 |

索引：唯一 `urn_hash`；查询 `(asset_type, status)`、`parent_id`、`source_kind, source_id`、名称检索所需索引。数据库不强依赖跨模块外键，服务层校验来源引用，另设周期完整性检查。

### 3.2 `dp_asset_version`

记录仅含元数据的成功快照：`id`, `asset_id`, `version_no`, `schema_hash`, `metadata_json`, `change_summary_json`, `observed_at`, `scan_run_id`, `created_by`。唯一索引 `(asset_id, version_no)`，查询索引 `(asset_id, observed_at)`。资产通过状态退役，不物理删除，从而保留审计和历史版本。

版本号只在成功快照提交时递增；同内容重复扫描可不创建新版本，但应更新 asset 的 `last_seen_at` 并记录 scan 结果。`metadata_json` 限制大小、字段白名单和版本兼容格式；禁止存储密码、连接 URL 中的 secret、业务数据样本和完整 SQL。

### 3.3 `dp_metadata_scan` 与 `dp_metadata_scan_run`

`dp_metadata_scan` 是采集定义：数据源 ID、范围配置（schema/table allowlist 或 exclude）、启用状态、触发方式与创建人。`dp_metadata_scan_run` 是每次执行：扫描 ID（配置被删除时允许置空以保留审计）、状态、触发人、开始/结束时间、进度计数、成功/失败对象数、错误摘要、结果摘要、trace/request ID。

初版运行状态：`PENDING → RUNNING → SUCCEEDED | PARTIAL_SUCCESS | FAILED | CANCELLED`。状态变更由服务端控制；重复触发需对同一数据源/扫描范围做并发保护。错误摘要应脱敏，不保存驱动异常里的凭据或 SQL 参数。

### 3.4 `dp_asset_tag`（可选）

若首期目录需要标签筛选，使用 `(asset_id, tag_key, tag_value)` 关联表；若 UI 暂无标签编辑需求则不在首批迁移创建，避免用任意 JSON 取代可查询模型。

## 4. 采集和一致性流程

1. 服务端检查发起人是否有元数据管理权限，并确认数据源存在、未删除、已授权；解密凭据仅在连接器内部使用。
2. 创建 scan run 后进入异步执行。范围配置按连接器能力校验；不允许浏览器传入任意 JDBC URL/驱动类。
3. Connector Registry 按数据源 `type` 解析连接器，向上层提供类别、能力（schema/table/column 列举、类型精度等）和安全元数据接口。
4. 连接器只调用系统目录视图读取结构，不执行用户 SQL，不读表行。对超时、最大对象数、schema allowlist 做硬限制。
5. 将单个扫描结果先写入暂存集合/内存受限批次，规范化 URN 和元数据后计算 hash；执行事务写 asset、版本、变更摘要和 run 汇总。
6. 只有全部对象映射校验通过的对象才更新 `current_version_id`。失败对象保留前一成功版本，run 标为部分成功或失败，并可重试。
7. 扫描未发现的对象首版标记 `STALE`，不立即删除；至少连续多次完整扫描缺失并经过保留期后，才进入人工确认/后续自动退役策略。

事务应按 schema/table 分块，避免大 schema 单事务和大 JSON；同一资产的版本递增应通过唯一约束/锁实现并发安全。若数据库方言对锁/JSON 类型有差异，使用项目兼容的通用列类型和事务模式。

## 5. 首批接口

统一沿用项目 `/bi` API 前缀、响应封装、分页和权限注解规范，具体 URL 需与现有 Controller 命名核对后定稿。

| 方法 | 资源 | 作用 |
|---|---|---|
| GET | `/bi/assets` | 分页搜索；支持 `keyword, assetType, sourceId, parentId, status` |
| GET | `/bi/assets/{id}` | 资产详情与最近成功版本摘要 |
| GET | `/bi/assets/{id}/versions` | 分页查看历史版本 |
| GET | `/bi/assets/{id}/versions/compare?from=&to=` | 返回新增/删除/变化字段，不返回源数据 |
| POST | `/bi/metadata-scans` | 创建扫描定义 |
| GET | `/bi/metadata-scans?datasourceId=` | 查询数据源的扫描定义 |
| PUT | `/bi/metadata-scans/{id}` | 更新名称、范围和启停状态 |
| DELETE | `/bi/metadata-scans/{id}` | 删除定义；运行中时拒绝删除，运行历史保留 |
| POST | `/bi/metadata-scans/{id}/runs` | 手动触发一次扫描，返回 run ID |
| GET | `/bi/metadata-scans/runs?datasourceId=` | 查看数据源最近 100 条运行记录 |
| GET | `/bi/metadata-scans/runs/{id}` | 进度、摘要和脱敏错误详情 |
| GET | `/bi/connectors` | 返回后端注册连接器类别及能力，供 UI 分类和能力展示 |

DP-006/007/008 当前实现支持扫描配置与异步运行，使用既有 JDBC `DatabaseMetaData` 枚举表和字段，将结构元数据写入资产目录并记录版本 hash、变更字段摘要。对象 URN 基于数据源 ID、schema/table/column 名称稳定生成；相同元数据重复扫描只更新 `last_seen_at`，不会制造新版本。只有全量、未筛选且无对象失败的成功扫描，才会把本次未发现的旧资产标记为 `STALE`；失败及部分成功不执行缺失推断。运行摘要声明 `snapshotPersisted=true`，仅代表结构快照持久化，不读取业务数据行、样本或凭据。DP-009 已新增按可见数据源权限过滤的搜索、详情和版本历史 API，含分页、类型、父级与关键词筛选。DP-010 前端已接入工作台入口，提供目录筛选、详情、版本差异、扫描配置和手动触发/运行状态；BI 类型检查和生产构建通过。V27 已在本地 MySQL 应用。PostgreSQL 容器通过 `pg_isready` 验证可连接，但还没有从已认证扫描 API 触发真实目录扫描；运行列表当前仅保留最近 100 条，尚无游标分页。

DP-011 已补充 BI 资源回填预览和管理员确认执行接口，按稳定 URN 幂等写入数据源、数据集、字段、图表、看板和指标的名称、类型、资源引用及版本快照；图表/看板定义、SQL 和凭据不复制。目录增加 BI 资源视图，并在查询、详情和版本历史时按原资源 `view` 授权过滤，字段继承数据集授权。尚需在本地登录环境执行预览/回填，对比源资源数量与目录数量，并验证撤权后不可见；API 独立资产类型/引用映射仍待补齐。

DP-012 已新增 V28 审计表，目录搜索/详情/版本、回填预览/执行、扫描配置与运行列表/触发操作记录操作者、资源标识、脱敏摘要和 `trace_id`。扫描运行状态轮询不逐次写审计，避免噪声。管理员可在资产目录查看全局审计；目录/扫描请求异常会记录方法、路径、异常类型和 Trace ID，不记录异常消息、请求体或查询参数。保留清理、任务时长/指标告警、审计写入失败告警和数据库迁移/实际运行验证仍待完成。

错误响应不应回显 credential、原始 JDBC URL、OAuth token、API secret、完整 SQL 或数据库驱动的原始异常内容。

### 4.1 注册目录与旧连接兼容边界

- 新建 JDBC 数据源及新建连接的试连必须使用 `ConnectorRegistry` 已登记的类型；不能因提交自定义 `driverClass` 就绕过类型目录。
- 未登记类型不能静默回退到 MySQL 驱动，避免类型拼错后连到错误驱动或造成误导性错误。
- 已存在的数据源保留其数据库中保存的驱动类，便于旧配置在迁移期继续管理；新类型是否纳入目录仍须先确认驱动依赖、测试矩阵和元数据适配能力。
- 当前目录不声明 `metadata-scan` 能力。完成真实目录读取适配器和验收前，不应在能力列表中展示可扫描。

## 6. 权限与审计

- 目录可见性必须与原资源授权相交集：有目录查看权限不代表可以读取其来源数据；无来源权限的资源不能通过目录搜索、详情、版本或父子层级侧信道暴露敏感名称/结构。
- 管理员可创建/执行扫描；普通用户只能查看其本来有权访问的资产。负责人/标签编辑另设权限，不默认授予目录浏览者。
- UI 隐藏按钮不构成授权。所有资产查询、版本比较、扫描触发、取消和范围变更均服务端鉴权并审计。
- 审计记录谁在何时对哪个数据源/范围触发何种扫描、结果和 trace ID；不记录凭据和业务行。
- 跨资源 owner/grant 关系由服务层校验；授权撤销要立即影响目录 API 查询，不等待下一轮扫描。

## 7. 交付顺序与验收

| 序号 | 交付项 | 验收条件 |
|---|---|---|
| 1 | 目标 schema 与 V26 核验 | 在部署前确认真实最高迁移、生产 DDL 差异和数据库方言；此设计的本机结果不替代生产核验 |
| 2 | Connector Registry | 后端单一映射类型、类别和能力；未知类型拒绝；前端分类来自后端 |
| 3 | additive migration | 空库和当前版本升级可执行；不改写已有 migration/BI 表/数据；索引适配受支持数据库 |
| 4 | 元数据目录 API | 授权过滤、分页、名称搜索、层级导航与版本详情工作 |
| 5 | 扫描生命周期 | 创建、触发、进度、失败/部分成功、重试可用；并发触发受控 |
| 6 | 两类真实连接器 | 至少 MySQL + PostgreSQL 或 ClickHouse 采集 schema/table/column；类型名、精度、可空性稳定 |
| 7 | 变化与回滚保护 | 重复扫描幂等；字段增删改能形成差异；采集失败不覆盖旧版本；缺失对象仅标 stale |
| 8 | 安全与可观测 | 正/负权限、撤权、软删除、跨 schema、禁用连接、凭据脱敏、超时/上限和 trace 审计通过 |

## 8. 后续阶段依赖

首期资产 URN 和版本表为后续阶段提供引用基线：第二阶段接 `dp_lineage_edge` 与变更影响分析；第三阶段质量规则版本、任务、运行和结果；第四阶段流程 DAG/运行/回放；之后再做结构变更迁移与回滚。不要在首期把血缘、质量、流程所有表一次性建齐，避免尚无执行语义时冻结错误模型。

