import request from '@/utils/request'
import { notifyBiSaved } from '@/utils/biThumbnail'
import type { BiResult as AjaxResult } from './response'
import type { GrantPrincipal, ResourceGrant } from './datasource'
import type { ChartType } from '@/utils/biChartTypes'

export interface Field { name: string; showName?: string; type: string; dimFlag?: string }
export interface Condition { field: string; op: string; value?: string | number | boolean | (string | number)[] }
export type FilterNode = Condition | { logic: 'AND' | 'OR'; children: FilterNode[] }
export interface QueryModel {
  datasetId: number; dimensions: { field: string; alias?: string }[]
  metrics: { field: string; agg: string; alias?: string }[]
  filters?: Condition[]; filterTree?: FilterNode; orderBys?: {field: string; order: string}[]; limit?: number
}
export interface ChartVisualStyle {
  showLegend?: boolean
  legendPosition?: 'top' | 'bottom'
  showTitle?: boolean
  title?: string
  palette?: 'business' | 'ocean' | 'warm'
  axisLabelRotate?: number
}
export interface ChartAdvancedOptions {
  nullMode?: 'keep' | 'zero' | 'hide'
  smoothLine?: boolean
  pivotRowFields?: string[]
  pivotColFields?: string[]
  mapScope?: string
  drillFields?: string[]
}
export interface ChartDefinition {
  schemaVersion: 1
  name: string
  type: ChartType
  query: QueryModel
  style?: ChartVisualStyle
  advanced?: ChartAdvancedOptions
}
export interface ChartSummary { id: number; name: string; type: ChartDefinition['type']; datasetId: number; version: number; resourceActions: string[] }
export interface ChartResult { columns: string[]; rows: Record<string, unknown>[]; rowCount: number; costMs: number }
export const chartFields = (id: number): Promise<AjaxResult<Field[]>> => request({url:`/bi/chart/dataset/${id}/fields`})
export const listCharts = (): Promise<AjaxResult<ChartSummary[]>> => request({url:'/bi/chart/saved'})
export const getChart = (id: number): Promise<AjaxResult<{id:number;version:number;definition:ChartDefinition}>> => request({url:`/bi/chart/saved/${id}`})
export const saveChart = (definition: ChartDefinition, id?: number, version?: number, templateLease?: boolean): Promise<AjaxResult<{id:number;version:number}>> =>request({url:id?`/bi/chart/saved/${id}`:'/bi/chart/saved',method:id?'put':'post',data:{definition,version,templateLease}}).then((response:AjaxResult<{id:number;version:number}>)=>{notifyBiSaved('chart',response.data.id);return response})
export const commitTemplateChartLeases = (chartIds: number[]): Promise<AjaxResult<{committed:number}>> => request({url:'/bi/chart/template-lease/commit',method:'post',data:{chartIds}})
export const releaseTemplateChartLeases = (chartIds: number[]): Promise<AjaxResult<{released:number}>> => request({url:'/bi/chart/template-lease/release',method:'post',data:{chartIds}})
export const deleteChart = (id: number): Promise<AjaxResult> => request({url:`/bi/chart/saved/${id}`,method:'delete'})
export const previewChart = (query: QueryModel): Promise<AjaxResult<ChartResult>> => request({url:'/bi/chart/data',method:'post',data:query,timeout:60000})
export const savedChartData = (id: number): Promise<AjaxResult<ChartResult>> => request({url:`/bi/chart/saved/${id}/data`,method:'post',timeout:60000})
export const searchChartPrincipals = (query=''): Promise<AjaxResult<GrantPrincipal[]>> => request({url:'/bi/chart/principals',params:{query}})
export const listChartGrants = (id: number): Promise<AjaxResult<ResourceGrant[]>> => request({url:`/bi/chart/saved/${id}/grants`})
export const grantChart = (id: number, principalId: number, action: string): Promise<AjaxResult> => request({url:`/bi/chart/saved/${id}/grants`,method:'post',data:{principalId,action}})
export const revokeChartGrant = (id: number, principalId: number, action: string): Promise<AjaxResult> => request({url:`/bi/chart/saved/${id}/grants/${principalId}/${action}`,method:'delete'})
