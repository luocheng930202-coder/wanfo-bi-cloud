<template>
  <div class="app-container glossary-page bi-page" v-loading="loading">
    <header class="page-head">
      <div>
        <h1>业务术语表</h1>
        <p>维护业务口径、责任人与认证状态，可关联已发布指标。</p>
      </div>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button v-hasPermi="['bi:resource:admin']" type="primary" @click="openCreate">新建术语</el-button>
      </div>
    </header>

    <el-table :data="rows" border>
      <el-table-column prop="termCode" label="编码" min-width="140" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="ownerName" label="责任人" width="120" />
      <el-table-column label="认证" width="110">
        <template #default="{ row }"><el-tag size="small">{{ statusLabel(row.certificationStatus) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="关联指标" width="100">
        <template #default="{ row }">{{ row.metricId ? `#${row.metricId}` : '—' }}</template>
      </el-table-column>
      <el-table-column prop="definitionText" label="口径说明" min-width="240" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['bi:resource:admin']" link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无术语" /></template>
    </el-table>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑术语' : '新建术语'" width="640px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="编码" required>
          <el-input v-model="form.termCode" :disabled="!!form.id" maxlength="96" placeholder="字母开头" />
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="160" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="form.category" maxlength="64" /></el-form-item>
        <el-form-item label="责任人"><el-input v-model="form.ownerName" maxlength="64" /></el-form-item>
        <el-form-item label="认证状态">
          <el-select v-model="form.certificationStatus" style="width:100%">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="评审中" value="REVIEW" />
            <el-option label="已认证" value="CERTIFIED" />
            <el-option label="已废弃" value="DEPRECATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联指标">
          <el-select v-model="form.metricId" clearable filterable style="width:100%" placeholder="可选">
            <el-option v-for="m in metrics" :key="m.id" :label="`${m.name} · ${m.code}`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="口径说明" required>
          <el-input v-model="form.definitionText" type="textarea" :rows="5" maxlength="2000" show-word-limit />
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
import { ElMessage } from 'element-plus'
import { listMetrics } from '@/api/bi/metric'
import { createGlossaryTerm, listGlossaryTerms, updateGlossaryTerm, type GlossaryTerm } from '@/api/bi/glossary'

const loading = ref(false)
const saving = ref(false)
const rows = ref<GlossaryTerm[]>([])
const metrics = ref<Array<{ id: number; name: string; code: string }>>([])
const formVisible = ref(false)
const form = reactive({
  id: 0 as number,
  termCode: '',
  name: '',
  category: '',
  ownerName: '',
  certificationStatus: 'DRAFT',
  metricId: null as number | null,
  definitionText: ''
})

function statusLabel(status: string) {
  return ({ DRAFT: '草稿', REVIEW: '评审中', CERTIFIED: '已认证', DEPRECATED: '已废弃' } as Record<string, string>)[status] || status
}

async function load() {
  loading.value = true
  try {
    const [termsRes, metricRes] = await Promise.all([listGlossaryTerms(), listMetrics()])
    rows.value = termsRes.data || []
    metrics.value = (metricRes.data || []).map((m: any) => ({ id: m.id, name: m.name, code: m.code }))
  } catch {
    ElMessage.error('加载术语失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: 0, termCode: '', name: '', category: '', ownerName: '', certificationStatus: 'DRAFT', metricId: null, definitionText: '' })
  formVisible.value = true
}

function openEdit(row: GlossaryTerm) {
  Object.assign(form, {
    id: row.id,
    termCode: row.termCode,
    name: row.name,
    category: row.category || '',
    ownerName: row.ownerName || '',
    certificationStatus: row.certificationStatus || 'DRAFT',
    metricId: row.metricId ?? null,
    definitionText: row.definitionText || ''
  })
  formVisible.value = true
}

async function save() {
  const code = form.termCode.trim()
  if (!form.id && !/^[A-Za-z][A-Za-z0-9_-]{1,95}$/.test(code)) {
    ElMessage.warning('编码须为 2～96 位，字母开头，仅字母数字_-')
    return
  }
  if (!form.name.trim() || !form.definitionText.trim()) {
    ElMessage.warning('请填写名称与口径说明')
    return
  }
  saving.value = true
  try {
    const payload = {
      termCode: code,
      name: form.name.trim(),
      category: form.category || undefined,
      ownerName: form.ownerName || undefined,
      certificationStatus: form.certificationStatus,
      metricId: form.metricId,
      definitionText: form.definitionText.trim()
    }
    if (form.id) await updateGlossaryTerm(form.id, payload)
    else await createGlossaryTerm(payload)
    ElMessage.success('已保存')
    formVisible.value = false
    await load()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 16px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.page-head p { margin: 0; color: var(--el-text-color-secondary); }
</style>
