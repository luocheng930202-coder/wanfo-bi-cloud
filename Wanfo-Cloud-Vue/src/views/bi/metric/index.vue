<template>
  <div class="app-container">
    <el-card class="mb8">
      <template #header>
        <div class="toolbar">
          <span>数据指标</span>
          <el-button v-hasPermi="['bi:metric:add']" type="primary" :disabled="busy" @click="newMetric()">新建指标</el-button>
        </div>
      </template>
      <el-alert class="mb8" :closable="false" show-icon title="已发布指标必须先取消发布才能修改。若已被其他地方引用，不允许修改或下线。按编码取数时不再接受额外条件。" />
      <el-table :data="metrics" v-loading="listLoading" border max-height="280">
        <el-table-column prop="code" label="编码" min-width="140" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.kind === 'scalar' ? '标量' : '表格' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.publishStatus === 'published' ? 'success' : 'info'" size="small">{{ row.publishStatus === 'published' ? '已发布' : '草稿' }}</el-tag>
            <el-tag v-if="row.inUse" type="warning" size="small" class="ml4">引用中</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="busy" @click="openMetric(row)">打开</el-button>
            <el-button v-if="row.resourceActions.includes('publish')" v-hasPermi="['bi:metric:publish']" link :disabled="busy || row.inUse" @click="togglePublish(row)">{{ row.publishStatus === 'published' ? '取消发布' : '发布' }}</el-button>
            <el-button v-if="row.resourceActions.includes('grant')" v-hasPermi="['bi:metric:grant']" link :disabled="busy" @click="openGrants(row)">授权</el-button>
            <el-button v-if="row.resourceActions.includes('remove')" v-hasPermi="['bi:metric:remove']" link type="danger" :disabled="busy || row.publishStatus === 'published' || row.inUse" @click="removeMetric(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无有权查看的指标" /></template>
      </el-table>
    </el-card>

    <el-card v-if="editorVisible">
      <template #header>
        <div class="toolbar">
          <span>{{ currentId ? '指标 · ' + definition.name : '新建指标' }}
            <el-tag v-if="dirty" size="small" type="warning">未保存</el-tag>
            <el-tag v-if="!editable" size="small" type="info">只读</el-tag>
            <el-tag v-if="selected?.publishStatus === 'published'" size="small" type="success">已发布冻结</el-tag>
          </span>
          <div class="toolbar">
            <el-button :loading="previewing" :disabled="busy || !canPreview" @click="preview">预览</el-button>
            <el-button v-if="currentId" :disabled="busy" @click="openHistory">历史版本</el-button>
            <el-button v-if="editable" type="primary" :loading="saving" :disabled="busy || !dirty" @click="save">保存</el-button>
          </div>
        </div>
      </template>
      <el-form label-width="90px" class="metric-form">
        <el-form-item label="编码" required>
          <el-input v-model="definition.code" maxlength="64" :disabled="!!currentId || !editable" placeholder="字母开头，2～64 位字母数字下划线" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="definition.name" maxlength="200" :disabled="!editable" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="definition.kind" :disabled="!editable" @change="kindChanged">
            <el-radio value="scalar">标量</el-radio>
            <el-radio value="table">表格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数据集" required>
          <el-select v-model="definition.query.datasetId" filterable style="width:100%" :disabled="!editable" @change="datasetChanged">
            <el-option v-for="d in datasetOptions" :key="d.id" :value="d.id" :label="d.name" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="definition.kind === 'table'" label="维度">
          <el-select v-model="dimensionFields" multiple filterable style="width:100%" :disabled="!editable">
            <el-option v-for="f in fields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)" />
          </el-select>
        </el-form-item>
        <el-form-item label="度量" required>
          <div class="metric-rows">
            <div v-for="(m, index) in definition.query.metrics" :key="index" class="metric-row">
              <el-select v-model="m.field" filterable :disabled="!editable" style="width:220px">
                <el-option v-for="f in fields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)" />
              </el-select>
              <el-select v-model="m.agg" :disabled="!editable" style="width:140px">
                <el-option v-for="(label, key) in aggregations" :key="key" :value="key" :label="label" :disabled="definition.kind === 'scalar' && key === 'NONE'" />
              </el-select>
              <el-input v-model="m.alias" :disabled="!editable" placeholder="别名" maxlength="64" style="width:160px" />
              <el-button v-if="definition.kind === 'table'" link type="danger" :disabled="!editable" @click="removeMeasure(index)">移除</el-button>
            </div>
            <el-button v-if="definition.kind === 'table'" link :disabled="!editable || !fields.length" @click="addMeasure">添加度量</el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="definition.kind === 'table'" label="行数">
          <el-input-number v-model="definition.query.limit" :min="1" :max="1000" :disabled="!editable" />
        </el-form-item>
        <el-form-item label="筛选">
          <div v-if="definition.query.filterTree">
            <FilterTreeEditor :model-value="definition.query.filterTree" :fields="fields" :disabled="!editable" @update:model-value="value => definition.query.filterTree = value" @remove="definition.query.filterTree = undefined" />
          </div>
          <el-button v-else link :disabled="!editable || !fields.length" @click="definition.query.filterTree = { field: fields[0]?.name || '', op: 'EQ', value: '' }">添加筛选</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="error" type="error" :closable="false" :title="error" class="mb8" />
      <div v-if="result" class="preview">
        <div v-if="definition.kind === 'scalar'" class="scalar">{{ scalarValue }}</div>
        <el-table v-else :data="result.rows" border max-height="320">
          <el-table-column v-for="col in result.columns" :key="col" :prop="col" :label="col" min-width="120" />
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="grantDialog.visible" :title="'指标授权 · ' + grantDialog.name" width="min(700px, calc(100vw - 24px))">
      <el-alert title="指标授权不自动授予数据集权限；使用者还需对应数据集的查看、查询及若依功能权限。" :closable="false" class="mb8" />
      <div class="toolbar mb8">
        <el-select v-model="grantDialog.principalId" filterable remote :remote-method="searchPrincipals" placeholder="搜索用户、角色或部门">
          <el-option v-for="p in principals" :key="p.id" :value="p.id" :label="p.type + ' · ' + p.name" />
        </el-select>
        <el-select v-model="grantDialog.action" style="width:130px">
          <el-option v-for="(label, key) in grantActions" :key="key" :value="key" :label="label" />
        </el-select>
        <el-button :loading="grantSaving" :disabled="!grantDialog.principalId" @click="addGrant">授权</el-button>
      </div>
      <el-table :data="grants" border>
        <el-table-column prop="displayName" label="对象" />
        <el-table-column label="权限"><template #default="{ row }">{{ grantActions[row.action] }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="danger" :disabled="grantSaving" @click="removeGrant(row)">撤销</el-button></template></el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="historyDialog.visible" title="发布历史" width="min(720px, calc(100vw - 24px))">
      <el-table :data="historyDialog.rows" border>
        <el-table-column prop="publishedVersion" label="版本" width="80" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column label="类型" width="80"><template #default="{ row }">{{ row.kind === 'scalar' ? '标量' : '表格' }}</template></el-table-column>
        <el-table-column prop="publishBy" label="发布人" width="120" />
        <el-table-column prop="publishTime" label="发布时间" min-width="170" />
      </el-table>
    </el-dialog>
  </div>
</template>
<script setup lang="ts" name="BiMetric">
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import { listDatasets, type DatasetSummary } from '@/api/bi/dataset'
import { listMetrics, getMetric, saveMetric, deleteMetric, previewMetric, savedMetricData, publishMetric, unpublishMetric, metricHistory, metricFields, searchMetricPrincipals, listMetricGrants, grantMetric, revokeMetricGrant, type MetricDefinition, type MetricSummary, type MetricHistory } from '@/api/bi/metric'
import type { Field, ChartResult } from '@/api/bi/chart'
import type { GrantPrincipal, ResourceGrant } from '@/api/bi/datasource'
import FilterTreeEditor from '../chart/FilterTreeEditor.vue'

const aggregations: Record<string, string> = { COUNT: '计数', COUNT_DISTINCT: '去重计数', SUM: '求和', AVG: '平均值', MIN: '最小值', MAX: '最大值', NONE: '原始值' }
function empty(): MetricDefinition {
  return { schemaVersion: 1, name: '', code: '', kind: 'scalar', query: { datasetId: 0, dimensions: [], metrics: [], filters: [], orderBys: [], limit: 1 } }
}
const definition = reactive<MetricDefinition>(empty())
const metrics = ref<MetricSummary[]>([]), datasets = ref<DatasetSummary[]>([]), fields = ref<Field[]>([]), result = ref<ChartResult | null>(null)
const currentId = ref<number>(), version = ref<number>(), editorVisible = ref(false), loading = ref(false), saving = ref(false), previewing = ref(false), listLoading = ref(false), error = ref(''), baseline = ref('')
let revision = 0, loadVersion = 0
const fingerprint = () => JSON.stringify(definition)
const dirty = computed(() => editorVisible.value && fingerprint() !== baseline.value)
const busy = computed(() => loading.value || saving.value || previewing.value)
const selected = computed(() => metrics.value.find(m => m.id === currentId.value))
const published = computed(() => selected.value?.publishStatus === 'published')
const editable = computed(() => {
  if (!currentId.value) return checkPermi(['bi:metric:add'])
  return !!selected.value?.resourceActions.includes('edit') && checkPermi(['bi:metric:edit']) && !published.value && !selected.value.inUse
})
const canPreview = computed(() => currentId.value && !dirty.value
  ? !!selected.value?.resourceActions.includes('query') && checkPermi(['bi:metric:query'])
  : checkPermi(['bi:dataset:query']) && !!definition.query.datasetId)
const datasetOptions = computed(() => {
  const id = definition.query.datasetId
  return datasets.value.some(d => d.id === id) || !id ? datasets.value : [...datasets.value, { id, name: '数据集 #' + id } as DatasetSummary]
})
const dimensionFields = computed({
  get: () => definition.query.dimensions.map(d => d.field),
  set: (names: string[]) => { definition.query.dimensions = names.map(field => ({ field, alias: field })) }
})
const fieldLabel = (name: string) => { const f = fields.value.find(item => item.name === name); return f?.showName?.trim() || name }
const scalarValue = computed(() => {
  const row = result.value?.rows?.[0]
  if (!row) return '—'
  const alias = definition.query.metrics[0]?.alias || definition.query.metrics[0]?.field
  return alias && alias in row ? String(row[alias]) : String(Object.values(row)[0] ?? '—')
})
watch(fingerprint, () => { revision++; result.value = null; error.value = '' })
async function discard() {
  if (!dirty.value) return true
  try { await ElMessageBox.confirm('当前指标有未保存的配置，是否放弃？', '未保存的修改', { type: 'warning' }); return true } catch { return false }
}
async function refresh() { listLoading.value = true; try { metrics.value = (await listMetrics()).data || [] } finally { listLoading.value = false } }
async function availableDatasets() {
  if (checkPermi(['bi:dataset:list'])) datasets.value = ((await listDatasets()).data || []).filter(d => d.type === 'table' && d.resourceActions?.includes('query'))
}
function numeric(field: Field) { return /(int|decimal|numeric|float|double|real|number)/i.test(field.type) }
function kindChanged() {
  if (definition.kind === 'scalar') {
    definition.query.dimensions = []
    definition.query.metrics = definition.query.metrics.slice(0, 1)
    if (definition.query.metrics[0]) definition.query.metrics[0].agg = definition.query.metrics[0].agg === 'NONE' ? 'COUNT' : definition.query.metrics[0].agg
    definition.query.limit = 1
  } else if (!definition.query.limit || definition.query.limit === 1) definition.query.limit = 100
}
function addMeasure() {
  const pick = fields.value.find(numeric) || fields.value[0]
  if (pick) definition.query.metrics.push({ field: pick.name, agg: 'SUM', alias: 'value_' + (definition.query.metrics.length + 1) })
}
function removeMeasure(index: number) { definition.query.metrics.splice(index, 1) }
async function newMetric() {
  if (!await discard()) return
  ++loadVersion; ++revision; currentId.value = undefined; version.value = undefined; fields.value = []; result.value = null
  Object.assign(definition, empty()); editorVisible.value = true
  await nextTick(); baseline.value = fingerprint()
}
async function datasetChanged(id: number) {
  const v = ++loadVersion; ++revision; result.value = null; error.value = ''; fields.value = []
  Object.assign(definition.query, { datasetId: id, dimensions: [], metrics: [], filters: [], filterTree: undefined, orderBys: [], limit: definition.kind === 'scalar' ? 1 : 100 })
  loading.value = true
  try {
    const r = await metricFields(id)
    if (v !== loadVersion) return
    fields.value = r.data || []
    const metric = fields.value.find(numeric) || fields.value[0]
    if (metric) definition.query.metrics = [{ field: metric.name, agg: 'COUNT', alias: 'value_1' }]
    if (definition.kind === 'table') {
      const dim = fields.value.find(f => !numeric(f)) || fields.value[0]
      if (dim) definition.query.dimensions = [{ field: dim.name, alias: dim.name }]
    }
  } catch { if (v === loadVersion) error.value = '字段加载失败，请检查数据集和列权限' }
  finally { if (v === loadVersion) loading.value = false }
}
async function openMetric(row: MetricSummary) {
  if (!await discard()) return
  const v = ++loadVersion; ++revision; loading.value = true; result.value = null; error.value = ''; editorVisible.value = false
  try {
    const r = await getMetric(row.id)
    const f = await metricFields(r.data.definition.query.datasetId)
    if (v !== loadVersion) return
    Object.assign(definition, r.data.definition)
    if (!definition.query.filters) definition.query.filters = []
    if (!definition.query.orderBys) definition.query.orderBys = []
    fields.value = f.data || []
    currentId.value = row.id
    version.value = r.data.version
    editorVisible.value = true
    await nextTick(); baseline.value = fingerprint()
  } finally { if (v === loadVersion) loading.value = false }
}
async function preview() {
  if (!definition.query.datasetId) { ElMessage.warning('请选择数据集'); return }
  const snapshot = fingerprint(), r = revision
  previewing.value = true; result.value = null; error.value = ''
  try {
    const response = currentId.value && !dirty.value ? await savedMetricData(currentId.value) : await previewMetric(JSON.parse(snapshot))
    if (r === revision && snapshot === fingerprint()) result.value = response.data
  } catch { if (r === revision) error.value = '预览失败，请检查字段、筛选条件及当前数据权限' }
  finally { previewing.value = false }
}
async function save() {
  if (!definition.code.trim() || !/^[A-Za-z][A-Za-z0-9_]{1,63}$/.test(definition.code.trim())) { ElMessage.warning('请填写有效编码'); return }
  if (!definition.name.trim()) { ElMessage.warning('请填写指标名称'); return }
  if (definition.kind === 'scalar') { definition.query.dimensions = []; definition.query.limit = 1 }
  const snapshot = JSON.parse(fingerprint()) as MetricDefinition
  snapshot.name = snapshot.name.trim(); snapshot.code = snapshot.code.trim()
  saving.value = true; error.value = ''
  try {
    const r = await saveMetric(snapshot, currentId.value, version.value)
    currentId.value = r.data.id; version.value = r.data.version
    Object.assign(definition, snapshot); await nextTick(); baseline.value = fingerprint(); await refresh()
    ElMessage.success('指标已保存')
  } catch { error.value = '保存未完成。若提示版本冲突，请重新打开后再保存' }
  finally { saving.value = false }
}
async function togglePublish(row: MetricSummary) {
  if (row.inUse) { ElMessage.warning('指标已被引用，不能取消发布'); return }
  const action = row.publishStatus === 'published' ? '取消发布' : '发布'
  try { await ElMessageBox.confirm(`确定${action}指标「${row.name}」？`, action, { type: 'warning' }) } catch { return }
  saving.value = true
  try {
    if (row.publishStatus === 'published') await unpublishMetric(row.id, row.version)
    else await publishMetric(row.id, row.version)
    if (currentId.value === row.id) version.value = row.version + 1
    await refresh()
    if (currentId.value === row.id) await openMetric((await listMetrics()).data?.find(m => m.id === row.id) || row)
    ElMessage.success('已' + action)
  } finally { saving.value = false }
}
async function removeMetric(row: MetricSummary) {
  try { await ElMessageBox.confirm(`确定删除指标「${row.name}」？`, '删除指标', { type: 'warning' }) } catch { return }
  if (currentId.value === row.id && !await discard()) return
  saving.value = true
  try {
    await deleteMetric(row.id)
    if (currentId.value === row.id) { editorVisible.value = false; currentId.value = undefined; result.value = null }
    await refresh(); ElMessage.success('指标已删除')
  } finally { saving.value = false }
}
const historyDialog = reactive({ visible: false, rows: [] as MetricHistory[] })
async function openHistory() {
  if (!currentId.value) return
  historyDialog.rows = (await metricHistory(currentId.value)).data || []
  historyDialog.visible = true
}
const grantActions: Record<string, string> = { view: '查看', query: '取数', edit: '编辑', publish: '发布', remove: '删除', grant: '授权管理' }
const grantDialog = reactive({ visible: false, id: 0, name: '', principalId: undefined as number | undefined, action: 'view' })
const principals = ref<GrantPrincipal[]>([]), grants = ref<ResourceGrant[]>([]), grantSaving = ref(false)
let principalVersion = 0
async function searchPrincipals(query = '') { const v = ++principalVersion, r = await searchMetricPrincipals(query); if (v === principalVersion) principals.value = r.data || [] }
async function openGrants(row: MetricSummary) {
  Object.assign(grantDialog, { visible: true, id: row.id, name: row.name, principalId: undefined, action: 'view' })
  grants.value = []; await Promise.all([searchPrincipals(), reloadGrants()])
}
async function reloadGrants() { const id = grantDialog.id, r = await listMetricGrants(id); if (id === grantDialog.id) grants.value = r.data || [] }
async function addGrant() {
  if (!grantDialog.principalId) return
  grantSaving.value = true
  try { await grantMetric(grantDialog.id, grantDialog.principalId, grantDialog.action); await reloadGrants(); ElMessage.success('已授权') }
  finally { grantSaving.value = false }
}
async function removeGrant(row: ResourceGrant) {
  grantSaving.value = true
  try { await revokeMetricGrant(grantDialog.id, row.principalId, row.action); await reloadGrants(); ElMessage.success('已撤销') }
  finally { grantSaving.value = false }
}
onMounted(async () => { await Promise.all([refresh(), availableDatasets()]) })
</script>
<style scoped>
.toolbar{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}
.ml4{margin-left:4px}
.metric-form{max-width:960px}
.metric-rows{display:flex;flex-direction:column;gap:8px}
.metric-row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
.preview{margin-top:8px}
.scalar{font-size:36px;font-weight:600;padding:24px 0}
</style>
