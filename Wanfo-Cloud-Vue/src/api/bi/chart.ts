import request from '@/utils/request'
import {queryCancellationHeaders} from '@/utils/biQueryCancellation'
import { notifyBiSaved } from '@/utils/biThumbnail'
import type { BiResult as AjaxResult } from './response'
import type { GrantPrincipal, ResourceGrant } from './datasource'
import type { ChartType } from '@/utils/biChartTypes'
import type { NumberFormat, Threshold } from './dashboard'

export interface ChartMetricStyle { key:string; format?:NumberFormat|null; seriesType?:'bar'|'line'; axis?:'left'|'right'; color?:string; rules?:Threshold[] }
export interface ChartReferenceLine { key:string; kind:'fixed'|'average'|'min'|'max'; value?:number; name:string; color:string; lineType:'solid'|'dashed'|'dotted' }

export interface Field { name: string; showName?: string; type: string; dimFlag?: string }
export interface Condition { field: string; op: string; value?: string | number | boolean | (string | number)[] }
export type FilterNode = Condition | { logic: 'AND' | 'OR'; children: FilterNode[] }
export type TimeGrain = 'day'|'week'|'month'|'quarter'|'year'
export type TimeFill = 'none'|'null'|'zero'
export interface QueryDimension { field:string; alias?:string; timeGrain?:TimeGrain|null; timeFill?:TimeFill|null }
export interface QueryModel {
  topN?:{count:number;metric:string;includeOther:boolean}
  timeComparison?:'previous'|'year'
  pivot?:{rows:string[];columns:string[]}
  datasetId: number; dimensions: QueryDimension[]
  metrics: { field: string; agg: string; alias?: string }[]
  filters?: Condition[]; filterTree?: FilterNode; orderBys?: {field: string; order: string}[]; limit?: number
}
export interface TableColumnStyle { key:string; width?:number; align?:'left'|'center'|'right'; fixed?:'none'|'left'|'right'; wrap?:boolean; backgroundRules?:Threshold[] }
export interface ChartVisualStyle {
  pivotRowTotals?:boolean
  pivotColTotals?:boolean
  pivotRowSubtotals?:boolean
  pivotColSubtotals?:boolean
  pivotTotalsFirst?:boolean
  pivotValuesInCols?:boolean
  pivotHierarchy?:'grid'|'tree'
  pivotRowHeight?:number
  pivotColumnWidth?:number
  pivotColorScale?:boolean
  pivotLowColor?:string
  pivotHighColor?:string
  pivotHeaderBackground?:string
  pivotHeaderColor?:string

  tableColumns?:TableColumnStyle[]
  tableStripe?:boolean
  tableBorder?:boolean
  tableRowHeight?:number
  tableNullText?:string
  tableHeaderColor?:string
  tableHeaderBackground?:string
  funnelAlign?: 'left'|'center'|'right'
  funnelGap?: number
  funnelMinSize?: number
  funnelMaxSize?: number
  funnelLabelPosition?: 'inside'|'left'|'right'
  funnelLabelContent?: 'name'|'value'|'nameValue'
  mapColorMode?: 'continuous'|'piecewise'
  mapLowColor?: string
  mapHighColor?: string
  mapNoDataColor?: string
  mapBorderColor?: string
  mapLabelContent?: 'name'|'value'|'nameValue'
  mapPieceColors?: (string|null)[]
  mapBreaks?: number[]
  mapShowLabels?: boolean
  mapShowTooltip?: boolean
  radarShape?: 'polygon'|'circle'
  radarSplitNumber?: number
  radarShowSplitArea?: boolean
  radarShowAxisName?: boolean
  scatterSymbol?: 'circle'|'rect'|'roundRect'|'triangle'|'diamond'
  scatterSize?: number
  scatterOpacity?: number
  scatterBorderWidth?: number
  scatterBorderColor?: string
  scatterLabelContent?: 'category'|'coordinates'

  metrics?: ChartMetricStyle[]
  referenceLines?: ChartReferenceLine[]
  comboAxes?: {left?:{min?:number;max?:number};right?:{min?:number;max?:number}}
  showLegend?: boolean
  legendPosition?: 'top' | 'bottom'
  showTitle?: boolean
  title?: string
  palette?: 'business' | 'ocean' | 'warm'
  axisLabelRotate?: number
  showLabels?: boolean
  barMaxWidth?: number
  barRadius?: number
  lineWidth?: number
  symbolSize?: number
  showPoints?: boolean
  areaOpacity?: number
  pieInnerRadius?: number
  pieOuterRadius?: number
  pieLabelPosition?: 'inside' | 'outside'
  startAngle?: number
  ringShowTotal?: boolean
  gaugeMin?: number
  gaugeMax?: number
  gaugeStyle?: 'pointer' | 'progress'
}
export interface ChartAnalysis {
  funnelRate?:'adjacent'|'first'
  funnelStages?:string[]
  funnelFillZero?:boolean
  radarNormalize?:boolean
  radarAxes?:{key:string;min:number;max:number;target?:number}[]
  scatterQuadrants?:boolean
  scatterXSplit?:number
  scatterYSplit?:number
  scatterZoom?:boolean
  scatterLogX?:boolean
  scatterLogY?:boolean
}
export interface ChartAdvancedOptions {
  analysis?:ChartAnalysis
  mapFields?:{province?:string;city?:string;county?:string}
  mapValueMode?:'name'|'code'
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
export interface MapContext {fieldLabel?:string;scope:string;rootScope:string;rootName:string;field:string;canDrillDown:boolean;drillableRegions:string[];breadcrumbs:{name:string;code:string}[]}
export interface PivotTotals {status:'complete'|'unavailable';scope:'filtered';rowFields:string[];colFields:string[];metricKeys:string[];detailTruncated:boolean;reason?:string;cells:{rowDepth:number;colDepth:number;dimensions:Record<string,unknown>;values:Record<string,unknown>}[]}
export interface ComparisonField {key:string;baseline:string;difference:string;rate:string}
export interface ChartResult { topN?:{count:number;includeOther:boolean;scope:string;otherRowIndexes:number[];note:string}; queryDiagnostics?:{requestId:string;slow:boolean;durationMs:number}; timeComparison?:{mode:'previous'|'year';label:string;grain:TimeGrain;status:'complete'|'skipped';fields:ComparisonField[];note?:string;reason?:string;missingValues?:number;zeroBases?:number};columnLabels?:Record<string,string>;pivotTotals?:PivotTotals; columns: string[]; rows: Record<string, unknown>[]; rowCount: number; costMs: number;mapContext?:MapContext; timeFill?:{mode:'null'|'zero';status:'complete'|'skipped';sourceRows:number;addedRows:number;periods:string[];reason?:string} }
export const chartFields = (id: number): Promise<AjaxResult<Field[]>> => request({url:`/bi/chart/dataset/${id}/fields`})
export const listCharts = (): Promise<AjaxResult<ChartSummary[]>> => request({url:'/bi/chart/saved'})
export const getChart = (id: number, signal?:AbortSignal): Promise<AjaxResult<{id:number;version:number;definition:ChartDefinition}>> => request({url:`/bi/chart/saved/${id}`,signal})
export const saveChart = (definition: ChartDefinition, id?: number, version?: number, templateLease?: boolean): Promise<AjaxResult<{id:number;version:number}>> =>request({url:id?`/bi/chart/saved/${id}`:'/bi/chart/saved',method:id?'put':'post',data:{definition,version,templateLease}}).then((response:AjaxResult<{id:number;version:number}>)=>{notifyBiSaved('chart',response.data.id);return response})
export const commitTemplateChartLeases = (chartIds: number[]): Promise<AjaxResult<{committed:number}>> => request({url:'/bi/chart/template-lease/commit',method:'post',data:{chartIds}})
export const releaseTemplateChartLeases = (chartIds: number[]): Promise<AjaxResult<{released:number}>> => request({url:'/bi/chart/template-lease/release',method:'post',data:{chartIds}})
export const deleteChart = (id: number): Promise<AjaxResult> => request({url:`/bi/chart/saved/${id}`,method:'delete'})
export const previewChart = (query: QueryModel, signal?: AbortSignal): Promise<AjaxResult<ChartResult>> => request({url:'/bi/chart/data',method:'post',data:query,timeout:60000,signal,headers:queryCancellationHeaders(signal)})
export const savedChartData = (id: number, signal?: AbortSignal,input?:unknown): Promise<AjaxResult<ChartResult>> => request({url:`/bi/chart/saved/${id}/data`,method:'post',data:input,timeout:60000,signal,headers:queryCancellationHeaders(signal)})
export const previewMapChart=(definition:ChartDefinition,input:unknown,signal?:AbortSignal):Promise<AjaxResult<ChartResult>>=>request({url:'/bi/chart/map-preview',method:'post',data:{definition,input},timeout:60000,signal,headers:queryCancellationHeaders(signal)})
export const searchChartPrincipals = (query=''): Promise<AjaxResult<GrantPrincipal[]>> => request({url:'/bi/chart/principals',params:{query}})
export const listChartGrants = (id: number): Promise<AjaxResult<ResourceGrant[]>> => request({url:`/bi/chart/saved/${id}/grants`})
export const grantChart = (id: number, principalId: number, action: string): Promise<AjaxResult> => request({url:`/bi/chart/saved/${id}/grants`,method:'post',data:{principalId,action}})
export const revokeChartGrant = (id: number, principalId: number, action: string): Promise<AjaxResult> => request({url:`/bi/chart/saved/${id}/grants/${principalId}/${action}`,method:'delete'})
