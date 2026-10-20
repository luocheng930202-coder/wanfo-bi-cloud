import type { EChartsOption } from 'echarts'
import type { ChartDefinition, ChartResult } from '@/api/bi/chart'
import type { DashboardAppearance, NumberFormat, MetricStyle } from '@/api/bi/dashboard'
import { chartAppearance, paletteFromStyle } from '@/utils/biChartPresentation'
import { displayNumber, formatMetric } from '@/utils/biPresentation'
import { defaultMapScope, normalizeProvinceName, type MapScopeId } from '@/utils/biMapGeo'
import { metricResultKey } from '@/utils/biEchartsPreview'
import type { ChartType } from '@/utils/biChartTypes'

export function validateMapConfig(definition: ChartDefinition): string | null {
  if (definition.type !== 'map') return null
  const dims = definition.query.dimensions
  const metrics = definition.query.metrics
  if (dims.length !== 1 || metrics.length !== 1) return '行政区地图须选择一个地区维度和一个度量'
  const scope = definition.advanced?.mapScope || defaultMapScope()
  if (!(scope in { 'china-provinces': 1 })) return '不支持的地图范围'
  return null
}

function readNumber(raw: unknown): number | null {
  if (raw == null) return null
  const value = Number(raw)
  if (!Number.isFinite(value) || Math.abs(value) > Number.MAX_SAFE_INTEGER) return null
  return value
}

export function buildMapPreviewOption(params: {
  mapName: string
  definition: ChartDefinition
  result: ChartResult
  labels: Record<string, string>
  appearance?: DashboardAppearance
  numberFormat?: NumberFormat | null
  metricStyles?: MetricStyle[]
  formatFor: (key: string) => NumberFormat | null | undefined
}): EChartsOption {
  const { mapName, definition, result, labels, appearance, numberFormat, formatFor } = params
  const style = definition.style
  const mergedAppearance = chartAppearance(style, appearance)
  const textColor = mergedAppearance?.textColor || '#303133'
  const dimField = definition.query.dimensions[0]?.alias || definition.query.dimensions[0]?.field
  const metric = definition.query.metrics[0]
  const key = metricResultKey(metric)
  const nullMode = definition.advanced?.nullMode || 'keep'

  const data: { name: string; value: number }[] = []
  for (const row of result.rows) {
    const name = normalizeProvinceName(String(row[dimField!] ?? ''))
    if (!name) continue
    const raw = row[key]
    let value = readNumber(raw)
    if (value == null) {
      if (nullMode === 'zero') value = 0
      else if (nullMode === 'hide') continue
      else continue
    }
    data.push({ name, value })
  }
  if (!data.length) throw new Error('没有可匹配到地图的行政区数据，请检查维度取值（如 广东省、北京市）')

  const values = data.map(d => d.value)
  const min = Math.min(...values)
  const max = Math.max(...values, min + 1)
  const axisFormatter = (value: number) =>
    String(numberFormat ? formatMetric(value, appearance, numberFormat) : displayNumber(value, appearance))

  return {
    color: paletteFromStyle(style, mergedAppearance),
    textStyle: { color: textColor },
    title: style?.showTitle ? { text: style.title || definition.name, left: 'center', textStyle: { color: textColor, fontSize: 14 } } : undefined,
    tooltip: {
      trigger: 'item',
      renderMode: 'richText',
      formatter: (p) => {
        const item = p as { name?: string; value?: number }
        return `${item.name ?? ''}: ${axisFormatter(Number(item.value ?? 0))}`
      }
    },
    visualMap: {
      min,
      max,
      left: 16,
      bottom: 24,
      text: ['高', '低'],
      calculable: true,
      inRange: { color: ['#e0f3ff', '#006edd'] },
      textStyle: { color: textColor }
    },
    series: [{
      type: 'map',
      map: mapName,
      roam: true,
      emphasis: { label: { show: true } },
      data
    }]
  }
}

export function isMapType(type: ChartType): boolean {
  return type === 'map'
}
