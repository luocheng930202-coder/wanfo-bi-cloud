<template>
  <div class="board-library chart-library" :class="{ 'chart-workspace-page': standalone }">
    <template v-if="!standalone">
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
          <div class="chart-cover"><BiResourceThumbnail kind="chart" :id="row.id" :name="row.name" :version="row.version" :actions="row.resourceActions" :refresh-key="thumbnailRefresh"/><div class="chart-cover-action"><el-button :icon="View" tag="a" :href="workspaceHref(row.id)" @click.prevent="openWindow(row.id)">打开图表</el-button></div></div>
          <div class="board-card-details"><div class="board-card-heading"><h2 :title="row.name">{{ row.name }}</h2><ChartActions :chart="row" :disabled="busy" @action="command=>handleChartAction(command,row)"/></div><span class="chart-type">{{ chartTypeLabel(row.type) }}</span><p class="board-card-meta">版本 v{{ row.version }}<span class="meta-divider">·</span>{{ datasetName(row.datasetId) }}</p></div>
        </article>
      </div>
      <el-table v-else-if="displayMode==='list' && pageCharts.length" :data="pageCharts" class="board-table" row-key="id">
        <el-table-column label="图表名称" min-width="230"><template #default="{row}"><div class="board-table-name"><el-icon><Histogram/></el-icon><span :title="row.name">{{ row.name }}</span></div></template></el-table-column>
        <el-table-column label="图表类型" width="150"><template #default="{row}"><span class="chart-type">{{ chartTypeLabel(row.type) }}</span></template></el-table-column>
        <el-table-column label="数据集" min-width="160"><template #default="{row}">{{ datasetName(row.datasetId) }}</template></el-table-column>
        <el-table-column label="版本" width="90"><template #default="{row}">v{{ row.version }}</template></el-table-column>
        <el-table-column label="操作" width="130" align="right"><template #default="{row}"><div class="board-table-actions"><el-button link type="primary" tag="a" :href="workspaceHref(row.id)" @click.prevent="openWindow(row.id)">打开</el-button><ChartActions :chart="row" :disabled="busy" @action="command=>handleChartAction(command,row)"/></div></template></el-table-column>
      </el-table>
      <el-empty v-else-if="!listLoading && !listError" :description="search.trim() || typeFilter!=='all' ? '没有找到匹配的图表' : '暂无有权查看的图表'" :image-size="100"><el-button v-if="search.trim() || typeFilter!=='all'" @click="clearFilters">清除筛选</el-button><el-button v-else v-hasPermi="['bi:chart:add']" type="primary" @click="newChart()">创建第一个图表</el-button></el-empty>
    </section>
    <footer v-if="filteredCharts.length" class="library-pagination"><span role="status">共 {{ filteredCharts.length }} 个图表</span><el-pagination v-model:current-page="page" :page-size="pageSize" :total="filteredCharts.length" layout="prev, pager, next" background/></footer>
    </template>
    <el-alert v-if="standalone && !editorVisible && error" :title="error" type="error" :closable="false" show-icon><template #default><el-button @click="initialize">重新加载</el-button><el-button @click="closeEditor">返回图表管理</el-button></template></el-alert>
    <el-skeleton v-else-if="standalone && !editorVisible" :rows="10" animated/>
    <el-card v-if="editorVisible" class="editor-card">
      <template #header><div class="toolbar"><span>{{ currentId ? '图表 · '+definition.name : '新建图表' }} <el-tag v-if="dirty" size="small" type="warning">未保存</el-tag><el-tag v-if="!editable" size="small" type="info">只读</el-tag></span><div class="toolbar"><el-button :icon="Close" :disabled="busy" @click="closeEditor">返回图表管理</el-button><el-button v-if="readOnly && canEditResource" :disabled="busy" @click="openWindow(currentId!, 'edit')">编辑图表</el-button><el-button :loading="previewing" :disabled="busy || !canPreview" @click="preview">更新数据</el-button><el-dropdown v-if="canGrantResource || canExportResource" trigger="click" :disabled="busy" @command="handleWorkspaceAction">
          <el-button :disabled="busy" :icon="MoreFilled" aria-label="更多图表操作">更多操作<el-icon class="el-icon--right"><ArrowDown/></el-icon></el-button>
          <template #dropdown><el-dropdown-menu>
            <el-dropdown-item v-if="canGrantResource" command="grant" :icon="Lock">图表授权</el-dropdown-item>
            <el-dropdown-item v-if="canExportResource" command="csv" :divided="canGrantResource" :disabled="dirty || !canPreview">导出 CSV</el-dropdown-item>
            <el-dropdown-item v-if="canExportResource" command="xlsx" :disabled="dirty || !canPreview">导出 Excel</el-dropdown-item>
            <el-dropdown-item v-if="canExportResource" command="pdf" :disabled="dirty || !canPreview">导出 PDF</el-dropdown-item>
          </el-dropdown-menu></template>
        </el-dropdown><el-button v-if="editable" type="primary" :loading="saving" :disabled="busy || !dirty" @click="save">保存图表</el-button></div></div></template>
      <nav v-if="definition.type==='map' && (chartMapPath.length || result?.mapContext)" aria-label="地图下钻路径" class="query-status"><el-button link :disabled="busy || !chartMapPath.length" @click="mapBack(0)">{{ result?.mapContext?.rootName || mapRootName || '初始地图' }}</el-button><template v-for="(segment,i) in chartMapPath" :key="i"><span>›</span><el-button link :disabled="busy" @click="mapBack(i+1)">{{ result?.mapContext?.breadcrumbs[i]?.name || mapPathNames[i] || segment.value }}</el-button></template></nav>
      <div class="query-status" role="status"><span>{{ lastQueryAt ? '数据更新于 '+lastQueryAt : '尚未查询' }}</span><span v-if="result">{{ result.rowCount }} 行 · {{ result.costMs }} ms</span><el-switch v-if="editable" v-model="autoQuery" active-text="自动查询"/></div>
      <section v-if="readOnly" v-loading="previewing" class="chart-view-canvas"><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-empty v-if="!canPreview" description="当前账号没有此图表的取数权限"/><ChartPreview v-else :loading="previewing || loading" :definition="definition" :result="result" :labels="labels" :drillable="definition.type==='map'" :can-drill-down="result?.mapContext?.canDrillDown" @drill="mapDrill"/></section>
      <ChartEditorWorkspace v-else :definition="definition" :fields="fields" :datasets="datasets" :labels="labels" :result="result" :error="error" :loading="loading" :disabled="!editable" :busy="busy" :previewing="previewing" :can-preview="canPreview" :field-label="fieldLabel" :map-drillable="definition.type==='map'" :map-can-drill-down="result?.mapContext?.canDrillDown" @map-drill="mapDrill" @type-changed="typeChanged" @dataset-changed="datasetChanged" @set-dimension="setDimension" @add-metric="addMetric" @pick-metric="addMetricField" @remove-metric="removeMetric" @set-sort="setSort" @raw-table="rawTable" @ensure-scatter="ensureScatterMetrics" @pivot-fields="syncPivotFields" @preview="preview"/>
    </el-card>
    <el-dialog v-model="grantDialog.visible" :title="'图表授权 · '+grantDialog.name" width="min(700px, calc(100vw - 24px))">
      <el-alert title="图表授权不自动授予数据集权限；使用者还需对应数据集的查看、查询及若依功能权限。" :closable="false" class="mb8"/>
      <div class="toolbar mb8"><el-select v-model="grantDialog.principalId" filterable remote :remote-method="searchPrincipals" placeholder="搜索用户、角色或部门"><el-option v-for="p in principals" :key="p.id" :value="p.id" :label="p.type+' · '+p.name"/></el-select><el-select v-model="grantDialog.action" style="width:130px"><el-option v-for="(label,key) in grantActions" :key="key" :value="key" :label="label"/></el-select><el-button :loading="grantSaving" :disabled="!grantDialog.principalId" @click="addGrant">授权</el-button></div>
      <el-table :data="grants" border><el-table-column prop="displayName" label="对象"/><el-table-column label="权限"><template #default="{row}">{{ grantActions[row.action] }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="danger" :disabled="grantSaving" @click="removeGrant(row)">撤销</el-button></template></el-table-column></el-table>
    </el-dialog>
  </div>
</template>
<script setup lang="ts" name="BiChart">
import { ref, reactive, computed, watch, onMounted, onActivated, nextTick, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import { listDatasets, type DatasetSummary } from '@/api/bi/dataset'
import { listCharts, getChart, saveChart, deleteChart, previewChart, savedChartData,previewMapChart, chartFields, searchChartPrincipals, listChartGrants, grantChart, revokeChartGrant, type ChartDefinition, type ChartSummary, type Field, type ChartResult } from '@/api/bi/chart'
import { submitExport, type ExportFormat } from '@/api/bi/export'
import type {DrillPathSegment} from '@/api/bi/dashboard'
import type { GrantPrincipal, ResourceGrant } from '@/api/bi/datasource'
import ChartEditorWorkspace from './ChartEditorWorkspace.vue'
import ChartActions from './ChartActions.vue'
import ChartPreview from './ChartPreview.vue'
import {registeredGeoFeatures} from '@/utils/biMapGeo'
import {matchGeoFeature} from '@/utils/biMapRegions'
import { metricResultKey } from '@/utils/biEchartsPreview'
import { BI_SAVED_EVENT } from '@/utils/biThumbnail'
import { chartWorkspacePath,readLibraryPosition,saveLibraryPosition } from '@/utils/biWorkspaceNavigation'
import useUserStore from '@/store/modules/user'
import BiResourceThumbnail from '../components/BiResourceThumbnail.vue'
import { Search, Refresh, Grid, List, Plus, Histogram, View, Close, MoreFilled, ArrowDown, Lock } from '@element-plus/icons-vue'
import { CHART_TYPE_LABELS, applyTypeDefaults, isChartType, metricAddDisabled, validateChartQueryShape } from '@/utils/biChartTypes'
import { defaultChartAdvanced, defaultChartStyle, ensureChartPresentation,validateChartMetricPresentation,nextChartMetricAlias } from '@/utils/biChartPresentation'
import { syncPivotQueryDimensions, validatePivotConfig } from '@/utils/biPivotPreview'
import { validateMapConfig } from '@/utils/biMapPreview'
import {validateTimeGrain} from '@/utils/biTimeGrain'
const route=useRoute(), router=useRouter()
const standalone=computed(()=>route.meta.chartWorkspace===true)
const readOnly=computed(()=>standalone.value && route.params.mode==='view')
const autoQuery=ref(false),lastQueryAt=ref('')
let queryController:AbortController|undefined,autoQueryTimer:ReturnType<typeof setTimeout>|undefined
const baselineQuery=ref('')
let loadedType:ChartDefinition['type']='bar'
const chartMapPath=ref<DrillPathSegment[]>([]),mapRootName=ref(''),mapPathNames=ref<string[]>([])
const queryFingerprint=()=>JSON.stringify({...definition.query,...(definition.type==='map'?{__map:{scope:definition.advanced?.mapScope,fields:definition.advanced?.mapFields,mode:definition.advanced?.mapValueMode}}:{})})
async function mapDrill(payload:{field:string;value:string|number|null}){if(busy.value || definition.type!=='map' || !result.value?.mapContext?.canDrillDown)return;const context=result.value.mapContext;mapRootName.value=context.rootName;mapPathNames.value=[...context.breadcrumbs.map(v=>v.name),matchGeoFeature(registeredGeoFeatures(context.scope),payload.value,definition.advanced?.mapValueMode || 'name')?.name || String(payload.value)];chartMapPath.value=[...chartMapPath.value,payload];await preview()}
async function mapBack(level:number){if(busy.value)return;mapPathNames.value=mapPathNames.value.slice(0,level);chartMapPath.value=chartMapPath.value.slice(0,level);await preview()}
function workspaceHref(id?:number, mode='view', datasetId?:number){
  return router.resolve({path:chartWorkspacePath(id,mode),query:datasetId?{datasetId}:undefined}).href
}
function openWindow(id?:number,mode='view',datasetId?:number){
  void router.push({path:chartWorkspacePath(id,mode),query:datasetId?{datasetId}:undefined})
}
const types=CHART_TYPE_LABELS
const chartTypeLabel=(value:string)=>isChartType(value)?types[value]:value
const aggregations:Record<string,string>={COUNT:'计数',COUNT_DISTINCT:'去重计数',SUM:'求和',AVG:'平均值',MIN:'最小值',MAX:'最大值',NONE:'原始值'}
function empty():ChartDefinition{return {schemaVersion:1,name:'',type:'bar',query:{datasetId:0,dimensions:[],metrics:[],filters:[],orderBys:[],limit:100},style:defaultChartStyle(),advanced:defaultChartAdvanced()}}
const libraryActor=String(useUserStore().id)
const libraryPosition=readLibraryPosition('chart',libraryActor)
const search=ref(libraryPosition.search),typeFilter=ref(libraryPosition.filter),displayMode=ref<'grid'|'list'>(libraryPosition.display),page=ref(libraryPosition.page),thumbnailRefresh=ref(0),listError=ref('')
const pageSize=6
const filteredCharts=computed(()=>charts.value.filter(row=>(!search.value.trim() || row.name.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase())) && (typeFilter.value==='all' || row.type===typeFilter.value)))
const pageCharts=computed(()=>filteredCharts.value.slice((page.value-1)*pageSize,page.value*pageSize))
watch([search,typeFilter],()=>page.value=1)
function clearFilters(){search.value='';typeFilter.value='all'}
function datasetName(id:number){return datasets.value.find(row=>row.id===id)?.name || '数据集 #'+id}
function handleChartAction(command:string,row:ChartSummary){if(busy.value)return;if(command==='open')return openWindow(row.id,row.resourceActions.includes('edit') && checkPermi(['bi:chart:edit'])?'edit':'view');if(command==='grant' && row.resourceActions.includes('grant') && checkPermi(['bi:chart:grant']))return openGrants(row);if(command==='remove' && row.resourceActions.includes('remove') && checkPermi(['bi:chart:remove']))return removeChart(row)}
async function closeEditor(){await router.push('/bi-admin/chart')}
const definition=reactive<ChartDefinition>(empty())
const charts=ref<ChartSummary[]>([]),datasets=ref<DatasetSummary[]>([]),fields=ref<Field[]>([]),result=ref<ChartResult|null>(null)
watch(()=>filteredCharts.value.length,count=>page.value=Math.min(page.value,Math.max(1,Math.ceil(count/pageSize))))
const currentId=ref<number>(),version=ref<number>(),editorVisible=ref(false),loading=ref(false),saving=ref(false),previewing=ref(false),listLoading=ref(false),error=ref(''),baseline=ref('')
let revision=0,loadVersion=0
const fingerprint=()=>JSON.stringify(definition)
const dirty=computed(()=>editorVisible.value && fingerprint()!==baseline.value)
const busy=computed(()=>loading.value || saving.value || previewing.value)
const selected=computed(()=>charts.value.find(c=>c.id===currentId.value))
const canGrantResource=computed(()=>!!selected.value?.resourceActions.includes('grant') && checkPermi(['bi:chart:grant']))
const canExportResource=computed(()=>!!currentId.value && checkPermi(['bi:export:add']))
function handleWorkspaceAction(command:string){
  if(busy.value)return
  if(command==='grant'){
    if(canGrantResource.value && selected.value)void openGrants({...selected.value,name:definition.name})
  }else if(canExportResource.value && !dirty.value && canPreview.value && (command==='csv' || command==='xlsx' || command==='pdf'))void exportSaved(command)
}
const canEditResource=computed(()=>!!selected.value?.resourceActions.includes('edit') && checkPermi(['bi:chart:edit']))
const editable=computed(()=>!readOnly.value && (currentId.value?!!selected.value?.resourceActions.includes('edit') && checkPermi(['bi:chart:edit']):checkPermi(['bi:chart:add'])))
const canPreview=computed(()=>currentId.value && queryFingerprint()===baselineQuery.value ? !!selected.value?.resourceActions.includes('query') && checkPermi(['bi:chart:query']):editable.value && checkPermi(['bi:dataset:query']))
const fieldLabel=(name:string)=>{const f=fields.value.find(f=>f.name===name);const show=f?.showName?.trim();return show||name}
const labels=computed(()=>{const names:Record<string,string>={};for(const d of definition.query.dimensions)names[d.alias||d.field]=fieldLabel(d.field);for(const m of definition.query.metrics)names[metricResultKey(m)]=`${fieldLabel(m.field)} · ${aggregations[m.agg]}`;return names})
watch(queryFingerprint,()=>{
  chartMapPath.value=[];mapRootName.value='';mapPathNames.value=[];revision++;queryController?.abort();previewing.value=false;result.value=null;error.value='';lastQueryAt.value=''
  clearTimeout(autoQueryTimer)
  if(autoQuery.value && editorVisible.value && !loading.value)autoQueryTimer=setTimeout(()=>{if(canPreview.value)void preview()},500)
})
watch(autoQuery,enabled=>{clearTimeout(autoQueryTimer);if(enabled && canPreview.value && !result.value)void preview()})
function beforeUnload(event:BeforeUnloadEvent){if(dirty.value){event.preventDefault();event.returnValue=''}}
function onSaved(event:Event){if(!standalone.value && (event as CustomEvent).detail?.kind==='chart')void refresh()}
function onStorage(event:StorageEvent){if(!standalone.value && event.key==='bi:saved-resource')void refresh()}
onBeforeUnmount(()=>{++loadVersion;++revision;queryController?.abort();clearTimeout(autoQueryTimer);window.removeEventListener('beforeunload',beforeUnload);window.removeEventListener(BI_SAVED_EVENT,onSaved);window.removeEventListener('storage',onStorage)})
async function discard(){if(!dirty.value)return true;try{await ElMessageBox.confirm('当前图表有未保存的配置，是否放弃？','未保存的修改',{type:'warning'});return true}catch{return false}}
onBeforeRouteLeave(async()=>{if(!standalone.value)saveLibraryPosition('chart',libraryActor,{search:search.value,filter:typeFilter.value,display:displayMode.value,page:page.value,scroll:window.scrollY});return await discard()})
async function refresh(){listLoading.value=true;listError.value='';try{charts.value=(await listCharts()).data||[];thumbnailRefresh.value++}catch{listError.value='图表列表加载失败，请重试'}finally{listLoading.value=false}}
async function availableDatasets(){if(checkPermi(['bi:dataset:list']))datasets.value=((await listDatasets()).data||[]).filter(d=>d.type==='table' && d.resourceActions?.includes('query'))}
function numeric(field:Field){return /(int|decimal|numeric|float|double|real|number)/i.test(field.type)}
async function newChart(source?:number){
  if(!standalone.value){openWindow(undefined,'edit',source);return}
  if(!checkPermi(['bi:chart:add'])){error.value='没有创建图表的权限';return}
  if(!await discard())return
  ++loadVersion;++revision;currentId.value=undefined;version.value=undefined;fields.value=[];result.value=null;Object.assign(definition,empty());loadedType='bar';editorVisible.value=true
  await nextTick();baseline.value=fingerprint()
  if(source && datasets.value.some(d=>d.id===source)){definition.query.datasetId=source;await datasetChanged(source)}
  baselineQuery.value=queryFingerprint()
}
async function datasetChanged(id:number){
  const v=++loadVersion;++revision;result.value=null;error.value='';fields.value=[]
  Object.assign(definition.query,{datasetId:id,pivot:undefined,dimensions:[],metrics:[],filters:[],filterTree:undefined,orderBys:[]});loading.value=true
  try {const r=await chartFields(id);if(v!==loadVersion)return;fields.value=r.data||[];const dim=fields.value.find(f=>!numeric(f))||fields.value[0],metric=fields.value.find(numeric)||fields.value[0];if(dim)definition.query.dimensions=[{field:dim.name,alias:'dimension'}];if(metric)definition.query.metrics=[{field:metric.name,agg:'COUNT',alias:'value_1'}]}
  catch{if(v===loadVersion)error.value='字段加载失败，请检查数据集和列权限'}finally{if(v===loadVersion)loading.value=false}
}
async function openChart(row:ChartSummary){
  if(!standalone.value){openWindow(row.id);return}
  const v=++loadVersion;++revision;queryController?.abort();loading.value=true;chartMapPath.value=[];mapRootName.value='';mapPathNames.value=[];result.value=null;error.value='';editorVisible.value=false
  try {
    const r=await getChart(row.id);if(v!==loadVersion)return
    Object.assign(definition,r.data.definition);loadedType=definition.type;ensureChartPresentation(definition);if(definition.type==='pivot')syncPivotQueryDimensions(definition)
    currentId.value=row.id;version.value=r.data.version
    if(!readOnly.value && !canEditResource.value)throw new Error('edit-denied')
    const f=await chartFields(r.data.definition.query.datasetId);if(v!==loadVersion)return
    fields.value=f.data||[];editorVisible.value=true;await nextTick();baseline.value=fingerprint();baselineQuery.value=queryFingerprint()
  }catch{if(v===loadVersion)error.value='无法打开图表，请检查图表是否存在，以及查看、编辑和数据集字段权限'}
  finally{if(v===loadVersion)loading.value=false}
  if(v===loadVersion && editorVisible.value && canPreview.value)await preview()
}

function setDimension(field:string){if(definition.type==='map' && definition.advanced?.mapFields){const old=definition.query.dimensions[0]?.field;for(const level of ['province','city','county'] as const)if(definition.advanced.mapFields[level]===old)definition.advanced.mapFields[level]=field || undefined}definition.query.dimensions=field?[{field,alias:'dimension'}]:[];definition.query.orderBys=[]}
function addMetric(){if(fields.value[0])definition.query.metrics.push({field:fields.value[0].name,agg:'COUNT',alias:nextChartMetricAlias(definition.query.metrics)})}
function addMetricField(name:string){if(!name || metricAddDisabled(definition.type, definition.query.metrics.length))return;definition.query.metrics.push({field:name,agg:'SUM',alias:nextChartMetricAlias(definition.query.metrics)})}
function removeMetric(index:number){definition.query.metrics.splice(index,1);definition.query.orderBys=[]}
function setSort(field:string){definition.query.orderBys=field?[{field,order:'asc'}]:[]}
function rawTable(){delete definition.query.pivot;definition.query.dimensions=[];definition.query.metrics=fields.value.slice(0,5).map((f,i)=>({field:f.name,agg:'NONE',alias:'value_'+(i+1)}));definition.query.orderBys=[]}
function ensurePivotDefaults(){
  ensureChartPresentation(definition)
  const dims=fields.value.filter(f=>!numeric(f) || f.dimFlag==='1')
  if(!definition.advanced!.pivotRowFields?.length)definition.advanced!.pivotRowFields=dims[0]?[dims[0].name]:definition.query.dimensions.slice(0,1).map(d=>d.field)
  if(!definition.advanced!.pivotColFields?.length)definition.advanced!.pivotColFields=dims[1]?[dims[1].name]:definition.query.dimensions.slice(1,2).map(d=>d.field)
  definition.query.metrics=definition.query.metrics.map(m=>({...m,agg:m.agg==='NONE'?'SUM':m.agg}))
  syncPivotQueryDimensions(definition)
}
function syncPivotFields(){if(definition.type==='pivot')syncPivotQueryDimensions(definition)}
async function typeChanged(){
  const previousType=loadedType
  if(definition.type!=='pivot')delete definition.query.pivot
  const next=applyTypeDefaults(definition.type,definition.query.dimensions,definition.query.metrics)
  if(next.dimensions.length<definition.query.dimensions.length || next.metrics.length<definition.query.metrics.length){
    try{await ElMessageBox.confirm('新图表类型不支持当前全部字段，切换将移除不兼容的维度或多余指标。是否继续？','确认字段调整',{type:'warning'})}
    catch{definition.type=previousType;return}
  }
  loadedType=definition.type
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
    definition.query.metrics.push({field:pick.name,agg:'SUM',alias:nextChartMetricAlias(definition.query.metrics)})
  }
}
async function preview(){
  if(!definition.query.datasetId){ElMessage.warning('请选择数据集');return}
  const shape=validateChartQueryShape(definition.type,definition.query.dimensions,definition.query.metrics) || validatePivotConfig(definition) || validateMapConfig(definition) || validateTimeGrain(definition,fields.value)
  if(shape){ElMessage.warning(shape);return}
  if(!canPreview.value)return
  queryController?.abort();const controller=new AbortController();queryController=controller
  const snapshot=queryFingerprint(),r=revision;previewing.value=true;result.value=null;error.value=''
  try {const response=currentId.value && snapshot===baselineQuery.value?await savedChartData(currentId.value,controller.signal,definition.type==='map'?{drills:{chart:chartMapPath.value}}:undefined):definition.type==='map'?await previewMapChart(JSON.parse(fingerprint()),{drills:{chart:chartMapPath.value}},controller.signal):await previewChart(JSON.parse(snapshot),controller.signal);if(currentId.value && snapshot===baselineQuery.value && !controller.signal.aborted){const latest=await getChart(currentId.value);if(latest.data.version!==version.value)throw new Error('version-changed')}if(!controller.signal.aborted && r===revision && snapshot===queryFingerprint()){result.value=response.data;if(response.data.mapContext){mapRootName.value=response.data.mapContext.rootName;mapPathNames.value=response.data.mapContext.breadcrumbs.map(v=>v.name)}lastQueryAt.value=new Date().toLocaleTimeString()}}
  catch{if(!controller.signal.aborted && r===revision)error.value='查询失败，请检查字段、筛选条件及数据权限，或点击更新数据重试'}finally{if(queryController===controller)previewing.value=false}
}
async function exportSaved(format:ExportFormat){
  if(!currentId.value || dirty.value){ElMessage.warning('请先保存图表再导出');return}
  saving.value=true
  try{await submitExport({sourceType:'chart',sourceId:currentId.value,format,name:definition.name.trim()||undefined});ElMessage.success('已提交导出任务，请到导出中心下载')}
  finally{saving.value=false}
}
async function save(){
  const presentationError=validateChartMetricPresentation(definition) || validateTimeGrain(definition,fields.value);if(presentationError){ElMessage.warning(presentationError);return}
  if(!definition.name.trim()){ElMessage.warning('请填写图表名称');return}
  const snapshot=JSON.parse(fingerprint()) as ChartDefinition;snapshot.name=snapshot.name.trim();saving.value=true;error.value=''
  try {const r=await saveChart(snapshot,currentId.value,version.value);currentId.value=r.data.id;version.value=r.data.version;Object.assign(definition,snapshot);await nextTick();baseline.value=fingerprint();baselineQuery.value=queryFingerprint();await refresh();if(standalone.value && route.path==='/bi-admin/chart-workspace/new')await router.replace(chartWorkspacePath(currentId.value,'edit'));ElMessage.success('图表已保存')}
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
async function initialize(){
  error.value=''
  try{
    await refresh()
    if(listError.value){error.value=listError.value;return}
    if(standalone.value){
      await availableDatasets().catch(()=>{})
      const id=Number(route.params.id)
      if(route.path==='/bi-admin/chart-workspace/new'){await newChart(Number(route.query.datasetId)||undefined);return}
      const row=charts.value.find(c=>c.id===id)
      if(!row){error.value='图表不存在或没有查看权限';return}
      await openChart(row)
    }else{
      await availableDatasets().catch(()=>{})
      const source=Number(route.query.datasetId),open=Number(route.query.open)
      if(source && checkPermi(['bi:chart:add']))await router.replace({path:chartWorkspacePath(),query:{datasetId:source}})
      else if(open)await router.replace(chartWorkspacePath(open))
    }
    if(!standalone.value){await nextTick();requestAnimationFrame(()=>window.scrollTo({top:libraryPosition.scroll}))}
  }catch{error.value='图表工作区加载失败，请重试'}
}
onMounted(()=>{window.addEventListener('beforeunload',beforeUnload);window.addEventListener(BI_SAVED_EVENT,onSaved);window.addEventListener('storage',onStorage);void initialize()})

watch(()=>[route.params.id,route.params.mode],()=>{if(standalone.value)void initialize()})
let activatedOnce=false
onActivated(()=>{if(!activatedOnce){activatedOnce=true;return}if(!standalone.value)void refresh()})
</script>
<style scoped lang="scss" src="../components/BiLibrary.scss"></style>
<style scoped>
.chart-workspace-page{padding:24px 32px;background:var(--bi-background,#f6f8fc);min-height:var(--bi-content-height,100vh)}.chart-workspace-page .editor-card{margin-top:0}.query-status{display:flex;align-items:center;gap:20px;padding:12px 20px;color:var(--bi-muted,#667085);border-bottom:1px solid var(--bi-border,#edf0f5);font-size:12px}.chart-view-canvas{padding:24px;background:var(--bi-surface,#fff);min-height:70vh}.chart-workspace-page :deep(.toolbar){flex-wrap:wrap;gap:8px}.chart-type{display:inline-flex;align-items:center;padding:3px 9px;background:#edf2ff;color:#3563e9;font-size:12px;line-height:20px;border-radius:4px}.meta-divider{margin:0 8px;color:#c5cbd5}.chart-cover{position:relative}.chart-cover-action{position:absolute;inset:0;background:#1023444d;display:flex;align-items:center;justify-content:center;opacity:0;transition:opacity .16s;pointer-events:none}.chart-cover-action>.el-button{pointer-events:auto;border:0;color:#254dcc}.chart-cover:hover .chart-cover-action,.chart-cover:focus-within .chart-cover-action{opacity:1}.editor-card{margin-top:30px;scroll-margin-top:20px;border-radius:10px}.editor-card :deep(.el-card__body){padding:0}.board-card-meta{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
@media(hover:none){.chart-cover-action{opacity:1;inset:auto auto 8px 8px;background:transparent}.chart-cover-action>.el-button{height:28px;font-size:12px}}
@media(prefers-reduced-motion:reduce){.chart-cover-action{transition:none}}
</style>

<style scoped>@media(max-width:700px){.chart-workspace-page{padding:16px}.chart-view-canvas{padding:16px}.query-status{padding:12px;gap:12px;flex-wrap:wrap}}</style>
