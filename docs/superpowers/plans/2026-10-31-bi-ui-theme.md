# BI 三套可切换 UI 主题 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `/bi-admin` 壳上落地 `restrained | saas | ops` 三套 CSS 令牌主题，仅在工作台提供切换，刷新保持，并分批让 Hub/List/Editor 吃令牌。

**Architecture:** 纯前端：`biUiTheme.ts` 读写 `localStorage.bi-ui-theme` 并广播；`BiWorkspaceLayout` 根节点绑定 `data-bi-theme`；`BiTheme.scss` 用属性选择器覆盖同一组 CSS 变量；业务页逐步去掉硬编码色，改用 `.bi-page` / `.bi-panel` 等骨架类。

**Tech Stack:** Vue 3 + TypeScript、Element Plus、SCSS（现有 `BiTheme.scss` / `BiWorkspaceLayout.vue`）、本地 JPG 预览资产。

**Spec:** `docs/superpowers/specs/2026-10-31-bi-ui-theme-design.md`

## Global Constraints

- 主题键仅允许：`restrained`（默认）、`saas`、`ops`；非法值回退 `restrained`
- 持久化键：`localStorage.bi-ui-theme`；不同步服务端
- 切换入口：仅工作台「外观设置」；不放顶栏、不改 RuoYi `/index`
- 预览图路径：`@/assets/bi/themes/{restrained,saas,ops}.jpg`（已存在则勿重新生成）
- 不改业务 API / 权限 / 路由逻辑
- Commit 步骤：仅当用户在本会话明确要求提交时执行；否则跳过 `git commit`
- 本仓库前端无 vitest：逻辑用 `node -e` 断言核对；UI 用手工清单验收

## File map

| 文件 | 职责 |
|---|---|
| `Wanfo-Cloud-Vue/src/utils/biUiTheme.ts` | get/set/subscribe/normalize |
| `Wanfo-Cloud-Vue/src/views/bi/components/BiTheme.scss` | 三套令牌 + `.bi-page*` 骨架 |
| `Wanfo-Cloud-Vue/src/layout/components/BiWorkspaceLayout.vue` | `data-bi-theme` + 订阅 |
| `Wanfo-Cloud-Vue/src/views/bi/workbench/index.vue` | 外观设置 UI + Hub 去硬编码色 |
| `Wanfo-Cloud-Vue/src/views/bi/{catalog,task-center,datasource,dataset,pipeline,warehouse}/index.vue` | P2 List 令牌化 |
| `docs/SYSTEM-DESIGN.md` | §1.5 / §16.3 / §16.4 记主题能力 |

---

### Task 1: `biUiTheme` 工具模块

**Files:**
- Create: `Wanfo-Cloud-Vue/src/utils/biUiTheme.ts`
- Test: `node -e` 内联断言（无 vitest）

**Interfaces:**
- Consumes: `window.localStorage`
- Produces:
  - `export type BiUiTheme = 'restrained' | 'saas' | 'ops'`
  - `export const BI_UI_THEME_KEY = 'bi-ui-theme'`
  - `export const BI_UI_THEMES: readonly BiUiTheme[]`
  - `export function normalizeBiUiTheme(raw: unknown): BiUiTheme`
  - `export function getBiUiTheme(): BiUiTheme`
  - `export function setBiUiTheme(theme: BiUiTheme): void`
  - `export function subscribeBiUiTheme(listener: (theme: BiUiTheme) => void): () => void`

- [ ] **Step 1: 写规范化断言（期望失败——模块尚不存在）**

在仓库根或任意目录运行（先确认文件不存在）：

```bash
node -e "require('fs').accessSync('Wanfo-Cloud-Vue/src/utils/biUiTheme.ts'); console.log('exists')"
```

Expected: 抛出 `ENOENT`（文件不存在）。

- [ ] **Step 2: 实现 `biUiTheme.ts`**

```ts
export type BiUiTheme = 'restrained' | 'saas' | 'ops'

export const BI_UI_THEME_KEY = 'bi-ui-theme'
export const BI_UI_THEMES = ['restrained', 'saas', 'ops'] as const

const listeners = new Set<(theme: BiUiTheme) => void>()

export function normalizeBiUiTheme(raw: unknown): BiUiTheme {
  return BI_UI_THEMES.includes(raw as BiUiTheme) ? (raw as BiUiTheme) : 'restrained'
}

export function getBiUiTheme(): BiUiTheme {
  try {
    return normalizeBiUiTheme(localStorage.getItem(BI_UI_THEME_KEY))
  } catch {
    return 'restrained'
  }
}

export function setBiUiTheme(theme: BiUiTheme): void {
  const next = normalizeBiUiTheme(theme)
  try {
    localStorage.setItem(BI_UI_THEME_KEY, next)
  } catch {
    /* ignore quota / private mode */
  }
  listeners.forEach((fn) => fn(next))
}

export function subscribeBiUiTheme(listener: (theme: BiUiTheme) => void): () => void {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}
```

- [ ] **Step 3: 用内联逻辑核对 normalize 契约**

```bash
node -e "const IDS=['restrained','saas','ops']; const n=v=>IDS.includes(v)?v:'restrained'; console.assert(n('ops')==='ops'); console.assert(n('saas')==='saas'); console.assert(n('restrained')==='restrained'); console.assert(n('x')==='restrained'); console.assert(n(null)==='restrained'); console.log('normalize ok')"
```

Expected: 打印 `normalize ok`。

- [ ] **Step 4: Commit（仅用户要求时）**

```bash
git add Wanfo-Cloud-Vue/src/utils/biUiTheme.ts
git commit -m "$(cat <<'EOF'
feat(bi): add biUiTheme localStorage helper for UI presets

EOF
)"
```

---

### Task 2: `BiTheme.scss` 三套令牌 + 页面骨架类

**Files:**
- Modify: `Wanfo-Cloud-Vue/src/views/bi/components/BiTheme.scss`
- Note: 基线变量仍写在 `.bi-workspace`；`restrained` 可与基线相同；`saas`/`ops` 用属性选择器覆盖

**Interfaces:**
- Consumes: `data-bi-theme` on `.bi-workspace`
- Produces: CSS vars `--bi-*`、`--bi-page-pad-x/y`、`--bi-nav-item-height`；类 `.bi-page`、`.bi-page-header`、`.bi-filter-bar`、`.bi-panel`、`.bi-empty`

- [ ] **Step 1: 在现有 `.bi-workspace { ... }` 块内追加默认密度令牌（紧挨现有 `--bi-shadow` 一行之后）**

追加：

```scss
  --bi-page-pad-x: 32px;
  --bi-page-pad-y: 24px;
  --bi-nav-item-height: 40px;
```

并在同一文件**末尾**（`html.dark` 规则之后）追加三套覆盖与骨架类——完整内容如下：

```scss
.bi-workspace[data-bi-theme="restrained"] {
  --bi-background: #f6f8fc;
  --bi-surface: #fff;
  --bi-text: #182230;
  --bi-muted: #667085;
  --bi-border: #e6ebf3;
  --bi-soft: #f8fafc;
  --bi-radius-sm: 6px;
  --bi-radius-lg: 10px;
  --bi-shadow: 0 4px 16px #1822300a;
  --bi-page-pad-x: 32px;
  --bi-page-pad-y: 24px;
  --bi-nav-item-height: 40px;
  --el-color-primary: #3563e9;
  --el-color-primary-light-3: #7292f0;
  --el-color-primary-light-5: #9ab1f4;
  --el-color-primary-light-7: #c2d0f8;
  --el-color-primary-light-8: #d7e0fb;
  --el-color-primary-light-9: #edf2ff;
  --el-color-primary-dark-2: #2a4fba;
}

.bi-workspace[data-bi-theme="saas"] {
  --bi-background: #f3f6fb;
  --bi-surface: #ffffff;
  --bi-text: #0f172a;
  --bi-muted: #64748b;
  --bi-border: #e2e8f0;
  --bi-soft: #f8fafc;
  --bi-radius-sm: 10px;
  --bi-radius-lg: 16px;
  --bi-shadow: 0 8px 28px #18223014;
  --bi-page-pad-x: 36px;
  --bi-page-pad-y: 28px;
  --bi-nav-item-height: 44px;
  --el-color-primary: #2563eb;
  --el-color-primary-light-3: #60a5fa;
  --el-color-primary-light-5: #93c5fd;
  --el-color-primary-light-7: #bfdbfe;
  --el-color-primary-light-8: #dbeafe;
  --el-color-primary-light-9: #eff6ff;
  --el-color-primary-dark-2: #1d4ed8;
}

.bi-workspace[data-bi-theme="ops"] {
  --bi-background: #121a27;
  --bi-surface: #1c2432;
  --bi-text: #e8edf5;
  --bi-muted: #a0aec0;
  --bi-border: #303c50;
  --bi-soft: #253044;
  --bi-radius-sm: 6px;
  --bi-radius-lg: 10px;
  --bi-shadow: 0 8px 28px #00000040;
  --bi-page-pad-x: 24px;
  --bi-page-pad-y: 20px;
  --bi-nav-item-height: 38px;
  --el-color-primary: #38bdf8;
  --el-color-primary-light-3: #7dd3fc;
  --el-color-primary-light-5: #bae6fd;
  --el-color-primary-light-7: #e0f2fe;
  --el-color-primary-light-8: #082f49;
  --el-color-primary-light-9: #0c4a6e;
  --el-color-primary-dark-2: #0ea5e9;
}

.bi-workspace {
  .bi-page { padding: var(--bi-page-pad-y) var(--bi-page-pad-x); box-sizing: border-box; }
  .bi-page-header {
    display: flex; align-items: flex-start; justify-content: space-between; gap: 16px;
    margin-bottom: 16px;
  }
  .bi-page-header h1, .bi-page-header .bi-page-title {
    margin: 0; font-size: 20px; font-weight: 700; color: var(--bi-text);
  }
  .bi-page-header .bi-page-desc { margin: 6px 0 0; font-size: 13px; color: var(--bi-muted); }
  .bi-filter-bar {
    display: flex; flex-wrap: wrap; gap: 12px; align-items: center;
    padding: 12px 16px; margin-bottom: 16px;
    background: var(--bi-surface); border: 1px solid var(--bi-border);
    border-radius: var(--bi-radius-lg); box-shadow: var(--bi-shadow);
  }
  .bi-panel {
    background: var(--bi-surface); border: 1px solid var(--bi-border);
    border-radius: var(--bi-radius-lg); box-shadow: var(--bi-shadow);
  }
  .bi-empty { padding: 32px 16px; }
}
```

- [ ] **Step 2: 确认 Layout 侧栏高度可吃令牌（小改）**

在 `BiWorkspaceLayout.vue` 的 scoped CSS 中，将 `.bi-nav-link` 的 `min-height:40px` 改为：

```css
min-height: var(--bi-nav-item-height, 40px);
```

- [ ] **Step 3: 手工验收准备**

启动前端后（若已在跑则跳过启动），在 DevTools 给 `.bi-workspace` 临时加 `data-bi-theme="ops"`，确认背景变深；再改为 `saas` 确认圆角变大。本 Task 可不依赖工作台切换器。

- [ ] **Step 4: Commit（仅用户要求时）**

```bash
git add Wanfo-Cloud-Vue/src/views/bi/components/BiTheme.scss Wanfo-Cloud-Vue/src/layout/components/BiWorkspaceLayout.vue
git commit -m "$(cat <<'EOF'
feat(bi): add restrained/saas/ops CSS theme tokens

EOF
)"
```

---

### Task 3: Layout 绑定 `data-bi-theme` 并订阅变更

**Files:**
- Modify: `Wanfo-Cloud-Vue/src/layout/components/BiWorkspaceLayout.vue`

**Interfaces:**
- Consumes: `getBiUiTheme`, `subscribeBiUiTheme` from `@/utils/biUiTheme`
- Produces: 根节点 `:data-bi-theme="uiTheme"`

- [ ] **Step 1: 改模板根节点**

将：

```vue
<div class="bi-workspace" :class="{ 'is-editor': isEditor }">
```

改为：

```vue
<div class="bi-workspace" :class="{ 'is-editor': isEditor }" :data-bi-theme="uiTheme">
```

- [ ] **Step 2: 在 `<script setup>` 增加主题状态**

在现有 import 区增加：

```ts
import { onMounted, onUnmounted } from 'vue'
import { getBiUiTheme, subscribeBiUiTheme, type BiUiTheme } from '@/utils/biUiTheme'
```

（若文件已从 `vue` 导入 `computed, ref, watch`，把 `onMounted, onUnmounted` 合并进同一行 import。）

在 `const displayName = ...` 附近增加：

```ts
const uiTheme = ref<BiUiTheme>(getBiUiTheme())
let unsubscribeTheme: (() => void) | undefined
onMounted(() => {
  uiTheme.value = getBiUiTheme()
  unsubscribeTheme = subscribeBiUiTheme((t) => { uiTheme.value = t })
})
onUnmounted(() => { unsubscribeTheme?.() })
```

- [ ] **Step 3: 手工验收**

1. 打开任意 `/bi-admin/*` 页，检查根节点有 `data-bi-theme="restrained"`（或本地已有其它合法值）。
2. 在 Console 执行（需主题工具已挂到模块；若不便，用 Application → Local Storage 写入 `bi-ui-theme=ops` 后刷新）：

```js
localStorage.setItem('bi-ui-theme', 'ops'); location.reload()
```

Expected: 壳变深色。再设回 `restrained` 并刷新。

- [ ] **Step 4: Commit（仅用户要求时）**

```bash
git add Wanfo-Cloud-Vue/src/layout/components/BiWorkspaceLayout.vue
git commit -m "$(cat <<'EOF'
feat(bi): bind data-bi-theme on workspace layout

EOF
)"
```

---

### Task 4: 工作台「外观设置」切换器（P0 收口）

**Files:**
- Modify: `Wanfo-Cloud-Vue/src/views/bi/workbench/index.vue`
- Assets (already present): `Wanfo-Cloud-Vue/src/assets/bi/themes/restrained.jpg`, `saas.jpg`, `ops.jpg`

**Interfaces:**
- Consumes: `BI_UI_THEMES`, `getBiUiTheme`, `setBiUiTheme`, `BiUiTheme`
- Produces: 工作台左侧外观设置三卡；点击调用 `setBiUiTheme`

- [ ] **Step 1: 在左侧 `quick-card` 与 `domain-hubs` 之间（或 `domain-hubs` 之后）插入外观设置区块**

在 `</section>` 结束 `quick-card` 之后、`</div>`（workbranch-left）之前增加一节：

```vue
      <section class="card theme-card">
        <h3 class="card-label">外观设置</h3>
        <p class="theme-hint">仅影响数据中心工作区皮肤，保存在本机。</p>
        <div class="theme-grid" role="listbox" aria-label="主题预设">
          <button
            v-for="opt in themeOptions"
            :key="opt.id"
            type="button"
            role="option"
            class="theme-option"
            :class="{ active: uiTheme === opt.id }"
            :aria-selected="uiTheme === opt.id"
            @click="applyTheme(opt.id)"
          >
            <img :src="opt.preview" :alt="opt.label" class="theme-preview" />
            <span class="theme-name">{{ opt.label }}</span>
            <span class="theme-desc">{{ opt.desc }}</span>
          </button>
        </div>
      </section>
```

- [ ] **Step 2: script 增加主题逻辑**

```ts
import {
  BI_UI_THEMES, getBiUiTheme, setBiUiTheme, subscribeBiUiTheme, type BiUiTheme
} from '@/utils/biUiTheme'
import previewRestrained from '@/assets/bi/themes/restrained.jpg'
import previewSaas from '@/assets/bi/themes/saas.jpg'
import previewOps from '@/assets/bi/themes/ops.jpg'

const uiTheme = ref<BiUiTheme>(getBiUiTheme())
const themeOptions = [
  { id: 'restrained' as const, label: '克制专业', desc: '浅色企业风', preview: previewRestrained },
  { id: 'saas' as const, label: '明亮产品', desc: '大圆角卡片', preview: previewSaas },
  { id: 'ops' as const, label: '深色运营', desc: '监控运维风', preview: previewOps }
].filter(o => (BI_UI_THEMES as readonly string[]).includes(o.id))

function applyTheme(id: BiUiTheme) {
  setBiUiTheme(id)
  uiTheme.value = id
}

let unsubTheme: (() => void) | undefined
onMounted(() => {
  uiTheme.value = getBiUiTheme()
  unsubTheme = subscribeBiUiTheme((t) => { uiTheme.value = t })
})
```

将现有 `onMounted(refresh)` 改为在同一 `onMounted` 内先订阅主题再 `void refresh()`，或再增加一个 `onMounted`；`onUnmounted` 取消订阅：

```ts
import { onUnmounted } from 'vue'
// ...
onUnmounted(() => { unsubTheme?.() })
```

若已有 `onMounted(refresh)`，合并为：

```ts
onMounted(() => {
  uiTheme.value = getBiUiTheme()
  unsubTheme = subscribeBiUiTheme((t) => { uiTheme.value = t })
  void refresh()
})
```

并删除重复的旧 `onMounted(refresh)`。

- [ ] **Step 3: 追加 scoped 样式**

```css
.theme-card { padding: 16px; }
.theme-hint { margin: 8px 0 0; font-size: 12px; color: var(--bi-muted, #667085); }
.theme-grid { display: grid; grid-template-columns: 1fr; gap: 10px; margin-top: 12px; }
.theme-option {
  display: flex; flex-direction: column; align-items: stretch; gap: 4px;
  padding: 8px; border: 2px solid var(--bi-border, #e6ebf3); border-radius: var(--bi-radius-lg, 12px);
  background: var(--bi-surface, #fff); cursor: pointer; text-align: left;
}
.theme-option.active { border-color: var(--el-color-primary, #3563e9); box-shadow: 0 0 0 1px var(--el-color-primary, #3563e9); }
.theme-preview { width: 100%; height: 88px; object-fit: cover; border-radius: 8px; display: block; }
.theme-name { font-size: 13px; font-weight: 650; color: var(--bi-text, #182230); }
.theme-desc { font-size: 12px; color: var(--bi-muted, #667085); }
```

- [ ] **Step 4: P0 验收清单**

1. 打开 `/bi-admin/workbench`，看到三张预览卡。
2. 点「深色运营」→ 侧栏/背景立即变深，卡为选中态。
3. 刷新页面 → 仍为 `ops`。
4. 点「克制专业」→ 恢复浅色。
5. 进入图表/看板编辑器（`workspaceEditor`）→ 全屏仍可读，主题色继承。

- [ ] **Step 5: Commit（仅用户要求时）**

```bash
git add Wanfo-Cloud-Vue/src/views/bi/workbench/index.vue Wanfo-Cloud-Vue/src/assets/bi/themes/
git commit -m "$(cat <<'EOF'
feat(bi): add workbench appearance theme switcher

EOF
)"
```

---

### Task 5: P1 — 工作台 Hub 去硬编码色

**Files:**
- Modify: `Wanfo-Cloud-Vue/src/views/bi/workbench/index.vue` `<style scoped>`

**Interfaces:**
- Consumes: `--bi-background`、`--bi-surface`、`--bi-text`、`--bi-muted`、`--bi-border`、`--el-color-primary`、`--bi-radius-lg`、`--bi-shadow`、`--bi-page-pad-x/y`
- Produces: 无新 API

- [ ] **Step 1: 将 `.workbranch` 内边距与背景改为令牌**

```css
.workbranch {
  padding: var(--bi-page-pad-y, 24px) var(--bi-page-pad-x, 32px) 40px;
  background: var(--bi-background, #f6f8fc);
}
```

- [ ] **Step 2: `.card` / 链接色 / hover 边框改令牌**

把硬编码 `#fff`、`#e6ebf3`、`#182230`、`#667085`、`#3370ff`、`#edf3ff`、`#f8fafd` 等替换为对应 `var(--bi-*)` / `var(--el-color-primary)`；`.card` 使用：

```css
.card {
  background: var(--bi-surface, #fff);
  border-radius: var(--bi-radius-lg, 12px);
  border: 1px solid var(--bi-border, #e6ebf3);
  box-shadow: var(--bi-shadow, none);
}
```

保留语义色（失败红 `#d64545`、警告橙）可不改。

- [ ] **Step 3: 在三种主题下目视工作台**

切换 restrained / saas / ops，确认卡片表面、文字、边框对比度可读；saas 下圆角明显更大。

- [ ] **Step 4: Commit（仅用户要求时）**

```bash
git add Wanfo-Cloud-Vue/src/views/bi/workbench/index.vue
git commit -m "$(cat <<'EOF'
refactor(bi): make workbench styles theme-token aware

EOF
)"
```

---

### Task 6: P2 — 高频 List 页套骨架类

**Files:**
- Modify（逐个，能改多少改多少，优先外壳）：
  - `Wanfo-Cloud-Vue/src/views/bi/catalog/index.vue`
  - `Wanfo-Cloud-Vue/src/views/bi/task-center/index.vue`
  - `Wanfo-Cloud-Vue/src/views/bi/datasource/index.vue`
  - `Wanfo-Cloud-Vue/src/views/bi/dataset/index.vue`
  - `Wanfo-Cloud-Vue/src/views/bi/pipeline/index.vue`
  - `Wanfo-Cloud-Vue/src/views/bi/warehouse/index.vue`

**Interfaces:**
- Consumes: `.bi-page`、`.bi-page-header`、`.bi-filter-bar`、`.bi-panel`
- Produces: 无新 API

- [ ] **Step 1: 对每个文件做同一模式改造**

1. 根容器加 class `bi-page`（若已有 padding 则去掉冲突的固定 padding，改吃令牌）。
2. 标题行包 `bi-page-header`；主内容白底块加 `bi-panel`（可与现有 card class 并存）。
3. 筛选行加 `bi-filter-bar`（若结构允许）。
4. 将 scoped 样式里页面背景/卡片边框的硬编码色改为 `var(--bi-*)`；**不要**重做交互或表格列。

每个文件改完后切换 `ops` 看一眼表格是否仍可读。

- [ ] **Step 2: 冒烟路径**

登录后依次打开：目录 → 任务中心 → 数据源 → 数据集 → 加工 → 装载；在 `saas` 与 `ops` 下各扫一遍，无布局崩坏即可。

- [ ] **Step 3: Commit（仅用户要求时）**

```bash
git add Wanfo-Cloud-Vue/src/views/bi/catalog/index.vue Wanfo-Cloud-Vue/src/views/bi/task-center/index.vue Wanfo-Cloud-Vue/src/views/bi/datasource/index.vue Wanfo-Cloud-Vue/src/views/bi/dataset/index.vue Wanfo-Cloud-Vue/src/views/bi/pipeline/index.vue Wanfo-Cloud-Vue/src/views/bi/warehouse/index.vue
git commit -m "$(cat <<'EOF'
refactor(bi): apply theme page skeleton to high-traffic list views

EOF
)"
```

---

### Task 7: P3 — 文档 + 残余页轻量收尾

**Files:**
- Modify: `docs/SYSTEM-DESIGN.md`（§1.5 前端壳一行、§16.3 补主题说明、§16.4 变更日志一行）
- Optionally light-touch remaining list pages under `Wanfo-Cloud-Vue/src/views/bi/*/index.vue`（glossary、field-standard、data-api、access-request、backup-drill、query-monitor、metric、metric-set、form、export、subscription、template、dashboard、chart 列表壳）——同样只加骨架类/换令牌，不改逻辑
- Optional: 空态插画延后；本 Task **不**强制生成新图

- [ ] **Step 1: 更新 SYSTEM-DESIGN**

§1.5 前端壳单元格追加：`data-bi-theme=restrained|saas|ops`（工作台切换，`localStorage.bi-ui-theme`）。

§16.3 增加一小段：

```markdown
**外观主题**：工作台「外观设置」切换 `restrained` / `saas` / `ops`；壳根 `data-bi-theme`；实现说明见 `docs/superpowers/specs/2026-10-31-bi-ui-theme-design.md`。
```

§16.4 追加一行：

```markdown
| 2026-10-31 | 前端：三套可切换 UI 主题（令牌 + 工作台外观设置；List 骨架分批） |
```

- [ ] **Step 2:（可选）其余 List 根节点加 `bi-page`，明显硬编码背景改令牌**

每个文件不超过「根 class + 背景/边框色」级改动；遇复杂编辑器页跳过。

- [ ] **Step 3: 全量手工验收**

- [ ] 三主题切换 + 刷新保持  
- [ ] 顶栏无主题入口  
- [ ] Editor 全屏可读  
- [ ] P2 六页在 ops 下对比度可接受  
- [ ] SYSTEM-DESIGN 已更新  

- [ ] **Step 4: Commit（仅用户要求时）**

```bash
git add docs/SYSTEM-DESIGN.md Wanfo-Cloud-Vue/src/views/bi
git commit -m "$(cat <<'EOF'
docs(bi): record UI theme presets; light-touch remaining list shells

EOF
)"
```

---

## Spec coverage check

| Spec 要求 | Task |
|---|---|
| CSS 令牌三套 + `data-bi-theme` | 2, 3 |
| `localStorage.bi-ui-theme` + normalize | 1, 3 |
| 仅工作台外观设置 + 预览图 | 4 |
| Hub / List / Editor 骨架 | 2, 5, 6；Editor 继承于 3 |
| P0→P3 批次 | 4=P0, 5=P1, 6=P2, 7=P3 |
| 不做服务端/顶栏/RuoYi 换肤 | Global Constraints |
| SYSTEM-DESIGN 更新 | 7 |

## Placeholder / 一致性自检

- 主题键与 storage key 全文统一为 `restrained|saas|ops` 与 `bi-ui-theme`
- API 名统一 `getBiUiTheme` / `setBiUiTheme` / `subscribeBiUiTheme` / `normalizeBiUiTheme`
- 无 TBD；P3 空态插画明确为可选延后
