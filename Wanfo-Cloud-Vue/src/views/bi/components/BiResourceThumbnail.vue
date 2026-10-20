<template>
  <div ref="host" class="resource-thumbnail" role="img" :aria-label="`${name}的真实布局缩略图`" :aria-busy="loading">
    <div v-if="board || chart" class="thumbnail-viewport" :style="viewportStyle" aria-hidden="true" inert>
      <div class="thumbnail-document" :style="documentStyle">
        <ChartPreview v-if="chart" thumbnail :definition="chart" :result="chartData" :labels="chartLabels" />
        <DashboardCanvas v-else-if="board" readonly :scene="board.scene ? {...board.scene,fit:'contain'} : undefined" :panels="board.panels" :appearance="board.appearance">
          <template #default="{panel}"><DashboardCard thumbnail :title="panel.title" :component-type="panel.componentType" :content="panel.content" :panel-style="panel.style" :appearance="board.appearance" :payload="payloads[panel.key]" :error="panelErrors[panel.key]" /></template>
        </DashboardCanvas>
      </div>
    </div>
    <div v-if="loading || !board && !chart" class="thumbnail-state"><el-icon :class="{'is-loading':loading}"><Loading v-if="loading"/><Picture v-else/></el-icon><span>{{ loading ? '正在生成真实预览' : error || '等待生成预览' }}</span></div>
    <span v-else class="thumbnail-version">{{ source === 'published' ? '发布' : '保存' }}版本 v{{ actualVersion }}</span>
    <span v-if="warning && !loading" class="thumbnail-warning">{{ warning }}</span>
    <button v-if="(error || warning) && !loading" type="button" class="thumbnail-retry" @click.stop="load">重试预览</button>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, shallowRef, watch, onMounted, onBeforeUnmount, onActivated, onDeactivated } from 'vue'
import { Loading, Picture } from '@element-plus/icons-vue'
import { checkPermi } from '@/utils/permission'
import useUserStore from '@/store/modules/user'
import { BI_SAVED_EVENT, thumbnailTask, versionedThumbnail } from '@/utils/biThumbnail'
import { getChart, savedChartData, type ChartDefinition, type ChartResult } from '@/api/bi/chart'
import { getDashboardDraft, getDashboardView, previewDashboard, dashboardPanelData, type DashboardView, type DashboardDefinition, type PanelResult } from '@/api/bi/dashboard'
import { runPanelQueue } from '@/utils/biPanelQueue'
import DashboardCanvas from '../dashboard/DashboardCanvas.vue'
import DashboardCard from '../dashboard/DashboardCard.vue'
import ChartPreview from '../chart/ChartPreview.vue'
const props = defineProps<{kind:'chart'|'dashboard';id:number;version:number;name:string;actions:string[];published?:boolean;refreshKey?:number}>()
const user = useUserStore()
const host=ref<HTMLElement>(),width=ref(0),height=ref(0),visible=ref(false),active=ref(true)
const board=shallowRef<DashboardDefinition|DashboardView>(),chart=shallowRef<ChartDefinition>(),chartData=shallowRef<ChartResult|null>(null)
const payloads=shallowRef<Record<string,PanelResult>>({}),panelErrors=shallowRef<Record<string,string>>({})
const loading=ref(false),error=ref(''),warning=ref(''),actualVersion=ref(0),source=ref('saved')
let generation=0,observer:IntersectionObserver|undefined,resize:ResizeObserver|undefined
const chartLabels=computed(()=>Object.fromEntries([...(chart.value?.query.dimensions || []),...(chart.value?.query.metrics || [])].map(f=>[f.alias || f.field,f.field])))
const dimensions=computed(()=>board.value?.scene ? {w:board.value.scene.width,h:board.value.scene.height} : board.value ? {w:1280,h:Math.max(720,...board.value.panels.map(p=>(p.y+p.h)*78+16))} : {w:900,h:500})
const scale=computed(()=>Math.min(width.value/dimensions.value.w,height.value/dimensions.value.h))
const viewportStyle=computed(()=>({width:dimensions.value.w*scale.value+'px',height:dimensions.value.h*scale.value+'px'}))
const documentStyle=computed(()=>({width:dimensions.value.w+'px',height:dimensions.value.h+'px',transform:`scale(${scale.value})`}))
function clear(){board.value=undefined;chart.value=undefined;chartData.value=null;payloads.value={};panelErrors.value={};warning.value='';error.value=''}
function saved(event:Event){const detail=(event as CustomEvent<{kind:string;id:number}>).detail;if(detail && ((detail.kind===props.kind && detail.id===props.id) || detail.kind==='chart' && props.kind==='dashboard'))void load()}
async function load(){
  const g=++generation;clear();if(!visible.value || !active.value)return
  loading.value=true
  const current=()=>g===generation && active.value
  try{await thumbnailTask(async()=>{
    if(props.kind==='chart'){
      const response=await getChart(props.id);if(!current())return
      chart.value=response.data.definition;actualVersion.value=response.data.version;source.value='saved'
      if(!props.actions.includes('query') || !checkPermi(['bi:chart:query'])){warning.value='暂无取数权限';return}
      try{const snapshot=await versionedThumbnail(async()=>(await getChart(props.id)).data,async()=>(await savedChartData(props.id)).data,current,response.data);if(current() && snapshot){chart.value=snapshot.definition;chartData.value=snapshot.data;actualVersion.value=snapshot.version}}catch{if(current())warning.value='数据或版本暂不可用'}
      return
    }
    const edit=props.actions.includes('edit') && checkPermi(['bi:dashboard:edit'])
    if(edit){
      const draft=(await getDashboardDraft(props.id)).data;if(!current())return
      board.value={...draft.definition,panels:draft.definition.panels.filter(p=>!p.hidden)};actualVersion.value=draft.version;source.value='saved'
      const input={filters:{},links:{}}
      // Same preview endpoint as the editor, including component overrides/default filters.
      if(!draft.definition.panels.some(p=>p.chartId && !p.hidden))return
      if(!checkPermi(['bi:dataset:query'])){warning.value='暂无取数权限';return}
      try{const preview=(await previewDashboard(draft.definition,input,props.id,draft.version)).data;if(current()){board.value=preview.view;payloads.value=preview.payloads}}
      catch{if(current()){warning.value='数据暂不可用';panelErrors.value=Object.fromEntries(draft.definition.panels.filter(p=>p.chartId).map(p=>[p.key,'暂无数据权限或数据不可用']))}}
    }else if(props.published && props.actions.includes('consume') && checkPermi(['bi:dashboard:consume'])){
      const view=(await getDashboardView(props.id)).data;if(!current())return
      board.value=view;actualVersion.value=view.revision;source.value='published'
      const panels=view.panels.filter(p=>!p.componentType || ['chart','kpi'].includes(p.componentType))
      await runPanelQueue(panels,async panel=>{try{const data=(await dashboardPanelData(props.id,panel.key,view.revision)).data;if(current())payloads.value={...payloads.value,[panel.key]:data}}catch{if(current()){panelErrors.value={...panelErrors.value,[panel.key]:'暂无数据权限或数据不可用'};warning.value='部分数据不可用'}}},current)
    }else error.value='暂无预览权限'
  },current)}catch{if(current()){clear();error.value='预览加载失败'}}finally{if(current())loading.value=false}
}
watch(()=>[props.kind,props.id,props.version,props.refreshKey,props.published,JSON.stringify(props.actions),user.id,user.permissions.join('|'),visible.value],()=>{void load()})
onMounted(()=>{
  window.addEventListener(BI_SAVED_EVENT,saved)
  if(!host.value)return
  resize=new ResizeObserver(([entry])=>{width.value=entry.contentRect.width;height.value=entry.contentRect.height});resize.observe(host.value)
  observer=new IntersectionObserver(([entry])=>{if(entry.isIntersecting){visible.value=true;observer?.disconnect()}},{rootMargin:'100px'});observer.observe(host.value)
})
onDeactivated(()=>{active.value=false;++generation;loading.value=false;clear()})
onActivated(()=>{if(!active.value){active.value=true;void load()}})
onBeforeUnmount(()=>{++generation;window.removeEventListener(BI_SAVED_EVENT,saved);observer?.disconnect();resize?.disconnect();clear()})
</script>
<style scoped>
.resource-thumbnail{position:relative;aspect-ratio:16/10;overflow:hidden;border-radius:7px;background:#edf1f7;display:flex;align-items:center;justify-content:center;isolation:isolate}.thumbnail-viewport{position:relative;flex-shrink:0;overflow:hidden;pointer-events:none}.thumbnail-document{position:absolute;left:0;top:0;transform-origin:top left;background:#fff;overflow:hidden;pointer-events:none}.thumbnail-state{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;background:#f3f6fb;color:#8693a7;font-size:12px}.thumbnail-state>.el-icon{font-size:25px}.thumbnail-version,.thumbnail-warning{position:absolute;bottom:7px;right:7px;border-radius:4px;background:#182230c9;padding:3px 6px;color:#fff;font-size:10px;line-height:1.4}.thumbnail-warning{right:auto;left:7px;background:#fff4de;color:#98651b}.thumbnail-retry{position:absolute;bottom:15px;border:0;background:transparent;color:#3563e9;cursor:pointer;font-size:12px}.thumbnail-document :deep(.screen-editor){height:100%}.thumbnail-document :deep(.screen-display-tools){display:none}.thumbnail-document :deep(.screen-frame){height:100%;min-height:0;border:0;border-radius:0;overflow:hidden}.thumbnail-document :deep(.view-panel){overflow:hidden}.thumbnail-document :deep(.view-grid){grid-template-columns:repeat(12,minmax(0,1fr));grid-auto-rows:70px}.thumbnail-document :deep(.view-panel){grid-column:var(--thumbnail-column)!important;grid-row:var(--thumbnail-row)!important;min-height:0;display:flex;flex-direction:column}.thumbnail-document :deep(.panel-body){flex:1;min-height:0;overflow:hidden}.thumbnail-document :deep(.panel-header){flex-shrink:0}.thumbnail-document :deep(.chart-canvas),.thumbnail-document :deep(.chart-canvas--gauge),.thumbnail-document :deep(.chart-canvas--map){height:100%;min-height:100px}.thumbnail-document>.chart-canvas{height:100%}
</style>
