<template>
  <div class="board-library chart-library">
    <header class="library-heading"><h1>图表管理</h1><p>管理可复用的数据图表，用真实预览快速找到需要的分析。</p></header>
    <section class="library-toolbar" aria-label="图表筛选与创建">
      <div class="library-filters">
        <el-input v-model="search" clearable :prefix-icon="Search" placeholder="搜索图表名称" aria-label="搜索图表名称" class="board-search"/>
        <el-select v-model="typeFilter" aria-label="图表类型" class="board-status"><el-option label="全部类型" value="all"/><el-option v-for="(label,key) in types" :key="key" :label="label" :value="key"/></el-select>
      </div>
      <div class="library-controls">
        <el-button :icon="Refresh" :loading="listLoading" aria-label="刷新图表列表" title="刷新列表" class="refresh-button" @click="refresh"/>
        <div class="view-switch" role="group" aria-label="展示方式"><button type="button" :class="{active:displayMode==='grid'}" :aria-pressed="displayMode==='grid'" aria-label="卡片视图" @click="displayMode='grid'"><el-icon><Grid/></el-icon></button><button type="button" :class="{active:displayMode==='list'}" :aria-pressed="displayMode==='list'" aria-label="列表视图" @click="displayMode='list'"><el-icon><List/></el-icon></button></div>
        <el-button v-hasPermi="['bi:chart:add']" type="primary" :icon="Plus" :disabled="busy" @click="newChart()">新建图表</el-button>
      </div>
    </section>
    <el-alert v-if="listError" type="error" :title="listError" :closable="false" show-icon class="library-list-error"><template #default><el-button link @click="refresh">重新加载</el-button></template></el-alert>
    <section v-loading="listLoading" class="library-content" aria-label="图表列表" :aria-busy="listLoading">
      <div v-if="displayMode==='grid' && pageCharts.length" class="board-grid">
        <article v-for="row in pageCharts" :key="row.id" class="board-card">
          <div class="chart-cover"><BiResourceThumbnail kind="chart" :id="row.id" :name="row.name" :version="row.version" :actions="row.resourceActions" :refresh-key="thumbnailRefresh"/><div class="chart-cover-action"><el-button :icon="View" :disabled="busy" @click="openChart(row)">打开图表</el-button></div></div>
          <div class="board-card-details"><div class="board-card-heading"><h2 :title="row.name">{{ row.name }}</h2><ChartActions :chart="row" :disabled="busy" @action="command=>handleChartAction(command,row)"/></div><span class="chart-type">{{ chartTypeLabel(row.type) }}</span><p class="board-card-meta">版本 v{{ row.version }}<span class="meta-divider">·</span>{{ datasetName(row.datasetId) }}</p></div>
        </article>
      </div>
      <el-table v-else-if="displayMode==='list' && pageCharts.length" :data="pageCharts" class="board-table" row-key="id">
        <el-table-column label="图表名称" min-width="230"><template #default="{row}"><div class="board-table-name"><el-icon><Histogram/></el-icon><span :title="row.name">{{ row.name }}</span></div></template></el-table-column>
        <el-table-column label="图表类型" width="150"><template #default="{row}"><span class="chart-type">{{ chartTypeLabel(row.type) }}</span></template></el-table-column>
        <el-table-column label="数据集" min-width="160"><template #default="{row}">{{ datasetName(row.datasetId) }}</template></el-table-column>
        <el-table-column label="版本" width="90"><template #default="{row}">v{{ row.version }}</template></el-table-column>
        <el-table-column label="操作" width="130" align="right"><template #default="{row}"><div class="board-table-actions"><el-button link type="primary" :disabled="busy" @click="openChart(row)">打开</el-button><ChartActions :chart="row" :disabled="busy" @action="command=>handleChartAction(command,row)"/></div></template></el-table-column>
      </el-table>
      <el-empty v-else-if="!listLoading && !listError" :description="search.trim() || typeFilter!=='all' ? '没有找到匹配的图表' : '暂无有权查看的图表'" :image-size="100"><el-button v-if="search.trim() || typeFilter!=='all'" @click="clearFilters">清除筛选</el-button><el-button v-else v-hasPermi="['bi:chart:add']" type="primary" @click="newChart()">创建第一个图表</el-button></el-empty>
    </section>
    <footer v-if="filteredCharts.length" class="library-pagination"><span role="status">共 {{ filteredCharts.length }} 个图表</span><el-pagination v-model:current-page="page" :page-size="pageSize" :total="filteredCharts.length" layout="prev, pager, next" background/></footer>
    <el-card v-if="editorVisible" ref="editorCard" class="editor-card">
      <template #header><div class="toolbar"><span>{{ currentId ? '图表 · '+definition.name : '新建图表' }} <el-tag v-if="dirty" size="small" type="warning">未保存</el-tag><el-tag v-if="!editable" size="small" type="info">只读</el-tag></span><div class="toolbar"><el-button :icon="Close" :disabled="busy" @click="closeEditor">关闭编辑</el-button><el-button :loading="previewing" :disabled="busy || !canPreview" @click="preview">更新数据</el-button><el-button v-if="currentId && !dirty" v-hasPermi="['bi:export:add']" :disabled="busy || !canPreview" @click="exportSaved('csv')">导出 CSV</el-button><el-button v-if="currentId && !dirty" v-hasPermi="['bi:export:add']" :disabled="busy || !canPreview" @click="exportSaved('xlsx')">导出 Excel</el-button><el-button v-if="currentId && !dirty" v-hasPermi="['bi:export:add']" :disabled="busy || !canPreview" @click="exportSaved('pdf')">导出 PDF</el-button><el-button v-if="editable" type="primary" :loading="saving" :disabled="busy || !dirty" @click="save">保存图表</el-button></div></div></template>
      <ChartEditorWorkspace :definition="definition" :fields="fields" :datasets="datasets" :labels="labels" :result="result" :error="error" :loading="loading" :disabled="!editable" :busy="busy" :previewing="previewing" :can-preview="canPreview" :field-label="fieldLabel" @type-changed="typeChanged" @dataset-changed="datasetChanged" @set-dimension="setDimension" @add-metric="addMetric" @pick-metric="addMetricField" @remove-metric="removeMetric" @set-sort="setSort" @raw-table="rawTable" @ensure-scatter="ensureScatterMetrics" @pivot-fields="syncPivotFields" @preview="preview"/>
    </el-card>
    <el-dialog v-model="grantDialog.visible" :title="'图表授权 · '+grantDialog.name" width="min(700px, calc(100vw - 24px))">
      <el-alert title="图表授权不自动授予数据集权限；使用者还需对应数据集的查看、查询及若依功能权限。" :closable="false" class="mb8"/>
      <div class="toolbar mb8"><el-select v-model="grantDialog.principalId" filterable remote :remote-method="searchPrincipals" placeholder="搜索用户、角色或部门"><el-option v-for="p in principals" :key="p.id" :value="p.id" :label="p.type+' · '+p.name"/></el-select><el-select v-model="grantDialog.action" style="width:130px"><el-option v-for="(label,key) in grantActions" :key="key" :value="key" :label="label"/></el-select><el-button :loading="grantSaving" :disabled="!grantDialog.principalId" @click="addGrant">授权</el-button></div>
      <el-table :data="grants" border><el-table-column prop="displayName" label="对象"/><el-table-column label="权限"><template #default="{row}">{{ grantActions[row.action] }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="danger" :disabled="grantSaving" @click="removeGrant(row)">撤销</el-button></template></el-table-column></el-table>
    </el-dialog>
  </div>
</template>
<script setup lang="ts" name="BiChart">
import { ref, reactive, computed, watch, onMounted, onActivated, nextTick } from 'vue'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import { listDatasets, type DatasetSummary } from '@/api/bi/dataset'
import { listCharts, getChart, saveChart, deleteChart, previewChart, savedChartData, chartFields, searchChartPrincipals, listChartGrants, grantChart, revokeChartGrant, type ChartDefinition, type ChartSummary, type Field, type ChartResult } from '@/api/bi/chart'
import { submitExport, type ExportFormat } from '@/api/bi/export'
import type { GrantPrincipal, ResourceGrant } from '@/api/bi/datasource'
import ChartEditorWorkspace from './ChartEditorWorkspace.vue'
import ChartActions from './ChartActions.vue'
import BiResourceThumbnail from '../components/BiResourceThumbnail.vue'
import { Search, Refresh, Grid, List, Plus, Histogram, View, Close } from '@element-plus/icons-vue'
import { CHART_TYPE_LABELS, applyTypeDefaults, isChartType, metricAddDisabled, validateChartQueryShape } from '@/utils/biChartTypes'
import { defaultChartAdvanced, defaultChartStyle, ensureChartPresentation } from '@/utils/biChartPresentation'
import { syncPivotQueryDimensions, validatePivotConfig } from '@/utils/biPivotPreview'
import { validateMapConfig } from '@/utils/biMapPreview'
const route=useRoute()
const types=CHART_TYPE_LABELS
const chartTypeLabel=(value:string)=>isChartType(value)?types[value]:value
const aggregations:Record<string,string>={COUNT:'计数',COUNT_DISTINCT:'去重计数',SUM:'求和',AVG:'平均值',MIN:'最小值',MAX:'最大值',NONE:'原始值'}
function empty():ChartDefinition{return {schemaVersion:1,name:'',type:'bar',query:{datasetId:0,dimensions:[],metrics:[],filters:[],orderBys:[],limit:100},style:defaultChartStyle(),advanced:defaultChartAdvanced()}}
const search=ref(''),typeFilter=ref('all'),displayMode=ref<'grid'|'list'>('grid'),page=ref(1),thumbnailRefresh=ref(0),listError=ref('')
const pageSize=6
const editorCard=ref<{$el:HTMLElement}>()
const filteredCharts=computed(()=>charts.value.filter(row=>(!search.value.trim() || row.name.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase())) && (typeFilter.value==='all' || row.type===typeFilter.value)))
const pageCharts=computed(()=>filteredCharts.value.slice((page.value-1)*pageSize,page.value*pageSize))
watch([search,typeFilter],()=>page.value=1)
function clearFilters(){search.value='';typeFilter.value='all'}
function datasetName(id:number){return datasets.value.find(row=>row.id===id)?.name || '数据集 #'+id}
function handleChartAction(command:string,row:ChartSummary){if(busy.value)return;if(command==='open')return openChart(row);if(command==='grant' && row.resourceActions.includes('grant') && checkPermi(['bi:chart:grant']))return openGrants(row);if(command==='remove' && row.resourceActions.includes('remove') && checkPermi(['bi:chart:remove']))return removeChart(row)}
async function scrollToEditor(){await nextTick();editorCard.value?.$el.scrollIntoView({behavior:'smooth',block:'start'})}
async function closeEditor(){if(!await discard())return;++loadVersion;editorVisible.value=false;currentId.value=undefined;result.value=null}
const definition=reactive<ChartDefinition>(empty())
const charts=ref<ChartSummary[]>([]),datasets=ref<DatasetSummary[]>([]),fields=ref<Field[]>([]),result=ref<ChartResult|null>(null)
watch(()=>filteredCharts.value.length,count=>page.value=Math.min(page.value,Math.max(1,Math.ceil(count/pageSize))))
const currentId=ref<number>(),version=ref<number>(),editorVisible=ref(false),loading=ref(false),saving=ref(false),previewing=ref(false),listLoading=ref(false),error=ref(''),baseline=ref('')
let revision=0,loadVersion=0
const fingerprint=()=>JSON.stringify(definition)
const dirty=computed(()=>editorVisible.value && fingerprint()!==baseline.value)
const busy=computed(()=>loading.value || saving.value || previewing.value)
const selected=computed(()=>charts.value.find(c=>c.id===currentId.value))
const editable=computed(()=>currentId.value?!!selected.value?.resourceActions.includes('edit') && checkPermi(['bi:chart:edit']):checkPermi(['bi:chart:add']))
const canPreview=computed(()=>currentId.value && !dirty.value ? !!selected.value?.resourceActions.includes('query') && checkPermi(['bi:chart:query']):editable.value && checkPermi(['bi:dataset:query']))
const datasetOptions=computed(()=>{const id=definition.query.datasetId;return datasets.value.some(d=>d.id===id)||!id?datasets.value:[...datasets.value,{id,name:'数据集 #'+id}]})
const fieldLabel=(name:string)=>{const f=fields.value.find(f=>f.name===name);const show=f?.showName?.trim();return show||name}
const labels=computed(()=>{const names:Record<string,string>={};for(const d of definition.query.dimensions)names[d.alias||d.field]=fieldLabel(d.field);for(const m of definition.query.metrics)names[m.alias||m.field]=`${fieldLabel(m.field)} · ${aggregations[m.agg]}`;return names})
watch(fingerprint,()=>{revision++;result.value=null;error.value=''})
async function discard(){if(!dirty.value)return true;try{await ElMessageBox.confirm('当前图表有未保存的配置，是否放弃？','未保存的修改',{type:'warning'});return true}catch{return false}}
onBeforeRouteLeave(async()=>await discard())
async function refresh(){listLoading.value=true;listError.value='';try{charts.value=(await listCharts()).data||[];thumbnailRefresh.value++}catch{listError.value='图表列表加载失败，请重试'}finally{listLoading.value=false}}
async function availableDatasets(){if(checkPermi(['bi:dataset:list']))datasets.value=((await listDatasets()).data||[]).filter(d=>d.type==='table' && d.resourceActions?.includes('query'))}
function numeric(field:Field){return /(int|decimal|numeric|float|double|real|number)/i.test(field.type)}
async function newChart(source?:number){
  if(!await discard())return
  ++loadVersion;++revision;currentId.value=undefined;version.value=undefined;fields.value=[];result.value=null;Object.assign(definition,empty());editorVisible.value=true
  await nextTick();baseline.value=fingerprint()
  if(source && datasets.value.some(d=>d.id===source)){definition.query.datasetId=source;await datasetChanged(source)}
  await scrollToEditor()
}
async function datasetChanged(id:number){
  const v=++loadVersion;++revision;result.value=null;error.value='';fields.value=[]
  Object.assign(definition.query,{datasetId:id,dimensions:[],metrics:[],filters:[],filterTree:undefined,orderBys:[]});loading.value=true
  try {const r=await chartFields(id);if(v!==loadVersion)return;fields.value=r.data||[];const dim=fields.value.find(f=>!numeric(f))||fields.value[0],metric=fields.value.find(numeric)||fields.value[0];if(dim)definition.query.dimensions=[{field:dim.name,alias:'dimension'}];if(metric)definition.query.metrics=[{field:metric.name,agg:'COUNT',alias:'value_1'}]}
  catch{if(v===loadVersion)error.value='字段加载失败，请检查数据集和列权限'}finally{if(v===loadVersion)loading.value=false}
}
async function openChart(row:ChartSummary){
  if(!await discard())return
  const v=++loadVersion;++revision;loading.value=true;result.value=null;error.value='';editorVisible.value=false
  try {const r=await getChart(row.id);const f=await chartFields(r.data.definition.query.datasetId);if(v!==loadVersion)return;Object.assign(definition,r.data.definition);ensureChartPresentation(definition);fields.value=f.data||[];currentId.value=row.id;version.value=r.data.version;editorVisible.value=true;await nextTick();baseline.value=fingerprint();await scrollToEditor()}
  finally{if(v===loadVersion)loading.value=false}
}
function setDimension(field:string){definition.query.dimensions=field?[{field,alias:'dimension'}]:[];definition.query.orderBys=[]}
function addMetric(){if(fields.value[0])definition.query.metrics.push({field:fields.value[0].name,agg:'COUNT',alias:'value_'+(definition.query.metrics.length+1)})}
function addMetricField(name:string){if(!name || metricAddDisabled(definition.type, definition.query.metrics.length))return;definition.query.metrics.push({field:name,agg:'SUM',alias:'value_'+(definition.query.metrics.length+1)})}
function removeMetric(index:number){definition.query.metrics.splice(index,1);definition.query.metrics.forEach((m,i)=>m.alias='value_'+(i+1));definition.query.orderBys=[]}
function setSort(field:string){definition.query.orderBys=field?[{field,order:'asc'}]:[]}
function rawTable(){definition.query.dimensions=[];definition.query.metrics=fields.value.slice(0,5).map((f,i)=>({field:f.name,agg:'NONE',alias:'value_'+(i+1)}));definition.query.orderBys=[]}
function ensurePivotDefaults(){
  ensureChartPresentation(definition)
  const dims=fields.value.filter(f=>!numeric(f) || f.dimFlag==='1')
  if(!definition.advanced!.pivotRowFields?.length)definition.advanced!.pivotRowFields=dims[0]?[dims[0].name]:definition.query.dimensions.slice(0,1).map(d=>d.field)
  if(!definition.advanced!.pivotColFields?.length)definition.advanced!.pivotColFields=dims[1]?[dims[1].name]:definition.query.dimensions.slice(1,2).map(d=>d.field)
  definition.query.metrics=definition.query.metrics.map(m=>({...m,agg:m.agg==='NONE'?'SUM':m.agg}))
  syncPivotQueryDimensions(definition)
}
function syncPivotFields(){if(definition.type==='pivot')syncPivotQueryDimensions(definition)}
function typeChanged(){
  const next=applyTypeDefaults(definition.type,definition.query.dimensions,definition.query.metrics)
  definition.query.dimensions=next.dimensions
  definition.query.metrics=next.metrics
  if(definition.type==='scatter' && definition.query.metrics.length<2)ensureScatterMetrics()
  if(definition.type==='pivot')ensurePivotDefaults()
  else if(definition.type==='map'){
    ensureChartPresentation(definition)
    if(!definition.advanced!.mapScope)definition.advanced!.mapScope='china-provinces'
    if(definition.query.metrics[0] && definition.query.metrics[0].agg==='NONE')definition.query.metrics[0].agg='SUM'
    if(!definition.query.dimensions.length && fields.value[0])setDimension((fields.value.find(f=>!numeric(f))||fields.value[0]).name)
  }
  else if(definition.type!=='table' && definition.type!=='gauge' && !definition.query.dimensions.length && fields.value[0])setDimension((fields.value.find(f=>!numeric(f))||fields.value[0]).name)
}
function ensureScatterMetrics(){
  while(definition.query.metrics.length<2 && fields.value.length){
    const used=new Set(definition.query.metrics.map(m=>m.field))
    const pick=fields.value.find(numeric) || fields.value.find(f=>!used.has(f.name)) || fields.value[0]
    definition.query.metrics.push({field:pick.name,agg:'SUM',alias:'value_'+(definition.query.metrics.length+1)})
  }
}
async function preview(){
  if(!definition.query.datasetId){ElMessage.warning('请选择数据集');return}
  const shape=validateChartQueryShape(definition.type,definition.query.dimensions,definition.query.metrics) || validatePivotConfig(definition) || validateMapConfig(definition)
  if(shape){ElMessage.warning(shape);return}
  const snapshot=fingerprint(),r=revision;previewing.value=true;result.value=null;error.value=''
  try {const response=currentId.value && !dirty.value?await savedChartData(currentId.value):await previewChart(JSON.parse(snapshot).query);if(r===revision && snapshot===fingerprint())result.value=response.data}
  catch{if(r===revision)error.value='预览失败，请检查字段、筛选条件及当前数据权限'}finally{previewing.value=false}
}
async function exportSaved(format:ExportFormat){
  if(!currentId.value || dirty.value){ElMessage.warning('请先保存图表再导出');return}
  saving.value=true
  try{await submitExport({sourceType:'chart',sourceId:currentId.value,format,name:definition.name.trim()||undefined});ElMessage.success('已提交导出任务，请到导出中心下载')}
  finally{saving.value=false}
}
async function save(){
  if(!definition.name.trim()){ElMessage.warning('请填写图表名称');return}
  const snapshot=JSON.parse(fingerprint()) as ChartDefinition;snapshot.name=snapshot.name.trim();saving.value=true;error.value=''
  try {const r=await saveChart(snapshot,currentId.value,version.value);currentId.value=r.data.id;version.value=r.data.version;Object.assign(definition,snapshot);await nextTick();baseline.value=fingerprint();await refresh();ElMessage.success('图表已保存')}
  catch{error.value='保存未完成。若提示版本冲突，请重新打开图表核对后再保存'}finally{saving.value=false}
}
async function removeChart(row:ChartSummary){
  try{await ElMessageBox.confirm(`确定删除图表「${row.name}」？`,'删除图表',{type:'warning'})}catch{return}
  if(currentId.value===row.id && !await discard())return
  saving.value=true
  try{await deleteChart(row.id);if(currentId.value===row.id){editorVisible.value=false;currentId.value=undefined;result.value=null}await refresh();ElMessage.success('图表已删除')}finally{saving.value=false}
}
const grantActions:Record<string,string>={view:'查看',query:'取数',edit:'编辑',remove:'删除',grant:'授权管理'}
const grantDialog=reactive({visible:false,id:0,name:'',principalId:undefined as number|undefined,action:'view'})
const principals=ref<GrantPrincipal[]>([]),grants=ref<ResourceGrant[]>([]),grantSaving=ref(false)
let principalVersion=0
async function searchPrincipals(query=''){const v=++principalVersion,r=await searchChartPrincipals(query);if(v===principalVersion)principals.value=r.data||[]}
async function openGrants(row:ChartSummary){Object.assign(grantDialog,{visible:true,id:row.id,name:row.name,principalId:undefined,action:'view'});grants.value=[];await Promise.all([searchPrincipals(),reloadGrants()])}
async function reloadGrants(){const id=grantDialog.id,r=await listChartGrants(id);if(id===grantDialog.id)grants.value=r.data||[]}
async function addGrant(){if(!grantDialog.principalId)return;grantSaving.value=true;try{await grantChart(grantDialog.id,grantDialog.principalId,grantDialog.action);await reloadGrants();ElMessage.success('已授权')}finally{grantSaving.value=false}}
async function removeGrant(row:ResourceGrant){grantSaving.value=true;try{await revokeChartGrant(grantDialog.id,row.principalId,row.action);await reloadGrants();ElMessage.success('已撤销')}finally{grantSaving.value=false}}
onMounted(async()=>{await Promise.all([refresh(),availableDatasets()]);const id=Number(route.query.datasetId);if(id && checkPermi(['bi:chart:add']))await newChart(id);const open=Number(route.query.open);if(open)await openChart({id:open,name:'',type:'bar',datasetId:0,version:0,resourceActions:['edit','query']})})
let activatedOnce=false
onActivated(()=>{if(!activatedOnce){activatedOnce=true;return}void refresh()})
</script>
<style scoped lang="scss" src="../components/BiLibrary.scss"></style>
<style scoped>
.chart-type{display:inline-flex;align-items:center;padding:3px 9px;background:#edf2ff;color:#3563e9;font-size:12px;line-height:20px;border-radius:4px}.meta-divider{margin:0 8px;color:#c5cbd5}.chart-cover{position:relative}.chart-cover-action{position:absolute;inset:0;background:#1023444d;display:flex;align-items:center;justify-content:center;opacity:0;transition:opacity .16s;pointer-events:none}.chart-cover-action>.el-button{pointer-events:auto;border:0;color:#254dcc}.chart-cover:hover .chart-cover-action,.chart-cover:focus-within .chart-cover-action{opacity:1}.editor-card{margin-top:30px;scroll-margin-top:20px;border-radius:10px}.editor-card :deep(.el-card__body){padding:0}.board-card-meta{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
@media(hover:none){.chart-cover-action{opacity:1;inset:auto auto 8px 8px;background:transparent}.chart-cover-action>.el-button{height:28px;font-size:12px}}
@media(prefers-reduced-motion:reduce){.chart-cover-action{transition:none}}
</style>
