<template>
  <el-empty v-if="!result" :description="thumbnail ? '暂无可预览数据' : '配置字段和筛选条件后，点击预览'"/>
  <el-empty v-else-if="!result.rows.length" description="当前筛选与权限范围内没有数据"/>
  <template v-else>
    <el-alert v-if="renderError" :title="renderError" type="warning" :closable="false" class="mb8"/>
    <div v-show="type!=='table' && type!=='pivot' && !renderError" ref="host" class="chart-canvas" :class="{'chart-canvas--gauge':type==='gauge','chart-canvas--map':type==='map'}" role="img" :aria-label="'图表预览，共 '+result.rowCount+' 行'" />
    <div v-show="type==='pivot' && !renderError" ref="pivotHost" class="pivot-canvas" role="grid" :aria-label="'透视表，共 '+result.rowCount+' 行'"/>
    <el-table v-if="type==='table' || type!=='pivot' && !thumbnail" :data="result.rows" border max-height="420" size="small"><el-table-column v-if="linkable || drillable" label="操作" width="88"><template #default="{row}"><el-button v-if="drillable && canDrillDown" link type="primary" @click="onChartPick(row)">下钻</el-button><el-button v-else-if="linkable" link type="primary" @click="selectRow(row)">选择</el-button></template></el-table-column><el-table-column v-for="column in result.columns" :key="column" :prop="column" :label="labels[column] || column" min-width="140" show-overflow-tooltip><template #default="{row}"><span :style="{color:metricKeys.has(column)?metricColor(row[column],column,metricStyles):undefined}">{{ metricKeys.has(column) && formatFor(column)?formatMetric(row[column],appearance,formatFor(column)):displayNumber(row[column],appearance) }}</span></template></el-table-column></el-table>
    <p v-if="!thumbnail" class="result-note">{{ result.rowCount }} 行 · {{ result.costMs }} ms · 图表与明细使用当前用户的数据权限</p>
  </template>
</template>
<script setup lang="ts">
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart, ScatterChart, RadarChart, FunnelChart, GaugeChart, MapChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, GeoComponent, VisualMapComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ChartResult, ChartDefinition } from '@/api/bi/chart'
import type { DashboardAppearance, NumberFormat, MetricStyle } from '@/api/bi/dashboard'
import { displayNumber, formatMetric, metricColor } from '@/utils/biPresentation'
import { buildChartPreviewOption, metricResultKey } from '@/utils/biEchartsPreview'
import { buildPivotDataConfig, validatePivotConfig } from '@/utils/biPivotPreview'
import { buildMapPreviewOption, validateMapConfig } from '@/utils/biMapPreview'
import { defaultMapScope, ensureMapRegistered, type MapScopeId } from '@/utils/biMapGeo'
import { PivotSheet } from '@antv/s2'
echarts.use([BarChart,LineChart,PieChart,ScatterChart,RadarChart,FunnelChart,GaugeChart,MapChart,GridComponent,TooltipComponent,LegendComponent,GeoComponent,VisualMapComponent,TitleComponent,CanvasRenderer])
const props=defineProps<{thumbnail?:boolean;result:ChartResult|null;definition:ChartDefinition;labels:Record<string,string>;appearance?:DashboardAppearance;numberFormat?:NumberFormat|null;metricStyles?:MetricStyle[];linkable?:boolean;drillable?:boolean;canDrillDown?:boolean}>()
const emit=defineEmits<{(e:'select',value:string|number|null):void;(e:'drill',payload:{field:string;value:string|number|null}):void}>()
function dimensionValue(row:Record<string,unknown>){const d=props.definition.query.dimensions[0];if(!d)return undefined;const v=row[d.alias || d.field];if(v===null)return null;if(typeof v==='string' || (typeof v==='number' && Number.isFinite(v) && Math.abs(v)<=Number.MAX_SAFE_INTEGER))return v;return undefined}
function selectRow(row:Record<string,unknown>){const v=dimensionValue(row);if(v===undefined)return;if(props.linkable)emit('select',v)}
async function onChartPick(row:Record<string,unknown>){const d=props.definition.query.dimensions[0];if(!d)return;const v=dimensionValue(row);if(v===undefined)return;const drill=props.drillable && props.canDrillDown;const link=props.linkable;if(drill && link){try{const {ElMessageBox}=await import('element-plus');const action=await ElMessageBox.confirm('选择交互方式','图表交互',{confirmButtonText:'下钻',cancelButtonText:'联动',distinguishCancelAndClose:true,type:'info'});if(action==='confirm')emit('drill',{field:d.field,value:v});else if(action==='cancel')emit('select',v)}catch{/* closed */}return}if(drill)emit('drill',{field:d.field,value:v});else if(link)emit('select',v)}
const host=ref<HTMLElement>(),pivotHost=ref<HTMLElement>(),renderError=ref('')
const metricKeys=computed(()=>new Set(props.definition.query.metrics.map(metricResultKey)))
function formatFor(key:string){return props.metricStyles?.find(m=>m.key===key)?.format || props.numberFormat}
const type=computed(()=>props.definition.type)
let renderVersion=0
let instance:echarts.ECharts|undefined,pivotSheet:PivotSheet|undefined,observer:ResizeObserver|undefined
function disposePivot(){pivotSheet?.destroy();pivotSheet=undefined}
function dispose(){observer?.disconnect();observer=undefined;instance?.dispose();instance=undefined;disposePivot()}
watch(()=>[props.result,props.definition.type,JSON.stringify(props.definition.query.metrics),JSON.stringify(props.definition.query.dimensions),JSON.stringify(props.definition.advanced),JSON.stringify(props.appearance),JSON.stringify(props.numberFormat),JSON.stringify(props.metricStyles)],async()=>{
  const v=++renderVersion;dispose();renderError.value='';await nextTick();if(v!==renderVersion)return
  if(!props.result?.rows.length || type.value==='table')return
  if(type.value==='pivot'){
    const pivotErr=validatePivotConfig(props.definition)
    if(pivotErr){renderError.value=pivotErr;return}
    if(!pivotHost.value)return
    try{
      const cfg=buildPivotDataConfig(props.definition,props.result)
      pivotSheet=new PivotSheet(pivotHost.value,cfg,{width:pivotHost.value.clientWidth,height:420,interaction:{hoverHighlight:true}})
      pivotSheet.render()
      observer=new ResizeObserver(()=>{if(!pivotHost.value || !pivotSheet)return;pivotSheet.changeSheetSize(pivotHost.value.clientWidth,420);pivotSheet.render(false)})
      observer.observe(pivotHost.value)
    }catch(error){renderError.value=error instanceof Error?error.message:'透视表渲染失败';disposePivot()}
    return
  }
  if(!host.value)return
  try {
    if(type.value==='map'){
      const mapErr=validateMapConfig(props.definition)
      if(mapErr){renderError.value=mapErr;return}
      const scope=(props.definition.advanced?.mapScope || defaultMapScope()) as MapScopeId
      const mapName=await ensureMapRegistered(scope)
      if(v!==renderVersion)return
      const option=buildMapPreviewOption({mapName,definition:props.definition,result:props.result,labels:props.labels,appearance:props.appearance,numberFormat:props.numberFormat,metricStyles:props.metricStyles,formatFor})
      instance=echarts.init(host.value)
      instance.on('click',(event:any)=>{if(event.name && (props.linkable || props.drillable))onChartPick({[props.definition.query.dimensions[0]?.alias || props.definition.query.dimensions[0]?.field || '']:event.name})})
      instance.setOption(option)
    }else{
    const option=buildChartPreviewOption({type:type.value,definition:props.definition,result:props.result,labels:props.labels,appearance:props.appearance,numberFormat:props.numberFormat,metricStyles:props.metricStyles,formatFor})
    instance=echarts.init(host.value)
    instance.on('click',(event:any)=>{if((props.linkable || props.drillable) && event.componentType==='series' && Number.isInteger(event.dataIndex)){const row=props.result?.rows[event.dataIndex];if(row)onChartPick(row)}})
    instance.setOption(option)
    }
    observer=new ResizeObserver(()=>instance?.resize());observer.observe(host.value)
  }catch(error){renderError.value=error instanceof Error?error.message:'绘图失败，请查看明细';dispose()}
},{immediate:true})
onBeforeUnmount(()=>{++renderVersion;dispose()})
</script>
<style scoped>.chart-canvas{width:100%;height:340px}.chart-canvas--gauge{height:280px}.chart-canvas--map{height:420px}.pivot-canvas{width:100%;height:420px;overflow:hidden}.result-note{color:var(--bi-text,#606266);font-size:12px;opacity:.8}</style>
