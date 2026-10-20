<template>
  <div class="app-container">
    <el-card>
      <template #header><div class="toolbar"><span>导出中心</span><div><el-button :disabled="loading" @click="refresh">刷新</el-button></div></div></template>
      <el-alert title="从已保存图表或已发布看板提交导出。任务按当前登录用户的功能权限、资源授权和行列规则重新取数，文件保留 7 天，仅提交人可下载。" :closable="false" class="mb8"/>
      <el-table :data="jobs" v-loading="loading" border>
        <el-table-column prop="name" label="名称" min-width="160"/>
        <el-table-column label="来源" width="120"><template #default="{row}">{{ row.sourceType==='chart'?'图表':'看板' }} #{{ row.sourceId }}</template></el-table-column>
        <el-table-column prop="format" label="格式" width="80"/>
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusType[row.status]||'info'">{{ statusLabel[row.status]||row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="rowCount" label="行数" width="80"/>
        <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip/>
        <el-table-column prop="createTime" label="提交时间" width="170"/>
        <el-table-column label="操作" width="160" fixed="right"><template #default="{row}">
          <el-button v-if="row.status==='succeeded'" v-hasPermi="['bi:export:download']" link type="primary" :disabled="busyId===row.id" @click="download(row)">下载</el-button>
          <el-button v-if="row.status==='queued' || row.status==='running'" v-hasPermi="['bi:export:cancel']" link type="warning" :disabled="busyId===row.id" @click="cancel(row)">取消</el-button>
        </template></el-table-column>
        <template #empty><el-empty description="暂无导出任务"/></template>
      </el-table>
    </el-card>
  </div>
</template>
<script setup lang="ts" name="BiExport">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { cancelExport, downloadExport, listExports, type ExportJob } from '@/api/bi/export'
const jobs = ref<ExportJob[]>([])
const loading = ref(false)
const busyId = ref<number>()
const statusLabel: Record<string, string> = { queued: '排队中', running: '导出中', succeeded: '已完成', failed: '失败', cancelled: '已取消', expired: '已过期' }
const statusType: Record<string, 'info' | 'success' | 'warning' | 'danger'> = { queued: 'info', running: 'warning', succeeded: 'success', failed: 'danger', cancelled: 'info', expired: 'info' }
const pending = computed(() => jobs.value.some(j => j.status === 'queued' || j.status === 'running'))
let timer: ReturnType<typeof setInterval> | undefined
async function refresh() {
  loading.value = true
  try { jobs.value = (await listExports()).data || [] }
  finally { loading.value = false }
}
async function download(row: ExportJob) {
  busyId.value = row.id
  try { await downloadExport(row.id, row.fileName || (row.name + (row.format === 'csv' ? '.csv' : '.xlsx'))) }
  finally { busyId.value = undefined }
}
async function cancel(row: ExportJob) {
  busyId.value = row.id
  try { await cancelExport(row.id); ElMessage.success('已取消'); await refresh() }
  finally { busyId.value = undefined }
}
onMounted(() => { refresh(); timer = setInterval(() => { if (pending.value) refresh() }, 2000) })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>
<style scoped>.toolbar{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}</style>
