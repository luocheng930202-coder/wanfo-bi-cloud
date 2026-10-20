<template>
    <el-dialog v-model="visible" :title="dashboard?'新建看板':'新建数据大屏'" width="min(1100px,calc(100vw - 24px))" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" :before-close="close">
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice"/>
    <template v-if="!selected">
      <div class="creation-choice"><div><h2>从布局开始，让数据成为主角</h2><p>选择模板并绑定自己的数据，或从空白{{ dashboard?'看板':'画布' }}开始。</p></div><div class="creation-actions"><label class="import-template">导入模板文件<input type="file" accept=".json,application/json" :aria-label="dashboard?'导入看板模板文件':'导入大屏模板文件'" :disabled="busy" @change="importFile"/></label><el-button :disabled="busy" @click="blank">空白创建</el-button></div></div>
      <div class="template-filters"><el-input v-model="search" clearable placeholder="搜索模板" :aria-label="dashboard?'搜索看板模板':'搜索大屏模板'"/><el-select v-model="category" placeholder="全部分类" aria-label="模板分类"><el-option label="全部分类" value=""/><el-option v-for="c in categories" :key="c" :label="c" :value="c"/></el-select></div>
      <div class="template-grid"><button v-for="item in filtered" :key="item.id" class="template-card" :disabled="busy" @click="select(item.id)"><DashboardTemplatePreview :template="item" :file="savedFiles[item.id] || (item.id==='imported-file'?file:undefined)"/><div class="template-info"><strong>{{ item.name }}</strong><span>{{ item.category }}</span></div><p>{{ item.description }}</p><span class="template-action">预览与使用 →</span></button></div>
      <el-empty v-if="!filtered.length" description="没有匹配的模板，试试其他分类或关键词"/>
    </template>
    <template v-else>
      <el-steps :active="wizardStep" finish-status="success" align-center class="wizard-steps"><el-step title="选择模板"/><el-step title="绑定数据"/><el-step title="确认创建"/></el-steps>
      <div class="binding-heading"><el-button :disabled="busy" @click="back">返回模板列表</el-button><span>{{ selected.name }} · {{ dashboard?'12 列栅格':(scene.width+' × '+scene.height) }} · {{ selected.slots.length }} 个数据组件</span></div>
      <template v-if="wizardStep<2">
      <DashboardTemplatePreview :template="selected" :file="selectedFile"/>
      <el-alert title="可为每个组件从已保存图表或数据集映射字段；切换数据集时会尽量按字段名保留映射。创建前会整板验证，不会自动发布。" :closable="false" class="notice"/>
      <el-form label-position="top" :disabled="busy"><el-form-item :label="dashboard?'新看板名称':'新大屏名称'"><el-input v-model="name" maxlength="200" :aria-label="dashboard?'模板创建看板名称':'模板创建大屏名称'"/></el-form-item></el-form>
      <div v-if="datasets.length" class="batch-bar"><span>批量替换数据集</span><el-select v-model="batchDatasetId" filterable clearable placeholder="选择统一数据集" :disabled="busy" aria-label="批量应用的数据集"><el-option v-for="ds in datasets" :key="ds.id" :value="ds.id" :label="ds.name+' · #'+ds.id"/></el-select><el-button :disabled="busy || !batchDatasetId" @click="applyBatchDataset">应用到全部数据槽</el-button><el-button :disabled="busy || !selected.slots.length" :loading="validatingAll" @click="validateAll">验证全部绑定</el-button></div>
      <div v-for="slot in selected.slots" :key="slot.key" class="binding-row">
        <div class="binding-label"><strong>{{ slot.label }}</strong><el-tag :type="verified[slot.key]?'success':'warning'">{{ verified[slot.key]?'已验证':'待绑定验证' }}</el-tag></div>
        <el-radio-group v-model="sources[slot.key]" :disabled="busy || !!slotLoading[slot.key]" @change="switchSource(slot)">
          <el-radio value="chart">从已保存图表</el-radio>
          <el-radio value="dataset">从数据集映射</el-radio>
        </el-radio-group>
        <el-select v-if="sources[slot.key]==='chart'" :model-value="bindings[slot.key]?.chartId" filterable clearable :aria-label="slot.label+'绑定图表'" :placeholder="'为'+slot.label+'选择图表'" :disabled="busy || !!slotLoading[slot.key]" @change="value=>bindChart(slot,value)"><el-option v-for="chart in charts" :key="chart.id" :value="chart.id" :label="chart.name+' · 数据集 #'+chart.datasetId"/></el-select>
        <el-select v-else :model-value="bindings[slot.key]?.datasetId" filterable clearable :aria-label="slot.label+'绑定数据集'" :placeholder="'为'+slot.label+'选择数据集'" :disabled="busy || !!slotLoading[slot.key]" @change="value=>bindDataset(slot,value)"><el-option v-for="ds in datasets" :key="ds.id" :value="ds.id" :label="ds.name+' · #'+ds.id"/></el-select>
        <p v-if="impacts[slot.key]?.length" class="binding-impact" role="status">{{ impacts[slot.key].join('；') }}</p>
        <p v-if="summaries[slot.key]" class="binding-summary">{{ summaries[slot.key] }}</p>
        <template v-if="bindings[slot.key] && !slotLoading[slot.key]">
          <DashboardChartEditor v-if="sources[slot.key]==='chart'" :key="slot.key+'-chart-'+bindings[slot.key].chartId" :panel="templateSlotPanel(slot,bindings[slot.key])" :kind="dashboard?'dashboard':'screen'" :layout="dashboard?'grid':'absolute'" :scene="dashboard?undefined:scene" :disabled="busy" @apply="(_key,config)=>configured(slot,config)"/>
          <DashboardTemplateDatasetEditor v-else :slot="slot" :dataset-id="bindings[slot.key].datasetId!" :config="bindings[slot.key].config" :disabled="busy" @apply="config=>configured(slot,config)"/>
          <el-button :disabled="busy" :loading="!!validating[slot.key]" @click="validateSlot(slot)">验证当前绑定</el-button>
        </template>
        <p v-if="slotErrors[slot.key]" class="binding-error" role="alert">{{ slotErrors[slot.key] }}</p>
      </div>
      <el-empty v-if="!charts.length && !datasets.length && !busy" description="暂无可用图表或数据集，请先创建并授权"/>
      </template>
      <template v-else>
        <el-alert title="请确认名称与各数据槽绑定摘要，再创建独立草稿。" type="info" :closable="false" class="notice"/>
        <el-form label-position="top" :disabled="busy"><el-form-item :label="dashboard?'新看板名称':'新大屏名称'"><el-input v-model="name" maxlength="200"/></el-form-item></el-form>
        <el-table :data="selected.slots" size="small" border class="review-table"><el-table-column prop="label" label="组件" min-width="120"/><el-table-column label="数据来源" min-width="100"><template #default="{row}">{{ sources[row.key]==='dataset'?'数据集映射':'已保存图表' }}</template></el-table-column><el-table-column label="绑定摘要" min-width="280"><template #default="{row}">{{ summaries[row.key] || '—' }}</template></el-table-column><el-table-column label="验证" width="88"><template #default="{row}"><el-tag :type="verified[row.key]?'success':'warning'" size="small">{{ verified[row.key]?'通过':'待验证' }}</el-tag></template></el-table-column></el-table>
        <el-button link type="primary" :disabled="busy" @click="wizardStep=1">返回调整绑定</el-button>
      </template>
    </template>
    <template #footer><el-button :disabled="busy" @click="visible=false">取消</el-button><el-button v-if="selected && wizardStep===1" :disabled="busy" @click="goReview">下一步：确认</el-button><el-button v-if="selected && wizardStep===2" type="primary" :disabled="(!ready && !createdId) || busy" :loading="busy" @click="create">{{ createdId?'打开已创建草稿':'验证并创建独立草稿' }}</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import {computed,reactive,ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import {listCharts,getChart,commitTemplateChartLeases,releaseTemplateChartLeases,type ChartSummary} from '@/api/bi/chart'
import {listDatasets,type DatasetSummary} from '@/api/bi/dataset'
import {chartFields} from '@/api/bi/chart'
import {listTemplates,listTemplateMarket,getMarketTemplate,type MarketTemplateEntry} from '@/api/bi/template'
import {previewDashboard,saveDashboard,type PanelChartConfig,type ScreenScene} from '@/api/bi/dashboard'
import {TEMPLATE_MAX_BYTES,parseLayoutTemplate,importedTemplate,instantiateImportedTemplate,type LayoutTemplateFile} from '@/utils/biTemplateFiles'
import {screenTemplates,dashboardTemplates,getScreenTemplate,getDashboardTemplate,templateSlotPanel,instantiateScreenTemplate,instantiateDashboardTemplate,type TemplateSlot,type TemplateBinding,type ScreenTemplate} from '@/utils/biTemplates'
import {analyzeBindingImpact,ensureBindingChart,remapPanelConfig,suggestPanelConfig,type TemplateBindingSource} from '@/utils/biTemplateBinding'
import DashboardTemplatePreview from './DashboardTemplatePreview.vue'
import DashboardChartEditor from './DashboardChartEditor.vue'
import DashboardTemplateDatasetEditor from './DashboardTemplateDatasetEditor.vue'
const visible=defineModel<boolean>({required:true})
const props=defineProps<{kind?:'screen'|'dashboard';initialSelectId?:string}>()
const emit=defineEmits<{(e:'created',id:number):void}>()
const dashboard=computed(()=>props.kind==='dashboard')
const router=useRouter(),selected=ref<ScreenTemplate>(),search=ref(''),category=ref(''),name=ref(''),busy=ref(false),error=ref(''),charts=ref<ChartSummary[]>([]),datasets=ref<DatasetSummary[]>([])
const createdId=ref<number>()
const bindings=reactive<Record<string,TemplateBinding>>({}),verified=reactive<Record<string,boolean>>({}),summaries=reactive<Record<string,string>>({}),slotErrors=reactive<Record<string,string>>({}),slotLoading=reactive<Record<string,boolean>>({}),validating=reactive<Record<string,boolean>>({})
const sources=reactive<Record<string,TemplateBindingSource>>({}),impacts=reactive<Record<string,string[]>>({})
const wizardStep=ref(1),batchDatasetId=ref<number>(),validatingAll=ref(false)
const file=ref<LayoutTemplateFile>()
const saved=ref<ScreenTemplate[]>([])
const savedFiles=reactive<Record<string,LayoutTemplateFile>>({})
const market=ref<ScreenTemplate[]>([])
const marketFiles=reactive<Record<string,LayoutTemplateFile>>({})
const catalog=computed(()=>[...saved.value,...market.value,...(file.value?[importedTemplate(file.value)]:[]),...(dashboard.value?dashboardTemplates:screenTemplates)])
const selectedFile=computed(()=>selected.value?(savedFiles[selected.value.id] || marketFiles[selected.value.id] || (selected.value.id==='imported-file'?file.value:undefined)):undefined)
function marketCard(row:MarketTemplateEntry):ScreenTemplate{
  const id='market-'+row.id
  const cached=marketFiles[id]
  const base:ScreenTemplate={schemaVersion:1,id,name:row.name,category:row.category,description:row.description,theme:cached && 'appearance' in cached?cached.appearance.theme:'light',kind:row.kind,slots:cached?importedTemplate(cached).slots:[]}
  return base
}
async function loadMarket(){
  try{
    const rows=(await listTemplateMarket()).data || []
    market.value=rows.filter(r=>r.kind=== (dashboard.value?'dashboard':'screen')).map(marketCard)
  }catch{market.value=[]}
}
async function ensureMarketFile(id:string){
  const entryId=id.slice('market-'.length)
  if(marketFiles[id])return
  const row=(await getMarketTemplate(entryId)).data
  const parsed=parseLayoutTemplate(JSON.stringify(row.content))
  if((parsed.format==='datacenter-dashboard-template')!==dashboard.value)throw Error(dashboard.value?'请使用看板类市场模板':'请使用大屏类市场模板')
  marketFiles[id]=parsed
  const card=market.value.find(t=>t.id===id)
  if(card){const imported=importedTemplate(parsed);card.slots=imported.slots;card.theme=imported.theme}
}
async function loadSaved(){
  try{
    const rows=(await listTemplates()).data || []
    const items:ScreenTemplate[]=[]
    for(const key of Object.keys(savedFiles))delete savedFiles[key]
    for(const row of rows){
      try{
        const parsed=parseLayoutTemplate(JSON.stringify(row.content))
        if((parsed.format==='datacenter-dashboard-template')!==dashboard.value)continue
        const item=importedTemplate(parsed)
        item.id='saved-'+row.id
        item.name=row.name
        item.category=row.category || item.category
        item.description=row.description || item.description
        items.push(item)
        savedFiles[item.id]=parsed
      }catch{/* skip damaged personal templates */}
    }
    saved.value=items
  }catch{saved.value=[]}
}
async function bootstrapPicker(){
  await Promise.all([loadSaved(),loadMarket()])
  const pick=props.initialSelectId?.trim()
  if(pick && catalog.value.some(t=>t.id===pick))await select(pick)
}
watch([visible,()=>props.kind],([open])=>{if(open)void bootstrapPicker()})
const categories=computed(()=>[...new Set(catalog.value.map(t=>t.category))])
const filtered=computed(()=>catalog.value.filter(t=>(!category.value || t.category===category.value) && (t.name+t.description).includes(search.value.trim())))
const scene=computed<ScreenScene>(()=>selectedFile.value && 'scene' in selectedFile.value?selectedFile.value.scene:{width:1920,height:1080,fit:'contain',refreshSeconds:0})
let generation=0
const sequences:Record<string,number>={}
const ready=computed(()=>!!name.value.trim() && !!selected.value && selected.value.slots.every(s=>bindings[s.key] && verified[s.key] && !slotLoading[s.key] && !validating[s.key]))
function leasedChartIds(){const ids=new Set<number>();for(const b of Object.values(bindings))if(b?.source==='dataset' && b.chartId)ids.add(b.chartId);return [...ids]}
async function releaseLeased(){const ids=leasedChartIds();if(!ids.length)return;try{await releaseTemplateChartLeases(ids)}catch{/* best effort */}}
function close(done:()=>void){if(!busy.value){if(!createdId.value)void releaseLeased();++generation;done()}}
async function importFile(event:Event){
  const input=event.target as HTMLInputElement,upload=input.files?.[0];if(!upload)return
  if(busy.value){input.value='';return}const g=++generation;error.value='';busy.value=true
  try{if(upload.size>TEMPLATE_MAX_BYTES)throw Error('模板文件不能超过2MB');const candidate=parseLayoutTemplate(await upload.text());if((candidate.format==='datacenter-dashboard-template')!==dashboard.value)throw Error(dashboard.value?'请导入本项目 v1 看板模板':'请导入本项目 v1 大屏模板');if(g!==generation)return;file.value=candidate;search.value='';category.value=dashboard.value?'导入看板模板':'导入模板'}catch(e){if(g===generation)error.value=e instanceof Error?e.message:'模板读取失败'}finally{input.value='';if(g===generation)busy.value=false}
}
function blank(){visible.value=false;router.push(dashboard.value?'/bi-designer':'/bi-designer?kind=screen')}
function clear(){for(const map of [bindings,verified,summaries,slotErrors,slotLoading,validating,impacts])for(const key of Object.keys(map))delete map[key];for(const key of Object.keys(sources))delete sources[key]}
async function back(){await releaseLeased();++generation;selected.value=undefined;wizardStep.value=1;batchDatasetId.value=undefined;error.value='';clear()}
async function select(id:string){const g=++generation;busy.value=true;error.value=''
  try{
    if(id.startsWith('market-')){await ensureMarketFile(id);selected.value=market.value.find(t=>t.id===id)}
    else selected.value=id.startsWith('saved-') && savedFiles[id]?saved.value.find(t=>t.id===id):id==='imported-file' && file.value?importedTemplate(file.value):dashboard.value?getDashboardTemplate(id):getScreenTemplate(id)
    if(g!==generation || !selected.value)return
    const suffix=dashboard.value?'看板':'大屏';name.value=selected.value.name.endsWith(suffix)?selected.value.name.slice(0,200):(selected.value.name.slice(0,198)+suffix);clear();wizardStep.value=1;batchDatasetId.value=undefined;for(const slot of selected.value.slots)sources[slot.key]='chart'
    const [c,d]=await Promise.all([listCharts(),listDatasets()]);if(g===generation){charts.value=c.data || [];datasets.value=(d.data || []).filter(x=>{const a=x.resourceActions;return Array.isArray(a)?a.includes('query'):String(a||'').includes('query')})}
  }catch{if(g===generation)error.value='模板加载失败，请检查权限或网络后重试'}finally{if(g===generation)busy.value=false}}
function goReview(){if(!name.value.trim()){error.value='请填写名称';return}error.value='';wizardStep.value=2}
function summary(binding:TemplateBinding){const id=binding.source==='dataset'?binding.datasetId:binding.datasetId || charts.value.find(c=>c.id===binding.chartId)?.datasetId;return `${binding.source==='dataset'?'数据集直绑':'源图表'} · #${id} · 维度：${binding.config.dimensions.map(d=>d.field).join('、') || '无'} · 度量：${binding.config.metrics.map(m=>m.agg+'('+m.field+')').join('、')} · 排序：${binding.config.orderBys.map(o=>o.field+' '+o.order).join('、') || '无'}`}
function refreshImpact(slot:TemplateSlot){const b=bindings[slot.key];if(!b || b.source!=='dataset' || !b.datasetId)return;chartFields(b.datasetId).then(r=>{impacts[slot.key]=analyzeBindingImpact(slot,b.config,r.data || [])}).catch(()=>{impacts[slot.key]=['字段清单加载失败，请检查数据集权限']})}
function switchSource(slot:TemplateSlot){delete bindings[slot.key];delete summaries[slot.key];delete impacts[slot.key];verified[slot.key]=false;slotErrors[slot.key]=''}
async function bindChart(slot:TemplateSlot,id?:number){
  const g=generation,v=(sequences[slot.key] || 0)+1;sequences[slot.key]=v;delete bindings[slot.key];delete summaries[slot.key];delete impacts[slot.key];verified[slot.key]=false;slotErrors[slot.key]='';if(!id)return
  slotLoading[slot.key]=true
  try{const d=(await getChart(id)).data.definition;if(g!==generation || sequences[slot.key]!==v)return
    const config:PanelChartConfig={type:slot.type,dimensions:slot.kind==='kpi'?[]:JSON.parse(JSON.stringify(d.query.dimensions)),metrics:JSON.parse(JSON.stringify(slot.kind==='kpi'?d.query.metrics.slice(0,1):d.query.metrics)),orderBys:slot.kind==='kpi'?[]:JSON.parse(JSON.stringify(d.query.orderBys || [])),limit:d.query.limit || 100}
    bindings[slot.key]={source:'chart',chartId:id,datasetId:d.query.datasetId,config};summaries[slot.key]=summary(bindings[slot.key])
  }catch{if(g===generation && sequences[slot.key]===v)slotErrors[slot.key]='绑定失败，请检查图表与数据集权限后重新选择'}finally{if(g===generation && sequences[slot.key]===v)slotLoading[slot.key]=false}
}
async function bindDataset(slot:TemplateSlot,id?:number,prevConfig?:PanelChartConfig){
  const g=generation,v=(sequences[slot.key] || 0)+1;sequences[slot.key]=v;const prior=prevConfig || bindings[slot.key]?.config;delete bindings[slot.key];delete summaries[slot.key];delete impacts[slot.key];verified[slot.key]=false;slotErrors[slot.key]='';if(!id)return
  slotLoading[slot.key]=true
  try{const fields=(await chartFields(id)).data || [];if(g!==generation || sequences[slot.key]!==v)return
    const config=prior?remapPanelConfig(slot,prior,fields):suggestPanelConfig(slot,fields);bindings[slot.key]={source:'dataset',datasetId:id,config};summaries[slot.key]=summary(bindings[slot.key]);impacts[slot.key]=analyzeBindingImpact(slot,config,fields)
  }catch{if(g===generation && sequences[slot.key]===v)slotErrors[slot.key]='数据集字段加载失败，请检查权限后重试'}finally{if(g===generation && sequences[slot.key]===v)slotLoading[slot.key]=false}
}
async function applyBatchDataset(){
  const id=batchDatasetId.value;if(!id || !selected.value || busy.value)return;busy.value=true;error.value=''
  try{
    const fields=(await chartFields(id)).data || []
    for(const slot of selected.value.slots){sources[slot.key]='dataset';const prior=bindings[slot.key]?.config;await bindDataset(slot,id,prior || suggestPanelConfig(slot,fields))}
  }catch{error.value='批量应用失败，请检查数据集权限'}finally{busy.value=false}
}
async function validateAll(){
  if(!selected.value || validatingAll.value)return;validatingAll.value=true;error.value=''
  try{for(const slot of selected.value.slots)await validateSlot(slot)}finally{validatingAll.value=false}
}
function configured(slot:TemplateSlot,config?:PanelChartConfig){const b=bindings[slot.key];if(!b || !config)return;b.config=config;verified[slot.key]=false;summaries[slot.key]=summary(b);slotErrors[slot.key]='字段已更新，请验证当前绑定';if(b.source==='dataset')refreshImpact(slot)}
async function resolveBinding(slot:TemplateSlot,binding:TemplateBinding){return binding.source==='dataset'?ensureBindingChart(binding,slot,name.value):binding}
async function validateSlot(slot:TemplateSlot){
  let binding=bindings[slot.key];if(!binding || validating[slot.key])return;const g=generation;validating[slot.key]=true;verified[slot.key]=false;slotErrors[slot.key]=''
  try{binding=await resolveBinding(slot,binding);bindings[slot.key]=binding;await previewDashboard({schemaVersion:1,name:'模板绑定验证',kind:dashboard.value?'dashboard':'screen',layout:dashboard.value?'grid':'absolute',scene:dashboard.value?undefined:scene.value,panels:[templateSlotPanel(slot,binding)],interaction:{controls:[],links:[]}},{filters:{},links:{}});if(g===generation)verified[slot.key]=true}
  catch{if(g===generation)slotErrors[slot.key]='验证失败，请检查字段类型、维度数量、聚合、排序或当前数据权限'}finally{if(g===generation)validating[slot.key]=false}
}
async function create(){
  if(!selected.value || (!ready.value && !createdId.value) || busy.value)return;busy.value=true;error.value=''
  try{if(!createdId.value){const resolved:Record<string,TemplateBinding>={};for(const slot of selected.value.slots)resolved[slot.key]=await resolveBinding(slot,bindings[slot.key]);const definition=selectedFile.value?instantiateImportedTemplate(selectedFile.value,name.value,resolved):dashboard.value?instantiateDashboardTemplate(selected.value.id,name.value,resolved):instantiateScreenTemplate(selected.value.id,name.value,resolved);await previewDashboard(definition,{filters:{},links:{}});const result=(await saveDashboard(definition)).data;createdId.value=result.id;emit('created',result.id);const leased=Object.values(resolved).filter(b=>b.source==='dataset' && b.chartId).map(b=>b.chartId!);if(leased.length)await commitTemplateChartLeases(leased)}await router.push('/bi-designer/'+createdId.value);visible.value=false;selected.value=undefined;++generation}
  catch{error.value='创建失败，请检查当前权限和数据绑定后重试；若页面跳转失败，请在看板列表确认是否已创建';await releaseLeased()}finally{busy.value=false}
}
</script>
<style scoped>
.creation-actions{display:flex;gap:12px;align-items:center;flex-wrap:wrap}.import-template{position:relative;display:inline-flex;align-items:center;border:1px solid #cbd5e1;border-radius:4px;padding:8px 14px;font-size:14px;color:#3370ff;cursor:pointer;overflow:hidden}.import-template:focus-within{outline:2px solid #3370ff}.import-template input{position:absolute;inset:0;opacity:0;width:100%;height:100%;cursor:pointer}.notice{margin:16px 0}.creation-choice{display:flex;justify-content:space-between;gap:20px;align-items:center;padding:12px 0 24px}.creation-choice h2{font-size:22px;color:#20324d;margin:0 0 10px}.creation-choice p,.template-card p{color:#78869a;line-height:1.7;font-size:13px;margin:0}.template-filters{display:flex;gap:12px;margin-bottom:20px}.template-filters .el-input{max-width:400px}.template-filters .el-select{width:180px}.template-grid{display:grid;grid-template-columns:1fr 1fr;gap:22px}.template-card{background:white;border:1px solid #dce5f0;border-radius:14px;padding:12px;text-align:left;cursor:pointer;transition:border-color .15s,box-shadow .15s}.template-card:hover,.template-card:focus-visible{border-color:#3370ff;box-shadow:0 6px 24px #3370ff18;outline:2px solid #3370ff22}.template-info{display:flex;justify-content:space-between;align-items:center;margin:16px 2px 8px}.template-info strong{font-size:17px;color:#20324d}.template-info span{font-size:11px;color:#7b8ba0}.template-action{display:block;color:#3370ff;font-size:12px;margin:14px 2px 4px}.binding-heading{display:flex;gap:18px;align-items:center;margin-bottom:18px;color:#64748b}.binding-row{border:1px solid #dce5f0;border-radius:10px;padding:18px;margin-top:14px}.binding-label{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.binding-row>.el-select{width:100%;margin-top:10px}.binding-summary,.binding-impact{color:#64748b;font-size:12px;line-height:1.8}.binding-impact{color:#b45309}.binding-error{color:var(--el-color-danger);font-size:12px}.binding-row .el-radio-group{margin-bottom:8px}.wizard-steps{margin:8px 0 20px}.batch-bar{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin:0 0 16px;padding:12px 14px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;font-size:13px;color:#64748b}.batch-bar .el-select{min-width:220px}.review-table{margin:12px 0 16px}@media(max-width:680px){.template-grid{grid-template-columns:1fr}.creation-choice{align-items:start;flex-direction:column}.creation-choice h2{font-size:18px}.binding-heading{align-items:start;flex-direction:column}.batch-bar{align-items:stretch;flex-direction:column}}
</style>
