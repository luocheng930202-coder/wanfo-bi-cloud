import request from '@/utils/request'
import type { BiResult as AjaxResult } from './response'
import type { LayoutTemplateFile } from '@/utils/biTemplateFiles'
import type { GrantPrincipal, ResourceGrant } from './datasource'
export interface SavedScreenTemplate {
  id: number
  name: string
  category: string
  description: string
  kind?: 'screen' | 'dashboard'
  content: LayoutTemplateFile
  resourceActions?: string[]
}
export interface TemplateSummary {
  id: number
  name: string
  category: string
  description: string
  kind?: 'screen' | 'dashboard'
  createBy?: string
  createTime?: string
  resourceActions?: string[]
}
export interface MarketTemplateEntry {
  id: string
  name: string
  category: string
  description: string
  kind: 'screen' | 'dashboard'
  provider: 'builtin' | 'remote'
}
export interface MarketTemplateDetail extends MarketTemplateEntry {
  content: LayoutTemplateFile
}
export const listTemplates = (): Promise<AjaxResult<SavedScreenTemplate[]>> => request({ url: '/bi/template' })
export const listTemplateSummaries = (): Promise<AjaxResult<TemplateSummary[]>> => request({ url: '/bi/template/summaries' })
export const listTemplateMarket = (): Promise<AjaxResult<MarketTemplateEntry[]>> => request({ url: '/bi/template/market' })
export const getMarketTemplate = (id: string): Promise<AjaxResult<MarketTemplateDetail>> => request({ url: `/bi/template/market/${encodeURIComponent(id)}` })
export const getTemplate = (id: number): Promise<AjaxResult<SavedScreenTemplate>> => request({ url: `/bi/template/${id}` })
export const saveTemplate = (data: { name: string; category: string; description: string; content: LayoutTemplateFile }, id?: number): Promise<AjaxResult<{ id: number }>> =>
  request({ url: id ? `/bi/template/${id}` : '/bi/template', method: id ? 'put' : 'post', data })
export const deleteTemplate = (id: number): Promise<AjaxResult> => request({ url: `/bi/template/${id}`, method: 'delete' })
export const searchTemplatePrincipals = (query = ''): Promise<AjaxResult<GrantPrincipal[]>> => request({ url: '/bi/template/principals', params: { query } })
export const listTemplateGrants = (id: number): Promise<AjaxResult<ResourceGrant[]>> => request({ url: `/bi/template/${id}/grants` })
export const grantTemplate = (id: number, principalId: number, action: string): Promise<AjaxResult> => request({ url: `/bi/template/${id}/grants`, method: 'post', data: { principalId, action } })
export const revokeTemplateGrant = (id: number, principalId: number, action: string): Promise<AjaxResult> => request({ url: `/bi/template/${id}/grants/${principalId}/${action}`, method: 'delete' })
