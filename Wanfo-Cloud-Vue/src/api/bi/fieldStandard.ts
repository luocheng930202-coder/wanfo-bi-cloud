import request from '@/utils/request'
import type { BiResult } from './response'

export interface FieldStandard {
  id: number
  standardCode: string
  name: string
  dataTypeFamily: 'STRING' | 'NUMBER' | 'DATETIME' | 'BOOLEAN' | string
  maxLength?: number
  nullablePolicy: 'REQUIRED' | 'OPTIONAL' | string
  valuePattern?: string
  enumValues?: string[]
  matchAliases?: string[]
  glossaryTermId?: number
  metricId?: number
  description?: string
  status: 'ACTIVE' | 'DRAFT' | 'DEPRECATED' | string
}

export interface FieldStandardSave {
  standardCode: string
  name: string
  dataTypeFamily: string
  maxLength?: number | null
  nullablePolicy?: string
  valuePattern?: string | null
  enumValues?: string[]
  matchAliases?: string[]
  glossaryTermId?: number | null
  metricId?: number | null
  description?: string | null
  status?: string
}

export interface ComplianceResult {
  summary: {
    scannedColumns: number
    activeStandards: number
    compliant: number
    typeMismatch: number
    unbound: number
    aliasMatched: number
    note?: string
  }
  findings: Array<{
    assetId?: number
    columnName?: string
    tableName?: string
    qualifiedName?: string
    observedType?: string
    observedFamily?: string
    result: string
    message?: string
    standardCode?: string
    standardName?: string
    expectedFamily?: string
    matchMode?: string
  }>
}

export function listFieldStandards(): Promise<BiResult<FieldStandard[]>> {
  return request({ url: '/bi/field-standards' }) as Promise<BiResult<FieldStandard[]>>
}

export function createFieldStandard(data: FieldStandardSave): Promise<BiResult<FieldStandard>> {
  return request({ url: '/bi/field-standards', method: 'post', data }) as Promise<BiResult<FieldStandard>>
}

export function updateFieldStandard(id: number, data: FieldStandardSave): Promise<BiResult<FieldStandard>> {
  return request({ url: `/bi/field-standards/${id}`, method: 'put', data }) as Promise<BiResult<FieldStandard>>
}

export function bindFieldStandard(data: { standardId: number; assetId: number; note?: string }): Promise<BiResult<Record<string, unknown>>> {
  return request({ url: '/bi/field-standards/bindings', method: 'post', data }) as Promise<BiResult<Record<string, unknown>>>
}

export function unbindFieldStandard(assetId: number): Promise<BiResult<Record<string, unknown>>> {
  return request({ url: `/bi/field-standards/bindings/${assetId}`, method: 'delete' }) as Promise<BiResult<Record<string, unknown>>>
}

export function checkFieldStandardCompliance(limit?: number): Promise<BiResult<ComplianceResult>> {
  return request({ url: '/bi/field-standards/compliance', params: { limit } }) as Promise<BiResult<ComplianceResult>>
}

export function onlineSampleValidate(assetId: number, limit?: number): Promise<BiResult<Record<string, unknown>>> {
  return request({
    url: '/bi/field-standards/online-sample-validate',
    method: 'post',
    params: { assetId, limit }
  }) as Promise<BiResult<Record<string, unknown>>>
}
