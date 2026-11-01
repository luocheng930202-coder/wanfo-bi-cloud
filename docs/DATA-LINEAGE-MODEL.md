# 数据血缘模型与图谱（DP-023 / DP-024）

## 本期内容

- `dp_lineage_edge` 保存每条当前关系的来源资产、目标资产、关系层级/类型、来源方式、置信度、两端资产版本、脱敏证据摘要及状态。
- `dp_lineage_edge_revision` 为创建、更新、失效、撤销和重算留不可变快照。
- `edge_key` 按源资产、目标资产、层级和关系类型计算 SHA-256，保证同一逻辑关系重跑幂等。
- 每条边必须引用存在且 ACTIVE 的资产；提供版本时必须属于对应端点。数据库外键阻止孤儿资产/版本，服务层同时校验端点类型和 TABLE/COLUMN 粒度。
- 来源方式包括 `AUTO_SQL`、`PIPELINE`、`MANUAL`、`CATALOG`、`RESOURCE_BINDING`，置信度范围为 0～1。证据摘要仅保存小型 JSON 元数据，拒绝凭据关键词和过长内容；不得写入完整 SQL、连接串或业务行。
- 失效可设为 `STALE` 或终态 `REVOKED`。重算绑定当前源/目标资产版本、更新置信度/证据，并写新修订。撤销边不能被同一逻辑键静默复活。

## 管理 API

- `POST /bi/catalog/lineage/edges`：创建或幂等更新一条关系。
- `GET /bi/catalog/lineage/edges?assetId={id}&activeOnly={true|false}`：查看某个资产关联的最多 500 条边。
- `POST /bi/catalog/lineage/edges/{id}/invalidate`，请求体 `{"status":"STALE"}` 或 `{"status":"REVOKED"}`。
- `POST /bi/catalog/lineage/edges/{id}/recompute`：将一条未撤销关系重绑到两端最新元数据版本。
- `GET /bi/catalog/lineage/edges/{id}/revisions`：查看最近 100 个修订快照。
- 本节所列管理接口限 `bi:resource:admin`；DP-024 的图谱查询另外按功能权限和每个资产端点的资源授权过滤。

## DP-024 核心采集与图谱

- `POST /bi/catalog/lineage/rebuild`：管理员幂等重建目录可推导关系。来源包括 CONNECTOR 的数据源→Schema→表层级、BI 数据源→数据集/接口快照引用、物理表→数据集、数据集→图表/指标、图表/数据集→看板绑定。目录不唯一匹配、端点无当前版本或资产不活跃时跳过，缺失的旧自动边标记为 `STALE`。
- `GET /bi/catalog/lineage/graph/{assetId}?direction=both&depth=3&includeStale=false`：按端点授权过滤后查询。方向可为 `both`、`upstream` 或 `downstream`；默认深度 3，最大 6，硬限制 200 个节点和 500 条边。超出扫描或输出限制时返回 `truncated=true`。响应不包含边证据、连接配置或业务数据。
- 数据资产目录的资产详情行增加“血缘”入口，可选上下游方向、遍历深度和是否包括失效边。
- 图谱接口要求数据源建模功能权限，并逐端点复核 BI 资源功能权限与资源授权；任一端点不可见时整条边不返回，以避免通过关系推断其他未授权资源。
- 自动重建目前仅推导能从目录层级与已存 BI 引用安全取得的表级关系；SQL 解析、发布流程 DAG 边和自动定时重建仍未实现。

## 验收状态与限制

V38 migration、服务/API、权限过滤图查询及目录页面已编写并在本机应用。2026-10-30 管理员目录 UI 完成一次正向重建：扫描 218 个资产、发现 226 个候选关系、观察到 119 条有效边；查询 PostgreSQL `sales_demo` 时显示 5 个节点和 4 条关系。为闭环管理操作，资产目录新增管理员“重建血缘”按钮，执行结果反馈统计并写入统一审计。后端编译、BI 前端类型检查（自有诊断 0）和生产构建通过。仍需验证重复重建幂等、无权限端点过滤、方向/深度与失效边行为，以及 DP-023 的版本和修订历史场景。自动 SQL 解析、已发布流程自动生成边及定时重建属于后续采集器工作，当前不能描述为已自动发现完整血缘。
