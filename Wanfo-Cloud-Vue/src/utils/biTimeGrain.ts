import type { ChartDefinition, Field } from '@/api/bi/chart'

export const TIME_GRAINS = [{value:'day',label:'日'},{value:'week',label:'周（周一开始）'},{value:'month',label:'月'},{value:'quarter',label:'季度'},{value:'year',label:'年'}] as const
export function temporalField(type:string) { return /^(DATE|DATETIME|TIMESTAMP)(\(\d+\))?$/i.test(type.trim()) }
export function supportsTimeGrain(type:string) { return ['bar','hbar','line','area','stack','percent','combo'].includes(type) }
export function validateTimeGrain(definition:ChartDefinition,fields:Field[]):string|null {
  if(definition.query.dimensions.length===2 && definition.advanced?.drillFields?.length)return '使用系列维度时请先移除维度下钻'
  if(definition.query.timeComparison){
    const q=definition.query,d=q.dimensions[0]
    if(!['previous','year'].includes(q.timeComparison||'') || !['bar','hbar','line','area'].includes(definition.type))return '时间比较仅支持柱状图、条形图、折线图和面积图，请先关闭比较再切换类型'
    if(q.dimensions.length!==1 || !d?.timeGrain || !q.metrics.length || q.metrics.length>3 || q.metrics.some(m=>m.agg==='NONE'))return '时间比较需要一个设置粒度的时间维度及 1～3 个聚合指标'
    if((q.limit||1000)>1000 || (q.orderBys?.length||0)>1 || q.orderBys?.some(o=>![d.field,d.alias||d.field].includes(o.field)))return '时间比较最多 1000 个周期，且仅支持按时间排序'
  }
  for(const dimension of definition.query.dimensions) {
    if(dimension.timeFill!=null && !['none','null','zero'].includes(dimension.timeFill))return '请选择有效的缺失周期处理方式'
    if(dimension.timeFill && dimension.timeFill!=='none') {
      if(!dimension.timeGrain || definition.query.dimensions.length!==1 || !definition.query.metrics.length || definition.query.metrics.some(m=>m.agg==='NONE'))return '时间补齐需要一个已设置粒度的时间维度及聚合指标'
      if((definition.query.orderBys?.length||0)>1 || definition.query.orderBys?.some(o=>![dimension.field,dimension.alias||dimension.field].includes(o.field)))return '时间补齐仅支持按时间排序，请清除指标排名或其他排序'
    }
    if(dimension.timeGrain==null)continue
    if(!supportsTimeGrain(definition.type))return '当前图表类型不支持时间粒度，请恢复原始时间后切换类型'
    if(!TIME_GRAINS.some(g=>g.value===dimension.timeGrain))return '请选择有效的时间粒度'
    if(!temporalField(fields.find(f=>f.name===dimension.field)?.type||''))return '时间粒度仅支持日期或日期时间字段，请先转换文本日期'
    if(definition.advanced?.drillFields?.length)return '时间粒度暂不支持维度下钻，请清除下钻字段或恢复原始时间'
  }
  return null
}
