import request from '@/utils/request'
import type { BiResult as AjaxResult } from './response'

export interface BiDatasource {
  id: number
  name: string
  type: string
  schemaName: string
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
