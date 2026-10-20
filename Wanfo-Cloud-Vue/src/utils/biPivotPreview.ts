import type { S2DataConfig } from '@antv/s2'
import type { ChartDefinition, ChartResult } from '@/api/bi/chart'
import { metricResultKey } from '@/utils/biEchartsPreview'

function dimKey(definition: ChartDefinition, field: string): string {
  const d = definition.query.dimensions.find(x => x.field === field)
  return d?.alias || field
}

export function pivotFieldKeys(definition: ChartDefinition): { rows: string[]; columns: string[]; values: string[] } {
  const rows = (definition.advanced?.pivotRowFields || []).map(f => dimKey(definition, f))
  const columns = (definition.advanced?.pivotColFields || []).map(f => dimKey(definition, f))
  const values = definition.query.metrics.map(metricResultKey)
  return { rows, columns, values }
}

export function buildPivotDataConfig(definition: ChartDefinition, result: ChartResult): S2DataConfig {
  const fields = pivotFieldKeys(definition)
  return { fields, data: result.rows as Record<string, string | number>[] }
}

export function validatePivotConfig(definition: ChartDefinition): string | null {
  if (definition.type !== 'pivot') return null
  const rows = definition.advanced?.pivotRowFields || []
  const cols = definition.advanced?.pivotColFields || []
  if (!rows.length) return '透视表须配置至少一个行维度'
  if (!cols.length) return '透视表须配置至少一个列维度'
  if (rows.length > 3) return '透视表行维度最多 3 个'
  if (cols.length > 2) return '透视表列维度最多 2 个'
  if (!definition.query.metrics.length || definition.query.metrics.length > 3) return '透视表须配置 1～3 个聚合度量'
  if (definition.query.metrics.some(m => (m.agg || 'NONE').toUpperCase() === 'NONE')) return '透视表度量须使用聚合函数'
  const names = new Set(definition.query.dimensions.map(d => d.field))
  for (const f of [...rows, ...cols]) if (!names.has(f)) return `维度 ${f} 未加入查询分组`
  const overlap = rows.find(f => cols.includes(f))
  if (overlap) return `字段 ${overlap} 不能同时作为行与列维度`
  return null
}

export function syncPivotQueryDimensions(definition: ChartDefinition): void {
  if (definition.type !== 'pivot') return
  const ordered = [...(definition.advanced?.pivotRowFields || []), ...(definition.advanced?.pivotColFields || [])]
  const prev = definition.query.dimensions
  definition.query.dimensions = ordered.map(field => prev.find(d => d.field === field) || { field, alias: field })
}
