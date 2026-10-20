import type { ChartAdvancedOptions, ChartDefinition, ChartVisualStyle } from '@/api/bi/chart'
import type { DashboardAppearance } from '@/api/bi/dashboard'
import { chartPalette } from '@/utils/biPresentation'

export function defaultChartStyle(): ChartVisualStyle {
  return { showLegend: true, legendPosition: 'top', showTitle: false, palette: 'business', axisLabelRotate: undefined }
}

export function defaultChartAdvanced(): ChartAdvancedOptions {
  return { nullMode: 'keep', smoothLine: true, pivotRowFields: [], pivotColFields: [], mapScope: 'china-provinces', drillFields: [] }
}

export function ensureChartPresentation(definition: ChartDefinition): void {
  if (!definition.style) definition.style = defaultChartStyle()
  if (!definition.advanced) definition.advanced = defaultChartAdvanced()
}

export function chartAppearance(style?: ChartVisualStyle, fallback?: DashboardAppearance): DashboardAppearance | undefined {
  if (!style) return fallback
  if (!style.palette && !fallback) return fallback
  const base = fallback || {
    theme: 'light',
    background: '#f4f6fa',
    cardBackground: '#ffffff',
    textColor: '#303133',
    borderColor: '#e4e7ed',
    palette: 'business',
    titleSize: 14,
    radius: 8,
    decimals: 2,
    grouping: true
  }
  return { ...base, palette: style.palette || base.palette }
}

export function paletteFromStyle(style?: ChartVisualStyle, appearance?: DashboardAppearance) {
  const merged = chartAppearance(style, appearance)
  return chartPalette(merged || appearance)
}
