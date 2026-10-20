import type { DatasetField } from '@/api/bi/dataset'

export function fieldShowName(fields: DatasetField[], originName: string): string {
  const row = fields.find(f => f.originName === originName)
  const show = row?.showName?.trim()
  return show || originName
}

export function previewColumnLabel(fields: DatasetField[], column: string): string {
  const direct = fields.find(f => f.originName === column)
  if (direct) return fieldShowName(fields, column)
  return column
}
