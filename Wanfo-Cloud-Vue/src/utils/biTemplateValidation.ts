import type { ChartResult } from '@/api/bi/chart'
import type { PanelChartConfig } from '@/api/bi/dashboard'

export interface TemplateValidationReport { state: 'success' | 'warning'; message: string }
export function templateValidationReport(data: ChartResult | undefined, config: PanelChartConfig, metricNames: string[] = []): TemplateValidationReport {
  if (!data || !Array.isArray(data.rows)) throw new Error('模板预览未返回组件数据')
  if (!data.rows.length) return { state: 'warning', message: '查询成功，但当前筛选范围没有数据；请检查日期、地区和数据权限范围。' }
  const knownMetricNames = metricNames.filter(name => data.columns.includes(name))
  if (knownMetricNames.length && data.rows.every(row => knownMetricNames.every(name => row[name] == null))) {
    return { state: 'warning', message: '查询成功，但度量均为空值；空值不会被当作 0，请检查源数据与聚合口径。' }
  }
  const limited = config.limit > 0 && data.rows.length >= config.limit
  return { state: limited ? 'warning' : 'success', message: `查询成功，返回 ${data.rows.length} 行${limited ? '，已达到查询条数上限，可能存在未展示数据；此行数不是业务总量' : ''}。` }
}

export function templateValidationError(error: unknown): string {
  const err = error as { response?: { status?: number }; code?: string }
  if (err?.response?.status === 401) return '登录已失效，请重新登录后验证'
  if (err?.response?.status === 403) return '没有当前图表或数据集的查询权限，请联系管理员授权'
  if (err?.code === 'ECONNABORTED' || err?.code === 'ETIMEDOUT') return '查询超时，请缩小时间范围或检查数据源后重试'
  if (err?.code === 'ERR_NETWORK') return '网络连接失败，请检查服务状态后重试'
  return '验证失败，请检查字段类型、聚合、排序和数据源连接后重试'
}
