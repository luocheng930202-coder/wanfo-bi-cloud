/** Chart visual kinds persisted on bi_chart and rendered in ChartPreview. */
export const CHART_TYPES = [
  'bar', 'line', 'area', 'hbar', 'stack', 'percent',
  'pie', 'ring', 'funnel', 'radar', 'scatter', 'gauge', 'combo', 'table', 'pivot', 'map',
  'heatmap','treemap','sunburst','sankey','boxplot','histogram','waterfall','geo-map'
] as const

export type ChartType = (typeof CHART_TYPES)[number]

/** Grouped for chart type picker UI. */
export const CHART_TYPE_GROUPS: { title: string; types: ChartType[] }[] = [
  { title: '柱线与趋势', types: ['bar', 'line', 'area', 'hbar', 'stack', 'percent', 'combo'] },
  { title: '占比与构成', types: ['pie', 'ring', 'funnel', 'radar'] },
  { title: '分布与指标', types: ['scatter', 'boxplot', 'histogram', 'gauge', 'map', 'geo-map'] },
  { title: '层级与流向', types: ['heatmap','treemap','sunburst','sankey','waterfall'] },
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
  map: '行政区地图', heatmap:'热力图',treemap:'矩形树图',sunburst:'旭日图',sankey:'桑基图',boxplot:'箱线图',histogram:'直方图',waterfall:'瀑布图','geo-map':'经纬度地图'
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
  return type === 'table' || type === 'gauge' || type === 'scatter' || type==='histogram' || type==='geo-map'
}

export function isPivotType(type: ChartType): boolean {
  return type === 'pivot'
}

export function supportsDrill(type: ChartType): boolean {
  return !new Set<ChartType>(['table', 'pivot', 'map', 'geo-map', 'gauge','histogram']).has(type)
}

export function supportsSeriesDimension(type:ChartType):boolean { return ['bar','hbar','line','area','stack','percent','combo','heatmap','sunburst','sankey','treemap'].includes(type) }

export function maxMetricsForType(type: ChartType): number {
  if (type === 'pivot') return 3
  if (['heatmap','treemap','sunburst','sankey','boxplot','histogram','waterfall'].includes(type)) return 1
  if(type==='geo-map')return 3
  if (type === 'table') return 5
  if (isPieLike(type) || type === 'gauge' || type === 'map') return 1
  if (type === 'scatter') return 3
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
    if (metrics.length < 2 || metrics.length > 3) return '散点图需要 X、Y 两个度量，可选第三个气泡大小指标'
    if (dimensions.length > 1) return '散点图最多一个维度用于系列分组'
    if (!metrics.length) return '请配置度量'
    return null
  }
  if(type==='histogram')return dimensions.length===0&&metrics.length===1&&(metrics[0] as {agg?:string}).agg==='NONE'?null:'直方图需要一个未聚合的数值字段，且不需要维度'
  if(type==='geo-map')return dimensions.length<=1&&metrics.length>=2&&metrics.length<=3&&metrics.every(m=>(m as {agg?:string}).agg==='NONE')?null:'经纬度地图需要未聚合的经度、纬度，可选一个点大小指标和一个名称维度'
  if(type==='heatmap')return dimensions.length===2&&metrics.length===1&&(metrics[0] as {agg?:string}).agg!=='NONE'?null:'热力图需要两个分类维度和一个聚合指标'
  if(type==='sunburst')return dimensions.length===2&&metrics.length===1&&(metrics[0] as {agg?:string}).agg!=='NONE'?null:'旭日图需要父级、子级两个维度和一个聚合指标'
  if(type==='sankey')return dimensions.length===2&&metrics.length===1&&(metrics[0] as {agg?:string}).agg!=='NONE'?null:'桑基图需要来源、目标两个维度和一个聚合流量指标'
  if(type==='treemap')return (dimensions.length===1||(dimensions.length===2&&dimensions[0].field!==dimensions[1].field))&&metrics.length===1&&(metrics[0] as {agg?:string}).agg!=='NONE'?null:'矩形树图需要一个或两个不同维度和一个聚合指标'
  if(type==='waterfall')return dimensions.length===1&&metrics.length===1&&(metrics[0] as {agg?:string}).agg!=='NONE'?null:'瀑布图需要一个维度和一个聚合指标'
  if(type==='boxplot')return dimensions.length===1&&metrics.length===1&&(metrics[0] as {agg?:string}).agg==='NONE'?null:'箱线图需要一个分类维度和一个未聚合数值字段'
  if (isMapLike(type)) {
    if (dimensions.length !== 1 || metrics.length !== 1) return '行政区地图须选择一个地区维度和一个度量'
    return null
  }
  if (isPieLike(type)) {
    if (dimensions.length !== 1 || metrics.length !== 1) return '饼图/环形图/漏斗图须选择一个维度和一个度量'
    return null
  }
  if ((dimensions.length !== 1 && !(supportsSeriesDimension(type) && dimensions.length===2 && dimensions[0].field!==dimensions[1].field)) || !metrics.length || metrics.length > 5) return '图形须选择一个分类维度、可选一个系列维度和 1～5 个度量'
  if(dimensions.length===2 && metrics.some(m=>(m as {agg?:string}).agg==='NONE'))return '系列维度需要聚合指标'
  if (type === 'combo' && metrics.length < 2) return '柱线组合图至少需要两个度量'
  return null
}

export function applyTypeDefaults(
  type: ChartType,
  dimensions: import('@/api/bi/chart').QueryDimension[],
  metrics: { field: string; agg: string; alias?: string }[]
): { dimensions: typeof dimensions; metrics: typeof metrics } {
  let dims = dimensions
  let mets = metrics
  const max = maxMetricsForType(type)
  if (mets.length > max) mets = mets.slice(0, max)
  if (type === 'gauge'||type==='histogram') dims = []
  else if (!supportsSeriesDimension(type) && type!=='table' && type!=='pivot' && type!=='geo-map') dims=dims.slice(0,1)
  if(['heatmap','sunburst','sankey'].includes(type))dims=dims.slice(0,2)
  if(type==='geo-map')mets=mets.map(m=>({...m,agg:'NONE'})).slice(0,3)
  if(['boxplot','histogram'].includes(type))mets=mets.map(m=>({...m,agg:'NONE'}))
  return { dimensions: dims, metrics: mets }
}

export interface ChartConfigurationIssue { code:string; path:string; message:string; severity:'error'|'warning' }

/** Explain invalid field roles before submitting a query; the server remains authoritative. */
export function chartConfigurationIssues(definition:import('@/api/bi/chart').ChartDefinition, fields:import('@/api/bi/chart').Field[]):ChartConfigurationIssue[] {
  const issues:ChartConfigurationIssue[]=[]
  const add=(code:string,path:string,message:string,severity:'error'|'warning'='error')=>issues.push({code,path,message,severity})
  const query=definition.query, type=definition.type
  if(!query.datasetId)add('dataset','datasetId','请选择数据集')
  const shape=validateChartQueryShape(type,query.dimensions,query.metrics)
  if(shape && query.metrics.length>0 && query.metrics.length<=maxMetricsForType(type))add('shape','roles',shape)
  if(!query.metrics.length && !query.dimensions.length && !['table','pivot','gauge','scatter','histogram','geo-map'].includes(type))add('dimension','dimensions','请选择分类维度')
  if(!query.metrics.length || query.metrics.length>maxMetricsForType(type))add('metrics','metrics',`请选择 1～${maxMetricsForType(type)} 个${type==='table'?'展示字段':'指标'}`)
  const known=new Map(fields.map(f=>[f.name,f]))
  const numeric=(field:string)=>/(int|decimal|numeric|float|double|real|number)/i.test(known.get(field)?.type || '')
  const exists=(field:string,path:string)=>{if(!field || !known.has(field)){add('field',path,field?`字段「${field}」已失效或无权使用，请重新选择`:'请选择字段');return false}return true}
  const keys=new Set<string>()
  query.dimensions.forEach((d,i)=>{exists(d.field,`dimensions.${i}`);if(keys.has(d.field))add('duplicate',`dimensions.${i}`,'分类与系列维度不能重复');keys.add(d.field)})
  query.metrics.forEach((m,i)=>{
    const valid=exists(m.field,`metrics.${i}.field`)
    if(!['NONE','COUNT','COUNT_DISTINCT','SUM','AVG','MIN','MAX'].includes(m.agg))add('aggregation',`metrics.${i}.agg`,'请选择有效的聚合方式')
    if(valid && ['SUM','AVG','MIN','MAX'].includes(m.agg) && !numeric(m.field))add('numeric',`metrics.${i}.agg`,`「${known.get(m.field)?.showName || m.field}」不是数值字段，请使用计数或去重计数`)
    const key=m.alias || (m.agg==='NONE'?m.field:`${m.agg.toLowerCase()}_${m.field}`)
    if(keys.has(key))add('duplicate',`metrics.${i}.alias`,`结果名称「${key}」重复，请调整指标别名`)
    keys.add(key)
  })
  if(type==='table' && query.metrics.some(m=>m.agg==='NONE') && query.metrics.some(m=>m.agg!=='NONE'))add('mixed','metrics','明细字段与聚合指标不能混用，请统一展示口径')
  if(type==='pivot'){
    const rows=definition.advanced?.pivotRowFields?.length?definition.advanced.pivotRowFields:query.pivot?.rows || [], columns=definition.advanced?.pivotColFields?.length?definition.advanced.pivotColFields:query.pivot?.columns || []
    if(rows.length<1 || rows.length>3 || columns.length<1 || columns.length>2)add('pivot','pivot','透视表需要 1～3 个行维度、1～2 个列维度')
    const used=new Set<string>()
    for(const field of [...rows,...columns]){exists(field,'pivot');if(used.has(field))add('duplicate','pivot','透视表行列维度不能重复');used.add(field)}
  }
  const visit=(node:import('@/api/bi/chart').FilterNode,path:string):void=>{
    if('children' in node){if(!node.children.length)add('filter',path,'筛选分组不能为空');node.children.forEach((child,i)=>visit(child,`${path}.${i}`));return}
    exists(node.field,path)
    if(!['IS_NULL','IS_NOT_NULL'].includes(node.op) && (node.value==null || node.value==='' || Array.isArray(node.value) && !node.value.length))add('filter',path,'请填写筛选值，或移除此筛选条件')
  }
  if(query.filterTree)visit(query.filterTree,'filterTree')
  query.filters?.forEach((f,i)=>visit(f,`filters.${i}`))
  if(['pie','ring','funnel','percent'].includes(type))add('nonnegative','data','此图要求非负数据；字段配置通过后，仍需检查实际数据中的负值及零合计','warning')
  if(type==='radar' && query.metrics.length>1)add('units','metrics','不同量纲的指标建议在高级分析中配置评分范围，避免直接比较','warning')
  return issues
}
