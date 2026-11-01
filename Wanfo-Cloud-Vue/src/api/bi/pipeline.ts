import request from '@/utils/request'
import type { BiResult } from './response'
import type { SubscriptionSchedule } from './subscription'

export interface PipelineNode {
  key: string
  type: string
  name: string
  config: Record<string, unknown>
  inputSchema: Record<string, unknown>
  outputSchema: Record<string, unknown>
  x: number
  y: number
}
export interface PipelineEdge { source: string; sourcePort: string; target: string; targetPort: string; type: string }
export interface PipelineRevision {
  id: number; revision: number; status: 'DRAFT' | 'PUBLISHED' | string; definitionHash?: string; lockVersion: number; rollbackFromRevisionId?: number
  inputSchema: Record<string, unknown> | string; outputSchema: Record<string, unknown> | string
  parameters: Record<string, unknown> | string; remark?: string; nodes: Array<PipelineNode & { nodeType?: string }>; edges: PipelineEdge[]
}
export interface PipelineDefinition { id: number; pipelineCode: string; name: string; description?: string; status: string; currentRevisionId?: number; revisions: PipelineRevision[] }
export interface PipelineDiff { from: Record<string, unknown>; to: Record<string, unknown>; same: boolean; addedNodes: Array<Record<string, unknown>>; removedNodes: Array<Record<string, unknown>>; changedNodes: Array<Record<string, unknown>>; addedEdges: string[]; removedEdges: string[]; changedMetadata: string[] }
export interface PipelinePublishHistory { id: number; pipelineId: number; previousRevisionId?: number; revisionId: number; actionType: 'PUBLISH' | 'ROLLBACK' | string; actionBy: string; reason?: string; createTime?: string }
export interface PipelineSchedule {
  id?: number
  pipelineId?: number
  name?: string
  enabled?: boolean
  revisionId?: number
  targetDatasourceId?: number
  maxRows?: number
  timeoutSeconds?: number
  intervalMinutes?: number
  schedule?: SubscriptionSchedule
  nextRunUtc?: string
  lastStatus?: string
  lastMessage?: string
  failureCount?: number
}
export interface PipelineScheduleSave {
  name: string
  enabled: boolean
  revisionId?: number
  targetDatasourceId: number
  maxRows?: number
  timeoutSeconds?: number
  intervalMinutes?: number
  schedule?: SubscriptionSchedule
}
export interface PipelineProductionRunOptions {
  requestKey: string
  timeoutSeconds?: number
  targetDatasourceId?: number
  maxRows?: number
}
export interface PipelineRun {
  id: number; pipelineId: number; revisionId: number; revisionHash: string; runMode: 'PREVIEW' | 'PRODUCTION' | string
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'TIMED_OUT' | string
  triggerBy: string; timeoutSeconds: number; errorSummary?: string; createTime?: string; startedAt?: string; finishedAt?: string; durationMs?: number
  nodes?: Array<{ nodeKey: string; nodeType: string; status: string; inputRowCount: number; outputRowCount: number; summary?: Record<string, unknown>; errorSummary?: string; durationMs?: number }>
}
export interface PipelineSaveDefinition {
  nodes: PipelineNode[]; edges: PipelineEdge[]; inputSchema: Record<string, unknown>
  outputSchema: Record<string, unknown>; parameters: Record<string, unknown>; remark?: string; expectedVersion: number
}

export const listPipelines = (): Promise<BiResult<PipelineDefinition[]>> => request({ url: '/bi/pipeline' }) as Promise<BiResult<PipelineDefinition[]>>
export const getPipeline = (id: number): Promise<BiResult<PipelineDefinition>> => request({ url: `/bi/pipeline/${id}` }) as Promise<BiResult<PipelineDefinition>>
export const createPipeline = (data: { code: string; name: string; description?: string }): Promise<BiResult<PipelineDefinition>> => request({ url: '/bi/pipeline', method: 'post', data }) as Promise<BiResult<PipelineDefinition>>
export const savePipelineRevision = (pipelineId: number, revisionId: number, data: PipelineSaveDefinition): Promise<BiResult<PipelineRevision>> => request({ url: `/bi/pipeline/${pipelineId}/revisions/${revisionId}`, method: 'put', data }) as Promise<BiResult<PipelineRevision>>
export const copyPipelineRevision = (pipelineId: number, revisionId: number): Promise<BiResult<PipelineDefinition>> => request({ url: `/bi/pipeline/${pipelineId}/revisions/${revisionId}/copy`, method: 'post' }) as Promise<BiResult<PipelineDefinition>>
export const publishPipelineRevision = (pipelineId: number, revisionId: number): Promise<BiResult<PipelineDefinition>> => request({ url: `/bi/pipeline/${pipelineId}/revisions/${revisionId}/publish`, method: 'post' }) as Promise<BiResult<PipelineDefinition>>
export const diffPipelineRevisions = (pipelineId: number, fromRevisionId: number, toRevisionId: number): Promise<BiResult<PipelineDiff>> => request({ url: `/bi/pipeline/${pipelineId}/revisions/${fromRevisionId}/diff/${toRevisionId}` }) as Promise<BiResult<PipelineDiff>>
export const rollbackPipelineRevision = (pipelineId: number, revisionId: number): Promise<BiResult<PipelineDefinition>> => request({ url: `/bi/pipeline/${pipelineId}/revisions/${revisionId}/rollback`, method: 'post' }) as Promise<BiResult<PipelineDefinition>>
export const listPipelinePublishHistory = (pipelineId: number, limit = 30): Promise<BiResult<PipelinePublishHistory[]>> => request({ url: `/bi/pipeline/${pipelineId}/publish-history`, params: { limit } }) as Promise<BiResult<PipelinePublishHistory[]>>
export const previewPipelineRevision = (pipelineId: number, revisionId: number, requestKey: string, timeoutSeconds = 30): Promise<BiResult<PipelineRun>> => request({ url: `/bi/pipeline/${pipelineId}/revisions/${revisionId}/runs/preview`, method: 'post', data: { requestKey, timeoutSeconds } }) as Promise<BiResult<PipelineRun>>
export const productionPipelineRevision = (pipelineId: number, revisionId: number, options: PipelineProductionRunOptions): Promise<BiResult<PipelineRun>> =>
  request({ url: `/bi/pipeline/${pipelineId}/revisions/${revisionId}/runs/production`, method: 'post', data: options, timeout: Math.max(60_000, (options.timeoutSeconds ?? 300) * 1000 + 15_000), headers: { repeatSubmit: false } }) as Promise<BiResult<PipelineRun>>
export const getPipelineSchedule = (pipelineId: number): Promise<BiResult<PipelineSchedule>> => request({ url: `/bi/pipeline/${pipelineId}/schedule` }) as Promise<BiResult<PipelineSchedule>>
export const savePipelineSchedule = (pipelineId: number, data: PipelineScheduleSave): Promise<BiResult<PipelineSchedule>> =>
  request({ url: `/bi/pipeline/${pipelineId}/schedule`, method: 'put', data }) as Promise<BiResult<PipelineSchedule>>
export const disablePipelineSchedule = (pipelineId: number): Promise<BiResult<PipelineSchedule>> =>
  request({ url: `/bi/pipeline/${pipelineId}/schedule/disable`, method: 'post' }) as Promise<BiResult<PipelineSchedule>>
export const listPipelineRuns = (pipelineId: number, limit = 30): Promise<BiResult<PipelineRun[]>> => request({ url: `/bi/pipeline/${pipelineId}/runs`, params: { limit } }) as Promise<BiResult<PipelineRun[]>>
export const getPipelineRun = (runId: number): Promise<BiResult<PipelineRun>> => request({ url: `/bi/pipeline/runs/${runId}` }) as Promise<BiResult<PipelineRun>>
export const cancelPipelineRun = (runId: number): Promise<BiResult<PipelineRun>> => request({ url: `/bi/pipeline/runs/${runId}/cancel`, method: 'post' }) as Promise<BiResult<PipelineRun>>
