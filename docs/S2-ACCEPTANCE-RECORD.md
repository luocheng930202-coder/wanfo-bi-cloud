# S2 六项观感验收记录

> 日期：2026-10-31  
> 范围：G1～G6（对齐 [S2-DEVELOPMENT-PLAN.md](S2-DEVELOPMENT-PLAN.md)）  
> 环境：本地 `ry_vue` + BI `9206`；Flyway BI 至 **V53**

## 总览

| 观感 | 结论 | 主证据 |
|---|---|---|
| G1 指标口径 | **通过** | 指标集 / 发布审批 / `consumer-context` |
| G2 目录申请 | **通过** | `dp_access_request` + grant `expires_at` + 调度回收 |
| G3 仓可信 | **通过** | W1 合成源 E2E（REPLACE/MERGE/失败保留） |
| G4 质量门禁 | **通过** | 告警去重升级 + PRODUCTION QUALITY_GATE |
| G5 敏感强制 | **通过** | 分级继承 + Data API `policyMode=ENFORCED` |
| G6 Data API | **通过** | Key/配额/审计既有 + V53 废弃/sunset/OpenAPI |

负向原则：未授权返回业务拒绝；撤权后即时不可见/不可调；失败不静默标成功。

---

## G1 — 指标可统一、可追溯、消费侧可见口径

| 用例 | 结果 | 说明 |
|---|---|---|
| 指标集 CRUD + ACL | ✅ | `/bi/metric-sets`；`metric_set` 资源授权 |
| 草稿→提交审批→通过冻结 | ✅ | `POST /bi/metric/{id}/publish` 仅建 PENDING；`publish-requests/{id}/decide` 后冻结 |
| 消费侧口径 | ✅ | `GET /bi/metric/{id}/consumer-context`（定义/负责人/术语链/pinNote） |
| 无权限负向 | ✅ | 无 `view` 的指标集/指标不可见 |

前端：`/bi-admin/metric`、`/bi-admin/metric-set`。

---

## G2 — 目录可搜、可申请、授权可到期

| 用例 | 结果 | 说明 |
|---|---|---|
| 目录搜索可见资产 | ✅ | `/bi/catalog/assets`（CONNECTOR / BI_RESOURCE） |
| 申请→审批→grant | ✅ | `/bi/access-requests`；批准写 `bi_resource_grant.expires_at` |
| 到期回收 | ✅ | `@Scheduled` 删过期 grant + 申请标 EXPIRED |
| 深链 | ✅ | `GET /bi/catalog/assets/{id}/consume-links` → 申请/分析/生成 API |
| 撤权即时 | ✅ | revoke grant 后 `allowed` 立即 false（沿用既有 ResourceGrant） |

前端：`/bi-admin/access-requests`、目录详情 CTA。

---

## G3 — 入仓可重跑、失败不污染

| 用例 | 结果 | 说明 |
|---|---|---|
| REPLACE 发布 | ✅ | 合成源 E2E：目标 8 行（见计划 W1 退出证据） |
| MERGE 增量 | ✅ | 目标 36 行；PK=`tenant_id+order_id+line_no` |
| 失败保留旧表 | ✅ | 人为失败后可见数据不污染；run=`FAILED` |
| 对账/门禁 | ✅ | `consumer-gate` API + 运维页 promote 状态 |
| 管道走 Publisher | ✅ | `WAREHOUSE_PUBLISH` 统一 facade |

迁移：`V49`；引擎：ClickHouse（Doris 差距见 ADR）。

---

## G4 — 质量可拦截生产 + 告警升级

| 用例 | 结果 | 说明 |
|---|---|---|
| 告警窗口去重 | ✅ | `dp_quality_alert_dedupe` |
| SLA 升级 | ✅ | 逾期 L1 站内信 |
| 复验闭环 | ✅ | `FIXED_PENDING`→PASS 写 `verify_run_id`→`RESOLVED` |
| PRODUCTION 门禁 | ✅ | QUALITY_GATE 默认阻断；可配 `previewPolicy`/`blockOnFailure` |

---

## G5 — 敏感策略一致

| 用例 | 结果 | 说明 |
|---|---|---|
| 分级模型 + 血缘继承 | ✅ | `V51` + 重建继承 |
| 目录预览 MASK/DENY | ✅ | CONNECTOR 列分级 |
| Data API 强制 | ✅ | `DatasetDataPolicy.runEnforced`；禁用 owner-admin 明文旁路 |
| 查询/导出路径 | ✅ | 与 DataPolicy 同源方向（W2 已挂接） |

---

## G6 — 受控 Data API（版本/废弃）

| 用例 | 结果 | 说明 |
|---|---|---|
| Key / 配额 / 审计 | ✅ | 既有 `wfa_` Key、窗口配额、`dp_data_api_call` |
| 契约版本字段 | ✅ | `api_version`（V53） |
| 废弃→sunset→停用 | ✅ | `POST .../deprecate`；过期拒绝 `API_SUNSET`；响应带 `deprecation` |
| OpenAPI 说明 | ✅ | `GET /bi/data-apis/{id}/openapi` 含 `lifecycle` / `deprecated` |
| 不重做 Key 体系 | ✅ | 轮换/撤销逻辑未改 |

迁移：**V53**。

---

## 遗留与非阻塞项

- 看板组件内嵌口径抽屉可再复用 `consumer-context`（指标页已可用）。
- Data API 契约自动化回归（契约测试套件）划入 **S3 API 版本门户**。
- KMS、统一任务池、数据标准 MVP 见 S3 backlog，不阻塞 S2 退出。

## 结论

**S2 六项观感达到退出标准**：可对外表述为「带可信入仓与指标/资产治理的企业 BI」。主航道可转入定位文档 **S3** 可选包装项，或按业务触发扩容。
