<template>
  <div class="warehouse-ops-page bi-page" v-loading="loading">
    <header class="page-head">
      <div><div class="eyebrow">WAREHOUSE OPS</div><h1>数仓装载运维</h1><p>管理员触发 JDBC → Doris / StarRocks / ClickHouse 入仓，查看活动运行并重试失败任务。</p></div>
      <el-button v-hasPermi="['bi:resource:admin']" :icon="Refresh" @click="refresh">刷新</el-button>
    </header>

    <div class="summary-grid">
      <article class="summary-card"><span>运行中</span><strong>{{ overview?.activeRuns ?? 0 }}</strong></article>
      <article class="summary-card warn"><span>疑似孤儿</span><strong>{{ overview?.staleRuns ?? 0 }}</strong></article>
      <article class="summary-card note"><span>{{ overview?.note || '—' }}</span></article>
    </div>

    <section class="panel">
      <div class="panel-title">触发入仓</div>
      <el-alert class="mb8" type="info" :closable="false" show-icon title="目标端支持 Doris、StarRocks、ClickHouse；分区与并行切片为可选高级参数。" />
      <el-form :model="form" label-width="120px" class="load-form">
        <el-form-item label="源数据源" required>
          <el-select v-model="form.sourceDatasourceId" filterable placeholder="JDBC 业务源" style="width:100%">
            <el-option v-for="s in sourceOptions" :key="s.id" :label="`${s.name} · ${s.type}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标数据源" required>
          <el-select v-model="form.targetDatasourceId" filterable placeholder="数仓目标" style="width:100%" @change="loadRuns">
            <el-option v-for="t in targetOptions" :key="t.id" :label="`${t.name} · ${t.type}`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="源表" required><el-input v-model="form.sourceTable" maxlength="256" placeholder="schema.table 或 table" /></el-form-item>
        <el-form-item label="目标表" required><el-input v-model="form.targetTable" maxlength="128" placeholder="例如 ods_retail_order" /></el-form-item>
        <el-form-item label="数仓层">
          <el-select v-model="form.targetLayer" style="width:160px">
            <el-option v-for="layer in ['ODS','DWD','DWS','ADS']" :key="layer" :label="layer" :value="layer" />
          </el-select>
        </el-form-item>
        <el-form-item label="装载模式">
          <el-radio-group v-model="form.loadMode"><el-radio-button value="FULL">全量</el-radio-button><el-radio-button value="INCREMENTAL">增量</el-radio-button></el-radio-group>
        </el-form-item>
        <el-form-item label="发布模式">
          <el-radio-group v-model="form.publishMode">
            <el-radio-button value="REPLACE">REPLACE</el-radio-button>
            <el-radio-button value="MERGE">MERGE</el-radio-button>
          </el-radio-group>
          <span class="hint">增量默认 MERGE；全量默认 REPLACE</span>
        </el-form-item>
        <template v-if="needsMergeKeys">
          <el-form-item label="水位字段"><el-input v-model="form.watermarkColumn" maxlength="128" /></el-form-item>
          <el-form-item label="业务键"><el-input v-model="form.keyColumnsText" placeholder="逗号分隔" /></el-form-item>
        </template>
        <el-form-item label="分区列"><el-input v-model="form.partitionColumn" maxlength="128" placeholder="可选" /></el-form-item>
        <el-form-item label="分区起止"><el-input v-model="form.partitionFrom" placeholder="From" style="width:160px" /><span class="sep">—</span><el-input v-model="form.partitionTo" placeholder="To" style="width:160px" /></el-form-item>
        <el-form-item label="最大行数"><el-input-number v-model="form.maxRows" :min="1" :max="1000000" :step="10000" /></el-form-item>
        <el-form-item label="并行切片"><el-input-number v-model="form.parallelSlices" :min="1" :max="8" placeholder="可选 1～8" /> <span class="hint">仅全量分区装载生效</span></el-form-item>
        <el-form-item label="筛选数据集"><el-select v-model="form.filterDatasetId" clearable filterable placeholder="可选，行级策略数据集" style="width:100%"><el-option v-for="d in datasetOptions" :key="d.id" :label="`${d.name} · #${d.id}`" :value="d.id" /></el-select></el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['bi:resource:admin']" type="primary" :loading="submitting" :disabled="!ready" @click="trigger">提交入仓</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel">
      <div class="panel-head"><strong>运行记录</strong><el-button link :loading="runsLoading" @click="loadRuns">刷新</el-button></div>
      <el-table :data="runs" size="small" border :empty-text="runsLoading ? '读取中' : '暂无记录'">
        <el-table-column prop="id" label="运行号" width="80" />
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="Promote" width="110"><template #default="{ row }"><span>{{ row.promoteStatus || '—' }}</span></template></el-table-column>
        <el-table-column prop="sourceTable" label="源表" min-width="140" show-overflow-tooltip />
        <el-table-column prop="targetTable" label="目标表" min-width="140" show-overflow-tooltip />
        <el-table-column prop="loadMode" label="装载" width="90" />
        <el-table-column prop="publishMode" label="发布" width="90" />
        <el-table-column prop="rowCount" label="行数" width="90" />
        <el-table-column prop="triggerBy" label="触发人" width="110" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="摘要" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorSummary || row.message || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button v-if="row.status === 'FAILED'" v-hasPermi="['bi:resource:admin']" link type="warning" @click="retry(row.id)">重试</el-button></template></el-table-column>
      </el-table>
    </section>

    <section v-if="overview?.recentRuns?.length" class="panel">
      <div class="panel-title">平台最近入仓（概览）</div>
      <el-table :data="overview.recentRuns" size="small" border>
        <el-table-column prop="id" label="运行号" width="80" />
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="Promote" width="110"><template #default="{ row }">{{ row.promoteStatus || '—' }}</template></el-table-column>
        <el-table-column prop="publishMode" label="发布" width="90" />
        <el-table-column prop="targetTable" label="目标表" min-width="160" />
        <el-table-column prop="createTime" label="时间" min-width="160" />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts" name="BiWarehouse">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDatasources, type BiDatasource } from '@/api/bi/datasource'
import { listDatasets, type DatasetSummary } from '@/api/bi/dataset'
import { listWarehouseLoadRuns, opsOverview, retryWarehouseLoad, triggerWarehouseLoad, type WarehouseLoadOpsOverview, type WarehouseLoadRun } from '@/api/bi/warehouse'
import { unwrapBiData } from '@/api/bi/response'

const WAREHOUSE_TYPES = ['clickhouse', 'ck', 'doris', 'starrocks']
const loading = ref(false)
const runsLoading = ref(false)
const submitting = ref(false)
const sources = ref<BiDatasource[]>([])
const datasets = ref<DatasetSummary[]>([])
const overview = ref<WarehouseLoadOpsOverview>()
const runs = ref<WarehouseLoadRun[]>([])
const form = reactive({
  sourceDatasourceId: undefined as number | undefined,
  targetDatasourceId: undefined as number | undefined,
  sourceTable: '',
  targetTable: '',
  targetLayer: 'ODS' as string,
  loadMode: 'FULL' as 'FULL' | 'INCREMENTAL',
  publishMode: 'REPLACE' as 'REPLACE' | 'MERGE',
  watermarkColumn: '',
  keyColumnsText: '',
  partitionColumn: '',
  partitionFrom: '',
  partitionTo: '',
  maxRows: 100000,
  parallelSlices: undefined as number | undefined,
  filterDatasetId: undefined as number | undefined
})
const sourceOptions = computed(() => sources.value.filter(s => !WAREHOUSE_TYPES.includes(String(s.type || '').toLowerCase())))
const targetOptions = computed(() => sources.value.filter(s => WAREHOUSE_TYPES.includes(String(s.type || '').toLowerCase())))
const datasetOptions = computed(() => datasets.value)
const needsMergeKeys = computed(() => form.publishMode === 'MERGE' || form.loadMode === 'INCREMENTAL')
const ready = computed(() => Boolean(form.sourceDatasourceId && form.targetDatasourceId && form.sourceTable.trim() && form.targetTable.trim()))
watch(() => form.loadMode, mode => {
  form.publishMode = mode === 'INCREMENTAL' ? 'MERGE' : 'REPLACE'
})

async function refresh() {
  loading.value = true
  try {
    overview.value = unwrapBiData(await opsOverview())
    await Promise.all([loadSources(), loadRuns()])
  } catch {
    ElMessage.error('读取运维概览失败')
  } finally {
    loading.value = false
  }
}
async function loadSources() {
  sources.value = unwrapBiData(await listDatasources()) || []
  if (!form.sourceDatasourceId) form.sourceDatasourceId = sourceOptions.value[0]?.id
  if (!form.targetDatasourceId) {
    const recentTarget = Number(overview.value?.recentRuns?.[0]?.targetDatasourceId || 0)
    form.targetDatasourceId = (recentTarget && targetOptions.value.some(t => t.id === recentTarget)
      ? recentTarget
      : targetOptions.value[0]?.id)
  }
  try {
    datasets.value = unwrapBiData(await listDatasets()) || []
  } catch {
    datasets.value = []
  }
}
async function loadRuns() {
  if (!form.targetDatasourceId) {
    runs.value = []
    return
  }
  runsLoading.value = true
  try {
    runs.value = unwrapBiData(await listWarehouseLoadRuns(form.targetDatasourceId)) || []
  } catch {
    runs.value = []
  } finally {
    runsLoading.value = false
  }
}
async function trigger() {
  if (!ready.value) return
  const keyColumns = form.keyColumnsText.split(',').map(v => v.trim()).filter(Boolean)
  try {
    await ElMessageBox.confirm(`确认从源表 ${form.sourceTable} 装载到 ${form.targetTable}？`, '提交入仓', { type: 'warning' })
  } catch {
    return
  }
  submitting.value = true
  try {
    await triggerWarehouseLoad({
      sourceDatasourceId: form.sourceDatasourceId!,
      targetDatasourceId: form.targetDatasourceId!,
      sourceTable: form.sourceTable.trim(),
      targetTable: form.targetTable.trim(),
      maxRows: form.maxRows,
      loadMode: form.loadMode,
      publishMode: form.publishMode,
      watermarkColumn: form.watermarkColumn.trim() || undefined,
      keyColumns: keyColumns.length ? keyColumns : undefined,
      targetLayer: form.targetLayer,
      partitionColumn: form.partitionColumn.trim() || undefined,
      partitionFrom: form.partitionFrom.trim() || undefined,
      partitionTo: form.partitionTo.trim() || undefined,
      filterDatasetId: form.filterDatasetId || undefined,
      parallelSlices: form.parallelSlices && form.parallelSlices > 1 ? form.parallelSlices : undefined
    })
    ElMessage.success('入仓任务已提交')
    await refresh()
  } catch {
    ElMessage.error('提交入仓失败')
  } finally {
    submitting.value = false
  }
}
async function retry(id: number) {
  try {
    await retryWarehouseLoad(id)
    ElMessage.success('已重新提交')
    await refresh()
  } catch {
    ElMessage.error('重试失败')
  }
}
function statusLabel(status: string) {
  return ({ PENDING: '排队中', RUNNING: '运行中', SUCCEEDED: '成功', FAILED: '失败', INTERRUPTED: '中断' } as Record<string, string>)[status] || status
}
function statusTag(status: string) {
  return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'RUNNING' ? 'warning' : 'info'
}
onMounted(refresh)
</script>

<style scoped lang="scss">
.warehouse-ops-page{padding:var(--bi-page-pad-y,24px) var(--bi-page-pad-x,32px);min-height:calc(100vh - 84px);background:var(--bi-background,#f5f7fb);color:var(--bi-text,#1f2a3d)}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.eyebrow{font-size:11px;font-weight:700;letter-spacing:.14em;color:var(--el-color-primary,#5574f7)}.page-head h1{margin:6px 0;font-size:26px}.page-head p{margin:0;color:var(--bi-muted,#718096);font-size:13px}.summary-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-bottom:16px}.summary-card,.panel{background:var(--bi-surface,#fff);border:1px solid var(--bi-border,#e8edf5);border-radius:var(--bi-radius-lg,12px);box-shadow:var(--bi-shadow,0 3px 14px #14233b08);padding:16px 18px}.summary-card{display:flex;align-items:center;justify-content:space-between;color:var(--bi-muted,#64748b)}.summary-card strong{font-size:24px;color:var(--bi-text,#263449)}.summary-card.warn strong{color:#e85c61}.summary-card.note{font-size:12px;line-height:1.5;color:#8792a3}.panel{margin-bottom:14px}.panel-title,.panel-head{font-weight:600;margin-bottom:12px;display:flex;align-items:center;justify-content:space-between}.load-form{max-width:720px}.mb8{margin-bottom:12px}.sep{margin:0 8px;color:#94a3b8}.hint{margin-left:8px;color:#94a3b8;font-size:12px}@media(max-width:900px){.summary-grid{grid-template-columns:1fr}}
</style>
