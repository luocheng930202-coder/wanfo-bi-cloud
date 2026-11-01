import request from '@/utils/request'
import type { BiResult } from './response'

export interface GlossaryTerm {
  id: number
  termCode: string
  name: string
  category?: string
  definitionText: string
  ownerName?: string
  certificationStatus: 'DRAFT' | 'REVIEW' | 'CERTIFIED' | 'DEPRECATED' | string
  metricId?: number
  createTime?: string
  updateTime?: string
}

export interface GlossaryTermSave {
  termCode: string
  name: string
  category?: string
  definitionText: string
  ownerName?: string
  certificationStatus?: string
  metricId?: number | null
}

export function listGlossaryTerms(): Promise<BiResult<GlossaryTerm[]>> {
  return request({ url: '/bi/glossary/terms' }) as Promise<BiResult<GlossaryTerm[]>>
}

export function getGlossaryTerm(id: number): Promise<BiResult<GlossaryTerm>> {
  return request({ url: `/bi/glossary/terms/${id}` }) as Promise<BiResult<GlossaryTerm>>
}

export function createGlossaryTerm(data: GlossaryTermSave): Promise<BiResult<GlossaryTerm>> {
  return request({ url: '/bi/glossary/terms', method: 'post', data }) as Promise<BiResult<GlossaryTerm>>
}

export function updateGlossaryTerm(id: number, data: GlossaryTermSave): Promise<BiResult<GlossaryTerm>> {
  return request({ url: `/bi/glossary/terms/${id}`, method: 'put', data }) as Promise<BiResult<GlossaryTerm>>
}
