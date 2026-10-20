# DataEase 参考项目体验与实施建议

日期：2026-10-16。用途：后续 UI 和功能开发依据；本轮完成调研及排期补充，未修改运行代码或参考系统业务数据。

## 参考范围与证据

- 本地运行入口：http://localhost:8100/#/workbranch/index 。使用本次提供的账号登录，凭据不保存到文档。
- 本地源码：`D:/bi-tools/dataease`；根 AGENTS.md 明确为 V3。运行实例的构建版本未核验，不假定它与本地源码逐提交一致。
- 官方产品说明：https://docs.fit2cloud.com/dataease/ ，数据集说明：https://docs.fit2cloud.com/dataease/user_manual/dataset_description/ ，企业功能说明：https://docs.fit2cloud.com/dataease/faq/enterprise_faq/ 。检索摘要支持工作台分层、关联建模和血缘功能分类；本次部分正文抓取失败。
- 用户提供的 `/dataease/xpack/blood_relationship/` 页面抓取失败，不把旧链接的具体行为作为已验证结论。血缘未在本地界面实测。
- 截图为本次实测，位于 `D:/datacenter/.local/bi-reference/`。当前浏览器约 566px 宽，截图用于结构和流程观察，不能代表桌面完整布局或性能结论。

## 实际操作与界面发现

1. **工作台（可用，窄屏存在挤压）**：资源数量、快速创建、模板和收藏/最近使用集中展示，降低首次使用的入口成本。模板区在当前窄视口发生压缩，不能直接照搬其固定布局。

   ![工作台](../../.local/bi-reference/01-workbench.png)

2. **资源目录与看板查看（可用，内容超出窄视口）**：左侧文件夹树、搜索与选中状态，右侧统一查看/预览/编辑入口；官方茶饮示例提供指标卡、趋势及 Tab 组织方式。树面板需要折叠和独立滚动，查看模式需响应式适配。

   ![看板查看](../../.local/bi-reference/02-dashboard.png)

3. **看板编辑器（可用，仅观察未保存）**：顶部组件工具栏、中央画布、属性面板；全局配置将主题、背景、配色、标题、查询组件和数字格式分组，适合渐进展开。当前窄屏属性区挤占画布，后续实现需要抽屉或分步编辑。

   ![全局属性](../../.local/bi-reference/03-editor.png)

4. **选中图表配置（可用，仅观察）**：属性区按数据/样式/高级拆分，字段按维度和指标分类，配置槽提示必填；更新数据按钮明确区分配置和取数。我们应保留现有键盘/数值编辑入口，并补充显示名称和字段类型。

   ![图表属性](../../.local/bi-reference/04-chart-properties.png)

可访问性观察：当前视口出现横向滚动、属性面板挤压及若干无可读名称的图标按钮；建议新增组件具备文字标签、可见焦点和键盘替代。仅截图与可访问性树不足以证明对比度、完整键盘可达性或读屏合规，本轮未完成这些测试。

## 与现有平台的差距及落点

| 顺序 | 借鉴点 | 当前情况 | 实施方式及验收 |
|---|---|---|---|
| 1 | 统一编辑器布局、选中态和属性分组 | 看板已有 GridStack、数值布局、保存/发布；属性与列表同页 | 拆分资源浏览/编辑/查看状态；顶部操作、组件区、画布、属性区；未保存提示及窄屏抽屉。保留现有版本冲突和消费边界 |
| 2 | 主题与卡片样式 | DashboardDefinition/Publication 尚无样式模型，卡片样式固定 | S7-T04：浅/深主题、背景、卡片边距/圆角/边框、标题、图表色板及数字格式；定义受控 schema、默认值和旧数据兼容；保存重开/发布一致，草稿不影响发布 |
| 3 | 查询组件及明确应用/重置 | 过滤树已有，看板消费只允许发布绑定查询 | S7-T05：文本、枚举、日期/数值范围；发布绑定允许的字段、操作符和目标组件。消费只传控件 key 与类型化值；服务端校验并与固定过滤和使用者行权限 AND 组合；枚举候选值同样授权，不开放任意字段或 QueryModel |
| 4 | 图表点击联动、取消联动 | 尚未支持交互映射 | S6-T32~T36：发布时校验来源维度与目标字段类型；仅传服务端认可的映射 key、选中值，限制值数量/查询并发；可见联动提示、清除按钮，测试越权及脱敏字段 |
| 5 | 目录、最近使用、收藏与快速创建 | 四类管理入口已具备，缺少统一 BI 工作台 | 在上述编辑闭环稳定后完善资源浏览；统计、搜索、收藏和最近记录均按当前主体过滤，不泄漏无权资源 |
| 6 | 引用血缘与变更影响 | 已有图表/数据集引用删除保护及草稿/发布绑定 | 从数据源→数据集→图表→看板生成引用列表，区分草稿/发布，提供删除前影响说明；再做关系图。首期称“资源引用/影响分析”，不宣称 SQL 字段级血缘；不可见节点和名称不得泄漏 |

随后再评估指标卡、Tab/容器、撤销重做、历史回滚、模板和导入导出。关联建模/计算字段属于数据模型专项，不与主题批次混做；导出、分享、外部参数须分别进行权限设计。

## 源码阅读入口

以下均相对 `D:/bi-tools/dataease/core/core-frontend/src/`，只读用于理解职责划分：

- `views/pages/workbranch/index.vue`、`views/workbranch/ShortcutTable.vue`：工作台快速创建及资源入口。
- `components/dashboard/DbCanvasAttr.vue`、`components/dashboard/subject-setting/dashboard-style/`：全局主题、标题、配色、格式等属性分组。
- `views/data-visualization/indexV3.vue`、`views/canvas/DeCanvas.vue`、`store/modules/data-visualization/dvMain.ts`、`snapshot.ts`：编辑器与状态职责入口，后续按具体调用链深入。
- `custom-component/v-query/QueryConditionConfiguration.vue`：查询条件、字段与目标组件配置；已看到权限字段接口和数据集绑定，不能只照抄控件外观。
- `components/visualization/LinkageSet.vue`、`LinkageSetOption.vue`、`api/visualization/linkage.ts`：联动配置与独立接口入口。
- `views/visualized/data/dataset/form/DatasetUnion.vue`、`UnionEdit.vue`：后续关联建模参考入口，本轮未实测该流程。

我们对应入口：`RuoYi-Cloud-Vue/src/views/bi/dashboard/`、`src/api/bi/dashboard.ts`、后端 `DashboardService` / `ChartQueryService`。优先扩展现有定义与发布快照，避免旁路新建一套取数和授权。

## 架构及复用边界

现阶段沿用若依登录、菜单、按钮、角色及部门；BI 核心通过 CurrentActorProvider、CapabilityProvider、PrincipalDirectory 和资源 ACL 工作。主题、筛选、联动及血缘配置不得写死若依 sys_* 主体，后续独立部署由 S11 的身份适配承接。

本地 LICENSE 声明主项目为附加条件的 GPLv3；AGENTS.md 明确 de-xpack 非开源。本次采用界面体验、公开行为和架构思路作为需求依据，自主实现，不复制 X-Pack 实现或专有资源。实际引入第三方代码/资产时再核对具体许可与归属。

本轮没有构建、重启或更改 DataEase，没有保存其示例看板，没有改动我们的业务代码或权限。截图和本报告只代表上述已观察范围。
