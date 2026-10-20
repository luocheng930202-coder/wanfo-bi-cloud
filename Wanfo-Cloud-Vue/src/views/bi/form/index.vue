<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="toolbar">
          <span>数据填报</span>
          <el-button v-hasPermi="['bi:form:add']" type="primary" @click="openCreate">新建表单</el-button>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon class="mb8"
        title="填报需要数据集查看、查询及填报写入权限。提交结果待核对时，请先查看记录，避免重复填报。" />
      <el-table :data="forms" v-loading="loading" border>
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="datasetId" label="数据集 ID" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createBy" label="创建人" width="120" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-hasPermi="['bi:form:submit']" link type="primary" :disabled="row.status !== '0'" @click="openFill(row)">填报</el-button>
            <el-button v-hasPermi="['bi:form:list']" link @click="openRecords(row)">记录</el-button>
            <el-button v-hasPermi="['bi:form:edit']" link @click="openEdit(row)">编辑</el-button>
            <el-button v-hasPermi="['bi:form:remove']" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无表单" /></template>
      </el-table>
    </el-card>

    <el-dialog v-model="editor.visible" :title="editor.id ? '编辑表单' : '新建表单'" width="min(760px, calc(100vw - 24px))">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="editor.name" maxlength="200" /></el-form-item>
        <el-form-item label="数据集" required>
          <el-select v-model="editor.datasetId" filterable style="width:100%" :disabled="!!editor.id" @change="loadDatasetFields">
            <el-option v-for="d in datasets" :key="d.id" :value="d.id" :label="d.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段">
          <el-table :data="editor.fields" border size="small" max-height="280">
            <el-table-column label="物理字段" width="140">
              <template #default="{ row }">
                <el-select v-model="row.field" filterable @change="syncLabel(row)">
                  <el-option v-for="f in physicalFields" :key="f.originName" :value="f.originName" :label="f.originName" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="显示名" min-width="120"><template #default="{ row }"><el-input v-model="row.label" /></template></el-table-column>
            <el-table-column label="控件" width="110">
              <template #default="{ row }">
                <el-select v-model="row.widget"><el-option value="text" label="文本" /><el-option value="number" label="数字" /><el-option value="date" label="日期" /><el-option value="textarea" label="多行" /></el-select>
              </template>
            </el-table-column>
            <el-table-column label="必填" width="70"><template #default="{ row }"><el-checkbox v-model="row.required" /></template></el-table-column>
            <el-table-column width="70"><template #default="{ $index }"><el-button link type="danger" @click="editor.fields.splice($index, 1)">删</el-button></template></el-table-column>
          </el-table>
          <el-button class="mt8" link @click="addFieldRow">添加字段</el-button>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="editor.remark" /></el-form-item>
        <el-form-item v-if="editor.id" label="状态">
          <el-select v-model="editor.status" style="width:120px"><el-option value="0" label="启用" /><el-option value="1" label="停用" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">取消</el-button>
        <el-button type="primary" :loading="editor.saving" @click="saveEditor">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fill.visible" :title="'填报 · ' + fill.name" width="min(520px, calc(100vw - 24px))">
      <el-form label-width="120px">
        <el-form-item v-for="f in fill.fields" :key="f.field" :label="f.label || f.field" :required="f.required">
          <el-input v-if="f.widget === 'textarea'" v-model="fill.values[f.field]" type="textarea" :rows="3" />
          <el-input v-else-if="f.widget === 'number'" v-model="fill.values[f.field]" />
          <el-date-picker v-else-if="f.widget === 'date'" v-model="fill.values[f.field]" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          <el-input v-else v-model="fill.values[f.field]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="fill.saving" @click="submitFill">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="records.visible" :title="'填报记录 · ' + records.name" width="min(900px, calc(100vw - 24px))">
      <el-alert title="待核对可能仍在执行，请勿重复填报。管理员可记录核对依据；结果不确定时，须先检查源库业务唯一标识与事务结果再确认终态。" type="warning" :closable="false" class="mb8" />
      <el-table :data="records.rows" border size="small" max-height="400">
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column prop="status" label="提交状态" width="140"><template #default="{row}">{{ ({succeeded:'成功',failed:'写入失败',pending:'待核对',uncertain:'结果不确定',legacy:'历史记录'} as Record<string,string>)[row.status] || row.status }}</template></el-table-column><el-table-column prop="createBy" label="提交人" width="120" />
        <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="['pending','uncertain'].includes(row.status) ? 'warning' : row.success ? 'success' : 'danger'">{{ ['pending','uncertain'].includes(row.status) ? '待核对' : row.success ? '成功' : '失败' }}</el-tag></template></el-table-column>
        <el-table-column prop="errorMsg" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="数据" min-width="200"><template #default="{ row }"><code class="row-json">{{ row.row }}</code></template></el-table-column>
        <el-table-column prop="reviewNote" label="核对说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="核对" width="130"><template #default="{row}"><template v-if="row.canReview"><el-button v-if="['pending','uncertain'].includes(row.status)" link type="primary" @click="openReview(row)">处理</el-button><el-button link @click="showReviews(row)">记录</el-button></template></template></el-table-column>
      </el-table>
    </el-dialog>
    <el-dialog v-model="review.visible" title="人工核对" width="min(620px, calc(100vw - 24px))">
      <el-alert title="仅记录核对结果，不会向源库补写数据。待核对状态只能补充记录，避免与仍在执行的提交冲突。" type="info" :closable="false" />
      <el-form label-width="100px" style="margin-top:16px">
        <el-form-item label="处理方式"><el-select v-model="review.decision"><el-option label="补充核对记录" value="note"/><el-option v-if="review.status==='uncertain'" label="确认已写入" value="succeeded"/><el-option v-if="review.status==='uncertain'" label="确认未写入" value="failed"/></el-select></el-form-item>
        <el-form-item label="核对依据"><el-input v-model="review.evidence" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="填写业务唯一标识、源库核对结果和核对依据（5至500字）"/></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" :loading="review.saving" @click="saveReview">保存核对记录</el-button></template>
    </el-dialog>
    <el-dialog v-model="reviewLog.visible" title="核对历史" width="min(760px, calc(100vw - 24px))"><el-table :data="reviewLog.rows"><el-table-column prop="createTime" label="时间" width="170"/><el-table-column prop="reviewerPrincipal" label="核对人ID" width="100"/><el-table-column prop="previousStatus" label="原状态"/><el-table-column prop="resolvedStatus" label="处理后状态"/><el-table-column prop="evidence" label="核对依据" min-width="240"/></el-table></el-dialog>
  </div>
</template>

<script setup lang="ts" name="BiDataForm">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDatasets, getDataset, type DatasetSummary, type DatasetField } from '@/api/bi/dataset'
import { listDataForms, getDataForm, createDataForm, updateDataForm, deleteDataForm, submitDataForm, listFormRecords, type DataFormField, type DataFormSummary, type FormRecord } from '@/api/bi/form'
import {reviewFormRecord,listFormReviews,type FormReview} from '@/api/bi/form'

const forms = ref<DataFormSummary[]>([])
const datasets = ref<DatasetSummary[]>([])
const physicalFields = ref<DatasetField[]>([])
const loading = ref(false)

const editor = reactive({
  visible: false, id: 0, name: '', datasetId: undefined as number | undefined, remark: '', status: '0',
  fields: [] as DataFormField[], saving: false
})

const fill = reactive({
  visible: false, id: 0, name: '', fields: [] as DataFormField[],
  values: {} as Record<string, string>, requestId: '', requestBody: '', saving: false
})

const records = reactive({ visible: false, id:0, name: '', rows: [] as FormRecord[] })
const review=reactive({visible:false,id:0,status:'',decision:'note',evidence:'',saving:false})
const reviewLog=reactive({visible:false,rows:[] as FormReview[]})
function openReview(row:FormRecord){Object.assign(review,{visible:true,id:row.id,status:row.status||'',decision:'note',evidence:'',saving:false})}
async function saveReview(){
  if(review.saving)return
  if(review.evidence.trim().length<5){ElMessage.warning('请填写至少5字核对依据');return}
  await ElMessageBox.confirm('请确认已核查源库结果。保存只更新核对记录，不会重写业务数据。','保存核对')
  review.saving=true
  try{await reviewFormRecord(records.id,review.id,{expectedStatus:review.status,decision:review.decision,evidence:review.evidence});review.visible=false;records.rows=(await listFormRecords(records.id)).data||[];ElMessage.success('核对记录已保存')}finally{review.saving=false}
}
async function showReviews(row:FormRecord){reviewLog.rows=(await listFormReviews(records.id,row.id)).data||[];reviewLog.visible=true}

async function refresh() {
  loading.value = true
  try { forms.value = (await listDataForms()).data || [] }
  finally { loading.value = false }
}

async function loadDatasets() {
  datasets.value = (await listDatasets()).data?.filter(d => d.type === 'table') || []
}

function addFieldRow() {
  editor.fields.push({ field: '', label: '', widget: 'text', required: false })
}

function syncLabel(row: DataFormField) {
  if (!row.label) row.label = row.field
}

async function loadDatasetFields() {
  physicalFields.value = []
  if (!editor.datasetId) return
  const detail = await getDataset(editor.datasetId)
  physicalFields.value = (detail.data?.fields || []).filter(f => f.extFlag !== '1')
}

function openCreate() {
  Object.assign(editor, { visible: true, id: 0, name: '', datasetId: undefined, remark: '', status: '0', fields: [{ field: '', label: '', widget: 'text', required: true }] })
  physicalFields.value = []
}

async function openEdit(row: DataFormSummary) {
  const detail = (await getDataForm(row.id)).data!
  Object.assign(editor, { visible: true, id: row.id, name: detail.name, datasetId: detail.datasetId, remark: detail.remark || '', status: detail.status, fields: JSON.parse(JSON.stringify(detail.fields)) })
  await loadDatasetFields()
}

async function saveEditor() {
  if (!editor.name.trim() || !editor.datasetId || !editor.fields.length) { ElMessage.warning('请填写名称、数据集和字段'); return }
  editor.saving = true
  try {
    if (editor.id) await updateDataForm(editor.id, { name: editor.name, fields: editor.fields, remark: editor.remark, status: editor.status })
    else await createDataForm({ name: editor.name, datasetId: editor.datasetId, fields: editor.fields, remark: editor.remark })
    editor.visible = false
    ElMessage.success('已保存')
    await refresh()
  } finally { editor.saving = false }
}

async function openFill(row: DataFormSummary) {
  const detail = (await getDataForm(row.id)).data!
  Object.assign(fill, { visible: true, id: row.id, name: detail.name, fields: detail.fields, values: {} as Record<string, string>, requestId: '', requestBody: '', saving: false })
}

async function submitFill() {
  if(fill.saving)return
  fill.saving = true
  try {
    const body=JSON.stringify(fill.values)
    if(!fill.requestId || fill.requestBody!==body){fill.requestId=crypto.randomUUID();fill.requestBody=body}
    const result=(await submitDataForm(fill.id, fill.values,fill.requestId)).data!
    if(result.submitted)ElMessage.success('提交成功')
    else ElMessage.warning(result.message || '提交状态待核对，请查看填报记录，勿重复写入')
    fill.visible = false
  } finally { fill.saving = false }
}

async function openRecords(row: DataFormSummary) {
  records.id=row.id
  records.name = row.name
  records.rows = (await listFormRecords(row.id)).data || []
  records.visible = true
}

async function remove(row: DataFormSummary) {
  await ElMessageBox.confirm(`删除表单「${row.name}」？`, '确认')
  await deleteDataForm(row.id)
  ElMessage.success('已删除')
  await refresh()
}

onMounted(async () => { await Promise.all([refresh(), loadDatasets()]) })
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.mb8 { margin-bottom: 8px; }
.mt8 { margin-top: 8px; }
.row-json { font-size: 12px; word-break: break-all; }
</style>
