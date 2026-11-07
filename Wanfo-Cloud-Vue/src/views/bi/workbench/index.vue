<template>
 <div class="platform-workbench" v-loading="loading">
  <div class="workbench-heading"><div><div class="eyebrow">WORKSPACE / 我的工作空间</div><h1>你好，{{ displayName }}</h1><p>从数据接入到分析应用，继续今天的工作。</p></div><div class="heading-actions"><el-button @click="refresh">刷新</el-button><el-dropdown v-if="quickCreates.some(i=>i.enabled)" trigger="click"><el-button type="primary">＋ 新建</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="item in quickCreates.filter(i=>i.enabled)" :key="item.key" @click="item.run()">{{ item.title }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></div>
  <nav class="workbench-shortcuts" aria-label="常用功能"><router-link v-for="item in shortcuts" :key="item.path" :to="item.path"><el-icon><component :is="item.icon" /></el-icon>{{ item.label }}</router-link></nav>
  <el-alert v-if="summaryError" :title="summaryError" type="warning" :closable="false"/>
  <div class="workbench-stats"><router-link v-for="item in headlineStats" :key="item.key" :to="statPath(item.key)"><span>{{ item.label }}</span><strong>{{ statValue(item.key) }}</strong></router-link></div>
  <section class="workbench-section"><div class="section-heading"><h2>最近编辑</h2><el-tabs v-model="recentKind"><el-tab-pane label="全部" name="all"/><el-tab-pane label="看板" name="dashboard"/><el-tab-pane label="大屏" name="screen"/><el-tab-pane label="图表" name="chart"/></el-tabs><el-input v-model="recentSearch" clearable placeholder="搜索资源名称" class="resource-search"/></div>
   <el-alert v-if="recentError" :title="recentError" type="warning" :closable="false"/>
   <div v-loading="recentLoading" class="workbench-resources"><article v-for="row in filteredRecent.slice(0,6)" :key="row.kind+':'+row.id"><div class="workbench-cover" v-loading="!metadataReady" role="group" :aria-label="row.name"><BiResourceThumbnail v-if="metadataReady" :kind="row.kind==='chart'?'chart':'dashboard'" :id="row.id" :name="row.name" :version="meta(row).version||0" :cover-url="meta(row).coverUrl" :actions="meta(row).resourceActions||[]"/></div><button class="resource-name" @click="openRecent(row)">{{ row.name }}</button><div class="resource-meta"><span>{{ kindLabel(row) }}</span><el-button link type="primary" @click="openRecent(row)">{{ openLabel(row) }}</el-button></div></article></div><el-empty v-if="!recentLoading&&!filteredRecent.length&&!recentError" description="暂无匹配的最近编辑资源" :image-size="70"/>
  </section>
  <div class="workbench-bottom"><section class="workbench-section"><div class="section-heading"><h2>近期任务</h2><el-button v-if="checkPermi(['bi:task:list'])" link type="primary" @click="router.push('/bi-admin/task-center')">任务中心 →</el-button></div><el-alert v-if="taskError" :title="taskError" type="warning" :closable="false"/><el-table v-else-if="checkPermi(['bi:task:list'])" :data="taskRows"><el-table-column prop="name" label="任务名称" min-width="160" show-overflow-tooltip/><el-table-column prop="type" label="类型" width="145"/><el-table-column label="最近运行" min-width="140"><template #default="{row}">{{ row.startedAt||row.createdAt||'—' }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="{row}"><span :class="['task-state',row.status]">{{ taskLabel(row.status) }}</span></template></el-table-column><el-table-column label="操作" width="65"><template #default="{row}"><el-button link type="primary" @click="router.push('/bi-admin/task-center')">查看</el-button></template></el-table-column></el-table><el-empty v-else description="暂无任务查看权限" :image-size="60"/></section>
  <section class="workbench-section"><div class="section-heading"><h2>待处理</h2><span class="muted">{{ todos.length }} 类</span></div><button v-for="todo in todos" :key="todo.key" class="todo-entry" @click="todo.run()"><span><strong>{{ todo.title }}</strong><small>{{ todo.hint }}</small></span><b>{{ todo.count }} 项 →</b></button><el-empty v-if="!todos.length" description="暂无待处理事项" :image-size="65"/></section></div>
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
  workbenchRecent, workbenchSummary,
  type WorkbenchRecentItem, type WorkbenchSummary
} from '@/api/bi/workbench'
import BiResourceThumbnail from '../components/BiResourceThumbnail.vue'
import { listCharts } from '@/api/bi/chart'
import { listDashboards } from '@/api/bi/dashboard'
import { listTaskRuns, type TaskRunItem } from '@/api/bi/taskCenter'
import { listPendingAccessRequests, listMyAccessRequests } from '@/api/bi/accessRequest'
import { CHART_TYPE_LABELS, isChartType } from '@/utils/biChartTypes'

type SummaryKey = keyof WorkbenchSummary

const router = useRouter()
const userStore = useUserStore()
const summary = ref<WorkbenchSummary | null>(null)
const recent = ref<WorkbenchRecentItem[]>([])
const recentKind = ref('all')
const recentSearch = ref('')
const metadataReady=ref(false)
const taskRows=ref<TaskRunItem[]>([])
const taskError=ref('')
const summaryError=ref('')
const recentError=ref('')
const resourceMeta=ref<Record<string,{version?:number;coverUrl?:string;resourceActions?:string[]}>>({})
const recentLoading = ref(false)
const loading = ref(false)
const failedTaskCount = ref(0)
const pendingAccessCount = ref(0)
const myPendingAccessCount = ref(0)
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

const statDefs: { key: SummaryKey; label: string; perm: string; primary?: boolean }[] = [
  { key: 'dashboard', label: '仪表板', perm: 'bi:dashboard:list', primary: true },
  { key: 'screen', label: '数据大屏', perm: 'bi:dashboard:list', primary: true },
  { key: 'dataset', label: '数据集', perm: 'bi:dataset:list', primary: true },
  { key: 'datasource', label: '数据源', perm: 'bi:datasource:list' },
  { key: 'chart', label: '图表', perm: 'bi:chart:list' },
  { key: 'template', label: '我的模板', perm: 'bi:template:list' },
  { key: 'form', label: '数据填报', perm: 'bi:form:list' },
  { key: 'export', label: '导出任务', perm: 'bi:export:list' }
]

function statValue(key: SummaryKey) {
  if (!checkPermi([statDefs.find(d => d.key === key)?.perm || ''])) return '—'
  const v = summary.value?.[key]
  return v === undefined || v === null ? '—' : v
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

function openCreate(kind: 'dashboard' | 'screen') {
  router.push({ path: '/bi-admin/dashboard', query: { openTemplate: kind } })
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
  summaryError.value=''
  try { summary.value = unwrapBiData(await workbenchSummary()) } catch { summary.value = null;summaryError.value='统计加载失败，请重试' }
}
async function loadRecent() {
  recentLoading.value = true
  recentError.value=''
  try { recent.value = unwrapBiData(await workbenchRecent(recentKind.value, 20)) || [] } catch {recent.value=[];recentError.value='最近编辑加载失败，请重试'} finally { recentLoading.value = false }
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
    await Promise.all([loadSummary(), loadRecent(), loadTodos(), loadTasks(), loadResourceMeta()])
  } finally {
    loading.value = false
  }
}

watch(recentKind, () => { void loadRecent() })


const headlineStats=computed(()=>statDefs.filter(d=>['datasource','dataset','chart','dashboard','screen'].includes(d.key)&&checkPermi([d.perm])))
const shortcuts=computed(()=>[
 {label:'数据源',path:'/bi-admin/datasource',perm:'bi:datasource:list',icon:Connection},
 {label:'数据集',path:'/bi-admin/dataset',perm:'bi:dataset:list',icon:Coin},
 {label:'数据加工',path:'/bi-admin/pipeline',perm:'bi:pipeline:list',icon:Connection},
 {label:'数据资产',path:'/bi-admin/catalog',perm:'bi:datasource:model',icon:Coin},
 {label:'图表',path:'/bi-admin/chart',perm:'bi:chart:list',icon:DataAnalysis},
 {label:'看板与大屏',path:'/bi-admin/dashboard',perm:'bi:dashboard:list',icon:Monitor},
 {label:'指标管理',path:'/bi-admin/metric',perm:'bi:metric:list',icon:DataAnalysis},
 {label:'模板中心',path:'/bi-admin/template',perm:'bi:template:list',icon:Document}
].filter(i=>checkPermi([i.perm])))
function statPath(key:string){return '/bi-admin/'+({datasource:'datasource',dataset:'dataset',chart:'chart',dashboard:'dashboard',screen:'dashboard'}[key]||'workbench')}
function meta(row:WorkbenchRecentItem){return resourceMeta.value[(row.kind==='chart'?'chart':'dashboard')+':'+row.id]||{resourceActions:[]}}
async function loadResourceMeta(){
 const entries:Record<string,{version?:number;coverUrl?:string;resourceActions?:string[]}>= {}
 await Promise.all([
 checkPermi(['bi:chart:list'])?(async()=>{try{for(const r of (await listCharts()).data||[])entries['chart:'+r.id]=r}catch{/* previews retain permission-safe fallback */}})():Promise.resolve(),
 checkPermi(['bi:dashboard:list'])?(async()=>{try{for(const r of (await listDashboards()).data||[])entries['dashboard:'+r.id]=r}catch{/* previews retain permission-safe fallback */}})():Promise.resolve()
 ]);resourceMeta.value=entries;metadataReady.value=true
}
async function loadTasks(){taskError.value='';taskRows.value=[];if(!checkPermi(['bi:task:list']))return;try{taskRows.value=unwrapBiData(await listTaskRuns({page:1,pageSize:5})).items||[]}catch{taskError.value='任务加载失败，请重试'}}
function taskLabel(status:string){return ({SUCCEEDED:'运行成功',SUCCESS:'运行成功',FAILED:'运行失败',RUNNING:'运行中',WAITING:'等待中',CANCELED:'已取消'} as Record<string,string>)[status]||status}

let activatedOnce = false
onMounted(() => {
  void refresh()
})
onActivated(() => {
  if (!activatedOnce) { activatedOnce = true; return }
  void refresh()
})
</script>
<style scoped>
.platform-workbench{padding:30px 34px 40px;min-height:var(--bi-content-height);background:var(--bi-background);color:var(--bi-text)}.workbench-heading{display:flex;justify-content:space-between;align-items:center;gap:20px;margin-bottom:24px}.eyebrow{font-size:10px;color:var(--bi-muted);letter-spacing:1.2px;margin-bottom:10px}h1{font-size:30px;letter-spacing:-.5px;margin:0}h2{font-size:17px;margin:0}.workbench-heading p{color:var(--bi-muted);font-size:13px;margin:9px 0 0}.heading-actions{display:flex;gap:12px}.workbench-shortcuts{display:flex;gap:24px;flex-wrap:wrap;padding:4px 0 22px;border-bottom:1px solid var(--bi-border)}.workbench-shortcuts a{display:flex;align-items:center;gap:8px;color:var(--bi-muted);font-size:13px}.workbench-shortcuts .el-icon{font-size:20px;color:var(--el-color-primary)}.workbench-stats{display:flex;flex-wrap:wrap;margin:22px 0;background:var(--bi-surface);border:1px solid var(--bi-border);border-radius:8px;padding:20px 0}.workbench-stats a{flex:1;min-width:120px;border-right:1px solid var(--bi-border);padding:0 26px;display:flex;gap:18px;align-items:center;color:var(--bi-muted);font-size:13px}.workbench-stats a:last-child{border:0}.workbench-stats strong{font-size:27px;color:var(--bi-text)}.workbench-section{background:var(--bi-surface);border:1px solid var(--bi-border);border-radius:8px;padding:20px;min-width:0}.section-heading{display:flex;align-items:center;gap:25px;margin-bottom:18px}.section-heading>.el-button,.section-heading>.muted{margin-left:auto}.section-heading :deep(.el-tabs__header){margin:0}.section-heading :deep(.el-tabs__nav-wrap:after){display:none}.resource-search{margin-left:auto;width:200px}.workbench-resources{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:24px}.workbench-resources article{min-width:0}.workbench-cover{height:195px;overflow:hidden;border:1px solid var(--bi-border);border-radius:6px;background:var(--bi-soft)}.workbench-cover :deep(.resource-thumbnail){height:100%;min-height:0}.resource-name{font:inherit;font-weight:600;font-size:14px;text-align:left;background:none;border:0;color:var(--bi-text);padding:14px 0 4px;cursor:pointer;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.resource-name:hover{color:var(--el-color-primary)}.resource-meta{display:flex;align-items:center;justify-content:space-between;color:var(--bi-muted);font-size:11px}.workbench-bottom{display:grid;grid-template-columns:minmax(0,2fr) minmax(260px,1fr);gap:22px;margin-top:22px}.todo-entry{display:flex;align-items:center;justify-content:space-between;gap:12px;width:100%;padding:18px 0;border:0;border-bottom:1px solid var(--bi-border);background:none;color:var(--bi-text);text-align:left;cursor:pointer}.todo-entry small{display:block;font-size:11px;color:var(--bi-muted);margin-top:8px}.todo-entry b{font-size:12px;color:var(--el-color-primary);white-space:nowrap}.muted{color:var(--bi-muted);font-size:12px}.task-state{font-size:12px;color:var(--bi-muted)}.task-state.SUCCEEDED,.task-state.SUCCESS{color:#179b82}.task-state.FAILED{color:#ed6574}.task-state.RUNNING{color:var(--el-color-primary)}@media(max-width:1100px){.workbench-bottom{grid-template-columns:1fr}.workbench-stats a{padding:0 15px}.workbench-cover{height:150px}}@media(max-width:700px){.platform-workbench{padding:20px 14px}.workbench-resources{grid-template-columns:1fr}.workbench-cover{height:200px}.section-heading{flex-wrap:wrap;gap:10px}.resource-search{width:100%}.workbench-stats{gap:15px}.workbench-stats a{border:0}h1{font-size:24px}.workbench-shortcuts{gap:15px}}
</style>