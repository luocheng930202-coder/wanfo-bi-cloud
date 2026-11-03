<template>
  <div v-if="(!decoration && !container && panelStyle?.showTitle!==false) || editable" class="panel-header" :class="{'hologram-header':appearance?.visualPreset==='hologram-ice-v1','decoration-header':decoration || container,'overlay-header':editable && panelStyle?.showTitle===false}"><strong :class="{'panel-handle':editable}" :style="{textAlign:panelStyle?.titleAlign || 'left'}"><span v-if="decoration || container || panelStyle?.showTitle!==false">{{ title || '未命名图表' }}</span><span v-else-if="editable" class="hidden-title">标题已隐藏（拖动此处移动）</span><span v-else class="sr-only">{{ title || '图表' }}</span></strong><time v-if="!thumbnail && queryCompletedAt && payload" class="query-completed" :datetime="queryCompletedAt" title="查询成功返回的时间，可能命中缓存，不代表源数据更新时间">查询 {{ new Date(queryCompletedAt).toLocaleTimeString() }}</time><div><el-button v-if="!thumbnail && !demonstration && (!componentType || ['chart','kpi','scene3d'].includes(componentType)) && (componentType!=='scene3d' || !!payload)" link :disabled="disabled || loading || refreshDisabled" @click="emit('refresh')">{{ error || queryStopped ? '重试' : '刷新' }}</el-button><el-button v-if="editable" link type="danger" :disabled="disabled" @click="emit('remove')">移除</el-button></div></div>

  <div v-if="!thumbnail && queryFeedback && (loading || queryStopped)" class="query-feedback"><span role="status" aria-live="polite">{{ loading ? queryWaitMessage(queryElapsed || 0) : '已停止等待并请求取消，可重试此组件。' }}</span><el-button v-if="loading" size="small" :aria-label="'停止等待'+(title || '图表')" @click="emit('stop')">停止等待</el-button><span v-if="loading">停止时将请求服务端取消，部分驱动可能延迟生效。</span></div>
  <nav v-if="drillFields?.length && !decoration && !container" class="drill-crumb" aria-label="下钻路径"><button type="button" class="crumb" :disabled="disabled || loading" @click="emit('drill-up',0)">全部</button><template v-for="(seg,i) in drillPath" :key="i"><span class="sep">›</span><button type="button" class="crumb" :disabled="disabled || loading" @click="emit('drill-up',i+1)">{{ payload?.data.mapContext?.breadcrumbs[i]?.name || mapCrumbNames[String(seg.value)] || seg.value || '空值' }}</button></template></nav>
  <div class="panel-body" :style="{'--bi-component-font':panelStyle?.fontSize?panelStyle.fontSize+'px':undefined,'--bi-component-text':panelStyle?.textColor || undefined}" :class="{'decoration-body':decoration || container}" v-loading="loading">
    <div v-if="queryStopped && !loading" class="panel-loading">重新查询后显示数据</div>
    <el-alert v-else-if="error" :title="error" type="error" :closable="false"/>
    <div v-else-if="container" class="decoration-shape" :style="containerStyle" role="group" :aria-label="title"/>
    <div v-else-if="decoration" class="decoration-shape" :class="componentType==='border'?'decoration-border':'decoration-line'" :style="decorationStyle" :role="componentType==='line'?'separator':'img'" :aria-label="title"/>
    <div v-else-if="componentType==='text'" class="static-text" :class="{'centered-text':panelStyle?.titleAlign==='center','tech-text':['tech-blue-v1','hologram-ice-v1'].includes(appearance?.visualPreset || ''),'hologram-heading':appearance?.visualPreset==='hologram-ice-v1' && (panelStyle?.fontSize || 15)>=24}">{{ content?.text || '在右侧填写文本内容' }}</div>
    <div v-else-if="componentType==='clock'" class="static-text clock-text" :class="{'centered-text':panelStyle?.titleAlign!=='left','tech-text':['tech-blue-v1','hologram-ice-v1'].includes(appearance?.visualPreset || ''),'hologram-heading':appearance?.visualPreset==='hologram-ice-v1' && (panelStyle?.fontSize || 15)>=24}" role="timer">{{ clockLabel }}</div>
    <div v-else-if="componentType==='image'" class="static-image"><img v-if="content?.image" :src="content.image" :alt="title || '看板图片'"/><el-empty v-else description="在右侧选择图片" :image-size="56"/></div>
  <div v-else-if="needsBind" class="panel-unbound" role="status"><strong>尚未绑定数据</strong><span>在右侧「数据」中选择已保存图表后再预览或发布</span></div>
  <div v-else-if="componentType==='kpi'" class="kpi-content" :class="{'tech-kpi':['tech-blue-v1','hologram-ice-v1'].includes(appearance?.visualPreset || '')}"><span v-if="panelStyle?.showTitle===false" class="kpi-label">{{ kpiMetricLabel }}</span><strong :aria-label="kpiMetricLabel+'：'+kpiValue" :style="{color:kpiColor}">{{ kpiValue }}</strong><span class="kpi-caption">{{ demonstration?'演示数据 · 非真实业务':payload?(payload.data.rows.length?'当前筛选与权限范围内的聚合值':'查询成功，当前筛选与权限范围内没有数据；请检查筛选范围'):'点击预览加载数据' }}</span></div>
    <div v-else-if="componentType==='scene3d' && (thumbnail || !sceneConfig.enabled)" class="scene-thumbnail" role="img" :aria-label="sceneConfig.enabled?'3D 场景缩略图':'3D 场景安全预览'"><span>3D</span><strong>{{ sceneConfig.enabled?(sceneConfig.mode==='map'?'行政区地图':sceneConfig.mode==='chart'?'数据柱图':'食堂建筑'):'场景静态预览' }}</strong><small>{{ sceneConfig.enabled?'科技大屏场景':sceneConfig.fallbackReason || '不支持此配置，已安全降级' }}</small></div>
    <div v-else-if="componentType==='scene3d'" class="scene-panel"><SchoolCanteen3DScene :holographic="appearance?.visualPreset==='hologram-ice-v1'" :glow-intensity="appearance?.glowIntensity ?? 55" :mode="sceneConfig.mode" region="未配置地区" :rows="payload?.data.rows" :dimension="payload?.presentation.dimensions[0]" :metric="payload?.presentation.metrics[0]" :linkable="linkable" :interaction-disabled="!sceneInteractive || disabled || loading || refreshDisabled" @drill="value=>emit('drill',value)" @select="value=>emit('select',value)" :map-context="payload?.data.mapContext" :map-value-mode="payload?.presentation.advanced?.mapValueMode" :data-bound="dataBound ?? !!payload" :loading="loading" :camera-preset="sceneConfig.cameraPreset" :rotate-enabled="sceneConfig.rotateEnabled" :zoom-enabled="sceneConfig.zoomEnabled"/></div>
    <div v-else-if="loading && !payload" class="panel-loading" role="status" aria-live="polite">正在加载图表数据…</div>
    <ChartPreview v-else embedded :loading="loading" :thumbnail="thumbnail || demonstration" :result="payload?.data || null" :definition="definition" :labels="labels" :appearance="appearance" :number-format="panelStyle?.numberFormat" :metric-styles="panelStyle?.metrics" :linkable="linkable" :drillable="!!drillFields?.length" :can-drill-down="payload?.presentation?.canDrillDown" @select="v=>emit('select',v)" @drill="p=>emit('drill',p)"/>
  </div>
</template>
<script setup lang="ts">
import { resolveScene3DConfig, type PanelResult, type PanelStyle, type DashboardAppearance, type ComponentType, type PanelContent, type DrillPathSegment } from '@/api/bi/dashboard'
import type { ChartDefinition } from '@/api/bi/chart'
import ChartPreview from '../chart/ChartPreview.vue'
import {queryWaitMessage} from '@/utils/biQueryRun'
import {formatMetric,metricFormat,metricColor,normalizeDecoration,normalizeContainer} from '@/utils/biPresentation'
import { resolveDrillPath } from '@/utils/biDrill'
import { computed,ref,watch,onMounted,onBeforeUnmount } from 'vue'
import { defineAsyncComponent } from 'vue'
const SchoolCanteen3DScene=defineAsyncComponent(()=>import('./SchoolCanteen3DScene.vue'))
import {registeredGeoFeatures} from '@/utils/biMapGeo'
import { resolveChartMetricFormat } from '@/utils/biChartPresentation'
const props=defineProps<{demonstration?:boolean;sceneInteractive?:boolean;dataBound?:boolean;queryCompletedAt?:string;queryFeedback?:boolean;queryElapsed?:number;queryStopped?:boolean;refreshDisabled?:boolean;thumbnail?:boolean;title:string;payload?:PanelResult;error?:string;loading?:boolean;editable?:boolean;disabled?:boolean;panelStyle?:PanelStyle;appearance?:DashboardAppearance;linkable?:boolean;drillFields?:string[];drillPath?:DrillPathSegment[];componentType?:ComponentType;content?:PanelContent}>()
const mapCrumbNames=ref<Record<string,string>>(Object.create(null))
function rememberMapNames(payload:PanelResult|undefined){const context=payload?.data.mapContext;if(!context)return;for(const f of registeredGeoFeatures(context.scope))mapCrumbNames.value[String(f.adcode)]=f.name;for(const crumb of context.breadcrumbs)mapCrumbNames.value[crumb.code]=crumb.name}
watch(()=>props.payload,(now,previous)=>{rememberMapNames(previous);if(now && !now.data.mapContext)mapCrumbNames.value=Object.create(null);rememberMapNames(now)},{immediate:true})
watch(()=>props.title,()=>{mapCrumbNames.value=Object.create(null)})
const drillFields=computed(()=>props.drillFields?.length?props.drillFields:props.payload?.presentation?.drillFields)
const drillPath=computed(()=>resolveDrillPath(props.drillPath,props.payload))
const emit=defineEmits<{(e:'stop'):void;(e:'refresh'):void;(e:'remove'):void;(e:'select',value:string|number|null):void;(e:'drill',payload:{field:string;value:string|number|null}):void;(e:'drill-up',level:number):void}>()
// Presentation aliases are display-only. This generated model is never submitted for querying.
const definition=computed<ChartDefinition>(()=>{
  const p=props.payload?.presentation
  const dims=p?.queryDimensions?.length?p.queryDimensions:(p?.dimensions||[]).map(f=>({field:f,alias:f}))
  return {schemaVersion:1,name:props.title,type:p?.type || 'table',style:p?.style,query:{datasetId:0,timeComparison:props.payload?.data.timeComparison?.mode,dimensions:dims,metrics:p?.queryMetrics?.length?p.queryMetrics:(p?.metrics||[]).map(f=>({field:f,alias:f,agg:'NONE'}))},advanced:p?.advanced || (p?.pivotRowFields?{pivotRowFields:p.pivotRowFields,pivotColFields:p.pivotColFields||[]}:undefined)}
})
const container=computed(()=>props.componentType==='container')
const needsBind=computed(()=>{
  if(props.dataBound!==false)return false
  const t=String(props.componentType||'chart')
  if(t==='chart'||t==='kpi')return true
  if(t==='scene3d'){const mode=props.content?.scene3d?.mode;return mode==='map'||mode==='chart'}
  return false
})
const clockLabel=ref('')
function formatClock(now:Date,pattern:string){
  const pad=(n:number)=>String(n).padStart(2,'0')
  const Y=now.getFullYear(),M=pad(now.getMonth()+1),D=pad(now.getDate()),h=pad(now.getHours()),m=pad(now.getMinutes()),s=pad(now.getSeconds())
  return pattern.replace('YYYY',String(Y)).replace('MM',M).replace('DD',D).replace('HH',h).replace('mm',m).replace('ss',s)
}
function tickClock(){clockLabel.value=formatClock(new Date(),props.content?.text || 'HH:mm:ss')}
let clockTimer:ReturnType<typeof setInterval>|undefined
onMounted(()=>{if(props.componentType==='clock'){tickClock();clockTimer=setInterval(tickClock,1000)}})
watch(()=>[props.componentType,props.content?.text],()=>{
  if(clockTimer){clearInterval(clockTimer);clockTimer=undefined}
  if(props.componentType==='clock'){tickClock();clockTimer=setInterval(tickClock,1000)}
})
onBeforeUnmount(()=>{if(clockTimer)clearInterval(clockTimer)})
const sceneConfig=computed(()=>resolveScene3DConfig(props.content?.scene3d))
const containerStyle=computed(()=>{let c:ReturnType<typeof normalizeContainer>;try{c=normalizeContainer(props.content?.container)}catch{c=normalizeContainer()}return {inset:'0',background:c.background,border:`${c.stroke}px solid ${c.borderColor}`,borderRadius:c.radius+'px'}})
const decoration=computed(()=>props.componentType==='border' || props.componentType==='line')
const decorationStyle=computed(()=>{if(!decoration.value)return {};let d:ReturnType<typeof normalizeDecoration>;try{d=normalizeDecoration(props.content?.decoration)}catch{d=normalizeDecoration()}const stroke=`${d.stroke}px ${d.pattern} ${d.color}`;return props.componentType==='border'?{inset:'0',border:stroke,borderRadius:d.radius+'px'}:d.direction==='vertical'?{left:'50%',top:'0',height:'100%',borderLeft:stroke}:{top:'50%',left:'0',width:'100%',borderTop:stroke}})
const kpiValue=computed(()=>{const key=props.payload?.presentation.metrics[0],value=key?props.payload?.data.rows[0]?.[key]:null;return formatMetric(value,props.appearance,resolveChartMetricFormat(props.payload?.presentation.style,key || '',props.panelStyle?.metrics,props.panelStyle?.numberFormat))})
const kpiColor=computed(()=>{const key=props.payload?.presentation.metrics[0];return key?metricColor(props.payload?.data.rows[0]?.[key],key,props.panelStyle?.metrics):undefined})
const labels=computed(()=>{
  const fl=props.payload?.presentation?.fieldLabels
  if(fl && Object.keys(fl).length) return fl
  return Object.fromEntries((props.payload?.data.columns||[]).map(c=>[c,c]))
})
const kpiMetricLabel=computed(()=>{
  const key=props.payload?.presentation.metrics[0]
  if(!key) return '聚合指标'
  return props.payload?.presentation?.fieldLabels?.[key] || key
})
</script>
<style scoped>.query-feedback{display:flex;align-items:center;flex-wrap:wrap;gap:8px;padding:8px 12px;font-size:12px;color:var(--bi-text,#667085)}.panel-loading,.panel-unbound{min-height:120px;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:6px;font-size:12px;color:var(--bi-text,#667085);text-align:center;padding:16px}.panel-unbound strong{font-size:14px;color:var(--bi-text,#303133)}.drill-crumb{display:flex;flex-wrap:wrap;align-items:center;gap:4px;padding:6px 12px 0;font-size:12px;color:var(--bi-text,#606266)}.crumb{border:0;background:transparent;color:var(--bi-link,var(--el-color-primary));cursor:pointer;padding:0}.crumb:hover{text-decoration:underline}.crumb:disabled{opacity:.55;cursor:wait}.sep{opacity:.5}.decoration-body{position:absolute;inset:0;padding:0!important;min-height:0!important;overflow:hidden!important}.decoration-shape{position:absolute;box-sizing:border-box;pointer-events:none}.decoration-header{position:absolute;top:2px;left:4px;right:4px;z-index:2;padding:2px 5px!important;background:#172c4dcc!important;border:0!important;color:#e5ecff!important}.decoration-header strong{font-size:11px!important}.decoration-header :deep(.el-button){font-size:10px}.decoration-line{box-sizing:border-box}.panel-header{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:10px 12px;border-bottom:1px solid var(--bi-border,#e4e7ed);background:var(--bi-heading-fill,var(--bi-card,#fafbfd));color:var(--bi-text,#303133);box-shadow:inset 3px 0 var(--bi-heading-accent,transparent)}.panel-header strong{font-size:var(--bi-title-size,14px);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1;min-width:0}.panel-handle{cursor:move;touch-action:none}.panel-body :deep(.el-button.is-link){color:var(--bi-link,var(--el-color-primary))}.panel-body{padding:12px;min-height:120px}.hidden-title{font-size:12px;font-weight:normal;opacity:.75}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}.static-text{white-space:pre-wrap;overflow-wrap:anywhere;color:var(--bi-component-text,var(--bi-text));line-height:1.6;font-size:var(--bi-component-font,15px)}.clock-text{font-variant-numeric:tabular-nums;font-size:clamp(22px,4vw,40px);font-weight:650;letter-spacing:.04em}.static-image{height:100%;min-height:120px;display:flex;align-items:center;justify-content:center}.static-image img{max-width:100%;max-height:480px;object-fit:contain}.kpi-content{container-type:inline-size;display:flex;min-height:160px;flex-direction:column;justify-content:center;gap:12px;padding:12px;color:var(--bi-text)}.kpi-content strong{text-shadow:var(--bi-number-glow,none);letter-spacing:.02em;font-size:clamp(28px,4vw,46px);font-size:clamp(20px,11cqw,46px);font-variant-numeric:tabular-nums;overflow-wrap:anywhere;line-height:1.2}.kpi-label,.kpi-caption{opacity:.65;font-size:12px}</style>
<style scoped>.scene-panel{position:relative;height:100%;min-height:260px;margin:-12px;overflow:hidden}.scene-thumbnail{height:100%;min-height:110px;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:5px;background:radial-gradient(ellipse at 50% 35%,#163e59,#081827 70%);color:#d9f5ff}.scene-thumbnail span{font-size:26px;font-weight:800;letter-spacing:.1em;color:#4bd8eb;text-shadow:0 0 16px #26bee6}.scene-thumbnail strong{font-size:13px}.scene-thumbnail small{font-size:10px;color:#83a9bc}</style>

<style scoped>.query-completed{padding:2px 12px;font-size:11px;color:var(--bi-text);opacity:.65}</style>

<style scoped>
.centered-text{text-align:center;display:flex;align-items:center;justify-content:center;height:100%;box-sizing:border-box}
.tech-text.centered-text{font-size:var(--bi-component-font,30px);line-height:1.2;font-weight:700;letter-spacing:.16em;text-shadow:var(--bi-title-glow,0 0 22px #38bdf855)}
.panel-body:has(>.tech-text.centered-text){min-height:0;height:100%;padding:8px 20px;box-sizing:border-box}
</style>

<style scoped>
.overlay-header{position:absolute;inset:0 0 auto;z-index:3;padding:3px 8px;background:#0b2032e6;opacity:0}
.overlay-header:hover,.overlay-header:focus-within{opacity:1}
.clock-text{font-size:var(--bi-component-font,22px);color:var(--bi-component-text,var(--bi-text));font-variant-numeric:tabular-nums}
.kpi-content{color:var(--bi-component-text,var(--bi-text))}
.panel-body:has(>.static-text){min-height:0;height:100%;box-sizing:border-box}
</style>

<style scoped>.panel-header .query-completed{font-size:10px;white-space:nowrap;padding:0;flex-shrink:0;opacity:.55}</style>

<style scoped>.hologram-heading{text-shadow:var(--bi-title-glow,none)}.hologram-header strong{text-shadow:var(--bi-number-glow,none)}</style>
