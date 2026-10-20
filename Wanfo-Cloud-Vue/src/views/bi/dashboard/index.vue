<template>
  <div class="board-library">
    <header class="library-heading">
      <h1>看板管理</h1>
      <p>管理企业内的可视化看板，支持新建、编辑、发布与权限设置。</p>
    </header>
    <section class="library-toolbar" aria-label="看板筛选与创建">
      <div class="library-filters">
        <el-input v-model="search" clearable :prefix-icon="Search" placeholder="搜索看板名称" aria-label="搜索看板名称" class="board-search" />
        <el-select v-model="statusFilter" aria-label="发布状态" class="board-status">
          <el-option label="全部状态" value="all" /><el-option label="已发布" value="published" /><el-option label="草稿" value="draft" />
        </el-select>
      </div>
      <div class="library-controls">
        <el-button :icon="Refresh" :loading="listLoading" :disabled="busy" aria-label="刷新看板列表" title="刷新列表" class="refresh-button" @click="refreshList" />
        <div class="view-switch" role="group" aria-label="展示方式">
          <button type="button" :class="{ active: displayMode === 'grid' }" :aria-pressed="displayMode === 'grid'" aria-label="卡片视图" title="卡片视图" @click="displayMode = 'grid'"><el-icon><Grid /></el-icon></button>
          <button type="button" :class="{ active: displayMode === 'list' }" :aria-pressed="displayMode === 'list'" aria-label="列表视图" title="列表视图" @click="displayMode = 'list'"><el-icon><List /></el-icon></button>
        </div>
        <el-button v-hasPermi="['bi:dashboard:add']" type="primary" :icon="Plus" :disabled="busy" @click="openTemplate('dashboard')">新建看板</el-button>
        <el-button v-hasPermi="['bi:dashboard:add']" :icon="Monitor" :disabled="busy" class="new-screen-button" @click="openTemplate('screen')">新建数据大屏</el-button>
      </div>
    </section>
    <el-alert v-if="listError" type="error" :title="listError" :closable="false" show-icon class="library-list-error">
      <template #default><el-button link type="primary" @click="refreshList">重新加载</el-button></template>
    </el-alert>
    <section v-loading="listLoading" class="library-content" aria-label="看板列表" :aria-busy="listLoading">
      <div v-if="displayMode === 'grid' && pageBoards.length" class="board-grid">
        <article v-for="board in pageBoards" :key="board.id" class="board-card">
          <DashboardLibraryCover :board="board" :disabled="busy" :refresh-key="thumbnailRefresh" @action="command => handleBoardAction(command, board)" />
          <div class="board-card-details">
            <div class="board-card-heading">
              <h2 :title="board.name">{{ board.name }}</h2>
              <DashboardActions :board="board" :disabled="busy" @action="command => handleBoardAction(command, board)" />
            </div>
            <span :class="['board-state', { published: board.published }]">{{ board.published ? '已发布' : '草稿' }}</span>
            <p class="board-card-meta">草稿版本 v{{ board.version }}</p>
          </div>
        </article>
      </div>
      <el-table v-else-if="displayMode === 'list' && pageBoards.length" :data="pageBoards" class="board-table" row-key="id">
        <el-table-column label="看板名称" min-width="240"><template #default="{ row }"><div class="board-table-name"><el-icon><DataAnalysis /></el-icon><span :title="row.name">{{ row.name }}</span></div></template></el-table-column>
        <el-table-column label="发布状态" width="130"><template #default="{ row }"><span :class="['board-state', { published: row.published }]">{{ row.published ? '已发布' : '草稿' }}</span></template></el-table-column>
        <el-table-column label="草稿版本" width="120"><template #default="{ row }">v{{ row.version }}</template></el-table-column>
        <el-table-column label="操作" width="150" align="right"><template #default="{ row }"><div class="board-table-actions">
          <el-button v-if="row.published && canBoardAction(row, 'consume', 'bi:dashboard:consume')" link type="primary" :disabled="busy" @click="handleBoardAction('view', row)">查看</el-button>
          <el-button v-else-if="canBoardAction(row, 'edit', 'bi:dashboard:edit')" link type="primary" :disabled="busy" @click="handleBoardAction('edit', row)">编辑</el-button>
          <DashboardActions :board="row" :disabled="busy" @action="command => handleBoardAction(command, row)" />
        </div></template></el-table-column>
      </el-table>
      <el-empty v-else-if="!listLoading && !listError" :description="search.trim() || statusFilter !== 'all' ? '没有找到匹配的看板' : '暂无有权查看的看板'" :image-size="100">
        <el-button v-if="search.trim() || statusFilter !== 'all'" @click="clearLibraryFilters">清除筛选</el-button>
        <el-button v-else v-hasPermi="['bi:dashboard:add']" type="primary" :icon="Plus" @click="openTemplate('dashboard')">创建第一个看板</el-button>
      </el-empty>
    </section>
    <footer v-if="filteredBoards.length" class="library-pagination">
      <span role="status">共 {{ filteredBoards.length }} 个看板</span>
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="filteredBoards.length" layout="prev, pager, next" background />
    </footer>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb8"/>
    <el-card v-if="mode==='view' && view" ref="viewCard" class="board-view">
      <template #header><div class="toolbar"><span>{{ view.name }} <el-tag size="small">发布版本 {{ view.revision }}</el-tag> <small v-if="view.scene" role="status">{{ refreshStatus }}</small></span><div class="toolbar"><el-button :icon="Close" @click="closeView">关闭预览</el-button><el-button v-hasPermi="['bi:export:add']" :disabled="busy || !queryPanels.length" @click="openExport">导出数据</el-button><el-button :loading="loading" @click="openView(view.id,true)">刷新看板</el-button></div></div></template>
      <div v-if="queryPanels.length" class="toolbar mb8" role="status" aria-live="polite"><span>数据组件 {{ queryPanels.length }} · 已加载 {{ loadedCount }} · 加载中 {{ loadingCount }} · 失败 {{ failedKeys.length }}</span><el-button v-if="failedKeys.length" type="warning" :disabled="loading || loadingCount>0" @click="retryFailed">重试失败组件</el-button></div>
      <div v-if="view.controls?.length" class="filter-bar">
        <div v-for="c in view.controls" :key="c.key" class="filter-item"><label>{{ c.label }}</label><DashboardFilterInput v-model="filterInputs[c.key]" :type="c.type" :label="c.label" :options="filterOptions[c.key] || []" :loading="optionLoading[c.key]" @options="loadOptions(c.key)"/><span v-if="optionErrors[c.key]" class="filter-error">{{ optionErrors[c.key] }}</span></div>
        <div class="toolbar"><el-button type="primary" @click="applyFilters">应用筛选</el-button><el-button @click="clearFilters">清空输入</el-button><el-button @click="resetFilters">恢复默认输入</el-button><span>{{ pendingFilters?'输入已修改，点击应用后生效':'当前显示已应用条件' }}</span></div>
      </div>
      <div v-if="Object.keys(activeLinks).length" class="toolbar mb8"><el-tag v-for="(value,key) in activeLinks" :key="key" closable @close="removeActiveLink(String(key))">联动：{{ value ?? '空值' }}</el-tag><el-button link type="primary" @click="clearLinks">清除全部联动</el-button></div>
      <p v-if="view.links?.length" class="interaction-hint">点击柱形、折线点、扇区或明细可下钻或联动（下钻图不可作联动来源）。</p>
      <DashboardCanvas :scene="view.scene" :panels="visiblePanels" :appearance="view.appearance" readonly><template #default="{panel}"><DashboardCard :title="panelTitle(panel.key)" :component-type="panel.componentType" :content="panel.content" :panel-style="panelStyle(panel.key)" :appearance="view.appearance" :payload="payloads[panel.key]" :error="panelErrors[panel.key]" :loading="panelLoading[panel.key]" @refresh="refreshViewPanel(panel.key)" :linkable="view.links?.some(l=>l.sourcePanel===panel.key)" :drill-fields="panel.drillFields" :drill-path="drillPath(panel.key)" @select="v=>selectLink(panel.key,v)" @drill="p=>drillDown(panel.key,p.field,p.value)" @drill-up="lvl=>drillUp(panel.key,lvl)"/></template></DashboardCanvas>
    </el-card>
    <DashboardTemplatePicker v-if="templateOpen" v-model="templateOpen" :kind="templateKind" :initial-select-id="templatePickId" @created="onTemplateCreated"/>
    <DashboardShareDialog v-model:visible="shareDialog.visible" :dashboard-id="shareDialog.id" :name="shareDialog.name" />
    <el-dialog v-model="grantDialog.visible" :title="'看板授权 · '+grantDialog.name" width="min(720px,calc(100vw - 24px))">
      <el-alert title="纯看板使用者需：看板查看/消费权限，以及所用数据集的“看板消费”资源权限。无需数据集查看/查询资格，仍执行使用者自己的行列规则。" :closable="false" class="mb8"/>
      <el-button v-hasPermi="['bi:dataset:grant']" link type="primary" @click="goDatasets">前往数据集管理配置消费授权</el-button>
      <div class="toolbar mb8"><el-select v-model="grantDialog.principalId" remote filterable :remote-method="searchPrincipals" placeholder="搜索用户、角色或部门"><el-option v-for="p in principals" :key="p.id" :value="p.id" :label="p.type+' · '+p.name"/></el-select><el-select v-model="grantDialog.action" style="width:130px"><el-option v-for="(label,key) in grantActions" :key="key" :value="key" :label="label"/></el-select><el-button :disabled="!grantDialog.principalId" :loading="grantSaving" @click="addGrant">授权</el-button></div>
      <el-table :data="grants" border><el-table-column prop="displayName" label="对象"/><el-table-column label="权限"><template #default="{row}">{{ grantActions[row.action] }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="danger" :disabled="grantSaving" @click="removeGrant(row)">撤销</el-button></template></el-table-column></el-table>
    </el-dialog>
    <el-dialog v-model="exportDialog.visible" title="导出看板数据" width="420px">
      <el-form label-position="top">
        <el-form-item label="范围"><el-select v-model="exportDialog.panelKey" clearable placeholder="全部数据组件（Excel）" style="width:100%"><el-option v-for="p in queryPanels" :key="p.key" :value="p.key" :label="p.title"/></el-select></el-form-item>
        <el-form-item label="格式"><el-radio-group v-model="exportDialog.format"><el-radio value="xlsx">Excel</el-radio><el-radio value="csv" :disabled="!exportDialog.panelKey && queryPanels.length>1">CSV</el-radio><el-radio value="pdf" :disabled="!exportDialog.panelKey && queryPanels.length>1">PDF</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="exportDialog.visible=false">取消</el-button><el-button type="primary" :loading="exportDialog.saving" @click="submitBoardExport">提交任务</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts" name="BiDashboardAdmin">
import { ref, reactive, computed, onMounted, onActivated, onBeforeUnmount, watch, nextTick } from 'vue'
import {runPanelQueue} from '@/utils/biPanelQueue'
import {useBiRefresh} from '@/composables/useBiRefresh'
import { useRoute, useRouter } from 'vue-router'
import { checkPermi } from '@/utils/permission'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Grid, List, Plus, Monitor, DataAnalysis, Close } from '@element-plus/icons-vue'
import DashboardActions from './DashboardActions.vue'
import DashboardLibraryCover from './DashboardLibraryCover.vue'
import { listDashboards, publishDashboard, unpublishDashboard, deleteDashboard, getDashboardView, dashboardPanelData, searchDashboardPrincipals, listDashboardGrants, grantDashboard, revokeDashboardGrant, type DashboardSummary, type DashboardView, type PanelResult } from '@/api/bi/dashboard'
import type { GrantPrincipal, ResourceGrant } from '@/api/bi/datasource'
import DashboardTemplatePicker from './DashboardTemplatePicker.vue'
import DashboardShareDialog from './DashboardShareDialog.vue'
const shareDialog = reactive({ visible: false, id: 0, name: '' })
function openShare(row: DashboardSummary) { Object.assign(shareDialog, { visible: true, id: row.id, name: row.name }) }
import DashboardCanvas from './DashboardCanvas.vue'
import DashboardCard from './DashboardCard.vue'
import DashboardFilterInput from './DashboardFilterInput.vue'
import {dashboardControlOptions,type FilterValue,type DashboardInput,type DrillPathSegment} from '@/api/bi/dashboard'
import {appendDrillSegment,mergeDashboardInput,truncateDrillPath} from '@/utils/biDrill'
import { submitExport, type ExportFormat } from '@/api/bi/export'
const router=useRouter(),route=useRoute(),templateOpen=ref(false),templateKind=ref<'screen'|'dashboard'>('screen'),templatePickId=ref('')
function openTemplate(kind:'screen'|'dashboard'){templatePickId.value='';templateKind.value=kind;templateOpen.value=true}
function onTemplateCreated(){templatePickId.value='';refreshList()}
function openTemplateFromQuery(){
  const kind=route.query.openTemplate,templateId=String(route.query.templateId || '').trim()
  if((kind==='screen' || kind==='dashboard') && checkPermi(['bi:dashboard:add'])){
    templateKind.value=kind;templatePickId.value=templateId;templateOpen.value=true
    const q={...route.query};delete q.openTemplate;delete q.templateId;router.replace({path:route.path,query:q})
  }
}
function openViewFromQuery(){
  const viewId=Number(route.query.view)
  if(viewId>0 && checkPermi(['bi:dashboard:consume'])){void openView(viewId)}
  else if(viewId>0){const q={...route.query};delete q.view;router.replace({path:route.path,query:q})}
}
const boards=ref<DashboardSummary[]>([]),view=ref<DashboardView|null>(null)
const search = ref('')
const statusFilter = ref<'all' | 'published' | 'draft'>('all')
const displayMode = ref<'grid' | 'list'>('grid')
const page = ref(1)
const pageSize = 6
const listError = ref('')
const thumbnailRefresh = ref(0)
const viewCard = ref<{ $el: HTMLElement }>()
const filteredBoards = computed(() => {
  const keyword = search.value.trim().toLocaleLowerCase()
  return boards.value.filter(board => (!keyword || board.name.toLocaleLowerCase().includes(keyword)) &&
    (statusFilter.value === 'all' || board.published === (statusFilter.value === 'published')))
})
const pageBoards = computed(() => filteredBoards.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch([search, statusFilter], () => { page.value = 1 })
watch(() => filteredBoards.value.length, count => { page.value = Math.min(page.value, Math.max(1, Math.ceil(count / pageSize))) })
function clearLibraryFilters() { search.value = ''; statusFilter.value = 'all' }
function canBoardAction(board: DashboardSummary, resource: string, permission: string) {
  return board.resourceActions?.includes(resource) && checkPermi([permission])
}
function handleBoardAction(command: string, board: DashboardSummary) {
  if (busy.value) return
  if (command === 'view' && board.published && canBoardAction(board, 'consume', 'bi:dashboard:consume')) return openView(board.id)
  if (command === 'edit' && canBoardAction(board, 'edit', 'bi:dashboard:edit')) return router.push('/bi-designer/' + board.id)
  if (command === 'publish' && canBoardAction(board, 'publish', 'bi:dashboard:publish')) return publishRow(board)
  if (command === 'offline' && board.published && canBoardAction(board, 'publish', 'bi:dashboard:publish')) return takeOffline(board)
  if (command === 'share' && board.published && canBoardAction(board, 'publish', 'bi:dashboard:publish')) return openShare(board)
  if (command === 'grant' && canBoardAction(board, 'grant', 'bi:dashboard:grant')) return openGrants(board)
  if (command === 'remove' && canBoardAction(board, 'remove', 'bi:dashboard:remove')) return removeBoard(board)
}
function closeView() {
  invalidate(); loading.value = false; mode.value = null; currentId.value = undefined; view.value = null
  if (route.query.view) { const query = { ...route.query }; delete query.view; router.replace({ path: route.path, query }) }
}
const mode=ref<'view'|null>(null),currentId=ref<number>()
const payloads=reactive<Record<string,PanelResult>>(Object.create(null)),panelErrors=reactive<Record<string,string>>(Object.create(null)),panelLoading=reactive<Record<string,boolean>>(Object.create(null))
const loading=ref(false),saving=ref(false),listLoading=ref(false),error=ref('')
const busy=computed(()=>loading.value || saving.value)
const visiblePanels=computed(()=>view.value?.kind==='screen'?[...view.value.panels]:[...(view.value?.panels||[])].sort((a,b)=>a.y-b.y || a.x-b.x))
const filterInputs=reactive<Record<string,FilterValue>>(Object.create(null)),appliedFilters=reactive<Record<string,FilterValue>>(Object.create(null)),activeLinks=reactive<Record<string,string|number|null>>(Object.create(null)),activeDrills=reactive<Record<string,DrillPathSegment[]>>(Object.create(null))
const filterOptions=reactive<Record<string,(string|number)[]>>(Object.create(null)),optionLoading=reactive<Record<string,boolean>>(Object.create(null)),optionErrors=reactive<Record<string,string>>(Object.create(null))
const pendingFilters=computed(()=>JSON.stringify(filterInputs)!==JSON.stringify(appliedFilters))
function replaceMap<T>(target:Record<string,T>,next:Record<string,T>){for(const k of Object.keys(target))delete target[k];Object.assign(target,JSON.parse(JSON.stringify(next)))}
function resetFilters(){replaceMap(filterInputs,Object.fromEntries((view.value?.controls || []).map(c=>[c.key,c.defaultValue??null])))}
function clearFilters(){replaceMap(filterInputs,Object.fromEntries((view.value?.controls || []).map(c=>[c.key,null])))}
function runtimeInput():DashboardInput{return mergeDashboardInput(JSON.parse(JSON.stringify(appliedFilters)),JSON.parse(JSON.stringify(activeLinks)),JSON.parse(JSON.stringify(activeDrills)))}
function drillPath(key:string){return activeDrills[key] || []}
function drillDown(panel:string,field:string,value:string|number|null){if(loading.value)return;activeDrills[panel]=appendDrillSegment(drillPath(panel),field,value);refreshViewPanel(panel)}
function drillUp(panel:string,level:number){if(loading.value)return;activeDrills[panel]=truncateDrillPath(drillPath(panel),level);refreshViewPanel(panel)}
function applyFilters(){if(loading.value)return;replaceMap(appliedFilters,JSON.parse(JSON.stringify(filterInputs)));replaceMap(activeLinks,{});replaceMap(activeDrills,{});refreshAppliedPanels()}
function selectLink(panel:string,value:string|number|null){if(loading.value)return;for(const l of view.value?.links || [])if(l.sourcePanel===panel)activeLinks[l.key]=value;refreshAppliedPanels()}
function removeActiveLink(key:string){if(loading.value)return;delete activeLinks[key];refreshAppliedPanels()}
function clearLinks(){if(loading.value)return;replaceMap(activeLinks,{});refreshAppliedPanels()}
const optionSequences:Record<string,number>=Object.create(null)
async function loadOptions(key:string){const current=view.value;if(!current)return;const e=epoch,v=(optionSequences[key]||0)+1;optionSequences[key]=v;optionLoading[key]=true;optionErrors[key]='';inFlight.value++;try{const r=await dashboardControlOptions(current.id,key,current.revision,runtimeInput());if(e!==epoch || v!==optionSequences[key])return;filterOptions[key]=r.data.values;optionErrors[key]=r.data.truncated?'候选值超过100项，仅显示前100项':''}catch{if(e===epoch && v===optionSequences[key]){filterOptions[key]=[];optionErrors[key]='候选值加载失败，请检查权限或刷新发布版本'}}finally{inFlight.value--;if(e===epoch && v===optionSequences[key])optionLoading[key]=false}}
async function refreshAppliedPanels(){if(mode.value!=='view' || !view.value)return;invalidate();const e=epoch;loading.value=true;const keys=view.value.panels.map(p=>p.key);try{await runPanelQueue(keys,key=>refreshViewPanel(key,true),()=>e===epoch && (!refreshSeconds.value || available.value))}finally{if(e===epoch)loading.value=false}}
const queryPanels=computed(()=>view.value?.panels.filter(p=>!p.componentType || ['chart','kpi'].includes(p.componentType)) || [])
const failedKeys=computed(()=>queryPanels.value.filter(p=>!!panelErrors[p.key]).map(p=>p.key))
const loadedCount=computed(()=>queryPanels.value.filter(p=>!!payloads[p.key]).length)
const loadingCount=computed(()=>queryPanels.value.filter(p=>panelLoading[p.key]).length)
async function retryFailed(){
  if(loading.value || loadingCount.value || !view.value)return
  const e=epoch,keys=[...failedKeys.value];loading.value=true
  try{await runPanelQueue(keys,key=>refreshViewPanel(key,true),()=>e===epoch)}finally{if(e===epoch)loading.value=false}
}
const inFlight=ref(0)
const refreshSeconds=computed(()=>currentId.value===view.value?.id?(view.value?.scene?.refreshSeconds || 0):0)
const {available,refreshStatus}=useBiRefresh(refreshSeconds,async()=>{if(currentId.value)await openView(currentId.value,true)},()=>loading.value || saving.value || inFlight.value>0)
let epoch=0
const panelRequests:Record<string,number>=Object.create(null)
function clearPanels(){for(const map of [payloads,panelErrors,panelLoading])for(const key of Object.keys(map))delete map[key]}
function invalidate(preserve=false){++epoch;if(!preserve)clearPanels();for(const key of Object.keys(optionLoading))optionLoading[key]=false;error.value=''}
onBeforeUnmount(()=>{++epoch})
async function refreshList(){
  thumbnailRefresh.value++;if(listLoading.value)return;listLoading.value=true;listError.value='';try{boards.value=(await listDashboards()).data||[]}catch{listError.value='看板列表加载失败，请稍后重试'}finally{listLoading.value=false}}
function panelTitle(key:string){return view.value?.panels.find(p=>p.key===key)?.title || '图表'}
function panelStyle(key:string){return view.value?.panels.find(p=>p.key===key)?.style}
async function publishRow(row:DashboardSummary){
  try{await ElMessageBox.confirm('将固定当前草稿及引用图表的配置，替换已发布版本。确认发布？','发布看板',{type:'warning'})}catch{return}
  saving.value=true
  try{const r=await publishDashboard(row.id,row.version);await refreshList();ElMessage.success('看板已发布')}
  finally{saving.value=false}
}
async function takeOffline(row:DashboardSummary){
  try{await ElMessageBox.confirm('下线后，后续看板取数请求将被拒绝。确认下线？','下线看板',{type:'warning'})}catch{return}
  saving.value=true
  try{const r=await unpublishDashboard(row.id,row.version);if(currentId.value===row.id){if(mode.value==='view'){invalidate();mode.value=null;currentId.value=undefined}}await refreshList();ElMessage.success('看板已下线')}
  finally{saving.value=false}
}
async function removeBoard(row:DashboardSummary){
  try{await ElMessageBox.confirm(`确定删除看板「${row.name}」？`,'删除看板',{type:'warning'})}catch{return}
  saving.value=true
  try{await deleteDashboard(row.id);if(currentId.value===row.id){invalidate();mode.value=null;currentId.value=undefined}await refreshList();ElMessage.success('看板已删除')}
  finally{saving.value=false}
}
async function openView(id:number,force=false){
  if(loading.value)return
  const preserve=force && view.value?.id===id
  currentId.value=id;invalidate(preserve);const e=epoch;loading.value=true;if(!preserve)mode.value=null;inFlight.value++
  try{const previous=view.value,r=await getDashboardView(id);if(e!==epoch)return;const keep=force && previous?.id===id && previous.revision===r.data.revision;view.value=r.data;currentId.value=id;mode.value='view';if(!force){await nextTick();viewCard.value?.$el.scrollIntoView({behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',block:'start'})}if(!keep){clearPanels();resetFilters();replaceMap(appliedFilters,JSON.parse(JSON.stringify(filterInputs)));replaceMap(activeLinks,{});replaceMap(activeDrills,{})}replaceMap(filterOptions,{});replaceMap(optionErrors,{});replaceMap(optionLoading,{});await runPanelQueue(r.data.panels,panel=>refreshViewPanel(panel.key,true,keep),()=>e===epoch && (!refreshSeconds.value || available.value))}
  catch{if(e===epoch){clearPanels();mode.value=null;error.value='看板加载失败，请检查是否已下线或权限已改变；自动刷新开启时将按周期重试'}}finally{inFlight.value--;if(e===epoch)loading.value=false}
}
async function refreshViewPanel(key:string,internal=false,preserve=false){
  if(!internal && (loading.value || panelLoading[key]))return
  const type=view.value?.panels.find(p=>p.key===key)?.componentType;if(type && !['chart','kpi'].includes(type))return
  const current=view.value;if(mode.value!=='view' || !current)return
  const e=epoch,v=(panelRequests[key]||0)+1;panelRequests[key]=v;panelLoading[key]=true;if(!preserve)delete payloads[key];delete panelErrors[key]
  inFlight.value++
  try{const r=await dashboardPanelData(current.id,key,current.revision,runtimeInput());if(e===epoch && panelRequests[key]===v)payloads[key]=r.data}
  catch{if(e===epoch && panelRequests[key]===v){delete payloads[key];panelErrors[key]='当前组件不可用，请检查数据集消费权限、行列规则或刷新发布版本'}}finally{inFlight.value--;if(e===epoch && panelRequests[key]===v)panelLoading[key]=false}
}
const grantActions:Record<string,string>={view:'查看列表',consume:'查看已发布数据',edit:'编辑草稿',publish:'发布/下线',remove:'删除',grant:'授权管理'}
const grantDialog=reactive({visible:false,id:0,name:'',principalId:undefined as number|undefined,action:'view'})
const principals=ref<GrantPrincipal[]>([]),grants=ref<ResourceGrant[]>([]),grantSaving=ref(false)
let principalVersion=0
async function searchPrincipals(query=''){const v=++principalVersion,r=await searchDashboardPrincipals(query);if(v===principalVersion)principals.value=r.data||[]}
async function reloadGrants(){const id=grantDialog.id,r=await listDashboardGrants(id);if(id===grantDialog.id)grants.value=r.data||[]}
async function openGrants(row:DashboardSummary){Object.assign(grantDialog,{visible:true,id:row.id,name:row.name,principalId:undefined,action:'view'});grants.value=[];await Promise.all([searchPrincipals(),reloadGrants()])}
async function addGrant(){if(!grantDialog.principalId)return;grantSaving.value=true;try{await grantDashboard(grantDialog.id,grantDialog.principalId,grantDialog.action);await reloadGrants();ElMessage.success('已授权')}finally{grantSaving.value=false}}
async function removeGrant(row:ResourceGrant){grantSaving.value=true;try{await revokeDashboardGrant(grantDialog.id,row.principalId,row.action);await reloadGrants();ElMessage.success('已撤销')}finally{grantSaving.value=false}}
function goDatasets(){grantDialog.visible=false;router.push('/bi-admin/dataset')}
const exportDialog=reactive({visible:false,panelKey:'' as string,format:'xlsx' as ExportFormat,saving:false})
function openExport(){if(!view.value || !queryPanels.value.length)return;exportDialog.visible=true;exportDialog.panelKey='';exportDialog.format='xlsx'}
async function submitBoardExport(){
  const current=view.value;if(!current)return
  if((exportDialog.format==='csv' || exportDialog.format==='pdf') && !exportDialog.panelKey && queryPanels.value.length>1){ElMessage.warning('多个数据组件请使用 Excel，或选择其中一个组件');return}
  exportDialog.saving=true
  try{await submitExport({sourceType:'dashboard',sourceId:current.id,panelKey:exportDialog.panelKey||undefined,revision:current.revision,format:exportDialog.format,name:current.name,input:runtimeInput()});exportDialog.visible=false;ElMessage.success('已提交导出任务，请到导出中心下载')}
  finally{exportDialog.saving=false}
}
onMounted(async()=>{await refreshList();openTemplateFromQuery();openViewFromQuery()})
let activatedOnce=false
onActivated(()=>{if(!activatedOnce){activatedOnce=true;return}refreshList();if(!refreshSeconds.value && view.value && mode.value==='view')openView(view.value.id,true)})
</script>
<style scoped lang="scss" src="../components/BiLibrary.scss"></style>

