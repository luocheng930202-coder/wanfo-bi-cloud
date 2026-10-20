import request from '@/utils/request'
import type { BiResult as AjaxResult } from './response'
import type { DashboardView, PanelResult, DashboardInput } from './dashboard'

function shareHeaders(password?: string) {
  return password ? { 'X-BI-Share-Password': password } : {}
}

export interface DashboardShareLink {
  id: number
  dashboardId: number
  token: string
  dataScope: 'viewer' | 'publisher'
  hasPassword: boolean
  expireTime?: string | null
  createBy?: string
  createTime?: string
}

export interface ShareDashboardView extends DashboardView {
  dataScope?: 'viewer' | 'publisher'
}
export interface ShareAccessLog {id:number;shareId:number;viewerPrincipal:number;event:string;createTime:string}
export function listShareAccessLogs(id:number):Promise<AjaxResult<ShareAccessLog[]>>{return request({url:`/bi/dashboard/${id}/share-access-logs`})}

export function listDashboardShares(dashboardId: number): Promise<AjaxResult<DashboardShareLink[]>> {
  return request({ url: `/bi/dashboard/${dashboardId}/shares` })
}

export function createDashboardShare(dashboardId: number, data: { dataScope: string; expireDays?: number; password?: string }): Promise<AjaxResult<DashboardShareLink>> {
  return request({ url: `/bi/dashboard/${dashboardId}/shares`, method: 'post', data })
}

export function revokeDashboardShare(dashboardId: number, shareId: number): Promise<AjaxResult> {
  return request({ url: `/bi/dashboard/${dashboardId}/shares/${shareId}`, method: 'delete' })
}

export function getShareDashboardView(token: string, password?: string): Promise<AjaxResult<ShareDashboardView>> {
  return request({ url: `/bi/share/${encodeURIComponent(token)}/view`, headers: shareHeaders(password) })
}

export function shareDashboardPanelData(token: string, key: string, revision: number, input?: DashboardInput, password?: string): Promise<AjaxResult<PanelResult>> {
  return request({ url: `/bi/share/${encodeURIComponent(token)}/panels/${encodeURIComponent(key)}/data`, method: 'post', params: { revision }, data: input, headers: shareHeaders(password), timeout: 60000 })
}

export function shareDashboardControlOptions(token: string, key: string, revision: number, input: DashboardInput, password?: string): Promise<AjaxResult<{ values: (string | number)[]; truncated: boolean }>> {
  return request({ url: `/bi/share/${encodeURIComponent(token)}/controls/${encodeURIComponent(key)}/options`, method: 'post', params: { revision }, data: input, headers: shareHeaders(password), timeout: 60000 })
}

export function shareLinkUrl(token: string): string {
  const base = window.location.origin + window.location.pathname.replace(/\/$/, '')
  return `${base}#/bi-share/${token}`
}
