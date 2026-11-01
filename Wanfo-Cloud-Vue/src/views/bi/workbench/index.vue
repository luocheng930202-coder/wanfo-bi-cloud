<template>
  <div class="workbranch" v-loading="loading">
    <div class="workbranch-left">
      <section class="card user-card">
        <el-avatar :size="48" :src="userStore.avatar" class="user-avatar">{{ avatarFallback }}</el-avatar>
        <div class="user-meta">
          <div class="user-name" :title="displayName">{{ displayName }}</div>
          <div v-if="userStore.id" class="user-id">ID: {{ userStore.id }}</div>
        </div>
        <div class="user-stats">
          <div v-for="item in primaryStats" :key="item.key" class="user-stat">
            <span class="user-stat-label">{{ item.label }}</span>
            <strong class="user-stat-num">{{ statValue(item.key) }}</strong>
          </div>
        </div>
        <div v-if="secondaryStats.length" class="user-stats user-stats--sub">
          <div v-for="item in secondaryStats" :key="item.key" class="user-stat user-stat--compact">
            <span class="user-stat-label">{{ item.label }}</span>
            <strong class="user-stat-num">{{ statValue(item.key) }}</strong>
          </div>
        </div>
      </section>

      <section class="card quick-card">
        <h3 class="card-label">快速创建</h3>
        <div class="quick-grid">
          <button
            v-for="item in quickCreates"
            :key="item.key"
            type="button"
            class="quick-item"
            :class="{ disabled: !item.enabled }"
            :disabled="!item.enabled"
            @click="item.run()"
          >
            <span class="quick-icon" :style="{ background: item.color }">
              <component :is="item.icon" />
            </span>
            <span>{{ item.title }}</span>
          </button>
          <button
            type="button"
            class="quick-item quick-item--wide"
            :class="{ disabled: !canCreateVisual }"
            :disabled="!canCreateVisual"
            @click="openTemplateCenter"
          >
            <span class="quick-icon quick-icon--template">
              <el-icon><Document /></el-icon>
            </span>
            <span>使用模板新建</span>
          </button>
        </div>
        <div class="domain-hubs">
          <button
            v-for="hub in domainHubs"
            :key="hub.key"
            type="button"
            class="domain-hub"
            @click="hub.run()"
          >
            <strong>{{ hub.title }}</strong>
            <span>{{ hub.desc }}</span>
          </button>
        </div>
      </section>

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
    </div>

    <div class="workbranch-right">
      <section class="card todo-card">
        <div class="card-head">
          <h3 class="card-label">今日待办</h3>
          <el-button link type="primary" @click="router.push('/bi-admin/task-center')">任务中心</el-button>
        </div>
        <div v-if="todos.length" class="todo-list">
          <button
            v-for="item in todos"
            :key="item.key"
            type="button"
            class="todo-item"
            @click="item.run()"
          >
            <span class="todo-badge" :class="item.tone">{{ item.count }}</span>
            <span class="todo-body">
              <strong>{{ item.title }}</strong>
              <em>{{ item.hint }}</em>
            </span>
            <span class="todo-action">处理</span>
          </button>
        </div>
        <el-empty v-else description="暂无待处理事项" :image-size="56" />
      </section>

      <section class="card template-card">
        <div class="card-head">
          <h3 class="card-label">模板中心</h3>
          <div class="card-actions">
            <el-button link type="primary" @click="router.push('/bi-admin/template')">查看全部</el-button>
            <el-button link @click="templateFold = !templateFold">{{ templateFold ? '展开' : '收起' }}</el-button>
          </div>
        </div>
        <template v-if="!templateFold">
          <div class="template-tabs">
            <button type="button" :class="{ active: templateTab === 'dashboard' }" @click="templateTab = 'dashboard'">仪表板</button>
            <button type="button" :class="{ active: templateTab === 'screen' }" @click="templateTab = 'screen'">数据大屏</button>
          </div>
          <div v-if="filteredTemplates.length" class="template-list">
            <article v-for="t in filteredTemplates" :key="t.id" class="template-item" @click="applyTemplate(t)">
              <div class="template-thumb" :class="t.kind === 'screen' ? 'screen' : 'dashboard'">
                <span>{{ t.kind === 'screen' ? '大屏' : '看板' }}</span>
              </div>
              <div class="template-body">
                <strong :title="t.name">{{ t.name }}</strong>
                <p>{{ t.description || t.category }}</p>
              </div>
            </article>
          </div>
          <el-empty v-else description="暂无推荐模板" :image-size="64" />
        </template>
      </section>

      <section class="card recent-card">
        <div class="card-head">
          <h3 class="card-label">最近编辑</h3>
          <el-input v-model="recentSearch" clearable placeholder="搜索" class="recent-search" />
        </div>
        <el-tabs v-model="recentKind" class="recent-tabs">
          <el-tab-pane label="全部" name="all" />
          <el-tab-pane label="仪表板" name="dashboard" />
          <el-tab-pane label="数据大屏" name="screen" />
          <el-tab-pane label="图表" name="chart" />
        </el-tabs>
        <el-table v-loading="recentLoading" :data="filteredRecent" size="small" :show-header="true" max-height="320">
          <el-table-column label="" width="40">
            <template #default="{ row }">
              <span class="type-dot" :class="row.kind" />
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ kindLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="88" align="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openRecent(row)">{{ openLabel(row) }}</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无记录" :image-size="56" /></template>
        </el-table>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts" name="BiWorkbench">
import { computed, h, onActivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Document, DataAnalysis, Monitor, Coin, Connection } from '@element-plus/icons-vue'
import useUserStore from '@/store/modules/user'
import { checkPermi } from '@/utils/permission'
import { unwrapBiData } from '@/api/bi/response'
import {
  workbenchRecent, workbenchSummary, workbenchTemplatePicks,
  type WorkbenchRecentItem, type WorkbenchSummary
} from '@/api/bi/workbench'
import { listTaskRuns } from '@/api/bi/taskCenter'
import { listPendingAccessRequests, listMyAccessRequests } from '@/api/bi/accessRequest'
import type { MarketTemplateEntry } from '@/api/bi/template'
import { CHART_TYPE_LABELS, isChartType } from '@/utils/biChartTypes'
import {
  BI_UI_THEMES, getBiUiTheme, setBiUiTheme, subscribeBiUiTheme, type BiUiTheme
} from '@/utils/biUiTheme'
import previewRestrained from '@/assets/bi/themes/restrained.jpg'
import previewSaas from '@/assets/bi/themes/saas.jpg'
import previewOps from '@/assets/bi/themes/ops.jpg'

type SummaryKey = keyof WorkbenchSummary

const router = useRouter()
const userStore = useUserStore()
const summary = ref<WorkbenchSummary | null>(null)
const recent = ref<WorkbenchRecentItem[]>([])
const templatePicks = ref<MarketTemplateEntry[]>([])
const recentKind = ref('all')
const recentSearch = ref('')
const recentLoading = ref(false)
const loading = ref(false)
const templateFold = ref(false)
const templateTab = ref<'dashboard' | 'screen'>('dashboard')
const failedTaskCount = ref(0)
const pendingAccessCount = ref(0)
const myPendingAccessCount = ref(0)
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

const displayName = computed(() => userStore.nickName || userStore.name || '当前用户')
const avatarFallback = computed(() => (displayName.value || 'U').slice(0, 1))
const canCreateVisual = computed(() => checkPermi(['bi:dashboard:add']))

const todos = computed(() => {
  const items: { key: string; title: string; hint: string; count: number; tone: string; run: () => void }[] = []
  if (checkPermi(['bi:task:list']) && failedTaskCount.value > 0) {
    items.push({
      key: 'failed-tasks',
      title: '失败任务待处理',
      hint: '统一任务池中的失败运行',
      count: failedTaskCount.value,
      tone: 'danger',
      run: () => router.push({ path: '/bi-admin/task-center', query: { status: 'FAILED' } })
    })
  }
  if (checkPermi(['bi:datasource:model']) && pendingAccessCount.value > 0) {
    items.push({
      key: 'access-pending',
      title: '访问申请待审批',
      hint: '资源授权申请需要你处理',
      count: pendingAccessCount.value,
      tone: 'warn',
      run: () => router.push('/bi-admin/access-requests')
    })
  }
  if (myPendingAccessCount.value > 0) {
    items.push({
      key: 'my-access',
      title: '我的申请待结果',
      hint: '已提交、等待审批的访问申请',
      count: myPendingAccessCount.value,
      tone: 'info',
      run: () => router.push('/bi-admin/access-requests')
    })
  }
  return items
})

function firstAvailable(paths: { path: string; perm: string }[]) {
  return paths.find(p => !p.perm || checkPermi([p.perm]))?.path
}

const domainHubs = computed(() => {
  const analyzePath = firstAvailable([
    { path: '/bi-admin/dashboard', perm: 'bi:dashboard:list' },
    { path: '/bi-admin/chart', perm: 'bi:chart:list' },
    { path: '/bi-admin/metric', perm: 'bi:metric:list' }
  ])
  const platformPath = firstAvailable([
    { path: '/bi-admin/catalog', perm: 'bi:datasource:model' },
    { path: '/bi-admin/pipeline', perm: 'bi:pipeline:list' },
    { path: '/bi-admin/warehouse', perm: 'bi:resource:admin' },
    { path: '/bi-admin/task-center', perm: 'bi:task:list' }
  ])
  const hubs: { key: string; title: string; desc: string; run: () => void }[] = []
  if (analyzePath) {
    hubs.push({
      key: 'analyze',
      title: '进入分析',
      desc: '看板 · 图表 · 指标 · 填报',
      run: () => {
        localStorage.setItem('bi-workspace-domain', 'analyze')
        router.push(analyzePath)
      }
    })
  }
  if (platformPath) {
    hubs.push({
      key: 'platform',
      title: '进入数据中心',
      desc: '目录 · 加工 · 装载 · 治理',
      run: () => {
        localStorage.setItem('bi-workspace-domain', 'platform')
        router.push(platformPath)
      }
    })
  }
  return hubs
})

const statDefs: { key: SummaryKey; label: string; perm: string; primary?: boolean }[] = [
  { key: 'dashboard', label: '仪表板', perm: 'bi:dashboard:list', primary: true },
  { key: 'screen', label: '数据大屏', perm: 'bi:dashboard:list', primary: true },
  { key: 'dataset', label: '数据集', perm: 'bi:dataset:list', primary: true },
  { key: 'datasource', label: '数据源', perm: 'bi:datasource:list' },
  { key: 'chart', label: '图表', perm: 'bi:chart:list' },
  { key: 'template', label: '本地模板', perm: 'bi:template:list' },
  { key: 'form', label: '数据填报', perm: 'bi:form:list' },
  { key: 'export', label: '导出任务', perm: 'bi:export:list' }
]

const primaryStats = computed(() => statDefs.filter(d => d.primary && checkPermi([d.perm])))
const secondaryStats = computed(() => statDefs.filter(d => !d.primary && checkPermi([d.perm])))

function statValue(key: SummaryKey) {
  if (!checkPermi([statDefs.find(d => d.key === key)?.perm || ''])) return '—'
  const v = summary.value?.[key]
  return v === undefined || v === null ? 0 : v
}

const BarIcon = { render: () => h(DataAnalysis) }
const ScreenIcon = { render: () => h(Monitor) }
const DatasetIcon = { render: () => h(Coin) }
const DsIcon = { render: () => h(Connection) }

const quickCreates = computed(() => [
  { key: 'dashboard', title: '仪表板', color: '#3370ff', icon: BarIcon, enabled: checkPermi(['bi:dashboard:add']), run: () => openCreate('dashboard') },
  { key: 'screen', title: '数据大屏', color: '#00d6b9', icon: ScreenIcon, enabled: checkPermi(['bi:dashboard:add']), run: () => openCreate('screen') },
  { key: 'dataset', title: '数据集', color: '#16c0ff', icon: DatasetIcon, enabled: checkPermi(['bi:dataset:add']), run: () => router.push('/bi-admin/dataset') },
  { key: 'datasource', title: '数据源', color: '#7f3bf6', icon: DsIcon, enabled: checkPermi(['bi:datasource:add']), run: () => router.push('/bi-admin/datasource') }
])

const filteredTemplates = computed(() =>
  templatePicks.value.filter(t => t.kind === templateTab.value).slice(0, 6)
)

function openCreate(kind: 'dashboard' | 'screen') {
  router.push({ path: '/bi-admin/dashboard', query: { openTemplate: kind } })
}

function openTemplateCenter() {
  if (canCreateVisual.value) router.push({ path: '/bi-admin/dashboard', query: { openTemplate: templateTab.value } })
  else router.push('/bi-admin/template')
}

function applyTemplate(t: MarketTemplateEntry) {
  if (!checkPermi(['bi:dashboard:add'])) return
  router.push({ path: '/bi-admin/dashboard', query: { openTemplate: t.kind, templateId: 'market-' + t.id } })
}

function kindLabel(row: WorkbenchRecentItem) {
  if (row.kind === 'chart') return isChartType(String(row.type || '')) ? CHART_TYPE_LABELS[row.type as keyof typeof CHART_TYPE_LABELS] : '图表'
  if (row.kind === 'screen') return '数据大屏'
  return '仪表板'
}

function openLabel(row: WorkbenchRecentItem) {
  if (row.kind === 'chart') return '打开'
  if (checkPermi(['bi:dashboard:edit'])) return '编辑'
  if (row.published && checkPermi(['bi:dashboard:consume'])) return '查看'
  return '打开'
}

function openRecent(row: WorkbenchRecentItem) {
  if (row.kind === 'chart') {
    router.push({ path: '/bi-admin/chart', query: { open: String(row.id) } })
    return
  }
  if (checkPermi(['bi:dashboard:edit'])) {
    router.push('/bi-admin/dashboard-workspace/' + row.id+'/edit')
    return
  }
  if (row.published && checkPermi(['bi:dashboard:consume'])) {
    router.push({ path: '/bi-admin/dashboard', query: { view: String(row.id) } })
    return
  }
  router.push('/bi-admin/dashboard')
}

const filteredRecent = computed(() => {
  const q = recentSearch.value.trim()
  if (!q) return recent.value
  return recent.value.filter(r => r.name.includes(q))
})

async function loadSummary() {
  try { summary.value = unwrapBiData(await workbenchSummary()) } catch { summary.value = null }
}
async function loadRecent() {
  recentLoading.value = true
  try { recent.value = unwrapBiData(await workbenchRecent(recentKind.value, 20)) || [] } finally { recentLoading.value = false }
}
async function loadTemplates() {
  if (!checkPermi(['bi:template:list']) && !checkPermi(['bi:dashboard:add'])) {
    templatePicks.value = []
    return
  }
  try { templatePicks.value = unwrapBiData(await workbenchTemplatePicks(12)) || [] } catch { templatePicks.value = [] }
}

async function loadTodos() {
  failedTaskCount.value = 0
  pendingAccessCount.value = 0
  myPendingAccessCount.value = 0
  const jobs: Promise<void>[] = []
  if (checkPermi(['bi:task:list'])) {
    jobs.push((async () => {
      try {
        const res = unwrapBiData(await listTaskRuns({ status: 'FAILED', page: 1, pageSize: 1 }))
        failedTaskCount.value = Number(res?.summary?.failed ?? res?.total ?? 0)
      } catch { /* ignore */ }
    })())
  }
  if (checkPermi(['bi:datasource:model'])) {
    jobs.push((async () => {
      try {
        const list = unwrapBiData(await listPendingAccessRequests()) || []
        pendingAccessCount.value = list.filter(i => i.status === 'PENDING').length
      } catch { /* ignore */ }
    })())
  }
  jobs.push((async () => {
    try {
      const list = unwrapBiData(await listMyAccessRequests()) || []
      myPendingAccessCount.value = list.filter(i => i.status === 'PENDING').length
    } catch { /* ignore */ }
  })())
  await Promise.all(jobs)
}

async function refresh() {
  loading.value = true
  try {
    await Promise.all([loadSummary(), loadRecent(), loadTemplates(), loadTodos()])
  } finally {
    loading.value = false
  }
}

watch(recentKind, () => { void loadRecent() })

let activatedOnce = false
let unsubTheme: (() => void) | undefined
onMounted(() => {
  uiTheme.value = getBiUiTheme()
  unsubTheme = subscribeBiUiTheme((t) => { uiTheme.value = t })
  void refresh()
})
onUnmounted(() => { unsubTheme?.() })
onActivated(() => {
  if (!activatedOnce) { activatedOnce = true; return }
  void refresh()
})
</script>

<style scoped>
.workbranch {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  min-height: calc(100vh - 140px);
  margin: 0;
  padding: var(--bi-page-pad-y, 24px) var(--bi-page-pad-x, 32px) 40px;
  background: var(--bi-background, #f6f8fc);
  box-sizing: border-box;
}
.workbranch-left {
  width: 360px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.workbranch-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.card {
  background: var(--bi-surface, #fff);
  border-radius: var(--bi-radius-lg, 12px);
  border: 1px solid var(--bi-border, #e6ebf3);
  box-shadow: var(--bi-shadow, none);
}
.theme-card { padding: 16px; }
.theme-hint { margin: 8px 0 0; font-size: 12px; color: var(--bi-muted, #667085); }
.theme-grid { display: grid; grid-template-columns: 1fr; gap: 10px; margin-top: 12px; }
.theme-option {
  display: flex; flex-direction: column; align-items: stretch; gap: 4px;
  padding: 8px; border: 2px solid var(--bi-border, #e6ebf3);
  border-radius: var(--bi-radius-lg, 12px);
  background: var(--bi-surface, #fff); cursor: pointer; text-align: left;
}
.theme-option.active {
  border-color: var(--el-color-primary, #3563e9);
  box-shadow: 0 0 0 1px var(--el-color-primary, #3563e9);
}
.theme-preview { width: 100%; height: 88px; object-fit: cover; border-radius: 8px; display: block; }
.theme-name { font-size: 13px; font-weight: 650; color: var(--bi-text, #182230); }
.theme-desc { font-size: 12px; color: var(--bi-muted, #667085); }
.user-card {
  padding: 20px 16px 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 12px;
}
.user-avatar { flex-shrink: 0; background: #3370ff; color: #fff; font-weight: 600; }
.user-meta { flex: 1; min-width: 120px; }
.user-name { font-size: 16px; font-weight: 650; color: var(--bi-text, #182230); line-height: 1.3; }
.user-id { font-size: 12px; color: var(--bi-muted, #667085); margin-top: 4px; }
.user-stats {
  width: 100%;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 0;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #f0f1f3;
}
.user-stats--sub {
  margin-top: 0;
  padding-top: 8px;
  border-top: none;
}
.user-stat {
  flex: 1 1 33%;
  min-width: 90px;
  text-align: center;
}
.user-stat--compact { flex: 1 1 25%; min-width: 72px; }
.user-stat-label { display: block; font-size: 12px; color: var(--bi-muted, #667085); margin-bottom: 4px; }
.user-stat-num { font-size: 22px; color: var(--bi-text, #182230); font-weight: 650; }
.user-stat--compact .user-stat-num { font-size: 16px; }

.quick-card { padding: 16px; }
.card-label { margin: 0; font-size: 14px; font-weight: 650; color: var(--bi-text, #182230); }
.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 14px;
}
.quick-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--bi-border, #e6ebf3);
  border-radius: var(--bi-radius-lg, 10px);
  background: var(--bi-surface, #fff);
  cursor: pointer;
  font-size: 14px;
  color: var(--bi-text, #182230);
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.quick-item:hover:not(.disabled) { border-color: var(--el-color-primary, #3370ff); box-shadow: var(--bi-shadow, 0 2px 8px rgba(51, 112, 255, 0.1)); }
.quick-item.disabled { opacity: 0.45; cursor: not-allowed; }
.quick-item--wide { grid-column: 1 / -1; }
.quick-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
}
.quick-icon--template { background: linear-gradient(135deg, #ff9a3c, #ff6b35); color: #fff; }

.domain-hubs {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #ebedf0;
}
.domain-hub {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 10px 12px;
  border: 1px solid var(--bi-border, #e6ebf3);
  border-radius: var(--bi-radius-lg, 10px);
  background: var(--bi-soft, #f8fafd);
  cursor: pointer;
  text-align: left;
  transition: border-color .15s, background .15s;
}
.domain-hub:hover { border-color: var(--el-color-primary, #3370ff); background: var(--el-color-primary-light-9, #edf3ff); }
.domain-hub strong { font-size: 13px; color: var(--bi-text, #182230); }
.domain-hub span { font-size: 12px; color: var(--bi-muted, #667085); }

.todo-card { padding: 16px; }
.todo-list { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }
.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--bi-border, #e6ebf3);
  border-radius: var(--bi-radius-lg, 10px);
  background: var(--bi-surface, #fff);
  cursor: pointer;
  text-align: left;
  transition: border-color .15s;
}
.todo-item:hover { border-color: var(--el-color-primary, #3370ff); }
.todo-badge {
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: #667085;
}
.todo-badge.danger { background: #d64545; }
.todo-badge.warn { background: #d97706; }
.todo-badge.info { background: #3563e9; }
.todo-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.todo-body strong { font-size: 13px; color: var(--bi-text, #182230); font-style: normal; }
.todo-body em { font-size: 12px; color: var(--bi-muted, #667085); font-style: normal; }
.todo-action { font-size: 12px; color: var(--el-color-primary, #3370ff); flex-shrink: 0; }

.template-card, .recent-card { padding: 16px 16px 8px; }
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.card-actions { display: flex; align-items: center; gap: 4px; }
.template-tabs {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}
.template-tabs button {
  border: none;
  background: var(--bi-background, #f6f8fc);
  padding: 4px 12px;
  border-radius: var(--bi-radius-sm, 6px);
  font-size: 13px;
  color: var(--bi-muted, #646a73);
  cursor: pointer;
}
.template-tabs button.active { background: var(--el-color-primary-light-9, #e8f3ff); color: var(--el-color-primary, #3370ff); font-weight: 600; }
.template-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.template-item {
  display: flex;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--bi-border, #e6ebf3);
  border-radius: var(--bi-radius-lg, 10px);
  cursor: pointer;
  transition: border-color 0.15s;
}
.template-item:hover { border-color: var(--el-color-primary, #3370ff); }
.template-thumb {
  width: 56px;
  height: 42px;
  border-radius: 6px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #fff;
  font-weight: 600;
}
.template-thumb.dashboard { background: linear-gradient(145deg, #5b8ff9, #3370ff); }
.template-thumb.screen { background: linear-gradient(145deg, #2dd4bf, #00a870); }
.template-body { min-width: 0; }
.template-body strong { display: block; font-size: 13px; color: var(--bi-text, #182230); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.template-body p {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--bi-muted, #667085);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.recent-search { width: 160px; }
.recent-tabs :deep(.el-tabs__header) { margin-bottom: 8px; }
.type-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
}
.type-dot.chart { background: #3370ff; }
.type-dot.dashboard { background: #00b96b; }
.type-dot.screen { background: #00d6b9; }

@media (max-width: 960px) {
  .workbranch { flex-direction: column; }
  .workbranch-left { width: 100%; }
}
</style>

<style scoped>@media(max-width:700px){.workbranch{padding:16px;flex-direction:column}.workbranch-left{width:100%}.workbranch-right{width:100%}}</style>
