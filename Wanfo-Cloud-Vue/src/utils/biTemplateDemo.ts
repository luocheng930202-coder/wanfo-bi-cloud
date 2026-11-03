import type {PanelResult} from '@/api/bi/dashboard'
import type {LayoutTemplateFile} from './biTemplateFiles'

/** Presentation-only fixtures. Never submit these payloads to draft, chart or query APIs. */
export function templateDemoPayloads(file:LayoutTemplateFile):Record<string,PanelResult>{
  const payloads:Record<string,PanelResult>={}
  for(const panel of file.panels){
    if(!['chart','kpi'].includes(panel.componentType || ''))continue
    const type=panel.chartType
    if(!type || !['bar','line','area','pie','ring','table','map'].includes(type))continue
    const kpi=panel.componentType==='kpi',map=type==='map'
    const dimension=map?'demo_region':'demo_category',metric='demo_value'
    const categories=map?['110000','320000','330000','440000','510000']:['示例一','示例二','示例三','示例四','示例五']
    const rows=kpi?[{[metric]:(panel.style?.numberFormat?.mode==='percent' ? 0.963 : 28640)}]:categories.map((category,index)=>({[dimension]:category,[metric]:[82,136,108,174,152][index]}))
    payloads[panel.key]={data:{columns:kpi?[metric]:[dimension,metric],rows,rowCount:rows.length,costMs:0},presentation:{type,dimensions:kpi?[]:[dimension],metrics:[metric],fieldLabels:{[dimension]:map?'演示地区':'演示分类',[metric]:'演示数值'},...(map?{advanced:{mapScope:'china-provinces',mapValueMode:'code'}}:{})}}
  }
  return payloads
}
