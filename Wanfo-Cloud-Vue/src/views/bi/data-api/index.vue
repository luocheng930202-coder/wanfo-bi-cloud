<template>
  <div class="app-container bi-page">
    <el-card>
      <template #header>
        <div class="toolbar">
          <div><strong>开放数据 API</strong><div class="hint">绑定已发布指标，发布后可轮换 API Key；调用审计不含请求参数。版本门户展示 30 日调用与废弃下线窗口。</div></div>
          <div>
            <el-button @click="refresh">刷新</el-button>
            <el-button v-hasPermi="['bi:resource:admin']" @click="openCallers">调用方名录</el-button>
            <el-button v-hasPermi="['bi:resource:admin']" type="primary" @click="openCreate">新建 API</el-button>
          </div>
        </div>
      </template>
      <el-alert v-if="portal" class="mb8" type="info" :closable="false" show-icon :title="portalSummaryTitle">
        <template #default>{{ portal.note }}</template>
      </el-alert>
      <el-table :data="portal?.items || rows" v-loading="loading" border>
        <el-table-column prop="apiCode" label="编码" min-width="140" />
        <el-table-column prop="familyCode" label="族" min-width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="指标" width="100"><template #default="{ row }">#{{ row.metricId }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="apiVersion" label="契约版本" width="90" />
        <el-table-column label="下线" min-width="140">
          <template #default="{ row }">
            <span v-if="row.pastSunset" class="sunset-late">已过 sunset</span>
            <span v-else-if="row.sunsetAt">{{ row.sunsetAt }}</span>
            <span v-else>—</span>
            <div v-if="row.successorApiCode" class="hint">→ {{ row.successorApiCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="30日调用" width="110">
          <template #default="{ row }">{{ row.callCount30d ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="调用组织" min-width="160">
          <template #default="{ row }">
            <span v-if="!(row.callerOrgs || []).length">—</span>
            <el-tag v-for="c in (row.callerOrgs || []).slice(0, 3)" :key="c.orgCode" size="small" class="ml4" effect="plain">{{ c.orgName }}·{{ c.callCount30d }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="调用 Key" min-width="140">
          <template #default="{ row }">
            <span v-if="!(row.callerKeyPrefixes || []).length">—</span>
            <el-tag v-for="c in (row.callerKeyPrefixes || []).slice(0, 2)" :key="c.keyPrefix" size="small" class="ml4" effect="plain">{{ c.keyPrefix }}·{{ c.callCount30d }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="callsPerMinute" label="每分钟" width="90" />
        <el-table-column prop="dailyQuota" label="日配额" width="90" />
        <el-table-column label="操作" width="720" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['bi:resource:admin']" link @click="editRow(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['bi:resource:admin']" link @click="contractCheck(row)">契约校验</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['bi:resource:admin']" link type="success" @click="publish(row)">发布</el-button>
            <el-button v-if="row.status === 'ACTIVE' || row.status === 'DEPRECATED'" v-hasPermi="['bi:resource:admin']" link type="primary" @click="nextVersion(row)">下一版本</el-button>
            <el-button v-if="row.status === 'ACTIVE' || row.status === 'DEPRECATED'" v-hasPermi="['bi:resource:admin']" link @click="contractReplay(row)">契约回放</el-button>
            <el-button v-if="row.status === 'ACTIVE' || row.status === 'DEPRECATED'" v-hasPermi="['bi:resource:admin']" link @click="contractSampleReplay(row)">样本回放</el-button>
            <el-button v-if="row.status === 'ACTIVE' || row.status === 'DEPRECATED'" v-hasPermi="['bi:resource:admin']" link @click="refreshContractSample(row)">刷新样本</el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-hasPermi="['bi:resource:admin']" link @click="rotate(row)">轮换 Key</el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-hasPermi="['bi:resource:admin']" link type="warning" @click="deprecate(row)">废弃</el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-hasPermi="['bi:resource:admin']" link type="warning" @click="autoDeprecate(row)">废弃同族旧版</el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-hasPermi="['bi:resource:admin']" link @click="setCanary(row)">金丝雀</el-button>
            <el-button v-if="row.status === 'DEPRECATED'" v-hasPermi="['bi:resource:admin']" link @click="undeprecate(row)">取消废弃</el-button>
            <el-button v-if="row.status === 'ACTIVE' || row.status === 'DEPRECATED'" v-hasPermi="['bi:resource:admin']" link type="danger" @click="disable(row)">停用</el-button>
            <el-button link @click="openGrants(row)">授权</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无数据 API" /></template>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑数据 API' : '新建数据 API'" width="560px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="form.code" :disabled="!!form.id" maxlength="64" placeholder="字母开头" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="已发布指标" required>
          <el-select v-model="form.metricId" filterable style="width:100%">
            <el-option v-for="m in publishedMetrics" :key="m.id" :label="`${m.name} · ${m.code}`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="契约版本"><el-input-number v-model="form.apiVersion" :min="1" :max="9999" /></el-form-item>
        <el-form-item label="单页上限"><el-input-number v-model="form.maxPageSize" :min="1" :max="1000" /></el-form-item>
        <el-form-item label="超时(秒)"><el-input-number v-model="form.timeoutSeconds" :min="1" :max="120" /></el-form-item>
        <el-form-item label="每分钟配额"><el-input-number v-model="form.callsPerMinute" :min="1" :max="10000" /></el-form-item>
        <el-form-item label="日配额"><el-input-number v-model="form.dailyQuota" :min="1" :max="1000000" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="API 详情" width="min(920px, 96vw)" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border size="small" class="mb8">
          <el-descriptions-item label="编码">{{ detail.apiCode }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="指标">#{{ detail.metricId }} · v{{ detail.metricPublishedVersion || '—' }}</el-descriptions-item>
          <el-descriptions-item label="契约版本">{{ detail.apiVersion || 1 }}</el-descriptions-item>
          <el-descriptions-item label="配额">{{ detail.callsPerMinute }}/分 · {{ detail.dailyQuota }}/日</el-descriptions-item>
          <el-descriptions-item v-if="detail.sunsetAt" label="计划下线">{{ detail.sunsetAt }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.successorApiCode" label="继任编码">{{ detail.successorApiCode }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.deprecationNote" label="废弃说明">{{ detail.deprecationNote }}</el-descriptions-item>
        </el-descriptions>
        <div class="toolbar mb8">
          <el-button @click="loadOpenApi">OpenAPI 文档</el-button>
          <el-button @click="openGrantsFromDetail">授权管理</el-button>
        </div>
        <el-tabs>
          <el-tab-pane label="API Key">
            <el-table :data="detail.keys || []" border size="small">
              <el-table-column prop="keyPrefix" label="前缀" width="120" />
              <el-table-column label="调用方" min-width="140">
                <template #default="{ row }">{{ row.callerOrgName || row.callerOrgCode || '—' }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="90" />
              <el-table-column prop="expiresAt" label="过期" min-width="140" />
              <el-table-column prop="lastUsedAt" label="最近使用" min-width="140" />
              <el-table-column label="操作" width="160">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'ACTIVE'" link @click="assignCaller(row)">绑定组织</el-button>
                  <el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="revokeKey(row.id)">撤销</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="调用审计">
            <el-table :data="detail.calls || []" border size="small" max-height="320">
              <el-table-column prop="calledAt" label="时间" width="170" />
              <el-table-column prop="status" label="状态" width="90" />
              <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
              <el-table-column prop="rowCount" label="行数" width="80" />
              <el-table-column prop="errorCode" label="错误码" min-width="120" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="openapiVisible" title="OpenAPI 描述" width="min(760px, 96vw)">
      <pre class="openapi-pre">{{ openapiText }}</pre>
      <template #footer><el-button @click="openapiVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="keyDialog.visible" title="一次性 API Key" width="520px">
      <el-alert type="warning" :closable="false" show-icon title="明文 Key 仅展示一次，请立即复制保存。" />
      <el-input v-model="keyDialog.value" readonly class="mt8"><template #append><el-button @click="copyKey">复制</el-button></template></el-input>
    </el-dialog>

    <el-dialog v-model="callerDialog.visible" title="调用方组织名录" width="min(760px, 96vw)" destroy-on-close>
      <div class="toolbar mb8">
        <el-input v-model="callerForm.orgCode" placeholder="编码" style="width:140px" maxlength="64" />
        <el-input v-model="callerForm.orgName" placeholder="组织名称" style="flex:1" maxlength="200" />
        <el-input v-model="callerForm.contactName" placeholder="联系人" style="width:120px" maxlength="64" />
        <el-button type="primary" :loading="callerSaving" @click="saveCaller">登记</el-button>
      </div>
      <el-table :data="callerRows" border size="small" max-height="360">
        <el-table-column prop="orgCode" label="编码" width="120" />
        <el-table-column prop="orgName" label="组织" min-width="160" />
        <el-table-column prop="contactName" label="联系人" width="100" />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="disableCaller(row)">停用</el-button>
            <el-button v-else link type="success" @click="enableCaller(row)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="grantDialog.visible" :title="'API 授权 · ' + grantDialog.name" width="min(700px, calc(100vw - 24px))">
      <div class="toolbar mb8">
        <el-select v-model="grantDialog.principalId" filterable remote :remote-method="searchPrincipals" placeholder="搜索用户、角色或部门" style="flex:1">
          <el-option v-for="p in principals" :key="p.id" :value="p.id" :label="p.type + ' · ' + p.name" />
        </el-select>
        <el-select v-model="grantDialog.action" style="width:130px">
          <el-option v-for="(label, key) in grantActions" :key="key" :value="key" :label="label" />
        </el-select>
        <el-button :loading="grantSaving" :disabled="!grantDialog.principalId" @click="addGrant">授权</el-button>
      </div>
      <el-table :data="grants" border>
        <el-table-column prop="displayName" label="对象" />
        <el-table-column label="权限"><template #default="{ row }">{{ grantActions[row.action] || row.action }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="danger" :disabled="grantSaving" @click="removeGrant(row)">撤销</el-button></template></el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="BiDataApi">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMetrics, type MetricSummary } from '@/api/bi/metric'
import { searchGrantPrincipals, type GrantPrincipal, type ResourceGrant } from '@/api/bi/datasource'
import {
  assignDataApiKeyCaller, autoDeprecateDataApiSiblings, checkDataApiContract, createDataApi, createDataApiCaller, deprecateDataApi, disableDataApi, getDataApi, getDataApiOpenApiDoc, getDataApiPortal, grantDataApi, listDataApiCallers, listDataApiGrants, listDataApis,
  nextDataApiVersion, publishDataApi, refreshDataApiContractSample, replayDataApiContract, replayDataApiContractSample, revokeDataApiGrant, revokeDataApiKey, rotateDataApiKey, setDataApiCanary, undeprecateDataApi, updateDataApi, updateDataApiCaller,
  type DataApiCaller, type DataApiDetail, type DataApiPortal, type DataApiSummary
} from '@/api/bi/dataApi'

const loading = ref(false)
const saving = ref(false)
const rows = ref<DataApiSummary[]>([])
const portal = ref<DataApiPortal | null>(null)
const metrics = ref<MetricSummary[]>([])
const portalSummaryTitle = computed(() => {
  const s = portal.value?.summary
  if (!s) return '版本门户'
  return `版本门户 · 共 ${s.total} · 发布 ${s.active} · 废弃 ${s.deprecated}（已过 sunset ${s.pastSunset}） · 草稿 ${s.draft} · 调用方 ${s.registeredCallers ?? 0} · 族 ${s.families ?? 0}（并存 ${s.multiActiveFamilies ?? 0}）`
})
const formVisible = ref(false)
const detailVisible = ref(false)
const openapiVisible = ref(false)
const openapiText = ref('')
const detail = ref<DataApiDetail>()
const form = reactive({ id: 0, code: '', name: '', metricId: undefined as number | undefined, apiVersion: 1, maxPageSize: 100, timeoutSeconds: 20, callsPerMinute: 60, dailyQuota: 10000 })
const keyDialog = reactive({ visible: false, value: '' })
const grantActions: Record<string, string> = { view: '查看', manage: '管理', grant: '授权', remove: '删除' }
const grantDialog = reactive({ visible: false, id: 0, name: '', principalId: undefined as number | undefined, action: 'view' })
const principals = ref<GrantPrincipal[]>([])
const grants = ref<ResourceGrant[]>([])
const grantSaving = ref(false)
const callerDialog = reactive({ visible: false })
const callerRows = ref<DataApiCaller[]>([])
const callerForm = reactive({ orgCode: '', orgName: '', contactName: '' })
const callerSaving = ref(false)
const activeCallers = ref<DataApiCaller[]>([])
let principalVersion = 0

const publishedMetrics = computed(() => metrics.value.filter(m => m.publishStatus === 'published'))

async function refresh() {
  loading.value = true
  try {
    const [apiRes, portalRes, metricRes] = await Promise.all([listDataApis(), getDataApiPortal(), listMetrics()])
    rows.value = apiRes.data || []
    portal.value = portalRes.data || null
    metrics.value = metricRes.data || []
  } finally {
    loading.value = false
  }
}
async function openCallers() {
  callerRows.value = (await listDataApiCallers(false)).data || []
  Object.assign(callerForm, { orgCode: '', orgName: '', contactName: '' })
  callerDialog.visible = true
}
async function saveCaller() {
  if (!callerForm.orgCode.trim() || !callerForm.orgName.trim()) {
    ElMessage.warning('请填写编码与组织名称')
    return
  }
  callerSaving.value = true
  try {
    await createDataApiCaller({ orgCode: callerForm.orgCode.trim(), orgName: callerForm.orgName.trim(), contactName: callerForm.contactName.trim() || undefined })
    callerRows.value = (await listDataApiCallers(false)).data || []
    Object.assign(callerForm, { orgCode: '', orgName: '', contactName: '' })
    await refresh()
    ElMessage.success('已登记调用方')
  } finally {
    callerSaving.value = false
  }
}
async function disableCaller(row: DataApiCaller) {
  await updateDataApiCaller(row.id, { orgName: row.orgName, contactName: row.contactName, contactEmail: row.contactEmail, notes: row.notes, status: 'DISABLED' })
  callerRows.value = (await listDataApiCallers(false)).data || []
  await refresh()
}
async function enableCaller(row: DataApiCaller) {
  await updateDataApiCaller(row.id, { orgName: row.orgName, contactName: row.contactName, contactEmail: row.contactEmail, notes: row.notes, status: 'ACTIVE' })
  callerRows.value = (await listDataApiCallers(false)).data || []
  await refresh()
}
async function assignCaller(row: { id: number }) {
  if (!detail.value) return
  activeCallers.value = (await listDataApiCallers(true)).data || []
  if (!activeCallers.value.length) {
    ElMessage.warning('请先在调用方名录中登记组织')
    return
  }
  const options = activeCallers.value.map(c => `${c.id}:${c.orgCode} · ${c.orgName}`).join('\n')
  try {
    const { value } = await ElMessageBox.prompt(`输入调用方 ID 绑定到 Key（可选列表）\n${options}`, '绑定调用方组织', {
      inputPlaceholder: 'callerId，留空清除绑定',
      confirmButtonText: '绑定'
    })
    const callerId = value?.trim() ? Number(value.trim()) : null
    if (value?.trim() && Number.isNaN(callerId as number)) {
      ElMessage.error('callerId 无效')
      return
    }
    detail.value = (await assignDataApiKeyCaller(detail.value.id, row.id, callerId)).data
    await refresh()
    ElMessage.success(callerId ? '已绑定调用方' : '已清除绑定')
  } catch {
    /* cancel */
  }
}
function openCreate() {
  Object.assign(form, { id: 0, code: '', name: '', metricId: publishedMetrics.value[0]?.id, apiVersion: 1, maxPageSize: 100, timeoutSeconds: 20, callsPerMinute: 60, dailyQuota: 10000 })
  formVisible.value = true
}
function editRow(row: DataApiSummary) {
  Object.assign(form, { id: row.id, code: row.apiCode, name: row.name, metricId: row.metricId, apiVersion: row.apiVersion || 1, maxPageSize: row.maxPageSize, timeoutSeconds: row.timeoutSeconds, callsPerMinute: row.callsPerMinute, dailyQuota: row.dailyQuota })
  formVisible.value = true
}
async function save() {
  if (!form.code.trim() || !form.name.trim() || !form.metricId) {
    ElMessage.warning('请填写编码、名称并选择指标')
    return
  }
  saving.value = true
  try {
    const payload = { code: form.code.trim(), name: form.name.trim(), metricId: form.metricId, apiVersion: form.apiVersion, maxPageSize: form.maxPageSize, timeoutSeconds: form.timeoutSeconds, callsPerMinute: form.callsPerMinute, dailyQuota: form.dailyQuota }
    if (form.id) await updateDataApi(form.id, payload)
    else await createDataApi(payload)
    formVisible.value = false
    await refresh()
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}
async function openDetail(row: DataApiSummary) {
  detail.value = (await getDataApi(row.id)).data
  detailVisible.value = true
}
async function contractCheck(row: DataApiSummary) {
  const res = await checkDataApiContract(row.id)
  const check = res.data
  if (!check) return
  const issues = (check.issues || []).map(i => `[${i.severity}] ${i.message}`).join('\n') || '无问题'
  if (check.passed) ElMessage.success(`契约校验通过（样本 ${check.sampleRows ?? 0} 行）`)
  else ElMessage.error(`契约门禁未通过：${(check.issues || []).filter(i => i.severity === 'BLOCKING').map(i => i.message).join('；')}`)
  await ElMessageBox.alert(issues, check.passed ? '契约校验通过' : '契约校验未通过', { type: check.passed ? 'success' : 'error' })
}
async function contractReplay(row: DataApiSummary) {
  const res = await replayDataApiContract(row.id)
  const check = res.data
  if (!check) return
  const issues = (check.issues || []).map(i => `[${i.severity}] ${i.message}`).join('\n') || '与冻结契约一致'
  if (check.passed) ElMessage.success('契约回放通过（相对冻结契约）')
  else ElMessage.error('契约回放发现阻断差异')
  await ElMessageBox.alert(issues, check.passed ? '契约回放通过' : '契约回放未通过', { type: check.passed ? 'success' : 'error' })
}
async function contractSampleReplay(row: DataApiSummary) {
  const res = await replayDataApiContractSample(row.id)
  const check = res.data
  if (!check) return
  const issues = (check.issues || []).map(i => `[${i.severity}] ${i.message}`).join('\n') || '发布样本与冻结契约一致'
  if (check.passed) ElMessage.success('发布样本回放通过（不探测实时指标）')
  else ElMessage.error('发布样本与冻结契约不一致')
  await ElMessageBox.alert(issues, check.passed ? '样本回放通过' : '样本回放未通过', { type: check.passed ? 'success' : 'error' })
}
async function refreshContractSample(row: DataApiSummary) {
  const res = await refreshDataApiContractSample(row.id)
  ElMessage.success(`已刷新发布样本（${res.data?.sampleRows ?? 0} 行脱敏预览）`)
}
async function autoDeprecate(row: DataApiSummary) {
  try {
    await ElMessageBox.confirm(
      `将同 family 其它 ACTIVE 版本废弃，并指向「${row.code}」为继任（默认 90 天 sunset）？`,
      '废弃同族旧版',
      { type: 'warning' }
    )
  } catch {
    return
  }
  const res = await autoDeprecateDataApiSiblings(row.id, { sunsetDays: 90 })
  ElMessage.success(`已废弃 ${res.data?.deprecatedCount ?? 0} 个同族旧版`)
  await refresh()
}
async function setCanary(row: DataApiSummary) {
  try {
    const { value } = await ElMessageBox.prompt(
      '设置金丝雀比例（0 关闭；1～100 表示命中 family 稳定编码时路由到本版本的百分比）',
      '金丝雀流量',
      { inputValue: '10', inputPattern: /^(100|[1-9]?\d)$/, inputErrorMessage: '请输入 0～100 整数' }
    )
    const percent = Number(value)
    const res = await setDataApiCanary(row.id, percent)
    ElMessage.success(res.data?.note || `已设置金丝雀 ${percent}%`)
    await refresh()
  } catch {
    /* cancel */
  }
}
async function publish(row: DataApiSummary) {
  try {
    await ElMessageBox.confirm(`发布 API「${row.name}」前将执行契约门禁；通过后冻结响应列契约。继续？`, '发布', { type: 'warning' })
  } catch {
    return
  }
  const res = await publishDataApi(row.id)
  if (res.data?.oneTimeKey) showKey(res.data.oneTimeKey)
  await refresh()
  if (detailVisible.value && detail.value?.id === row.id) detail.value = res.data
  ElMessage.success('已发布并冻结契约')
}
async function nextVersion(row: DataApiSummary) {
  try {
    await ElMessageBox.confirm(
      `从「${row.apiCode}」拉出同族下一版本草稿？旧 ACTIVE 可继续并存，迁移后再废弃。`,
      '下一版本',
      { type: 'info' }
    )
  } catch {
    return
  }
  const res = await nextDataApiVersion(row.id)
  await refresh()
  if (res.data?.id) {
    detail.value = res.data
    detailVisible.value = true
  }
  ElMessage.success(res.data?.coexistenceHint || '已创建下一版本草稿')
}
async function rotate(row: DataApiSummary) {
  activeCallers.value = (await listDataApiCallers(true)).data || []
  let callerId: number | undefined
  if (activeCallers.value.length) {
    try {
      const { value } = await ElMessageBox.prompt(
        `可选绑定调用方 ID（${activeCallers.value.map(c => `${c.id}:${c.orgCode}`).join('，')}）`,
        '轮换 Key',
        { inputPlaceholder: 'callerId，可留空', confirmButtonText: '轮换' }
      )
      if (value?.trim()) {
        callerId = Number(value.trim())
        if (Number.isNaN(callerId)) {
          ElMessage.error('callerId 无效')
          return
        }
      }
    } catch {
      return
    }
  }
  const res = await rotateDataApiKey(row.id, 90, callerId)
  if (res.data?.oneTimeKey) showKey(res.data.oneTimeKey)
  await refresh()
}
async function disable(row: DataApiSummary) {
  try {
    await ElMessageBox.confirm(`停用 API「${row.name}」？`, '停用', { type: 'warning' })
  } catch {
    return
  }
  await disableDataApi(row.id)
  await refresh()
  ElMessage.success('已停用')
}
async function deprecate(row: DataApiSummary) {
  try {
    await ElMessageBox.confirm(`将「${row.name}」标记为废弃（默认 90 天下线，调用仍可用直至 sunset）？`, '废弃 API', { type: 'warning' })
  } catch {
    return
  }
  await deprecateDataApi(row.id, { sunsetDays: 90, note: '请迁移到新契约编码；Key 体系不变' })
  await refresh()
  ElMessage.success('已标记废弃')
}
async function undeprecate(row: DataApiSummary) {
  await undeprecateDataApi(row.id)
  await refresh()
  ElMessage.success('已取消废弃')
}
async function revokeKey(keyId: number) {
  if (!detail.value) return
  await revokeDataApiKey(detail.value.id, keyId)
  detail.value = (await getDataApi(detail.value.id)).data
}
function showKey(value: string) {
  keyDialog.value = value
  keyDialog.visible = true
}
async function copyKey() {
  try {
    await navigator.clipboard.writeText(keyDialog.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}
async function loadOpenApi() {
  if (!detail.value) return
  try {
    const doc = (await getDataApiOpenApiDoc(detail.value.id)).data
    openapiText.value = JSON.stringify(doc, null, 2)
    openapiVisible.value = true
  } catch {
    ElMessage.error('OpenAPI 文档暂不可用')
  }
}
function statusLabel(status: string) {
  return ({ DRAFT: '草稿', ACTIVE: '已发布', DEPRECATED: '已废弃', DISABLED: '已停用' } as Record<string, string>)[status] || status
}
function statusType(status: string) {
  return status === 'ACTIVE' ? 'success' : status === 'DEPRECATED' ? 'warning' : status === 'DISABLED' ? 'info' : 'warning'
}
async function searchPrincipals(query = '') {
  const v = ++principalVersion
  const r = await searchGrantPrincipals(query)
  if (v === principalVersion) principals.value = r.data || []
}
async function openGrants(row: DataApiSummary) {
  Object.assign(grantDialog, { visible: true, id: row.id, name: row.name, principalId: undefined, action: 'view' })
  grants.value = []
  await Promise.all([searchPrincipals(), reloadGrants()])
}
function openGrantsFromDetail() {
  if (!detail.value) return
  void openGrants(detail.value)
}
async function reloadGrants() {
  const id = grantDialog.id
  const r = await listDataApiGrants(id)
  if (id === grantDialog.id) grants.value = r.data || []
}
async function addGrant() {
  if (!grantDialog.principalId) return
  grantSaving.value = true
  try {
    await grantDataApi(grantDialog.id, grantDialog.principalId, grantDialog.action)
    await reloadGrants()
    ElMessage.success('已授权')
  } finally {
    grantSaving.value = false
  }
}
async function removeGrant(row: ResourceGrant) {
  grantSaving.value = true
  try {
    await revokeDataApiGrant(grantDialog.id, row.principalId, row.action)
    await reloadGrants()
    ElMessage.success('已撤销')
  } finally {
    grantSaving.value = false
  }
}
onMounted(refresh)
</script>

<style scoped>
.toolbar{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}.hint{margin-top:5px;color:#667085;font-size:12px}.mb8{margin-bottom:12px}.mt8{margin-top:12px}.ml4{margin-left:4px}.sunset-late{color:#b42318;font-weight:600}.openapi-pre{max-height:420px;overflow:auto;margin:0;padding:12px;background:#f5f7fb;border-radius:8px;font-size:12px;white-space:pre-wrap;word-break:break-word}
</style>
