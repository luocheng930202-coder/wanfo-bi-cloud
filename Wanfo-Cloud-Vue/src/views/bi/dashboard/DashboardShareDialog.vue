<template>
  <el-dialog :model-value="visible" title="分享看板" width="min(720px, calc(100vw - 24px))" @update:model-value="emit('update:visible', $event)" @open="load">
    <el-alert type="info" :closable="false" show-icon class="mb8"
      title="分享链接需登录后打开。访问者按当前登录用户权限取数；创建者按创建者的当前权限取数。连续密码错误会暂时限制访问。" />
    <el-form label-width="110px" class="mb8">
      <el-form-item label="数据范围">
        <el-select v-model="form.dataScope" style="width:220px">
          <el-option value="viewer" label="访问者数据权限" />
          <el-option value="publisher" label="创建者数据权限" />
        </el-select>
      </el-form-item>
      <el-form-item label="有效天数"><el-input-number v-model="form.expireDays" :min="0" :max="365" :precision="0" /> <span class="hint">0 表示永不过期</span></el-form-item>
      <el-form-item label="访问密码"><el-input v-model="form.password" type="password" show-password placeholder="可选" maxlength="64" /></el-form-item>
      <el-button type="primary" :loading="creating" @click="create">生成链接</el-button>
    </el-form>
    <el-table :data="links" border size="small" v-loading="loading">
      <el-table-column prop="token" label="Token" min-width="160" show-overflow-tooltip />
      <el-table-column label="范围" width="120"><template #default="{ row }">{{ row.dataScope === 'publisher' ? '创建者' : '访问者' }}</template></el-table-column>
      <el-table-column label="密码" width="70"><template #default="{ row }">{{ row.hasPassword ? '是' : '否' }}</template></el-table-column>
      <el-table-column prop="expireTime" label="过期" width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="copy(row.token)">复制链接</el-button>
          <el-button link type="danger" @click="revoke(row)">停用</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button style="margin-top:12px" :loading="logsLoading" @click="loadLogs">查看访问记录（最近200条）</el-button>
    <el-table v-if="logsVisible" :data="logs" size="small" max-height="260"><el-table-column prop="createTime" label="时间" width="170"/><el-table-column prop="shareId" label="链接ID" width="80"/><el-table-column prop="viewerPrincipal" label="访问者ID" width="100"/><el-table-column label="操作"><template #default="{row}">{{eventLabels[row.event]||row.event}}</template></el-table-column></el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDashboardShares, createDashboardShare, revokeDashboardShare, shareLinkUrl, type DashboardShareLink } from '@/api/bi/share'
import {listShareAccessLogs,type ShareAccessLog} from '@/api/bi/share'

const props = defineProps<{ visible: boolean; dashboardId: number; name: string }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void }>()

const loading = ref(false)
const creating = ref(false)
const links = ref<DashboardShareLink[]>([])
const form = reactive({ dataScope: 'viewer', expireDays: 30, password: '' })
const logs=ref<ShareAccessLog[]>([]),logsVisible=ref(false),logsLoading=ref(false)
const eventLabels:Record<string,string>={VIEW:'查看',DATA:'组件取数',OPTIONS:'查询候选值',PASSWORD_REJECTED:'密码错误',THROTTLED:'尝试受限',ACCESS_DENIED:'权限拒绝'}
async function loadLogs(){logsLoading.value=true;try{logs.value=(await listShareAccessLogs(props.dashboardId)).data||[];logsVisible.value=true}finally{logsLoading.value=false}}

async function load() {
  logsVisible.value=false;logs.value=[]
  if (!props.dashboardId) return
  loading.value = true
  try { links.value = (await listDashboardShares(props.dashboardId)).data || [] }
  finally { loading.value = false }
}

async function create() {
  creating.value = true
  try {
    const res = await createDashboardShare(props.dashboardId, {
      dataScope: form.dataScope,
      expireDays: form.expireDays || undefined,
      password: form.password || undefined
    })
    ElMessage.success('分享链接已创建')
    if (res.data?.token) await copy(res.data.token)
    form.password = ''
    await load()
  } finally { creating.value = false }
}

async function copy(token: string) {
  const url = shareLinkUrl(token)
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.info(url)
  }
}

async function revoke(row: DashboardShareLink) {
  await ElMessageBox.confirm('停用后该链接将无法再访问，确认？', '停用分享')
  await revokeDashboardShare(props.dashboardId, row.id)
  ElMessage.success('已停用')
  await load()
}
</script>

<style scoped>
.mb8 { margin-bottom: 8px; }
.hint { margin-left: 8px; color: #909399; font-size: 12px; }
</style>
