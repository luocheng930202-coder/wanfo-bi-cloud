<template>
  <div class="app-container">
    <el-row :gutter="16">
      <el-col :xs="24" :sm="6">
        <el-card class="mb8">
          <template #header><div class="toolbar"><span>数据集目录</span><el-button v-hasPermi="['bi:dataset:group:add']" link @click="newGroup">新建</el-button></div></template>
          <el-button link @click="selectGroup(null)">全部数据集 / 根目录</el-button>
          <el-tree :data="groups" node-key="id" :props="{label:'name',children:'children'}" default-expand-all highlight-current @node-click="selectGroup" />
          <el-button v-if="currentGroup" v-hasPermi="['bi:dataset:group:remove']" link type="danger" @click="removeGroup">删除当前目录</el-button>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="18">
        <el-card>
          <template #header><div class="toolbar">
            <span>{{ currentGroup?.name || '全部数据集' }}</span>
            <div>
              <el-button v-if="checkPermi(['bi:datasource:model'])" v-hasPermi="['bi:dataset:add']" type="primary" @click="openCreate('table')">新建表数据集</el-button>
              <el-button v-if="checkPermi(['bi:resource:admin'])" @click="openCreate('sql')">SQL 数据集</el-button>
            </div>
          </div></template>
          <el-table :data="datasets" v-loading="loading" border>
            <el-table-column prop="name" label="名称" min-width="150" />
            <el-table-column prop="sourceCatalog" label="数据库" min-width="110" />
            <el-table-column prop="sourceTable" label="来源表" min-width="120" />
            <el-table-column prop="fieldCount" label="字段数" width="85" />
            <el-table-column label="操作" width="450" fixed="right"><template #default="{row}">
              <el-button v-if="row.type==='table' && checkPermi(['bi:datasource:model'])" v-hasPermi="['bi:dataset:add']" link type="primary" @click="openModel(row)">建模</el-button>
              <el-button v-if="row.resourceActions?.includes('query')" v-hasPermi="['bi:dataset:query']" link type="primary" @click="previewSaved(row)">预览</el-button>
              <el-button link @click="viewDefinition(row)">定义</el-button>
              <el-button v-if="row.type==='table' && row.resourceActions?.includes('query')" v-hasPermi="['bi:chart:add']" link type="primary" @click="router.push({path:'/bi-admin/chart',query:{datasetId:row.id}})">建图表</el-button>
              <el-button v-if="row.type==='table' && checkPermi(['bi:resource:admin'])" link type="primary" @click="Object.assign(policyDialog,{visible:true,id:row.id,name:row.name})">数据权限</el-button>
              <el-button v-if="row.resourceActions?.includes('grant')" v-hasPermi="['bi:dataset:grant']" link type="primary" @click="openGrants(row)">授权</el-button>
              <DatasetCacheButton v-if="row.type==='table'" :id="row.id"/><ResourceImpactButton type="dataset" :id="row.id"/><el-button v-if="row.resourceActions?.includes('remove')" v-hasPermi="['bi:dataset:remove']" link type="danger" @click="removeDataset(row)">删除</el-button>
            </template></el-table-column>
            <template #empty><el-empty description="暂无有权查看的数据集" /></template>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="createDialog.visible" :title="createDialog.mode === 'table' ? '新建表数据集' : '新建 SQL 数据集'" width="min(980px, calc(100vw - 24px))" top="4vh" @closed="invalidateModel">
      <el-alert title="建模范围为数据源连接串指定的数据库，预览最多 100 行。" type="info" :closable="false" show-icon class="mb8" />
      <el-form label-width="100px">
        <el-form-item label="名称" required><el-input v-model="form.name" placeholder="数据集名称" /></el-form-item>
        <el-form-item label="数据源" required>
          <el-select v-model="form.datasourceId" placeholder="选择有权建模的数据源" @change="sourceChanged" style="width:100%">
            <el-option v-for="source in sources" :key="source.id" :value="source.id" :label="source.name" />
          </el-select>
        </el-form-item>
        <template v-if="createDialog.mode === 'table'">
          <el-form-item label="数据库" required>
            <el-select v-model="form.sourceCatalog" placeholder="选择数据库" :disabled="!form.datasourceId" @change="catalogChanged" style="width:100%">
              <el-option v-for="name in catalogs" :key="name" :value="name" :label="name" />
            </el-select>
          </el-form-item>
          <el-form-item label="物理表" required>
            <el-select v-model="form.sourceTable" filterable :loading="modelLoading" :disabled="!form.sourceCatalog" placeholder="选择表或视图" @change="tableChanged" style="width:100%">
              <el-option v-for="table in tables" :key="table.name" :value="table.name" :label="table.comment ? `${table.name} · ${table.comment}` : table.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="字段选择">
            <div style="width:100%">
              <div class="toolbar mb8"><span>已选 {{ form.fields.length }} / {{ columns.length }} 个字段</span><div><el-button link @click="selectAll">全选</el-button><el-button link @click="form.fields = []">清空</el-button></div></div>
              <el-table :data="columns" border size="small" height="260" v-loading="modelLoading">
                <el-table-column label="选择" width="65"><template #default="{row}"><el-checkbox :model-value="form.fields.includes(row.name)" :aria-label="'选择字段 ' + row.name" @change="value => toggleField(row.name, !!value)" /></template></el-table-column>
                <el-table-column prop="name" label="物理字段名" min-width="120" />
                <el-table-column prop="type" label="类型" min-width="90" />
                <el-table-column label="显示名称（中文）" min-width="160">
                  <template #default="{row}"><el-input v-model="row.showName" :placeholder="row.name" maxlength="200" :disabled="!form.fields.includes(row.name)" /></template>
                </el-table-column>
                <el-table-column label="库表注释" min-width="120" show-overflow-tooltip><template #default="{row}">{{ row.comment || '—' }}</template></el-table-column>
                <el-table-column label="角色" width="100">
                  <template #default="{row}">
                    <el-select v-model="row.dimFlag" :disabled="!form.fields.includes(row.name)" style="width:88px">
                      <el-option label="维度" value="1" /><el-option label="度量" value="0" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="主键" width="60"><template #default="{row}">{{ row.primaryKey ? '是' : '' }}</template></el-table-column>
              </el-table>
            </div>
          </el-form-item>
        </template>
        <el-form-item v-else label="SQL" required><el-input v-model="form.sqlText" type="textarea" :rows="7" placeholder="管理员 SQL 数据集" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <div v-if="draftPreview">
        <el-alert :title="`预览 ${draftPreview.rowCount} 行 · ${draftPreview.costMs} ms（尚未保存）`" type="success" :closable="false" class="mb8" />
        <el-table :data="draftPreview.rows" border size="small" height="200"><el-table-column v-for="col in draftPreview.columns" :key="col" :prop="col" :label="draftColumnLabel(col)" min-width="130" show-overflow-tooltip /></el-table>
      </div>
      <template #footer><div class="footer">
        <el-button v-if="createDialog.mode === 'table'" :disabled="!form.sourceTable || !form.fields.length || modelLoading || saving" :loading="previewLoading" @click="previewDraft">预览数据</el-button>
        <el-button @click="createDialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="modelLoading || previewLoading" @click="saveDataset">保存数据集</el-button>
      </div></template>
    </el-dialog>

    <DataPolicyDialog v-model="policyDialog.visible" :dataset-id="policyDialog.id" :name="policyDialog.name" />
    <DatasetModelDialog v-model:visible="modelDialog.visible" :dataset-id="modelDialog.id" :name="modelDialog.name" @saved="loadDatasets" />
    <el-dialog v-model="previewDialog.visible" :title="previewDialog.title" width="min(1000px, calc(100vw - 24px))">
      <el-table :data="previewDialog.rows" border size="small" height="360"><el-table-column v-for="col in previewDialog.columns" :key="col" :prop="col" :label="savedColumnLabel(col)" min-width="140" show-overflow-tooltip /></el-table>
      <p>{{ previewDialog.rows.length }} 行 · {{ previewDialog.costMs }} ms</p>
    </el-dialog>
    <el-dialog v-model="definition.visible" title="数据集定义" width="min(760px, calc(100vw - 24px))"><el-input :model-value="definition.sql" type="textarea" :rows="8" readonly /></el-dialog>
    <el-dialog v-model="grantDialog.visible" :title="'数据集授权 · ' + grantDialog.name" width="min(680px, calc(100vw - 24px))">
      <el-alert title="查看和查询分别授权；数据集查询者无需获得原始数据源建模权限。" type="info" :closable="false" class="mb8" />
      <div class="footer mb8">
        <el-select v-model="grantDialog.principalId" filterable remote :remote-method="loadPrincipals" placeholder="搜索用户、角色或部门" style="width:250px"><el-option v-for="p in principals" :key="p.id" :value="p.id" :label="`${principalTypes[p.type] || p.type} · ${p.name}`" /></el-select>
        <el-select v-model="grantDialog.action" style="width:130px"><el-option v-for="(label,key) in grantActions" :key="key" :value="key" :label="label" /></el-select>
        <el-button :disabled="!grantDialog.principalId" :loading="grantSaving" type="primary" @click="addGrant">授权</el-button>
      </div>
      <el-table :data="grants" border><el-table-column prop="displayName" label="对象" /><el-table-column label="类型"><template #default="{row}">{{ principalTypes[row.principalType] || row.principalType }}</template></el-table-column><el-table-column label="权限"><template #default="{row}">{{ grantActions[row.action] || row.action }}</template></el-table-column><el-table-column label="操作"><template #default="{row}"><el-button link type="danger" @click="removeGrant(row)">撤销</el-button></template></el-table-column></el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="BiDataset">
import DatasetCacheButton from '../components/DatasetCacheButton.vue'
import ResourceImpactButton from '../components/ResourceImpactButton.vue'
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import DataPolicyDialog from './DataPolicyDialog.vue'
import DatasetModelDialog from './DatasetModelDialog.vue'
const policyDialog=reactive({visible:false,id:0,name:''})
const modelDialog=reactive({visible:false,id:0,name:''})
import { getGroupTree, createGroup, deleteGroup, listDatasets, getDataset, listCatalogs, listTables, listColumns, listModelSources, previewTable, createTableDataset, createSqlDataset, previewDataset, deleteDataset, searchDatasetPrincipals, listDatasetGrants, grantDataset, revokeDatasetGrant, type DatasetGroupNode, type DatasetSummary, type ModelDatasource, type CatalogTable, type CatalogColumn, type PreviewResult, type DatasetField } from '@/api/bi/dataset'
import { previewColumnLabel } from '@/utils/biFieldLabels'
import type { GrantPrincipal, ResourceGrant } from '@/api/bi/datasource'

const route = useRoute()
const router = useRouter()
const groups = ref<DatasetGroupNode[]>([])
const currentGroup = ref<DatasetGroupNode | null>(null)
const datasets = ref<DatasetSummary[]>([])
const loading = ref(false)
const sources = ref<ModelDatasource[]>([])
const catalogs = ref<string[]>([])
const tables = ref<CatalogTable[]>([])
type WizardColumn = CatalogColumn & { showName: string; dimFlag: '0' | '1' }
const columns = ref<WizardColumn[]>([])
const savedPreviewFields = ref<DatasetField[]>([])
const modelLoading = ref(false)
const previewLoading = ref(false)
const saving = ref(false)
const createDialog = reactive({ visible:false, mode:'table' as 'table' | 'sql' })
const form = reactive({ name:'', datasourceId:undefined as number | undefined, sourceCatalog:'', sourceTable:'', fields:[] as string[], sqlText:'', remark:'' })
const draftPreview = ref<PreviewResult | null>(null)
let modelVersion=0, draftVersion=0, savedPreviewVersion=0, datasetListVersion=0
const fingerprint = () => JSON.stringify([form.datasourceId, form.sourceCatalog, form.sourceTable, form.fields])
watch(fingerprint, () => { draftPreview.value=null })
const previewDialog = reactive({ visible:false, title:'', columns:[] as string[], rows:[] as Record<string,unknown>[], costMs:0 })
const definition = reactive({ visible:false, sql:'' })

async function loadGroups() { groups.value=(await getGroupTree()).data || [] }
async function loadDatasets() {
  const version=++datasetListVersion
  loading.value=true
  try { const res=await listDatasets(currentGroup.value?.id); if(version===datasetListVersion) datasets.value=res.data || [] }
  finally { if(version===datasetListVersion) loading.value=false }
}
function selectGroup(group:DatasetGroupNode | null) { currentGroup.value=group; loadDatasets() }
async function newGroup() {
  try {
    const result=await ElMessageBox.prompt('请输入目录名称','新建目录',{ inputValidator:(value:string)=>!!value?.trim() || '请输入目录名称' })
    await createGroup(result.value.trim(),currentGroup.value?.id); await loadGroups(); ElMessage.success('目录已创建')
  } catch { /* cancellation or API error already shown */ }
}
async function removeGroup() {
  if(!currentGroup.value) return
  try { await ElMessageBox.confirm('确定删除当前目录？目录须为空。','删除目录',{type:'warning'}); await deleteGroup(currentGroup.value.id); currentGroup.value=null; await Promise.all([loadGroups(),loadDatasets()]) } catch { }
}
function invalidateModel() { modelVersion++; draftVersion++; previewLoading.value=false; draftPreview.value=null; modelLoading.value=false }
async function openCreate(mode:'table'|'sql', sourceId?:number) {
  invalidateModel()
  Object.assign(form,{name:'',datasourceId:undefined,sourceCatalog:'',sourceTable:'',fields:[],sqlText:'',remark:''})
  catalogs.value=[]; tables.value=[]; columns.value=[]; sources.value=[]
  createDialog.mode=mode; createDialog.visible=true
  const version=modelVersion
  try {
    const res=await listModelSources()
    if(version!==modelVersion || !createDialog.visible) return
    sources.value=res.data || []
    if(sourceId && sources.value.some(s=>s.id===sourceId)) { form.datasourceId=sourceId; await sourceChanged(sourceId) }
  } catch { }
}
async function sourceChanged(id:number) {
  const version=++modelVersion
  draftPreview.value=null; form.sourceCatalog=''; form.sourceTable=''; form.fields=[]; tables.value=[]; columns.value=[]; catalogs.value=[]
  const source=sources.value.find(s=>s.id===id)
  if(!source || createDialog.mode==='sql') return
  modelLoading.value=true
  try {
    const res=await listCatalogs(source.schemaName)
    if(version!==modelVersion || !createDialog.visible) return
    catalogs.value=res.data || []; form.sourceCatalog=catalogs.value[0] || ''
    if(form.sourceCatalog) await catalogChanged(form.sourceCatalog)
  } finally { if(version===modelVersion) modelLoading.value=false }
}
async function catalogChanged(catalog:string) {
  const version=++modelVersion
  form.sourceTable=''; form.fields=[]; columns.value=[]; tables.value=[]; draftPreview.value=null
  const source=sources.value.find(s=>s.id===form.datasourceId)
  if(!source) return
  modelLoading.value=true
  try { const res=await listTables(source.schemaName,catalog); if(version===modelVersion && createDialog.visible) tables.value=res.data || [] }
  finally { if(version===modelVersion) modelLoading.value=false }
}
async function tableChanged(table:string) {
  const version=++modelVersion
  form.fields=[]; columns.value=[]; draftPreview.value=null
  const source=sources.value.find(s=>s.id===form.datasourceId)
  if(!source) return
  modelLoading.value=true
  try {
    const res=await listColumns(source.schemaName,table,form.sourceCatalog)
    if(version!==modelVersion || !createDialog.visible) return
    columns.value=(res.data || []).map(c=>({...c,showName:(c.comment?.trim()||c.name),dimFlag:c.suggestAs==='metric'?'0':'1'}))
    form.fields=columns.value.slice(0,200).map(c=>c.name)
  } finally { if(version===modelVersion) modelLoading.value=false }
}
function selectAll() { form.fields=columns.value.slice(0,200).map(c=>c.name) }
function toggleField(name:string,selected:boolean) { form.fields=selected ? [...new Set([...form.fields,name])] : form.fields.filter(f=>f!==name) }
async function previewDraft() {
  const source=sources.value.find(s=>s.id===form.datasourceId)
  if(!source || !form.sourceTable || !form.fields.length) return
  const version=++draftVersion, snapshot=fingerprint()
  previewLoading.value=true
  try {
    const res=await previewTable(source.schemaName,{catalog:form.sourceCatalog,table:form.sourceTable,fields:[...form.fields],maxRows:100})
    if(version===draftVersion && snapshot===fingerprint() && createDialog.visible) draftPreview.value=res.data
  } finally { if(version===draftVersion) previewLoading.value=false }
}
async function saveDataset() {
  if(!form.name.trim() || !form.datasourceId) { ElMessage.warning('请填写名称并选择数据源'); return }
  if(createDialog.mode==='table' && (!form.sourceCatalog || !form.sourceTable || !form.fields.length)) { ElMessage.warning('请选择数据库、表和字段'); return }
  saving.value=true
  try {
    if(createDialog.mode==='table') {
      const picked=new Set(form.fields)
      const fieldPayload=columns.value.filter(c=>picked.has(c.name)).map(c=>({name:c.name,showName:c.showName?.trim()||c.name,dimFlag:c.dimFlag}))
      await createTableDataset({name:form.name.trim(),groupId:currentGroup.value?.id,datasourceId:form.datasourceId,sourceCatalog:form.sourceCatalog,sourceTable:form.sourceTable,fields:fieldPayload,remark:form.remark})
    }
    else await createSqlDataset({name:form.name.trim(),groupId:currentGroup.value?.id,datasourceId:form.datasourceId,sqlText:form.sqlText,remark:form.remark})
    createDialog.visible=false; ElMessage.success('数据集已保存'); await Promise.all([loadGroups(),loadDatasets()])
  } finally { saving.value=false }
}
function draftColumnLabel(col:string){const row=columns.value.find(c=>c.name===col);return row?.showName?.trim()||col}
function savedColumnLabel(col:string){return previewColumnLabel(savedPreviewFields.value,col)}
async function previewSaved(row:DatasetSummary) {
  const version=++savedPreviewVersion
  const [res,detail]=await Promise.all([previewDataset(row.id,100),getDataset(row.id)])
  if(version!==savedPreviewVersion) return
  if(!res.data?.ok) { ElMessage.error(res.data?.message || '预览失败'); return }
  savedPreviewFields.value=detail.data?.fields || []
  Object.assign(previewDialog,{visible:true,title:row.name+' · 数据预览',columns:res.data.columns,rows:res.data.rows,costMs:res.data.costMs})
}
function openModel(row: DatasetSummary) { Object.assign(modelDialog, { visible: true, id: row.id, name: row.name }) }
async function viewDefinition(row:DatasetSummary) { const res=await getDataset(row.id); definition.sql=res.data.unionSql || res.data.sqlText || ''; definition.visible=true }
async function removeDataset(row:DatasetSummary) {
  try { await ElMessageBox.confirm(`确定删除数据集「${row.name}」？`,'删除数据集',{type:'warning'}); await deleteDataset(row.id); await Promise.all([loadGroups(),loadDatasets()]) } catch { }
}

const grantActions:Record<string,string>={view:'查看',query:'查询/预览',consume:'看板消费（不可自由查询）',write:'填报写入',remove:'删除',grant:'授权管理'}
const principalTypes:Record<string,string>={user:'用户',role:'角色',organization:'部门'}
const grantDialog=reactive({visible:false,id:0,name:'',principalId:undefined as number | undefined,action:'view'})
const principals=ref<GrantPrincipal[]>([]), grants=ref<ResourceGrant[]>([]), grantSaving=ref(false)
let principalVersion=0
async function loadPrincipals(query='') { const version=++principalVersion; const res=await searchDatasetPrincipals(query); if(version===principalVersion) principals.value=res.data || [] }
async function loadGrants() { grants.value=(await listDatasetGrants(grantDialog.id)).data || [] }
async function openGrants(row:DatasetSummary) { Object.assign(grantDialog,{visible:true,id:row.id,name:row.name,principalId:undefined,action:'view'}); grants.value=[]; await Promise.all([loadPrincipals(),loadGrants()]) }
async function addGrant() { if(!grantDialog.principalId) return; grantSaving.value=true; try { await grantDataset(grantDialog.id,grantDialog.principalId,grantDialog.action); await loadGrants(); ElMessage.success('已授权') } finally { grantSaving.value=false } }
async function removeGrant(row:ResourceGrant) { await revokeDatasetGrant(grantDialog.id,row.principalId,row.action); await loadGrants(); ElMessage.success('已撤销') }

onMounted(async()=>{
  await Promise.all([loadGroups(),loadDatasets()])
  const sourceId=Number(route.query.datasourceId)
  if(sourceId && checkPermi(['bi:dataset:add']) && checkPermi(['bi:datasource:model'])) await openCreate('table',sourceId)
})
</script>

<style scoped>
.toolbar { display:flex; align-items:center; justify-content:space-between; gap:8px; flex-wrap:wrap; }
.footer { display:flex; justify-content:flex-end; gap:8px; flex-wrap:wrap; }
</style>
