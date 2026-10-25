import request from '@/utils/request'
import type { BiResult as AjaxResult } from './response'

export interface BiDatasource {
  id: number
  name: string
  type: string
  schemaName: string
  sourceSchema?: string
  jdbcUrl: string
  driverClass: string
  username: string
  status: string
  remark?: string
  /** 管理列表用：是否已设置密码（不返回原文） */
  passwordSet?: boolean
  /** 运行时列表用：脱敏后的密码 */
  password?: string
  resourceActions?: string[]
  poolMaxTotal?: number
  queryTimeout?: number
  createTime?: string
}

export interface DatasourceForm {
  id?: number
  name: string
  type: string
  schemaName: string
  sourceSchema?: string
  jdbcUrl: string
  driverClass?: string
  username: string
  password?: string
  poolMaxTotal?: number
  queryTimeout?: number
  remark?: string
  status?: string
}

/** 管理列表（含停用，密码只返回是否已设置） */
export function listDatasources(): Promise<AjaxResult<BiDatasource[]>> {
  return request({
    url: '/bi/datasource/manage',
    method: 'get'
  })
}

/** 运行时列表（仅启用） */
export function listEnabledDatasources(): Promise<AjaxResult<BiDatasource[]>> {
  return request({
    url: '/bi/datasource/list',
    method: 'get'
  })
}

export function getDatasource(id: number): Promise<AjaxResult<BiDatasource>> {
  return request({
    url: '/bi/datasource/' + id,
    method: 'get'
  })
}

export interface DatasourceMutationResult {id?:number;rolledBack?:boolean;reachable?:boolean;probe:ConnectionTestResult}
export function createDatasource(data: DatasourceForm): Promise<AjaxResult<DatasourceMutationResult>> {
  return request({
    url: '/bi/datasource',
    method: 'post',
    data
  })
}

export function updateDatasource(data: DatasourceForm): Promise<AjaxResult<DatasourceMutationResult>> {
  return request({
    url: '/bi/datasource',
    method: 'put',
    data
  })
}

export function changeDatasourceStatus(id: number, status: string): Promise<AjaxResult> {
  return request({
    url: '/bi/datasource/' + id + '/status',
    method: 'put',
    data: { status }
  })
}

export function deleteDatasource(id: number): Promise<AjaxResult> {
  return request({
    url: '/bi/datasource/' + id,
    method: 'delete'
  })
}

/** 连通性测试（不落库） */
export function probeDatasource(data: DatasourceForm): Promise<AjaxResult<ConnectionTestResult>> {
  return request({
    url: '/bi/datasource/probe-test',
    method: 'post',
    data
  })
}

/** 批量探测已配置数据源 */
export function probeAll(): Promise<AjaxResult<Record<string, boolean>>> {
  return request({
    url: '/bi/datasource/probe',
    method: 'get'
  })
}

/** 从配置同步到数据库 */
export function syncFromConfig(): Promise<AjaxResult> {
  return request({
    url: '/bi/datasource/reload',
    method: 'post'
  })
}


/** Test saved credentials on the server; never send masked passwords back. */
export function testSavedDatasource(schemaName: string): Promise<AjaxResult<ConnectionTestResult>> {
  return request({ url: '/bi/datasource/' + encodeURIComponent(schemaName) + '/test', method: 'post' })
}

export interface ConnectionTestResult {
  ok: boolean
  costMs: number
  message?: string
  databaseProduct?: string
}

export interface ResourceGrant {
  principalId: number
  displayName: string
  principalType: string
  action: string
}
export interface GrantPrincipal { id: number; type: string; name: string }
export function searchGrantPrincipals(query: string): Promise<AjaxResult<GrantPrincipal[]>> {
  return request({ url: '/bi/datasource/principals', params: { query } })
}
export function listResourceGrants(id: number): Promise<AjaxResult<ResourceGrant[]>> {
  return request({ url: `/bi/datasource/${id}/grants` })
}
export function grantResource(id: number, principalId: number, action: string): Promise<AjaxResult> {
  return request({ url: `/bi/datasource/${id}/grants`, method: 'post', data: { principalId, action } })
}
export function revokeResource(id: number, principalId: number, action: string): Promise<AjaxResult> {
  return request({ url: `/bi/datasource/${id}/grants/${principalId}/${action}`, method: 'delete' })
}

export interface ApiPagination {pageParam:string;sizeParam:string;pageSize:number;startPage:number;maxPages:number;totalPath:string;cursorParam?:string;nextCursorPath?:string;initialCursor?:string}
export function importApiSource(data: {id?:number;name:string;schemaName:string;url:string;dataPath:string;headers?:Record<string,string>;pagination?:ApiPagination;refreshMinutes?:number;method?:string;body?:Record<string,unknown>;flatten?:boolean;expandArrayPath?:string;expandArrayPaths?:string[];oauthGrantId?:string;oauth?:{tokenUrl:string;clientId:string;clientSecret:string;scope:string;tokenField:string;grantType?:string}}): Promise<AjaxResult<{id:number;rowCount:number}>> {
  return request({url:'/bi/datasource/import/api',method:'post',data,timeout:30000,headers:{repeatSubmit:false}})
}
export function beginApiOAuth(data:{authorizationUrl:string;tokenUrl:string;clientId:string;redirectUri:string;scope:string;tokenField:string}):Promise<AjaxResult<{authorizationUrl:string;state:string}>>{return request({url:'/bi/datasource/import/api/oauth/authorize',method:'post',data})}
export function exchangeApiOAuth(data:{state:string;code:string;clientSecret:string}):Promise<AjaxResult<{grantId:string;status:string}>>{return request({url:'/bi/datasource/import/api/oauth/exchange',method:'post',data})}
export function importFileSource(data: FormData): Promise<AjaxResult<{id:number;rowCount:number;rejectedRows?:number;duplicateRows?:number;rowErrors?:{row:number;message:string}[]}>> {
  return request({url:'/bi/datasource/import/file',method:'post',data,timeout:30000,headers:{'Content-Type':'multipart/form-data',repeatSubmit:false}})
}
export function snapshotSourceInfo(id:number): Promise<AjaxResult<{url?:string;dataPath?:string;expandArrayPath?:string;expandArrayPaths?:string[];rowCount:number;version:number;pagination?:ApiPagination|null;method?:string;body?:Record<string,unknown>|null;flatten?:boolean;oauth?:{tokenUrl:string;clientId:string;scope:string;tokenField:string;secretSet:boolean;grantType?:string;refreshTokenSet?:boolean};schedule?:{intervalMinutes:number;nextRun?:string;lastStatus?:string}|null}>> {
  return request({url:`/bi/datasource/import/${id}/info`})
}
export function refreshApiSource(id:number): Promise<AjaxResult<{rowCount:number}>> {
  return request({url:`/bi/datasource/import/${id}/refresh`,method:'post',timeout:30000})
}

export interface FileSourcePreview {columns:{name:string;type:string}[];existingColumns:{name:string;type:string}[];rows:(string|null)[][];rowCount:number}
export function previewFileSource(data:FormData):Promise<AjaxResult<FileSourcePreview>> {
  return request({url:'/bi/datasource/import/file/preview',method:'post',data,timeout:30000,headers:{'Content-Type':'multipart/form-data',repeatSubmit:false}})
}
