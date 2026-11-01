import request from '@/utils/request'
import type { BiResult } from './response'

export type TaskRunType = 'QUALITY' | 'METADATA_SCAN' | 'PIPELINE' | 'WAREHOUSE_LOAD' | 'OBJECT_LOAD' | 'CDC_JOB' | 'EXPORT' | 'SUBSCRIPTION' | 'SUBSCRIPTION_SCHEDULE' | string
export interface TaskRunItem {
  type: TaskRunType; id: number; name: string; resourceName?: string; status: string
  createdAt?: string; startedAt?: string; finishedAt?: string; message?: string; detailUrl: string
  cancelable: boolean; retryable: boolean; priority?: number; poolLane?: string
}
export interface TaskCenterResult {
  items: TaskRunItem[]
  total: number
  page: number
  pageSize: number
  summary: {
    total: number
    running: number
    failed: number
    succeeded: number
    waiting?: number
    byType?: Record<string, number>
    byLane?: Record<string, number>
    sort?: string
    note?: string
  }
}
export const listTaskRuns = (params: {
  type?: string; status?: string; query?: string; page?: number; pageSize?: number; sort?: 'created' | 'priority' | string
}): Promise<BiResult<TaskCenterResult>> => request({ url: '/bi/tasks', params }) as Promise<BiResult<TaskCenterResult>>
export const cancelTaskRun = (type: string, id: number): Promise<BiResult<Record<string, unknown>>> => request({ url: `/bi/tasks/${encodeURIComponent(type)}/${id}/cancel`, method: 'post' }) as Promise<BiResult<Record<string, unknown>>>
export const retryTaskRun = (type: string, id: number): Promise<BiResult<Record<string, unknown>>> => request({ url: `/bi/tasks/${encodeURIComponent(type)}/${id}/retry`, method: 'post' }) as Promise<BiResult<Record<string, unknown>>>
export interface TaskPoolLane {
  lane: string
  paused: number | boolean
  maxConcurrent: number
  updateBy?: string
  updateTime?: string
}
export const listTaskPoolLanes = (): Promise<BiResult<TaskPoolLane[]>> =>
  request({ url: '/bi/tasks/lanes' }) as Promise<BiResult<TaskPoolLane[]>>
export const updateTaskPoolLane = (lane: string, data: { paused?: boolean; maxConcurrent?: number }): Promise<BiResult<TaskPoolLane>> =>
  request({ url: `/bi/tasks/lanes/${encodeURIComponent(lane)}`, method: 'put', data }) as Promise<BiResult<TaskPoolLane>>
