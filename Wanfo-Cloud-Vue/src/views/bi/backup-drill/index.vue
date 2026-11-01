<template>
  <div class="app-container backup-drill-page bi-page" v-loading="loading">
    <header class="page-head">
      <div>
        <h1>备份与演练</h1>
        <p>登记元数据备份/恢复演练证据；平台不自动恢复生产库。</p>
      </div>
      <div class="actions">
        <el-button @click="load">刷新</el-button>
        <el-button v-hasPermi="['bi:resource:admin']" type="primary" @click="openRecord">登记演练</el-button>
      </div>
    </header>

    <el-alert
      v-if="readiness"
      class="ready-alert"
      :type="readiness.summary.ready ? 'success' : 'warning'"
      :closable="false"
      show-icon
      :title="readiness.summary.ready ? '就绪：已有通过演练' : '未就绪：缺少通过演练'"
    >
      <template #default>
        声明 RPO {{ readiness.summary.declaredRpoMinutes }} 分钟 / RTO {{ readiness.summary.declaredRtoMinutes }} 分钟；
        Flyway {{ readiness.summary.flywayVersion || '—' }}；
        本地 dump {{ readiness.summary.localArtifactCount }} 个
        <span v-if="readiness.summary.newestArtifactAgeMinutes != null">（最新约 {{ readiness.summary.newestArtifactAgeMinutes }} 分钟前）</span>
      </template>
    </el-alert>
    <ul v-if="readiness?.summary.blockers?.length" class="issue-list blockers">
      <li v-for="(b, i) in readiness.summary.blockers" :key="'b'+i">{{ b }}</li>
    </ul>
    <ul v-if="readiness?.summary.warnings?.length" class="issue-list warnings">
      <li v-for="(w, i) in readiness.summary.warnings" :key="'w'+i">{{ w }}</li>
    </ul>
    <p v-if="readiness?.note" class="note">{{ readiness.note }}</p>
    <p class="note">备份脚本：<code>scripts/backup-metadata.ps1</code> · Runbook：<code>docs/BACKUP-RESTORE-DRILL.md</code></p>

    <el-table :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="drillType" label="类型" width="150" />
      <el-table-column label="结果" width="100">
        <template #default="{ row }"><el-tag :type="row.result === 'PASSED' ? 'success' : row.result === 'FAILED' ? 'danger' : 'warning'" size="small">{{ row.result }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="rpoMinutes" label="RPO分" width="90" />
      <el-table-column prop="rtoMinutes" label="RTO分" width="90" />
      <el-table-column prop="artifactPath" label="产物/工单" min-width="220" show-overflow-tooltip />
      <el-table-column prop="actor" label="执行人" width="110" />
      <el-table-column prop="finishedAt" label="完成时间" min-width="170" />
      <template #empty><el-empty description="暂无演练记录" /></template>
    </el-table>

    <el-dialog v-model="formVisible" title="登记演练" width="640px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="类型" required>
          <el-select v-model="form.drillType" style="width:100%">
            <el-option label="元数据 Dump" value="METADATA_DUMP" />
            <el-option label="恢复干跑" value="RESTORE_DRY_RUN" />
            <el-option label="Flyway 检查" value="FLYWAY_CHECK" />
            <el-option label="仓侧备注" value="WAREHOUSE_NOTE" />
            <el-option label="完整演练" value="FULL_DRILL" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果" required>
          <el-select v-model="form.result" style="width:100%">
            <el-option label="PASSED" value="PASSED" />
            <el-option label="PARTIAL" value="PARTIAL" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="观测 RPO 分"><el-input-number v-model="form.rpoMinutes" :min="1" :max="10080" /></el-form-item>
        <el-form-item label="观测 RTO 分"><el-input-number v-model="form.rtoMinutes" :min="1" :max="10080" /></el-form-item>
        <el-form-item label="产物路径"><el-input v-model="form.artifactPath" maxlength="1000" placeholder="本地 dump 路径或工单号" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.notes" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getBackupReadiness, listBackupDrills, recordBackupDrill, type BackupDrill, type BackupReadiness } from '@/api/bi/backupDrill'
import { unwrapBiData } from '@/api/bi/response'

const loading = ref(false)
const saving = ref(false)
const readiness = ref<BackupReadiness>()
const rows = ref<BackupDrill[]>([])
const formVisible = ref(false)
const form = reactive({
  drillType: 'METADATA_DUMP',
  result: 'PASSED',
  rpoMinutes: 60 as number | undefined,
  rtoMinutes: 45 as number | undefined,
  artifactPath: '',
  notes: ''
})

async function load() {
  loading.value = true
  try {
    readiness.value = unwrapBiData(await getBackupReadiness())
    rows.value = unwrapBiData(await listBackupDrills()) || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openRecord() {
  Object.assign(form, {
    drillType: 'METADATA_DUMP',
    result: 'PASSED',
    rpoMinutes: 60,
    rtoMinutes: 45,
    artifactPath: readiness.value?.summary.newestLocalArtifact?.path || '',
    notes: ''
  })
  formVisible.value = true
}

async function save() {
  saving.value = true
  try {
    await recordBackupDrill({
      drillType: form.drillType,
      result: form.result,
      rpoMinutes: form.rpoMinutes,
      rtoMinutes: form.rtoMinutes,
      artifactPath: form.artifactPath || null,
      notes: form.notes || null,
      scope: { metadataDb: true, redis: false, warehouse: false, objectStore: false },
      checklist: readiness.value?.checklist || []
    })
    ElMessage.success('已登记')
    formVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.page-head p { margin: 0; color: var(--el-text-color-secondary); }
.actions { display: flex; gap: 8px; }
.ready-alert { margin-bottom: 12px; }
.issue-list { margin: 0 0 10px; padding-left: 18px; }
.issue-list.blockers { color: var(--el-color-danger); }
.issue-list.warnings { color: var(--el-color-warning-dark-2); }
.note { color: var(--el-text-color-secondary); font-size: 13px; margin: 0 0 10px; }
code { font-size: 12px; }
</style>
