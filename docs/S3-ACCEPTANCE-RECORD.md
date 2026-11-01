# S3 轻量中台验收记录

> 日期：2026-10-31  
> 范围：S3-01～S3-08 MVP（对齐 [S3-BACKLOG.md](S3-BACKLOG.md)、[PRODUCT-POSITIONING-AND-FOUNDATION.md](PRODUCT-POSITIONING-AND-FOUNDATION.md) §9）  
> 环境：本地 `ry_vue` + BI `9206`；Flyway BI 至 **V63**  
> 前置：S2 六项观感已关闭（见 [S2-ACCEPTANCE-RECORD.md](S2-ACCEPTANCE-RECORD.md)）

## 总览

| ID | 主题 | 结论 | 主证据 |
|---|---|---|---|
| S3-01 | 统一任务池 | **通过（MVP）** | `/bi/tasks?sort=priority` + `OBJECT_LOAD`/`CDC_JOB` + 分池摘要 |
| S3-02 | 数据标准 | **通过（MVP）** | V54 `dp_field_standard` + `/compliance` 只读落标 |
| S3-03 | API 版本门户 | **通过（MVP）** | `/portal` + V57 契约门禁 + V58 调用方 + V59 `family_code`/`next-version` |
| S3-04 | 运维影响包 | **通过（MVP）** | `GET .../impact-pack` 跨指标/API/流程/装载/CDC |
| S3-05 | 密钥版本/轮换 | **通过（MVP）** | `encgv:{n}:` + LOCAL_CONFIG 密钥环 + V55 审计 |
| S3-06 | 备份与演练 | **通过（MVP）** | V56 `dp_backup_drill` + readiness + dump 脚本 + runbook |
| S3-07 | Parquet 对象入仓 | **通过（MVP）** | `fileFormat=AUTO\|CSV\|PARQUET`；扁平列 ≤5MB |
| S3-08 | 第二引擎 MERGE | **通过（MVP）** | `MysqlProtocolBatchPublisher` Doris/SR REPLACE+MERGE |

负向原则（延续 S2）：未授权业务拒绝；失败不静默成功；密文/凭据不回显明文。  
边界：本记录证明 **轻量中台 MVP 可包装**，不等于 DataWorks 平替，也不等于生产 HA/KMS/跨机房已验收。

---

## S3-01 — 统一任务池

| 用例 | 结果 | 说明 |
|---|---|---|
| 聚合装载/对象入仓/CDC | ✅ | 无第二套任务状态表；复用既有 run |
| 优先级排序 | ✅ | `GET /bi/tasks?sort=priority` |
| 分池摘要 | ✅ | `summary.byType` / `byLane` |
| 前端运维页 | ✅ | `/bi-admin/task-center` |

仍非本 MVP：抢占式调度器、多租户公平队列、全链路暂停。

---

## S3-02 — 字段标准字典与落标

| 用例 | 结果 | 说明 |
|---|---|---|
| 标准 CRUD / 绑定 | ✅ | `/bi/field-standards`；COLUMN 绑定 |
| 只读合规 | ✅ | `GET .../compliance` 类型族检查 |
| 弱绑术语/指标 | ✅ | 可选 `glossaryTermId` / `metricId` |

迁移：**V54**。仍非本 MVP：写入门禁、值域抽样、批量自动绑定审批。

---

## S3-03 — API 版本门户完整路径

| 用例 | 结果 | 说明 |
|---|---|---|
| 生命周期门户 | ✅ | `GET /bi/data-apis/portal`（状态/sunset/继任/30 日调用） |
| 契约门禁 | ✅ | V57 `contract_schema_json`；`POST .../contract-check`；发布阻断删列/改类型 |
| 调用方名录 | ✅ | V58 `dp_data_api_caller`；Key.`caller_id`；门户 `callerOrgs` |
| 多版本并存 | ✅ | V59 `family_code`；`POST .../next-version`；旧 ACTIVE 可并存 |
| 冒烟 | ✅ | `e2e_api_region` → 草稿 `e2e_api_region_v2`；族内 active=1 / draft=1 |

迁移：**V57～V59**（废弃/sunset 基线仍为 V53）。  
仍非本 MVP：契约金丝雀/调用回放、调用方配额分账、自动 deprecate 编排。

前端：`/bi-admin` Data API（门户 / 契约校验 / 调用方 / 下一版本）。

---

## S3-04 — 运维影响一键包

| 用例 | 结果 | 说明 |
|---|---|---|
| 影响包 API | ✅ | `GET /bi/catalog/assets/{id}/impact-pack` |
| 消费者扇出 | ✅ | 血缘下游 + 数据集→指标/图表/看板/API/流程/装载/CDC |
| 严重度 | ✅ | BLOCKING / REVIEW / COMPATIBLE + 检查清单 |
| 结构变更联动 | ✅ | impact 扩展 metrics/charts/dashboards/dataApis |

仍非本 MVP：写入阻断、自动变更单、跨引擎完备依赖。

---

## S3-05 — 凭据密钥版本与轮换审计

| 用例 | 结果 | 说明 |
|---|---|---|
| 版本信封 | ✅ | `encgv:{n}:`（兼容既有 `encg:`/`enc:` 解密路径） |
| 本地密钥环 | ✅ | `CredentialKeyProvider` LOCAL_CONFIG |
| 状态 / 轮换 | ✅ | `GET .../credentials/key-status`；`POST .../rotate` |
| 审计 | ✅ | V55 指纹元数据与轮换记录；查询不回明文 |

仍非本 MVP：云 KMS/HSM、零接触托管、双写过渡自动化。

---

## S3-06 — 备份与演练

| 用例 | 结果 | 说明 |
|---|---|---|
| 演练登记 | ✅ | V56 `dp_backup_drill`；`/bi/ops/backup-drills` |
| 就绪度 | ✅ | `GET .../readiness`（声明 RPO/RTO、dump 年龄、Flyway） |
| dump 脚本 | ✅ | `scripts/backup-metadata.ps1` |
| Runbook | ✅ | [BACKUP-RESTORE-DRILL.md](BACKUP-RESTORE-DRILL.md)（隔离副本恢复） |

仍非本 MVP：定时备份、仓引擎官方备份联调、跨机房复制；**不自动恢复生产**。

---

## S3-07 — Parquet 对象入仓

| 用例 | 结果 | 说明 |
|---|---|---|
| 格式选择 | ✅ | `fileFormat=AUTO\|CSV\|PARQUET` on `/bi/warehouse/object-loads` |
| 解析 | ✅ | `SnapshotParser.parquet`（内存 InputFile；扁平原始列） |
| 边界 | ✅ | 单对象 ≤5MB / 行数上限；单测覆盖解析与格式匹配 |

仍非本 MVP：嵌套/ORC、>5MB 流式、CH 原生 Parquet 直写。

---

## S3-08 — Doris / StarRocks MERGE

| 用例 | 结果 | 说明 |
|---|---|---|
| 引擎分发 | ✅ | `WarehousePublisher.Engine` → `MysqlProtocolBatchPublisher` |
| REPLACE | ✅ | Doris `REPLACE WITH TABLE`；StarRocks `SWAP`（失败 RENAME 回退） |
| MERGE | ✅ | 与 CH 同契约：水位 + 业务键 staging→校验→promote |
| 能力声明 | ✅ | 连接器 `warehouse-merge` |

仍非本 MVP：管道/对象入仓默认第二引擎、生产多副本 UNIQUE KEY upsert 联调证明。  
管道 Maps / 对象入仓默认引擎仍为 ClickHouse（见 ADR）。

---

## 加深波次（2026-10-31，Flyway **V60**）

| ID | 加深交付 | 结论 |
|---|---|---|
| S3-01 | 分池暂停/软并发 `/bi/tasks/lanes` | ✅ |
| S3-02 | `enforce_gate` + 迁移执行阻断 | ✅ |
| S3-03 | `contract-replay` / `auto-deprecate-siblings` / 调用方配额分账 | ✅ |
| S3-04 | `/change-gate` + 迁移默认 BLOCKING 阻断 | ✅ |
| S3-05 | `EXTERNAL_KMS_STUB` 失败闭合占位 | ✅ |
| S3-06 | dump 文件定时/手动自动登记 | ✅ |
| S3-08 | 对象入仓引擎随目标类型分发 | ✅ |
| S3-07 | RECORD 展平 / ARRAY 文本化 | ✅（流式仍 backlog） |
| S3-03+ | 金丝雀 % 路由（V61） | ✅ |
| S3-02+ | sample-validate 值域抽样 | ✅ |
| S3-08+ | 管道发布多引擎 | ✅ |
| S3-03++ | Key sticky 金丝雀 + 全局调用方日配额 | ✅ |
| S3-04++ | 迁移 overrideBlocking 审计放行 | ✅ |
| S3-02++ | 管道发布落标/值域门禁 | ✅ |
| S3-06++ | dump-now / 可选定时 dump | ✅ |

## 加深波次 W4（2026-10-31，Flyway **V62**）

| ID | 加深交付 | 结论 |
|---|---|---|
| S3-02+++ | JDBC 在线抽样 `/field-standards/online-sample-validate` | ✅ |
| S3-04+++ | 影响包无权消费者脱敏占位 | ✅ |
| S3-03+++ | 发布样本落库 + sample-replay / refresh | ✅ |

## 加深波次 W5（2026-10-31）

| ID | 加深交付 | 结论 |
|---|---|---|
| S3-01++ | 管道生产提交 `assertLaneRunnable(PIPELINE)` | ✅ 冒烟：泳道暂停 → 生产返回「任务分池已暂停：PIPELINE」 |
| 管道 | `WAREHOUSE_PUBLISH` Maps 路径 `APPEND`（CH/Doris/SR） | ✅ 冒烟：同模型两次生产 `publishMode=APPEND`，`pipe_append_w5_*` 行数 4→8 |

## 加深波次 W6（2026-10-31）

| ID | 加深交付 | 结论 |
|---|---|---|
| 管道 | 生产 `SOURCE_DATASET` 走 `boundedExtract`（突破 preview 100） | ✅ 冒烟：`maxRows=500` 生产 `SUCCEEDED`，summary.`rowLimit=500`（此前同参失败于 preview 100） |
| S3-01+++ | `QUALITY` / `CDC_JOB` / `METADATA_SCAN` 泳道门禁 | ✅ 冒烟：`METADATA_SCAN` 暂停 → trigger 返回「任务分池已暂停：METADATA_SCAN」；QUALITY/CDC 同 `assertLaneRunnable` 接入（CDC 调度遇暂停跳过不写失败） |

## 加深波次 W7（2026-10-31，Flyway **V63**）

| ID | 加深交付 | 结论 |
|---|---|---|
| S3-01++++ | 泳道 priority/weight + 全局公平软并发 | ✅ |
| S3-02+++ | `domain_code` + 批量绑定审批计划 | ✅ |
| S3-03++++ | 调用方加权金丝雀 + 调用 sample_json | ✅ |
| S3-04+++ | `dp_change_ticket` + override 须 ticket | ✅ |
| S3-05++ | `CLOUD_KMS_DEV` 可插拔 Provider | ✅ |
| S3-06+++ | `warehouseEngineProbe` | ✅ |
| S3-07++ | 大对象临时文件流式入口；ORC 显式拒绝 | ✅ |
| S3-08++ | 管道 Maps `UPSERT`（mergeKeys） | ✅ |

## 遗留（不阻塞 S3；属 S4+ / 合规触发）

| 主题 | 去向 |
|---|---|
| 抢占式 Worker / 跨实例任务状态机 | S4+ |
| 完整历史调用 payload 无限保留 | S3-03 S4+ |
| 云 KMS SDK / HSM | S3-05 合规触发 |
| CH 官方 BACKUP/RESTORE、跨机房 | S3-06 S4+ |
| ORC 真解析 / 无限流式 | S3-07 S4+ |
| 多副本 UNIQUE、默认引擎配置中心 | S3-08 S4+ |
| 湖仓一体 / 实时流 / DataWorks 全量 | **不对齐** |

## 结论

**S3-01～S3-08 MVP 达到轻量中台包装退出标准**；同日加深波次（V60）覆盖任务分池门禁、落标/影响写入门禁、API 回放与自动废弃、调用方配额、备份自动登记、对象入仓多引擎与 KMS stub。  
仍 **不是** DataWorks 平替；S4+/嵌套 Parquet 等默认不排。
