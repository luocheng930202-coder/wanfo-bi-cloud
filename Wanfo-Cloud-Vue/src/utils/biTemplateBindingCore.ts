import type { ChartType } from '@/utils/biChartTypes'
import { isPieLike, maxMetricsForType, validateChartQueryShape } from '@/utils/biChartTypes'

export interface BindingField { name: string; type: string; dimFlag?: string; showName?: string }
export interface BindingSlot { kind: 'chart' | 'kpi'; type: ChartType; label?: string }
export interface BindingPanelConfig {
  type: ChartType
  dimensions: { field: string; alias?: string }[]
  metrics: { field: string; agg: string; alias?: string }[]
  orderBys: { field: string; order: string }[]
  limit: number
  filterTree?: unknown
}

export function isNumericFieldType(type: string): boolean {
  return /(int|decimal|numeric|float|double|real|number)/i.test(type || '')
}

function dimensionFields(fields: BindingField[]): BindingField[] {
  return fields.filter(f => f.dimFlag === '1' || (!isNumericFieldType(f.type) && f.dimFlag !== '0'))
}

function metricFields(fields: BindingField[]): BindingField[] {
  const dims = new Set(dimensionFields(fields).map(f => f.name))
  return fields.filter(f => !dims.has(f.name) && (f.dimFlag === '0' || isNumericFieldType(f.type)))
}

export function suggestPanelConfig(slot: BindingSlot, fields: BindingField[]): BindingPanelConfig {
  const dims = dimensionFields(fields)
  const metrics = metricFields(fields)
  const firstDim = dims[0]?.name || fields.find(f => !isNumericFieldType(f.type))?.name || fields[0]?.name
  const firstMetric = metrics[0]?.name || fields.find(f => isNumericFieldType(f.type))?.name || fields[0]?.name
  if (!firstMetric) throw new Error('当前数据集没有可用字段')
  if (slot.kind === 'kpi') {
    return { type: 'table', dimensions: [], metrics: [{ field: firstMetric, agg: 'SUM', alias: 'value' }], orderBys: [], limit: 100 }
  }
  if (slot.type === 'table') {
    const picked = fields.slice(0, Math.min(6, fields.length))
    return {
      type: 'table',
      dimensions: [],
      metrics: picked.map(f => ({ field: f.name, agg: 'NONE', alias: f.name })),
      orderBys: [],
      limit: 100
    }
  }
  if (slot.type === 'gauge') {
    return { type: 'gauge', dimensions: [], metrics: [{ field: firstMetric, agg: 'SUM', alias: firstMetric }], orderBys: [], limit: 100 }
  }
  if (slot.type === 'scatter') {
    const second = metrics[1]?.name || fields.find(f => f.name !== firstMetric && isNumericFieldType(f.type))?.name || firstMetric
    return {
      type: 'scatter',
      dimensions: firstDim ? [{ field: firstDim, alias: firstDim }] : [],
      metrics: [{ field: firstMetric, agg: 'SUM', alias: 'x' }, { field: second, agg: 'SUM', alias: 'y' }],
      orderBys: [],
      limit: 100
    }
  }
  const dim = firstDim || firstMetric
  const metric = firstMetric
  return {
    type: slot.type,
    dimensions: dim ? [{ field: dim, alias: dim }] : [],
    metrics: [{ field: metric, agg: 'SUM', alias: metric }],
    orderBys: [],
    limit: 100
  }
}

function resolveFieldName(name: string, fields: BindingField[]): string {
  if (!name) return ''
  const names = new Set(fields.map(f => f.name))
  if (names.has(name)) return name
  const lower = name.toLowerCase()
  const same = fields.find(f => f.name.toLowerCase() === lower)
  if (same) return same.name
  const dim = dimensionFields(fields).find(f => (f.showName || f.name).toLowerCase() === lower)
  if (dim) return dim.name
  const metric = metricFields(fields).find(f => (f.showName || f.name).toLowerCase() === lower)
  return metric?.name || ''
}

/** Keep mapping where target dataset has the same (or case-insensitive) field names; fill gaps from slot recommendations. */
export function remapPanelConfig(slot: BindingSlot, config: BindingPanelConfig, fields: BindingField[]): BindingPanelConfig {
  if (!fields.length) throw new Error('当前数据集没有可用字段')
  const names = new Set(fields.map(f => f.name))
  const next: BindingPanelConfig = {
    type: config.type,
    dimensions: config.dimensions.map(d => ({ ...d, field: resolveFieldName(d.field, fields) || d.field })).filter(d => d.field && names.has(d.field)),
    metrics: config.metrics
      .map(m => ({ ...m, field: resolveFieldName(m.field, fields) || m.field }))
      .filter(m => m.field && names.has(m.field)),
    orderBys: config.orderBys.map(o => ({ ...o, field: resolveFieldName(o.field, fields) || o.field })),
    limit: config.limit,
    filterTree: config.filterTree
  }
  const outputs = new Set([
    ...next.dimensions.map(d => d.alias || d.field),
    ...next.metrics.map(m => m.alias || (m.agg === 'NONE' ? m.field : `${m.agg.toLowerCase()}_${m.field}`))
  ])
  next.orderBys = next.orderBys.filter(o => o.field && (names.has(o.field) || outputs.has(o.field)))
  const suggested = suggestPanelConfig(slot, fields)
  if (slot.kind === 'kpi') {
    if (!next.metrics.length) next.metrics = suggested.metrics
    next.dimensions = []
    next.type = 'table'
    next.orderBys = []
    return next
  }
  if (!next.metrics.length) next.metrics = suggested.metrics
  if (next.type !== 'table' && next.type !== 'gauge' && !next.dimensions.length) next.dimensions = suggested.dimensions
  if (analyzeBindingImpact(slot, next, fields).some(x => x.includes('不存在') || x.includes('须为'))) {
    return suggested
  }
  return next
}

export function analyzeBindingImpact(slot: BindingSlot, config: BindingPanelConfig, fields: BindingField[]): string[] {
  const known = new Map(fields.map(f => [f.name, f]))
  const issues: string[] = []
  const missing = [...config.dimensions.map(d => d.field), ...config.metrics.map(m => m.field), ...config.orderBys.map(o => o.field)]
    .filter(name => name && !known.has(name))
  if (missing.length) issues.push(`字段不存在或不可见：${[...new Set(missing)].join('、')}`)
  if (slot.kind === 'kpi') {
    if (config.dimensions.length) issues.push('指标卡不能配置维度')
    if (config.metrics.length !== 1) issues.push('指标卡须绑定单个聚合度量')
    if (config.type !== 'table') issues.push('指标卡查询类型须为明细表聚合')
  } else {
    const shape = validateChartQueryShape(config.type, config.dimensions, config.metrics)
    if (shape) issues.push(shape)
    if (config.type !== 'table' && config.metrics.length > maxMetricsForType(config.type)) {
      issues.push(`当前图表类型最多 ${maxMetricsForType(config.type)} 个度量`)
    }
    for (const m of config.metrics) {
      const agg = (m.agg || 'NONE').toUpperCase()
      const t = known.get(m.field)?.type || ''
      if (!['COUNT', 'COUNT_DISTINCT'].includes(agg) && !isNumericFieldType(t)) issues.push(`度量 ${m.field} 须为数值字段或使用计数聚合`)
    }
  }
  for (const d of config.dimensions) {
    const t = known.get(d.field)?.type || ''
    if (isNumericFieldType(t) && slot.type !== 'table' && !isPieLike(slot.type)) issues.push(`维度 ${d.field} 建议使用文本/枚举类字段`)
  }
  return issues
}
