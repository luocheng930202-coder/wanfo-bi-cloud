import type {DashboardDefinition,Panel,PanelChartConfig,DashboardAppearance} from '@/api/bi/dashboard'

import type { ChartType } from '@/utils/biChartTypes'
export interface TemplateSlot {key:string;label:string;kind:'chart'|'kpi';type:ChartType;x:number;y:number;w:number;h:number}
export interface ScreenTemplate {schemaVersion:1;id:string;name:string;category:string;description:string;theme:'light'|'dark';kind?:'screen'|'dashboard';slots:TemplateSlot[]}
export interface TemplateBinding {source:'chart'|'dataset';chartId?:number;datasetId?:number;chartVersion?:number;config:PanelChartConfig}
// Curated layouts contain no resource IDs, credentials, ACLs, assets or business results.
export const screenTemplates:readonly ScreenTemplate[]=[
  {schemaVersion:1,id:'business-overview',name:'经营总览',category:'经营分析',description:'双指标与左右分析区，适合销售、业务经营概览。',theme:'dark',slots:[
    {key:'metric-a',label:'核心指标',kind:'kpi',type:'table',x:40,y:130,w:900,h:230},
    {key:'metric-b',label:'辅助指标',kind:'kpi',type:'table',x:980,y:130,w:900,h:230},
    {key:'analysis-a',label:'分类分析',kind:'chart',type:'bar',x:40,y:400,w:900,h:640},
    {key:'analysis-b',label:'业务明细',kind:'chart',type:'table',x:980,y:400,w:900,h:640}]},
  {schemaVersion:1,id:'operations-focus',name:'运营监测',category:'运营监测',description:'左侧指标与右侧主分析区，适合运营日常监测。',theme:'light',slots:[
    {key:'metric-a',label:'关键指标',kind:'kpi',type:'table',x:40,y:130,w:520,h:240},
    {key:'metric-b',label:'监测指标',kind:'kpi',type:'table',x:40,y:410,w:520,h:240},
    {key:'analysis-a',label:'分布分析',kind:'chart',type:'bar',x:600,y:130,w:1280,h:520},
    {key:'analysis-b',label:'监测明细',kind:'chart',type:'table',x:40,y:690,w:1840,h:350}]}
]
export function getScreenTemplate(id:string,version=1){
  const template=screenTemplates.find(t=>t.id===id)
  if(version!==1 || !template)throw new Error('不支持的模板或模板版本')
  return JSON.parse(JSON.stringify(template)) as ScreenTemplate
}
export function templateAppearance(theme:'light'|'dark'):DashboardAppearance {
  const dark=theme==='dark'
  return {theme,background:dark?'#0B1426':'#F1F5F9',cardBackground:dark?'#14243B':'#FFFFFF',textColor:dark?'#E7F0FF':'#20324D',borderColor:dark?'#284361':'#D7E2F0',palette:'ocean',titleSize:20,radius:12,decimals:2,grouping:true}
}
export function templateSlotPanel(slot:TemplateSlot,binding:TemplateBinding):Panel {
  return {key:slot.key,title:slot.label,componentType:slot.kind,x:slot.x,y:slot.y,w:slot.w,h:slot.h,
    chartId:binding.chartId,chartConfig:JSON.parse(JSON.stringify({type:binding.config.type,
      dimensions:binding.config.dimensions.map(({field,alias,timeGrain,timeFill})=>({field,alias,...(timeGrain?{timeGrain}:{}),...(timeFill?{timeFill}:{})})),
      metrics:binding.config.metrics.map(({field,alias,agg})=>({field,alias,agg})),
      orderBys:binding.config.orderBys.map(({field,order})=>({field,order})),limit:binding.config.limit,
      filterTree:binding.config.filterTree})),style:{showTitle:true,titleAlign:'left'}}
}
export function instantiateScreenTemplate(id:string,name:string,bindings:Record<string,TemplateBinding>,keyFactory:()=>string=()=>crypto.randomUUID()):DashboardDefinition {
  const template=getScreenTemplate(id)
  if(!name.trim() || name.trim().length>200)throw new Error('请填写200字以内的大屏名称')
  if(Object.keys(bindings).some(k=>!template.slots.some(s=>s.key===k)))throw new Error('存在未知绑定槽')
  const panels:Panel[]=[{key:keyFactory(),title:name.trim(),componentType:'text',x:40,y:20,w:1840,h:90,content:{text:template.description},style:{showTitle:true,titleAlign:'left'}}]
  for(const slot of template.slots){
    const binding=bindings[slot.key]
    if(!binding || !Number.isSafeInteger(binding.chartId) || binding.chartId!<=0 || (!binding.config?.metrics?.length && binding.config.type!=='table'))throw new Error('请完整绑定并验证所有数据组件')
    if(slot.kind==='kpi' && (binding.config.dimensions.length || binding.config.metrics.length!==1 || binding.config.type!=='table'))throw new Error('指标卡须绑定一个聚合指标且不带维度')
    panels.push({...templateSlotPanel(slot,binding),key:keyFactory()})
  }
  if(new Set(panels.map(p=>p.key)).size!==panels.length)throw new Error('组件标识重复')
  return {schemaVersion:1,name:name.trim(),kind:'screen',layout:'absolute',scene:{width:1920,height:1080,fit:'contain',refreshSeconds:0},appearance:templateAppearance(template.theme),panels,interaction:{controls:[],links:[]}}
}
export const dashboardTemplates:readonly ScreenTemplate[]=[
  {schemaVersion:1,id:'grid-business-overview',name:'经营总览看板',category:'经营分析',description:'双指标与左右分析区，适合销售、业务经营概览。',theme:'light',kind:'dashboard',slots:[
    {key:'metric-a',label:'核心指标',kind:'kpi',type:'table',x:0,y:0,w:6,h:3},
    {key:'metric-b',label:'辅助指标',kind:'kpi',type:'table',x:6,y:0,w:6,h:3},
    {key:'analysis-a',label:'分类分析',kind:'chart',type:'bar',x:0,y:3,w:6,h:6},
    {key:'analysis-b',label:'业务明细',kind:'chart',type:'table',x:6,y:3,w:6,h:6}]},
  {schemaVersion:1,id:'grid-operations-focus',name:'运营监测看板',category:'运营监测',description:'左侧指标与右侧主分析区，适合运营日常监测。',theme:'light',kind:'dashboard',slots:[
    {key:'metric-a',label:'关键指标',kind:'kpi',type:'table',x:0,y:0,w:3,h:3},
    {key:'metric-b',label:'监测指标',kind:'kpi',type:'table',x:0,y:3,w:3,h:3},
    {key:'analysis-a',label:'分布分析',kind:'chart',type:'bar',x:3,y:0,w:9,h:6},
    {key:'analysis-b',label:'监测明细',kind:'chart',type:'table',x:0,y:6,w:12,h:5}]}
]
export function getDashboardTemplate(id:string,version=1){
  const template=dashboardTemplates.find(t=>t.id===id)
  if(version!==1 || !template)throw new Error('不支持的模板或模板版本')
  return JSON.parse(JSON.stringify(template)) as ScreenTemplate
}
function validDashboardLayout(panels:Panel[]){
  return panels.every((p,i)=>Number.isInteger(p.x)&&Number.isInteger(p.y)&&Number.isInteger(p.w)&&Number.isInteger(p.h)&&p.x>=0&&p.y>=0&&p.w>=2&&p.w<=12&&p.h>=2&&p.h<=12&&p.x+p.w<=12&&p.y+p.h<=120&&panels.slice(i+1).every(o=>p.x>=o.x+o.w||p.x+p.w<=o.x||p.y>=o.y+o.h||p.y+p.h<=o.y))
}
export function instantiateDashboardTemplate(id:string,name:string,bindings:Record<string,TemplateBinding>,keyFactory:()=>string=()=>crypto.randomUUID()):DashboardDefinition {
  const template=getDashboardTemplate(id)
  if(!name.trim() || name.trim().length>200)throw new Error('请填写200字以内的看板名称')
  if(Object.keys(bindings).some(k=>!template.slots.some(s=>s.key===k)))throw new Error('存在未知绑定槽')
  const panels:Panel[]=[]
  for(const slot of template.slots){
    const binding=bindings[slot.key]
    if(!binding || !Number.isSafeInteger(binding.chartId) || binding.chartId!<=0 || (!binding.config?.metrics?.length && binding.config.type!=='table'))throw new Error('请完整绑定并验证所有数据组件')
    if(slot.kind==='kpi' && (binding.config.dimensions.length || binding.config.metrics.length!==1 || binding.config.type!=='table'))throw new Error('指标卡须绑定一个聚合指标且不带维度')
    panels.push({...templateSlotPanel(slot,binding),key:keyFactory()})
  }
  if(new Set(panels.map(p=>p.key)).size!==panels.length)throw new Error('组件标识重复')
  if(!validDashboardLayout(panels))throw new Error('看板布局重叠或越界')
  return {schemaVersion:1,name:name.trim(),kind:'dashboard',layout:'grid',appearance:templateAppearance(template.theme),panels,interaction:{controls:[],links:[]}}
}
