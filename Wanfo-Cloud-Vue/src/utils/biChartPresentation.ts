import type { ChartAdvancedOptions, ChartDefinition, ChartVisualStyle } from '@/api/bi/chart'
import type { DashboardAppearance, NumberFormat, MetricStyle } from '@/api/bi/dashboard'
import {validateTableStyle} from './biTablePresentation'
import { chartPalette } from '@/utils/biPresentation'

export function defaultChartStyle(): ChartVisualStyle {
  return { showLegend: true, legendPosition: 'top', showTitle: false, palette: 'business', axisLabelRotate: undefined }
}

export function defaultChartAdvanced(): ChartAdvancedOptions {
  return { nullMode: 'keep', smoothLine: true, pivotRowFields: [], pivotColFields: [], mapScope: 'china-provinces', drillFields: [] }
}

export function ensureChartPresentation(definition: ChartDefinition): void {
  if (!definition.style) definition.style = defaultChartStyle()
  if (!definition.advanced) definition.advanced = defaultChartAdvanced()
}

/** Component overrides are explicit; otherwise inherit the saved chart metric. */
export function resolveChartMetricFormat(style:ChartVisualStyle|undefined,key:string,overrides?:MetricStyle[],fallback?:NumberFormat|null) {
  return overrides?.find(m=>m.key===key)?.format || fallback || style?.metrics?.find(m=>m.key===key)?.format
}

export function resolvedMetricStyles(style?:ChartVisualStyle,overrides?:MetricStyle[]):MetricStyle[] {
  const values=new Map<string,MetricStyle>((style?.metrics || []).map(m=>[m.key,{key:m.key,format:m.format,rules:m.rules || []}]))
  for(const m of overrides || [])values.set(m.key,m)
  return [...values.values()]
}

export function comboMetricAxis(style:ChartVisualStyle|undefined,key:string,index:number):'left'|'right' {
  return style?.metrics?.find(m=>m.key===key)?.axis || (index===0?'left':'right')
}

export function validateChartMetricPresentation(definition:ChartDefinition):string|null {
  const style=definition.style
  const tableError=validateTableStyle(definition);if(tableError)return tableError
  if(definition.type==='funnel' && (style?.funnelMinSize ?? 0)>=(style?.funnelMaxSize ?? 100))return '漏斗最小宽度必须小于最大宽度'
  for(const axis of Object.values(style?.comboAxes || {})) {
    if(axis.min!=null && !Number.isFinite(axis.min) || axis.max!=null && !Number.isFinite(axis.max))return '坐标轴范围须为有效数值'
    if(axis.min!=null && axis.max!=null && axis.min>=axis.max)return '坐标轴最大值必须大于最小值'
  }
  for(const metric of style?.metrics || [])for(const rule of metric.rules || [])if(!/^[+-]?[0-9]+(?:\.[0-9]+)?$/.test(rule.value))return '阈值须填写有效十进制原始数值'
  for(const line of style?.referenceLines || [])if(line.kind==='fixed' && (line.value==null || !Number.isFinite(line.value)))return '请填写参考线的原始数值'
  return null
}

export function nextChartMetricAlias(metrics:ChartDefinition['query']['metrics']):string {
  const used=new Set(metrics.map(m=>m.alias || (m.agg==='NONE'?m.field:`${m.agg.toLowerCase()}_${m.field}`)))
  let index=1;while(used.has(`value_${index}`))index++
  return `value_${index}`
}

export function retainChartMetricBindings(style:ChartVisualStyle|undefined,current:ChartDefinition['query']['metrics'],previous:ChartDefinition['query']['metrics']) {
  if(!style)return
  const key=(m:ChartDefinition['query']['metrics'][number])=>m.alias || (m.agg==='NONE'?m.field:`${m.agg.toLowerCase()}_${m.field}`)
  const valid=new Set(current.filter(m=>{const old=previous.find(p=>key(p)===key(m));return !old || old.field===m.field && old.agg===m.agg}).map(key))
  if(style.metrics)style.metrics=style.metrics.filter(m=>valid.has(m.key))
  if(style.referenceLines)style.referenceLines=style.referenceLines.filter(r=>valid.has(r.key))
}

export function chartAppearance(style?: ChartVisualStyle, fallback?: DashboardAppearance): DashboardAppearance | undefined {
  if (!style) return fallback
  if (!style.palette && !fallback) return fallback
  const base = fallback || {
    theme: 'light',
    background: '#f4f6fa',
    cardBackground: '#ffffff',
    textColor: '#303133',
    borderColor: '#e4e7ed',
    palette: 'business',
    titleSize: 14,
    radius: 8,
    decimals: 2,
    grouping: true
  }
  return { ...base, palette: style.palette || base.palette }
}

export function paletteFromStyle(style?: ChartVisualStyle, appearance?: DashboardAppearance) {
  const merged = chartAppearance(style, appearance)
  return chartPalette(merged || appearance)
}
