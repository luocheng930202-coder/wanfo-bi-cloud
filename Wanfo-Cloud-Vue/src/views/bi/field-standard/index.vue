<template>
  <div class="app-container field-standard-page bi-page" v-loading="loading">
    <header class="page-head">
      <div>
        <h1>字段标准</h1>
        <p>维护字段标准字典，弱绑定术语/指标；对目录 COLUMN 做只读落标检查。</p>
      </div>
      <div class="actions">
        <el-button @click="runCompliance" :loading="checking">落标检查</el-button>
        <el-button @click="load">刷新</el-button>
        <el-button v-hasPermi="['bi:resource:admin']" type="primary" @click="openCreate">新建标准</el-button>
      </div>
    </header>

    <div v-if="compliance" class="summary-row">
      <article><span>扫描字段</span><strong>{{ compliance.summary.scannedColumns }}</strong></article>
      <article class="ok"><span>合规</span><strong>{{ compliance.summary.compliant }}</strong></article>
      <article class="warn"><span>类型不一致</span><strong>{{ compliance.summary.typeMismatch }}</strong></article>
      <article><span>未绑定</span><strong>{{ compliance.summary.unbound }}</strong></article>
      <article><span>别名命中</span><strong>{{ compliance.summary.aliasMatched }}</strong></article>
    </div>
    <p v-if="compliance?.summary?.note" class="note">{{ compliance.summary.note }}</p>

    <el-table :data="rows" border>
      <el-table-column prop="standardCode" label="编码" min-width="140" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="类型族" width="110">
        <template #default="{ row }"><el-tag size="small">{{ row.dataTypeFamily }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="nullablePolicy" label="可空" width="100" />
      <el-table-column label="别名" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ (row.matchAliases || []).join(', ') || '—' }}</template>
      </el-table-column>
      <el-table-column label="术语/指标" width="120">
        <template #default="{ row }">
          {{ row.glossaryTermId ? `术语#${row.glossaryTermId}` : '—' }}
          <span v-if="row.metricId"> / 指标#{{ row.metricId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><el-tag size="small">{{ statusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['bi:resource:admin']" link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无字段标准" /></template>
    </el-table>

    <section v-if="compliance?.findings?.length" class="findings">
      <h2>落标结果（最近扫描）</h2>
      <el-table :data="compliance.findings" border max-height="420">
        <el-table-column prop="qualifiedName" label="字段" min-width="220" show-overflow-tooltip />
        <el-table-column prop="observedType" label="观测类型" width="120" />
        <el-table-column prop="standardCode" label="命中标准" width="140" />
        <el-table-column label="结果" width="130">
          <template #default="{ row }"><el-tag :type="resultType(row.result)" size="small">{{ row.result }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.assetId" v-hasPermi="['bi:resource:admin']" link type="primary" :loading="onlineSampleId === row.assetId" @click="runOnlineSample(row.assetId)">JDBC 抽样</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑字段标准' : '新建字段标准'" width="680px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="编码" required>
          <el-input v-model="form.standardCode" :disabled="!!form.id" maxlength="96" placeholder="字母开头，如 STD_AMOUNT" />
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="160" /></el-form-item>
        <el-form-item label="类型族" required>
          <el-select v-model="form.dataTypeFamily" style="width:100%">
            <el-option label="STRING" value="STRING" />
            <el-option label="NUMBER" value="NUMBER" />
            <el-option label="DATETIME" value="DATETIME" />
            <el-option label="BOOLEAN" value="BOOLEAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="可空策略">
          <el-select v-model="form.nullablePolicy" style="width:100%">
            <el-option label="OPTIONAL" value="OPTIONAL" />
            <el-option label="REQUIRED" value="REQUIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大长度"><el-input-number v-model="form.maxLength" :min="1" :max="65535" controls-position="right" /></el-form-item>
        <el-form-item label="名称别名">
          <el-select v-model="form.matchAliases" multiple filterable allow-create default-first-option style="width:100%" placeholder="输入列名后回车" />
        </el-form-item>
        <el-form-item label="关联术语">
          <el-select v-model="form.glossaryTermId" clearable filterable style="width:100%" placeholder="可选">
            <el-option v-for="t in terms" :key="t.id" :label="`${t.name} · ${t.termCode}`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联指标">
          <el-select v-model="form.metricId" clearable filterable style="width:100%" placeholder="可选">
            <el-option v-for="m in metrics" :key="m.id" :label="`${m.name} · ${m.code}`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="废弃" value="DEPRECATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
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
import { listGlossaryTerms, type GlossaryTerm } from '@/api/bi/glossary'
import {
  checkFieldStandardCompliance,
  createFieldStandard,
  listFieldStandards,
  onlineSampleValidate,
  updateFieldStandard,
  type ComplianceResult,
  type FieldStandard
} from '@/api/bi/fieldStandard'
import { unwrapBiData } from '@/api/bi/response'

const loading = ref(false)
const checking = ref(false)
const saving = ref(false)
const onlineSampleId = ref<number | null>(null)
const rows = ref<FieldStandard[]>([])
const terms = ref<GlossaryTerm[]>([])
const metrics = ref<Array<{ id: number; name: string; code: string }>>([])
const compliance = ref<ComplianceResult>()
const formVisible = ref(false)
const form = reactive({
  id: 0 as number,
  standardCode: '',
  name: '',
  dataTypeFamily: 'STRING',
  maxLength: undefined as number | undefined,
  nullablePolicy: 'OPTIONAL',
  matchAliases: [] as string[],
  glossaryTermId: undefined as number | undefined,
  metricId: undefined as number | undefined,
  description: '',
  status: 'ACTIVE'
})

function statusLabel(s: string) {
  return ({ ACTIVE: '启用', DRAFT: '草稿', DEPRECATED: '废弃' } as Record<string, string>)[s] || s
}
function resultType(r: string) {
  if (r === 'COMPLIANT') return 'success'
  if (r === 'TYPE_MISMATCH' || r === 'TYPE_UNKNOWN') return 'danger'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    rows.value = unwrapBiData(await listFieldStandards()) || []
  } finally {
    loading.value = false
  }
}

async function runCompliance() {
  checking.value = true
  try {
    compliance.value = unwrapBiData(await checkFieldStandardCompliance(500))
    ElMessage.success('落标检查完成')
  } catch (e: any) {
    ElMessage.error(e?.message || '落标检查失败')
  } finally {
    checking.value = false
  }
}

async function runOnlineSample(assetId: number) {
  onlineSampleId.value = assetId
  try {
    const res = unwrapBiData(await onlineSampleValidate(assetId, 100)) as Record<string, any>
    const summary = res?.summary || {}
    const title = res?.passed ? 'JDBC 在线抽样通过' : 'JDBC 在线抽样未通过'
    const detail = `标准 ${res?.standardCode || ''} · 抽样 ${summary.sampled ?? 0} · 通过 ${summary.passed ?? 0} · 失败 ${summary.failed ?? 0}`
    if (res?.passed) ElMessage.success(`${title}：${detail}`)
    else ElMessage.error(`${title}：${detail}`)
  } catch (e: any) {
    ElMessage.error(e?.message || 'JDBC 在线抽样失败')
  } finally {
    onlineSampleId.value = null
  }
}

function openCreate() {
  Object.assign(form, {
    id: 0, standardCode: '', name: '', dataTypeFamily: 'STRING', maxLength: undefined,
    nullablePolicy: 'OPTIONAL', matchAliases: [], glossaryTermId: undefined, metricId: undefined,
    description: '', status: 'ACTIVE'
  })
  formVisible.value = true
}

function openEdit(row: FieldStandard) {
  Object.assign(form, {
    id: row.id,
    standardCode: row.standardCode,
    name: row.name,
    dataTypeFamily: row.dataTypeFamily,
    maxLength: row.maxLength,
    nullablePolicy: row.nullablePolicy || 'OPTIONAL',
    matchAliases: [...(row.matchAliases || [])],
    glossaryTermId: row.glossaryTermId,
    metricId: row.metricId,
    description: row.description || '',
    status: row.status || 'ACTIVE'
  })
  formVisible.value = true
}

async function save() {
  saving.value = true
  try {
    const payload = {
      standardCode: form.standardCode,
      name: form.name,
      dataTypeFamily: form.dataTypeFamily,
      maxLength: form.maxLength ?? null,
      nullablePolicy: form.nullablePolicy,
      matchAliases: form.matchAliases,
      glossaryTermId: form.glossaryTermId ?? null,
      metricId: form.metricId ?? null,
      description: form.description || null,
      status: form.status
    }
    if (form.id) await updateFieldStandard(form.id, payload)
    else await createFieldStandard(payload)
    ElMessage.success('已保存')
    formVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await load()
  try {
    terms.value = unwrapBiData(await listGlossaryTerms()) || []
  } catch { terms.value = [] }
  try {
    const m = unwrapBiData(await listMetrics())
    metrics.value = Array.isArray(m) ? m : (m as any)?.rows || (m as any)?.records || []
  } catch { metrics.value = [] }
})
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 16px; align-items: flex-start; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.page-head p { margin: 0; color: var(--el-text-color-secondary); }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
.summary-row { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-row article { background: var(--el-fill-color-light); border-radius: 8px; padding: 12px 14px; }
.summary-row span { display: block; font-size: 12px; color: var(--el-text-color-secondary); }
.summary-row strong { font-size: 22px; }
.summary-row .ok strong { color: var(--el-color-success); }
.summary-row .warn strong { color: var(--el-color-danger); }
.note { color: var(--el-text-color-secondary); font-size: 13px; margin: 0 0 14px; }
.findings { margin-top: 24px; }
.findings h2 { font-size: 16px; margin: 0 0 10px; }
@media (max-width: 960px) {
  .summary-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .page-head { flex-direction: column; }
}
</style>
