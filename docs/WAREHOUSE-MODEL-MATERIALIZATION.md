# 零售模型物化 MVP 使用说明

> 功能状态：`retail-v1` 月度 DWS 汇总的受限手动刷新 API/UI 已实现；真实 DWD/DWS 目标数据联调待完成。  
> 关联：DP-038、DP-039；[模型规范](DATA-WAREHOUSE-MODELING-STANDARD.md)

## 前置条件

1. 在隔离的开发/验收 ClickHouse 目标上应用 `deploy/clickhouse/bootstrap-layers.sql` 与 `deploy/clickhouse/models/retail/001_retail_model_v1.sql`。
2. 在 BI 中选择连接 `wanfo_wh_ods` 的 ClickHouse 数据源。该连接身份还必须能读取 DWD 事实和维度、创建/替换 DWS 月汇总及清理 DWS 私有暂存表；请勿给普通 BI 只读角色此权限。
3. 将兼容 `retail-v1` 的订单明细、组织历史维度和商品历史维度装载到 `wanfo_wh_dwd`。要求业务键非空唯一、维度对每条明细在事件时间恰好匹配一行。当前手动入仓 UI 只写 ODS；DWD 加工数据准备尚待后续加工链路完成。

## 本地验收夹具

本地合成源由 `deploy/postgres/retail-acceptance-fixture.sql` 创建在 PostgreSQL `analytics` schema，表名为 `dp_acceptance_retail_org`、`dp_acceptance_retail_product`、`dp_acceptance_retail_order_line`。脚本可重复执行，只对自有合成表按业务键 upsert，不读取或改写既有演示表。ODS 显式 schema 见 `deploy/clickhouse/models/retail/003_retail-acceptance-ods.sql`；`004_retail-acceptance-materialize.sql` 只用于本地合成数据的数据库级校验与汇总发布。

在 BI 批量入仓页分别选择 PostgreSQL 源和专用 ClickHouse ODS 目标，使用全量模式将三张源表写到 ODS 同名表；每次源/目标都必须保持当前管理员授权。装载成功后，在同一个本地验收 ClickHouse 上执行 `deploy/clickhouse/models/retail/002_retail-acceptance-transform.sql`，把缺失业务键映射到 DWD。此脚本只插入缺失键，不删除、覆盖数据。随后从数据资产目录运行“刷新零售月汇总”，检查运行记录与 DP-040 对账结果。

截至 2026-10-30，夹具已在本机 PostgreSQL 生成 8 个组织版本节点、4 个商品和 36 条订单明细；BI 已登记 ODS ClickHouse 数据源 ID 161。ODS、DWD 数据库级计数均为 8/4/36；以 `bi_wh_acceptance` 执行固定模型汇总后，DWS 有 17 个分组、销售额 3038、净数量 64，维度匹配、唯一键与销售额/数量全量核对通过，Atomic EXCHANGE 后无暂存表残留。该次数据载入通过本地容器 SQL/CSV 操作完成；直连 BI 模块 API 返回“未登录”，因此尚未验收应用 `dp_warehouse_load_run` 及 `dp_warehouse_reconciliation` 运行记录。继续验收时应在登录态下从数据资产目录分别执行三张表全量入仓和“刷新零售月汇总”，不要把本次数据库级验证记为应用调度验收。不要将现有只读连接改为写连接，也不要把管理员账号配置成应用连接。

## 手动刷新

数据资产目录 → 批量入仓 → 选择 ClickHouse ODS 数据源 → **刷新零售月汇总**。系统只运行仓库内固定的 `retail-v1` 聚合定义，不接收客户端 SQL、源表或目标表名。刷新写入唯一 staging 表，校验明细键、组织/商品维度时间匹配、汇总键唯一，以及销售额/净数量全量对账后，通过 ClickHouse Atomic `EXCHANGE TABLES` 发布。失败时保留先前 DWS 汇总。

运行记录保存在现有 `dp_warehouse_load_run`，模式显示为“模型”。同一 DWS 表使用 MySQL 命名锁跨 BI 实例互斥。服务重启恢复会在目标锁保护下清理中断运行的模型暂存表。V31 新增 `dp_warehouse_reconciliation`，每次成功物化保存来源明细行数、目标汇总行数、销售额/净数量全量对账值、来源最新更新时间和计算的新鲜度秒数；BI 页可查看最近一次结果。源事实行数与汇总组行数是不同粒度，不要求两个行数相等；金额和净数量必须全量一致。

## 结果口径与限制

- 粒度：月份 × 租户 × 组织 × 组织地理代码 × 渠道 × 商品分类。
- 月份从订单 `order_date` 计算；汇总销售额和净数量必须与有效 DWD 明细完全一致。独立订单数按每个汇总粒度使用精确 `uniqExact(order_id)`。
- 物理删除不会自动同步；只按 `is_deleted=1` 过滤来源明确标记的软删除行。
- 当前为手动全量重建单张 DWS 汇总，不包含调度、分区增量发布、指标质量门禁或可视化 DAG。没有实际连接器数据和权限验收前，不宣称模型数据已生产可用。
