# 平台扩展实施清单

本文按平台扩展优先顺序追踪实现与未覆盖边界。

**产品定位与底座**见 [PRODUCT-POSITIONING-AND-FOUNDATION.md](PRODUCT-POSITIONING-AND-FOUNDATION.md)。新增能力须挂接其中主路径 M1–M6，并遵守底座约束；不得按「完整 DataWorks」范围膨胀。

## 1. API 数据源增强

- [x] GET / POST JSON 请求参数与页码分页
- [x] 定时同步
- [x] OAuth 2.0 客户端凭据取令牌；凭据加密保存，每次同步刷新
- [x] 可选展开嵌套对象为点号列名
- [x] 游标分页（可指定请求游标参数、响应下一游标路径、初始游标）
- [x] 指定一条嵌套对象数组路径并展开为多行
- [x] OAuth 授权码 + PKCE、Refresh Token 交换与轮换保存
- [x] 最多 4 个独立对象数组路径的有界关联展开

请求、授权和令牌地址仍受 `bi.external-source.allowed-origins` 服务端白名单约束；静态回调 `/bi-oauth-callback.html` 须精确加入 `bi.external-source.oauth-redirect-uris` 并登记到 OAuth 应用。授权使用一次性 state、PKCE S256、用户绑定的 Redis 状态（10 分钟过期），授权码在后端交换，Refresh Token 加密保存在数据源配置中并在后续同步时刷新；轮换返回的新 Refresh Token 会先持久化，再查询 API 数据。最多 5 MB、展开后 20000 行、30 秒。每条记录最多配置 4 个独立对象数组路径，按笛卡尔关系展开，字段以数组路径加前缀，父字段重复；不支持互相嵌套的数组路径。超行数、字段不一致或超时会拒绝更新并保留旧快照。

## 2. 文件数据源增强

- [x] CSV / Excel 预览和手动字段类型
- [x] 追加或覆盖
- [x] 按行拒绝或跳过并返回异常行号、字段和原因
- [x] 明确选择时可覆盖并替换字段结构
- [x] 交互式字段映射（重命名、映射旧字段、忽略字段）、全行/业务键去重
- [x] 结构替换前预览新增/移除字段并二次确认
- [x] 以规范化字段名提供一对一自动映射建议；目标名有歧义时要求手动选择
- [x] 依赖数据集字段自动迁移和回滚

追加可映射重命名字段，缺少的目标列补空值；业务键去重保留最后出现的数据行。自动建议只忽略大小写、空白和标点，且仅在唯一匹配时预填，不会自动保存。结构替换后可经 `POST /bi/datasource/field-migrations/apply` 对同数据源物理数据集做规范化一对一 remap，并支持 rollback；Schema 影响分析提供 `SEMI_AUTO_REMAP` 指引。

## 3. 数据加工能力

- [x] 已有字段公式、单表关联、UNION 数据集能力
- [x] 计算字段可依序引用之前的计算字段，支持有限安全公式函数
- [x] 样本质量报告：空值、不同值、重复值、重复行
- [x] 字段级质量规则（必填、唯一、最大空值率）和样本违规提示
- [x] 全量精确质量扫描（数据库聚合、沿用数据集行列权限；超时失败不返回部分结果）
- [x] 最多 8 条顺序关联的关系建模，后续关系可引用前序输出字段；计算清洗步骤支持依赖校验、排序和只读回放预览

快速质量报告最多统计当前用户可见的 100 行样本；全量模式由数据库计算空值、不同值和重复行，按当前数据权限过滤。多表关联最多 8 条，关联输出字段总数不超过 200；后续关系只能选择前序关系可用字段。清洗步骤按保存顺序依次投影，重排时重新校验公式依赖；回放重新读取数据源并预览前 20 行，不会写回源数据。全量扫描可能耗时，单条聚合查询最多 120 秒，超时直接失败；统计时源数据仍可能变化，因此不是跨查询事务快照。

## 4. 新图表类型

- [x] 热力图、矩形树图、旭日图、桑基图、箱线图、直方图、瀑布图、经纬度散点地图
- [x] 热力图色阶、矩形树图双维层级/导航、桑基图间距与曲度、箱线图方向、直方图手动分箱、瀑布图累计总计
- [x] 矩形树图/旭日图支持最多 6 层有序维度路径；地图提供内置边界底图主题及原始/墨卡托投影；箱线图异常点须线、倍数、形状、大小和颜色可配置

箱线图默认用 1.5×IQR 计算须线并显示范围外样本；地图使用仓库内置行政区边界，不加载第三方在线瓦片。直方图采用最多 20 个、约平方根样本数的等宽分箱；经纬度地图为经纬度坐标散点。

## 5. 分析算法

- [x] 线性回归趋势线、3 点移动平均、线性外推最多 12 个等间距周期
- [x] 均值 ± k 倍总体标准差异常点标记（k 在 1～4）
- [x] 可选择 MAD 稳健异常检测；分析配置持久化并校验边界
- [x] 季节性朴素预测（重复上一季节周期）、经验预测区间和业务指标口径说明

季节预测仅用于折线/面积图中的单一时间粒度和单一聚合指标；必须明确缺失周期补空或补零，并关闭同比环比、TopN。季节长度 2～366，预测最多 12 个周期，至少需要两个完整季节且至少 8 个历史季节误差；历史误差绝对值的经验分位数形成对称区间。需要填写业务指标口径说明。该方法只是重复最近季节周期的朴素基线，不含趋势/外部变量，区间基于同一历史序列回测误差，不代表统计模型的覆盖率保证；口径正确性仍由业务人员确认。

## 6. 报表订阅

- [x] 定时生成已保存图表/已发布看板，支持 CSV / Excel / PDF 邮件附件
- [x] 订阅管理、暂停/启用、删除、执行历史与最近状态
- [x] 每次执行重新解析订阅人的当前身份并校验资源和数据权限
- [x] 失败后延迟重试，连续 3 次失败自动暂停；审计记录不保存邮件地址或数据内容
- [x] 站内通知、管理员配置的企业微信机器人通道
- [x] 按日/周本地日历计划，支持 IANA 时区并按 UTC 持久化
- [x] 通知标题/正文模板和受控变量；管理员全局执行审计（最多 200 条）

邮件通道使用 SMTP。部署时需设置 `bi.report-subscription.enabled=true`、`bi.report-subscription.allowed-domains` 域名白名单和 Spring Boot `spring.mail.*`。企业微信需配置 `bi.report-subscription.wecom.webhook-url` 与 `bi.report-subscription.wecom.allowed-hosts`；只允许管理员配置的 HTTPS 主机，不跟随重定向，消息仅包含模板摘要、不发送报表附件。订阅可选择站内通知、企业微信、邮件的组合；通知模板支持 `reportName`、`status`、`rowCount`、`startedAt`。日/周计划按所选 IANA 时区计算并保存 UTC 下次运行时间，固定间隔仍支持 15 分钟至 7 天。全局审计通过 `bi:resource:admin` 保护，最多展示最近 200 次运行且不记录收件地址或报表数据。仓库不含通道凭据，也未执行真实邮件或企业微信投递验收。

## 7. 数仓平台差距补齐（2026-10-30 增补）

口径：相对完整数仓/数据平台能力盘点。现状是「成熟自助 BI + 早期数据平台骨架」——管道定义、ClickHouse ODS 有界装载、零售固定 DWS、元数据/血缘/质量/Schema 迁移/Data API 已有 MVP。下列为**缺失或需加强**项，按阶段推进；未勾选项均未达生产可用。

相关代码基线：`Wanfo-Cloud/ruoyi-modules/ruoyi-bi`（Flyway V27～V44 `dp_*`）、任务中心 `/bi/tasks`、管道 `/bi/pipeline`、仓装载 `/bi/warehouse/loads`、零售模型 `/bi/warehouse/models`。

### 7.0 阶段优先级

| 阶段 | 目标 | 退出条件（摘要） |
|---|---|---|
| A | 管道真正落仓 + 生产调度 | 发布管道可定时/手动写 ODS（或声明目标层），失败可重试/告警，任务中心可观测 |
| B | 通用分层建模（去掉硬编码零售） | 可配置 ODS→DWD→DWS（或等价）物化，不依赖单一 `retail-v1` SQL |
| C | 质量规则执行补齐 + 入仓门禁 | RANGE/REGEX/新鲜度/对账可执行；管道 `QUALITY_GATE` 可阻断发布 |
| D | 大批量装载与断点续传 | 突破单次约 10 万行硬顶；分区/并行/水位线可运维 |
| E | 湖仓连接器与近实时 | 至少一类湖/对象存储或 CDC 入仓可用 |
| F | 血缘/目录/语义深化与产品化 | 列级血缘、定时元数据采集、指标到仓表血缘；仓控台菜单与授权完整 |

### 7.1 阶段 A：管道生产运行时与调度（P0）

现状：DAG 可定义/发布/预览；预览 ≤100 行且 `warehouseWrites=0`；`WAREHOUSE_PUBLISH` 仅记录意图；无管道生产调度。

- [x] 管道生产运行模式（与 preview 分离）：`POST .../runs/production`，`run_mode=PRODUCTION`，行数上限可配（默认 10 万）
- [x] `WAREHOUSE_PUBLISH` 真实落仓：生产模式经 `ClickHouseBatchPublisher.publishMaps` 写入 `wanfo_wh_{dwd|dws|ads}.pipe_{modelCode}`（首期仅 REPLACE）
- [x] 管道运行状态机：沿用 PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED/TIMED_OUT；节点级摘要含 `writtenRows`/`targetTable`
- [x] 生产调度：`dp_pipeline_schedule` + `PUT/GET .../schedule`，复用 `SubscriptionSchedule`（interval/daily/weekly）；轮询提交 PRODUCTION
- [x] 失败策略：调度连续失败退避（60s/300s），达 3 次自动停用；任务中心重试按快照恢复 PRODUCTION
- [x] 并发与互斥：同一管道同时仅一个活动运行（`active_pipeline_id` 唯一约束 + activeCount）
- [x] 权限：生产运行沿用 `bi:pipeline:run` + 资源 `run`；目标须为 schema=`wanfo_wh_ods` 的 ClickHouse
- [x] 前端：管道设计器生产运行入口、运行历史、失败详情（菜单从隐藏权限提升为可见页面）

边界：首期仍可不做跨源联邦分布式执行；单控制面 + JDBC/ClickHouse/Doris/SR 目标即可。Maps 路径支持 REPLACE/APPEND；UPSERT 与截断后仍写入均拒绝。生产运行与调度入口见 `Wanfo-Cloud-Vue/src/views/bi/pipeline/index.vue`。

### 7.2 阶段 B：通用分层建模与物化（P0/P1）

现状：ClickHouse 装载硬限制 `wanfo_wh_ods`；`RetailModelMaterializationService` 固定 `agg_retail_sales_monthly`。

- [x] 分层目标可配置：`LoadRequest.targetLayer` 支持 ODS/DWD/DWS/ADS；连接 schema 仍须 `wanfo_wh_ods`，实际写入对应 `wanfo_wh_*` 库
- [x] 通用物化任务：`POST /bi/warehouse/models/materialize` 将授权数据集 REPLACE 写入指定逻辑层表（行数受 `bi.warehouse.*-max-rows` 约束）；表物理 FULL/INCREMENTAL 仍走 `/bi/warehouse/loads`
- [x] 维表/事实表声明：主键、分区键、更新策略（覆盖/合并）；禁止客户端任意 SQL 直写生产库（`/bi/warehouse/table-models`，MERGE 可声明、执行器仍仅 REPLACE）
- [x] 将 `retail-v1` 降级为「内置示例模型包」：`GET /bi/warehouse/models/packages` 标注 `kind=EXAMPLE`；专用 refresh 路径保留
- [x] 物化后对账钩子：行数/空跑检测写入既有 reconciliation 表（`GET /bi/warehouse/models/reconciliation`）
- [x] 仓行级 scope（`V37`）与物化结果数据集绑定产品化：物化请求可选 `scopeBind`，成功后写入 scope 绑定；消费侧经 `DatasetDataPolicy` 自动带组织范围

### 7.3 阶段 C：数据质量执行与入仓门禁（P1）

现状：规则版本与任务/问题流已有；可执行仅 `NOT_NULL`、`UNIQUE`、`NULL_RATE_MAX`；RANGE/REGEX/FRESHNESS/RECONCILIATION 定义可存但执行器拒绝。

- [x] 执行器补齐：`RANGE`/`REGEX`/`FRESHNESS`（样本评估，最多 1 万行）与 `RECONCILIATION`（相对上次成功全量运行行数容差）；管道门禁同步支持前三类
- [x] 质量任务可对仓表（ODS/DWS）与表数据集统一调度；结果进任务中心（仓表注册为物理数据集后即可挂质量任务；专用「仓表」任务类型未另开）
- [x] 管道 `QUALITY_GATE`：未达 `minPassRate` 抛错阻断后续 `WAREHOUSE_PUBLISH`（生产/预览均阻断）
- [x] 可配置「仅告警不阻断」：`blockOnFailure=false` 时失败写入节点摘要 `warnedOnly`，不阻断后续发布
- [x] 问题工单 SLA：认领时写入 `sla_due_at`（`bi.quality.issue-sla-hours`，默认 48h）；列表带 `slaBreached`，`GET .../quality/issues/sla` 汇总逾期；趋势已有，订阅告警对接仍待
- [x] 规则发布与回滚：门禁/任务已要求引用已发布规则版本

### 7.4 阶段 D：大批量装载与运维能力（P1）

现状：管理员触发 JDBC→ClickHouse；单次约 10 万行；有 staging/checkpoint/orphan recovery，但仍是有界 MVP。

- [x] 可配置批次大小与总行上限：`bi.warehouse.default-max-rows`（默认 10 万）、`bi.warehouse.absolute-max-rows`（默认 100 万，且不超过发布器上限）
- [x] 分区拉取：全量装载支持 `partitionColumn` + `partitionFrom`/`partitionTo`（ISO 日期，最多 366 天区间过滤）；真并行切片与背压限速仍待
- [x] 断点续传产品化：失败任务 `POST /bi/warehouse/loads/runs/{id}/retry` 复用请求快照（V43）；任务中心亦支持重试
- [x] 装载与数据集行权限策略对齐：可选 `filterDatasetId`，按当前身份可见的数据集预览结果入仓（与物理全表拷贝互斥）
- [x] ClickHouse 以外目标适配：Doris/StarRocks 装载 REPLACE/MERGE（S3-08；管道/物化默认仍 CH）
- [x] 装载运维页 API：`GET /bi/warehouse/loads/ops` 返回活动/孤儿运行计数与最近记录；前端运维页 `bi/warehouse/index`
- [x] 分区并行切片：全量分区装载支持 `parallelSlices`（1～8），按日窗并行拉取后一次 REPLACE 发布

### 7.5 阶段 E：连接器扩展与近实时（P2）

现状：`ConnectorRegistry` 含 `lake` 分类但无实现；无 Kafka/CDC/对象存储入仓。

- [x] 对象存储连接器（至少一种：S3 兼容 / OSS）：清单文件、CSV/Parquet 批入 ODS（`/bi/warehouse/object-loads`；`fileFormat=AUTO|CSV|PARQUET`；S3-07）
- [ ] 湖表只读 POC（Iceberg 或 Hive Metastore 之一）：元数据浏览 + 受限查询，再定入仓路径
- [x] CDC/增量：MySQL binlog 或等价（对接外部 Debezium staging 表，平台负责任务编排 `/bi/warehouse/cdc-jobs`）
- [ ] 近实时物化：分钟级调度与增量合并；明确与批调度资源隔离
- [ ] 连接器插件生命周期：驱动白名单、超时、目标网络策略（延续数据源安全治理）

### 7.6 阶段 F：治理深化、语义层与产品化（P2）

现状：资产目录/手动扫描、有界血缘图、指标 + 指标绑定 Data API、Schema 迁移限单表物理数据集；仓相关 UI/菜单不完整。

**元数据与血缘**

- [x] 元数据扫描定时调度：启用配置按 `bi.metadata-scan.interval-minutes`（默认 1440）由平台管理员身份轮询触发；失败跳过至下次
- [x] 列级血缘 MVP：重建时补充 `DATASET_FIELD` 父子边、指标→数据集 `DERIVES`、指标→字段 `REFERENCES`（管道/物化 SQL 列解析仍待）
- [x] 业务术语表 / 指标词典：与 `bi_metric` 对齐，支持责任人、口径说明、认证状态（`/bi/glossary/terms`，可选 `metricId`）
- [x] 指标 → 数据集 → 仓表 血缘贯通；Data API 调用审计关联到指标版本

**Schema 与目录**

- [x] Schema 迁移扩展：JOIN/SQL 数据集影响分析（可先只读阻断 + 指引，再支持半自动映射）
- [x] 类型变更字段的半自动 remap 向导；与文件数据源「依赖字段自动迁移」（§2）合流
- [x] 统一「平台资产目录」与「建模用 JDBC 目录」产品叙事，避免两套 catalog 概念混淆（`GET /bi/catalog/narrative`）

**Data API / 语义**

- [x] Data API 非仅管理员：资源级授权与配额按调用方（`data_api` 资源 + grants；配额仍按 Key 窗口）
- [x] 复合指标 / 跨数据集指标（明确首期边界，避免无限语义层）：`kind=composite`，2～8 个已发布标量 + 算术表达式
- [x] OpenAPI/SDK 文档与密钥轮换自助（`GET /bi/data-apis/{id}/openapi` + 既有 rotate/revoke；前端 `bi/data-api/index`）
- [x] Data API 契约版本 + 废弃/sunset 说明（`V53`；`deprecate`/`undeprecate`；OpenAPI `lifecycle`；不重做 Key）
- [x] S3-03 API 版本门户完整 MVP：`/portal` + 契约门禁（V57）+ 调用方名录（V58）+ `family_code`/`next-version` 多版本并存（V59）
- [x] S3-01 统一任务池 MVP：`/bi/tasks?sort=priority` 聚合装载/对象入仓/CDC + 分池摘要（无第二套任务状态表）
- [x] S3-02 数据标准 MVP：`dp_field_standard`（V54）+ `/bi/field-standards` + 只读落标检查；弱绑定术语/指标
- [x] S3-04 运维影响包 MVP：`/bi/catalog/assets/{id}/impact-pack` 跨指标/API/流程/装载/CDC；结构变更 impact 扩展消费者

**S2 关闭说明（2026-10-31）**：六项观感验收见 `docs/S2-ACCEPTANCE-RECORD.md`；未勾选且属轻量中台包装的能力转入 `docs/S3-BACKLOG.md`，不得默认升 P0。

**S3 MVP 关闭说明（2026-10-31）**：S3-01～S3-08 验收见 `docs/S3-ACCEPTANCE-RECORD.md`；加深至 **V61**（V60 门禁/回放/配额 + V61 金丝雀%/管道多引擎/Parquet 展平/值域抽样）；其余见 backlog，禁止无触发扩中台。
**产品与安全外壳**

- [x] 菜单与路由：`pipeline`/`catalog`/`warehouse`/`lineage`/`data-api` 已写入 `ruoyi-menu.sql`（需重跑菜单脚本）；前端页面组件可先占位深链
- [x] 仓操作审计与保留策略（对接 V42）产品化配置页（`/bi/warehouse/audit` + V48 `warehouse` policy_key）
- [x] 凭据加密从 ECB 迁到 GCM（新写 `encg:`；`POST /bi/datasource/credentials/rewrap-gcm` 批量升级）；KMS 仍待
- [x] S3-05 密钥版本/轮换审计 MVP：`encgv:{n}:` + `CredentialKeyProvider`（LOCAL_CONFIG）+ V55 审计；外部 KMS 适配仍待
- [x] S3-06 备份演练 MVP：`dp_backup_drill` + readiness + `backup-metadata.ps1` + runbook；不自动恢复生产
- [x] S3-07 Parquet 对象入仓 MVP：`SnapshotParser.parquet` + `fileFormat`；扁平列/5MB 界；嵌套与流式仍待
- [x] S3-08 Doris/StarRocks MERGE MVP：`MysqlProtocolBatchPublisher` 与 CH 同契约 REPLACE/MERGE；管道默认引擎仍待
- [ ] 独立部署评估仍不阻塞本阶段；继续 `bi.security.mode=ruoyi`，但接口保持宿主中立

### 7.7 明确不纳入本清单（防范围膨胀）

- 第二套登录/权限系统；多租户 SaaS 计费
- 任意用户在生产仓执行自由 SQL
- 完整替换 dbt/Airflow/Spark 生态；本清单以「可控入仓 + 治理闭环」为界
- DataEase/第三方模板市场兼容、大屏视觉无限扩展（见图表/大屏专项清单）

### 7.8 建议落地顺序（开发排期）

1. **A1** 管道生产运行 + WAREHOUSE_PUBLISH 真写 ODS（打通闭环）
2. **A2** 管道调度与任务中心/告警
3. **C1** 质量执行器补齐 + QUALITY_GATE 阻断
4. **B1** 分层目标可配置 + 通用增量物化（retail 示例化）
5. **D1** 批次/并行/续跑与行上限策略
6. **F1** 菜单产品化 + 定时元数据采集 + 列级血缘 MVP
7. **E*** 按业务需要插入湖/CDC，不阻塞 A～D

每完成一小阶段须：后端测试 + 任务中心联调 + 一份短验收记录（输入源、目标表、行数、失败重试、权限拒绝用例）。

### 7.9 本轮验收摘记（2026-10-31）

| 能力 | 入口 | 备注 |
|---|---|---|
| 依赖字段迁移 | `/bi/datasource/field-migrations` | suggest/apply/rollback；结构替换钩子已接 SnapshotImport |
| 管道生产运行 UI | `bi/pipeline` | 生产运行 + 调度对话框；运行历史区分 PREVIEW/PRODUCTION |
| 装载运维 | `bi/warehouse` + `/bi/warehouse/loads/ops` | 分区/`parallelSlices`/Doris·StarRocks 目标说明 |
| 对象存储入仓 | `/bi/warehouse/object-loads` | S3/OSS/MinIO CSV/Parquet→CH ODS（≤50 对象、≤5MB；S3-07） |
| CDC 编排 | `/bi/warehouse/cdc-jobs` | 外部 Debezium staging 定时 FULL/INCREMENTAL |
| 复合指标 | `kind=composite` | 2～8 已发布标量 + 四则运算表达式求值 |
| Data API | `bi/data-api` + `/portal`/`openapi` | S2 生命周期 + S3-03 门户/契约/调用方/多版本 |
| 目录叙事 | `GET /bi/catalog/narrative` | 区分平台资产目录与 JDBC 建模目录 |
| 凭据 GCM/版本 | `encg:` / `encgv:{n}:`；key-status/rotate | S3-05 LOCAL_CONFIG；外部 KMS 仍待 |
| 仓审计保留 | `/bi/warehouse/audit/retention` | V48 `policy_key=warehouse` |
| 统一任务池 | `/bi/tasks?sort=priority` | S3-01 装载/对象/CDC 聚合视图 |
| 字段标准 | `/bi/field-standards` | S3-02 字典 + 只读落标 |
| 运维影响包 | `.../assets/{id}/impact-pack` | S3-04 |
| 备份演练 | `/bi/ops/backup-drills` | S3-06；见 BACKUP-RESTORE-DRILL.md |
| Doris/SR MERGE | `/bi/warehouse/loads` | S3-08；管道默认仍 CH |

仍明确未纳入本轮：Iceberg/Hive 湖表 POC、分钟级近实时物化、连接器插件生命周期、外部 KMS/HSM、独立部署评估。S3 MVP 证据见 `S3-ACCEPTANCE-RECORD.md`。
