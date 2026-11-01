<template>
  <div class="app-container">
    <el-card class="mb8">
      <template #header>
        <div class="toolbar">
          <span>数据指标</span>
          <el-button v-hasPermi="['bi:metric:add']" type="primary" :disabled="busy" @click="newMetric()">新建指标</el-button>
        </div>
      </template>
      <el-alert class="mb8" :closable="false" show-icon title="指标定义与查询分离：定义只含口径与计算规则；预览/取数时可选择允许维度内的分组。已发布须先取消发布才能修改；被引用时不可改或下线。" />
      <el-alert v-if="conflicts.length" class="mb8" type="warning" :closable="false" show-icon :title="`检测到 ${conflicts.length} 组同数据集、同度量但口径描述不一致的原子指标。`">
        <div class="conflict-lines"><div v-for="item in conflicts.slice(0, 5)" :key="`${item.leftId}-${item.rightId}-${item.field}`">{{ item.leftCode }} / {{ item.rightCode }} · {{ item.field }} ({{ item.aggregation }}) · 差异：{{ item.differences.join('、') }}</div></div>
      </el-alert>
      <el-table :data="metrics" v-loading="listLoading" border max-height="280">
        <el-table-column prop="code" label="编码" min-width="140" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ kindLabel(row.kind) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="160">
          <template #default="{ row }">
            <el-tag :type="row.publishStatus === 'published' ? 'success' : 'info'" size="small">{{ row.publishStatus === 'published' ? '已发布' : '草稿' }}</el-tag>
            <el-tag v-if="row.pendingPublish" type="warning" size="small" class="ml4">待审</el-tag>
            <el-tag v-if="row.inUse" type="warning" size="small" class="ml4">引用中</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="420" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="busy" @click="openMetric(row)">打开</el-button>
            <el-button link :disabled="busy" @click="openConsumer(row)">口径</el-button>
            <el-button v-if="row.resourceActions.includes('publish')" v-hasPermi="['bi:metric:publish']" link :disabled="busy || row.inUse || (!!row.pendingPublish && row.publishStatus !== 'published')" @click="togglePublish(row)">{{ row.publishStatus === 'published' ? '取消发布' : '提交审批' }}</el-button>
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
      <el-form label-width="100px" class="metric-form">
        <el-form-item label="编码" required>
          <el-input v-model="definition.code" maxlength="64" :disabled="!!currentId || !editable" placeholder="字母开头，2～64 位字母数字下划线" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="definition.name" maxlength="200" :disabled="!editable" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="definition.kind" :disabled="!editable" @change="kindChanged">
            <el-radio value="atomic">原子</el-radio>
            <el-radio value="derived">派生</el-radio>
            <el-radio value="composite">复合</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="definition.kind === 'composite'">
          <el-form-item label="组件指标" required>
            <el-select v-model="definition.componentMetricCodes" multiple filterable allow-create default-first-option style="width:100%" :disabled="!editable" placeholder="已发布原子/派生指标编码">
              <el-option v-for="m in compositeComponentOptions" :key="m.code" :value="m.code" :label="`${m.code} · ${m.name}`" />
            </el-select>
          </el-form-item>
          <el-form-item label="复合表达式" required>
            <el-input v-model="definition.compositeExpression" :disabled="!editable" maxlength="500" placeholder="例如：(gmv_total - cost_total) / gmv_total" />
            <div class="hint">仅标量求值；允许组件编码与 + - * / ( )。</div>
          </el-form-item>
          <el-form-item label="锚点数据集" required>
            <el-select v-model="definition.datasetId" filterable style="width:100%" :disabled="!editable" @change="datasetChanged">
              <el-option v-for="d in datasetOptions" :key="d.id" :value="d.id" :label="d.name" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else-if="definition.kind === 'derived'">
          <el-form-item label="数据集" required>
            <el-select v-model="definition.datasetId" filterable style="width:100%" :disabled="!editable" @change="datasetChanged">
              <el-option v-for="d in datasetOptions" :key="d.id" :value="d.id" :label="d.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="组件 A" required>
            <el-select v-model="derivedACode" filterable style="width:55%" :disabled="!editable" placeholder="已发布原子指标">
              <el-option v-for="m in atomicOptions" :key="m.code" :value="m.code" :label="`${m.code} · ${m.name}`" />
            </el-select>
            <el-input v-model="derivedAAlias" :disabled="!editable" maxlength="32" placeholder="别名" style="width:35%;margin-left:8px" />
          </el-form-item>
          <el-form-item label="组件 B" required>
            <el-select v-model="derivedBCode" filterable style="width:55%" :disabled="!editable" placeholder="已发布原子指标">
              <el-option v-for="m in atomicOptions" :key="m.code" :value="m.code" :label="`${m.code} · ${m.name}`" />
            </el-select>
            <el-input v-model="derivedBAlias" :disabled="!editable" maxlength="32" placeholder="别名" style="width:35%;margin-left:8px" />
          </el-form-item>
          <el-form-item label="派生表达式" required>
            <el-input v-model="definition.expression" :disabled="!editable" maxlength="500" placeholder="例如：gmv/orders" />
          </el-form-item>
          <el-form-item label="允许维度">
            <el-select v-model="definition.allowedDimensions" multiple filterable style="width:100%" :disabled="!editable" placeholder="留空则取两原子允许维度交集">
              <el-option v-for="f in fields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="数据集" required>
            <el-select v-model="definition.datasetId" filterable style="width:100%" :disabled="!editable" @change="datasetChanged">
              <el-option v-for="d in datasetOptions" :key="d.id" :value="d.id" :label="d.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="度量" required>
            <el-select v-model="measureField" filterable :disabled="!editable" style="width:220px">
              <el-option v-for="f in fields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)" />
            </el-select>
            <el-select v-model="measureAgg" :disabled="!editable" style="width:140px;margin-left:8px">
              <el-option v-for="(label, key) in aggregations" :key="key" :value="key" :label="label" />
            </el-select>
          </el-form-item>
          <el-form-item label="允许维度">
            <el-select v-model="definition.allowedDimensions" multiple filterable style="width:100%" :disabled="!editable">
              <el-option v-for="f in fields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)" />
            </el-select>
          </el-form-item>
          <el-form-item label="固定过滤">
            <div v-if="fixedFilter">
              <el-select v-model="fixedFilter.field" filterable :disabled="!editable" style="width:160px">
                <el-option v-for="f in fields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)" />
              </el-select>
              <el-select v-model="fixedFilter.op" :disabled="!editable" style="width:110px;margin-left:8px">
                <el-option value="EQ" label="等于" />
                <el-option value="NE" label="不等于" />
                <el-option value="IN" label="包含" />
              </el-select>
              <el-input v-model="fixedFilterValue" :disabled="!editable" style="width:180px;margin-left:8px" placeholder="值" />
              <el-button link type="danger" :disabled="!editable" @click="clearFixedFilter">清除</el-button>
            </div>
            <el-button v-else link :disabled="!editable || !fields.length" @click="addFixedFilter">添加固定过滤</el-button>
          </el-form-item>
        </template>

        <el-divider content-position="left">业务口径</el-divider>
        <el-form-item label="业务定义" required><el-input v-model="definition.businessDefinition" type="textarea" :rows="2" maxlength="2000" show-word-limit :disabled="!editable" /></el-form-item>
        <el-form-item label="统计粒度" required><el-input v-model="definition.grain" maxlength="100" :disabled="!editable" placeholder="例如：订单日 / 自然月" /></el-form-item>
        <el-form-item label="单位" required><el-input v-model="definition.unit" maxlength="64" :disabled="!editable" /></el-form-item>
        <el-form-item label="时区" required><el-input v-model="definition.timeZone" maxlength="64" :disabled="!editable" placeholder="Asia/Shanghai" /></el-form-item>
        <el-form-item label="负责人" required><el-input v-model="definition.owner" maxlength="128" :disabled="!editable" /></el-form-item>

        <el-divider v-if="definition.kind !== 'composite'" content-position="left">预览查询（运行时）</el-divider>
        <el-form-item v-if="definition.kind !== 'composite'" label="分组维度">
          <el-select v-model="runtimeDimensions" multiple filterable style="width:100%" :disabled="previewing">
            <el-option v-for="name in (definition.allowedDimensions || [])" :key="name" :value="name" :label="fieldLabel(name)" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert v-if="error" type="error" :closable="false" :title="error" class="mb8" />
      <div v-if="result" class="preview">
        <div v-if="isScalarPreview" class="scalar">{{ scalarValue }}</div>
        <el-table v-else :data="result.rows" border max-height="320">
          <el-table-column v-for="col in result.columns" :key="col" :prop="col" :label="col" min-width="120" />
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="grantDialog.visible" :title="'指标授权 · ' + grantDialog.name" width="min(700px, calc(100vw - 24px))">
      <el-alert title="指标授权不自动授予数据集权限。" :closable="false" class="mb8" />
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
        <el-table-column label="类型" width="80"><template #default="{ row }">{{ kindLabel(row.kind) }}</template></el-table-column>
        <el-table-column prop="publishBy" label="发布人" width="120" />
        <el-table-column prop="businessDefinition" label="冻结业务定义" min-width="220" show-overflow-tooltip />
        <el-table-column prop="grain" label="粒度" width="120" />
        <el-table-column prop="unit" label="单位" width="100" />
        <el-table-column prop="timeZone" label="时区" width="150" />
        <el-table-column prop="owner" label="负责人" width="130" />
        <el-table-column prop="publishTime" label="发布时间" min-width="170" />
      </el-table>
    </el-dialog>

    <el-card v-if="pendingPublish.length" class="mb8">
      <template #header><div class="toolbar"><span>待审批发布</span><el-button link @click="loadPendingPublish">刷新</el-button></div></template>
      <el-table :data="pendingPublish" border>
        <el-table-column prop="id" label="单号" width="80" />
        <el-table-column prop="metricId" label="指标 ID" width="100" />
        <el-table-column prop="requestedBy" label="提交人" width="120" />
        <el-table-column prop="requestNote" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="metricVersion" label="版本" width="80" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" :disabled="busy" @click="decidePublish(row.id, true)">通过</el-button>
            <el-button link type="danger" :disabled="busy" @click="decidePublish(row.id, false)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="consumerVisible" title="消费侧口径" size="min(480px, 94vw)" destroy-on-close>
      <template v-if="consumer">
        <el-alert :closable="false" show-icon :title="consumer.pinNote" class="mb8" />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="编码">{{ consumer.code }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ consumer.name }}</el-descriptions-item>
          <el-descriptions-item label="发布版本">{{ consumer.publishedVersion || '—' }}</el-descriptions-item>
          <el-descriptions-item label="业务定义">{{ consumer.businessDefinition || '—' }}</el-descriptions-item>
          <el-descriptions-item label="允许维度">{{ (consumer.allowedDimensions || []).join(', ') || consumer.businessDimensions || '—' }}</el-descriptions-item>
          <el-descriptions-item label="粒度">{{ consumer.grain || '—' }}</el-descriptions-item>
          <el-descriptions-item label="单位">{{ consumer.unit || '—' }}</el-descriptions-item>
          <el-descriptions-item label="时区">{{ consumer.timeZone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ consumer.owner || '—' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>
<script setup lang="ts" name="BiMetric">
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import { listDatasets, type DatasetSummary } from '@/api/bi/dataset'
import {
  listMetrics, listMetricConflicts, getMetric, saveMetric, deleteMetric, previewMetric, savedMetricData,
  publishMetric, unpublishMetric, metricHistory, metricFields, searchMetricPrincipals, listMetricGrants,
  grantMetric, revokeMetricGrant, listPendingMetricPublishRequests, decideMetricPublish, metricConsumerContext,
  type MetricDefinition, type MetricSummary, type MetricHistory, type MetricConflict, type MetricPublishRequest,
  type MetricConsumerContext, type MetricQueryRequest, type MetricFilter
} from '@/api/bi/metric'
import type { Field, ChartResult } from '@/api/bi/chart'
import type { GrantPrincipal, ResourceGrant } from '@/api/bi/datasource'

const aggregations: Record<string, string> = { COUNT: '计数', COUNT_DISTINCT: '去重计数', SUM: '求和', AVG: '平均值', MIN: '最小值', MAX: '最大值' }

function empty(): MetricDefinition {
  return {
    schemaVersion: 2, name: '', code: '', kind: 'atomic', datasetId: 0,
    measure: { field: '', agg: 'SUM' }, fixedFilters: [], allowedDimensions: [],
    components: [], expression: '', componentMetricCodes: [], compositeExpression: '',
    businessDefinition: '', grain: '', unit: '', timeZone: 'Asia/Shanghai', owner: ''
  }
}
function kindLabel(kind: string) {
  return kind === 'atomic' ? '原子' : kind === 'derived' ? '派生' : kind === 'composite' ? '复合' : kind
}

const definition = reactive<MetricDefinition>(empty())
const metrics = ref<MetricSummary[]>([]), datasets = ref<DatasetSummary[]>([]), fields = ref<Field[]>([]), result = ref<ChartResult | null>(null)
const currentId = ref<number>(), version = ref<number>(), editorVisible = ref(false), loading = ref(false), saving = ref(false), previewing = ref(false), listLoading = ref(false), error = ref(''), baseline = ref('')
const conflicts = ref<MetricConflict[]>([])
const runtimeDimensions = ref<string[]>([])
const derivedACode = ref(''), derivedAAlias = ref('gmv'), derivedBCode = ref(''), derivedBAlias = ref('orders')
const fixedFilter = ref<MetricFilter | null>(null)
const fixedFilterValue = ref('')
let revision = 0, loadVersion = 0

const fingerprint = () => JSON.stringify({ definition, derivedACode: derivedACode.value, derivedAAlias: derivedAAlias.value, derivedBCode: derivedBCode.value, derivedBAlias: derivedBAlias.value, fixedFilter: fixedFilter.value, fixedFilterValue: fixedFilterValue.value })
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
  : checkPermi(['bi:dataset:query']) && !!definition.datasetId)
const datasetOptions = computed(() => {
  const id = definition.datasetId
  return datasets.value.some(d => d.id === id) || !id ? datasets.value : [...datasets.value, { id, name: '数据集 #' + id } as DatasetSummary]
})
const atomicOptions = computed(() => metrics.value.filter(m => m.kind === 'atomic' && m.publishStatus === 'published' && (!definition.datasetId || m.datasetId === definition.datasetId)))
const compositeComponentOptions = computed(() => metrics.value.filter(m => (m.kind === 'atomic' || m.kind === 'derived') && m.publishStatus === 'published'))
const measureField = computed({
  get: () => definition.measure?.field || '',
  set: (v: string) => { if (!definition.measure) definition.measure = { field: v, agg: 'SUM' }; else definition.measure.field = v }
})
const measureAgg = computed({
  get: () => definition.measure?.agg || 'SUM',
  set: (v: string) => { if (!definition.measure) definition.measure = { field: '', agg: v }; else definition.measure.agg = v }
})
const fieldLabel = (name: string) => { const f = fields.value.find(item => item.name === name); return f?.showName?.trim() || name }
const isScalarPreview = computed(() => !(runtimeDimensions.value?.length) || definition.kind === 'composite')
const scalarValue = computed(() => {
  const row = result.value?.rows?.[0]
  if (!row) return '—'
  if ('value' in row) return String(row.value)
  return String(Object.values(row)[0] ?? '—')
})

watch(fingerprint, () => { revision++; result.value = null; error.value = '' })

async function discard() {
  if (!dirty.value) return true
  try { await ElMessageBox.confirm('当前指标有未保存的配置，是否放弃？', '未保存的修改', { type: 'warning' }); return true } catch { return false }
}
async function refresh() {
  listLoading.value = true
  try {
    const [metricRows, conflictRows] = await Promise.all([listMetrics(), listMetricConflicts()])
    metrics.value = metricRows.data || []
    conflicts.value = conflictRows.data || []
  } finally { listLoading.value = false }
}
async function availableDatasets() {
  if (checkPermi(['bi:dataset:list'])) datasets.value = ((await listDatasets()).data || []).filter(d => d.type === 'table' && d.resourceActions?.includes('query'))
}
function numeric(field: Field) { return /(int|decimal|numeric|float|double|real|number)/i.test(field.type) }
function kindChanged() {
  if (definition.kind === 'atomic') {
    definition.components = []
    definition.expression = ''
    definition.componentMetricCodes = []
    definition.compositeExpression = ''
    if (!definition.measure) definition.measure = { field: '', agg: 'SUM' }
  } else if (definition.kind === 'derived') {
    definition.measure = undefined
    definition.fixedFilters = []
    fixedFilter.value = null
    if (!derivedAAlias.value) derivedAAlias.value = 'a'
    if (!derivedBAlias.value) derivedBAlias.value = 'b'
  } else {
    definition.measure = undefined
    definition.allowedDimensions = []
    definition.components = []
    definition.expression = ''
    runtimeDimensions.value = []
  }
}
function addFixedFilter() {
  fixedFilter.value = { field: fields.value[0]?.name || '', op: 'EQ', value: '' }
  fixedFilterValue.value = ''
}
function clearFixedFilter() { fixedFilter.value = null; fixedFilterValue.value = '' }

function buildDefinitionPayload(): MetricDefinition {
  const snapshot = JSON.parse(JSON.stringify(definition)) as MetricDefinition
  snapshot.name = snapshot.name.trim()
  snapshot.code = snapshot.code.trim()
  snapshot.schemaVersion = 2
  if (snapshot.kind === 'atomic') {
    snapshot.measure = { field: measureField.value, agg: measureAgg.value }
    snapshot.fixedFilters = fixedFilter.value
      ? [{ field: fixedFilter.value.field, op: fixedFilter.value.op, value: fixedFilterValue.value }]
      : []
    snapshot.components = undefined
    snapshot.expression = undefined
    snapshot.componentMetricCodes = undefined
    snapshot.compositeExpression = undefined
  } else if (snapshot.kind === 'derived') {
    snapshot.components = [
      { code: derivedACode.value, alias: derivedAAlias.value.trim() },
      { code: derivedBCode.value, alias: derivedBAlias.value.trim() }
    ]
    snapshot.measure = undefined
    snapshot.fixedFilters = []
    snapshot.componentMetricCodes = undefined
    snapshot.compositeExpression = undefined
  } else {
    snapshot.measure = undefined
    snapshot.fixedFilters = []
    snapshot.allowedDimensions = []
    snapshot.components = undefined
    snapshot.expression = undefined
  }
  return snapshot
}

function runtimeQuery(): MetricQueryRequest | undefined {
  if (definition.kind === 'composite') return undefined
  return {
    dimensions: runtimeDimensions.value.map(field => ({ field, alias: field })),
    filters: [],
    limit: runtimeDimensions.value.length ? 1000 : 1
  }
}

async function newMetric() {
  if (!await discard()) return
  ++loadVersion; ++revision; currentId.value = undefined; version.value = undefined; fields.value = []; result.value = null
  Object.assign(definition, empty())
  derivedACode.value = ''; derivedBCode.value = ''; derivedAAlias.value = 'gmv'; derivedBAlias.value = 'orders'
  fixedFilter.value = null; fixedFilterValue.value = ''; runtimeDimensions.value = []
  editorVisible.value = true
  await nextTick(); baseline.value = fingerprint()
}
async function datasetChanged(id: number) {
  const v = ++loadVersion; ++revision; result.value = null; error.value = ''; fields.value = []
  definition.datasetId = id
  definition.allowedDimensions = []
  runtimeDimensions.value = []
  loading.value = true
  try {
    const r = await metricFields(id)
    if (v !== loadVersion) return
    fields.value = r.data || []
    if (definition.kind === 'atomic') {
      const metric = fields.value.find(numeric) || fields.value[0]
      if (metric) { measureField.value = metric.name; measureAgg.value = 'SUM' }
    }
  } catch { if (v === loadVersion) error.value = '字段加载失败，请检查数据集和列权限' }
  finally { if (v === loadVersion) loading.value = false }
}
async function openMetric(row: MetricSummary) {
  if (!await discard()) return
  const v = ++loadVersion; ++revision; loading.value = true; result.value = null; error.value = ''; editorVisible.value = false
  try {
    const r = await getMetric(row.id)
    const def = r.data.definition
    const f = await metricFields(def.datasetId)
    if (v !== loadVersion) return
    Object.assign(definition, empty(), def)
    if (!definition.allowedDimensions) definition.allowedDimensions = []
    if (def.kind === 'derived' && def.components?.length === 2) {
      derivedACode.value = def.components[0].code
      derivedAAlias.value = def.components[0].alias
      derivedBCode.value = def.components[1].code
      derivedBAlias.value = def.components[1].alias
    }
    if (def.kind === 'atomic' && def.fixedFilters?.length) {
      fixedFilter.value = { ...def.fixedFilters[0] }
      fixedFilterValue.value = String(def.fixedFilters[0].value ?? '')
    } else {
      fixedFilter.value = null
      fixedFilterValue.value = ''
    }
    fields.value = f.data || []
    runtimeDimensions.value = []
    currentId.value = row.id
    version.value = r.data.version
    editorVisible.value = true
    await nextTick(); baseline.value = fingerprint()
  } finally { if (v === loadVersion) loading.value = false }
}
async function preview() {
  if (!definition.datasetId) { ElMessage.warning('请选择数据集'); return }
  const snapshot = buildDefinitionPayload()
  const query = runtimeQuery()
  const r = revision
  previewing.value = true; result.value = null; error.value = ''
  try {
    const response = currentId.value && !dirty.value
      ? await savedMetricData(currentId.value, query)
      : await previewMetric(snapshot, query)
    if (r === revision) result.value = response.data
  } catch { if (r === revision) error.value = '预览失败，请检查定义、允许维度及数据权限' }
  finally { previewing.value = false }
}
async function save() {
  if (!definition.code.trim() || !/^[A-Za-z][A-Za-z0-9_]{1,63}$/.test(definition.code.trim())) { ElMessage.warning('请填写有效编码'); return }
  if (!definition.name.trim()) { ElMessage.warning('请填写指标名称'); return }
  if (!definition.datasetId) { ElMessage.warning('请选择数据集'); return }
  if (definition.kind === 'atomic' && (!measureField.value || !measureAgg.value)) { ElMessage.warning('请配置度量'); return }
  if (definition.kind === 'derived') {
    if (!derivedACode.value || !derivedBCode.value || !definition.expression?.trim()) { ElMessage.warning('派生指标须配置两个组件与表达式'); return }
  }
  if (definition.kind === 'composite' && (!definition.componentMetricCodes?.length || !definition.compositeExpression?.trim())) {
    ElMessage.warning('复合指标须填写组件编码与表达式'); return
  }
  const snapshot = buildDefinitionPayload()
  saving.value = true; error.value = ''
  try {
    const r = await saveMetric(snapshot, currentId.value, version.value)
    currentId.value = r.data.id; version.value = r.data.version
    Object.assign(definition, snapshot); await nextTick(); baseline.value = fingerprint(); await refresh()
    ElMessage.success('指标已保存')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg && msg !== 'error'
      ? msg
      : '保存未完成。若仍提示「请选择指标类型并配置查询」，说明 BI 后端未升级到标准指标（atomic/derived/composite），请重新编译并重启 ruoyi-modules-bi。'
  } finally { saving.value = false }
}
async function togglePublish(row: MetricSummary) {
  if (row.inUse) { ElMessage.warning('指标已被引用，不能取消发布'); return }
  const action = row.publishStatus === 'published' ? '取消发布' : '提交发布审批'
  try { await ElMessageBox.confirm(`确定${action}指标「${row.name}」？`, action, { type: 'warning' }) } catch { return }
  saving.value = true
  try {
    if (row.publishStatus === 'published') {
      await unpublishMetric(row.id, row.version)
      if (currentId.value === row.id) version.value = row.version + 1
    } else {
      await publishMetric(row.id, row.version)
    }
    await refresh()
    await loadPendingPublish()
    if (currentId.value === row.id) await openMetric((await listMetrics()).data?.find(m => m.id === row.id) || row)
    ElMessage.success(row.publishStatus === 'published' ? '已取消发布' : '已提交审批')
  } finally { saving.value = false }
}
const pendingPublish = ref<MetricPublishRequest[]>([])
async function loadPendingPublish() {
  if (!checkPermi(['bi:metric:publish'])) { pendingPublish.value = []; return }
  try { pendingPublish.value = (await listPendingMetricPublishRequests()).data || [] }
  catch { pendingPublish.value = [] }
}
async function decidePublish(requestId: number, approve: boolean) {
  const action = approve ? '通过并发布' : '驳回'
  try { await ElMessageBox.confirm(`确定${action}？`, action, { type: approve ? 'success' : 'warning' }) } catch { return }
  saving.value = true
  try {
    await decideMetricPublish(requestId, approve)
    await Promise.all([refresh(), loadPendingPublish()])
    ElMessage.success('已' + action)
  } finally { saving.value = false }
}
const consumerVisible = ref(false)
const consumer = ref<MetricConsumerContext | null>(null)
async function openConsumer(row: MetricSummary) {
  consumer.value = (await metricConsumerContext(row.id)).data || null
  consumerVisible.value = true
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
onMounted(async () => { await Promise.all([refresh(), availableDatasets(), loadPendingPublish()]) })
</script>
<style scoped>
.toolbar{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}
.ml4{margin-left:4px}
.metric-form{max-width:960px}
.conflict-lines{display:grid;gap:4px;margin-top:6px;font-size:12px}
.hint{margin-top:6px;color:#667085;font-size:12px}
.preview{margin-top:8px}
.scalar{font-size:36px;font-weight:600;padding:24px 0}
</style>
