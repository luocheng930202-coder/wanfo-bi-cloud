import request from '@/utils/request'
import type { BiResult as AjaxResult } from './response'
import type { MarketTemplateEntry } from './template'

export interface WorkbenchSummary {
  datasource: number
  dataset: number
  chart: number
  dashboard: number
  screen: number
  form?: number
  export?: number
  template?: number
}

export interface WorkbenchRecentItem {
  kind: 'chart' | 'dashboard' | 'screen'
  id: number
  name: string
  type?: string
  published?: boolean
}

export const workbenchSummary = (): Promise<AjaxResult<WorkbenchSummary>> => request({ url: '/bi/workbench/summary' })
export const workbenchRecent = (kind = 'all', limit = 12): Promise<AjaxResult<WorkbenchRecentItem[]>> =>
  request({ url: '/bi/workbench/recent', params: { kind, limit } })
export const workbenchTemplatePicks = (limit = 8): Promise<AjaxResult<MarketTemplateEntry[]>> =>
  request({ url: '/bi/workbench/template-picks', params: { limit } })
