# ClickHouse 单表入仓 MVP 使用说明

> 功能状态：开发版 API/UI 已实现；本机 PostgreSQL → ClickHouse 管理员 UI 全量发布、同目标重跑及失败保护已验收。  
> 关联：[DP-037 任务状态](DATA-PLATFORM-TASK-LIST.md)、[物理规划](DATA-WAREHOUSE-PHYSICAL-PLAN.md)

## 1. 准备

1. 应用 Flyway `V29__dp_warehouse_load_runs.sql`，创建入仓运行记录表。
2. 在 BI 数据源管理中注册独立的 ClickHouse ODS 目标数据源：类型为 ClickHouse，物理 schema/database 设置为 `wanfo_wh_ods`，连接凭据使用专用写入账号；不要复用源系统只读账号。
3. 源数据源使用只读账号，目标与源必须是不同的数据源记录。
4. 当前直接复制物理表，尚不能把数据集行级策略带入入仓。因此只有同时拥有 `bi:resource:admin` 和两个数据源 `model` 授权的管理员可以发起任务。
5. 本地 ClickHouse 应先运行 `deploy/clickhouse/bootstrap-layers.sql` 初始化逻辑库。ODS 数据库需要 Atomic 引擎以支持原子发布。

## 2. 从界面运行

进入“数据资产目录”并点击“批量入仓”：

- 选择有权的 JDBC 业务源和表；首版不支持 API、文件快照或 ClickHouse 源。
- 选择物理 schema 为 `wanfo_wh_ods` 的 ClickHouse 目标数据源。
- 填写 ODS 目标表名及最大行数（1～100,000）。
- 点击“全量刷新入仓”，确认后任务进入队列；页面显示运行 ID、状态、行数、是否首次发布/原子替换和脱敏失败摘要。

源表按配置的 JDBC schema 范围解析并以只读连接执行 `SELECT`。表名、目标层和字段名由后端验证；最多提取配置行数加 1 用于超限检测。超过上限、类型不支持或源目标结构不兼容时，目标表保持原样。

## 3. API

- `POST /bi/warehouse/loads`：请求字段 `sourceDatasourceId`、`targetDatasourceId`、`sourceTable`、`targetTable`、`maxRows`、`loadMode`（FULL|INCREMENTAL）、可选 `publishMode`（REPLACE|MERGE；缺省时 INCREMENTAL→MERGE、FULL→REPLACE）、MERGE 时的 `watermarkColumn`/`keyColumns`。
- `GET /bi/warehouse/loads/runs?targetDatasourceId={id}`：查看最近 100 次运行（含 `publishMode`、`promoteStatus`）。
- `GET /bi/warehouse/loads/runs/{id}`：轮询运行状态与 promote 阶段。
- `GET /bi/warehouse/loads/ops`：运维概览（活动/孤儿运行）。
- `GET /bi/warehouse/table-models/{id}/consumer-gate?targetDatasourceId=`：表模型消费门禁与最近对账。

所有写仓路径经统一 `WarehousePublisher`（staging→校验→promote）；管道 `WAREHOUSE_PUBLISH` 生产写入同样走该门面。运行记录仅返回脱敏摘要，不记录 URL、账号、密码或业务行内容。触发与列表查询写入数据平台审计。

## 4. 发布方式和限制

数据先批量写入目标库临时表，再核对目标行数。首次运行将临时表重命名为目标表；目标已存在时使用 ClickHouse Atomic 库的 `EXCHANGE TABLES` 交换，再清理旧表。零行结果可以正常发布为空表。首次目标建表按 JDBC 元数据生成 ClickHouse 字段类型；已存在表必须列名、列顺序和类型完全一致，否则拒绝自动迁移。

入仓支持管理员手动全量刷新，以及按水位列和业务键执行的增量合并。增量仅接受非空数字/日期时间水位字段和 1～16 个非空业务键；同一批次业务键重复会被拒绝，已有键由本批次新行替换，并通过 ClickHouse Atomic 表交换发布。检查点在目标发布成功后推进；下次查询会包含检查点边界值以覆盖同水位值的迟到写入，重复行由业务键合并保证幂等。发布后检查点写入失败时可用相同配置安全重试。更新必须推进水位列；物理删除没有 tombstone/CDC，故不会自动同步。每次仍最多读取 10 万行，不含定时调度、分区回补或断点续传。

单实例由单线程队列串行执行；同一 ClickHouse 目标表还通过平台 MySQL `GET_LOCK` 做跨服务实例互斥，连接关闭时锁会释放。服务启动时会检查超过 5 分钟的 `PENDING`/`RUNNING` 记录：恢复流程先获取相同目标锁，再将运行标为 `INTERRUPTED` 并删除该运行 ID 对应的增量暂存/发布表；若锁仍被其他实例持有，则跳过该运行。目标连接不可用时运行仍保持中断状态，日志会提示需要人工检查暂存表。目标结构变化必须先走结构迁移评审，不可通过改名绕过检查。

## 5. 验收步骤（含 S2 Wave1）

1. 配置 PostgreSQL 合成源和独立 ClickHouse ODS 数据源，确认应用能分别测试连接；Flyway 含 `V49__dp_warehouse_promote_contract.sql`。
2. **FULL REPLACE**：选小于上限行数的测试表入仓；`publishMode=REPLACE`，`promoteStatus` 终态 `SUCCEEDED`；对比源/目标行数。
3. **同目标重跑 REPLACE**：确认原子替换、行数/聚合相同。
4. **INCREMENTAL/MERGE**：配置水位列与业务键；二次增量后键合并正确，检查点推进；`publishMode=MERGE`。
5. **失败保留**：人为造成结构不兼容或超限；任务 `FAILED`，消费者仍读旧目标表；`promoteStatus` 非成功。
6. **权限拒绝**：无 `bi:resource:admin` 或无数据源 `model` 授权时拒绝触发。
7. **门禁可读**：对已声明 table-model 调用 `GET .../consumer-gate`，可见 `OPEN`/`REQUIRE_PASS_RECON` 与可选对账行。
8. **管道统一 Publisher**：小 DAG 生产运行 `WAREHOUSE_PUBLISH`，节点摘要含 `promoteStatus`/`writtenRows`；预览模式 `writtenRows=0`。

历史基线：`dp_acceptance_retail_org/product/order_line` 首次 8/4/36 行；结构漂移失败须保留旧表。
