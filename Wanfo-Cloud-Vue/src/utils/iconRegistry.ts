import * as ElementPlusIconsVue from '@element-plus/icons-vue'

export const EP_ICON_PREFIX = 'ep:'

const svgModules = import.meta.glob('../assets/icons/svg/*.svg')
export const svgIconNames: string[] = []
for (const path of Object.keys(svgModules)) {
  const name = path.split('assets/icons/svg/')[1]?.split('.svg')[0]
  if (name) svgIconNames.push(name)
}
svgIconNames.sort((a, b) => a.localeCompare(b))

export const elementPlusIconNames = Object.keys(ElementPlusIconsVue).sort((a, b) => a.localeCompare(b))

const svgSet = new Set(svgIconNames)
const epSet = new Set(elementPlusIconNames)

export type IconKind = 'svg' | 'ep' | 'none'

export function encodeElementPlusIcon(name: string): string {
  return `${EP_ICON_PREFIX}${name}`
}

export function parseIconKey(icon?: string | null): { kind: IconKind; name: string } {
  if (!icon || icon === '#') return { kind: 'none', name: '' }
  if (icon.startsWith(EP_ICON_PREFIX)) {
    const name = icon.slice(EP_ICON_PREFIX.length)
    return epSet.has(name) ? { kind: 'ep', name } : { kind: 'none', name: '' }
  }
  if (svgSet.has(icon)) return { kind: 'svg', name: icon }
  if (epSet.has(icon)) return { kind: 'ep', name: icon }
  return { kind: 'svg', name: icon }
}

export function filterSvgIcons(query: string): string[] {
  const q = query.trim().toLowerCase()
  if (!q) return svgIconNames
  return svgIconNames.filter(n => n.toLowerCase().includes(q))
}

export function filterElementPlusIcons(query: string): string[] {
  const q = query.trim().toLowerCase()
  if (!q) return elementPlusIconNames
  return elementPlusIconNames.filter(n => n.toLowerCase().includes(q))
}
