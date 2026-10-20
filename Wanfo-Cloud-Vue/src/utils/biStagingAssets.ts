import {releaseStagingAssets} from '@/api/bi/asset'

const pending = new Set<number>()

export function trackStagingAsset(id: number) {
  if (Number.isSafeInteger(id) && id > 0) pending.add(id)
}

export function untrackStagingAsset(id: number) {
  pending.delete(id)
}

export async function flushStagingAssets() {
  if (!pending.size) return
  const ids = [...pending]
  pending.clear()
  try {
    await releaseStagingAssets(ids)
  } catch {
    ids.forEach(id => pending.add(id))
  }
}
