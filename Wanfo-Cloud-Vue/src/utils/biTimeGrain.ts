import type { ChartDefinition, Field } from '@/api/bi/chart'

export const TIME_GRAINS = [{value:'day',label:'日'},{value:'week',label:'周（周一开始）'},{value:'month',label:'月'},{value:'quarter',label:'季度'},{value:'year',label:'年'}] as const
export function temporalField(type:string) { return /^(DATE|DATETIME|TIMESTAMP)(\(\d+\))?$/i.test(type.trim()) }
export function supportsTimeGrain(type:string) { return ['bar','hbar','line','area','stack','percent','combo'].includes(type) }
export function validateTimeGrain(definition:ChartDefinition,fields:Field[]):string|null {
  for(const dimension of definition.query.dimensions) {
    if(dimension.timeGrain==null)continue
    if(!supportsTimeGrain(definition.type))return '当前图表类型不支持时间粒度，请恢复原始时间后切换类型'
    if(!TIME_GRAINS.some(g=>g.value===dimension.timeGrain))return '请选择有效的时间粒度'
    if(!temporalField(fields.find(f=>f.name===dimension.field)?.type||''))return '时间粒度仅支持日期或日期时间字段，请先转换文本日期'
    if(definition.advanced?.drillFields?.length)return '时间粒度暂不支持维度下钻，请清除下钻字段或恢复原始时间'
  }
  return null
}
