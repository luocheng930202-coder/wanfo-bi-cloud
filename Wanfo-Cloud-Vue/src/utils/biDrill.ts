import type { DrillPathSegment, DashboardInput } from '@/api/bi/dashboard'
import type { PanelResult } from '@/api/bi/dashboard'

export function drillPathFromPayload(payload?: PanelResult): DrillPathSegment[] {
  const path = payload?.presentation?.drillPath
  return Array.isArray(path) ? path.map(s => ({ field: s.field, value: s.value as string | number | null })) : []
}

export function canDrillDown(payload?: PanelResult): boolean {
  return payload?.presentation?.canDrillDown === true
}

export function appendDrillSegment(path: DrillPathSegment[], field: string, value: string | number | null): DrillPathSegment[] {
  return [...path, { field, value }]
}

export function truncateDrillPath(path: DrillPathSegment[], index: number): DrillPathSegment[] {
  if (index <= 0) return []
  return path.slice(0, index)
}

export function mergeDashboardInput(
  filters: DashboardInput['filters'],
  links: DashboardInput['links'],
  drills: DashboardInput['drills']
): DashboardInput {
  const body: DashboardInput = { filters, links }
  if (drills && Object.keys(drills).length) body.drills = drills
  return body
}
