# Wanfo 数仓建模规范与零售试点模型

> 版本：1.0（试点）  
> 状态：开发模型基线；需在专用 ClickHouse ODS 连接可用后完成数据样例联调  
> 任务：DP-038。与 [物理规划](DATA-WAREHOUSE-PHYSICAL-PLAN.md) 和 [引擎 ADR](DATA-WAREHOUSE-ENGINE-ADR.md) 配套。

## 1. 建模边界

- `wanfo_wh_ods` 保留来源结构和连接器抽取结果；不在 ODS 上改业务口径。
- `wanfo_wh_dwd` 保存清洗后的明细事实及一致维度；`wanfo_wh_dws` 保存共享主题汇总；`wanfo_wh_ads` 才是 BI/API 可发布消费层。
- MySQL 平台库只保存模型版本、作业运行、授权引用和检查点，不保存业务明细。
- 每个模型使用不可变版本号，例如 `retail-v1`。变更粒度、键、组织范围或指标语义必须升版本并列出兼容/回填计划。
- ClickHouse MergeTree 不提供业务唯一约束；作业发布前必须执行唯一键、空值和关系完整性检查，不能依靠后台 merge 或查询时“碰巧去重”。

## 2. 字段与命名标准

| 类别 | 规则 |
|---|---|
| 名称 | 数据库/表/字段用小写 `snake_case`；稳定业务键使用 `*_id`，日期用 `*_date`，UTC 时刻用 `*_at` |
| 类型 | 金额使用明确精度 `Decimal(p,2)`；数量按允许的正负语义使用整数/Decimal；编码以字符串保存，保留前导零 |
| 租户/组织 | 多租户模型要求 `tenant_id` 非空；受组织范围控制的表要求 `org_id` 非空。沿用稳定组织 ID，不用组织名称作关联键 |
| 地理层级 | 使用 `province_code`、`city_code`、`county_code` 和配套名称；代码/名称均不能靠字符串截取推导。层级归属由版本化组织维表维护 |
| 来源追踪 | DWD/DWS 行保留 `source_system`、来源业务键、`source_updated_at`、`ingested_at`、`load_run_id`；下游差异可追到来源和运行 |
| 删除 | 物理删除不得静默等同于软删。能够识别来源删除时以 `is_deleted`/删除事件显式表达；尚无 CDC/tombstone 时须标记“未捕获删除” |
| 空值 | 未知/未提供使用 Nullable 并记录语义；不得把空值随意改为 0、空串或“其他” |

## 3. 零售试点模型（`retail-v1`）

| 模型 | 层 | 行粒度 | 业务键 | 用途 |
|---|---|---|---|---|
| `dim_retail_org` | DWD | 一个租户内组织的一段有效版本 | tenant + org + valid_from | 省/市/县及上下级路径；SCD Type 2 |
| `dim_retail_store` | DWD | 一个门店的一段有效版本 | tenant + store + valid_from | 门店到组织和地理层级的映射；SCD Type 2 |
| `dim_retail_product` | DWD | 一个商品的一段有效版本 | tenant + product + valid_from | 商品分类、名称与价格属性；SCD Type 2 |
| `fact_retail_order_line` | DWD | 一个来源订单明细行 | tenant + order + line_no | 销售额/数量明细；本地 `fact_order` 演示源每行映射为 `line_no=1` |
| `agg_retail_sales_monthly` | DWS | 月 × 租户 × 组织 × 渠道 × 商品分类 | 上述维度组合 | 月销售额、数量、独立订单数，供看板和图表复用 |

### 3.1 事实口径

- `fact_retail_order_line` 一行表示一个订单明细行，而不是一个订单头。多行订单按 `tenant_id + order_id` 计算独立订单数。
- `sales_amount` 是来源明细行金额的 Decimal 汇总；退款或冲销允许负金额，报表须单独展示其业务口径。
- `quantity` 允许负数表示退货时，销量净额为带符号求和；如业务另有“售出件数”口径，单独定义指标，不能复用含糊的 `quantity`。
- 月度汇总以业务时区转换后的 `order_date` 归属月份；建议试点使用来源日期字段，不从 UTC 时间二次猜测日期。
- `order_count` 使用去重订单数；明细源有重复业务键时，入模检查失败而非把重复数据当成额外销售。
- `unit_price` 在数量为 0 时为 NULL；金额和数量均为空时不得按 0 代填。

### 3.2 迟到、变更与历史维度

- 源增量水位选择能随更新推进的 `source_updated_at` 或单调变更序号。重复边界读取按业务键幂等合并；同一水位内的迟到记录需在抽取上限内完整读取。
- 若源仅有创建时间，不能据此声称可捕获后续更新；应保持全量刷新或明确源端 CDC/更新时间字段缺口。
- 订单事实保存来源更新时刻；重放相同运行需得到相同业务结果。物理删除只有在源端提供 tombstone/CDC 或经全量对账识别后才能同步。
- 门店、商品和组织变更使用 SCD Type 2：`valid_from` 含、`valid_to` 不含，当前行 `valid_to IS NULL`。一次版本发布须校验同一业务键有效期不重叠且最多一个当前版本。
- 组织调整时保留历史归属；查询“当时组织”通过事实日期落入维度有效期，查询“当前组织口径”需显式选当前层级映射版本。

### 3.3 与现有零售演示源的差距

当前 `bi_phase_a_test.fact_order` 是图表演示表：每行有订单 ID、日期、门店、商品、渠道、数量和金额，但没有租户、组织编码、币种、更新时刻或删除标志。它适合验证基础金额聚合，不足以验收多租户隔离、组织下钻、迟到更新或删除同步。将其映射到 `retail-v1` 时，`wanfo_demo` 租户和 `line_no=1` 只作为本地合成数据约定；组织代码需由明确的门店组织映射补充。完整验收应创建带这些字段的专用合成 PostgreSQL 源表，不能把演示缺失列默认为生产值。

## 4. 权限与发布门禁

1. 从 ODS 生成 DWD 前，确认源表与目标列映射、行数上限、更新水位、键及组织字段。
2. DWD 发布前校验字段签名、业务键唯一且非空、维表键可解析、金额精度、来源覆盖和删除口径；失败不替换上一版。
3. DWS 发布前对来源与目标的行数、金额、净数量及独立订单数做全量核对；采样结果必须明确标注。
4. 面向 BI 的 ADS 发布前核对 `tenant_id`/`org_id` 过滤。省/市/县下钻只改变授权组织范围，客户端参数不能扩大权限。
5. 只有经授权发布的 ADS 模型供 BI 身份只读访问；ODS/DWD/DWS 不直接授予普通看板用户。

## 5. 文件与执行状态

- 字段字典和表粒度：[`deploy/clickhouse/models/retail/model-v1.yml`](../deploy/clickhouse/models/retail/model-v1.yml)
- 空表 DDL：[`deploy/clickhouse/models/retail/001_retail_model_v1.sql`](../deploy/clickhouse/models/retail/001_retail_model_v1.sql)
- DDL 只创建空表，不加载数据、不建立宽泛账号或授权。首次建模运行、对账和 BI 接入仍属于后续 DP-039～DP-041。
- 本轮未运行 DDL：专用 ODS/DWD/DWS/ADS 数据源身份仍待配置；本地服务可启动不代表数仓目标连接已验收。
