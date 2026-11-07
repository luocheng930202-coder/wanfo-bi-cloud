import request from '@/utils/request'
import type { BiResult } from './response'

export type CatalogAssetType = 'SCHEMA' | 'TABLE' | 'COLUMN' | 'DATASOURCE' | 'API_SOURCE' | 'DATASET' | 'DATASET_FIELD' | 'CHART' | 'DASHBOARD' | 'METRIC'

export interface CatalogAsset {
  id: number
  assetType: CatalogAssetType
  sourceId: number
  sourceKind?: 'CONNECTOR' | 'BI_RESOURCE' | string
  parentId?: number | null
  name: string
  qualifiedName?: string
  description?: string
  status: 'ACTIVE' | 'STALE' | string
  lastSeenAt?: string
  currentVersionId?: number | null
  urn?: string
}

export interface CatalogAssetVersion {
  id: number
  versionNo: number
  schemaHash: string
  metadata: Record<string, unknown>
  changes: { changeType?: string; fields?: { added?: string[]; removed?: string[]; changed?: string[] }; [key: string]: unknown }
  scanRunId?: number
  observedAt?: string
}

export interface CatalogAssetPage {
  items: CatalogAsset[]
  total: number
  page: number
  pageSize: number
}

export interface CatalogBackfillPreview {
  DATASET: number
  DATASOURCE: number
  API_SOURCE: number
  DATASET_FIELD: number
  CHART: number
  DASHBOARD: number
  METRIC: number
  DATA_API: number
  total: number
  mode: 'DRY_RUN'
  copiedDefinitions: false
}

export interface CatalogAuditRow {
  id: number | string
  actorId?: number
  actorName: string
  action: string
  subjectType: string
  subjectId?: number
  sourceId?: number
  outcome: string
  traceId?: string
  summaryJson: string
  createdAt: string
}

export interface LineageNode {
  id: number
  assetType: CatalogAssetType
  name: string
  qualifiedName?: string
  status: string
  currentVersionId?: number | null
}

export interface LineageEdge {
  id: number
  sourceAssetId: number
  targetAssetId: number
  lineageLevel: 'TABLE' | 'COLUMN'
  edgeKind: string
  sourceMethod: string
  confidence: number
  effectiveStatus: string
  relationNote?: string
}

export interface LineageGraph {
  rootAssetId: number
  direction: 'both' | 'upstream' | 'downstream'
  depth: number
  nodes: LineageNode[]
  edges: LineageEdge[]
  truncated: boolean
  limits: { maxDepth: number; maxNodes: number; maxEdges: number }
}

export interface SchemaChangeEvent {
  id: number
  assetId: number
  previousVersionId?: number
  currentVersionId: number
  scanRunId?: number
  changeType: string
  actor: string
  observedAt: string
  details: { changeType?: string; fields?: Record<string, unknown>; [key: string]: unknown }
}

export interface SchemaChangeImpact {
  event: SchemaChangeEvent
  impactNodes: Array<Record<string, unknown>>
  impactEdges: Array<Record<string, unknown>>
  datasetFields: Array<Record<string, unknown>>
  qualityRules: Array<Record<string, unknown>>
  qualityTasks: Array<Record<string, unknown>>
  pipelines: Array<Record<string, unknown>>
  affectedFields: string[]
  renameCandidates: Array<{ from: string; to: string; confidence: string; reason: string }>
  reviewRequired: boolean
  truncated: boolean
  counts: { assets: number; datasetFields: number; qualityRules: number; qualityTasks: number; pipelines: number }
}

export function getLineageGraph(id: number, params: { direction: 'both' | 'upstream' | 'downstream'; depth: number; includeStale: boolean }): Promise<BiResult<LineageGraph>> {
  return request({ url: `/bi/catalog/lineage/graph/${id}`, params })
}

export function rebuildCatalogLineage(): Promise<BiResult<Record<string, unknown>>> {
  return request({ url: '/bi/catalog/lineage/rebuild', method: 'post' })
}

export function listSchemaChangeEvents(assetId: number): Promise<BiResult<SchemaChangeEvent[]>> {
  return request({ url: `/bi/catalog/assets/${assetId}/changes` })
}

export function getSchemaChangeImpact(eventId: number): Promise<BiResult<SchemaChangeImpact>> {
  return request({ url: `/bi/catalog/schema-changes/${eventId}/impact` })
}

export interface OpsImpactPack {
  asset: { id: number; name: string; assetType: string; qualifiedName?: string; sourceId?: number }
  summary: {
    severity: string
    datasetCount: number
    metricCount: number
    chartCount: number
    dashboardCount: number
    dataApiCount: number
    pipelineCount: number
    warehouseLoadCount: number
    cdcJobCount: number
    downstreamAssetCount: number
    redactedConsumerCount?: number
    truncated?: boolean
    lineageIncompleteHint?: boolean
  }
  checklist: string[]
  metrics: Array<Record<string, unknown>>
  charts: Array<Record<string, unknown>>
  dashboards: Array<Record<string, unknown>>
  dataApis: Array<Record<string, unknown>>
  pipelines: Array<Record<string, unknown>>
  warehouseLoads: Array<Record<string, unknown>>
  cdcJobs: Array<Record<string, unknown>>
  note?: string
}

export function getOpsImpactPack(assetId: number): Promise<BiResult<OpsImpactPack>> {
  return request({ url: `/bi/catalog/assets/${assetId}/impact-pack` })
}

export interface SchemaMigrationPlan {
  id: number
  eventId: number
  planNo: number
  status: 'DRAFT' | 'BLOCKED' | 'APPROVED' | 'EXECUTED' | 'FAILED' | 'ROLLED_BACK' | string
  executionError?: string
  blockers?: string[]
  steps?: Array<Record<string, unknown>>
}

export function listSchemaMigrationPlans(eventId: number): Promise<BiResult<SchemaMigrationPlan[]>> {
  return request({ url: `/bi/catalog/schema-changes/${eventId}/migration-plans` })
}

export function createSchemaMigrationPlan(eventId: number, mappings: Array<{ datasetId: number; sourceField: string; targetField: string | null }>): Promise<BiResult<SchemaMigrationPlan>> {
  return request({ url: `/bi/catalog/schema-changes/${eventId}/migration-plans`, method: 'post', data: { mappings } })
}

export function approveSchemaMigrationPlan(id: number): Promise<BiResult<SchemaMigrationPlan>> {
  return request({ url: `/bi/catalog/schema-migration-plans/${id}/approve`, method: 'post' })
}

export function executeSchemaMigrationPlan(id: number): Promise<BiResult<SchemaMigrationPlan>> {
  return request({ url: `/bi/catalog/schema-migration-plans/${id}/execute`, method: 'post' })
}

export function rollbackSchemaMigrationPlan(id: number): Promise<BiResult<SchemaMigrationPlan>> {
  return request({ url: `/bi/catalog/schema-migration-plans/${id}/rollback`, method: 'post' })
}

export interface CatalogAuditFilters {
  page: number
  pageSize: number
  actor?: string
  action?: string
  outcome?: string
  from?: string
  to?: string
}

export function listCatalogAudit(params: CatalogAuditFilters): Promise<BiResult<{ items: CatalogAuditRow[]; total: number; page: number; pageSize: number }>> {
  return request({ url: '/bi/catalog/audit', params })
}

export interface CatalogAuditRetentionPolicy {
  liveRetentionDays: number
  archiveRetention: 'indefinite' | string
  updatedBy?: string
  updatedAt?: string
}

export function getCatalogAuditRetention(): Promise<BiResult<CatalogAuditRetentionPolicy>> {
  return request({ url: '/bi/catalog/audit/retention' })
}

export function updateCatalogAuditRetention(liveRetentionDays: number): Promise<BiResult<CatalogAuditRetentionPolicy>> {
  return request({ url: '/bi/catalog/audit/retention', method: 'put', params: { liveRetentionDays } })
}

export function previewCatalogBackfill(): Promise<BiResult<CatalogBackfillPreview>> {
  return request({ url: '/bi/catalog/assets/backfill/preview' })
}

export function runCatalogBackfill(): Promise<BiResult<{ mode: 'EXECUTED'; assetsProcessed: number; versionsCreated: number; copiedDefinitions: false }>> {
  return request({ url: '/bi/catalog/assets/backfill', method: 'post' })
}

export interface MetadataScanDefinition {
  id: number
  datasourceId: number
  name: string
  scopeJson: string
  status: '0' | '1' | string
  createTime?: string
  updateTime?: string
}

export interface MetadataScanRun {
  id: number
  scanId?: number
  datasourceId: number
  triggerType: string
  triggerBy?: string
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL_SUCCESS' | 'FAILED' | string
  progressTotal: number
  progressCompleted: number
  successCount: number
  failureCount: number
  errorSummary?: string
  resultSummaryJson?: string
  traceId?: string
  startedAt?: string
  finishedAt?: string
  createTime?: string
}

export interface MetadataScanSaveRequest {
  name: string
  scope: { includeTables: string[]; excludeTables: string[] }
  status: '0' | '1'
}

export function searchCatalogAssets(params: { sourceKind?: 'CONNECTOR' | 'BI_RESOURCE'; datasourceId?: number; type?: CatalogAssetType; parentId?: number; query?: string; page: number; pageSize: number }): Promise<BiResult<CatalogAssetPage>> {
  return request({ url: '/bi/catalog/assets', params })
}

export function getCatalogAsset(id: number): Promise<BiResult<CatalogAsset>> {
  return request({ url: `/bi/catalog/assets/${id}` })
}

export interface CatalogConsumeAction {
  key: string
  label: string
  path: string
  query?: Record<string, string | number>
  enabled?: boolean
}

export interface CatalogConsumeLinks extends CatalogAsset {
  consumeActions: CatalogConsumeAction[]
  permissionType?: string
}

export function getCatalogConsumeLinks(id: number): Promise<BiResult<CatalogConsumeLinks>> {
  return request({ url: `/bi/catalog/assets/${id}/consume-links` })
}

export function listCatalogAssetVersions(id: number): Promise<BiResult<CatalogAssetVersion[]>> {
  return request({ url: `/bi/catalog/assets/${id}/versions` })
}

export function listMetadataScans(datasourceId: number): Promise<BiResult<MetadataScanDefinition[]>> {
  return request({ url: '/bi/metadata-scans', params: { datasourceId } })
}

export function createMetadataScan(datasourceId: number, data: MetadataScanSaveRequest): Promise<BiResult<MetadataScanDefinition>> {
  return request({ url: '/bi/metadata-scans', method: 'post', data: { datasourceId, ...data } })
}

export function updateMetadataScan(id: number, data: MetadataScanSaveRequest): Promise<BiResult<MetadataScanDefinition>> {
  return request({ url: `/bi/metadata-scans/${id}`, method: 'put', data })
}

export function deleteMetadataScan(id: number): Promise<BiResult<{ deleted: boolean }>> {
  return request({ url: `/bi/metadata-scans/${id}`, method: 'delete' })
}

export function triggerMetadataScan(id: number): Promise<BiResult<MetadataScanRun>> {
  return request({ url: `/bi/metadata-scans/${id}/runs`, method: 'post' })
}

export function listMetadataScanRuns(datasourceId: number): Promise<BiResult<MetadataScanRun[]>> {
  return request({ url: '/bi/metadata-scans/runs', params: { datasourceId } })
}

export function getMetadataScanRun(id: number): Promise<BiResult<MetadataScanRun>> {
  return request({ url: `/bi/metadata-scans/runs/${id}` })
}
