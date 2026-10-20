import request from '@/utils/request'
import { blobValidate } from '@/utils/ruoyi'
import { saveAs } from 'file-saver'
import { ElMessage } from 'element-plus'
import type { BiResult as AjaxResult } from './response'
import type { DashboardInput } from './dashboard'

export type ExportFormat = 'csv' | 'xlsx' | 'pdf'
export type ExportSource = 'chart' | 'dashboard'
export type ExportStatus = 'queued' | 'running' | 'succeeded' | 'failed' | 'cancelled' | 'expired'
export interface ExportJob {
  id: number
  name: string
  sourceType: ExportSource
  sourceId: number
  panelKey?: string
  format: ExportFormat
  status: ExportStatus
  message?: string
  rowCount?: number
  fileName?: string
  expireTime?: string
  createTime?: string
}
export interface ExportSubmit {
  sourceType: ExportSource
  sourceId: number
  panelKey?: string
  revision?: number
  format: ExportFormat
  name?: string
  input?: DashboardInput
}
export const listExports = (): Promise<AjaxResult<ExportJob[]>> => request({ url: '/bi/export' })
export const getExport = (id: number): Promise<AjaxResult<ExportJob>> => request({ url: `/bi/export/${id}` })
export const submitExport = (body: ExportSubmit): Promise<AjaxResult<{ id: number; status: ExportStatus; name: string }>> => request({ url: '/bi/export', method: 'post', data: body })
export const cancelExport = (id: number): Promise<AjaxResult<{ cancelled: boolean }>> => request({ url: `/bi/export/${id}/cancel`, method: 'post' })
export async function downloadExport(id: number, fileName: string) {
  const data = await request({ url: `/bi/export/${id}/file`, method: 'get', responseType: 'blob', timeout: 60000 })
  if (!blobValidate(data)) {
    const text = await data.text()
    const obj = JSON.parse(text)
    ElMessage.error(obj.msg || '下载失败')
    return
  }
  saveAs(new Blob([data]), fileName)
}
