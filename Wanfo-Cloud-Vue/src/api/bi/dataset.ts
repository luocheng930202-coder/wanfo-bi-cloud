import request from '@/utils/request'
export interface DataPolicyRule { id?: number; principalId: number; fieldName: string; ruleKind: string; ruleValue?: string; createBy?: string }
export function listDataPolicies(id: number): Promise<AjaxResult<DataPolicyRule[]>> { return request({ url: `/bi/dataset/${id}/policies` }) }
export function addDataPolicy(id: number, data: DataPolicyRule): Promise<AjaxResult> { return request({ url: `/bi/dataset/${id}/policies`, method: 'post', data }) }
export function removeDataPolicy(id: number, ruleId: number): Promise<AjaxResult> { return request({ url: `/bi/dataset/${id}/policies/${ruleId}`, method: 'delete' }) }
import type { BiResult as AjaxResult } from './response'

export interface CatalogColumn {
  name: string
  type: string
  size: number
  nullable: boolean
  comment: string
  primaryKey: boolean
  /** 服务端建议：metric / dimension */
  suggestAs: string
}

export interface CatalogTable {
  name: string
  type: string
  comment: string
  catalog: string
}

export interface DatasetField {
  id?: number
  datasetId?: number
  originName: string
  originType?: string
  showName: string
  /** 1 维度 0 度量 */
  dimFlag: string
  extFlag: string
  extFormula?: string
  groupName?: string
  orderNum?: number
}

export interface DatasetGroupNode {
  id: number
  parentId: number
  name: string
  orderNum: number
  children: DatasetGroupNode[]
}

export interface DatasetSummary {
  id: number
  groupId: number
  name: string
  type: string
  datasourceId: number
  sourceTable: string
  sourceCatalog?: string
  resourceActions?: string[]
  fieldCount: number
}

export interface DatasetDetail extends DatasetSummary {
  sqlText: string | null
  unionSql: string
  modelJson?: string | null
  cacheEnabled: string
  cacheTtl: number
  remark?: string
  fields: DatasetField[]
}

export interface DatasetJoinModel {
  joinType?: string
  rightTable: string
  leftKey: string
  rightKey: string
  rightFields: string[]
}

// ==================== 元数据目录 ====================

export function listCatalogs(schemaName: string): Promise<AjaxResult<string[]>> {
  return request({
    url: '/bi/catalog/' + schemaName + '/catalogs',
    method: 'get'
  })
}

export function listTables(schemaName: string, catalog?: string): Promise<AjaxResult<CatalogTable[]>> {
  return request({
    url: '/bi/catalog/' + schemaName + '/tables',
    method: 'get',
    params: { catalog }
  })
}

export function listColumns(schemaName: string, table: string, catalog?: string): Promise<AjaxResult<CatalogColumn[]>> {
  return request({
    url: '/bi/catalog/' + schemaName + '/columns',
    method: 'get',
    params: { table, catalog }
  })
}

// ==================== 目录 ====================

export function getGroupTree(): Promise<AjaxResult<DatasetGroupNode[]>> {
  return request({
    url: '/bi/dataset/group/tree',
    method: 'get'
  })
}

export function createGroup(name: string, parentId?: number): Promise<AjaxResult> {
  return request({
    url: '/bi/dataset/group',
    method: 'post',
    data: { name, parentId }
  })
}

export function deleteGroup(id: number): Promise<AjaxResult> {
  return request({
    url: '/bi/dataset/group/' + id,
    method: 'delete'
  })
}

// ==================== 数据集 ====================

export function listDatasets(groupId?: number): Promise<AjaxResult<DatasetSummary[]>> {
  return request({
    url: '/bi/dataset/list',
    method: 'get',
    params: { groupId }
  })
}

export function getDataset(id: number): Promise<AjaxResult<DatasetDetail>> {
  return request({
    url: '/bi/dataset/' + id,
    method: 'get'
  })
}

export interface DatasetQualityRule {field:string;required:boolean;unique:boolean;maxNullRate?:number|null}
export interface DatasetQualityColumn {field:string;nullCount:number;nonNullCount:number;distinctCount:number;duplicateValueCount:number;nullRate:number;violations:string[];qualityStatus:'passed'|'failed'|'unconfigured'}
export interface DatasetQualityReport {scanMode:'sample'|'full';sampleLimit?:number;sampledRows?:number;scannedRows?:number;duplicateRows:number;costMs?:number;note:string;ruleNote:string;qualityStatus:string;ruleFailureCount:number;columns:DatasetQualityColumn[]}
export function getDatasetQuality(id:number,fullScan=false):Promise<AjaxResult<DatasetQualityReport>>{
  return request({url:`/bi/dataset/${id}/quality`,method:'get',params:{fullScan},timeout:250000})
}
export function getDatasetQualityRules(id:number):Promise<AjaxResult<DatasetQualityRule[]>>{return request({url:`/bi/dataset/${id}/quality/rules`,method:'get'})}
export function saveDatasetQualityRules(id:number,rules:DatasetQualityRule[]):Promise<AjaxResult<DatasetQualityRule[]>>{return request({url:`/bi/dataset/${id}/quality/rules`,method:'put',data:{rules}})}

export interface TableDatasetFieldInput {
  name: string
  showName?: string
  dimFlag?: '0' | '1'
}

export interface TableDatasetForm {
  name: string
  groupId?: number
  datasourceId: number
  sourceTable: string
  sourceCatalog?: string
  fields: (string | TableDatasetFieldInput)[]
  remark?: string
}

export function updateDatasetField(datasetId: number, fieldId: number, data: { showName?: string; dimFlag?: '0' | '1' }): Promise<AjaxResult> {
  return request({ url: `/bi/dataset/${datasetId}/fields/${fieldId}`, method: 'put', data })
}

export function createTableDataset(data: TableDatasetForm): Promise<AjaxResult> {
  return request({
    url: '/bi/dataset/table',
    method: 'post',
    data
  })
}

export interface SqlDatasetForm {
  name: string
  groupId?: number
  datasourceId: number
  sqlText: string
  fields?: DatasetField[]
  remark?: string
}

export function createSqlDataset(data: SqlDatasetForm): Promise<AjaxResult> {
  return request({
    url: '/bi/dataset/sql',
    method: 'post',
    data
  })
}

export interface PreviewResult {
  ok: boolean
  datasetId: number
  datasetName?: string
  columns: string[]
  rows: Record<string, unknown>[]
  rowCount: number
  costMs: number
  executedSql: string
  message?: string
}

export function previewDataset(id: number, maxRows = 100): Promise<AjaxResult<PreviewResult>> {
  return request({
    url: '/bi/dataset/' + id + '/preview',
    method: 'post',
    params: { maxRows }
  })
}

export function deleteDataset(id: number): Promise<AjaxResult> {
  return request({
    url: '/bi/dataset/' + id,
    method: 'delete'
  })
}

export function getDatasetModel(id: number): Promise<AjaxResult<{ datasetId: number; modelJson?: string; join?: DatasetJoinModel }>> {
  return request({ url: `/bi/dataset/${id}/model`, method: 'get' })
}

export function applyDatasetJoin(id: number, data: DatasetJoinModel): Promise<AjaxResult> {
  return request({ url: `/bi/dataset/${id}/model/join`, method: 'post', data })
}

export function clearDatasetJoin(id: number): Promise<AjaxResult> {
  return request({ url: `/bi/dataset/${id}/model/join`, method: 'delete' })
}

export function addCalculatedField(id: number, data: { originName: string; showName?: string; formula: string; dimFlag?: string }): Promise<AjaxResult> {
  return request({ url: `/bi/dataset/${id}/fields/calculated`, method: 'post', data })
}

export function removeCalculatedField(id: number, fieldId: number): Promise<AjaxResult> {
  return request({ url: `/bi/dataset/${id}/fields/calculated/${fieldId}`, method: 'delete' })
}


export interface ModelDatasource { id: number; name: string; schemaName: string; type: string }
export function listModelSources(): Promise<AjaxResult<ModelDatasource[]>> {
  return request({ url: '/bi/catalog/sources', method: 'get' })
}
export function previewTable(schemaName: string, data: { catalog: string; table: string; fields: string[]; maxRows?: number }): Promise<AjaxResult<PreviewResult>> {
  return request({ url: `/bi/catalog/${encodeURIComponent(schemaName)}/preview`, method: 'post', data, timeout: 20000 })
}
import type { GrantPrincipal, ResourceGrant } from './datasource'
export function searchDatasetPrincipals(query: string): Promise<AjaxResult<GrantPrincipal[]>> {
  return request({ url: '/bi/dataset/principals', params: { query } })
}
export function listDatasetGrants(id: number): Promise<AjaxResult<ResourceGrant[]>> {
  return request({ url: `/bi/dataset/${id}/grants` })
}
export function grantDataset(id: number, principalId: number, action: string): Promise<AjaxResult> {
  return request({ url: `/bi/dataset/${id}/grants`, method: 'post', data: { principalId, action } })
}
export function revokeDatasetGrant(id: number, principalId: number, action: string): Promise<AjaxResult> {
  return request({ url: `/bi/dataset/${id}/grants/${principalId}/${action}`, method: 'delete' })
}
