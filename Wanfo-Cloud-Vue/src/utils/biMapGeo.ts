import * as echarts from 'echarts/core'
import request from '@/utils/request'
import {mapScopeCode,type GeoCatalogIndex,type GeoFeature} from './biMapRegions'

export const MAP_SCOPES = {
  'china-provinces': '中国省级行政区'
} as const

export type MapScopeId = string

const registered = new Set<string>()
const features=new Map<string,GeoFeature[]>(),pending=new Map<string,Promise<string>>()
let catalogRequest:Promise<GeoCatalogIndex>|undefined
export function fetchGeoCatalog():Promise<GeoCatalogIndex>{
  if(catalogRequest)return catalogRequest
  const task=(async()=>{const response:GeoCatalogIndex|{data:GeoCatalogIndex}=await request({url:'/bi/geo/regions',timeout:60000});const value='data' in response?response.data:response;if(!Array.isArray(value.regions))throw new Error('地图目录格式无效');return value})()
  catalogRequest=task;void task.catch(()=>{if(catalogRequest===task)catalogRequest=undefined})
  return task
}
export function registeredGeoFeatures(scope:MapScopeId):GeoFeature[]{return features.get(mapScopeCode(scope)) || []}

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
  const code=mapScopeCode(scope)
  const res = await request({ url: `/bi/geo/${code==='100000'?'china-provinces':code}`, method: 'get', timeout: 60000 })
  return res.data ?? res
}

export async function ensureMapRegistered(scope: MapScopeId = defaultMapScope()): Promise<string> {
  const code=mapScopeCode(scope),mapName = `bi_${code}`
  if (registered.has(mapName)) return mapName
  if(pending.has(code))return pending.get(code)!
  const task=(async()=>{const geo=await fetchMapGeo(scope) as {features?:{properties?:GeoFeature}[]};
    if(!Array.isArray(geo.features))throw new Error('地图边界格式无效')
    features.set(code,geo.features.map(f=>f.properties).filter((p):p is GeoFeature=>!!p && /^[0-9]{6}$/.test(String(p.adcode)) && !!p.name))
    echarts.registerMap(mapName,geo as Parameters<typeof echarts.registerMap>[1]);registered.add(mapName);return mapName
  })();pending.set(code,task)
  try{return await task}finally{pending.delete(code)}
}
