import type { EChartsOption } from 'echarts'
import type { ChartDefinition, ChartResult } from '@/api/bi/chart'
import type { DashboardAppearance, NumberFormat, MetricStyle } from '@/api/bi/dashboard'
import { displayNumber, formatMetric, metricColor } from '@/utils/biPresentation'
import { chartAppearance, paletteFromStyle } from '@/utils/biChartPresentation'
import { isPieLike, type ChartType } from '@/utils/biChartTypes'

export function metricResultKey(m: ChartDefinition['query']['metrics'][number]): string {
  return m.alias || (m.agg === 'NONE' ? m.field : `${m.agg.toLowerCase()}_${m.field}`)
}

function readNumber(raw: unknown): number | null {
  if (raw == null) return null
  const value = Number(raw)
  if (!Number.isFinite(value) || Math.abs(value) > Number.MAX_SAFE_INTEGER) return null
  return value
}

function piePoint(value: number, key: string, metricStyles?: MetricStyle[]) {
  if (value < 0) throw new Error('饼图/环形图/漏斗图不支持负数，请改用柱状图或明细表')
  const color = metricColor(value, key, metricStyles)
  return color ? { value, itemStyle: { color } } : value
}

export function buildChartPreviewOption(params: {
  type: ChartType
  definition: ChartDefinition
  result: ChartResult
  labels: Record<string, string>
  appearance?: DashboardAppearance
  numberFormat?: NumberFormat | null
  metricStyles?: MetricStyle[]
  formatFor: (key: string) => NumberFormat | null | undefined
}): EChartsOption {
  const { type, definition, result, labels, appearance, numberFormat, metricStyles, formatFor } = params
  const style = definition.style
  const advanced = definition.advanced
  const mergedAppearance = chartAppearance(style, appearance)
  const metrics = definition.query.metrics
  const dimField = definition.query.dimensions[0]?.alias || definition.query.dimensions[0]?.field
  const categories = dimField ? result.rows.map(r => String(r[dimField] ?? '空值')) : result.rows.map((_, i) => String(i + 1))
  const textColor = mergedAppearance?.textColor || '#303133'
  const nullMode = advanced?.nullMode || 'keep'
  const smoothDefault = advanced?.smoothLine !== false
  const axisFormatter = (value: number) =>
    String(numberFormat ? formatMetric(value, appearance, numberFormat) : displayNumber(value, appearance))

  const tooltipFormatter = (input: unknown) => {
    const items = Array.isArray(input) ? input : [input]
    return items
      .map((item: { seriesIndex?: number; dataIndex?: number; name?: string; value?: unknown }) => {
        const metric = metrics[item.seriesIndex ?? 0]
        const key = metric ? metricResultKey(metric) : ''
        const raw = item.dataIndex != null ? result.rows[item.dataIndex]?.[key] : item.value
        const label = key ? labels[key] || key : ''
        return `${String(item.name ?? '')} · ${label}: ${String(formatMetric(raw, appearance, formatFor(key)))}`
      })
      .join('\n')
  }

  const legendTop = style?.legendPosition === 'bottom' ? undefined : 8
  const legendBottom = style?.legendPosition === 'bottom' ? 8 : undefined
  const base: EChartsOption = {
    color: paletteFromStyle(style, mergedAppearance),
    textStyle: { color: textColor },
    title: style?.showTitle ? { text: style.title || definition.name, left: 'center', textStyle: { color: textColor, fontSize: 14 } } : undefined,
    tooltip: {
      trigger: isPieLike(type) || type === 'gauge' || type === 'funnel' ? 'item' : 'axis',
      renderMode: 'richText',
      formatter: tooltipFormatter
    },
    legend: style?.showLegend === false
      ? { show: false }
      : { type: 'scroll', top: legendTop, bottom: legendBottom, textStyle: { color: textColor } }
  }

  function normalizeValue(raw: unknown, key: string): number | null {
    if (raw == null) {
      if (nullMode === 'zero') return 0
      return null
    }
    const value = readNumber(raw)
    if (value == null) return nullMode === 'zero' ? 0 : null
    return value
  }

  if (type === 'gauge') {
    const key = metricResultKey(metrics[0])
    const raw = result.rows[0]?.[key]
    const value = normalizeValue(raw, key)
    if (value == null) throw new Error('仪表盘需要可绘制的数值')
    const max = Math.max(value * 1.2, value + 1, 100)
    return {
      ...base,
      series: [{
        type: 'gauge',
        min: 0,
        max,
        detail: { formatter: (v: number) => axisFormatter(v), color: textColor },
        data: [{ value, name: labels[key] || key }]
      }]
    }
  }

  if (type === 'scatter') {
    const [xMetric, yMetric] = metrics
    const xKey = metricResultKey(xMetric)
    const yKey = metricResultKey(yMetric)
    const points = result.rows.map((row, i) => {
      const x = readNumber(row[xKey])
      const y = readNumber(row[yKey])
      if (x == null || y == null) throw new Error('散点图度量须为有效数值')
      return [x, y, categories[i]]
    })
    return {
      ...base,
      grid: { left: 40, right: 20, bottom: 40, top: 45, containLabel: true },
      xAxis: { type: 'value', name: labels[xKey] || xKey, axisLabel: { color: textColor, formatter: axisFormatter } },
      yAxis: { type: 'value', name: labels[yKey] || yKey, axisLabel: { color: textColor, formatter: axisFormatter }, splitLine: { lineStyle: { color: appearance?.borderColor || '#e4e7ed' } } },
      series: [{ type: 'scatter', name: `${labels[xKey] || xKey} × ${labels[yKey] || yKey}`, data: points }]
    }
  }

  if (type === 'radar') {
    const keys = metrics.map(metricResultKey)
    const indicators = keys.map(key => {
      let max = 0
      for (const row of result.rows) {
        const v = readNumber(row[key]) ?? 0
        if (v > max) max = v
      }
      return { name: labels[key] || key, max: max > 0 ? max * 1.1 : 1 }
    })
    const seriesData = result.rows.map((row, i) => ({
      name: categories[i],
      value: keys.map(key => readNumber(row[key]) ?? 0)
    }))
    return {
      ...base,
      radar: { indicator: indicators },
      series: [{ type: 'radar', data: seriesData }]
    }
  }

  if (isPieLike(type)) {
    const key = metricResultKey(metrics[0])
    const data = result.rows.map((row, i) => {
      const v = normalizeValue(row[key], key)
      if (v == null) {
        if (nullMode === 'hide') return null
        throw new Error('数值超出安全绘图范围，请查看下方明细或切换为明细表')
      }
      const point = piePoint(v, key, metricStyles)
      const name = categories[i]
      return typeof point === 'object' ? { name, ...point } : { name, value: point }
    }).filter((item): item is NonNullable<typeof item> => item != null)
    const radius = type === 'ring' ? ['45%', '70%'] : ['35%', '65%']
    if (type === 'funnel') {
      return { ...base, series: [{ type: 'funnel' as const, data, sort: 'descending' as const }] }
    }
    return { ...base, series: [{ type: 'pie' as const, radius, data }] }
  }

  const keys = metrics.map(metricResultKey)
  const valueMatrix = result.rows.map(row =>
    keys.map(key => {
      const v = normalizeValue(row[key], key)
      if (v == null) return null
      const color = metricColor(row[key], key, metricStyles)
      return color ? { value: v, itemStyle: { color } } : v
    })
  )

  const cartesianSeries: EChartsOption['series'] = metrics.map((m, mi) => {
    const key = keys[mi]
    const name = labels[key] || key
    const data = valueMatrix.map(row => row[mi])
    if (type === 'combo') {
      return mi === 0
        ? { name, type: 'bar' as const, data, yAxisIndex: 0 }
        : { name, type: 'line' as const, data, yAxisIndex: 1, smooth: smoothDefault }
    }
    if (type === 'line' || type === 'area') {
      return { name, type: 'line' as const, data, smooth: type === 'line' && smoothDefault, areaStyle: type === 'area' ? {} : undefined }
    }
    const stack = type === 'stack' || type === 'percent' ? 'total' : undefined
    let seriesData = data
    if (type === 'percent') {
      seriesData = result.rows.map((_, rowIdx) => {
        const values = keys.map((_, k) => readNumber(result.rows[rowIdx][keys[k]]) ?? 0)
        const sum = values.reduce((a, b) => a + b, 0) || 1
        const v = values[mi] / sum * 100
        return Math.round(v * 100) / 100
      })
    }
    return { name, type: 'bar' as const, stack, data: seriesData }
  })

  const valueYAxis = { type: 'value' as const, axisLabel: { color: textColor, formatter: axisFormatter }, splitLine: { lineStyle: { color: appearance?.borderColor || '#e4e7ed' } } }
  const percentYAxis = { type: 'value' as const, max: 100, axisLabel: { color: textColor, formatter: (v: number) => `${v}%` }, splitLine: { lineStyle: { color: appearance?.borderColor || '#e4e7ed' } } }
  const categoryYAxis = { type: 'category' as const, data: categories, axisLabel: { color: textColor }, axisLine: { lineStyle: { color: textColor } } }
  const categoryXAxis = {
    type: 'category' as const,
    data: categories,
      axisLabel: { rotate: style?.axisLabelRotate ?? (categories.length > 8 ? 30 : 0), color: textColor },
    axisLine: { lineStyle: { color: textColor } }
  }

  if (type === 'hbar') {
    return {
      ...base,
      grid: { left: 30, right: 20, bottom: 40, top: 45, containLabel: true },
      xAxis: valueYAxis,
      yAxis: categoryYAxis,
      series: cartesianSeries
    }
  }

  const yAxes: EChartsOption['yAxis'] = type === 'combo'
    ? [
        valueYAxis,
        { type: 'value' as const, axisLabel: { color: textColor, formatter: axisFormatter }, splitLine: { show: false } }
      ]
    : type === 'percent' ? percentYAxis : valueYAxis

  return {
    ...base,
    grid: { left: 30, right: type === 'combo' ? 48 : 20, bottom: 60, top: 45, containLabel: true },
    xAxis: categoryXAxis,
    yAxis: yAxes,
    series: cartesianSeries
  }
}
