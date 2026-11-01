import request from '@/utils/request'
import type { BiResult } from './response'

export interface WarehouseLoadRequest {
  sourceDatasourceId: number
  targetDatasourceId: number
  sourceTable: string
  targetTable: string
  maxRows?: number
  loadMode?: 'FULL' | 'INCREMENTAL'
  watermarkColumn?: string
  keyColumns?: string[]
  targetLayer?: 'ODS' | 'DWD' | 'DWS' | 'ADS' | string
  partitionColumn?: string
  partitionFrom?: string
  partitionTo?: string
  filterDatasetId?: number
  parallelSlices?: number
  /** REPLACE | MERGE；缺省时 INCREMENTAL→MERGE、FULL→REPLACE */
  publishMode?: 'REPLACE' | 'MERGE'
}

export interface WarehouseLoadOpsOverview {
  activeRuns: number
  staleRuns: number
  recentRuns: WarehouseLoadRun[]
  note?: string
}

export interface WarehouseLoadRun {
  id: number
  sourceDatasourceId: number
  targetDatasourceId: number
  sourceTable: string
  targetDatabase: string
  targetTable: string
  loadMode: 'FULL' | 'INCREMENTAL' | 'MODEL'
  publishMode?: 'REPLACE' | 'MERGE' | string
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'INTERRUPTED'
  promoteStatus?: 'STAGED' | 'VALIDATING' | 'PROMOTING' | 'SUCCEEDED' | 'FAILED' | string
  rowCount: number
  targetReplaced: boolean | number
  errorSummary?: string
  message?: string
  traceId?: string
  triggerBy?: string
  startedAt?: string
  finishedAt?: string
  createTime?: string
}

export interface WarehouseReconciliation {
  runId: number
  targetDatasourceId: number
  modelVersion: string
  sourceTable: string
  targetTable: string
  checkScope: 'FULL' | 'SAMPLE'
  sourceRowCount: number
  targetRowCount: number
  sourceSalesAmount: number | string
  targetSalesAmount: number | string
  sourceNetQuantity: number | string
  targetNetQuantity: number | string
  sourceMaxUpdatedAt?: string
  observedAt?: string
  freshnessAgeSeconds?: number
  status: 'PREPARED' | 'PASS' | 'FAIL'
  summary?: string
}

export function triggerWarehouseLoad(data: WarehouseLoadRequest): Promise<BiResult<WarehouseLoadRun>> {
  return request({ url: '/bi/warehouse/loads', method: 'post', data, timeout: 30_000, headers: { repeatSubmit: false } })
}

export function refreshRetailMonthlyModel(targetDatasourceId: number): Promise<BiResult<WarehouseLoadRun>> {
  return request({ url: '/bi/warehouse/models/retail-v1/monthly/refresh', method: 'post', params: { targetDatasourceId }, timeout: 30_000, headers: { repeatSubmit: false } })
}

export function getRetailMonthlyReconciliation(targetDatasourceId: number): Promise<BiResult<WarehouseReconciliation>> {
  return request({ url: '/bi/warehouse/models/retail-v1/reconciliation', params: { targetDatasourceId } })
}

export function listWarehouseLoadRuns(targetDatasourceId: number): Promise<BiResult<WarehouseLoadRun[]>> {
  return request({ url: '/bi/warehouse/loads/runs', params: { targetDatasourceId } })
}

export function getWarehouseLoadRun(id: number): Promise<BiResult<WarehouseLoadRun>> {
  return request({ url: `/bi/warehouse/loads/runs/${id}` })
}

export function opsOverview(): Promise<BiResult<WarehouseLoadOpsOverview>> {
  return request({ url: '/bi/warehouse/loads/ops' })
}

export function retryWarehouseLoad(id: number): Promise<BiResult<WarehouseLoadRun>> {
  return request({ url: `/bi/warehouse/loads/runs/${id}/retry`, method: 'post', timeout: 30_000, headers: { repeatSubmit: false } })
}
