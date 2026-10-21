import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import { test } from 'node:test'

let bundle=''
for(const file of ['biTheme.ts','biPresentation.ts','biChartTypes.ts','biChartPresentation.ts','biEchartsPreview.ts','biChartStylePresets.ts']) {
  const source=(await fs.readFile(new URL('../src/utils/'+file,import.meta.url),'utf8')).split('\n').filter(line=>!line.startsWith('import ')).join('\n')
  bundle+=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText+'\n'
}
const {buildChartPreviewOption,chartStylePresets,applyChartStylePreset}=await import('data:text/javascript;base64,'+Buffer.from(bundle).toString('base64'))
function option(type,style={},value=20) {
  return buildChartPreviewOption({type,definition:{schemaVersion:1,name:'销售额',type,style,query:{datasetId:1,dimensions:[{field:'category'}],metrics:[{field:'value',agg:'SUM',alias:'value'}]}},result:{columns:['category','value'],rows:[{category:'A',value}],rowCount:1,costMs:1},labels:{value:'销售额'},formatFor:()=>undefined})
}
test('bar, line and area controls modify their actual rendering options',()=>{
  const bar=option('hbar',{barMaxWidth:28,barRadius:6,showLabels:true}).series[0]
  assert.equal(bar.barMaxWidth,28);assert.deepEqual(bar.itemStyle.borderRadius,[0,6,6,0]);assert.equal(bar.label.position,'right')
  const line=option('line',{lineWidth:5,symbolSize:10,showPoints:false}).series[0]
  assert.equal(line.lineStyle.width,5);assert.equal(line.symbolSize,10);assert.equal(line.showSymbol,false)
  assert.equal(option('area',{areaOpacity:0.35}).series[0].areaStyle.opacity,0.35)
})
test('pie stays solid and ring can display total with independently configured radii',()=>{
  assert.deepEqual(option('pie',{pieInnerRadius:50,pieOuterRadius:80}).series[0].radius,['0%','80%'])
  const ring=option('ring',{pieInnerRadius:42,pieOuterRadius:74,ringShowTotal:true,pieLabelPosition:'inside',startAngle:180})
  assert.deepEqual(ring.series[0].radius,['42%','74%']);assert.equal(ring.series[0].startAngle,180)
  assert.equal(ring.series[0].label.formatter,'{d}%');assert.match(ring.graphic[0].style.text,/合计\n20/)
  assert.throws(()=>option('ring',{pieInnerRadius:80}),/内半径/)
})
test('fixed gauge ranges never silently rescale the target and keep the real value',()=>{
  const gauge=option('gauge',{gaugeStyle:'progress',gaugeMin:0,gaugeMax:100},150).series[0]
  assert.equal(gauge.max,100);assert.equal(gauge.data[0].value,150)
  assert.equal(gauge.pointer.show,false);assert.equal(gauge.progress.show,true)
  assert.throws(()=>option('gauge',{gaugeMin:100,gaugeMax:10}),/范围无效/)
})
test('presets preserve titles and legends while replacing incompatible shape settings',()=>{
  const style={title:'用户标题',showTitle:true,showLegend:false,barRadius:6,gaugeMax:999}
  applyChartStylePreset(style,chartStylePresets('ring')[0])
  assert.equal(style.title,'用户标题');assert.equal(style.showLegend,false)
  assert.equal(style.gaugeMax,undefined);assert.equal(style.barRadius,undefined)
  assert.equal(style.ringShowTotal,true)
  const restored=JSON.parse(JSON.stringify(style))
  assert.deepEqual(option('ring',restored).series[0].radius,['52%','70%'])
  applyChartStylePreset(style)
  assert.equal(style.pieInnerRadius,undefined);assert.equal(style.title,'用户标题')
})

test('funnel layout and formatted labels preserve business stage order rather than sorting by value',()=>{
  const definition={schemaVersion:1,name:'流程',type:'funnel',style:{funnelAlign:'left',funnelGap:8,funnelMinSize:10,funnelMaxSize:90,funnelLabelPosition:'inside',funnelLabelContent:'nameValue'},query:{datasetId:1,dimensions:[{field:'stage'}],metrics:[{field:'value',agg:'SUM',alias:'value'}]}}
  const rows=[{stage:'申请',value:20},{stage:'审核',value:100},{stage:'完成',value:50}]
  const o=buildChartPreviewOption({type:'funnel',definition,result:{columns:['stage','value'],rows,rowCount:3,costMs:1},labels:{},formatFor:()=>({mode:'number',scale:'none',decimals:0,grouping:true,fixed:true,prefix:'',suffix:'人',nullText:'—'})})
  const s=o.series[0];assert.equal(s.sort,'none');assert.deepEqual(s.data.map(d=>d.name),['申请','审核','完成'])
  assert.equal(s.funnelAlign,'left');assert.equal(s.gap,8);assert.equal(s.minSize,'10%');assert.equal(s.maxSize,'90%')
  assert.equal(s.label.formatter({name:'申请',value:20}),'申请\n20人');assert.equal(s.labelLine.show,false)
  assert.throws(()=>option('funnel',{funnelMinSize:70,funnelMaxSize:50}),/最小宽度/)
})

test('radar styling changes grids and fills while keeping negative measurements inside indicator ranges',()=>{
  const definition={schemaVersion:1,name:'雷达',type:'radar',style:{radarShape:'circle',radarSplitNumber:4,radarShowSplitArea:false,radarShowAxisName:false,lineWidth:4,areaOpacity:0.25,showPoints:false},query:{datasetId:1,dimensions:[{field:'name'}],metrics:['a','b','c'].map(field=>({field,agg:'SUM',alias:field}))}}
  const rows=[{name:'甲',a:-10,b:0,c:5},{name:'乙',a:20,b:0,c:-2}]
  const o=buildChartPreviewOption({type:'radar',definition,result:{columns:[],rows,rowCount:2,costMs:1},labels:{},formatFor:()=>undefined})
  assert.equal(o.radar.shape,'circle');assert.equal(o.radar.splitNumber,4);assert.equal(o.radar.splitArea.show,false);assert.equal(o.radar.axisName.show,false)
  assert.ok(o.radar.indicator[0].min<-10);assert.ok(o.radar.indicator[0].max>20);assert.equal(o.radar.indicator[1].max,1)
  assert.equal(o.series[0].symbol,'none');assert.equal(o.series[0].areaStyle.opacity,0.25);assert.equal(o.series[0].lineStyle.width,4)
  assert.deepEqual(o.series[0].data[0].value,[-10,0,5])
})

test('scatter point appearance and coordinate labels do not transform numeric point values',()=>{
  const definition={schemaVersion:1,name:'分布',type:'scatter',style:{scatterSymbol:'diamond',scatterSize:18,scatterOpacity:0.45,scatterBorderWidth:2,scatterBorderColor:'#3366FF',showLabels:true,scatterLabelContent:'coordinates'},query:{datasetId:1,dimensions:[{field:'name'}],metrics:[{field:'x',agg:'SUM',alias:'x'},{field:'y',agg:'SUM',alias:'y'}]}}
  const rows=[{name:'客户甲',x:-10,y:20}]
  const o=buildChartPreviewOption({type:'scatter',definition,result:{columns:[],rows,rowCount:1,costMs:1},labels:{},formatFor:()=>undefined})
  const s=o.series[0];assert.equal(s.symbol,'diamond');assert.equal(s.symbolSize,18);assert.equal(s.itemStyle.opacity,0.45)
  assert.equal(s.itemStyle.borderWidth,2);assert.equal(s.itemStyle.borderColor,'#3366FF');assert.equal(s.label.formatter({dataIndex:0}),'-10, 20')
  assert.deepEqual(s.data[0],[-10,20,'客户甲']);assert.match(o.tooltip.formatter({dataIndex:0}),/客户甲/)
})

test('specialized presets reset other shapes but preserve metric formats, references and chart chrome',()=>{
  for(const type of ['funnel','radar','scatter'])assert.equal(chartStylePresets(type).length,2)
  const style={title:'保留标题',metrics:[{key:'value',format:{mode:'percent'}}],referenceLines:[{key:'value'}],funnelGap:20,radarShape:'circle',scatterSize:40}
  applyChartStylePreset(style,chartStylePresets('scatter')[1])
  assert.equal(style.funnelGap,undefined);assert.equal(style.radarShape,undefined);assert.equal(style.scatterSize,6)
  assert.equal(style.title,'保留标题');assert.equal(style.metrics[0].format.mode,'percent');assert.equal(style.referenceLines.length,1)
})
