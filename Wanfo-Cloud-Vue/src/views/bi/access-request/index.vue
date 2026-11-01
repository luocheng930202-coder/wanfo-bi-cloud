<template>
  <div class="app-container access-request-page bi-page" v-loading="loading">
    <header class="page-head">
      <div>
        <h1>访问申请</h1>
        <p>从目录申请资产权限；审批通过后写入有限期资源授权，到期自动回收。</p>
      </div>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate()">新建申请</el-button>
      </div>
    </header>

    <el-tabs v-model="tab">
      <el-tab-pane label="我的申请" name="mine">
        <el-table :data="mine" border>
          <el-table-column prop="id" label="单号" width="80" />
          <el-table-column label="资源" min-width="180">
            <template #default="{ row }">{{ row.resourceType }} #{{ row.resourceId }}</template>
          </el-table-column>
          <el-table-column prop="purpose" label="目的" min-width="200" show-overflow-tooltip />
          <el-table-column label="动作" width="140">
            <template #default="{ row }">{{ (row.actions || []).join(', ') }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="requestedAt" label="申请时间" min-width="160" />
          <template #empty><el-empty description="暂无申请" /></template>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="待我审批" name="pending">
        <el-table :data="pending" border>
          <el-table-column prop="id" label="单号" width="80" />
          <el-table-column label="资源" min-width="180">
            <template #default="{ row }">{{ row.resourceType }} #{{ row.resourceId }}</template>
          </el-table-column>
          <el-table-column prop="purpose" label="目的" min-width="200" show-overflow-tooltip />
          <el-table-column prop="requestedBy" label="申请人" width="120" />
          <el-table-column label="有效期" width="90">
            <template #default="{ row }">{{ row.validDays }} 天</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" :loading="decidingId === row.id" @click="decide(row.id, true)">通过</el-button>
              <el-button link type="danger" :loading="decidingId === row.id" @click="decide(row.id, false)">驳回</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无待审申请" /></template>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="formVisible" title="新建访问申请" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="资源类型" required>
          <el-select v-model="form.resourceType" style="width:100%">
            <el-option label="数据集" value="dataset" />
            <el-option label="指标" value="metric" />
            <el-option label="数据源" value="datasource" />
            <el-option label="看板" value="dashboard" />
            <el-option label="图表" value="chart" />
            <el-option label="数据 API" value="data_api" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源 ID" required><el-input-number v-model="form.resourceId" :min="1" controls-position="right" /></el-form-item>
        <el-form-item label="资产 URN"><el-input v-model="form.assetUrn" maxlength="1000" /></el-form-item>
        <el-form-item label="申请动作" required>
          <el-select v-model="form.actions" multiple style="width:100%">
            <el-option label="查看 view" value="view" />
            <el-option label="取数 query" value="query" />
            <el-option label="消费 consume" value="consume" />
            <el-option label="编辑 edit" value="edit" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效天数" required><el-input-number v-model="form.validDays" :min="1" :max="365" /></el-form-item>
        <el-form-item label="申请目的" required>
          <el-input v-model="form.purpose" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAccessRequest, decideAccessRequest, listMyAccessRequests, listPendingAccessRequests, type AccessRequest
} from '@/api/bi/accessRequest'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const decidingId = ref<number | null>(null)
const tab = ref('mine')
const mine = ref<AccessRequest[]>([])
const pending = ref<AccessRequest[]>([])
const formVisible = ref(false)
const form = reactive({
  resourceType: 'dataset',
  resourceId: 1,
  assetUrn: '',
  actions: ['view'] as string[],
  validDays: 30,
  purpose: ''
})

function statusLabel(status: string) {
  return ({ PENDING: '待审', APPROVED: '已通过', REJECTED: '已驳回', EXPIRED: '已过期', CANCELLED: '已取消' } as Record<string, string>)[status] || status
}
function statusType(status: string) {
  return ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'info' } as Record<string, string>)[status] || 'info'
}

async function load() {
  loading.value = true
  try {
    const [mineRes, pendingRes] = await Promise.all([listMyAccessRequests(), listPendingAccessRequests()])
    mine.value = mineRes.data || []
    pending.value = pendingRes.data || []
  } finally { loading.value = false }
}

function openCreate(preset?: Partial<typeof form>) {
  Object.assign(form, {
    resourceType: 'dataset',
    resourceId: 1,
    assetUrn: '',
    actions: ['view'],
    validDays: 30,
    purpose: '',
    ...preset
  })
  formVisible.value = true
}

async function save() {
  if (!form.purpose.trim() || !form.actions.length) { ElMessage.warning('请填写目的并选择动作'); return }
  saving.value = true
  try {
    await createAccessRequest({
      resourceType: form.resourceType,
      resourceId: form.resourceId,
      assetUrn: form.assetUrn.trim() || undefined,
      purpose: form.purpose.trim(),
      actions: form.actions,
      validDays: form.validDays
    })
    formVisible.value = false
    tab.value = 'mine'
    await load()
    ElMessage.success('申请已提交')
  } finally { saving.value = false }
}

async function decide(id: number, approve: boolean) {
  const action = approve ? '通过' : '驳回'
  try { await ElMessageBox.confirm(`确定${action}该访问申请？`, action, { type: approve ? 'success' : 'warning' }) } catch { return }
  decidingId.value = id
  try {
    await decideAccessRequest(id, approve)
    await load()
    ElMessage.success('已' + action)
  } finally { decidingId.value = null }
}

onMounted(async () => {
  await load()
  const q = route.query
  if (q.resourceType || q.resourceId || q.assetUrn) {
    openCreate({
      resourceType: String(q.resourceType || 'dataset'),
      resourceId: Number(q.resourceId || 1),
      assetUrn: String(q.assetUrn || ''),
      actions: ['view', 'query'],
      purpose: q.assetName ? `申请使用「${q.assetName}」` : ''
    })
  }
})
</script>

<style scoped>
.page-head { display:flex; justify-content:space-between; gap:12px; align-items:flex-start; margin-bottom:16px; flex-wrap:wrap }
.page-head h1 { margin:0; font-size:22px }
.page-head p { margin:6px 0 0; color:#667085 }
</style>
