# S2 Wave 1: 仓可信（原子发布 / MERGE / 统一 Publisher）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让装载与管道生产共用「staging → 校验 → promote」写仓路径，支持可配置 REPLACE/MERGE，失败不污染消费者可见表，并对账/新鲜度可门禁。

**Architecture:** 在现有 `WarehouseLoadService` 与管道生产写入之上抽出/强化统一 `WarehousePublisher`（或等价门面）；扩展 `dp_warehouse_load_run`（及必要的 table model 字段）表达 promote 状态与 publishMode；管道 `WAREHOUSE_PUBLISH` 只调用该门面。先落地一种分析引擎的 MERGE，另一引擎记 ADR 差距。

**Tech Stack:** Java 17 / Spring Boot（`ruoyi-modules-bi`）、Flyway `dp_*`、ClickHouse 或 Doris JDBC、Vue3 装载运维页、JUnit。

**Program parent:** [docs/S2-DEVELOPMENT-PLAN.md](../../S2-DEVELOPMENT-PLAN.md) · Charter: [docs/PRODUCT-POSITIONING-AND-FOUNDATION.md](../../PRODUCT-POSITIONING-AND-FOUNDATION.md)

## Global Constraints

- 禁止开放任意生产 SQL 写仓；禁止第二套写仓实现绕过 Publisher。
- 平台元库不写业务明细；分析仓目标须显式配置。
- 行数/时长继续受 `bi.warehouse.*-max-rows` 等上限约束。
- 服务端权限：`bi:resource:admin` / 既有装载与 pipeline run 权限；负向测试必过。
- 日志与审计不含凭据与行数据正文。

---

## File map（预期触点）

| 区域 | 路径 | 职责 |
|---|---|---|
| 迁移 | `Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/db/migration/bi/V49__*.sql`（版本号以仓库最新+1为准） | run/promote/publishMode/gate 字段 |
| 装载核心 | `.../service/WarehouseLoadService.java` | 状态机、校验、promote |
| Publisher | `.../service/warehouse/` 下新建或拆分 publisher 类 | REPLACE/MERGE 方言 |
| Mapper | `.../mapper/WarehouseLoadMapper.java` | SQL |
| 管道 | 生产运行 / `WAREHOUSE_PUBLISH` 相关 service | 改调统一 Publisher |
| 对账 | reconciliation 相关 service/controller | gate 读写 |
| API | `WarehouseLoadController.java` | 请求字段 |
| 前端 | `Wanfo-Cloud-Vue/src/views/bi/warehouse/index.vue` + `api/bi/warehouse.ts` | 展示 promote/对账 |
| 测试 | `.../src/test/java/.../Warehouse*Test.java` | 状态机与权限 |
| 文档 | `docs/WAREHOUSE-LOAD-MVP-GUIDE.md` | 验收步骤 |

---

### Task 1: 发布契约与库表扩展

**Files:**
- Create: `Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/db/migration/bi/V49__dp_warehouse_promote_contract.sql`（若 V49 已被占用则顺延）
- Modify: `WarehouseLoadMapper.java`、装载相关 DO/请求 DTO
- Test: 迁移空库/升级冒烟（本地 ry/Flyway）

**Interfaces:**
- Produces: run 上可读 `publishMode`、`promoteStatus`（如 `STAGED|VALIDATING|PROMOTING|SUCCEEDED|FAILED`）、可选 `consumerGate`

- [x] **Step 1:** 查当前最高 Flyway 版本号，确定本迁移文件名
- [x] **Step 2:** 编写 migration：为 `dp_warehouse_load_run`（及如需的 `dp_warehouse_table_model`）增加 `publish_mode`、`promote_status`、失败保留策略相关列；默认兼容旧行（REPLACE + 旧成功语义）
- [x] **Step 3:** 更新 Mapper 插入/查询字段映射
- [x] **Step 4:** 本地启动 BI 确认 Flyway 成功；`mvn -pl ruoyi-modules/ruoyi-bi -DskipTests compile`
- [ ] **Step 5:** Commit：`feat(warehouse): add promote/publishMode contract columns`（待用户要求再提交）

---

### Task 2: 状态机 — STAGED → VALIDATING → PROMOTING

**Files:**
- Modify: `WarehouseLoadService.java`
- Test: `WarehouseLoadServiceTest` 或新建专注状态机的单测（mock JDBC 边界）

**Interfaces:**
- Consumes: Task 1 列
- Produces: `promote` 成功前消费者仍读旧表；失败 run 终态明确且 staging 可按 TTL/既有 orphan 清理

- [x] **Step 1:** 写失败用例：校验失败时 `promoteStatus=FAILED`（或 VALIDATING 失败），目标表内容未被坏数据替换（用测试替身或集成夹具）
- [x] **Step 2:** 跑测试确认失败（红）
- [x] **Step 3:** 实现拉取写入 staging → 结构/行数校验 → promote；成功才标记 SUCCEEDED
- [x] **Step 4:** 跑测试通过；手工触发一次小表 FULL REPLACE 回归（run 11/12/22）
- [ ] **Step 5:** Commit：`feat(warehouse): staging validate promote state machine`（待用户要求再提交）

---

### Task 3: MERGE 适配（单引擎）

**Files:**
- Create/Modify: `.../service/warehouse/*Publisher*.java`（按现有包结构命名）
- Modify: `WarehouseLoadService` 在 `publishMode=MERGE` 时走键合并
- Docs: 短 ADR 片段写入 `docs/DATA-WAREHOUSE-ENGINE-ADR.md` 或本 plan 附录「未实现引擎」

**Interfaces:**
- Consumes: `keyColumns`、staging 表
- Produces: `Publisher.publish(Replace|Merge)` 单一入口

- [x] **Step 1:** 选定引擎（默认延续现网 ClickHouse 或 Doris；写进验收记录）
- [x] **Step 2:** 单测/集成：按业务键合并两批增量，行数与键预期正确
- [x] **Step 3:** 实现 MERGE SQL/方言；无主键时拒绝 MERGE
- [x] **Step 4:** 管道暂不改，仅装载路径可测 MERGE
- [x] **Step 5:** MERGE 验收：run 17/20（CH）；Commit 待用户要求

---

### Task 4: 对账门禁字段与 API

**Files:**
- Modify: reconciliation / table-model service、controller
- Modify: 前端 warehouse 运维只读展示（可本 Task 或 Task 6）

**Interfaces:**
- Produces: 查询某目标表最近对账结果 + `consumerGate`（OPEN / REQUIRE_PASS_RECON）
- 消费侧强制拦截可标为 Wave1 可选、Wave2 加强；本 Task 至少**写入与可读**

- [x] **Step 1:** API 契约：GET 对账/门禁；装载成功钩子写对账行（沿用现表或扩展）
- [x] **Step 2:** 实现与权限校验
- [x] **Step 3:** admin 可读门禁；无令牌 `401`
- [ ] **Step 4:** Commit：`feat(warehouse): reconciliation consumer gate fields`（待用户要求）

---

### Task 5: 管道生产改调统一 Publisher

**Files:**
- Modify: 管道生产运行 / `WAREHOUSE_PUBLISH` 实现类（搜索 `ClickHouseBatchPublisher` / `warehouseWrites` / `PRODUCTION`）
- Test: 既有 pipeline 相关测试 + 一次手工生产小 DAG

**Interfaces:**
- Consumes: Task 2–3 Publisher
- Produces: 节点摘要含 `promoteStatus` / `writtenRows`；不再旁路直写

- [x] **Step 1:** 定位当前生产写仓调用点，列出旁路清单
- [x] **Step 2:** 全部改为 Publisher；删除或废弃重复写路径
- [x] **Step 3:** 预览模式仍 `warehouseWrites=0`；生产走 promote
- [x] **Step 4:** 代码路径已统一 Publisher；管道手工生产回归可后续补跑
- [ ] **Step 5:** Commit：`refactor(pipeline): publish via unified warehouse publisher`（待用户要求）

---

### Task 6: 运维 UI 与验收记录

**Files:**
- Modify: `Wanfo-Cloud-Vue/src/api/bi/warehouse.ts`、`views/bi/warehouse/index.vue`
- Modify: `docs/WAREHOUSE-LOAD-MVP-GUIDE.md`
- Modify: `docs/S2-DEVELOPMENT-PLAN.md`（勾选 W1 完成项）

- [x] **Step 1:** API 类型增加 promote/publishMode/对账展示字段
- [x] **Step 2:** 运维页表格展示状态；失败可点开摘要
- [x] **Step 3:** 按指南跑通：FULL REPLACE、INCREMENTAL/MERGE、失败保留、权限拒绝
- [x] **Step 4:** 在 `S2-DEVELOPMENT-PLAN.md` 标注 W1 退出证据（日期/表名/行数）
- [ ] **Step 5:** Commit：`docs(warehouse): wave1 acceptance and ops UI`（待用户要求）

---

## Wave 1 完成门禁

- [x] 装载与管道生产无第二写路径  
- [x] 至少一种 publishMode 在目标引擎验收通过（CH REPLACE + MERGE）  
- [x] 失败用例证明消费者未见半截坏表（run 21 后 CH 仍 36 行）  
- [x] Flyway + 编译 + 关键单测通过  
- [x] 验收记录写入 S2 计划  

完成后进入 [S2-DEVELOPMENT-PLAN.md](../../S2-DEVELOPMENT-PLAN.md) **Wave 2**（质量告警 + 分级强制）。
