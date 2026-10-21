/** Chart visual kinds persisted on bi_chart and rendered in ChartPreview. */
export const CHART_TYPES = [
  'bar', 'line', 'area', 'hbar', 'stack', 'percent',
  'pie', 'ring', 'funnel', 'radar', 'scatter', 'gauge', 'combo', 'table', 'pivot', 'map'
] as const

export type ChartType = (typeof CHART_TYPES)[number]

/** Grouped for chart type picker UI. */
export const CHART_TYPE_GROUPS: { title: string; types: ChartType[] }[] = [
  { title: '柱线与趋势', types: ['bar', 'line', 'area', 'hbar', 'stack', 'percent', 'combo'] },
  { title: '占比与构成', types: ['pie', 'ring', 'funnel', 'radar'] },
  { title: '分布与指标', types: ['scatter', 'gauge', 'map'] },
  { title: '表格分析', types: ['table', 'pivot'] }
]

export const CHART_TYPE_LABELS: Record<ChartType, string> = {
  bar: '柱状图',
  line: '折线图',
  area: '面积图',
  hbar: '条形图',
  stack: '堆叠柱状图',
  percent: '百分比堆叠',
  pie: '饼图',
  ring: '环形图',
  funnel: '漏斗图',
  radar: '雷达图',
  scatter: '散点图',
  gauge: '仪表盘',
  combo: '柱线组合',
  table: '明细表',
  pivot: '透视表',
  map: '行政区地图'
}

const PIE_LIKE = new Set<ChartType>(['pie', 'ring', 'funnel'])
const MAP_LIKE = new Set<ChartType>(['map'])

export function isChartType(value: string): value is ChartType {
  return (CHART_TYPES as readonly string[]).includes(value)
}

export function isPieLike(type: ChartType): boolean {
  return PIE_LIKE.has(type)
}

export function isMapLike(type: ChartType): boolean {
  return MAP_LIKE.has(type)
}

export function allowsClearDimension(type: ChartType): boolean {
  return type === 'table' || type === 'gauge' || type === 'scatter'
}

export function isPivotType(type: ChartType): boolean {
  return type === 'pivot'
}

export function supportsDrill(type: ChartType): boolean {
  return !new Set<ChartType>(['table', 'pivot', 'map', 'gauge']).has(type)
}

export function maxMetricsForType(type: ChartType): number {
  if (type === 'pivot') return 3
  if (type === 'table') return 5
  if (isPieLike(type) || type === 'gauge' || type === 'map') return 1
  if (type === 'scatter') return 2
  return 5
}

export function metricAddDisabled(type: ChartType, count: number): boolean {
  return count >= maxMetricsForType(type)
}

/** Client-side shape check aligned with backend ChartTypes.validateShape. */
export function validateChartQueryShape(
  type: ChartType,
  dimensions: { field: string }[],
  metrics: { field: string }[]
): string | null {
  if (type === 'table') return null
  if (type === 'pivot') return null
  if (type === 'gauge') {
    if (dimensions.length) return '仪表盘不需要维度，请只保留一个度量'
    if (metrics.length !== 1) return '仪表盘仅支持一个度量'
    return null
  }
  if (type === 'scatter') {
    if (metrics.length !== 2) return '散点图需要两个度量（横轴与纵轴）'
    if (dimensions.length > 1) return '散点图最多一个维度用于系列分组'
    if (!metrics.length) return '请配置度量'
    return null
  }
  if (isMapLike(type)) {
    if (dimensions.length !== 1 || metrics.length !== 1) return '行政区地图须选择一个地区维度和一个度量'
    return null
  }
  if (isPieLike(type)) {
    if (dimensions.length !== 1 || metrics.length !== 1) return '饼图/环形图/漏斗图须选择一个维度和一个度量'
    return null
  }
  if (dimensions.length !== 1 || !metrics.length || metrics.length > 5) return '图形须选择一个维度和 1～5 个度量'
  if (type === 'combo' && metrics.length < 2) return '柱线组合图至少需要两个度量'
  return null
}

export function applyTypeDefaults(
  type: ChartType,
  dimensions: { field: string; alias?: string; timeGrain?:import('@/api/bi/chart').TimeGrain|null }[],
  metrics: { field: string; agg: string; alias?: string }[]
): { dimensions: typeof dimensions; metrics: typeof metrics } {
  let dims = dimensions
  let mets = metrics
  const max = maxMetricsForType(type)
  if (mets.length > max) mets = mets.slice(0, max)
  if (type === 'gauge') dims = []
  return { dimensions: dims, metrics: mets }
}
