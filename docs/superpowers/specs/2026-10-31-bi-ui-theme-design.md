# BI `/bi-admin` 三套可切换 UI 主题 — 设计说明

**日期**：2026-10-31  
**状态**：已实现（P0–P3 内联落地；空态插画仍可选延后）  
**范围**：`Wanfo-Cloud-Vue` 下 `/bi-admin/*` 壳与业务页视觉；不含 RuoYi `/index` 管理后台重肤。

## 1. 目标

在已落地的「分析 / 数据中心」双域壳之上，提供 **三套可切换外观预设**，统一列表/工作台/编辑器的页面骨架与 CSS 令牌，避免每模块各自硬编码颜色与圆角。

成功标准：

- 用户可在工作台切换主题，刷新后保持。
- 切换后壳层、工作台、已适配 List 页的主色/表面色/边框/圆角/阴影一致。
- 不改业务 API、权限与路由逻辑。

## 2. 决策摘要

| 项 | 选择 |
|---|---|
| 机制 | CSS 主题令牌（方案 1） |
| 预设 | `restrained`（A 克制专业，默认）、`saas`（B 明亮产品感）、`ops`（C 深色运营风） |
| 切换入口 | **仅工作台**「外观设置」区（三预览卡） |
| 持久化 | `localStorage` 键 `bi-ui-theme`；不同步服务端 |
| 挂载 | `BiWorkspaceLayout` 根：`data-bi-theme="…"` |
| 插画 | 主题预览用生成图；空态中性图按需，不做每模块海报 |

预览资产（已就绪）：

- 文档对照：`docs/ui-style-previews/{A-restrained,B-bright-saas,C-dark-ops}.jpg`
- 前端引用：`Wanfo-Cloud-Vue/src/assets/bi/themes/{restrained,saas,ops}.jpg`

## 3. 令牌契约

在 `.bi-workspace`（及 `BiTheme.scss`）中定义同一组变量；三套预设只改取值：

**色**

- `--bi-background`、`--bi-surface`、`--bi-text`、`--bi-muted`、`--bi-border`
- `--el-color-primary` 及 light/dark 阶（与现壳一致）

**形与密度**

- `--bi-radius-sm`、`--bi-radius-lg`、`--bi-shadow`
- `--bi-nav-item-height`、页面内容区水平/垂直 padding 档（可映射为 `--bi-page-pad-x/y`）

**预设观感（实现时对齐预览图，不要求像素级）**

| 键 | 观感 |
|---|---|
| `restrained` | 浅灰蓝底、小圆角、弱阴影、侧栏偏紧凑 |
| `saas` | 更白更透气、大圆角、卡片阴影略强 |
| `ops` | 深色底、青蓝主色、状态色更醒目、表格略密 |

非法或缺失值 → 回退 `restrained`。

与 `html.dark`：`ops` 自带深色表面。BI 壳内一律以 `data-bi-theme` 令牌为准；若用户同时打开全局 dark，不另维护第四套，仅保证 `restrained`/`saas` 在全局 dark 下仍可读（沿用现有 `html.dark .bi-workspace` 回退，或在 P1 收敛为「选 ops ≈ 深色」）。

## 4. 运行时行为

1. Layout 挂载时读 `localStorage.bi-ui-theme`，设 `data-bi-theme`。
2. 工作台外观卡点击 → 写 storage → 更新 Layout 上的 theme（通过提供的 `setBiUiTheme` 小模块或 `provide/inject` / 自定义事件；推荐单一 `src/utils/biUiTheme.ts`：`get/set/subscribe`）。
3. 切换不强制跳转路由；当前页立即换肤。
4. **不**在顶栏、管理后台系统设置放入口（本轮）。

## 5. 页面骨架

三型页面，骨架统一、主题只换皮：

| 型 | 场景 | 结构 |
|---|---|---|
| Hub | 工作台 | 左：身份/快捷/外观设置；右：待办 → 模板 → 最近 |
| List | 目录、源、集、任务… | 页头 → 筛选条 → 表/卡 → 分页 |
| Editor | 看板/图表/管道画布 | `workspaceEditor` 全屏；只继承色与边框 |

约定类名（P0 可先写在 `BiTheme.scss`，P2 再抽小组件）：

- `.bi-page`、`.bi-page-header`、`.bi-filter-bar`、`.bi-panel`、`.bi-empty`

业务页减少硬编码色值，优先令牌 + Element Plus。

## 6. 插画与空态

- 外观设置：三张主题预览图 + 选中主色描边。
- List/Hub 空态：`el-empty`；中性插画浅/深各一（P3 按需生成），不为三主题各做三套。
- 不做模块头图横幅、侧栏大装饰图。

## 7. 落地批次

| 批次 | 内容 |
|---|---|
| **P0** | `biUiTheme.ts` + `BiTheme.scss` 三套变量 + Layout `data-bi-theme` + 工作台外观设置 UI |
| **P1** | 工作台 Hub 去掉硬编码色，全面吃令牌 |
| **P2** | List 高频：目录、任务中心、数据源、数据集、管道、装载 |
| **P3** | 其余 List + Editor 微调；可选空态插画 |

明确不做：服务端存主题、按角色强制主题、RuoYi `/index` 换肤、为每模块出三套完整独立视觉稿。

## 8. 文档与验收

- 原则 SoT：实现后在 `docs/SYSTEM-DESIGN.md` §1.5 / §16.3 记一行主题能力 + §16.4 变更日志。
- 手工验收：三主题切换、刷新保持、编辑器全屏仍可读、`ops` 下表格/输入对比度可接受、预览图不显著拖慢工作台。

## 9. 实现入口文件（预期）

- `Wanfo-Cloud-Vue/src/utils/biUiTheme.ts`（新建）
- `Wanfo-Cloud-Vue/src/views/bi/components/BiTheme.scss`（扩展）
- `Wanfo-Cloud-Vue/src/layout/components/BiWorkspaceLayout.vue`（绑定 attribute）
- `Wanfo-Cloud-Vue/src/views/bi/workbench/index.vue`（外观设置）
- `Wanfo-Cloud-Vue/src/assets/bi/themes/*.jpg`

---

**下一步**：用户审阅本 spec 无异议后，再编写实现计划（writing-plans）并按 P0→P3 实施。
