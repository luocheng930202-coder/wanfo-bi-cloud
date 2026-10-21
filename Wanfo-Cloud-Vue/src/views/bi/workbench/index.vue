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
        <div class="manage-links">
          <button v-for="link in manageLinks" :key="link.key" type="button" class="manage-link" :disabled="!link.enabled" @click="link.run()">
            {{ link.title }}
          </button>
        </div>
      </section>
    </div>

    <div class="workbranch-right">
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
import { computed, h, onActivated, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Document, DataAnalysis, Monitor, Coin, Connection } from '@element-plus/icons-vue'
import useUserStore from '@/store/modules/user'
import { checkPermi } from '@/utils/permission'
import { unwrapBiData } from '@/api/bi/response'
import {
  workbenchRecent, workbenchSummary, workbenchTemplatePicks,
  type WorkbenchRecentItem, type WorkbenchSummary
} from '@/api/bi/workbench'
import type { MarketTemplateEntry } from '@/api/bi/template'
import { CHART_TYPE_LABELS, isChartType } from '@/utils/biChartTypes'

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

const displayName = computed(() => userStore.nickName || userStore.name || '当前用户')
const avatarFallback = computed(() => (displayName.value || 'U').slice(0, 1))
const canCreateVisual = computed(() => checkPermi(['bi:dashboard:add']))

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

const manageLinks = computed(() => [
  { key: 'chart', title: '图表管理', enabled: checkPermi(['bi:chart:list']), run: () => router.push('/bi-admin/chart') },
  { key: 'board', title: '看板管理', enabled: checkPermi(['bi:dashboard:list']), run: () => router.push('/bi-admin/dashboard') },
  { key: 'form', title: '数据填报', enabled: checkPermi(['bi:form:list']), run: () => router.push('/bi-admin/form') },
  { key: 'export', title: '导出中心', enabled: checkPermi(['bi:export:list']), run: () => router.push('/bi-admin/export') }
].filter(l => l.enabled))

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

async function refresh() {
  loading.value = true
  try {
    await Promise.all([loadSummary(), loadRecent(), loadTemplates()])
  } finally {
    loading.value = false
  }
}

watch(recentKind, () => { void loadRecent() })

let activatedOnce = false
onMounted(refresh)
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
  padding: 24px 32px 40px;
  background: var(--bi-background,#f6f8fc);
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
  background: var(--bi-surface,#fff);
  border-radius: 12px;
  border: 1px solid var(--bi-border,#e6ebf3);
}
.user-card {
  padding: 20px 16px 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 12px;
}
.user-avatar { flex-shrink: 0; background: #3370ff; color: #fff; font-weight: 600; }
.user-meta { flex: 1; min-width: 120px; }
.user-name { font-size: 16px; font-weight: 650; color: var(--bi-text,#182230); line-height: 1.3; }
.user-id { font-size: 12px; color: var(--bi-muted,#667085); margin-top: 4px; }
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
.user-stat-label { display: block; font-size: 12px; color: var(--bi-muted,#667085); margin-bottom: 4px; }
.user-stat-num { font-size: 22px; color: var(--bi-text,#182230); font-weight: 650; }
.user-stat--compact .user-stat-num { font-size: 16px; }

.quick-card { padding: 16px; }
.card-label { margin: 0; font-size: 14px; font-weight: 650; color: var(--bi-text,#182230); }
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
  border: 1px solid var(--bi-border,#e6ebf3);
  border-radius: 10px;
  background: var(--bi-surface,#fff);
  cursor: pointer;
  font-size: 14px;
  color: var(--bi-text,#182230);
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.quick-item:hover:not(.disabled) { border-color: #3370ff; box-shadow: 0 2px 8px rgba(51, 112, 255, 0.1); }
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

.manage-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #ebedf0;
}
.manage-link {
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: #3370ff;
  cursor: pointer;
}
.manage-link:disabled { color: #c0c4cc; cursor: not-allowed; }

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
  background: var(--bi-background,#f6f8fc);
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 13px;
  color: #646a73;
  cursor: pointer;
}
.template-tabs button.active { background: #e8f3ff; color: #3370ff; font-weight: 600; }
.template-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.template-item {
  display: flex;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--bi-border,#e6ebf3);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.template-item:hover { border-color: #3370ff; }
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
.template-body strong { display: block; font-size: 13px; color: var(--bi-text,#182230); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.template-body p {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--bi-muted,#667085);
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
