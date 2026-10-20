import request from '@/utils/request'
import type { BiResult as AjaxResult } from './response'

export interface DataFormField {
  field: string
  label: string
  required?: boolean
  widget?: 'text' | 'number' | 'date' | 'textarea'
}

export interface DataFormSummary {
  id: number
  name: string
  datasetId: number
  status: string
  createBy?: string
  createTime?: string
}

export interface DataFormDetail extends DataFormSummary {
  fields: DataFormField[]
  remark?: string
}

export interface FormRecord {
  id: number
  status?: string
  canReview?: boolean
  reviewNote?: string
  reviewTime?: string
  success: boolean
  errorMsg?: string
  createBy?: string
  createTime?: string
  row: string
}

export const listDataForms = (): Promise<AjaxResult<DataFormSummary[]>> => request({ url: '/bi/form' })
export const getDataForm = (id: number): Promise<AjaxResult<DataFormDetail>> => request({ url: `/bi/form/${id}` })
export const createDataForm = (data: { name: string; datasetId: number; fields: DataFormField[]; remark?: string }): Promise<AjaxResult<{ id: number }>> =>
  request({ url: '/bi/form', method: 'post', data })
export const updateDataForm = (id: number, data: Partial<{ name: string; fields: DataFormField[]; remark: string; status: string }>): Promise<AjaxResult> =>
  request({ url: `/bi/form/${id}`, method: 'put', data })
export const deleteDataForm = (id: number): Promise<AjaxResult> => request({ url: `/bi/form/${id}`, method: 'delete' })
export const submitDataForm = (id: number, values: Record<string, unknown>, requestId: string): Promise<AjaxResult<{ submitted: boolean; recordId: number; status: string; message: string }>> =>
  request({ url: `/bi/form/${id}/submit`, method: 'post', data: values, headers: {'X-BI-Submission-Id':requestId} })
export const listFormRecords = (id: number): Promise<AjaxResult<FormRecord[]>> => request({ url: `/bi/form/${id}/records` })
export interface FormReview {id:number; previousStatus:string; resolvedStatus:string; reviewerPrincipal:number; evidence:string; createTime:string}
export const reviewFormRecord=(formId:number,id:number,data:{expectedStatus:string;decision:string;evidence:string}):Promise<AjaxResult>=>request({url:`/bi/form/${formId}/records/${id}/review`,method:'post',data})
export const listFormReviews=(formId:number,id:number):Promise<AjaxResult<FormReview[]>>=>request({url:`/bi/form/${formId}/records/${id}/reviews`})
