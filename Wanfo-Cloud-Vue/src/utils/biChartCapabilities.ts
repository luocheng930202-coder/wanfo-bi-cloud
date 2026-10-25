import type { ChartType } from './biChartTypes'

export interface ChartCapabilities {
  dimension: string | null
  metric: string
  hint: string
  legend: boolean
  axis: boolean
  smooth: boolean
}

const axis = { legend: true, axis: true, smooth: false }
const circular = { legend: true, axis: false, smooth: false }
export const CHART_CAPABILITIES: Record<ChartType, ChartCapabilities> = {
  bar: { ...axis, dimension: '分类维度', metric: '数值指标', hint: '一个分类维度，1～5 个指标；用于比较不同类别。' },
  hbar: { ...axis, dimension: '排名分类', metric: '数值指标', hint: '一个分类维度，1～5 个指标；适合长名称与排名。' },
  line: { ...axis, smooth: true, dimension: '时间 / 有序分类', metric: '趋势指标', hint: '一个有序维度，1～5 个指标；时间字段请按时间升序。' },
  area: { ...axis, smooth: true, dimension: '时间 / 有序分类', metric: '趋势指标', hint: '一个有序维度，1～5 个指标；面积表现趋势与规模。' },
  stack: { ...axis, dimension: '分类维度', metric: '堆叠指标', hint: '一个分类维度，1～5 个指标；各指标应具有可比较的单位。' },
  percent: { ...axis, dimension: '分类维度', metric: '构成指标', hint: '一个分类维度，1～5 个非负指标；按当前分类内总值计算占比。' },
  pie: { ...circular, dimension: '扇区分类', metric: '扇区数值', hint: '一个分类维度和一个非负指标；分类过多时建议使用条形图。' },
  ring: { ...circular, dimension: '扇区分类', metric: '扇区数值', hint: '一个分类维度和一个非负指标；用于展示整体构成。' },
  funnel: { ...circular, dimension: '业务阶段', metric: '阶段数值', hint: '一个阶段维度和一个非负指标；请按业务顺序设置查询排序。' },
  radar: { ...circular, dimension: '对比对象', metric: '雷达轴指标', hint: '一个对象维度，1～5 个指标；每个指标形成一个雷达轴，注意量纲。' },
  scatter: { ...axis, dimension: '点标识 / 分组（可选）', metric: '坐标指标', hint: '两个数值指标分别映射 X、Y 轴；可选一个维度标识数据点。' },
  gauge: { legend: false, axis: false, smooth: false, dimension: null, metric: '仪表数值', hint: '仅一个聚合指标，不需要维度；仪表展示汇总结果。' },
  combo: { ...axis, smooth: true, dimension: '共享分类 / 时间', metric: '柱线指标', hint: '2～5 个指标；可在样式页逐指标配置柱线图形、左右轴与数字格式。' },
  table: { legend: false, axis: false, smooth: false, dimension: null, metric: '展示字段', hint: '明细展示可选择原始字段；需要汇总时显式选择聚合方式。' },
  pivot: { legend: false, axis: false, smooth: false, dimension: null, metric: '汇总指标', hint: '分别设置行、列维度及聚合指标；不要重复选择同一行列字段。' },
  map: { legend: false, axis: false, smooth: false, dimension: '地区维度', metric: '区域数值', hint: '一个地区维度和一个数值指标；支持省市县范围，在高级页绑定层级字段。' },
  heatmap:{...axis,dimension:'热力格维度',metric:'强度指标',hint:'两个分类维度和一个聚合数值；颜色表示格点强度。'},
  treemap:{...circular,dimension:'层级路径',metric:'矩形权重',hint:'1～6 个维度按顺序构成父子路径，搭配一个非负聚合指标。'},
  sunburst:{...circular,dimension:'层级路径',metric:'层级权重',hint:'2～6 个维度按顺序构成环形层级，搭配一个非负聚合指标。'},
  sankey:{...circular,dimension:'流向来源',metric:'流量指标',hint:'来源与目标两个维度及一个聚合流量指标。'},
  boxplot:{...axis,dimension:'样本分组',metric:'原始样本值',hint:'一个分组维度和一个原始数值字段；自动计算五数概括。'},
  histogram:{...axis,dimension:null,metric:'原始样本值',hint:'一个原始数值字段；按平方根规则自动估算区间数。'},
  waterfall:{...axis,dimension:'变化类别',metric:'增减指标',hint:'一个分类维度和一个聚合指标，按查询顺序累加变化。'},
  'geo-map':{...axis,dimension:'点名称（可选）',metric:'经纬度与点大小',hint:'选择未聚合经度、纬度，可选点大小和名称维度。'}
}

export function allowedAggregations(fieldType: string, type: ChartType): string[] {
  const numeric = /(int|decimal|numeric|float|double|real|number)/i.test(fieldType)
  return [...(['table','boxplot','histogram','geo-map'].includes(type) ? ['NONE'] : []), 'COUNT', 'COUNT_DISTINCT', ...(numeric ? ['SUM', 'AVG', 'MIN', 'MAX'] : [])]
}
