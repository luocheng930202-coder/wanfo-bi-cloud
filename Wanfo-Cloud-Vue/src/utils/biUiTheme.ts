export type BiUiTheme = 'restrained' | 'saas' | 'ops'

export const BI_UI_THEME_KEY = 'bi-ui-theme'
export const BI_UI_THEMES = ['restrained', 'saas', 'ops'] as const

const listeners = new Set<(theme: BiUiTheme) => void>()

export function normalizeBiUiTheme(raw: unknown): BiUiTheme {
  return BI_UI_THEMES.includes(raw as BiUiTheme) ? (raw as BiUiTheme) : 'restrained'
}

export function getBiUiTheme(): BiUiTheme {
  try {
    return normalizeBiUiTheme(localStorage.getItem(BI_UI_THEME_KEY))
  } catch {
    return 'restrained'
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
