<template>
  <el-dialog :model-value="visible" :title="'数据集建模 · ' + name" width="min(920px, calc(100vw - 24px))" @update:model-value="emit('update:visible', $event)" @open="load">
    <el-tabs v-model="tab" @tab-change="tabChanged">
      <el-tab-pane label="关联表" name="join">
        <el-alert type="info" :closable="false" show-icon class="mb8" title="按顺序连接多张物理表；后续关系可引用前序关系输出的字段。首条关联字段使用 r_ 前缀，后续字段使用 r2_、r3_ 前缀。" />
        <div class="join-steps"><el-tag v-for="(step,index) in joinSteps" :key="index" :type="index===activeJoinIndex?'primary':'info'" effect="plain" class="join-step" @click="selectJoin(index)">关系 {{ index+1 }} · {{ step.rightTable || '未配置' }}<el-icon v-if="joinSteps.length>1" class="remove-step" @click.stop="removeJoin(index)"><Close /></el-icon></el-tag><el-button size="small" :disabled="joinSteps.length>=8" @click="addJoin">添加关系</el-button></div>
        <el-form label-width="100px">
          <el-form-item label="关联类型">
            <el-select v-model="activeJoin.joinType" style="width:160px">
              <el-option label="LEFT JOIN" value="LEFT" />
              <el-option label="INNER JOIN" value="INNER" />
              <el-option label="RIGHT JOIN" value="RIGHT" />
            </el-select>
          </el-form-item>
          <el-form-item label="右表" required>
            <el-select v-model="activeJoin.rightTable" filterable :loading="tablesLoading" placeholder="选择表" style="width:100%" @change="rightTableChanged">
              <el-option v-for="t in tables" :key="t.name" :value="t.name" :label="t.comment ? `${t.name} · ${t.comment}` : t.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="左表键" required>
            <el-select v-model="activeJoin.leftKey" filterable placeholder="左侧已建模字段" style="width:100%">
              <el-option v-for="f in relationshipFields" :key="f.originName" :value="f.originName" :label="f.showName && f.showName !== f.originName ? `${f.showName} (${f.originName})` : f.originName" />
            </el-select>
          </el-form-item>
          <el-form-item label="右表键" required>
            <el-select v-model="activeJoin.rightKey" filterable placeholder="右表关联键" style="width:100%">
              <el-option v-for="c in rightColumns" :key="c.name" :value="c.name" :label="c.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="右表字段">
            <el-table :data="rightColumns" border size="small" height="200" v-loading="columnsLoading">
              <el-table-column label="选择" width="65">
                <template #default="{ row }">
                  <el-checkbox :model-value="activeJoin.rightFields.includes(row.name)" @change="v => toggleRight(row.name, !!v)" />
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
          <el-button type="primary" :loading="saving" @click="saveJoin">应用全部关系</el-button>
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
        <el-alert type="info" :closable="false" title="清洗步骤按表格顺序执行；后续字段可引用前序步骤。调整顺序时系统会检查依赖，避免把步骤移到依赖字段之前。每次查询都会从数据源重放已保存步骤。支持 TRIM、UPPER、LOWER、COALESCE、ROUND、ABS 等安全函数。" />
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
        <div class="calc-actions"><el-button type="primary" :loading="saving" @click="addCalc">添加清洗步骤</el-button><el-button :loading="replayLoading" @click="replaySteps">预览并回放步骤</el-button></div>
        <el-table :data="calcFields" border size="small" class="mt8">
          <el-table-column prop="originName" label="字段" />
          <el-table-column prop="showName" label="显示名" />
          <el-table-column prop="extFormula" label="表达式" show-overflow-tooltip />
          <el-table-column label="顺序" width="140"><template #default="{ row, $index }"><el-button link :disabled="$index===0" @click="moveCalc(row,-1)">上移</el-button><el-button link :disabled="$index===calcFields.length-1" @click="moveCalc(row,1)">下移</el-button></template></el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeCalc(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="质量检查" name="quality">
        <div class="quality-toolbar"><el-radio-group v-model="qualityFullScan" :disabled="qualityLoading"><el-radio-button :value="false">快速抽样</el-radio-button><el-radio-button :value="true">全量精确扫描</el-radio-button></el-radio-group><div><el-button v-if="quality" v-hasPermi="['bi:datasource:model']" :loading="savingRules" @click="saveQualityRules">保存规则</el-button><el-button type="primary" :loading="qualityLoading" @click="loadQuality">{{ qualityFullScan ? '执行全量扫描' : '运行抽样检查' }}</el-button></div></div>
        <el-alert v-if="qualityFullScan" type="warning" :closable="false" show-icon title="全量扫描会遍历当前数据权限可见的全部数据，可能执行较久；若超过两分钟将终止并返回错误，不会返回部分结果。" />
        <el-alert v-if="quality" :type="quality.qualityStatus==='failed'?'warning':'info'" :closable="false" :title="`${quality.scanMode==='full'?`${quality.scannedRows} 行全量数据`:`${quality.sampledRows} 行样本`} · ${quality.duplicateRows} 行重复 · ${quality.ruleFailureCount} 项规则问题 · ${quality.ruleNote}`" />
        <el-table v-if="quality" :data="qualityRuleRows" border size="small" class="mt8"><el-table-column prop="field" label="字段" min-width="130"/><el-table-column label="必填" width="90"><template #default="{row}"><el-switch v-model="row.required"/></template></el-table-column><el-table-column label="唯一值" width="100"><template #default="{row}"><el-switch v-model="row.unique"/></template></el-table-column><el-table-column label="最大空值率" width="180"><template #default="{row}"><el-input-number v-model="row.maxNullRate" :min="0" :max="1" :step="0.05" :precision="2" :controls="false" placeholder="关闭"/><small>0～1</small></template></el-table-column><el-table-column :label="quality.scanMode==='full'?'全量检查':'抽样检查'" width="125"><template #default="{row}"><el-tag v-if="row.qualityStatus==='failed'" type="danger">未通过</el-tag><el-tag v-else-if="row.qualityStatus==='passed'" type="success">通过</el-tag><el-tag v-else type="info">未配置</el-tag></template></el-table-column><el-table-column label="问题" min-width="150"><template #default="{row}">{{ row.violations?.join('、')||'—' }}</template></el-table-column><el-table-column prop="nullCount" label="空值" width="75"/><el-table-column label="空值率" width="90"><template #default="{row}">{{ (row.nullRate*100).toFixed(1) }}%</template></el-table-column><el-table-column prop="distinctCount" label="不同值" width="90"/></el-table>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="replayVisible" title="清洗步骤回放预览" width="min(900px, calc(100vw - 36px))" append-to-body>
      <el-alert type="info" :closable="false" class="mb8" title="此预览会从数据源重新执行当前已保存的关联和计算步骤，仅展示前 20 行；不会写回源数据。" />
      <el-table v-if="replayResult" :data="replayResult.rows" border size="small" max-height="420"><el-table-column v-for="field in replayResult.columns" :key="field" :prop="field" :label="field" min-width="130" show-overflow-tooltip /></el-table>
      <el-empty v-else description="暂无回放结果" />
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import { getDataset, getDatasetQuality, getDatasetQualityRules, saveDatasetQualityRules, listTables, listColumns, listModelSources, getDatasetModel, applyDatasetJoin, clearDatasetJoin, addCalculatedField, removeCalculatedField, reorderCalculatedFields, previewDataset, updateDatasetField, type DatasetField, type CatalogTable, type CatalogColumn, type DatasetJoinModel, type DatasetJoinStep, type DatasetQualityRule, type DatasetQualityColumn, type DatasetQualityReport, type PreviewResult } from '@/api/bi/dataset'

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
const blankJoin=():DatasetJoinStep=>({joinType:'LEFT',rightTable:'',leftKey:'',rightKey:'',rightFields:[]})
const joinSteps=ref<DatasetJoinStep[]>([blankJoin()]),activeJoinIndex=ref(0)
const activeJoin=computed(()=>joinSteps.value[activeJoinIndex.value]||joinSteps.value[0]||blankJoin())
const relationshipFields=computed(()=>{const generated=new Set<string>();joinSteps.value.forEach((step,index)=>step.rightFields.forEach(name=>generated.add((index===0?'r_':`r${index+1}_`)+name)));const rows=leftFields.value.filter(f=>f.extFlag!=='1'&&!generated.has(f.originName));const existing=new Set(rows.map(f=>f.originName));joinSteps.value.slice(0,activeJoinIndex.value).forEach((step,index)=>step.rightFields.forEach(name=>{const key=(index===0?'r_':`r${index+1}_`)+name;if(!existing.has(key)){rows.push({originName:key,showName:key,dimFlag:'1',extFlag:'0'});existing.add(key)}}));return rows})
const calc = reactive({ originName: '', showName: '', formula: '', dimFlag: '1' })
const calcFields = computed(() => leftFields.value.filter(f => f.extFlag === '1'))
const physicalFields = computed(() => leftFields.value.filter(f => f.extFlag !== '1'))
const replayVisible=ref(false),replayLoading=ref(false),replayResult=ref<PreviewResult>()
const quality=ref<DatasetQualityReport>(),qualityFullScan=ref(false),qualityLoading=ref(false),savingRules=ref(false)
const qualityRuleRows=ref<(DatasetQualityRule&DatasetQualityColumn)[]>([])
async function loadQuality(){if(qualityLoading.value)return;qualityLoading.value=true;try{const [report,saved]=await Promise.all([getDatasetQuality(props.datasetId,qualityFullScan.value),getDatasetQualityRules(props.datasetId)]);quality.value=report.data;const configured=new Map((saved.data||[]).map(rule=>[rule.field,rule]));qualityRuleRows.value=report.data.columns.map(column=>({...column,required:configured.get(column.field)?.required||false,unique:configured.get(column.field)?.unique||false,maxNullRate:configured.get(column.field)?.maxNullRate??undefined}))}catch{ElMessage.error('质量检查失败，请确认数据集查询权限及数据源状态')}finally{qualityLoading.value=false}}
async function saveQualityRules(){if(savingRules.value)return;savingRules.value=true;try{const rules=qualityRuleRows.value.filter(row=>row.required||row.unique||row.maxNullRate!==undefined&&row.maxNullRate!==null).map(row=>({field:row.field,required:row.required,unique:row.unique,maxNullRate:row.maxNullRate}));await saveDatasetQualityRules(props.datasetId,rules);ElMessage.success('质量规则已保存');await loadQuality()}catch(e){ElMessage.error(e instanceof Error?e.message:'规则保存失败')}finally{savingRules.value=false}}

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
  const steps=saved?.joins?.length?saved.joins:saved?.rightTable?[{joinType:(saved.joinType||'LEFT') as DatasetJoinStep['joinType'],rightTable:saved.rightTable,leftKey:saved.leftKey||'',rightKey:saved.rightKey||'',rightFields:saved.rightFields||[]}]:[]
  joinSteps.value=steps.length?steps.map(s=>({...s,rightFields:[...(s.rightFields||[])]})):[blankJoin()];activeJoinIndex.value=0
  if (activeJoin.value.rightTable) await rightTableChanged()
}

function tabChanged(name:string|number){if(name==='quality'&&!quality.value)void loadQuality()}

function toggleRight(name: string, on: boolean) {
  const step=activeJoin.value
  if (on && !step.rightFields.includes(name)) step.rightFields.push(name)
  else step.rightFields = step.rightFields.filter(n => n !== name)
}

function selectJoin(index:number){activeJoinIndex.value=index;rightColumns.value=[];if(activeJoin.value.rightTable)void rightTableChanged()}
function addJoin(){if(joinSteps.value.length>=8)return;joinSteps.value.push(blankJoin());selectJoin(joinSteps.value.length-1)}
function removeJoin(index:number){if(joinSteps.value.length<=1)return;joinSteps.value.splice(index,1);selectJoin(Math.min(activeJoinIndex.value,joinSteps.value.length-1))}

async function rightTableChanged() {
  if (!schemaName.value || !activeJoin.value.rightTable) { rightColumns.value = []; return }
  columnsLoading.value = true
  try {
    rightColumns.value = (await listColumns(schemaName.value, activeJoin.value.rightTable, sourceCatalog.value)).data || []
  } finally { columnsLoading.value = false }
}

async function saveJoin() {
  if (joinSteps.value.some(step=>!step.rightTable||!step.leftKey||!step.rightKey||!step.rightFields.length)) { ElMessage.warning('请完整填写每条关联的表、左右键和输出字段'); return }
  saving.value = true
  try {
    const first=joinSteps.value[0]
    await applyDatasetJoin(props.datasetId, { ...first, joins:joinSteps.value.map(step=>({...step,rightFields:[...step.rightFields]})) })
    ElMessage.success(`已应用 ${joinSteps.value.length} 条关联`)
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
    joinSteps.value=[blankJoin()];activeJoinIndex.value=0
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

async function reloadFields(){const detail=await getDataset(props.datasetId);leftFields.value=detail.data?.fields||[]}
async function moveCalc(row:DatasetField,direction:number){const ordered=[...calcFields.value];const from=ordered.findIndex(f=>f.id===row.id),to=from+direction;if(from<0||to<0||to>=ordered.length)return;[ordered[from],ordered[to]]=[ordered[to]!,ordered[from]!];saving.value=true;try{await reorderCalculatedFields(props.datasetId,ordered.map(f=>f.id!).filter(Boolean));await reloadFields();ElMessage.success('清洗步骤顺序已更新')}catch(e){ElMessage.error(e instanceof Error?e.message:'步骤顺序无效，请先处理字段依赖')}finally{saving.value=false}}
async function replaySteps(){replayLoading.value=true;try{const result=await previewDataset(props.datasetId,20);replayResult.value=result.data;replayVisible.value=true}catch(e){ElMessage.error(e instanceof Error?e.message:'回放失败，请检查数据源和当前配置')}finally{replayLoading.value=false}}
</script>

<style scoped>
.footer { display: flex; gap: 8px; justify-content: flex-end; margin-top: 12px; }
.mb8 { margin-bottom: 8px; }
.mt8 { margin-top: 8px; }
.join-steps{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:0 0 14px 100px}.join-step{cursor:pointer}.remove-step{margin-left:6px;vertical-align:-2px}
.calc-actions{display:flex;gap:8px}
</style>
