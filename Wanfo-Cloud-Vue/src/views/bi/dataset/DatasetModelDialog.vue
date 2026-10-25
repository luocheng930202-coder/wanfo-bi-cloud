<template>
  <el-dialog :model-value="visible" :title="'数据集建模 · ' + name" width="min(920px, calc(100vw - 24px))" @update:model-value="emit('update:visible', $event)" @open="load">
    <el-tabs v-model="tab" @tab-change="tabChanged">
      <el-tab-pane label="关联表" name="join">
        <el-alert type="info" :closable="false" show-icon class="mb8" title="左表为当前数据集物理表；右表字段以 r_ 前缀写入数据集。" />
        <el-form label-width="100px">
          <el-form-item label="关联类型">
            <el-select v-model="join.joinType" style="width:160px">
              <el-option label="LEFT JOIN" value="LEFT" />
              <el-option label="INNER JOIN" value="INNER" />
              <el-option label="RIGHT JOIN" value="RIGHT" />
            </el-select>
          </el-form-item>
          <el-form-item label="右表" required>
            <el-select v-model="join.rightTable" filterable :loading="tablesLoading" placeholder="选择表" style="width:100%" @change="rightTableChanged">
              <el-option v-for="t in tables" :key="t.name" :value="t.name" :label="t.comment ? `${t.name} · ${t.comment}` : t.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="左表键" required>
            <el-select v-model="join.leftKey" filterable placeholder="左表关联键" style="width:100%">
              <el-option v-for="f in leftFields" :key="f.originName" :value="f.originName" :label="f.showName && f.showName !== f.originName ? `${f.showName} (${f.originName})` : f.originName" />
            </el-select>
          </el-form-item>
          <el-form-item label="右表键" required>
            <el-select v-model="join.rightKey" filterable placeholder="右表关联键" style="width:100%">
              <el-option v-for="c in rightColumns" :key="c.name" :value="c.name" :label="c.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="右表字段">
            <el-table :data="rightColumns" border size="small" height="200" v-loading="columnsLoading">
              <el-table-column label="选择" width="65">
                <template #default="{ row }">
                  <el-checkbox :model-value="join.rightFields.includes(row.name)" @change="v => toggleRight(row.name, !!v)" />
                </template>
              </el-table-column>
              <el-table-column prop="name" label="字段" />
              <el-table-column prop="type" label="类型" width="120" />
              <el-table-column prop="comment" label="库表注释" min-width="120" show-overflow-tooltip />
            </el-table>
          </el-form-item>
        </el-form>
        <div class="footer">
          <el-button type="danger" plain :loading="saving" @click="clearJoin">移除关联</el-button>
          <el-button type="primary" :loading="saving" @click="saveJoin">应用关联</el-button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="显示名称" name="labels">
        <el-alert type="info" :closable="false" show-icon class="mb8" title="业务用户看到的是显示名称；查询与权限仍使用物理字段名（originName）。" />
        <el-table :data="physicalFields" border size="small" max-height="360">
          <el-table-column prop="originName" label="物理字段" min-width="140" />
          <el-table-column label="显示名称" min-width="180">
            <template #default="{ row }"><el-input v-model="row.showName" maxlength="200" @change="() => saveFieldLabel(row)" /></template>
          </el-table-column>
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <el-select v-model="row.dimFlag" style="width:90px" @change="() => saveFieldLabel(row)">
                <el-option label="维度" value="1" /><el-option label="度量" value="0" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="groupName" label="分组" width="90" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="计算字段" name="calc">
        <el-alert type="info" :closable="false" title="计算步骤按表格顺序执行；后续字段可引用前面建立的计算字段。支持 TRIM、UPPER、LOWER、COALESCE、ROUND、ABS 等安全函数。" />
        <el-form :inline="true" class="mb8">
          <el-form-item label="字段名"><el-input v-model="calc.originName" placeholder="profit_rate" /></el-form-item>
          <el-form-item label="显示名"><el-input v-model="calc.showName" placeholder="利润率" /></el-form-item>
          <el-form-item label="维度">
            <el-select v-model="calc.dimFlag" style="width:100px"><el-option label="维度" value="1" /><el-option label="度量" value="0" /></el-select>
          </el-form-item>
        </el-form>
        <el-form-item label="表达式" label-width="100px">
          <el-input v-model="calc.formula" type="textarea" :rows="3" placeholder="如 [amount] / [qty]，支持 ROUND/ABS 等" />
        </el-form-item>
        <el-button type="primary" :loading="saving" @click="addCalc">添加计算字段</el-button>
        <el-table :data="calcFields" border size="small" class="mt8">
          <el-table-column prop="originName" label="字段" />
          <el-table-column prop="showName" label="显示名" />
          <el-table-column prop="extFormula" label="表达式" show-overflow-tooltip />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeCalc(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="质量检查" name="quality">
        <div class="quality-toolbar"><span>基于当前权限可见的最多 100 行样本</span><el-button :loading="qualityLoading" @click="loadQuality">运行检查</el-button></div>
        <el-alert v-if="quality" type="info" :closable="false" :title="`${quality.sampledRows} 行样本 · ${quality.duplicateRows} 行重复 · ${quality.note}`" />
        <el-table v-if="quality" :data="quality.columns" border size="small" class="mt8"><el-table-column prop="field" label="字段"/><el-table-column prop="nullCount" label="空值" width="90"/><el-table-column label="空值率" width="110"><template #default="{row}">{{ (row.nullRate*100).toFixed(1) }}%</template></el-table-column><el-table-column prop="distinctCount" label="不同值" width="100"/><el-table-column prop="duplicateValueCount" label="重复值" width="100"/></el-table>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDataset, getDatasetQuality, listTables, listColumns, listModelSources, getDatasetModel, applyDatasetJoin, clearDatasetJoin, addCalculatedField, removeCalculatedField, updateDatasetField, type DatasetField, type CatalogTable, type CatalogColumn, type DatasetJoinModel } from '@/api/bi/dataset'

const props = defineProps<{ visible: boolean; datasetId: number; name: string }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'saved'): void }>()

const tab = ref('join')
const saving = ref(false)
const schemaName = ref('')
const sourceCatalog = ref('')
const leftFields = ref<DatasetField[]>([])
const tables = ref<CatalogTable[]>([])
const tablesLoading = ref(false)
const rightColumns = ref<CatalogColumn[]>([])
const columnsLoading = ref(false)
const join = reactive<DatasetJoinModel>({ joinType: 'LEFT', rightTable: '', leftKey: '', rightKey: '', rightFields: [] })
const calc = reactive({ originName: '', showName: '', formula: '', dimFlag: '1' })
const calcFields = computed(() => leftFields.value.filter(f => f.extFlag === '1'))
const physicalFields = computed(() => leftFields.value.filter(f => f.extFlag !== '1'))
const quality=ref<{sampleLimit:number;sampledRows:number;duplicateRows:number;note:string;columns:{field:string;nullCount:number;nonNullCount:number;distinctCount:number;duplicateValueCount:number;nullRate:number}[]}>(),qualityLoading=ref(false)
async function loadQuality(){qualityLoading.value=true;try{quality.value=(await getDatasetQuality(props.datasetId)).data}catch{ElMessage.error('质量检查失败，请确认数据集查询权限及数据源状态')}finally{qualityLoading.value=false}}

async function saveFieldLabel(row: DatasetField) {
  if (!row.id || !props.datasetId) return
  saving.value = true
  try {
    await updateDatasetField(props.datasetId, row.id, { showName: row.showName?.trim() || row.originName, dimFlag: row.dimFlag as '0' | '1' })
    ElMessage.success('已保存')
    emit('saved')
  } finally { saving.value = false }
}

async function load() {
  if (!props.datasetId) return
  const detail = await getDataset(props.datasetId)
  const row = detail.data!
  sourceCatalog.value = row.sourceCatalog || ''
  leftFields.value = row.fields || []
  const sources = await listModelSources()
  const source = sources.data?.find(s => s.id === row.datasourceId)
  schemaName.value = source?.schemaName || ''
  if (schemaName.value) {
    tablesLoading.value = true
    try {
      tables.value = (await listTables(schemaName.value, sourceCatalog.value)).data || []
    } finally { tablesLoading.value = false }
  }
  const model = await getDatasetModel(props.datasetId)
  const saved = model.data?.join as DatasetJoinModel | undefined
  if (saved) Object.assign(join, saved)
  if (join.rightTable) await rightTableChanged()
}

function tabChanged(name:string|number){if(name==='quality'&&!quality.value)void loadQuality()}

function toggleRight(name: string, on: boolean) {
  if (on && !join.rightFields.includes(name)) join.rightFields.push(name)
  else join.rightFields = join.rightFields.filter(n => n !== name)
}

async function rightTableChanged() {
  if (!schemaName.value || !join.rightTable) { rightColumns.value = []; return }
  columnsLoading.value = true
  try {
    rightColumns.value = (await listColumns(schemaName.value, join.rightTable, sourceCatalog.value)).data || []
  } finally { columnsLoading.value = false }
}

async function saveJoin() {
  if (!join.rightTable || !join.leftKey || !join.rightKey || !join.rightFields.length) {
    ElMessage.warning('请完整填写关联配置'); return
  }
  saving.value = true
  try {
    await applyDatasetJoin(props.datasetId, { ...join })
    ElMessage.success('关联已应用')
    emit('saved')
    await load()
  } finally { saving.value = false }
}

async function clearJoin() {
  await ElMessageBox.confirm('确定移除关联并删除右表字段？', '提示')
  saving.value = true
  try {
    await clearDatasetJoin(props.datasetId)
    ElMessage.success('已移除关联')
    join.rightTable = ''; join.leftKey = ''; join.rightKey = ''; join.rightFields = []
    emit('saved')
    await load()
  } finally { saving.value = false }
}

async function addCalc() {
  if (!calc.originName || !calc.formula) { ElMessage.warning('请填写字段名与表达式'); return }
  saving.value = true
  try {
    await addCalculatedField(props.datasetId, { ...calc })
    ElMessage.success('已添加')
    calc.originName = ''; calc.showName = ''; calc.formula = ''
    emit('saved')
    const detail = await getDataset(props.datasetId)
    leftFields.value = detail.data?.fields || []
  } finally { saving.value = false }
}

async function removeCalc(row: DatasetField) {
  if (!row.id) return
  saving.value = true
  try {
    await removeCalculatedField(props.datasetId, row.id)
    ElMessage.success('已删除')
    emit('saved')
    leftFields.value = leftFields.value.filter(f => f.id !== row.id)
  } finally { saving.value = false }
}
</script>

<style scoped>
.footer { display: flex; gap: 8px; justify-content: flex-end; margin-top: 12px; }
.mb8 { margin-bottom: 8px; }
.mt8 { margin-top: 8px; }
</style>
