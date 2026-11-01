import request from '@/utils/request'
import type { BiResult } from './response'

export interface BackupDrill {
  id: number
  drillType: string
  result: string
  rpoMinutes?: number
  rtoMinutes?: number
  artifactPath?: string
  notes?: string
  actor?: string
  startedAt?: string
  finishedAt?: string
  scope?: Record<string, unknown>
  checklist?: string[]
}

export interface BackupReadiness {
  summary: {
    ready: boolean
    blockers: string[]
    warnings: string[]
    flywayVersion?: string
    declaredRpoMinutes: number
    declaredRtoMinutes: number
    localArtifactCount: number
    newestLocalArtifact?: { name: string; path: string; sizeBytes: number; modifiedAt: string }
    newestArtifactAgeMinutes?: number
    lastPassedDrill?: BackupDrill
    lastDrill?: BackupDrill
  }
  checklist: string[]
  note?: string
}

export function getBackupReadiness(): Promise<BiResult<BackupReadiness>> {
  return request({ url: '/bi/ops/backup-drills/readiness' }) as Promise<BiResult<BackupReadiness>>
}

export function listBackupDrills(): Promise<BiResult<BackupDrill[]>> {
  return request({ url: '/bi/ops/backup-drills' }) as Promise<BiResult<BackupDrill[]>>
}

export function recordBackupDrill(data: Record<string, unknown>): Promise<BiResult<BackupDrill>> {
  return request({ url: '/bi/ops/backup-drills', method: 'post', data }) as Promise<BiResult<BackupDrill>>
}
