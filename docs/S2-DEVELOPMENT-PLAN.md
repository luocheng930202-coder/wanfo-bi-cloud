# S2 开发计划与任务总表

> 版本：1.0  
> 日期：2026-10-31  
> 状态：**当前主航道排期（取代近端对「全量数据中台」旧计划的优先级）**  
> 依据：[PRODUCT-POSITIONING-AND-FOUNDATION.md](PRODUCT-POSITIONING-AND-FOUNDATION.md) §2.2 / §9 S2  
> 详细 Wave1 实施步骤：[superpowers/plans/2026-10-31-s2-wave1-warehouse-trust.md](superpowers/plans/2026-10-31-s2-wave1-warehouse-trust.md)

## 1. 下一步结论

**S2（W1～W4）已于 2026-10-31 关闭**：六项观感见 [S2-ACCEPTANCE-RECORD.md](S2-ACCEPTANCE-RECORD.md)。

近端默认：

1. **不扩中台范围**；S3 MVP 已关闭见 [S3-ACCEPTANCE-RECORD.md](S3-ACCEPTANCE-RECORD.md)；加深项仅按 [S3-BACKLOG.md](S3-BACKLOG.md) 书面触发  
2. 生产加固与业务需求优先挂接 M1～M6，禁止第二套写仓/权限  
3. 旧文档 [DATA-PLATFORM-DEVELOPMENT-PLAN.md](DATA-PLATFORM-DEVELOPMENT-PLAN.md) 仅作历史参考  

清单中的 P2/暂缓项（湖仓全量、实时引擎、模板市场等）仍不进入默认排期。

---

## 2. S2 目标（退出观感）

完成后对外应能诚实表述为：**「带可信入仓与指标/资产治理的企业 BI」**。

| ID | 观感项 | 验收摘要 |
|---|---|---|
| G1 | 指标可统一、可追溯、消费侧可见口径 | 指标集/发布门禁；看板或指标详情可看口径与责任人 |
| G2 | 目录可搜、可申请、授权可到期 | 申请→审批→grant→到期回收；撤权即时生效 |
| G3 | 入仓可重跑、失败不污染、对账/新鲜度可门禁 | staging→校验→promote；失败保留旧可见表；门禁可读 |
| G4 | 质量可拦截生产 + 告警升级 | PRODUCTION 门禁；告警去重升级到 steward/管理员 |
| G5 | 敏感策略在查询/导出/API 一致 | 分级标签继承；样例/导出/API 强制 |
| G6 | 受控 Data API | 已有基础上补版本/sunset 说明与回归；不重做 |

---

## 3. 波次划分与依赖

```text
Wave 1 仓可信 (G3) ──┐
Wave 2 治理强制 (G4+G5) ─┼─→ Wave 3 指标门户+申请 (G1+G2) ─→ Wave 4 加固 (G6+回归)
Wave 1 可与 Wave 2 部分并行（不同模块），但 G5 拦截点需在消费路径统一后验收。
```

| 波次 | 主路径 | 建议工期* | 依赖 |
|---|---|---|---|
| **W1** 仓可信 | M2 | 3–4 周 | 现有装载/管道 Publisher |
| **W2** 质量告警 + 分级强制 | M4、横切安全 | 3–4 周 | 质量 issue/SLA 已有；需统一 DataPolicy |
| **W3** 指标门户 + 目录申请 | M1、M3、M5 | 3–4 周 | 术语/指标/目录已有 |
| **W4** API 版本说明 + S2 总验收 | M5 | 1–2 周 | W1–W3 |

\*按「后端 1～2 + 前端 1」粗算；单人全栈约 ×1.5～2。

---

## 4. 任务总表（Epic → Task）

### Wave 1 — 仓可信（G3）

| Task ID | 任务 | 主路径 | 主要落点 | 优先级 | 状态 |
|---|---|---|---|---|---|
| S2-W1-01 | 统一写仓契约：`publishMode` REPLACE/MERGE、promote 状态机 | M2 | `WarehouseLoadService`、管道生产 Publisher | P0 | ✅ 2026-10-31 |
| S2-W1-02 | Staging → VALIDATING → PROMOTING；失败保留旧可见表 | M2 | load run 表扩展、Flyway | P0 | ✅ 2026-10-31 |
| S2-W1-03 | MERGE 适配器（先 ClickHouse 或 Doris 选一，ADR 记下另一引擎差距） | M2 | warehouse publisher 包 | P0 | ✅ CH；Doris 差距见 ADR |
| S2-W1-04 | 对账结果结构化 + `consumer_gate` / 新鲜度 SLA 字段 | M2 | reconciliation API、table model | P0 | ✅ 字段+gate API |
| S2-W1-05 | 管道 `WAREHOUSE_PUBLISH` 改走统一 Publisher | M6/M2 | pipeline runtime | P0 | ✅ REPLACE via facade |
| S2-W1-06 | 装载运维页展示 promote/对账状态；文档验收用例 | M2 | `bi/warehouse`、指南 | P1 | ✅ UI+指南 |

**Wave 1 退出**：一条真实/合成源增量 MERGE 或等价幂等路径可演示；人为失败后消费者仍读旧数据；管道生产与装载写同一套发布逻辑。  
**退出证据（2026-10-31）**：Flyway `V49`；`WarehousePublisher` 统一装载/对象存储/物化/管道 Maps 写路径；CH `publishIncremental` 作为 MERGE；运维页展示 `publishMode`/`promoteStatus`；消费门禁 `GET /bi/warehouse/table-models/{id}/consumer-gate`。  
**合成源 E2E（源 DS 115 / 目标 CH 161）**：
| 场景 | runId | 目标表 | 结果 |
|---|---|---|---|
| FULL REPLACE | 11→12→22 | `dp_w1_org_replace` | SUCCEEDED / promote=SUCCEEDED / **8** 行；重跑 `targetReplaced=1` |
| SEED+MERGE | 16→17 | `dp_w1_order_line_merge` | REPLACE 36 → MERGE 36，promote=SUCCEEDED |
| MERGE 幂等重跑 | 20 | 同上 | SUCCEEDED / rows=1（水位边界）/ promote=SUCCEEDED |
| 超限失败保留 | 21 | 同上 | FAILED / promote=FAILED；CH 实查仍 **36** 行 |
| 无令牌 | — | — | `code=401` |
| 门禁 | model `w1_gate_demo` | — | `REQUIRE_PASS_RECON` / SLA 1440 |

### Wave 2 — 质量告警 + 分级强制（G4+G5）

| Task ID | 任务 | 主路径 | 主要落点 | 优先级 | 状态 |
|---|---|---|---|---|---|
| S2-W2-01 | 质量告警：窗口去重、升级到 steward/admin、通知无明细 | M4 | quality + 订阅通知通道 | P0 | ✅ V50 + QualityAlertService |
| S2-W2-02 | 「已修复」强制绑定复验成功 run | M4 | quality issue 状态机 | P0 | ✅ FIXED_PENDING→VERIFIED |
| S2-W2-03 | 字段/资产敏感分级模型 + 血缘继承 + 审计覆盖 | 横切 | catalog / classification | P0 | ✅ V51 + Classification* |
| S2-W2-04 | DataPolicy 拦截：查询样例、导出、订阅附件、Data API | M1/M5 | 查询与 data-api 执行路径 | P0 | ✅ 目录预览+DataAPI ENFORCED |
| S2-W2-05 | 管道 PRODUCTION 门禁与 PREVIEW 警告策略可配（若尚未） | M6 | QUALITY_GATE | P1 | ✅ previewPolicy WARN/BLOCK |

**Wave 2 退出**：脱敏账号看不到明文样例；导出/API 同步脱敏或拒绝；质量失败可告警且复验闭环。  
**W2-01/02 说明（2026-10-31）**：`dp_quality_alert_dedupe/log`；检测告警 L0 + SLA 逾期 L1 站内信（无行样例）；`resolve` → `FIXED_PENDING`，同规则后续 PASS 写 `verify_run_id` 并 `RESOLVED`。  
**W2-03/04/05（2026-10-31）**：`V51` 敏感级别 + `dp_asset_classification`；血缘重建后继承；目录预览按 CONNECTOR 列分级 MASK/DENY；Data API `policyMode=ENFORCED`（禁用 owner-admin 明文旁路）；QUALITY_GATE 预览默认 WARN、生产默认阻断，可配 `previewPolicy`/`blockOnFailure`。

### Wave 3 — 指标门户 + 目录申请（G1+G2）

| Task ID | 任务 | 主路径 | 主要落点 | 优先级 |
|---|---|---|---|---|
| S2-W3-01 | 指标集（主题打包）CRUD + 授权可见 | M1/M3 | metric API + 前端 | P0 |
| S2-W3-02 | 指标发布审批（草稿→待审→已发布）与版本钉扎说明 | M1 | metric 状态机 | P0 |
| S2-W3-03 | 消费侧口径：指标详情/看板组件查看定义、责任人、术语链 | M1 | chart/dashboard 或指标页 | P0 |
| S2-W3-04 | 访问申请单：资产 URN、目的、动作、有效期 | M3 | 新 `dp_access_request*` + 授权复用 ResourceGrant | P0 |
| S2-W3-05 | 审批工作台 + 到期回收调度 | M3 | 任务轮询 + 前端 | P0 |
| S2-W3-06 | 目录详情「申请使用 / 去分析 / 生成 API」深链 | M3/M5 | catalog UI | P1 |

**Wave 3 退出**：业务用户能从目录申请到获权用数；指标集可被分析引用；口径可在消费侧打开。  
**W3-01～06（2026-10-31）**：`V52` `dp_metric_set*` / `dp_metric_publish_request` / `dp_access_request` + `bi_resource_grant.expires_at`；指标集 CRUD+ACL；发布改为提交审批、steward 通过后冻结；`GET /bi/metric/{id}/consumer-context`；访问申请审批写有限期 grant + 定时回收；目录 `consume-links` + UI 深链。

### Wave 4 — 加固与总验收（G6）

| Task ID | 任务 | 说明 | 状态 |
|---|---|---|---|
| S2-W4-01 | Data API 版本/废弃字段与 OpenAPI 说明补齐 | 不重做 Key 体系 | ✅ V53 + deprecate/sunset/OpenAPI lifecycle |
| S2-W4-02 | S2 六项观感 E2E 清单执行（含权限负向） | 出验收记录 | ✅ [S2-ACCEPTANCE-RECORD.md](S2-ACCEPTANCE-RECORD.md) |
| S2-W4-03 | 更新路线图勾选与定位文档「现在→S1」状态 | 文档同步 | ✅ 定位/路线图标注 S2 退出 |
| S2-W4-04 | 明确划入 S3 的 backlog（任务池、标准、KMS…） | 防范围回潮 | ✅ [S3-BACKLOG.md](S3-BACKLOG.md) |

**Wave 4 退出（2026-10-31）**：G6 废弃生命周期可用；六项观感验收记录已归档；S3 backlog 书面隔离。

---

## 5. Sprint 建议日历（可按人力缩放）

假设两周 Sprint，后端主力 1～2 人 + 前端 1 人：

| Sprint | 焦点 | 交付 |
|---|---|---|
| S2-0 | 冻结验收数据集、目标引擎、权限账号矩阵 | 环境与用例清单 |
| S2-1～2 | W1-01～05 | 仓发布状态机 + 统一 Publisher |
| S2-3 | W1-06 + W2-01～02 | 运维可见 + 质量告警闭环 |
| S2-4～5 | W2-03～04 | 分级 + 全消费路径强制 |
| S2-6～7 | W3-01～03 | 指标门户 |
| S2-8～9 | W3-04～06 | 申请审批 |
| S2-10 | W4 | 总验收与文档 |

单人全栈可将并行改为串行，总日历约 **16–22 周**；二人并行后端约 **12–16 周**。

---

## 6. 每项任务的通用完成定义（DoD）

- [ ] 服务端权限负向（未授权 / 撤权）通过  
- [ ] Flyway 可空库+升级；无秘密进日志  
- [ ] 失败可恢复或明确终态；不静默标成功  
- [ ] 前端空/错/无权限状态齐全  
- [ ] 挂接主路径 ID 写入 PR/验收记录  
- [ ] 不新增第二套写仓或第二套资产权限模型  

---

## 7. 关闭后动作

1. 以 [S2-ACCEPTANCE-RECORD.md](S2-ACCEPTANCE-RECORD.md) / [S3-ACCEPTANCE-RECORD.md](S3-ACCEPTANCE-RECORD.md) 作为对外/对内退出证据。  
2. 新需求默认对照 [S3-BACKLOG.md](S3-BACKLOG.md) 加深项与定位宪章准入清单；无触发不立项。  
3. 生产环境补跑菜单脚本（指标集/访问申请）与 Flyway **V53**。

---

## 8. 文档索引

| 文档 | 用途 |
|---|---|
| [PRODUCT-POSITIONING-AND-FOUNDATION.md](PRODUCT-POSITIONING-AND-FOUNDATION.md) | 定位与底座 |
| 本文 | S2 任务与排期总表 |
| [superpowers/plans/2026-10-31-s2-wave1-warehouse-trust.md](superpowers/plans/2026-10-31-s2-wave1-warehouse-trust.md) | W1 可执行步骤 |
| [PLATFORM-EXTENSION-ROADMAP.md](PLATFORM-EXTENSION-ROADMAP.md) | 已实现勾选 |
| [WAREHOUSE-LOAD-MVP-GUIDE.md](WAREHOUSE-LOAD-MVP-GUIDE.md) | 装载操作基线 |
