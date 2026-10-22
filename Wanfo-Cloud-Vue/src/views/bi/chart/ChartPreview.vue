<template>
  <div v-if="type==='map' && loading && !result && !thumbnail" class="map-loading" role="status" aria-live="polite" aria-busy="true">正在加载地图数据…</div>
  <el-empty v-else-if="!result" :description="thumbnail ? '暂无可预览数据' : '配置字段和筛选条件后，点击预览'"/>
  <el-empty v-else-if="!result.rows.length && type!=='map'" description="查询成功，当前筛选与权限范围内没有数据；请检查筛选范围"/>
  <template v-else>
    <el-alert v-if="!result.rows.length && !thumbnail" title="查询成功，当前筛选与权限范围内没有数据；请检查筛选范围" type="info" :closable="false"/>
    <div v-if="type==='map' && !thumbnail" class="map-toolbar" :aria-busy="mapBusy"><span role="status" aria-live="polite">{{ mapStatus }}</span><el-button size="small" :disabled="mapBusy || !!renderError" title="恢复当前层级初始视图，不改变下钻路径和查询条件" @click="resetMapView">恢复视图</el-button></div>
    <el-alert v-if="type==='map' && mapWarning" :title="mapWarning" type="info" :closable="false" class="mb8"/>
    <el-alert v-if="renderError" :title="renderError" type="warning" :closable="false" class="mb8"/>
    <div v-show="type!=='table' && type!=='pivot' && !renderError" ref="host" class="chart-canvas" :class="{'chart-canvas--gauge':type==='gauge','chart-canvas--map':type==='map'}" role="img" :aria-label="'图表预览，共 '+result.rowCount+' 行'" />
    <el-alert v-if="type==='pivot' && !result.pivotTotals && pivotTotalsRequested(definition)" title="小计与总计仅基于当前查询已加载的分组结果；达到行数上限时可能不完整。SUM/COUNT累加，MIN/MAX取极值。" type="info" :closable="false" class="mb8"/>
    <div v-show="type==='pivot' && !renderError" ref="pivotHost" class="pivot-canvas" role="grid" :aria-label="'透视表，共 '+result.rowCount+' 行'"/>
    <ChartDataTable v-if="type==='table'" :definition="definition" :result="result" :labels="labels" :appearance="appearance" :number-format="numberFormat" :metric-styles="metricStyles" :linkable="linkable" :drillable="drillable" :can-drill-down="canDrillDown" @select="selectRow" @drill="onChartPick"/>
    <el-table v-if="type!=='table' && type!=='pivot' && !thumbnail" :data="result.rows" border max-height="420" size="small"><el-table-column v-if="linkable || drillable && canDrillDown" label="操作" width="88"><template #default="{row}"><el-button v-if="drillable && canDrillDown" link type="primary" :disabled="type==='map' && !mapRowDrillable(row)" @click="onChartPick(row)">下钻</el-button><el-button v-else-if="linkable" link type="primary" @click="selectRow(row)">选择</el-button></template></el-table-column><el-table-column v-if="type==='map' && definition.advanced?.mapValueMode==='code'" label="地区名称" min-width="120"><template #default="{row}">{{ mapRegionName(row) }}</template></el-table-column><el-table-column v-for="column in result.columns" :key="column" :prop="column" :label="displayLabels[column] || column" min-width="140" show-overflow-tooltip><template #default="{row}"><span :style="{color:metricKeys.has(column)?metricColor(row[column],column,effectiveMetricStyles):undefined}">{{ comparisonRate(result,column,row[column]) ?? (metricKeys.has(column) && formatFor(column)?formatMetric(row[column],appearance,formatFor(column)):displayNumber(row[column],appearance)) }}</span></template></el-table-column></el-table>
    <p v-if="!thumbnail && result.timeComparison" class="result-note" role="status">{{ result.timeComparison.label }}：{{ result.timeComparison.status==='skipped'?result.timeComparison.reason:result.timeComparison.note }}</p>
    <p v-if="!thumbnail && result.pivotTotals" class="result-note" role="status">{{ result.pivotTotals.status==='unavailable'?result.pivotTotals.reason:'小计/总计口径：当前筛选和权限范围内的全部数据' }}<span v-if="result.pivotTotals.detailTruncated"> · 明细分组已达展示上限，汇总包含未展示数据</span></p>
    <p v-if="!thumbnail" class="result-note">{{ result.rowCount }} 行 · {{ result.costMs }} ms · 图表与明细使用当前用户的数据权限<span v-if="result.timeFill"> · {{ result.timeFill.status==='skipped'?'时间补齐已跳过：'+result.timeFill.reason:'实际 '+result.timeFill.sourceRows+' 行，补'+(result.timeFill.mode==='zero'?'零':'空值')+' '+result.timeFill.addedRows+' 个周期' }}</span></p>
  </template>
</template>
<script setup lang="ts">
import ChartDataTable from './ChartDataTable.vue'
import {comparisonDefinition,comparisonLabels,comparisonRate} from '@/utils/biTimeComparison'
import {resolveChartMetricFormat,resolvedMetricStyles} from '@/utils/biChartPresentation'
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart, ScatterChart, RadarChart, FunnelChart, GaugeChart, MapChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, GeoComponent, VisualMapComponent, TitleComponent, GraphicComponent, MarkLineComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ChartResult, ChartDefinition } from '@/api/bi/chart'
import type { DashboardAppearance, NumberFormat, MetricStyle } from '@/api/bi/dashboard'
import { displayNumber, formatMetric, metricColor } from '@/utils/biPresentation'
import { buildChartPreviewOption, metricResultKey } from '@/utils/biEchartsPreview'
import { buildPivotDataConfig, validatePivotConfig,buildPivotOptions,pivotTheme,pivotTotalsRequested } from '@/utils/biPivotPreview'
import { buildMapPreviewOption, validateMapConfig,mapMatchWarning,mapDrillNames } from '@/utils/biMapPreview'
import { defaultMapScope, ensureMapRegistered,registeredGeoFeatures, type MapScopeId } from '@/utils/biMapGeo'
import { PivotSheet } from '@antv/s2'
import {matchGeoFeature,type GeoFeature} from '@/utils/biMapRegions'
echarts.use([BarChart,LineChart,PieChart,ScatterChart,RadarChart,FunnelChart,GaugeChart,MapChart,GridComponent,TooltipComponent,LegendComponent,GeoComponent,VisualMapComponent,TitleComponent,GraphicComponent,MarkLineComponent,CanvasRenderer])
const props=defineProps<{thumbnail?:boolean;loading?:boolean;result:ChartResult|null;definition:ChartDefinition;labels:Record<string,string>;appearance?:DashboardAppearance;numberFormat?:NumberFormat|null;metricStyles?:MetricStyle[];linkable?:boolean;drillable?:boolean;canDrillDown?:boolean}>()
const displayDefinition=computed<ChartDefinition>(()=>{const context=props.result?.mapContext;if(props.definition.type!=='map' || !context)return props.definition;return {...props.definition,advanced:{...props.definition.advanced,mapScope:context.scope},query:{...props.definition.query,dimensions:[{field:context.field,alias:context.field}]}}})
const displayLabels=computed(()=>{const labels=comparisonLabels(props.result,props.labels),context=props.result?.mapContext;return context?{...labels,[context.field]:context.fieldLabel || labels[context.field] || context.field}:labels})
const emit=defineEmits<{(e:'select',value:string|number|null):void;(e:'drill',payload:{field:string;value:string|number|null}):void}>()
function dimensionValue(row:Record<string,unknown>){const d=displayDefinition.value.query.dimensions[0];if(!d)return undefined;const v=row[d.alias || d.field];if(v===null)return null;if(typeof v==='string' || (typeof v==='number' && Number.isFinite(v) && Math.abs(v)<=Number.MAX_SAFE_INTEGER))return v;return undefined}
function selectRow(row:Record<string,unknown>){const v=dimensionValue(row);if(v===undefined)return;if(props.linkable)emit('select',v)}
async function onChartPick(row:Record<string,unknown>){if(props.definition.type==='map' && mapBusy.value)return;if(props.definition.type==='map' && props.drillable && props.canDrillDown && !mapRowDrillable(row)){mapWarning.value='该地区暂无可用下级边界或未绑定下级字段';return;}const d=displayDefinition.value.query.dimensions[0];if(!d)return;const v=dimensionValue(row);if(v===undefined)return;const drill=props.drillable && props.canDrillDown;const link=props.linkable;if(drill && link){try{const {ElMessageBox}=await import('element-plus');const action=await ElMessageBox.confirm('选择交互方式','图表交互',{confirmButtonText:'下钻',cancelButtonText:'联动',distinguishCancelAndClose:true,type:'info'});if(action==='confirm')emit('drill',{field:d.field,value:v});else if(action==='cancel')emit('select',v)}catch{/* closed */}return}if(drill)emit('drill',{field:d.field,value:v});else if(link)emit('select',v)}
const visibleGeoFeatures=ref<GeoFeature[]>([])
function mapRegionName(row:Record<string,unknown>){const d=displayDefinition.value.query.dimensions[0];return matchGeoFeature(visibleGeoFeatures.value,row[d.alias || d.field],'code')?.name || '未匹配地区'}
function mapRowDrillable(row:Record<string,unknown>){const dim=displayDefinition.value.query.dimensions[0];const feature=matchGeoFeature(visibleGeoFeatures.value,row[dim.alias || dim.field],props.definition.advanced?.mapValueMode || 'name');return !!feature && !!props.result?.mapContext?.drillableRegions.includes(String(feature.adcode))}
const host=ref<HTMLElement>(),pivotHost=ref<HTMLElement>(),renderError=ref(''),mapWarning=ref(''),mapLoading=ref(false),mapNotice=ref('')
const mapBusy=computed(()=>!!props.loading || mapLoading.value)
const drillNames=computed(()=>props.result?mapDrillNames(displayDefinition.value,props.result,visibleGeoFeatures.value):[])
const mapStatus=computed(()=>mapBusy.value?(props.loading?'正在加载地图数据…':'正在加载地区边界…'):(mapNotice.value?mapNotice.value+'；':'')+(props.drillable?(props.canDrillDown && drillNames.value.length?'点击有数据的地区下钻（'+drillNames.value.length+'个可用地区）':'当前有数据地区暂无可用下级')+'；':'')+'可拖动、滚轮缩放')
function resetMapView(){if(mapBusy.value || !instance)return;instance.dispatchAction({type:'restore'});mapNotice.value='已恢复当前层级视图'}
const metricKeys=computed(()=>new Set([...props.definition.query.metrics.map(metricResultKey),...(props.result?.timeComparison?.fields.flatMap(f=>[f.baseline,f.difference,f.rate])||[])]))
const effectiveMetricStyles=computed(()=>resolvedMetricStyles(props.definition.style,props.metricStyles))
function formatFor(key:string){const original=props.result?.timeComparison?.fields.find(f=>f.baseline===key || f.difference===key)?.key || key;return resolveChartMetricFormat(props.definition.style,original,props.metricStyles,props.numberFormat)}
const type=computed(()=>props.definition.type)
let renderVersion=0
let instance:echarts.ECharts|undefined,pivotSheet:PivotSheet|undefined,observer:ResizeObserver|undefined
function disposePivot(){pivotSheet?.destroy();pivotSheet=undefined}
function dispose(){observer?.disconnect();observer=undefined;instance?.dispose();instance=undefined;disposePivot()}
watch(()=>[props.result,props.definition.type,props.definition.name,JSON.stringify(props.definition.style),JSON.stringify(props.definition.query.metrics),JSON.stringify(props.definition.query.dimensions),JSON.stringify(props.definition.advanced),JSON.stringify(props.appearance),JSON.stringify(props.numberFormat),JSON.stringify(props.metricStyles)],async()=>{
  const v=++renderVersion;dispose();renderError.value='';mapWarning.value='';mapLoading.value=false;mapNotice.value='';visibleGeoFeatures.value=[];await nextTick();if(v!==renderVersion)return
  if(!props.result || !props.result.rows.length && type.value!=='map' || type.value==='table')return
  if(type.value==='pivot'){
    const pivotErr=validatePivotConfig(props.definition)
    if(pivotErr){renderError.value=pivotErr;return}
    if(!pivotHost.value)return
    try{
      const cfg=buildPivotDataConfig(props.definition,props.result,(key,value)=>String(formatMetric(value,props.appearance,formatFor(key))),props.labels)
      pivotSheet=new PivotSheet(pivotHost.value,cfg,buildPivotOptions(props.definition,props.result,pivotHost.value.clientWidth,effectiveMetricStyles.value))
      pivotSheet.setThemeCfg(pivotTheme(props.definition,props.appearance))
      await pivotSheet.render()
      if(v!==renderVersion)return
      observer=new ResizeObserver(()=>{if(!pivotHost.value || !pivotSheet)return;pivotSheet.changeSheetSize(pivotHost.value.clientWidth,420);void pivotSheet.render(false).catch(error=>{if(v===renderVersion){renderError.value=error instanceof Error?error.message:'透视表渲染失败';disposePivot()}})})
      observer.observe(pivotHost.value)
    }catch(error){if(v!==renderVersion)return;renderError.value=error instanceof Error?error.message:'透视表渲染失败';disposePivot()}
    return
  }
  if(!host.value)return
  try {
    if(type.value==='map'){
      const mapErr=validateMapConfig(displayDefinition.value)
      if(mapErr){renderError.value=mapErr;return}
      const scope=(displayDefinition.value.advanced?.mapScope || defaultMapScope()) as MapScopeId
      let mapName:string
      mapLoading.value=true
      try{mapName=await ensureMapRegistered(scope)}finally{if(v===renderVersion)mapLoading.value=false}
      if(v!==renderVersion)return
      const regions=registeredGeoFeatures(scope);visibleGeoFeatures.value=regions
      mapWarning.value=mapMatchWarning(displayDefinition.value,props.result,regions)
      const option=buildMapPreviewOption({mapName,geoFeatures:regions,interaction:{enabled:!!props.drillable,names:drillNames.value},definition:displayDefinition.value,result:props.result,labels:displayLabels.value,appearance:props.appearance,numberFormat:props.numberFormat,metricStyles:props.metricStyles,formatFor})
      instance=echarts.init(host.value)
      instance.on('click',(event:any)=>{
        if(props.thumbnail || mapBusy.value || !event.name)return
        const feature=regions.find(r=>r.name===event.name);if(!feature)return
        const raw=event.data?.rawRegion
        if(raw===undefined){mapWarning.value='该地区没有当前权限范围内的数据，无法下钻';return}
        if(props.drillable && !props.linkable && (!props.canDrillDown || !drillNames.value.includes(event.name))){mapWarning.value='该地区暂无可用下级边界或未绑定下级字段';return}
        if(props.linkable || props.drillable)void onChartPick({[displayDefinition.value.query.dimensions[0]?.alias || displayDefinition.value.query.dimensions[0]?.field || '']:raw})
      })
      instance.on('mouseover',(event:any)=>{instance?.getZr().setCursorStyle(props.drillable && props.canDrillDown && drillNames.value.includes(event.name)?'pointer':'move')})
      instance.on('mouseout',()=>instance?.getZr().setCursorStyle('move'))
      instance.setOption(option)
    }else{
    const option=buildChartPreviewOption({type:type.value,definition:comparisonDefinition(props.definition,props.result),result:props.result,labels:displayLabels.value,appearance:props.appearance,numberFormat:props.numberFormat,metricStyles:props.metricStyles,formatFor})
    instance=echarts.init(host.value)
    instance.on('click',(event:any)=>{if((props.linkable || props.drillable) && event.componentType==='series' && Number.isInteger(event.dataIndex)){const row=props.result?.rows[event.dataIndex];if(row)onChartPick(row)}})
    instance.setOption(option)
    }
    observer=new ResizeObserver(()=>instance?.resize());observer.observe(host.value)
  }catch(error){if(v!==renderVersion)return;renderError.value=error instanceof Error?error.message:'绘图失败，请查看明细';dispose()}
},{immediate:true})
onBeforeUnmount(()=>{++renderVersion;dispose()})
</script>
<style scoped>.map-loading{height:420px;display:flex;align-items:center;justify-content:center;color:var(--bi-text,#667085);background:var(--bi-card,var(--bi-soft,#f8fafc));border-radius:8px}.map-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-bottom:8px;font-size:12px;color:var(--bi-text,#667085)}.chart-canvas{width:100%;height:340px}.chart-canvas--gauge{height:280px}.chart-canvas--map{height:420px}.pivot-canvas{width:100%;height:420px;overflow:hidden}.result-note{color:var(--bi-text,#606266);font-size:12px;opacity:.8}</style>
