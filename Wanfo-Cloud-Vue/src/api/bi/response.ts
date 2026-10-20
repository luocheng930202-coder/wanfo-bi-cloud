import type {AjaxResult} from '@/types'
/** BI controllers return data on success; the request interceptor rejects non-200 responses. */
export type BiResult<T=unknown> = Omit<AjaxResult<T>,'data'> & {data:T}

/** Normalize BI payload after axios interceptor (expects RuoYi { code, data }). */
export function unwrapBiData<T>(res: BiResult<T> | T): T {
  if (res !== null && typeof res === 'object' && 'data' in res && (res as BiResult<T>).data !== undefined)
    return (res as BiResult<T>).data
  return res as T
}
