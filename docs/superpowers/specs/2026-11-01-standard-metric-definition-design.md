# 标准指标定义设计（原子 / 派生 / 复合）

日期：2026-11-01  
状态：已批准实施干净切换）

## 1. 目标

将「指标 = 冻结 QueryModel 结果」改为标准业务指标三件套：**定义与查询分离**。

- 指标只描述**口径与计算规则**
- 维度 / 过滤在**查询时**传入（须 ⊆ 允许维度白名单）
- 消费方：指标页预览、按编码取数、Data API

## 2. 非目标

- 独立 Measure / Dimension / Entity 语义层
- 跨数据集带维度的派生 / 复合
- 旧 `scalar` / `table` 自动迁移
- 图表强制绑定指标

## 3. 种类

| kind | 定义 | 查询 |
|------|------|------|
| `atomic` | 数据集 + 度量(field,agg) + fixedFilters + allowedDimensions + 口径字段 | 运行时 dimensions/filters → 编译 QueryModel |
| `derived` | 同数据集 2 个已发布原子 + 别名表达式（`a/b` 等） | 同维契约；一次查询两度量后按行求值 |
| `composite` | 2～8 个已发布原子/派生 + 算术表达式 | **仅标量**（禁止运行时维度） |

`schemaVersion` = **2**。废除 `scalar` / `table`。

## 4. 发布与治理

发布前强制：`businessDefinition`、`grain`、`unit`、`timeZone`（IANA）、`owner`。  
`allowedDimensions` 即业务可用维；消费上下文可将其展示为业务维度说明。  
仍保留：草稿乐观锁、发布审批、`published_json` 冻结、usage 阻塞下线、资源授权。

## 5. 迁移

Flyway `V64__metric_standard_cutover.sql`：**清空**指标及依赖（usage/history/publish requests/set members、Data API 族、field_standard.metric_id）。不映射旧 JSON。

## 6. API 要点

- `POST /bi/metric/preview`：`{ definition, query? }`
- `POST /bi/metric/{id}/data`、`/code/{code}/data`：可选 `MetricQueryRequest`
- Data API 分页：在已发布定义上叠加请求维过滤后 `dataPage`

## 7. 编译

`MetricQueryCompiler`：Definition + MetricQueryRequest → `QueryModel` → 既有 `ChartQueryService`。  
派生：两原子度量同 GROUP BY，结果行上求表达式得到单列。
