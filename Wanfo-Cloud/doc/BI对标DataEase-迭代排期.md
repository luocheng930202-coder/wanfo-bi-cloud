# BI 对标 DataEase — 迭代排期

更新：2026-10-18。参考实例：`http://127.0.0.1:8100/#/workbranch/index`（账号由环境配置，勿写入仓库）。

## 已完成 / 进行中

| 迭代 | 范围 | 状态 |
|------|------|------|
| 1 | BI 工作台：DataEase 式双栏（左：用户+资源统计+快速创建；右：模板中心+最近编辑）、统计含本地模板数；`/bi/workbench/*` | 已实现 |
| 2 | 图表编辑器三栏 + 数据/样式/高级 Tab；`style`/`advanced` 持久化 | 已实现 |
| 3 | 看板设计器属性区 Tab 化（布局/样式/数据/交互）、窄屏 `el-drawer` | 已实现 |
| 4 | 模板创建三步向导、批量统一数据集、`remapPanelConfig` 字段名保留映射 | 已实现 |
| 5 | 透视表（`pivot` + AntV S2）及既有 14+ 种 ECharts 分析图 | 已实现 |
| 6 | 关联建模（`model_json` + JOIN SQL）、计算字段（`ext_formula` + 查询展开） | 已实现 |
| 7 | 行政区地图（`map` + ECharts + `/bi/geo/*` 省级 GeoJSON） | 已实现 |
| 8 | 看板分享链接（`/bi/share/*`）、数据范围 viewer / publisher | 已实现 |
| 9 | 数据填报（`/bi/form/*`，表数据集单行 INSERT + 审计记录） | 已实现 |
| 10 | 本地模板中心 + 模板市场（`/bi/template/market`、内置目录 + 可选 `bi.template-market.catalog-url`） | 已实现 |

## 本期后续（已确认纳入本期，排在 1/2 之后）

| 迭代 | 对标 DE | 内容 |
|------|---------|------|
| 9 | DE-30 | （见上表；批量导入/审批流留后续） |
| 10 | DE-25 | （见上表；远程市场目录可配置，不含 DataEase X-Pack） |

## 部署提示

1. 重新编译部署 `ruoyi-bi`；本地若使用 `ruoyi-nacos-config/application-dev.yml` 且其中 `spring.flyway.enabled: false`，启动 BI 时需加 `--spring.flyway.enabled=true`，或先执行 Flyway 至 V16（含 `V16__bi_data_form`）。
2. 在若依库执行 `ruoyi-modules/ruoyi-bi/deploy/ruoyi-menu.sql` 中工作台菜单段；非超级管理员角色需在「角色管理」勾选「BI 工作台」（`menu_id` 以库内为准）。
3. 前端刷新后从 **BI 资源管理 → BI 工作台** 进入；看板设计器在宽度 ≤980px 时属性区以右侧抽屉展示。
