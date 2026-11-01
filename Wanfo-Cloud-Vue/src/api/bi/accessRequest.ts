import request from '@/utils/request'
import type { BiResult as AjaxResult } from './response'

export interface AccessRequest {
  id: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED' | string
  purpose: string
  resourceType: string
  resourceId: number
  assetUrn?: string
  actions: string[]
  validDays: number
  expiresAt?: string
  decisionNote?: string
  requestedBy?: string
  requesterUserId?: number
  requestedAt?: string
  reviewedBy?: string
  reviewedAt?: string
}

export interface AccessRequestCreate {
  resourceType: string
  resourceId: number
  assetUrn?: string
  purpose: string
  actions: string[]
  validDays?: number
}

export const listMyAccessRequests = (): Promise<AjaxResult<AccessRequest[]>> =>
  request({ url: '/bi/access-requests/mine' })
export const listPendingAccessRequests = (): Promise<AjaxResult<AccessRequest[]>> =>
  request({ url: '/bi/access-requests/pending' })
export const getAccessRequest = (id: number): Promise<AjaxResult<AccessRequest>> =>
  request({ url: `/bi/access-requests/${id}` })
export const createAccessRequest = (data: AccessRequestCreate): Promise<AjaxResult<AccessRequest>> =>
  request({ url: '/bi/access-requests', method: 'post', data })
export const decideAccessRequest = (id: number, approve: boolean, note?: string): Promise<AjaxResult<AccessRequest>> =>
  request({ url: `/bi/access-requests/${id}/decide`, method: 'post', data: { approve, note } })
