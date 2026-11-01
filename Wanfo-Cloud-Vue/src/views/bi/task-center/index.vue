<template>
  <div class="task-center-page bi-page" v-loading="loading">
    <header class="page-head bi-page-header">
      <div>
        <div class="eyebrow">DATA OPERATIONS</div>
        <h1 class="bi-page-title">统一任务池</h1>
        <p class="bi-page-desc">聚合质量、扫描、加工、装载、对象入仓、CDC、导出与订阅运行；可按优先级查看，降低仓类任务被饿死的风险。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </header>

    <div class="summary-grid">
      <article class="summary-card"><span>近期待办与历史</span><strong>{{ result?.summary.total ?? 0 }}</strong></article>
      <article class="summary-card active"><span>运行中</span><strong>{{ result?.summary.running ?? 0 }}</strong></article>
      <article class="summary-card failed"><span>失败</span><strong>{{ result?.summary.failed ?? 0 }}</strong></article>
      <article class="summary-card success"><span>成功</span><strong>{{ result?.summary.succeeded ?? 0 }}</strong></article>
      <article class="summary-card"><span>等待/计划</span><strong>{{ result?.summary.waiting ?? 0 }}</strong></article>
    </div>

    <div v-if="laneChips.length" class="lane-row">
      <el-tag v-for="chip in laneChips" :key="chip.key" effect="plain" round>{{ chip.label }} · {{ chip.count }}</el-tag>
    </div>

    <section class="task-panel bi-panel">
      <div class="toolbar bi-filter-bar">
        <el-input v-model="query" clearable placeholder="搜索任务或资源名称" :prefix-icon="Search" class="search" @keyup.enter="search" @clear="search" />
        <el-select v-model="type" clearable placeholder="全部任务类型" @change="search">
          <el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="status" clearable placeholder="全部状态" @change="search">
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-radio-group v-model="sort" @change="search">
          <el-radio-button value="priority">优先级</el-radio-button>
          <el-radio-button value="created">时间</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="result?.items || []" stripe>
        <el-table-column label="优先级" width="90">
          <template #default="{ row }"><span class="prio">{{ row.priority ?? '—' }}</span></template>
        </el-table-column>
        <el-table-column label="任务" min-width="230" show-overflow-tooltip>
          <template #default="{ row }"><div class="task-name">{{ row.name || '未命名任务' }}</div><div class="resource-name">{{ row.resourceName || '—' }}</div></template>
        </el-table-column>
        <el-table-column label="类型" width="140"><template #default="{ row }"><el-tag effect="plain" round>{{ typeLabel(row.type) }}</el-tag></template></el-table-column>
        <el-table-column label="池" width="110"><template #default="{ row }"><el-tag size="small" effect="light">{{ laneLabel(row.poolLane) }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="125"><template #default="{ row }"><el-tag :type="statusType(row.status)" effect="light" round>{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="开始时间" min-width="170"><template #default="{ row }">{{ row.startedAt || row.createdAt || '—' }}</template></el-table-column>
        <el-table-column label="结束时间" min-width="170"><template #default="{ row }">{{ row.finishedAt || '—' }}</template></el-table-column>
        <el-table-column label="信息" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.message || '—' }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row.detailUrl)">查看</el-button>
            <el-button v-if="row.retryable" link type="warning" @click="retry(row)">重试</el-button>
            <el-popconfirm v-if="row.cancelable" title="确定取消这条运行任务？" confirm-button-text="取消任务" cancel-button-text="返回" @confirm="cancel(row)">
              <template #reference><el-button link type="danger">取消</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty><el-empty description="当前筛选条件下没有任务记录" :image-size="72" /></template>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="result?.total || 0" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
    </section>
    <p class="scope-note">{{ result?.summary?.note || '任务中心复用各模块的运行记录；取消和重试仅对后端支持且当前用户拥有相应权限的任务开放。' }}</p>
  </div>
</template>

<script setup lang="ts" name="BiTaskCenter">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { cancelTaskRun, retryTaskRun, listTaskRuns, type TaskRunItem, type TaskCenterResult } from '@/api/bi/taskCenter'
import { unwrapBiData } from '@/api/bi/response'

const router = useRouter()
const loading = ref(false)
const query = ref('')
const type = ref('')
const status = ref('')
const sort = ref<'priority' | 'created'>('priority')
const page = ref(1)
const pageSize = ref(20)
const result = ref<TaskCenterResult>()
const types = [
  { value: 'QUALITY', label: '数据质量' },
  { value: 'METADATA_SCAN', label: '元数据扫描' },
  { value: 'PIPELINE', label: '数据加工' },
  { value: 'WAREHOUSE_LOAD', label: '数仓装载' },
  { value: 'OBJECT_LOAD', label: '对象入仓' },
  { value: 'CDC_JOB', label: 'CDC 编排' },
  { value: 'EXPORT', label: '报表导出' },
  { value: 'SUBSCRIPTION', label: '订阅发送' },
  { value: 'SUBSCRIPTION_SCHEDULE', label: '订阅计划' }
]
const statuses = [
  { value: 'RUNNING', label: '运行中' },
  { value: 'PENDING', label: '等待中' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'WAITING', label: '待触发' },
  { value: 'SUCCEEDED', label: '成功' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'PAUSED', label: '已暂停' }
]
const laneChips = computed(() => {
  const lanes = result.value?.summary?.byLane || {}
  return Object.entries(lanes).map(([key, count]) => ({ key, count, label: laneLabel(key) }))
})

async function load() {
  loading.value = true
  try {
    result.value = unwrapBiData(await listTaskRuns({
      type: type.value, status: status.value, query: query.value, page: page.value, pageSize: pageSize.value, sort: sort.value
    }))
  } finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function open(url: string) { if (url) void router.push(url) }
async function cancel(row: TaskRunItem) {
  try { await cancelTaskRun(row.type, row.id); ElMessage.success('任务已取消'); await load() } catch { /* request toast */ }
}
async function retry(row: TaskRunItem) {
  try { await retryTaskRun(row.type, row.id); ElMessage.success('已重新提交任务'); await load() } catch { /* request toast */ }
}
function typeLabel(value: string) { return types.find(item => item.value === value)?.label || value }
function statusLabel(value: string) { return statuses.find(item => item.value === String(value).toUpperCase())?.label || value }
function statusType(value: string) {
  const s = String(value).toUpperCase()
  if (s === 'FAILED') return 'danger'
  if (['SUCCEEDED', 'SUCCESS'].includes(s)) return 'success'
  if (['RUNNING'].includes(s)) return 'warning'
  return 'info'
}
function laneLabel(value?: string) {
  return ({ ACTIVE: '运行池', FAILED: '失败池', SCHEDULED: '计划池', COMPLETED: '完成池', OTHER: '其他' } as Record<string, string>)[String(value || '')] || value || '—'
}
onMounted(load)
</script>

<style scoped lang="scss">
.task-center-page{padding:var(--bi-page-pad-y,24px) var(--bi-page-pad-x,32px);min-height:calc(100vh - 84px);background:var(--bi-background,#f5f7fb);color:var(--bi-text,#182230)}
.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;gap:12px}
.eyebrow{font-size:11px;font-weight:700;letter-spacing:.12em;color:var(--el-color-primary,#5574f7)}
.page-head h1{margin:6px 0;font-size:26px;color:var(--bi-text,#182230)}
.page-head p{margin:0;color:var(--bi-muted,#718096);max-width:720px}
.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:14px;margin-bottom:12px}
.summary-card,.task-panel{background:var(--bi-surface,#fff);border:1px solid var(--bi-border,#e8edf5);border-radius:var(--bi-radius-lg,12px);box-shadow:var(--bi-shadow,0 3px 14px #14233b08)}
.summary-card{padding:16px 20px;display:flex;align-items:center;justify-content:space-between;color:var(--bi-muted,#64748b)}
.summary-card strong{font-size:24px;color:var(--bi-text,#263449)}
.summary-card.active strong{color:#3478f6}
.summary-card.failed strong{color:#e85c61}
.summary-card.success strong{color:#22a477}
.lane-row{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:14px}
.task-panel{padding:18px}
.toolbar{display:flex;gap:12px;margin-bottom:16px;flex-wrap:wrap;align-items:center;padding:0;border:0;box-shadow:none;background:transparent}
.search{width:300px}
.toolbar .el-select{width:160px}
.task-name{font-weight:600;color:#243247}
.resource-name{font-size:12px;color:#8a95a6;margin-top:4px}
.prio{font-variant-numeric:tabular-nums;color:#526174;font-weight:600}
.pagination{display:flex;justify-content:flex-end;padding-top:16px}
.scope-note{font-size:12px;color:#8792a3;margin:12px 4px}
@media(max-width:960px){.summary-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.search{width:100%}}
</style>
