export type BiUiTheme = 'silver' | 'violet' | 'jade' | 'ocean'

export const BI_UI_THEME_KEY = 'bi-ui-theme'
export const BI_UI_THEMES = ['silver', 'violet', 'jade', 'ocean'] as const

const listeners = new Set<(theme: BiUiTheme) => void>()

export function normalizeBiUiTheme(raw: unknown): BiUiTheme {
  const legacy:Record<string,BiUiTheme>={restrained:'silver',saas:'silver',ops:'ocean'}
  return typeof raw==='string' && Object.hasOwn(legacy,raw) ? legacy[raw] : BI_UI_THEMES.includes(raw as BiUiTheme) ? raw as BiUiTheme : 'silver'
}

export function getBiUiTheme(): BiUiTheme {
  try {
    return normalizeBiUiTheme(localStorage.getItem(BI_UI_THEME_KEY))
  } catch {
    return 'silver'
  }
}

export function setBiUiTheme(theme: BiUiTheme): void {
  const next = normalizeBiUiTheme(theme)
  try {
    localStorage.setItem(BI_UI_THEME_KEY, next)
  } catch {
    /* ignore quota / private mode */
  }
  listeners.forEach((fn) => fn(next))
}

export function subscribeBiUiTheme(listener: (theme: BiUiTheme) => void): () => void {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export const BI_UI_THEME_OPTIONS = [{id:'silver',label:'极简银白'},{id:'violet',label:'深空紫电'},{id:'jade',label:'青绿秩序'},{id:'ocean',label:'深海冰蓝'}] as const
