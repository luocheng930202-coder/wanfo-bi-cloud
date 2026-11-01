<template>
  <div class="app-container metric-set-page" v-loading="loading">
    <header class="page-head">
      <div>
        <h1>指标集</h1>
        <p>按主题打包已授权指标，供分析与门户引用。</p>
      </div>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button v-hasPermi="['bi:metric:add']" type="primary" @click="openCreate">新建指标集</el-button>
      </div>
    </header>

    <el-table :data="rows" border>
      <el-table-column prop="setCode" label="编码" min-width="140" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="theme" label="主题" width="120" />
      <el-table-column label="成员" min-width="220">
        <template #default="{ row }">{{ (row.members || []).map((m: any) => m.metricCode || m.metricId).join('、') || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><el-tag size="small">{{ row.status === 'ACTIVE' ? '启用' : '归档' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['bi:metric:edit']" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-hasPermi="['bi:metric:remove']" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无有权查看的指标集" /></template>
    </el-table>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑指标集' : '新建指标集'" width="640px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="编码" required>
          <el-input v-model="form.setCode" :disabled="!!form.id" maxlength="96" placeholder="字母开头" />
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="160" /></el-form-item>
        <el-form-item label="主题"><el-input v-model="form.theme" maxlength="64" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" maxlength="1000" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="成员指标">
          <el-select v-model="form.metricIds" multiple filterable style="width:100%" placeholder="选择有权查看的指标">
            <el-option v-for="m in metrics" :key="m.id" :label="`${m.code} · ${m.name}`" :value="m.id" />
          </el-select>
        </el-form-item>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMetrics, type MetricSummary } from '@/api/bi/metric'
import { createMetricSet, deleteMetricSet, listMetricSets, updateMetricSet, type MetricSet } from '@/api/bi/metricSet'

const loading = ref(false)
const saving = ref(false)
const rows = ref<MetricSet[]>([])
const metrics = ref<MetricSummary[]>([])
const formVisible = ref(false)
const form = reactive({
  id: 0 as number,
  setCode: '',
  name: '',
  theme: '',
  description: '',
  status: 'ACTIVE',
  metricIds: [] as number[]
})

async function load() {
  loading.value = true
  try {
    const [setRes, metricRes] = await Promise.all([listMetricSets(), listMetrics()])
    rows.value = setRes.data || []
    metrics.value = metricRes.data || []
  } finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { id: 0, setCode: '', name: '', theme: '', description: '', status: 'ACTIVE', metricIds: [] })
  formVisible.value = true
}

function openEdit(row: MetricSet) {
  Object.assign(form, {
    id: row.id,
    setCode: row.setCode,
    name: row.name,
    theme: row.theme || '',
    description: row.description || '',
    status: row.status || 'ACTIVE',
    metricIds: (row.members || []).map(m => m.metricId)
  })
  formVisible.value = true
}

async function save() {
  if (!form.setCode.trim() || !form.name.trim()) { ElMessage.warning('请填写编码与名称'); return }
  saving.value = true
  try {
    const payload = {
      setCode: form.setCode.trim(),
      name: form.name.trim(),
      theme: form.theme.trim() || undefined,
      description: form.description.trim() || undefined,
      status: form.status,
      metricIds: form.metricIds
    }
    if (form.id) await updateMetricSet(form.id, payload)
    else await createMetricSet(payload)
    formVisible.value = false
    await load()
    ElMessage.success('已保存')
  } finally { saving.value = false }
}

async function remove(row: MetricSet) {
  try { await ElMessageBox.confirm(`确定删除指标集「${row.name}」？`, '删除', { type: 'warning' }) } catch { return }
  await deleteMetricSet(row.id)
  await load()
  ElMessage.success('已删除')
}

onMounted(load)
</script>

<style scoped>
.page-head { display:flex; justify-content:space-between; gap:12px; align-items:flex-start; margin-bottom:16px; flex-wrap:wrap }
.page-head h1 { margin:0; font-size:22px }
.page-head p { margin:6px 0 0; color:#667085 }
</style>
