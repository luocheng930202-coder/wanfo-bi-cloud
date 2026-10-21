import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import { test } from 'node:test'

let bundle=''
for(const file of ['biPresentation.ts','biChartTypes.ts','biChartPresentation.ts','biEchartsPreview.ts','biChartStylePresets.ts']) {
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
