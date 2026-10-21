import {BI_FONT_FAMILY,BI_TYPE} from '@/utils/biTheme'
import type { S2Options, S2DataConfig, ThemeCfg } from '@antv/s2'
import type { ChartDefinition, ChartResult } from '@/api/bi/chart'
import type {DashboardAppearance,MetricStyle} from '@/api/bi/dashboard'
import {metricColor} from '@/utils/biPresentation'
import { metricResultKey } from '@/utils/biEchartsPreview'

function dimKey(definition: ChartDefinition, field: string): string {
  const d = definition.query.dimensions.find(x => x.field === field)
  return d?.alias || field
}

export function pivotFieldKeys(definition: ChartDefinition): { rows: string[]; columns: string[]; values: string[] } {
  const rows = (definition.advanced?.pivotRowFields || []).map(f => dimKey(definition, f))
  const columns = (definition.advanced?.pivotColFields || []).map(f => dimKey(definition, f))
  const values = definition.query.metrics.map(metricResultKey)
  return { rows, columns, values }
}

export function buildPivotDataConfig(definition: ChartDefinition, result: ChartResult, format?:(key:string,value:unknown)=>string,labels:Record<string,string>={}): S2DataConfig {
  const fields = pivotFieldKeys(definition)
  const seen=new Set<string>()
  for(const row of result.rows){const identity=JSON.stringify([...fields.rows,...fields.columns].map(k=>row[k] ?? null));if(seen.has(identity))throw new Error('透视单元格出现重复分组，请统一查询维度与行列维度');seen.add(identity)}
  return { fields:{...fields,valueInCols:definition.style?.pivotValuesInCols!==false}, data: result.rows as Record<string, string | number>[],meta:[...fields.rows,...fields.columns,...fields.values].map(key=>({field:key,name:labels[key] || key,...(fields.values.includes(key) && format?{formatter:(value:unknown)=>typeof value==='number' && !Number.isFinite(value)?'—':format(key,value)}:{})})) }
}

export function validatePivotConfig(definition: ChartDefinition): string | null {
  if (definition.type !== 'pivot') return null
  const rows = definition.advanced?.pivotRowFields || []
  const cols = definition.advanced?.pivotColFields || []
  if (!rows.length) return '透视表须配置至少一个行维度'
  if (!cols.length) return '透视表须配置至少一个列维度'
  if (rows.length > 3) return '透视表行维度最多 3 个'
  if (cols.length > 2) return '透视表列维度最多 2 个'
  if (!definition.query.metrics.length || definition.query.metrics.length > 3) return '透视表须配置 1～3 个聚合度量'
  if (definition.query.metrics.some(m => (m.agg || 'NONE').toUpperCase() === 'NONE')) return '透视表度量须使用聚合函数'
  const names = new Set(definition.query.dimensions.map(d => d.field))
  for (const f of [...rows, ...cols]) if (!names.has(f)) return `维度 ${f} 未加入查询分组`
  const overlap = rows.find(f => cols.includes(f))
  if (overlap) return `字段 ${overlap} 不能同时作为行与列维度`
  if(new Set([...rows,...cols]).size!==rows.length+cols.length || names.size!==definition.query.dimensions.length || names.size!==rows.length+cols.length)return '透视查询分组须与行列维度完全一致且不重复'
  if(pivotTotalsRequested(definition) && !pivotTotalsAvailable(definition))return '当前结果汇总仅支持 SUM、COUNT、MIN、MAX；AVG及去重计数需从明细重新聚合'
  if(definition.style?.pivotRowSubtotals && rows.length<2 || definition.style?.pivotColSubtotals && cols.length<2)return '小计须至少两个对应层级维度'
  return null
}

export function syncPivotQueryDimensions(definition: ChartDefinition): void {
  if (definition.type !== 'pivot') return
  const ordered = [...(definition.advanced?.pivotRowFields || []), ...(definition.advanced?.pivotColFields || [])]
  const prev = definition.query.dimensions
  definition.query.dimensions = ordered.map(field => prev.find(d => d.field === field) || { field, alias: field })
}

export function pivotTotalsRequested(d:ChartDefinition){const s=d.style;return !!(s?.pivotHierarchy==='tree' || s?.pivotRowTotals || s?.pivotColTotals || s?.pivotRowSubtotals || s?.pivotColSubtotals)}
export function pivotTotalsAvailable(d:ChartDefinition){return !!d.query.metrics.length && d.query.metrics.every(m=>['SUM','COUNT','MIN','MAX'].includes(m.agg.toUpperCase()))}
function pivotDecimal(value:unknown){if(value==null || value==='')return null;const m=String(value).match(/^([+-]?)([0-9]+)(?:\.([0-9]*))?(?:[eE]([+-]?[0-9]+))?$/);if(!m)throw new Error('汇总包含无效数值');const exponent=Number(m[4] || 0);if(Math.abs(exponent)>1000)throw new Error('汇总数值超出安全精度');return {n:BigInt((m[1]==='-'?'-':'')+m[2]+(m[3] || '')),p:(m[3]?.length || 0)-exponent}}
function pivotDecimalEqual(a:{n:bigint;p:number},b:{n:bigint;p:number}){const p=Math.max(a.p,b.p);return a.n*10n**BigInt(p-a.p)===b.n*10n**BigInt(p-b.p)}
export function pivotTotalValue(d:ChartDefinition,key:string,values:unknown[]):number {
 const metric=d.query.metrics.find(m=>metricResultKey(m)===key);if(!metric || !['SUM','COUNT','MIN','MAX'].includes(metric.agg.toUpperCase()))throw new Error('此指标需要从明细重新聚合')
 const decimals=values.map(pivotDecimal).filter((v):v is NonNullable<typeof v>=>v!=null);if(!decimals.length)return NaN
 const p=Math.max(...decimals.map(v=>v.p));const scaled=decimals.map(v=>v.n*10n**BigInt(p-v.p))
 const n=metric.agg.toUpperCase()==='MIN'?scaled.reduce((a,b)=>a<b?a:b):metric.agg.toUpperCase()==='MAX'?scaled.reduce((a,b)=>a>b?a:b):scaled.reduce((a,b)=>a+b,0n)
 const value=Number(n.toString()+'e'+(-p));const roundtrip=pivotDecimal(value)
 if(!Number.isFinite(value) || Math.abs(value)>Number.MAX_SAFE_INTEGER || !roundtrip || !pivotDecimalEqual({n,p},roundtrip))throw new Error('汇总数值超出安全显示精度，请关闭汇总或调整数据口径')
 return value
}
export function buildPivotOptions(d:ChartDefinition,result:ChartResult,width:number,styles:MetricStyle[]=[]):S2Options {
 const s=d.style,fields=pivotFieldKeys(d)
 const calc=(query:Record<string,unknown>,data:{'$$value$$'?:unknown}[])=>pivotTotalValue(d,String(query['$$extra$$']),data.map(v=>v['$$value$$']))
 const total=(row:boolean)=>({showGrandTotals:row?!!s?.pivotRowTotals:!!s?.pivotColTotals,showSubTotals:row?!!s?.pivotRowSubtotals:!!s?.pivotColSubtotals,subTotalsDimensions:(row?fields.rows:fields.columns).slice(0,-1),calcGrandTotals:{calcFunc:calc},calcSubTotals:{calcFunc:calc},grandTotalsLabel:'当前结果总计',subTotalsLabel:'当前结果小计',reverseGrandTotalsLayout:!!s?.pivotTotalsFirst,reverseSubTotalsLayout:!!s?.pivotTotalsFirst})
 const background=fields.values.map(key=>{const values=result.rows.map(r=>r[key]).filter(v=>v!=null && v!=='').map(Number).filter(Number.isFinite);const min=values.length?Math.min(...values):0,max=values.length?Math.max(...values):0;return {field:key,mapping:(value:unknown)=>{if(value==null || value==='' || !Number.isFinite(Number(value)))return undefined;const t=max===min?0.5:Math.max(0,Math.min(1,(Number(value)-min)/(max-min)));const low=s?.pivotLowColor || '#EFF6FF',high=s?.pivotHighColor || '#2563EB';const fill='#'+[0,2,4].map(i=>Math.round(parseInt(low.slice(i+1,i+3),16)*(1-t)+parseInt(high.slice(i+1,i+3),16)*t).toString(16).padStart(2,'0')).join('');return {fill,intelligentReverseTextColor:true}}}})
 return {width,height:420,hierarchyType:s?.pivotHierarchy || 'grid',interaction:{hoverHighlight:true},style:{dataCell:{width:s?.pivotColumnWidth || 120,height:s?.pivotRowHeight || 32},colCell:{width:s?.pivotColumnWidth || 120}},totals:{row:total(true),col:total(false)},conditions:{background:s?.pivotColorScale?background:[],text:fields.values.map(key=>({field:key,mapping:(value:unknown)=>{const fill=metricColor(value,key,styles);return fill?{fill}:undefined}}))}}
}
export function pivotTheme(d:ChartDefinition,appearance?:DashboardAppearance):ThemeCfg {
 const s=d.style,dark=appearance?.theme==='dark',bg=appearance?.cardBackground || (dark?'#1F2937':'#FFFFFF'),text=appearance?.textColor || (dark?'#F3F4F6':'#303133'),border=appearance?.borderColor || (dark?'#4B5563':'#E2E8F0')
 const header={cell:{backgroundColor:s?.pivotHeaderBackground || (dark?'#25344A':'#F1F5F9'),horizontalBorderColor:border,verticalBorderColor:border},text:{fill:s?.pivotHeaderColor || text,fontFamily:BI_FONT_FAMILY,fontSize:BI_TYPE.chart},bolderText:{fill:s?.pivotHeaderColor || text,fontFamily:BI_FONT_FAMILY,fontSize:BI_TYPE.chart},measureText:{fill:s?.pivotHeaderColor || text,fontFamily:BI_FONT_FAMILY,fontSize:BI_TYPE.chart}}
 return {name:dark?'dark':'default',theme:{background:{color:bg},dataCell:{cell:{backgroundColor:bg,horizontalBorderColor:border,verticalBorderColor:border},text:{fill:text,fontFamily:BI_FONT_FAMILY,fontSize:BI_TYPE.chart},bolderText:{fill:text,fontFamily:BI_FONT_FAMILY,fontSize:BI_TYPE.chart}},rowCell:header,colCell:header,cornerCell:header}}
}
