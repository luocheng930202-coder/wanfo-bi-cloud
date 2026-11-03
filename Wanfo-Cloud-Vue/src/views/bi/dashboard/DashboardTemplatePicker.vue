<template>
  <el-dialog v-model="visible" :title="dashboard?'新建看板':'新建数据大屏'" width="min(1100px,calc(100vw - 24px))" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" :before-close="close">
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice"/>
    <template v-if="!selected">
      <div class="creation-choice"><div><h2>从布局开始，再在设计器里绑数据</h2><p>选择模板即可创建草稿并打开设计器；图表绑定放到画布上完成，发布前再校验。也可从空白{{ dashboard?'看板':'画布' }}开始。</p></div><div class="creation-actions"><label class="import-template">导入模板文件<input type="file" accept=".json,application/json" :aria-label="dashboard?'导入看板模板文件':'导入大屏模板文件'" :disabled="busy" @change="importFile"/></label><el-button :disabled="busy" @click="blank">空白创建</el-button></div></div>
      <div class="template-filters"><el-input v-model="search" clearable placeholder="搜索模板" :aria-label="dashboard?'搜索看板模板':'搜索大屏模板'"/><el-select v-model="category" placeholder="全部分类" aria-label="模板分类"><el-option label="全部分类" value=""/><el-option v-for="c in categories" :key="c" :label="c" :value="c"/></el-select></div>
      <div class="template-grid"><button v-for="item in filtered" :key="item.id" class="template-card" :disabled="busy" @click="select(item.id)"><DashboardTemplatePreview :template="item" :file="savedFiles[item.id] || marketFiles[item.id] || (item.id==='imported-file'?file:undefined)"/><div class="template-info"><strong>{{ item.name }}</strong><span>{{ item.category }}</span></div><p>{{ item.description }}</p><span class="template-action">预览与使用 →</span></button></div>
      <el-empty v-if="!filtered.length" description="没有匹配的模板，试试其他分类或关键词"/>
    </template>
    <template v-else>
      <el-steps :active="1" finish-status="success" align-center class="wizard-steps"><el-step title="选择模板"/><el-step title="创建草稿"/></el-steps>
      <div class="binding-heading"><el-button :disabled="busy" @click="back">返回模板列表</el-button><span>{{ selected.name }} · {{ dashboard?'12 列栅格':(scene.width+' × '+scene.height) }} · {{ selected.slots.length }} 个数据组件</span></div>
      <DashboardTemplatePreview :template="selected" :file="selectedFile"/>
      <el-form label-position="top" :disabled="busy"><el-form-item :label="dashboard?'新看板名称':'新大屏名称'"><el-input v-model="name" maxlength="200" :aria-label="dashboard?'模板创建看板名称':'模板创建大屏名称'"/></el-form-item></el-form>
      <div v-if="!dashboard" class="notice"><strong>发布后的自动刷新</strong><el-select v-model="refreshSeconds" :disabled="busy" aria-label="发布后的自动刷新间隔"><el-option v-for="seconds in refreshChoices" :key="seconds" :value="seconds" :label="seconds===0?'手动刷新':('每 '+seconds+' 秒')" /></el-select></div>
      <el-alert title="将只复制布局与样式进草稿，数据组件先保持未绑定；进入设计器后再选图表，发布前仍须全部绑定。" type="success" :closable="false" class="notice"/>
    </template>
    <template #footer>
      <el-button :disabled="busy" @click="visible=false">取消</el-button>
      <el-button v-if="selected" type="primary" :disabled="busy || !name.trim()" :loading="busy" @click="applyLayoutFirst">应用布局并打开设计器</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import {computed,reactive,ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import {listTemplates,listTemplateMarket,getMarketTemplate,type MarketTemplateEntry} from '@/api/bi/template'
import {saveDashboard,type ScreenScene} from '@/api/bi/dashboard'
import {TEMPLATE_MAX_BYTES,parseLayoutTemplate,importedTemplate,applyImportedTemplateLayout,type LayoutTemplateFile} from '@/utils/biTemplateFiles'
import {screenTemplates,dashboardTemplates,getScreenTemplate,getDashboardTemplate,applyScreenTemplateLayout,applyDashboardTemplateLayout,type ScreenTemplate} from '@/utils/biTemplates'
import DashboardTemplatePreview from './DashboardTemplatePreview.vue'
import { CANTEEN_TEMPLATE_ID, canteenNumberFormat } from '@/utils/biCanteenTemplateContracts'
const visible=defineModel<boolean>({required:true})
const props=defineProps<{kind?:'screen'|'dashboard';initialSelectId?:string}>()
const emit=defineEmits<{(e:'created',id:number):void}>()
const dashboard=computed(()=>props.kind==='dashboard')
const router=useRouter(),selected=ref<ScreenTemplate>(),search=ref(''),category=ref(''),name=ref(''),busy=ref(false),error=ref('')
const createdId=ref<number>()
const refreshSeconds=ref(0)
const refreshChoices=computed(()=>[...new Set([0,10,30,60,300,900,refreshSeconds.value])].sort((a,b)=>a-b))
let generation=0
async function applyLayoutFirst(){
  if(!selected.value || !name.value.trim() || busy.value)return
  busy.value=true;error.value=''
  try{
    const definition=selectedFile.value
      ?applyImportedTemplateLayout(selectedFile.value,name.value)
      :dashboard.value?applyDashboardTemplateLayout(selected.value.id,name.value):applyScreenTemplateLayout(selected.value.id,name.value)
    if(definition.scene)definition.scene={...definition.scene,refreshSeconds:refreshSeconds.value}
    const result=(await saveDashboard(definition)).data
    createdId.value=result.id;emit('created',result.id)
    const failure=await router.push('/bi-admin/dashboard-workspace/'+createdId.value+'/edit')
    if(failure)throw Error('编辑器跳转未完成')
    visible.value=false;selected.value=undefined;++generation
  }catch(e){error.value=e instanceof Error && e.message && e.message!=='error'?e.message:(createdId.value?'草稿已创建，但尚未打开编辑器':'应用布局失败，请检查权限后重试')}
  finally{busy.value=false}
}
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
    const builtin=rows.filter(row=>row.provider==='builtin' && row.kind===(dashboard.value?'dashboard':'screen')).slice(0,12)
    for(let i=0;i<builtin.length;i+=2)await Promise.allSettled(builtin.slice(i,i+2).map(row=>ensureMarketFile('market-'+row.id)))
  }catch{market.value=[]}
}
async function ensureMarketFile(id:string){
  const entryId=id.slice('market-'.length)
  if(marketFiles[id])return
  const row=(await getMarketTemplate(entryId)).data
  const parsed=parseLayoutTemplate(JSON.stringify(row.content))
  if((parsed.format==='datacenter-dashboard-template')!==dashboard.value)throw Error(dashboard.value?'请使用看板类市场模板':'请使用大屏类市场模板')
  if(id===CANTEEN_TEMPLATE_ID)for(const panel of parsed.panels){const format=canteenNumberFormat(panel.key);if(format && !panel.style?.numberFormat)panel.style={showTitle:true,titleAlign:'left',...panel.style,numberFormat:format}}
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
watch([visible,()=>props.kind,()=>props.initialSelectId],([open])=>{if(open)void bootstrapPicker()},{immediate:true})
const categories=computed(()=>[...new Set(catalog.value.map(t=>t.category))])
const filtered=computed(()=>catalog.value.filter(t=>(!category.value || t.category===category.value) && (t.name+t.description).includes(search.value.trim())))
const scene=computed<ScreenScene>(()=>selectedFile.value && 'scene' in selectedFile.value?selectedFile.value.scene:{width:1920,height:1080,fit:'contain',refreshSeconds:0})
function close(done:()=>void){if(!busy.value){++generation;done()}}
async function importFile(event:Event){
  const input=event.target as HTMLInputElement,upload=input.files?.[0];if(!upload)return
  if(busy.value){input.value='';return}const g=++generation;error.value='';busy.value=true
  try{if(upload.size>TEMPLATE_MAX_BYTES)throw Error('模板文件不能超过2MB');const candidate=parseLayoutTemplate(await upload.text());if((candidate.format==='datacenter-dashboard-template')!==dashboard.value)throw Error(dashboard.value?'请导入本项目 v1 看板模板':'请导入本项目 v1 大屏模板');if(g!==generation)return;file.value=candidate;search.value='';category.value=dashboard.value?'导入看板模板':'导入模板'}catch(e){if(g===generation)error.value=e instanceof Error?e.message:'模板读取失败'}finally{input.value='';if(g===generation)busy.value=false}
}
function blank(){visible.value=false;router.push(dashboard.value?'/bi-admin/dashboard-workspace/new':'/bi-admin/dashboard-workspace/new?kind=screen')}
function back(){++generation;selected.value=undefined;error.value=''}
async function select(id:string){const g=++generation;busy.value=true;error.value=''
  try{
    if(id.startsWith('market-')){await ensureMarketFile(id);selected.value=market.value.find(t=>t.id===id)}
    else selected.value=id.startsWith('saved-') && savedFiles[id]?saved.value.find(t=>t.id===id):id==='imported-file' && file.value?importedTemplate(file.value):dashboard.value?getDashboardTemplate(id):getScreenTemplate(id)
    if(g!==generation || !selected.value)return
    refreshSeconds.value=selectedFile.value && 'scene' in selectedFile.value?(selectedFile.value.scene.refreshSeconds || 0):0
    const suffix=dashboard.value?'看板':'大屏'
    name.value=selected.value.name.endsWith(suffix)?selected.value.name.slice(0,200):(selected.value.name.slice(0,198)+suffix)
  }catch{if(g===generation)error.value='模板加载失败，请检查权限或网络后重试'}finally{if(g===generation)busy.value=false}
}
</script>
<style scoped>
.creation-actions{display:flex;gap:12px;align-items:center;flex-wrap:wrap}.import-template{position:relative;display:inline-flex;align-items:center;border:1px solid #cbd5e1;border-radius:4px;padding:8px 14px;font-size:14px;color:#3370ff;cursor:pointer;overflow:hidden}.import-template:focus-within{outline:2px solid #3370ff}.import-template input{position:absolute;inset:0;opacity:0;width:100%;height:100%;cursor:pointer}.notice{margin:16px 0}.creation-choice{display:flex;justify-content:space-between;gap:20px;align-items:center;padding:12px 0 24px}.creation-choice h2{font-size:22px;color:#20324d;margin:0 0 10px}.creation-choice p,.template-card p{color:#78869a;line-height:1.7;font-size:13px;margin:0}.template-filters{display:flex;gap:12px;margin-bottom:20px}.template-filters .el-input{max-width:400px}.template-filters .el-select{width:180px}.template-grid{display:grid;grid-template-columns:1fr 1fr;gap:22px}.template-card{background:white;border:1px solid #dce5f0;border-radius:14px;padding:12px;text-align:left;cursor:pointer;transition:border-color .15s,box-shadow .15s}.template-card:hover,.template-card:focus-visible{border-color:#3370ff;box-shadow:0 6px 24px #3370ff18;outline:2px solid #3370ff22}.template-info{display:flex;justify-content:space-between;align-items:center;margin:16px 2px 8px}.template-info strong{font-size:17px;color:#20324d}.template-info span{font-size:11px;color:#7b8ba0}.template-action{display:block;color:#3370ff;font-size:12px;margin:14px 2px 4px}.binding-heading{display:flex;gap:18px;align-items:center;margin-bottom:18px;color:#64748b}.wizard-steps{margin:8px 0 20px}@media(max-width:680px){.template-grid{grid-template-columns:1fr}.creation-choice{align-items:start;flex-direction:column}.creation-choice h2{font-size:18px}.binding-heading{align-items:start;flex-direction:column}}
</style>
