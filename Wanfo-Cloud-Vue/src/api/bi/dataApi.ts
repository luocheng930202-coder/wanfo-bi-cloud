import request from '@/utils/request'
import type { BiResult } from './response'
import type { GrantPrincipal, ResourceGrant } from './datasource'

export type DataApiStatus = 'DRAFT' | 'ACTIVE' | 'DEPRECATED' | 'DISABLED' | string

export interface DataApiSummary {
  id: number
  apiCode: string
  familyCode?: string
  name: string
  metricId: number
  metricPublishedVersion?: number
  apiVersion?: number
  status: DataApiStatus
  deprecatedAt?: string
  sunsetAt?: string
  successorApiCode?: string
  deprecationNote?: string
  maxPageSize: number
  timeoutSeconds: number
  callsPerMinute: number
  dailyQuota: number
  createBy?: string
  createTime?: string
  updateTime?: string
}

export interface DataApiFamily {
  familyCode: string
  activeCount: number
  draftCount: number
  deprecatedCount?: number
  coexisting: boolean
  versions: Array<{ id: number; apiCode: string; apiVersion?: number; status: string; name?: string }>
}

export interface DataApiKey {
  id: number
  apiId: number
  keyPrefix: string
  callerId?: number
  callerOrgCode?: string
  callerOrgName?: string
  status: string
  createBy?: string
  createdAt?: string
  expiresAt?: string
  lastUsedAt?: string
  revokedAt?: string
}

export interface DataApiCall {
  id: number
  apiKeyId?: number
  status: string
  durationMs?: number
  rowCount?: number
  errorCode?: string
  calledAt?: string
}

export interface DataApiRevision {
  id: number
  revisionNo: number
  definitionJson?: string
  createdBy?: string
  createdAt?: string
}

export interface DataApiDetail extends DataApiSummary {
  keys?: DataApiKey[]
  calls?: DataApiCall[]
  revisions?: DataApiRevision[]
}

export interface DataApiSaveRequest {
  code: string
  name: string
  metricId: number
  apiVersion?: number
  maxPageSize?: number
  timeoutSeconds?: number
  callsPerMinute?: number
  dailyQuota?: number
}

export interface DataApiDeprecateRequest {
  sunsetDays?: number
  successorApiCode?: string
  note?: string
}

export interface DataApiPortalItem extends DataApiSummary {
  callCount30d?: number
  successCount30d?: number
  distinctKeys30d?: number
  lastCalledAt?: string
  pastSunset?: boolean
  callerKeyPrefixes?: Array<{ keyPrefix: string; callCount30d: number; lastCalledAt?: string }>
  callerOrgs?: Array<{ callerId: number; orgCode: string; orgName: string; callCount30d: number; keyCount30d?: number; lastCalledAt?: string }>
}

export interface DataApiPortal {
  summary: { total: number; active: number; deprecated: number; pastSunset: number; draft: number; disabled: number; registeredCallers?: number; families?: number; multiActiveFamilies?: number }
  items: DataApiPortalItem[]
  families?: DataApiFamily[]
  note?: string
}

export interface DataApiCaller {
  id: number
  orgCode: string
  orgName: string
  contactName?: string
  contactEmail?: string
  notes?: string
  status: string
  callsPerMinute?: number
  dailyQuota?: number
}

export interface DataApiCallerSaveRequest {
  orgCode?: string
  orgName: string
  contactName?: string
  contactEmail?: string
  notes?: string
  status?: string
  callsPerMinute?: number
  dailyQuota?: number
}

export const listDataApis = (): Promise<BiResult<DataApiSummary[]>> => request({ url: '/bi/data-apis' })
export const getDataApiPortal = (): Promise<BiResult<DataApiPortal>> => request({ url: '/bi/data-apis/portal' })
export const getDataApi = (id: number): Promise<BiResult<DataApiDetail>> => request({ url: `/bi/data-apis/${id}` })
export const createDataApi = (data: DataApiSaveRequest): Promise<BiResult<DataApiDetail>> =>
  request({ url: '/bi/data-apis', method: 'post', data })
export const updateDataApi = (id: number, data: Omit<DataApiSaveRequest, 'code'>): Promise<BiResult<DataApiDetail>> =>
  request({ url: `/bi/data-apis/${id}`, method: 'put', data })
export interface DataApiContractIssue {
  severity: string
  code: string
  message: string
}

export interface DataApiContractCheck {
  passed: boolean
  apiId?: number
  apiCode?: string
  sampleRows?: number
  blockingCount?: number
  baselineSource?: string
  issues?: DataApiContractIssue[]
  proposed?: { columns?: Array<{ name: string; valueKind: string }>; metricId?: number; metricPublishedVersion?: number }
  baseline?: { columns?: Array<{ name: string; valueKind: string }>; metricId?: number; metricPublishedVersion?: number }
}

export const checkDataApiContract = (id: number): Promise<BiResult<DataApiContractCheck>> =>
  request({ url: `/bi/data-apis/${id}/contract-check`, method: 'post' })
export const replayDataApiContract = (id: number): Promise<BiResult<DataApiContractCheck>> =>
  request({ url: `/bi/data-apis/${id}/contract-replay`, method: 'post' })
export const replayDataApiContractSample = (id: number): Promise<BiResult<DataApiContractCheck & { mode?: string; sampleAt?: string; sample?: unknown }>> =>
  request({ url: `/bi/data-apis/${id}/contract-sample-replay`, method: 'post' })
export const refreshDataApiContractSample = (id: number): Promise<BiResult<{ apiId: number; refreshed: boolean; sampleRows?: number; sample?: unknown }>> =>
  request({ url: `/bi/data-apis/${id}/refresh-contract-sample`, method: 'post' })
export const autoDeprecateDataApiSiblings = (id: number, data?: { sunsetDays?: number; note?: string }): Promise<BiResult<{
  successorApiCode: string; familyCode: string; deprecatedCount: number; deprecated: Array<{ id: number; apiCode: string }>
}>> =>
  request({ url: `/bi/data-apis/${id}/auto-deprecate-siblings`, method: 'post', data: data || {} })
export const setDataApiCanary = (id: number, percent: number): Promise<BiResult<DataApiDetail & { canaryPercent?: number; note?: string }>> =>
  request({ url: `/bi/data-apis/${id}/canary`, method: 'post', data: { percent } })
export const publishDataApi = (id: number): Promise<BiResult<DataApiDetail & { oneTimeKey?: string; contractCheck?: DataApiContractCheck }>> =>
  request({ url: `/bi/data-apis/${id}/publish`, method: 'post' })
export const nextDataApiVersion = (id: number, data?: { newApiCode?: string; name?: string; metricId?: number; apiVersion?: number }): Promise<BiResult<DataApiDetail & { coexistenceHint?: string }>> =>
  request({ url: `/bi/data-apis/${id}/next-version`, method: 'post', data: data || {} })
export const listDataApiCallers = (activeOnly = false): Promise<BiResult<DataApiCaller[]>> =>
  request({ url: '/bi/data-apis/callers', params: { activeOnly } })
export const createDataApiCaller = (data: DataApiCallerSaveRequest): Promise<BiResult<DataApiCaller>> =>
  request({ url: '/bi/data-apis/callers', method: 'post', data })
export const updateDataApiCaller = (id: number, data: DataApiCallerSaveRequest): Promise<BiResult<DataApiCaller>> =>
  request({ url: `/bi/data-apis/callers/${id}`, method: 'put', data })
export const rotateDataApiKey = (id: number, expiresInDays?: number, callerId?: number): Promise<BiResult<{ apiId: number; oneTimeKey: string; message?: string }>> =>
  request({
    url: `/bi/data-apis/${id}/keys/rotate`,
    method: 'post',
    params: expiresInDays == null ? {} : { expiresInDays },
    data: callerId == null ? {} : { expiresInDays, callerId }
  })
export const assignDataApiKeyCaller = (apiId: number, keyId: number, callerId: number | null): Promise<BiResult<DataApiDetail>> =>
  request({ url: `/bi/data-apis/${apiId}/keys/${keyId}/caller`, method: 'post', data: { callerId } })
export const revokeDataApiKey = (id: number, keyId: number): Promise<BiResult<{ revoked: boolean }>> =>
  request({ url: `/bi/data-apis/${id}/keys/${keyId}/revoke`, method: 'post' })
export const disableDataApi = (id: number): Promise<BiResult<DataApiDetail>> =>
  request({ url: `/bi/data-apis/${id}/disable`, method: 'post' })
export const deprecateDataApi = (id: number, data?: DataApiDeprecateRequest): Promise<BiResult<DataApiDetail>> =>
  request({ url: `/bi/data-apis/${id}/deprecate`, method: 'post', data: data || {} })
export const undeprecateDataApi = (id: number): Promise<BiResult<DataApiDetail>> =>
  request({ url: `/bi/data-apis/${id}/undeprecate`, method: 'post' })
export const getDataApiOpenApiDoc = (id: number): Promise<BiResult<Record<string, unknown>>> =>
  request({ url: `/bi/data-apis/${id}/openapi` })

export const listDataApiGrants = (id: number): Promise<BiResult<ResourceGrant[]>> =>
  request({ url: `/bi/data-apis/${id}/grants` })
export const grantDataApi = (id: number, principalId: number, action: string): Promise<BiResult> =>
  request({ url: `/bi/data-apis/${id}/grants`, method: 'post', data: { principalId, action } })
export const revokeDataApiGrant = (id: number, principalId: number, action: string): Promise<BiResult> =>
  request({ url: `/bi/data-apis/${id}/grants/${principalId}/${action}`, method: 'delete' })

export type { GrantPrincipal }
