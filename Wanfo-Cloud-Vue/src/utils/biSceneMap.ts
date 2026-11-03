import {sceneData} from './biSceneData'
import type {MapContext} from '@/api/bi/chart'
export type MapPoint=[number,number]
export interface SceneMapRegion {code:string;name:string;polygons:MapPoint[][][];path:string}
/** Plate carree display: longitude/latitude axes share a scale. Not a surveying projection. */
export function prepareSceneMap(input:unknown):SceneMapRegion[] {
  const geo=input as {type?:string;features?:{properties?:{adcode?:unknown;name?:unknown;adchar?:unknown};geometry?:{type?:string;coordinates?:unknown}}[]}
  if(geo?.type!=='FeatureCollection' || !Array.isArray(geo.features) || !geo.features.length || geo.features.length>1000)throw Error('行政区边界格式或数量不受支持')
  let vertices=0,minX=Infinity,maxX=-Infinity,minY=Infinity,maxY=-Infinity
  const seen=new Set<string>()
  const regions=geo.features.map((feature,index)=>{
    const rawCode=String(feature.properties?.adcode ?? ''),business=/^\d{6}$/.test(rawCode)
    if(!business && !(feature.properties?.adchar==='JD' && rawCode==='100000_JD'))throw Error('行政区边界缺少有效地区编码')
    const code=business?rawCode:'boundary-'+index,name=business?String(feature.properties?.name || ''):'边界示意'
    if(seen.has(code) || (business && !name))throw Error('行政区边界编码重复或名称缺失')
    seen.add(code)
    const geometry=feature.geometry
    const polygons=geometry?.type==='Polygon'?[geometry.coordinates]:geometry?.type==='MultiPolygon'?geometry.coordinates:undefined
    if(!Array.isArray(polygons) || !polygons.length)throw Error('行政区边界仅支持面或多面几何')
    const checked=polygons.map(polygon=>{
      if(!Array.isArray(polygon) || !polygon.length)throw Error('行政区面缺少轮廓')
      return polygon.map(ring=>{
        if(!Array.isArray(ring) || ring.length<4)throw Error('行政区轮廓不完整')
        const points=ring.map(point=>{
          if(++vertices>200000 || !Array.isArray(point) || point.length<2 || !Number.isFinite(point[0]) || !Number.isFinite(point[1]) || Math.abs(point[0])>180 || Math.abs(point[1])>90)throw Error('行政区坐标无效或超过展示上限')
          const [x,y]=point as number[];minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y)
          return [x,y] as MapPoint
        })
        if(points[0][0]!==points.at(-1)![0] || points[0][1]!==points.at(-1)![1])throw Error('行政区轮廓未闭合')
        return points
      })
    })
    return {code,name,polygons:checked}
  })
  const spanX=maxX-minX,spanY=maxY-minY
  if(spanX<=0 || spanY<=0)throw Error('行政区边界范围无效')
  const scale=Math.min(12/spanX,8/spanY),cx=(minX+maxX)/2,cy=(minY+maxY)/2
  return regions.map(region=>{
    const polygons=region.polygons.map(polygon=>polygon.map(ring=>ring.map(([x,y])=>[(x-cx)*scale,(y-cy)*scale] as MapPoint)))
    return {...region,polygons,path:polygons.flatMap(polygon=>polygon.map(ring=>'M'+ring.map(([x,y])=>x.toFixed(4)+','+(-y).toFixed(4)).join('L')+'Z')).join('')}
  })
}
export function sceneMapValues(regions:SceneMapRegion[],rows:Record<string,unknown>[],dimension:string|undefined,metric:string|undefined,codeMode:boolean) {
  const values=new Map<string,number>(),duplicates=new Set<string>(),known=new Set(regions.map(region=>region.code))
  if(!codeMode || !dimension || !metric)return {values,invalid:0,unmatched:rows.length,duplicates:0}
  const data=sceneData(rows,dimension,metric,true,rows.length)
  let unmatched=0
  for(const record of data.records){
    const code=record.label.trim()
    if(!known.has(code) || !/^\d{6}$/.test(code)){unmatched++;continue}
    if(values.has(code) || duplicates.has(code)){duplicates.add(code);values.delete(code);continue}
    values.set(code,record.value)
  }
  return {values,invalid:data.invalid,unmatched,duplicates:duplicates.size}
}
export function sceneMapColor(value:number|undefined,maximum:number):string {
  if(value===undefined)return '#344252'
  const low=[25,92,112],high=value<0?[245,141,141]:[62,224,203],ratio=Math.min(1,Math.abs(value)/Math.max(maximum,1))
  return '#'+low.map((channel,index)=>Math.round(channel+(high[index]-channel)*ratio).toString(16).padStart(2,'0')).join('')
}
export function sceneMapDrill(context:MapContext|undefined,code:string,values:Map<string,number>) {
  if(!context?.canDrillDown || !context.field || !/^\d{6}$/.test(code) || !context.drillableRegions.includes(code) || !values.has(code))return
  return {field:context.field,value:code}
}
