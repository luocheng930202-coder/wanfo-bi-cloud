<template>
  <div v-if="(!decoration && !container) || editable" class="panel-header" :class="{'decoration-header':decoration || container}"><strong :class="{'panel-handle':editable}" :style="{textAlign:panelStyle?.titleAlign || 'left'}"><span v-if="decoration || container || panelStyle?.showTitle!==false">{{ title || '未命名图表' }}</span><span v-else-if="editable" class="hidden-title">标题已隐藏（拖动此处移动）</span><span v-else class="sr-only">{{ title || '图表' }}</span></strong><div><el-button v-if="!thumbnail && (!componentType || ['chart','kpi'].includes(componentType))" link :disabled="disabled || loading" @click="emit('refresh')">刷新</el-button><el-button v-if="editable" link type="danger" :disabled="disabled" @click="emit('remove')">移除</el-button></div></div>
  <nav v-if="drillFields?.length && !decoration && !container" class="drill-crumb" aria-label="下钻路径"><button type="button" class="crumb" @click="emit('drill-up',0)">全部</button><template v-for="(seg,i) in drillPath" :key="i"><span class="sep">›</span><button type="button" class="crumb" @click="emit('drill-up',i+1)">{{ seg.value ?? '空值' }}</button></template></nav>
  <div class="panel-body" :class="{'decoration-body':decoration || container}" v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <div v-else-if="container" class="decoration-shape" :style="containerStyle" role="group" :aria-label="title"/>
    <div v-else-if="decoration" class="decoration-shape" :class="componentType==='border'?'decoration-border':'decoration-line'" :style="decorationStyle" :role="componentType==='line'?'separator':'img'" :aria-label="title"/>
    <div v-else-if="componentType==='text'" class="static-text">{{ content?.text || '在右侧填写文本内容' }}</div>
    <div v-else-if="componentType==='image'" class="static-image"><img v-if="content?.image" :src="content.image" :alt="title || '看板图片'"/><el-empty v-else description="在右侧选择图片" :image-size="56"/></div>
    <div v-else-if="componentType==='kpi'" class="kpi-content"><span class="kpi-label">{{ kpiMetricLabel }}</span><strong :style="{color:kpiColor}">{{ kpiValue }}</strong><span class="kpi-caption">{{ payload?'当前筛选与权限范围内的聚合值':'点击预览加载数据' }}</span></div>
    <ChartPreview v-else :thumbnail="thumbnail" :result="payload?.data || null" :definition="definition" :labels="labels" :appearance="appearance" :number-format="panelStyle?.numberFormat" :metric-styles="panelStyle?.metrics" :linkable="linkable" :drillable="!!drillFields?.length" :can-drill-down="payload?.presentation?.canDrillDown" @select="v=>emit('select',v)" @drill="p=>emit('drill',p)"/>
  </div>
</template>
<script setup lang="ts">
import type { PanelResult, PanelStyle, DashboardAppearance, ComponentType, PanelContent, DrillPathSegment } from '@/api/bi/dashboard'
import type { ChartDefinition } from '@/api/bi/chart'
import ChartPreview from '../chart/ChartPreview.vue'
import {formatMetric,metricFormat,metricColor,normalizeDecoration,normalizeContainer} from '@/utils/biPresentation'
import { drillPathFromPayload } from '@/utils/biDrill'
import { computed } from 'vue'
const props=defineProps<{thumbnail?:boolean;title:string;payload?:PanelResult;error?:string;loading?:boolean;editable?:boolean;disabled?:boolean;panelStyle?:PanelStyle;appearance?:DashboardAppearance;linkable?:boolean;drillFields?:string[];drillPath?:DrillPathSegment[];componentType?:ComponentType;content?:PanelContent}>()
const drillFields=computed(()=>props.drillFields?.length?props.drillFields:props.payload?.presentation?.drillFields)
const drillPath=computed(()=>props.drillPath?.length?props.drillPath:drillPathFromPayload(props.payload))
const emit=defineEmits<{(e:'refresh'):void;(e:'remove'):void;(e:'select',value:string|number|null):void;(e:'drill',payload:{field:string;value:string|number|null}):void;(e:'drill-up',level:number):void}>()
// Presentation aliases are display-only. This generated model is never submitted for querying.
const definition=computed<ChartDefinition>(()=>{
  const p=props.payload?.presentation
  const dims=p?.queryDimensions?.length?p.queryDimensions:(p?.dimensions||[]).map(f=>({field:f,alias:f}))
  return {schemaVersion:1,name:props.title,type:p?.type || 'table',query:{datasetId:0,dimensions:dims,metrics:(p?.metrics||[]).map(f=>({field:f,alias:f,agg:'NONE'}))},advanced:p?.pivotRowFields?{pivotRowFields:p.pivotRowFields,pivotColFields:p.pivotColFields||[]}:undefined}
})
const container=computed(()=>props.componentType==='container')
const containerStyle=computed(()=>{let c:ReturnType<typeof normalizeContainer>;try{c=normalizeContainer(props.content?.container)}catch{c=normalizeContainer()}return {inset:'0',background:c.background,border:`${c.stroke}px solid ${c.borderColor}`,borderRadius:c.radius+'px'}})
const decoration=computed(()=>props.componentType==='border' || props.componentType==='line')
const decorationStyle=computed(()=>{if(!decoration.value)return {};let d:ReturnType<typeof normalizeDecoration>;try{d=normalizeDecoration(props.content?.decoration)}catch{d=normalizeDecoration()}const stroke=`${d.stroke}px ${d.pattern} ${d.color}`;return props.componentType==='border'?{inset:'0',border:stroke,borderRadius:d.radius+'px'}:d.direction==='vertical'?{left:'50%',top:'0',height:'100%',borderLeft:stroke}:{top:'50%',left:'0',width:'100%',borderTop:stroke}})
const kpiValue=computed(()=>{const key=props.payload?.presentation.metrics[0],value=key?props.payload?.data.rows[0]?.[key]:null;return formatMetric(value,props.appearance,metricFormat(props.panelStyle,key || ''))})
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
<style scoped>.drill-crumb{display:flex;flex-wrap:wrap;align-items:center;gap:4px;padding:6px 12px 0;font-size:12px;color:var(--bi-text,#606266)}.crumb{border:0;background:transparent;color:var(--el-color-primary);cursor:pointer;padding:0}.crumb:hover{text-decoration:underline}.sep{opacity:.5}.decoration-body{position:absolute;inset:0;padding:0!important;min-height:0!important;overflow:hidden!important}.decoration-shape{position:absolute;box-sizing:border-box;pointer-events:none}.decoration-header{position:absolute;top:2px;left:4px;right:4px;z-index:2;padding:2px 5px!important;background:#172c4dcc!important;border:0!important;color:#e5ecff!important}.decoration-header strong{font-size:11px!important}.decoration-header :deep(.el-button){font-size:10px}.decoration-line{box-sizing:border-box}.panel-header{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:10px 12px;border-bottom:1px solid var(--bi-border,#e4e7ed);background:var(--bi-card,#fafbfd);color:var(--bi-text,#303133)}.panel-header strong{font-size:var(--bi-title-size,14px);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1;min-width:0}.panel-handle{cursor:move;touch-action:none}.panel-body{padding:10px;min-height:120px}.hidden-title{font-size:12px;font-weight:normal;opacity:.75}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}.static-text{white-space:pre-wrap;overflow-wrap:anywhere;color:var(--bi-text);line-height:1.8;font-size:15px}.static-image{height:100%;min-height:120px;display:flex;align-items:center;justify-content:center}.static-image img{max-width:100%;max-height:480px;object-fit:contain}.kpi-content{container-type:inline-size;display:flex;min-height:160px;flex-direction:column;justify-content:center;gap:12px;padding:12px;color:var(--bi-text)}.kpi-content strong{font-size:clamp(28px,4vw,46px);font-size:clamp(20px,11cqw,46px);font-variant-numeric:tabular-nums;overflow-wrap:anywhere;line-height:1.2}.kpi-label,.kpi-caption{opacity:.65;font-size:12px}</style>
