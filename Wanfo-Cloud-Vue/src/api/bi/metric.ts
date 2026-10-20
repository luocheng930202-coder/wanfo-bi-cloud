import request from '@/utils/request'
import type { BiResult as AjaxResult } from './response'
import type { GrantPrincipal, ResourceGrant } from './datasource'
import type { Field, QueryModel, ChartResult } from './chart'

export type MetricKind = 'scalar' | 'table'
export type MetricPublishStatus = 'draft' | 'published'
export interface MetricDefinition {
  schemaVersion: 1
  name: string
  code: string
  kind: MetricKind
  query: QueryModel
}
export interface MetricSummary {
  id: number
  code: string
  name: string
  kind: MetricKind
  datasetId: number
  version: number
  publishStatus: MetricPublishStatus
  publishedVersion: number
  inUse: boolean
  resourceActions: string[]
}
export interface MetricDetail extends MetricSummary {
  definition: MetricDefinition
  published: MetricDefinition | null
}
export interface MetricHistory {
  id: number
  publishedVersion: number
  code: string
  name: string
  kind: MetricKind
  datasetId: number
  publishBy: string
  publishTime: string
}
export interface MetricUsage {
  id: number
  refType: string
  refId: string
  remark?: string
  createBy?: string
  createTime?: string
}

export const listMetrics = (): Promise<AjaxResult<MetricSummary[]>> => request({ url: '/bi/metric' })
export const getMetric = (id: number): Promise<AjaxResult<MetricDetail>> => request({ url: `/bi/metric/${id}` })
export const saveMetric = (definition: MetricDefinition, id?: number, version?: number): Promise<AjaxResult<{ id: number; version: number }>> =>
  request({ url: id ? `/bi/metric/${id}` : '/bi/metric', method: id ? 'put' : 'post', data: { definition, version } })
export const deleteMetric = (id: number): Promise<AjaxResult> => request({ url: `/bi/metric/${id}`, method: 'delete' })
export const previewMetric = (definition: MetricDefinition): Promise<AjaxResult<ChartResult>> =>
  request({ url: '/bi/metric/preview', method: 'post', data: definition, timeout: 60000 })
export const savedMetricData = (id: number): Promise<AjaxResult<ChartResult>> =>
  request({ url: `/bi/metric/${id}/data`, method: 'post', timeout: 60000 })
export const metricDataByCode = (code: string): Promise<AjaxResult<ChartResult>> =>
  request({ url: `/bi/metric/code/${encodeURIComponent(code)}/data`, method: 'post', timeout: 60000 })
export const publishMetric = (id: number, version: number): Promise<AjaxResult<{ version: number; publishedVersion: number }>> =>
  request({ url: `/bi/metric/${id}/publish`, method: 'post', data: { version } })
export const unpublishMetric = (id: number, version: number): Promise<AjaxResult<{ version: number }>> =>
  request({ url: `/bi/metric/${id}/unpublish`, method: 'post', data: { version } })
export const metricHistory = (id: number): Promise<AjaxResult<MetricHistory[]>> => request({ url: `/bi/metric/${id}/history` })
export const metricUsages = (id: number): Promise<AjaxResult<MetricUsage[]>> => request({ url: `/bi/metric/${id}/usages` })
export const metricFields = (datasetId: number): Promise<AjaxResult<Field[]>> => request({ url: `/bi/metric/dataset/${datasetId}/fields` })
export const searchMetricPrincipals = (query = ''): Promise<AjaxResult<GrantPrincipal[]>> =>
  request({ url: '/bi/metric/principals', params: { query } })
export const listMetricGrants = (id: number): Promise<AjaxResult<ResourceGrant[]>> => request({ url: `/bi/metric/${id}/grants` })
export const grantMetric = (id: number, principalId: number, action: string): Promise<AjaxResult> =>
  request({ url: `/bi/metric/${id}/grants`, method: 'post', data: { principalId, action } })
export const revokeMetricGrant = (id: number, principalId: number, action: string): Promise<AjaxResult> =>
  request({ url: `/bi/metric/${id}/grants/${principalId}/${action}`, method: 'delete' })
