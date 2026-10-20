import * as echarts from 'echarts/core'
import request from '@/utils/request'

export const MAP_SCOPES = {
  'china-provinces': '中国省级行政区'
} as const

export type MapScopeId = keyof typeof MAP_SCOPES

const registered = new Set<string>()

const ALIASES: Record<string, string> = {
  北京: '北京市',
  天津: '天津市',
  上海: '上海市',
  重庆: '重庆市',
  内蒙古: '内蒙古自治区',
  广西: '广西壮族自治区',
  西藏: '西藏自治区',
  宁夏: '宁夏回族自治区',
  新疆: '新疆维吾尔自治区',
  香港: '香港特别行政区',
  澳门: '澳门特别行政区'
}

const SUFFIXES = ['特别行政区', '维吾尔自治区', '壮族自治区', '回族自治区', '自治区', '省', '市']

/** Normalize dataset region labels to DataV / ECharts province names. */
export function normalizeProvinceName(raw: string): string {
  let name = String(raw ?? '').trim()
  if (!name) return ''
  if (ALIASES[name]) return ALIASES[name]
  for (const suf of SUFFIXES) {
    if (name.endsWith(suf)) return name
  }
  if (name.length >= 2) {
    const withProvince = name + '省'
    const withCity = name + '市'
    if (['北京', '天津', '上海', '重庆'].includes(name)) return withCity
    return withProvince
  }
  return name
}

export function defaultMapScope(): MapScopeId {
  return 'china-provinces'
}

export async function fetchMapGeo(scope: MapScopeId): Promise<unknown> {
  const res = await request({ url: `/bi/geo/${scope}`, method: 'get', timeout: 60000 })
  return res.data ?? res
}

export async function ensureMapRegistered(scope: MapScopeId = defaultMapScope()): Promise<string> {
  const mapName = `bi_${scope}`
  if (registered.has(mapName)) return mapName
  const geo = await fetchMapGeo(scope)
  echarts.registerMap(mapName, geo as Parameters<typeof echarts.registerMap>[1])
  registered.add(mapName)
  return mapName
}
