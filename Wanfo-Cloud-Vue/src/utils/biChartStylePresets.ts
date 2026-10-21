import type { ChartType } from './biChartTypes'
import type { ChartVisualStyle } from '@/api/bi/chart'

export const BAR_TYPES: readonly ChartType[] = ['bar','hbar','stack','percent','combo']
export const LINE_TYPES: readonly ChartType[] = ['line','area','combo']
export const PIE_TYPES: readonly ChartType[] = ['pie','ring']
const shapeKeys: (keyof ChartVisualStyle)[] = ['showLabels','barMaxWidth','barRadius','lineWidth','symbolSize','showPoints','areaOpacity','pieInnerRadius','pieOuterRadius','pieLabelPosition','startAngle','ringShowTotal','gaugeMin','gaugeMax','gaugeStyle','funnelAlign','funnelGap','funnelMinSize','funnelMaxSize','funnelLabelPosition','funnelLabelContent','radarShape','radarSplitNumber','radarShowSplitArea','radarShowAxisName','scatterSymbol','scatterSize','scatterOpacity','scatterBorderWidth','scatterBorderColor','scatterLabelContent']
export interface ChartStylePreset { id:string; name:string; description:string; style:ChartVisualStyle }

export function chartStylePresets(type:ChartType):ChartStylePreset[] {
  if(type==='funnel')return [
    {id:'stages',name:'清晰阶段',description:'居中漏斗与外侧阶段名称，保留查询返回顺序',style:{palette:'business',funnelAlign:'center',funnelGap:4,funnelMinSize:0,funnelMaxSize:90,funnelLabelPosition:'right',funnelLabelContent:'name',showLabels:true}},
    {id:'values',name:'阶段数值',description:'内部名称与格式化数值，适合阶段较少的流程',style:{palette:'ocean',funnelAlign:'left',funnelGap:8,funnelMinSize:12,funnelMaxSize:92,funnelLabelPosition:'inside',funnelLabelContent:'nameValue',showLabels:true}}
  ]
  if(type==='radar')return [
    {id:'outline',name:'轻量轮廓',description:'多边形网格与轻填充，强调各指标轮廓',style:{palette:'business',radarShape:'polygon',radarSplitNumber:5,radarShowSplitArea:false,radarShowAxisName:true,lineWidth:2,areaOpacity:0.08,showPoints:true,symbolSize:5}},
    {id:'filled',name:'柔和雷达',description:'圆形网格与柔和填充，突出对比区域',style:{palette:'ocean',radarShape:'circle',radarSplitNumber:4,radarShowSplitArea:true,radarShowAxisName:true,lineWidth:2,areaOpacity:0.24,showPoints:false,symbolSize:6}}
  ]
  if(type==='scatter')return [
    {id:'clean',name:'清晰分布',description:'中等圆点与轻描边，适合查看分布',style:{palette:'business',scatterSymbol:'circle',scatterSize:12,scatterOpacity:0.8,scatterBorderWidth:1,scatterBorderColor:'#FFFFFF',showLabels:false}},
    {id:'dense',name:'密集分布',description:'小点与较低透明度，减轻重叠遮挡',style:{palette:'ocean',scatterSymbol:'circle',scatterSize:6,scatterOpacity:0.45,scatterBorderWidth:0,scatterBorderColor:'#FFFFFF',showLabels:false}}
  ]
  if(PIE_TYPES.includes(type))return [
    {id:'clean',name:'清爽构成',description:'外侧标签，留出清晰的分类阅读空间',style:{palette:'business',showLabels:true,pieLabelPosition:'outside',pieInnerRadius:type==='ring'?52:0,pieOuterRadius:70,startAngle:90,ringShowTotal:type==='ring'}},
    {id:'compact',name:'紧凑占比',description:'内部百分比，适合分类较少的卡片',style:{palette:'ocean',showLabels:true,pieLabelPosition:'inside',pieInnerRadius:type==='ring'?42:0,pieOuterRadius:74,startAngle:90,ringShowTotal:false}}
  ]
  if(type==='gauge')return [
    {id:'pointer',name:'经典指针',description:'自动范围，保留刻度与指针',style:{palette:'business',gaugeStyle:'pointer',gaugeMin:0}},
    {id:'progress',name:'目标进度',description:'0–100 范围，适合百分制指标；其他单位请调整范围',style:{palette:'ocean',gaugeStyle:'progress',gaugeMin:0,gaugeMax:100}}
  ]
  if(LINE_TYPES.includes(type))return [
    {id:'clean',name:'清晰趋势',description:'细线与轻量数据点，强调趋势',style:{palette:'business',lineWidth:2,symbolSize:5,showPoints:true,areaOpacity:0.12,barMaxWidth:36,barRadius:4,showLabels:false}},
    {id:'focus',name:'醒目趋势',description:'较粗线条和柔和面积，隐藏密集数据点',style:{palette:'ocean',lineWidth:4,symbolSize:7,showPoints:false,areaOpacity:0.26,barMaxWidth:44,barRadius:6,showLabels:false}}
  ]
  if(BAR_TYPES.includes(type))return [
    {id:'clean',name:'清爽比较',description:'适中柱宽，轻量圆角',style:{palette:'business',barMaxWidth:36,barRadius:4,showLabels:false}},
    {id:'ranking',name:'数值对比',description:'突出数值标签，便于快速比较',style:{palette:'ocean',barMaxWidth:28,barRadius:6,showLabels:true}}
  ]
  return []
}

/** Presets replace shape settings deliberately; title and legend stay user-owned. */
export function applyChartStylePreset(style:ChartVisualStyle,preset?:ChartStylePreset) {
  for(const key of shapeKeys)delete style[key]
  if(preset)Object.assign(style,preset.style)
}
