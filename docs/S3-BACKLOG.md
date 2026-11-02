# S3 Backlog（防范围回潮）

> 日期：2026-10-31  
> 依据：[PRODUCT-POSITIONING-AND-FOUNDATION.md](PRODUCT-POSITIONING-AND-FOUNDATION.md) §9 S3  
> 原则：**未完成书面业务触发 + ADR 前，下列项不得挤占 S2 收尾或默认升 P0**

## S3 目标形态

对外可选包装为：**BI + 轻量数据中台**。退出观感：中小团队可讲「轻量中台」，但仍不是 DataWorks 平替。

## 明确划入 S3 的条目

| ID | 主题 | 说明 | 触发条件示例 |
|---|---|---|---|
| S3-01 | 统一任务池 | 管道/装载/质量/扫描/CDC 统一调度视图与优先级队列 | 多租户任务互相饿死或运维无法排障 |
| S3-02 | 数据标准 MVP | 字段标准字典、落标检查、与术语/指标弱绑定 | 多域同名异义投诉上升 |
| S3-03 | API 版本门户 | 多版本并存目录、调用方清单、契约测试门禁 | 外部集成方 >N 且要求兼容窗口 |
| S3-04 | 运维影响分析增强 | 跨仓表/管道/API 的变更影响一键包 | 生产变更事故复盘要求 |
| S3-05 | KMS / 凭据托管 | 替换本地 GCM 信封；密钥轮换审计 | 合规要求硬件/云 KMS |
| S3-06 | 备份与演练 | 元数据/仓配置/审计归档恢复演练 runbook | 容灾验收或审计抽查 |
| S3-07 | Parquet/列存对象入仓 | 对象存储不止 CSV | 上游只提供 Parquet |
| S3-08 | 第二引擎 MERGE 完备 | Doris/StarRocks 与 CH 对等（现有 ADR 差距） | 业务强制换仓引擎 |

### S3-07 启动记录（2026-10-31）

**触发**：对象入仓此前仅 CSV；上游湖仓落地多为 Parquet；承接「继续」。  
**MVP 已交付**：`SnapshotParser.parquet`（内存 `InputFile`，扁平原始列，5MB/2万行界）；`ObjectStorageLoadService` 支持 `fileFormat=AUTO|CSV|PARQUET`；连接器能力 `parquet-ingest`；单测覆盖解析与格式匹配。  
**加深（2026-10-31）**：RECORD 展平为 `parent.child`；ARRAY/MAP 文本化（深度≤3）。  
**加深++（2026-10-31，W7）**：大对象落临时文件再解析（流式入口）；ORC 显式拒绝并提示未支持。  
**仍 backlog（S4+）**：ORC 真解析、>5MB 无限流式、CH 原生 Parquet 直写、深层嵌套无限展开。

### S3-08 启动记录（2026-10-31）

**触发**：ADR 记载 Doris/StarRocks MERGE 未接入 Publisher；承接「继续」。  
**MVP 已交付**：`WarehousePublisher.Engine` + `MysqlProtocolBatchPublisher`；装载 REPLACE/MERGE 分发 Doris（`REPLACE WITH TABLE`）/ StarRocks（`SWAP`+RENAME 回退）；连接器能力 `warehouse-merge`；单测覆盖类型映射与引擎解析。  
**加深（2026-10-31）**：对象入仓 + **管道 WAREHOUSE_PUBLISH** 均按目标类型分发 CH/Doris/SR。  
**加深+（2026-10-31，W5）**：管道 Maps 路径 `APPEND`（已存在目标则 `INSERT…SELECT`；首写同 REPLACE 建表）。  
**加深++（2026-10-31，W7）**：管道 Maps `UPSERT`（`mergeKeys`）经 `WarehousePublisher.upsertMaps`。  
**仍 backlog（S4+）**：多副本 UNIQUE 引擎策略、全局默认引擎切换配置中心。

### S3-01 启动记录（2026-10-31）

**触发**：S2 关闭后运维需跨模块统一查看；承接「继续下一步」。  
**MVP 已交付**：任务中心纳入 `OBJECT_LOAD`/`CDC_JOB`；`sort=priority`；`summary.byType/byLane`；前端优先级排序与分池标签。  
**加深（2026-10-31）**：`dp_task_pool_lane` + `/bi/tasks/lanes` 暂停/软并发；装载/对象入仓提交前 `assertLaneRunnable`。  
**加深+（2026-10-31，W5）**：管道生产提交前 `assertLaneRunnable("PIPELINE", activeGlobalCount)`。  
**加深++（2026-10-31，W6）**：`QUALITY` / `CDC_JOB` / `METADATA_SCAN` 提交与调度入口同样 `assertLaneRunnable`。  
**加深+++（2026-10-31，W7/V63）**：泳道 `priority`/`weight` + `bi.task-pool.global-max-concurrent` 公平软份额。  
**仍 backlog（S4+）**：真正抢占式 Worker、跨实例任务状态机。

### S3-02 启动记录（2026-10-31）

**触发**：术语/指标已有，目录字段同名异义需可运营字典；承接「继续下一步」。  
**MVP 已交付**：`dp_field_standard` + 绑定表（V54）；`/bi/field-standards` CRUD/绑定；`GET .../compliance` 只读落标；可选 `glossaryTermId`/`metricId`；前端字段标准页。  
**加深（2026-10-31）**：`enforce_gate`；sample-validate；**管道 WAREHOUSE_PUBLISH** 对 enforce 标准列做样本值域门禁。  
**加深+（2026-10-31）**：`POST /bi/field-standards/online-sample-validate` 源表 JDBC 在线抽样（COLUMN→TABLE→datasource，标识符白名单引用）。  
**加深++（2026-10-31，W7/V63）**：`domain_code` 域分层；`dp_field_standard_bind_plan` 批量绑定审批（DRAFT→APPROVED→APPLIED）。  
**仍 backlog（S4+）**：多级标准域树 UI、跨租户标准市场。

### S3-03 启动记录（2026-10-31）

**触发**：S2/W4 已交付 deprecate/sunset；需可发现的生命周期目录与 30 日调用摘要（承接「继续下一步」）。  
**MVP 已交付**：`GET /bi/data-apis/portal` + 前端版本门户表（状态/sunset/继任编码/Key 前缀调用量）。  
**契约门禁（续，2026-10-31）**：V57 `contract_schema_json`；`POST .../contract-check`；发布强制探测指标响应列并相对继任基线阻断删列/改类型；前端「契约校验」。  
**调用方名录（续，2026-10-31）**：V58 `dp_data_api_caller` + Key.`caller_id`；`/bi/data-apis/callers` CRUD；轮换/绑定组织；门户 `callerOrgs` 30 日汇总。  
**多版本流水线（续，2026-10-31）**：V59 `family_code`；`POST .../next-version` 拉出同族草稿；门户 `families`/`coexisting`；旧 ACTIVE 可并存至 deprecate。  
**加深（2026-10-31）**：回放/自动废弃/调用方配额；V61 金丝雀%；**Key sticky**（hash(keyId,family)）；**全局日配额**（api_id=0 CALLER_GLOBAL_DAY）。  
**加深+（2026-10-31，V62）**：发布落库脱敏样本 `contract_sample_json`；`contract-sample-replay` / `refresh-contract-sample`。  
**加深++（2026-10-31，W7/V63）**：调用方 `canary_weight` 加权 sticky 金丝雀；`dp_data_api_call.sample_json` 按采样率落脱敏样本。  
**仍 backlog（S4+）**：完整历史 payload 无限保留、按调用方加权灰度编排 UI。

### S3-04 启动记录（2026-10-31）

**触发**：生产变更需跨仓表/管道/API 一键影响包；承接「继续下一步」。  
**MVP 已交付**：`GET /bi/catalog/assets/{id}/impact-pack`（血缘下游 + 物理表数据集扇出：指标/图表/看板/API/流程/装载/CDC + 严重度与检查清单）；结构变更 impact 同步补 metrics/charts/dashboards/dataApis；目录页「运维影响包」可复制 JSON。  
**加深（2026-10-31）**：change-gate；迁移默认阻断；**`execute?overrideBlocking=true`** 审计放行。  
**加深+（2026-10-31）**：影响包无权消费者 `[无权查看]` 占位 + `redactedConsumerCount`（不泄露名称/状态）。  
**加深++（2026-10-31，W7/V63）**：`dp_change_ticket` 影响包自动/手动变更单；迁移 `overrideBlocking` 须绑定已审批 ticket。  
**仍 backlog（S4+）**：跨引擎全量依赖、独立审批工作流引擎。

### S3-05 启动记录（2026-10-31）

**触发**：GCM 本地密钥需可轮换审计，并为云 KMS 留接口；承接「继续下一步」。  
**MVP 已交付**：`CredentialKeyProvider` + 本地密钥环；密文 `encgv:{version}:`；V55 指纹元数据/轮换审计；`GET /bi/datasource/credentials/key-status`、`POST .../rotate`（`rewrap-gcm` 复用）。  
**加深（2026-10-31）**：`EXTERNAL_KMS_STUB` Provider（`bi.credential.key-provider`）失败闭合占位。  
**加深+（2026-10-31，W7）**：`CLOUD_KMS_DEV` Provider（环境变量 `WANFO_CLOUD_KMS_DEV_KEY`）可插拔云 KMS 契约加深。  
**仍 backlog（S4+/合规）**：AWS/阿里云 SDK 真适配、HSM、自动双写过渡窗口。

### S3-06 启动记录（2026-10-31）

**触发**：OPS-010 需可查 RPO/RTO 与演练证据；承接「继续下一步」。  
**MVP 已交付**：V56 `dp_backup_drill`；`/bi/ops/backup-drills` + `readiness`；`scripts/backup-metadata.ps1`；`docs/BACKUP-RESTORE-DRILL.md`；前端备份演练页。  
**加深（2026-10-31）**：扫描登记；**`POST …/dump-now`** + 可选 cron（`bi.backup.auto-dump-enabled`，默认关）。  
**加深+（2026-10-31，W7）**：`warehouseEngineProbe` 对仓数据源只读连通/引擎探测并登记演练。  
**仍 backlog（S4+）**：副本自动拉起、ClickHouse 官方 BACKUP/RESTORE 联调、跨机房复制。

## 明确不划入 S3（仍属 S4+ / 不对齐）

- 湖仓一体全量、自由 SQL 生产仓、实时流引擎替换批处理主路径  
- 第二套登录/权限、多租户 SaaS 计费  
- 模板市场/大屏视觉无限扩展、对标 DataWorks 全功能清单  

## S3 MVP 关闭说明（2026-10-31）

S3-01～S3-08 主路径验收见 [S3-ACCEPTANCE-RECORD.md](S3-ACCEPTANCE-RECORD.md)（Flyway BI **V63**）。  
W7 已收口本表「S3 加深」MVP；上表「仍 backlog（S4+）」须新触发，不得默认升 P0 或对标 DataWorks 全量。

## 与清单/路线图关系

- 功能细项仍以 `DATA-PLATFORM-FUNCTION-CHECKLIST.md` 为准，但 **P2/暂缓不得默认升 P0**。  
- `PLATFORM-EXTENSION-ROADMAP.md` 未勾选且属于上表的，跟进时改状态并引用本文 ID。  
- S2/S3 MVP 均已关闭；后续加深以验收记录遗留表 + 本文触发条件为准。  
- **商业成交/过审观感**类后续项见 [COMMERCIAL-VALUE-BACKLOG.md](COMMERCIAL-VALUE-BACKLOG.md)（与本文余项交叉时两边互链 ID，不得无触发默认升 P0）。
