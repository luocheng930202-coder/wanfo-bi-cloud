import {resolveBiTheme,BI_TYPE} from '@/utils/biTheme'
import type { EChartsOption } from 'echarts'
import type { ChartDefinition, ChartResult } from '@/api/bi/chart'
import type { DashboardAppearance, NumberFormat, MetricStyle } from '@/api/bi/dashboard'
import { displayNumber, formatMetric, metricColor } from '@/utils/biPresentation'
import { chartAppearance, paletteFromStyle, resolvedMetricStyles, comboMetricAxis } from '@/utils/biChartPresentation'
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

/** Display-only axis shortening; raw categories remain available to tooltips and interactions. */
export function chartAxisLabel(value:unknown,maxLength=14):string {
  const text=String(value??'空值')
  const midnight=text.match(/^(\d{4}-\d{2}-\d{2})[T ]00:00:00(?:\.0+)?(?:Z|[+-]\d{2}:?\d{2})?$/)
  if(midnight)return midnight[1]
  const instant=text.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2}(?:\.\d+)?)(?:Z|[+-]\d{2}:?\d{2})?$/)
  if(instant)return instant[1]+'\n'+instant[2]
  const chars=Array.from(text);return chars.length>maxLength?chars.slice(0,maxLength-1).join('')+'…':text
}

export function analysisRows(definition:ChartDefinition,result:ChartResult):ChartResult {
  const a=definition.advanced?.analysis,dim=definition.query.dimensions[0]
  if(definition.type!=='funnel' || !a?.funnelStages?.length || !dim)return result
  const field=dim.alias||dim.field,key=metricResultKey(definition.query.metrics[0]),rows:Record<string,unknown>[]=[],used=new Set<Record<string,unknown>>()
  for(const stage of a.funnelStages){const matches=result.rows.filter(r=>String(r[field])===stage);if(matches.length>1)throw new Error('漏斗阶段重复，请使用聚合指标');if(!matches.length){if(!a.funnelFillZero)throw new Error(`阶段“${stage}”缺失，请确认阶段名称或明确开启补零`);rows.push({[field]:stage,[key]:0})}else{rows.push(matches[0]);used.add(matches[0])}}
  rows.push(...result.rows.filter(r=>!used.has(r)))
  return {...result,rows,rowCount:rows.length}
}
export function funnelAnalysis(rows:Record<string,unknown>[],key:string,mode:'adjacent'|'first',index:number){
  const value=readNumber(rows[index]?.[key]),base=readNumber(rows[mode==='first'?0:Math.max(0,index-1)]?.[key])
  const rate=value==null || base==null || base===0?null:value/base
  const previous=readNumber(rows[Math.max(0,index-1)]?.[key])
  return {rate,loss:rate==null?null:1-rate,increase:index>0 && value!=null && previous!=null && value>previous}
}

/** Pivot only for drawing. Query/export rows retain the two original dimensions. */
export function seriesDrawing(definition:ChartDefinition,result:ChartResult,labels:Record<string,string>={}) {
  const q=definition.query
  if(q.dimensions.length!==2 || !['bar','hbar','line','area','stack','percent','combo'].includes(definition.type))return null
  const [category,series]=q.dimensions.map(d=>d.alias||d.field)
  const identity=(v:unknown)=>JSON.stringify([v??null])
  const categories=[...new Map(result.rows.map(r=>[identity(r[category]),r[category]])).values()]
  const groups=[...new Map(result.rows.map(r=>[identity(r[series]),r[series]])).values()]
  if(groups.length*q.metrics.length>100)throw new Error('系列超过 100 个，请增加筛选或减少指标')
  const seen=new Set<string>(),indices=new Map<string,number>()
  result.rows.forEach((r,i)=>{const k=JSON.stringify([r[category]??null,r[series]??null]);if(seen.has(k))throw new Error('分类与系列组合重复，请为指标选择聚合方式');seen.add(k);indices.set(k,i)})
  const mapped=groups.flatMap((group,gi)=>q.metrics.map((metric,mi)=>({group,metric,source:metricResultKey(metric),key:`__series_${gi}_${mi}`})))
  const rows=categories.map(value=>{const row:Record<string,unknown>={[category]:value};for(const m of mapped){const i=indices.get(JSON.stringify([value??null,m.group??null]));row[m.key]=i==null?null:result.rows[i][m.source]}return row})
  const drawingLabels={...labels};for(const m of mapped)drawingLabels[m.key]=String(m.group??'空值')+(q.metrics.length>1?' · '+(labels[m.source]||m.source):'')
  return {definition:{...definition,query:{...q,dimensions:q.dimensions.slice(0,1),metrics:mapped.map(m=>({...m.metric,alias:m.key}))},style:{...definition.style,metrics:mapped.flatMap(m=>{const setting=definition.style?.metrics?.find(x=>x.key===m.source);return setting?[{...setting,key:m.key}]:[]}),referenceLines:mapped.flatMap(m=>(definition.style?.referenceLines||[]).filter(r=>r.key===m.source).map(r=>({...r,key:m.key})))}},result:{...result,rows,rowCount:rows.length,columns:[category,...mapped.map(m=>m.key)]},labels:drawingLabels,
    rowIndex:(seriesIndex:number,dataIndex:number)=>{const m=mapped[seriesIndex];return m?indices.get(JSON.stringify([categories[dataIndex]??null,m.group??null])):undefined}}
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
  const drawing=seriesDrawing(params.definition,params.result,params.labels)
  if(drawing)return buildChartPreviewOption({...params,...drawing})
  const { type, definition, labels, appearance, numberFormat } = params
  const result=analysisRows(definition,params.result)
  const analysis=definition.advanced?.analysis
  const style = definition.style
  const metricStyles=resolvedMetricStyles(style,params.metricStyles)
  const formatFor=(key:string)=>params.formatFor(key) || style?.metrics?.find(m=>m.key===key)?.format
  const advanced = definition.advanced
  const mergedAppearance = chartAppearance(style, appearance)
  const metrics = definition.query.metrics
  const dimField = definition.query.dimensions[0]?.alias || definition.query.dimensions[0]?.field
  const filledPeriods=new Set(result.timeFill?.periods||[])
  const filledRow=(row:Record<string,unknown>)=>!!dimField && filledPeriods.has(String(row[dimField]))
  const categories = dimField ? result.rows.map(r => String(r[dimField] ?? '空值')) : result.rows.map((_, i) => String(i + 1))
  const visualTheme=resolveBiTheme(mergedAppearance)
  const palette=paletteFromStyle(style,mergedAppearance)
  const textColor = visualTheme.text
  const comparisonBase=(key:string)=>!!result.timeComparison?.fields.some(f=>f.baseline===key)
  const nullMode = advanced?.nullMode || 'keep'
  const smoothDefault = advanced?.smoothLine !== false
  const formatValue=(key:string,value:unknown)=>String(formatMetric(value,appearance,formatFor(key)))
  function axisFor(keys:string[]){
    const formats=keys.map(formatFor)
    const common=formats.length && formats.every(f=>JSON.stringify(f)===JSON.stringify(formats[0]))?formats[0]:numberFormat
    return (value:number)=>String(formatMetric(value,appearance,common))
  }
  const axisFormatter=axisFor(metrics.map(metricResultKey))

  const tooltipFormatter = (input: unknown) => {
    const items = Array.isArray(input) ? input : [input]
    return items
      .map((item: { seriesIndex?: number; dataIndex?: number; name?: string; value?: unknown }) => {
        const metric = metrics[item.seriesIndex ?? 0]
        const key = metric ? metricResultKey(metric) : ''
        const raw = isPieLike(type) ? item.value : item.dataIndex != null ? result.rows[item.dataIndex]?.[key] : item.value
        const label = key ? labels[key] || key : ''
        const synthetic=item.dataIndex!=null && result.rows[item.dataIndex] && filledRow(result.rows[item.dataIndex]) && !comparisonBase(key)
        return `${String(item.name ?? '')} · ${label}: ${String(formatMetric(raw, appearance, formatFor(key)))}${synthetic?'（缺失周期补'+(result.timeFill?.mode==='zero'?'零':'空值')+'，非实际记录）':''}`
      })
      .join('\n')
  }

  const legendTop = style?.legendPosition === 'bottom' ? undefined : style?.showTitle?36:8
  const legendBottom = style?.legendPosition === 'bottom' ? 8 : undefined
  const chartTop=style?.showTitle?(style.showLegend!==false && style.legendPosition!=='bottom'?76:44):(style?.showLegend!==false && style?.legendPosition!=='bottom'?48:20)
  const chartBottom=style?.showLegend!==false && style?.legendPosition==='bottom'?52:28
  const base: EChartsOption = {
    color: palette,
    textStyle: { color: textColor,fontFamily:visualTheme.fontFamily,fontSize:BI_TYPE.chart },
    title: style?.showTitle ? { text: style.title || definition.name, left:16,top:4, textStyle: { color: textColor, fontFamily:visualTheme.fontFamily,fontSize:BI_TYPE.title,fontWeight:600 } } : undefined,
    tooltip: {
      trigger: isPieLike(type) || type === 'gauge' || type === 'funnel' ? 'item' : 'axis',
      renderMode: 'richText',
      confine:true,backgroundColor:visualTheme.surface,borderColor:visualTheme.border,borderWidth:1,
      shadowBlur:12,shadowColor:'rgba(16,24,40,0.12)',
      axisPointer:{type:'line',lineStyle:{color:visualTheme.muted,type:'dashed'}},
      textStyle:{color:textColor,fontFamily:visualTheme.fontFamily,fontSize:BI_TYPE.chart},
      padding:12,
      formatter: tooltipFormatter
    },
    legend: style?.showLegend === false
      ? { show: false }
      : { type: 'scroll', top: legendTop, bottom: legendBottom, left:16,right:16,itemWidth:16,itemHeight:8,itemGap:16,textStyle: { color: visualTheme.muted,fontFamily:visualTheme.fontFamily,fontSize:BI_TYPE.chart } }
  }

  function normalizeValue(raw: unknown, key: string): number | null {
    if(raw==null && comparisonBase(key))return null
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
    const min = style?.gaugeMin ?? 0
    const max = style?.gaugeMax ?? Math.max(value * 1.2, value + 1, min + 1, 100)
    if(!Number.isFinite(min) || !Number.isFinite(max) || max<=min)throw new Error('仪表范围无效：最大值必须大于最小值')
    const progress=style?.gaugeStyle==='progress'
    return {
      ...base,
      series: [{
        type: 'gauge',
        min, max,
        startAngle: progress?210:225, endAngle: progress?-30:-45,
        progress: {show:progress,width:16,roundCap:true},
        pointer: {show:!progress},
        axisLine: {lineStyle:{width:progress?16:10}},
        axisTick: {show:!progress}, splitLine:{show:!progress},
        axisLabel: {show:!progress,formatter:(v:number)=>formatValue(key,v)},
        detail: { formatter: (v: number) => formatValue(key,v), color:metricColor(raw,key,metricStyles) || style?.metrics?.find(m=>m.key===key)?.color || textColor },
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
      if(analysis?.scatterLogX && x<=0 || analysis?.scatterLogY && y<=0)throw new Error('对数轴仅支持正数，请筛选非正数或关闭对数轴')
      const size=metrics[2]?readNumber(row[metricResultKey(metrics[2])]):undefined
      if(metrics[2] && (size==null || size<0))throw new Error('气泡大小指标必须为有效的非负数')
      return metrics[2]?[x, y, categories[i],size]:[x, y, categories[i]]
    })
    const sizes=points.map(p=>Number(p[3]??0)),maxSize=Math.max(0,...sizes)
    const xSplit=analysis?.scatterXSplit??points.reduce((sum,p)=>sum+Number(p[0]),0)/Math.max(1,points.length),ySplit=analysis?.scatterYSplit??points.reduce((sum,p)=>sum+Number(p[1]),0)/Math.max(1,points.length)
    if(analysis?.scatterQuadrants && (analysis.scatterLogX && xSplit<=0 || analysis.scatterLogY && ySplit<=0))throw new Error('对数轴象限分界须为正数')
    return {
      ...base,
      dataZoom:analysis?.scatterZoom?[{type:'inside',xAxisIndex:0,filterMode:'none'},{type:'inside',yAxisIndex:0,filterMode:'none'},{type:'slider',xAxisIndex:0,bottom:4,filterMode:'none'}]:undefined,
      tooltip:{trigger:'item',renderMode:'richText',formatter:(input:unknown)=>{const item=input as {dataIndex?:number};const row=result.rows[item.dataIndex??0];return `${categories[item.dataIndex??0]}\n${labels[xKey] || xKey}: ${formatValue(xKey,row?.[xKey])}\n${labels[yKey] || yKey}: ${formatValue(yKey,row?.[yKey])}`+(metrics[2]?`\n气泡 ${labels[metricResultKey(metrics[2])]||metrics[2].field}: ${formatValue(metricResultKey(metrics[2]),row?.[metricResultKey(metrics[2])])}`:'')}},
      grid: { left: 45, right: 28, bottom: 65, top: 55, containLabel: true },
      xAxis: { type: analysis?.scatterLogX?'log':'value', name: labels[xKey] || xKey,nameLocation:'middle',nameGap:32,nameTruncate:{maxWidth:160,ellipsis:'…'},nameTextStyle:{color:textColor,fontSize:11}, axisLabel: { color: textColor, formatter:axisFor([xKey]) } },
      yAxis: { type: analysis?.scatterLogY?'log':'value', name: labels[yKey] || yKey,nameLocation:'middle',nameGap:50,nameTruncate:{maxWidth:150,ellipsis:'…'},nameTextStyle:{color:textColor,fontSize:11}, axisLabel: { color: textColor, formatter:axisFor([yKey]) }, splitLine: { lineStyle: { color: visualTheme.border } } },
      series: [{ type: 'scatter', name: `${labels[xKey] || xKey} × ${labels[yKey] || yKey}`, data: points,
        symbol:style?.scatterSymbol || 'circle',symbolSize:metrics[2]?(value:unknown)=>{const size=Number((value as unknown[])[3]??0);return maxSize===0?6:6+Math.sqrt(size/maxSize)*34}:style?.scatterSize ?? 10,
        markLine:analysis?.scatterQuadrants?{symbol:['none','none'],silent:true,data:[{xAxis:xSplit,name:'X 分界',label:{formatter:'X 分界'}},{yAxis:ySplit,name:'Y 分界',label:{formatter:'Y 分界'}}]}:undefined,
        itemStyle:{opacity:style?.scatterOpacity ?? 0.8,borderWidth:style?.scatterBorderWidth ?? 0,borderColor:style?.scatterBorderColor || '#FFFFFF'},
        label:{show:style?.showLabels ?? false,position:'top',color:textColor,formatter:(input:unknown)=>{const p=input as {dataIndex?:number};const i=p.dataIndex??0;return style?.scatterLabelContent==='coordinates'?`${formatValue(xKey,result.rows[i]?.[xKey])}, ${formatValue(yKey,result.rows[i]?.[yKey])}`:categories[i]}},
        labelLayout:{hideOverlap:true}}]
    }
  }

  if (type === 'radar') {
    const keys = metrics.map(metricResultKey)
    const indicators = keys.map(key => {
      if(analysis?.radarNormalize)return {name:labels[key]||key,min:0,max:100}
      let max = 0,min=0
      for (const row of result.rows) {
        const v = readNumber(row[key]) ?? 0
        if (v > max) max = v
        if (v < min) min = v
      }
      return { name: labels[key] || key, min:min<0?min*1.1:0,max: max > 0 ? max * 1.1 : min<0?0:1 }
    })
    const seriesData = result.rows.map((row, i) => ({
      name: categories[i],
      value: keys.map(key => {const raw=readNumber(row[key]);if(!analysis?.radarNormalize)return raw??0;const axis=analysis.radarAxes?.find(a=>a.key===key);if(!axis || !Number.isFinite(axis.min) || !Number.isFinite(axis.max) || axis.max<=axis.min)throw new Error('请为雷达指标配置有效的标准化范围');if(raw==null || raw<axis.min || raw>axis.max)throw new Error('雷达指标缺失或超出标准化范围，请调整配置');return (raw-axis.min)/(axis.max-axis.min)*100})
    }))
    if(analysis?.radarNormalize && keys.every(key=>analysis.radarAxes?.find(a=>a.key===key)?.target!=null))seriesData.push({name:'目标',value:keys.map(key=>{const a=analysis.radarAxes!.find(a=>a.key===key)!;return (a.target!-a.min)/(a.max-a.min)*100})})
    return {
      ...base,
      tooltip:{trigger:'item',renderMode:'richText',formatter:(input:unknown)=>{const item=input as {dataIndex?:number};if(item.dataIndex===result.rows.length)return '目标轮廓（标准化得分）';const row=result.rows[item.dataIndex??0];return keys.map(key=>`${labels[key] || key}: ${formatValue(key,row?.[key])}`).join('\n')}},
      radar: { indicator: indicators,shape:style?.radarShape || 'polygon',splitNumber:style?.radarSplitNumber ?? 5,radius:'60%',center:['50%','56%'],axisNameGap:8,
        axisName:{show:style?.radarShowAxisName!==false,color:textColor,fontSize:11,width:90,overflow:'truncate'},splitArea:{show:style?.radarShowSplitArea ?? true},
        splitLine:{lineStyle:{color:visualTheme.border}} },
      series: [{ type: 'radar', data: seriesData,lineStyle:{width:style?.lineWidth ?? 2},
        symbol:style?.showPoints===false?'none':'circle',symbolSize:style?.symbolSize ?? 8,
        areaStyle:style?.areaOpacity!=null?{opacity:style.areaOpacity}:undefined }]
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
    const outer=style?.pieOuterRadius??(type==='ring'?72:70)
    const inner=type==='ring'?(style?.pieInnerRadius??52):0
    if(inner>=outer)throw new Error('内半径必须小于外半径')
    const radius = [`${inner}%`,`${outer}%`]
    if (type === 'funnel') {
      const minSize=style?.funnelMinSize ?? 0,maxSize=style?.funnelMaxSize ?? 100
      if(minSize>=maxSize)throw new Error('漏斗最小宽度必须小于最大宽度')
      const position=style?.funnelLabelPosition || 'right'
      const rateText=(index:number)=>{if(!analysis?.funnelRate)return '';const stats=funnelAnalysis(result.rows,key,analysis.funnelRate,index);return `\n转化率 ${stats.rate==null?'—':(stats.rate*100).toFixed(1)+'%'} · 流失率 ${stats.loss==null?'—':(stats.loss*100).toFixed(1)+'%'}${stats.increase?' · 阶段异常增长':''}`}
      return { ...base,tooltip:{...base.tooltip as object,formatter:(input:unknown)=>{const item=input as {name?:string;value?:unknown;dataIndex?:number};return `${item.name||''}: ${formatValue(key,item.value)}${rateText(item.dataIndex??0)}`}}, series: [{ type: 'funnel' as const, data, sort: 'none' as const,
        left:position==='left'?'28%':'10%',right:position==='right'?'28%':'10%',top:50,bottom:35,
        funnelAlign:style?.funnelAlign || 'center',gap:style?.funnelGap ?? 0,minSize:`${minSize}%`,maxSize:`${maxSize}%`,
        label:{show:style?.showLabels!==false,position,color:position==='inside'?undefined:textColor,width:110,overflow:'truncate',formatter:(input:unknown)=>{const p=input as {name?:string;value?:unknown;dataIndex?:number};const value=formatValue(key,p.value)+rateText(p.dataIndex??0);return analysis?.funnelRate?`${p.name||''}\n${value}`:style?.funnelLabelContent==='value'?value:style?.funnelLabelContent==='nameValue'?`${p.name || ''}\n${value}`:p.name || ''}},
        labelLine:{show:position!=='inside'},labelLayout:{hideOverlap:true},itemStyle:{borderWidth:1,borderColor:mergedAppearance?.cardBackground || '#fff'} }] }
    }
    const total=data.reduce((sum,p)=>sum+p.value,0)
    return { ...base,
      graphic: type==='ring' && style?.ringShowTotal ? [{type:'text',left:'center',top:'middle',style:{text:`合计\n${formatValue(key,total)}`,align:'center' as const,fill:textColor,fontSize:18,lineHeight:28}}] : undefined,
      series: [{ type: 'pie' as const, radius, data, startAngle:style?.startAngle??90,
        label:{show:style?.showLabels!==false,position:style?.pieLabelPosition??'outside',formatter:style?.pieLabelPosition==='inside'?'{d}%':'{b}'},
        labelLine:{show:style?.pieLabelPosition!=='inside',length:12,length2:10},labelLayout:{hideOverlap:true},emphasis:{focus:'self'},itemStyle:{borderWidth:2,borderColor:visualTheme.surface}}] }
  }

  const keys = metrics.map(metricResultKey)
  const valueMatrix = result.rows.map(row =>
    keys.map(key => {
      const v = filledRow(row) && result.timeFill?.mode==='null' && !comparisonBase(key)?null:normalizeValue(row[key], key)
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
      const setting=style?.metrics?.find(m=>m.key===key)
      const seriesType=setting?.seriesType || (mi===0?'bar':'line')
      const yAxisIndex=comboMetricAxis(style,key,mi)==='left'?0:1
      return seriesType==='bar'
        ? { name, type: 'bar' as const, data, yAxisIndex, barMaxWidth:style?.barMaxWidth??44, itemStyle:{borderRadius:[style?.barRadius??5,style?.barRadius??5,0,0]},label:{show:style?.showLabels??false,position:'top' as const} }
        : { name, type: 'line' as const, connectNulls:false, data, yAxisIndex, smooth: smoothDefault, showSymbol:style?.showPoints??categories.length<=20, symbolSize:style?.symbolSize??6,lineStyle:{width:style?.lineWidth??3},label:{show:style?.showLabels??false,position:'top' as const} }
    }
    if (type === 'line' || type === 'area') {
      return { name, type: 'line' as const, connectNulls:false, data, smooth: smoothDefault, showSymbol:style?.showPoints??categories.length<=20, symbolSize:style?.symbolSize??6, lineStyle:{width:style?.lineWidth??3}, label:{show:style?.showLabels??false,position:'top' as const}, areaStyle: type === 'area' ? {opacity:style?.areaOpacity??0.18} : undefined }
    }
    const stack = type === 'stack' || type === 'percent' ? 'total' : undefined
    let seriesData = data
    if (type === 'percent') {
      seriesData = result.rows.map((row, rowIdx) => {
        if(filledRow(row) && result.timeFill?.mode==='null')return null
        const values = keys.map((_, k) => readNumber(result.rows[rowIdx][keys[k]]) ?? 0)
        if(values.some(v=>v<0))throw new Error('百分比堆叠仅支持非负指标')
        const sum = values.reduce((a, b) => a + b, 0) || 1
        const v = values[mi] / sum * 100
        return Math.round(v * 100) / 100
      })
    }
    const radius=style?.barRadius??5
    return { name, type: 'bar' as const, stack, barMaxWidth:style?.barMaxWidth??44, data: seriesData,
      label:{show:style?.showLabels??false,position:stack?'inside' as const:type==='hbar'?'right' as const:'top' as const,formatter:type==='percent'?'{c}%':undefined},
      itemStyle: stack ? undefined : {borderRadius:type==='hbar'?[0,radius,radius,0]:[radius,radius,0,0]} }
  }).map((series,index)=>{
    const key=keys[index],setting=style?.metrics?.find(m=>m.key===key)
    const lines=['bar','hbar','line','area','stack','combo'].includes(type)?(style?.referenceLines || []).filter(r=>r.key===key):[]
    const values=result.rows.filter(r=>!filledRow(r)).map(r=>normalizeValue(r[key],key)).filter((v):v is number=>v!=null)
    const markData=lines.flatMap((line,lineIndex)=>{
      if(line.kind!=='fixed' && !values.length)return []
      const value=line.kind==='fixed'?line.value:line.kind==='min'?Math.min(...values):line.kind==='max'?Math.max(...values):values.reduce((a,b)=>a+b,0)/values.length
      if(value==null || !Number.isFinite(value))return []
      return [{name:line.name || '参考线',...(type==='hbar'?{xAxis:value}:{yAxis:value}),lineStyle:{color:line.color,type:line.lineType},label:{position:lineIndex%2?'insideStartTop' as const:'insideEndBottom' as const,fontSize:11,backgroundColor:visualTheme.surface,padding:3,formatter:()=>`${line.name || '参考线'}: ${formatValue(key,value)}`}}]
    })
    const original=result.timeComparison?.fields.find(f=>f.baseline===key)?.key
    const originalKeys=keys.filter(k=>!comparisonBase(k))
    const pairColor=result.timeComparison?style?.metrics?.find(m=>m.key===(original||key))?.color || palette[Math.max(0,originalKeys.indexOf(original||key))%palette.length]:undefined
    return {...series,emphasis:{focus:'series' as const},labelLayout:{hideOverlap:true},itemStyle:{...series.itemStyle,...(pairColor?{color:pairColor}:{}),...(setting?.color?{color:setting.color}:{})},
      ...('lineStyle' in series?{lineStyle:{...series.lineStyle,...(pairColor?{color:pairColor,type:original?'dashed' as const:'solid' as const}:{}),...(setting?.color?{color:setting.color}:{})}}:{}),
      ...(original && 'areaStyle' in series?{areaStyle:{opacity:0}}:{}),
      label:{...series.label,formatter:type==='percent'?'{c}%':(input:unknown)=>{const p=input as {dataIndex?:number};return formatValue(key,result.rows[p.dataIndex??0]?.[key])}},
      markLine:markData.length?{symbol:['none','none'],silent:true,data:markData}:undefined}
  })

  const valueYAxis = { type: 'value' as const,splitNumber:5,axisLine:{show:false},axisTick:{show:false},axisLabel: { color: visualTheme.muted,margin:12, formatter: axisFormatter }, splitLine: { lineStyle: { color: visualTheme.border,type:'dashed' as const,opacity:0.65 } } }
  const percentYAxis = { ...valueYAxis,max:100,axisLabel:{...valueYAxis.axisLabel,formatter:(v:number)=>`${v}%`} }
  const categoryYAxis = { type: 'category' as const, data: categories, axisTick:{show:false},axisLabel: { color: visualTheme.muted,margin:12,hideOverlap:true,formatter:(value:string)=>chartAxisLabel(value,20), rotate: style?.axisLabelRotate ?? 0 }, axisLine: { lineStyle: { color: visualTheme.border } } }
  const categoryXAxis = {
    type: 'category' as const,
    boundaryGap:!['line','area'].includes(type),
    data: categories,
      axisTick:{show:false},axisLabel: { rotate: style?.axisLabelRotate ?? 0, color: visualTheme.muted,margin:12,hideOverlap:true,showMinLabel:true,showMaxLabel:true,alignMinLabel:'left' as const,alignMaxLabel:'right' as const,formatter:(value:string)=>chartAxisLabel(value) },
    axisLine: { lineStyle: { color: visualTheme.border } }
  }

  if (type === 'hbar') {
    return {
      ...base,
      grid: { left: 30, right: 20, bottom:chartBottom, top:chartTop, containLabel: true },
      xAxis: valueYAxis,
      yAxis: categoryYAxis,
      series: cartesianSeries
    }
  }

  const yAxes: EChartsOption['yAxis'] = type === 'combo'
    ? [
        {...valueYAxis,position:'left',show:metrics.some((m,i)=>comboMetricAxis(style,metricResultKey(m),i)==='left'),min:style?.comboAxes?.left?.min,max:style?.comboAxes?.left?.max,axisLabel:{color:textColor,formatter:axisFor(metrics.filter((m,i)=>comboMetricAxis(style,metricResultKey(m),i)==='left').map(metricResultKey))}},
        { type: 'value' as const,position:'right',show:metrics.some((m,i)=>comboMetricAxis(style,metricResultKey(m),i)==='right'), axisLabel: { color: textColor, formatter:axisFor(metrics.filter((m,i)=>comboMetricAxis(style,metricResultKey(m),i)==='right').map(metricResultKey)) }, min:style?.comboAxes?.right?.min,max:style?.comboAxes?.right?.max,splitLine: { show: false } }
      ]
    : type === 'percent' ? percentYAxis : valueYAxis

  return {
    ...base,
    grid: { left: 30, right: type === 'combo' ? 48 : 20, bottom:chartBottom+(style?.axisLabelRotate?24:0), top:chartTop, containLabel: true },
    xAxis: categoryXAxis,
    yAxis: yAxes,
    series: cartesianSeries
  }
}
