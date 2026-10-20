/** Thumbnails render saved definitions through the production components. No shared
 * image/data cache: each viewer still passes the normal resource and row/column ACLs. */
export const BI_SAVED_EVENT = 'bi:saved-resource'
export function notifyBiSaved(kind: 'chart' | 'dashboard', id: number) {
  window.dispatchEvent(new CustomEvent(BI_SAVED_EVENT, { detail: { kind, id } }))
}

// Bound background preview work across all cards; obsolete queued cards never query.
let running = 0
const waiting: (() => void)[] = []
export async function thumbnailTask<T>(task: () => Promise<T>, current: () => boolean): Promise<T | undefined> {
  if (running >= 2) await new Promise<void>(resolve => waiting.push(resolve))
  else running++
  try { return current() ? await task() : undefined }
  finally { const next = waiting.shift(); if (next) next(); else running-- }
}

/** Chart definition and data are separate legacy endpoints. Verify the version on
 * both sides of a query so an in-flight save cannot mix two different charts. */
export async function versionedThumbnail<D,T>(definition:()=>Promise<{version:number;definition:D}>,data:()=>Promise<T>,current:()=>boolean,initial?:{version:number;definition:D}) {
  for(let attempt=0;attempt<2 && current();attempt++) {
    const before=attempt===0 && initial ? initial : await definition();if(!current())return
    const result=await data();if(!current())return
    const after=await definition();if(!current())return
    if(before.version===after.version)return {...before,data:result}
  }
  if(current())throw new Error('保存版本正在变化，请刷新预览')
}
