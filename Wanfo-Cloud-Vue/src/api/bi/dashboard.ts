import request from '@/utils/request'
import {queryCancellationHeaders} from '@/utils/biQueryCancellation'
import { notifyBiSaved } from '@/utils/biThumbnail'
import type { BiResult as AjaxResult } from './response'
import type { GrantPrincipal, ResourceGrant } from './datasource'
import type { ChartResult, ChartDefinition, QueryModel } from './chart'
export interface DashboardAppearance {theme:'light'|'dark';background:string;cardBackground:string;textColor:string;borderColor:string;palette:'business'|'ocean'|'warm';titleSize:number;radius:number;decimals:number;grouping:boolean}
export interface NumberFormat {mode:'number'|'percent';scale:'none'|'thousand'|'wan'|'million'|'yi';decimals:number;grouping:boolean;fixed:boolean;prefix:string;suffix:string;nullText:string}
export interface Threshold {op:'GT'|'GTE'|'LT'|'LTE'|'EQ'|'NE';value:string;color:string}
export interface MetricStyle {key:string;format?:NumberFormat|null;rules:Threshold[]}
export interface PanelStyle {metrics?:MetricStyle[];numberFormat?:NumberFormat|null;showTitle:boolean;titleAlign:'left'|'center'|'right'}
export type ComponentType = 'chart'|'kpi'|'text'|'image'|'border'|'line'|'container'
export interface Decoration {color:string;stroke:number;pattern:'solid'|'dashed'|'dotted';radius:number;direction:'horizontal'|'vertical'}
export interface ContainerStyle {background:string;borderColor:string;stroke:number;radius:number;padding:number}
export interface PanelContent {container?:ContainerStyle;decoration?:Decoration;text?:string|null;image?:string|null}
export type PanelChartConfig = Pick<QueryModel,'dimensions'|'metrics'> & {type:ChartDefinition['type'];orderBys:NonNullable<QueryModel['orderBys']>;limit:number;filterTree?:QueryModel['filterTree']}
export interface Panel { parentKey?:string;hidden?:boolean;groupKey?:string;key:string;chartId?:number|null;title:string;x:number;y:number;w:number;h:number;componentType?:ComponentType;style?:PanelStyle;content?:PanelContent;locked?:boolean;chartConfig?:PanelChartConfig }
export type PanelLayout = Pick<Panel,'key'|'x'|'y'|'w'|'h'|'locked'|'componentType'|'groupKey'|'hidden'|'parentKey'|'content'>
export type FilterValue = string | number | [string|number|null,string|number|null] | null
export interface FilterTarget {panel:string;field:string;valueType?:string}
export interface FilterControl {key:string;label:string;type:'text'|'enum'|'number'|'date';defaultValue:FilterValue;targets:FilterTarget[]}
export interface DashboardLink {key:string;sourcePanel:string;sourceField:string;targets:FilterTarget[]}
export interface DashboardInteraction {controls:FilterControl[];links:DashboardLink[]}
export interface DrillPathSegment { field: string; value: string | number | null }
export interface DashboardInput {filters:Record<string,FilterValue>;links:Record<string,string|number|null>;drills?:Record<string,DrillPathSegment[]>}
export interface ViewControl {key:string;label:string;type:FilterControl['type'];defaultValue:FilterValue}
export interface ScreenScene {width:number;height:number;fit:'contain'|'width'|'none';refreshSeconds?:number}
export interface DashboardDefinition {schemaVersion:1;name:string;panels:Panel[];kind?:'dashboard'|'screen';layout?:'grid'|'absolute';scene?:ScreenScene;appearance?:DashboardAppearance;interaction?:DashboardInteraction}
export interface DashboardSummary {id:number;name:string;version:number;published:boolean;resourceActions:string[]}
export interface ViewPanel {parentKey?:string;key:string;title:string;type:ChartDefinition['type'];x:number;y:number;w:number;h:number;componentType?:ComponentType;style?:PanelStyle;content?:PanelContent;drillFields?:string[]}
export interface DashboardView {id:number;name:string;revision:number;panels:ViewPanel[];kind?:'dashboard'|'screen';layout?:'grid'|'absolute';scene?:ScreenScene;appearance?:DashboardAppearance;controls?:ViewControl[];links?:{key:string;sourcePanel:string}[]}
export interface PanelResult {data:ChartResult;presentation:{style?:ChartDefinition['style'];advanced?:ChartDefinition['advanced'];type:ChartDefinition['type'];dimensions:string[];metrics:string[];fieldLabels?:Record<string,string>;queryDimensions?:QueryModel['dimensions'];queryMetrics?:QueryModel['metrics'];pivotRowFields?:string[];pivotColFields?:string[];drillFields?:string[];drillPath?:DrillPathSegment[];drillLevel?:number;canDrillDown?:boolean}}
export const listDashboards=():Promise<AjaxResult<DashboardSummary[]>>=>request({url:'/bi/dashboard'})
export const getDashboardDraft=(id:number):Promise<AjaxResult<{id:number;version:number;definition:DashboardDefinition;published:boolean;resourceActions?:string[]}>>=>request({url:`/bi/dashboard/${id}/draft`})
export const saveDashboard=(definition:DashboardDefinition,id?:number,version?:number):Promise<AjaxResult<{id:number;version:number}>>=>request({url:id?`/bi/dashboard/${id}`:'/bi/dashboard',method:id?'put':'post',data:{definition,version}}).then((response:AjaxResult<{id:number;version:number}>)=>{notifyBiSaved('dashboard',response.data.id);return response})
export const publishDashboard=(id:number,version:number):Promise<AjaxResult<{version:number;revision:number}>>=>request({url:`/bi/dashboard/${id}/publish`,method:'post',data:{version}})
export const unpublishDashboard=(id:number,version:number):Promise<AjaxResult<{version:number}>>=>request({url:`/bi/dashboard/${id}/unpublish`,method:'post',data:{version}})
export const deleteDashboard=(id:number):Promise<AjaxResult>=>request({url:`/bi/dashboard/${id}`,method:'delete'})
export const getDashboardView=(id:number):Promise<AjaxResult<DashboardView>>=>request({url:`/bi/dashboard/${id}/view`})
export const dashboardPanelData=(id:number,key:string,revision:number,input?:DashboardInput,signal?:AbortSignal):Promise<AjaxResult<PanelResult>>=>request({url:`/bi/dashboard/${id}/panels/${encodeURIComponent(key)}/data`,method:'post',params:{revision},data:input,timeout:60000,signal,headers:queryCancellationHeaders(signal)})
export const searchDashboardPrincipals=(query=''):Promise<AjaxResult<GrantPrincipal[]>>=>request({url:'/bi/dashboard/principals',params:{query}})
export const listDashboardGrants=(id:number):Promise<AjaxResult<ResourceGrant[]>>=>request({url:`/bi/dashboard/${id}/grants`})
export const grantDashboard=(id:number,principalId:number,action:string):Promise<AjaxResult>=>request({url:`/bi/dashboard/${id}/grants`,method:'post',data:{principalId,action}})
export const revokeDashboardGrant=(id:number,principalId:number,action:string):Promise<AjaxResult>=>request({url:`/bi/dashboard/${id}/grants/${principalId}/${action}`,method:'delete'})

export const dashboardControlOptions=(id:number,key:string,revision:number,input:DashboardInput):Promise<AjaxResult<{values:(string|number)[];truncated:boolean}>>=>request({url:`/bi/dashboard/${id}/controls/${encodeURIComponent(key)}/options`,method:'post',params:{revision},data:input,timeout:60000})

export const previewDashboard=(definition:DashboardDefinition,input:DashboardInput,id?:number,version?:number):Promise<AjaxResult<{view:DashboardView;payloads:Record<string,PanelResult>}>>=>request({url:id?`/bi/dashboard/${id}/preview`:'/bi/dashboard/preview',method:'post',data:{definition,input,version},timeout:60000})
export const previewDashboardOptions=(key:string,definition:DashboardDefinition,input:DashboardInput,id?:number,version?:number):Promise<AjaxResult<{values:(string|number)[];truncated:boolean}>>=>request({url:id?`/bi/dashboard/${id}/preview/options/${encodeURIComponent(key)}`:`/bi/dashboard/preview/options/${encodeURIComponent(key)}`,method:'post',data:{definition,input,version},timeout:60000})
