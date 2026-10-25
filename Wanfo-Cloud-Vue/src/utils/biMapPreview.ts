import {resolveBiTheme,BI_TYPE} from '@/utils/biTheme'
import type { EChartsOption } from 'echarts'
import type { ChartDefinition, ChartResult, ChartVisualStyle } from '@/api/bi/chart'
import type { DashboardAppearance, NumberFormat, MetricStyle } from '@/api/bi/dashboard'
import { chartAppearance, paletteFromStyle } from '@/utils/biChartPresentation'
import { displayNumber, formatMetric } from '@/utils/biPresentation'
import { defaultMapScope, normalizeProvinceName, type MapScopeId } from '@/utils/biMapGeo'
import { metricResultKey } from '@/utils/biEchartsPreview'
import type { ChartType } from '@/utils/biChartTypes'
import {mapScopeCode,matchGeoFeature,type GeoFeature} from './biMapRegions'

export function validateMapConfig(definition: ChartDefinition): string | null {
  if (definition.type !== 'map') return null
  const dims = definition.query.dimensions
  const metrics = definition.query.metrics
  if (dims.length !== 1 || metrics.length !== 1) return '行政区地图须选择一个地区维度和一个度量'
  const scope = definition.advanced?.mapScope || defaultMapScope()
  try{mapScopeCode(scope)}catch{return '不支持的地图范围'}
  if(styleMapValue(definition.style?.mapBaseStyle)===false)return '不支持的地图底图主题'
  if(definition.style?.mapProjection && !['raw','mercator'].includes(definition.style.mapProjection))return '不支持的地图坐标投影'
  if(definition.advanced?.mapValueMode && !['name','code'].includes(definition.advanced.mapValueMode))return '请选择地区名称或行政区编码'
  const style=definition.style
  if(style?.mapPieceColors?.length && (!style.mapBreaks?.length || style.mapPieceColors.length!==style.mapBreaks.length+1 || style.mapPieceColors.length>6 || style.mapPieceColors.some(c=>c!=null && !/^#[0-9a-fA-F]{6}$/.test(c))))return '逐段颜色须与分段数量一致，并使用六位十六进制颜色'
  if(style?.mapColorMode==='piecewise' && (!style.mapBreaks?.length || style.mapBreaks.length>5 || style.mapBreaks.some((v,i,a)=>!Number.isFinite(v) || Math.abs(v)>Number.MAX_SAFE_INTEGER || i>0 && v<=a[i-1]!)))return '地图分段须填写 1—5 个严格递增的原始数值边界'
  return null
}
function styleMapValue(value:string|undefined):boolean{return value===undefined||['light','dark','outline'].includes(value)}

export function mapMatchWarning(definition:ChartDefinition,result:ChartResult,features:GeoFeature[]):string {
  if(!result.rows.length)return '当前筛选与权限范围内没有本层数据，显示区域边界'
  const dimension=definition.query.dimensions[0];const key=dimension.alias || dimension.field
  const unmatched=result.rows.filter(r=>!matchGeoFeature(features,r[key],definition.advanced?.mapValueMode || 'name'))
  return unmatched.length?`${unmatched.length} 个地区未匹配当前边界：${unmatched.slice(0,5).map(r=>String(r[key]??'空值').slice(0,40)).join('、')}。请核对层级、编码或完整名称。`:''
}


export function mapPieceColor(style:ChartVisualStyle|undefined,index:number):string {
 const low=style?.mapLowColor || '#e0f3ff',high=style?.mapHighColor || '#006edd',t=index/Math.max(1,style?.mapBreaks?.length || 0)
 return style?.mapPieceColors?.[index] || '#'+[0,2,4].map(i=>Math.round(parseInt(low.slice(1+i,3+i),16)*(1-t)+parseInt(high.slice(1+i,3+i),16)*t).toString(16).padStart(2,'0')).join('')
}
export function mapDrillNames(definition:ChartDefinition,result:ChartResult,features:GeoFeature[]):string[] {
 if(!result.mapContext?.canDrillDown)return []
 const d=definition.query.dimensions[0],key=d?.alias || d?.field;if(!key)return []
 const allowed=new Set(result.mapContext.drillableRegions),metric=definition.query.metrics[0];if(!metric)return [];const valueKey=metricResultKey(metric)
 return [...new Set(result.rows.map(row=>{if(readNumber(row[valueKey])==null && definition.advanced?.nullMode!=='zero')return undefined;const f=matchGeoFeature(features,row[key],definition.advanced?.mapValueMode || 'name');return f && allowed.has(String(f.adcode))?f.name:undefined}).filter((name):name is string=>!!name))]
}

function readNumber(raw: unknown): number | null {
  if (raw == null) return null
  const value = Number(raw)
  if (!Number.isFinite(value) || Math.abs(value) > Number.MAX_SAFE_INTEGER) return null
  return value
}

export function buildMapPreviewOption(params: {
  mapName: string
  definition: ChartDefinition
  result: ChartResult
  labels: Record<string, string>
  appearance?: DashboardAppearance
  numberFormat?: NumberFormat | null
  metricStyles?: MetricStyle[]
  formatFor: (key: string) => NumberFormat | null | undefined
  geoFeatures?:GeoFeature[]
  interaction?:{enabled:boolean;names:string[]}
}): EChartsOption {
  const { mapName, definition, result, labels, appearance, numberFormat, formatFor } = params
  const style = definition.style
  const baseStyle=style?.mapBaseStyle || 'light',darkBase=baseStyle==='dark',outlineBase=baseStyle==='outline'
  const mergedAppearance = chartAppearance(style, appearance)
  const visualTheme=resolveBiTheme(mergedAppearance)
  const textColor = visualTheme.text
  const dimField = definition.query.dimensions[0]?.alias || definition.query.dimensions[0]?.field
  const metric = definition.query.metrics[0]
  const key = metricResultKey(metric)
  const nullMode = definition.advanced?.nullMode || 'keep'

  const data: { name: string; value: number;rawRegion:unknown }[] = []
  const used=new Set<string>()
  for (const row of result.rows) {
    const match=params.geoFeatures?matchGeoFeature(params.geoFeatures,row[dimField!],definition.advanced?.mapValueMode || 'name'):undefined
    const name = params.geoFeatures?match?.name:normalizeProvinceName(String(row[dimField!] ?? ''))
    if (!name) continue
    const raw = row[key]
    let value = readNumber(raw)
    if (value == null) {
      if (nullMode === 'zero') value = 0
      else if (nullMode === 'hide') continue
      else continue
    }
    if(used.has(name))throw new Error('同一地区匹配多条结果，请统一地区编码或名称后重新聚合')
    used.add(name);data.push({ name, value,rawRegion:row[dimField!] })
  }

  const values = data.map(d => d.value)
  const min = values.length?Math.min(...values):0
  const max = Math.max(...values, min + 1)
  const axisFormatter = (value: number) =>
    String(formatMetric(value,appearance,formatFor(key) || style?.metrics?.find(m=>m.key===key)?.format || numberFormat))

  const breaks=style?.mapBreaks || []
  const low=style?.mapLowColor || '#e0f3ff',high=style?.mapHighColor || '#006edd'
  const pieces=breaks.length?Array.from({length:breaks.length+1},(_,i)=>({...(i?{gte:breaks[i-1]}:{}),...(i<breaks.length?{lt:breaks[i]}:{}),label:i===0?`< ${axisFormatter(breaks[0]!)}`:i===breaks.length?`≥ ${axisFormatter(breaks[i-1]!)}`:`${axisFormatter(breaks[i-1]!)} ≤ 值 < ${axisFormatter(breaks[i]!)}`,color:mapPieceColor(style,i)})):[]
  const labelFormatter=(p:unknown)=>{const item=p as {name?:string;value?:number};const value=item.value==null || !Number.isFinite(item.value)?'无数据':axisFormatter(item.value);return style?.mapLabelContent==='value'?value:style?.mapLabelContent==='nameValue'?`${item.name || ''}\n${value}`:item.name || ''}
  const noDataColor=style?.mapNoDataColor || (darkBase?'#273244':'#f4f6f9')
  const borderColor=style?.mapBorderColor || (darkBase?'#748198':outlineBase?'#8b98a9':'#c2cad5')
  const projection=style?.mapProjection==='mercator'?{project:(point:number[])=>{const latitude=Math.max(-85.05112878,Math.min(85.05112878,point[1]));const rad=latitude*Math.PI/180;return [point[0],Math.log(Math.tan(Math.PI/4+rad/2))*180/Math.PI]},unproject:(point:number[])=>[point[0],(2*Math.atan(Math.exp(point[1]*Math.PI/180))-Math.PI/2)*180/Math.PI]}:undefined
  return {
    backgroundColor:darkBase?'#151b26':undefined,
    color: paletteFromStyle(style, mergedAppearance),
    textStyle: { color: textColor,fontFamily:visualTheme.fontFamily,fontSize:BI_TYPE.chart },
    title: style?.showTitle ? { text: style.title || definition.name, left: 'center', textStyle: { color: textColor,fontFamily:visualTheme.fontFamily,fontSize:BI_TYPE.title,fontWeight:600 } } : undefined,
    tooltip: {
      show:style?.mapShowTooltip!==false,
      trigger: 'item',
      renderMode: 'richText',
      textStyle:{fontFamily:visualTheme.fontFamily,fontSize:BI_TYPE.chart},padding:12,
      formatter: (p) => {
        const item = p as { name?: string; value?: number }
        const hint=!params.interaction?.enabled?'':item.value==null || !Number.isFinite(item.value)?'当前无数据，无法下钻':params.interaction.names.includes(item.name || '')?'点击下钻':'暂无可用下级'
        return `${item.name ?? ''}\n${labels[key] || labels[metric.field] || key}: ${item.value==null || !Number.isFinite(item.value)?'无数据':axisFormatter(item.value)}${hint?'\n'+hint:''}`
      }
    },
    visualMap: {
      type:style?.mapColorMode==='piecewise'?'piecewise':'continuous',
      show:style?.showLegend!==false,
      ...(style?.mapColorMode==='piecewise'?{pieces}:{}),
      min,
      max,
      left: 16,
      bottom: 24,
      ...(style?.mapColorMode==='piecewise'?{}:{text:['高','低'],calculable:true,precision:2,formatter:(value:unknown)=>Number.isFinite(Number(value))?axisFormatter(Number(value)):String(value)}),
      inRange: { color: [low, high] },
      textStyle: { color: textColor,fontFamily:visualTheme.fontFamily,fontSize:BI_TYPE.chart }
    },
    geo:{map:mapName,roam:true,zoom:1,scaleLimit:{min:0.5,max:8},projection,itemStyle:{areaColor:noDataColor,borderColor},label:{show:!!style?.mapShowLabels,formatter:(p:unknown)=>{const v=(p as {value?:number}).value;return v==null || !Number.isFinite(v)?'':labelFormatter(p)},color:darkBase?'#e6edf7':textColor,fontSize:11},emphasis:{label:{show:true,formatter:labelFormatter}}},
    series: [{
      type: 'map',
      map: mapName,
      geoIndex:0,
      roam:true,
      zoom:1,
      scaleLimit:{min:0.5,max:8},
      itemStyle:{areaColor:noDataColor,borderColor},
      label:{show:!!style?.mapShowLabels,formatter:labelFormatter,color:darkBase?'#e6edf7':textColor,fontSize:11},
      emphasis:{label:{show:true,formatter:labelFormatter}},
      labelLayout:{hideOverlap:true},
      data
    }]
  }
}

export function isMapType(type: ChartType): boolean {
  return type === 'map'
}
