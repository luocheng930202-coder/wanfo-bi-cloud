import request from '@/utils/request'
import type { BiResult as AjaxResult } from './response'
import type { GrantPrincipal, ResourceGrant } from './datasource'
import type { Field, ChartResult } from './chart'

export type MetricKind = 'atomic' | 'derived' | 'composite'
export type MetricPublishStatus = 'draft' | 'published'

export interface MeasureSpec { field: string; agg: string }
export interface ComponentRef { code: string; alias: string }
export interface MetricFilter { field: string; op: string; value?: unknown }
export interface MetricDimension { field: string; alias?: string; timeGrain?: string }
export interface MetricQueryRequest {
  dimensions?: MetricDimension[]
  filters?: MetricFilter[]
  limit?: number
  timeoutSeconds?: number
}

export interface MetricDefinition {
  schemaVersion: 2
  name: string
  code: string
  kind: MetricKind
  datasetId: number
  measure?: MeasureSpec
  fixedFilters?: MetricFilter[]
  allowedDimensions?: string[]
  components?: ComponentRef[]
  expression?: string
  componentMetricCodes?: string[]
  compositeExpression?: string
  businessDefinition?: string
  grain?: string
  unit?: string
  timeZone?: string
  owner?: string
}

export interface MetricConflict {
  leftId: number; leftCode: string; leftName: string
  rightId: number; rightCode: string; rightName: string
  datasetId: number; field: string; aggregation: string; differences: string[]
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
  pendingPublish?: boolean
  resourceActions: string[]
}

export interface MetricConsumerContext {
  id: number
  code: string
  name: string
  kind: MetricKind
  publishStatus: MetricPublishStatus
  publishedVersion: number
  pinNote: string
  businessDefinition?: string
  businessDimensions?: string
  allowedDimensions?: string[]
  grain?: string
  unit?: string
  timeZone?: string
  owner?: string
  glossaryTerms: Array<{ id: number; termCode: string; name: string; definitionText?: string; certificationStatus?: string }>
}

export interface MetricPublishRequest {
  id: number
  metricId: number
  status: string
  requestNote?: string
  decisionNote?: string
  requestedBy?: string
  requestedAt?: string
  metricVersion: number
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
  businessDefinition?: string
  businessDimensions?: string
  grain?: string
  unit?: string
  timeZone?: string
  owner?: string
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
export const listMetricConflicts = (): Promise<AjaxResult<MetricConflict[]>> => request({ url: '/bi/metric/conflicts' })
export const getMetric = (id: number): Promise<AjaxResult<MetricDetail>> => request({ url: `/bi/metric/${id}` })
export const saveMetric = (definition: MetricDefinition, id?: number, version?: number): Promise<AjaxResult<{ id: number; version: number }>> =>
  request({ url: id ? `/bi/metric/${id}` : '/bi/metric', method: id ? 'put' : 'post', data: { definition, version } })
export const deleteMetric = (id: number): Promise<AjaxResult> => request({ url: `/bi/metric/${id}`, method: 'delete' })
export const previewMetric = (definition: MetricDefinition, query?: MetricQueryRequest): Promise<AjaxResult<ChartResult>> =>
  request({ url: '/bi/metric/preview', method: 'post', data: { definition, query }, timeout: 60000 })
export const savedMetricData = (id: number, query?: MetricQueryRequest): Promise<AjaxResult<ChartResult>> =>
  request({ url: `/bi/metric/${id}/data`, method: 'post', data: query || {}, timeout: 60000 })
export const metricDataByCode = (code: string, query?: MetricQueryRequest): Promise<AjaxResult<ChartResult>> =>
  request({ url: `/bi/metric/code/${encodeURIComponent(code)}/data`, method: 'post', data: query || {}, timeout: 60000 })
export const publishMetric = (id: number, version: number, note?: string): Promise<AjaxResult<{ publishRequestId: number; publishRequestStatus: string }>> =>
  request({ url: `/bi/metric/${id}/publish`, method: 'post', data: { version, note } })
export const listPendingMetricPublishRequests = (): Promise<AjaxResult<MetricPublishRequest[]>> =>
  request({ url: '/bi/metric/publish-requests/pending' })
export const decideMetricPublish = (requestId: number, approve: boolean, note?: string): Promise<AjaxResult<Record<string, unknown>>> =>
  request({ url: `/bi/metric/publish-requests/${requestId}/decide`, method: 'post', data: { approve, note } })
export const metricConsumerContext = (id: number): Promise<AjaxResult<MetricConsumerContext>> =>
  request({ url: `/bi/metric/${id}/consumer-context` })
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

export type { Field }
