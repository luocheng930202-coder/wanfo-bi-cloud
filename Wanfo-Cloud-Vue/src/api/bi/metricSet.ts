import request from '@/utils/request'
import type { BiResult as AjaxResult } from './response'
import type { GrantPrincipal, ResourceGrant } from './datasource'

export interface MetricSetMember {
  setId: number
  metricId: number
  sortNo: number
  metricCode?: string
  metricName?: string
  publishStatus?: string
}

export interface MetricSet {
  id: number
  setCode: string
  name: string
  theme?: string
  description?: string
  status: 'ACTIVE' | 'ARCHIVED' | string
  createBy?: string
  createTime?: string
  members?: MetricSetMember[]
}

export interface MetricSetUpsert {
  setCode: string
  name: string
  theme?: string
  description?: string
  status?: string
  metricIds?: number[]
}

export const listMetricSets = (): Promise<AjaxResult<MetricSet[]>> => request({ url: '/bi/metric-sets' })
export const getMetricSet = (id: number): Promise<AjaxResult<MetricSet>> => request({ url: `/bi/metric-sets/${id}` })
export const createMetricSet = (data: MetricSetUpsert): Promise<AjaxResult<MetricSet>> =>
  request({ url: '/bi/metric-sets', method: 'post', data })
export const updateMetricSet = (id: number, data: MetricSetUpsert): Promise<AjaxResult<MetricSet>> =>
  request({ url: `/bi/metric-sets/${id}`, method: 'put', data })
export const deleteMetricSet = (id: number): Promise<AjaxResult> =>
  request({ url: `/bi/metric-sets/${id}`, method: 'delete' })
export const searchMetricSetPrincipals = (query = ''): Promise<AjaxResult<GrantPrincipal[]>> =>
  request({ url: '/bi/metric-sets/principals', params: { query } })
export const listMetricSetGrants = (id: number): Promise<AjaxResult<ResourceGrant[]>> =>
  request({ url: `/bi/metric-sets/${id}/grants` })
export const grantMetricSet = (id: number, principalId: number, action: string): Promise<AjaxResult> =>
  request({ url: `/bi/metric-sets/${id}/grants`, method: 'post', data: { principalId, action } })
export const revokeMetricSetGrant = (id: number, principalId: number, action: string): Promise<AjaxResult> =>
  request({ url: `/bi/metric-sets/${id}/grants/${principalId}/${action}`, method: 'delete' })
