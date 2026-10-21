import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
let bundle=''
for(const file of ['biTheme.ts','biTablePresentation.ts','biPresentation.ts','biChartTypes.ts','biChartPresentation.ts','biEchartsPreview.ts','biPivotPreview.ts']){
  const source=(await fs.readFile(new URL('../src/utils/'+file,import.meta.url),'utf8')).split('\n').filter(line=>!line.startsWith('import ')).join('\n')
  bundle+=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText+'\n'
}
const {formatMetric,resolveChartMetricFormat,buildChartPreviewOption,buildPivotDataConfig,validateChartMetricPresentation,nextChartMetricAlias,retainChartMetricBindings,resolveBiTheme,BI_FONT_FAMILY,pivotTheme,presentationVars}=await import('data:text/javascript;base64,'+Buffer.from(bundle).toString('base64'))
const money={mode:'number',scale:'wan',decimals:2,grouping:true,fixed:true,prefix:'',suffix:'元',nullText:'无数据'}
const ratio={...money,mode:'percent',scale:'none',suffix:''}
const count={...money,scale:'none',decimals:0,suffix:'件'}

test('synthetic null gaps stay disconnected despite global zero mode and synthetic zeros do not alter reference averages',()=>{
  const fixture=chart('line',{referenceLines:[{key:'sales',kind:'average',name:'平均',color:'#123456',lineType:'solid'}]})
  fixture.definition.advanced={nullMode:'zero'}
  fixture.result.rows=[{category:'A',sales:10},{category:'B',sales:null},{category:'C',sales:20}]
  fixture.result.timeFill={mode:'null',status:'complete',sourceRows:2,addedRows:1,periods:['B']}
  let option=fixture.option();assert.deepEqual(option.series[0].data,[10,null,20]);assert.equal(option.series[0].connectNulls,false)
  assert.match(option.tooltip.formatter([{seriesIndex:0,dataIndex:1,name:'B',value:null}]),/非实际记录/)
  fixture.result.rows[1].sales=0;fixture.result.timeFill.mode='zero';option=fixture.option()
  assert.deepEqual(option.series[0].data,[10,0,20]);assert.equal(option.series[0].markLine.data[0].yAxis,15)
  fixture.definition.type='percent';option=buildChartPreviewOption({type:'percent',definition:fixture.definition,result:{...fixture.result,timeFill:{...fixture.result.timeFill,mode:'null'}},labels:{},formatFor:()=>undefined})
  assert.equal(option.series[0].data[1],null)
})
function chart(type='combo',style={}){
  const definition={schemaVersion:1,name:'零售',type,style,query:{datasetId:1,dimensions:[{field:'category'}],metrics:[{field:'amount',agg:'SUM',alias:'sales'},{field:'rate',agg:'AVG',alias:'rate'}]}}
  const result={columns:['category','sales','rate'],rows:[{category:'A',sales:10000,rate:0.25},{category:'B',sales:30000,rate:null}],rowCount:2,costMs:1}
  return {definition,result,option:()=>buildChartPreviewOption({type,definition,result,labels:{sales:'销售额',rate:'利润率'},formatFor:()=>undefined})}
}
test('metric display keeps exact decimal strings, scales large amounts and formats nulls without changing values',()=>{
  assert.equal(formatMetric('123456789012345678.125',undefined,{...money,scale:'none'}),'123,456,789,012,345,678.13元')
  assert.equal(formatMetric(-10000,undefined,money),'-1.00万元')
  assert.equal(formatMetric('100000000',undefined,{...money,scale:'yi'}),'1.00亿元')
  assert.equal(formatMetric(null,undefined,money),'无数据')
  assert.equal(formatMetric(0.125,undefined,ratio),'12.50%')
  assert.equal(formatMetric(0,undefined,count),'0件')
})
test('component metric and component default explicitly override saved chart formats',()=>{
  const style={metrics:[{key:'sales',format:money}]}
  assert.equal(resolveChartMetricFormat(style,'sales'),money)
  assert.equal(resolveChartMetricFormat(style,'sales',[],count),count)
  assert.equal(resolveChartMetricFormat(style,'sales',[{key:'sales',format:ratio,rules:[]}],count),ratio)
  assert.equal(resolveChartMetricFormat(style,'unknown'),undefined)
})
test('combo series independently select geometry, axis, color and metric formatting',()=>{
  const {option,result}=chart('combo',{metrics:[{key:'sales',format:money,seriesType:'line',axis:'right',color:'#3366FF'},{key:'rate',format:ratio,seriesType:'bar',axis:'left'}],comboAxes:{left:{min:0,max:1}}})
  const o=option()
  assert.equal(o.series[0].type,'line');assert.equal(o.series[0].yAxisIndex,1);assert.equal(o.series[0].lineStyle.color,'#3366FF')
  assert.equal(o.series[1].type,'bar');assert.equal(o.series[1].yAxisIndex,0)
  assert.equal(o.yAxis[0].axisLabel.formatter(0.25),'25.00%');assert.equal(o.yAxis[1].axisLabel.formatter(10000),'1.00万元')
  assert.equal(o.yAxis[0].max,1);assert.equal(o.series[0].data[0],10000)
  assert.equal(o.series[0].label.formatter({dataIndex:0}),'1.00万元')
  assert.match(o.tooltip.formatter([{seriesIndex:0,dataIndex:0},{seriesIndex:1,dataIndex:0}]),/25.00%/)
  assert.equal(result.rows[0].rate,0.25)
})
test('mixed units sharing an axis never format every series using the first metric unit',()=>{
  const o=chart('combo',{metrics:[{key:'sales',format:money,axis:'left'},{key:'rate',format:ratio,axis:'left'}]}).option()
  assert.equal(o.yAxis[0].axisLabel.formatter(10000),'10000')
  assert.match(o.tooltip.formatter({seriesIndex:0,dataIndex:0}),/1.00万元/)
})
test('fixed and computed reference lines use raw values, correct axis direction and metric-specific labels',()=>{
  const referenceLines=[{key:'sales',kind:'fixed',value:20000,name:'目标',color:'#F59E0B',lineType:'dashed'},{key:'sales',kind:'average',name:'均值',color:'#3366FF',lineType:'solid'}]
  const o=chart('hbar',{metrics:[{key:'sales',format:money}],referenceLines}).option()
  const lines=o.series[0].markLine.data
  assert.equal(lines[0].xAxis,20000);assert.equal(lines[1].xAxis,20000)
  assert.equal(lines[0].label.formatter(),'目标: 2.00万元')
  assert.equal(o.series[1].markLine,undefined)
  const empty=chart('line',{referenceLines});empty.result.rows[0].sales=null;empty.result.rows[1].sales=null
  assert.equal(empty.option().series[0].markLine.data.length,1)
})
test('threshold rules compare original values and percent stacks retain normalized labels',()=>{
  const style={metrics:[{key:'sales',format:money,rules:[{op:'GTE',value:'20000',color:'#F56C6C'}]}]}
  const o=chart('bar',style).option()
  assert.equal(o.series[0].data[1].itemStyle.color,'#F56C6C');assert.equal(o.series[0].data[1].value,30000)
  assert.equal(chart('percent',style).option().series[0].label.formatter,'{c}%')
})
test('pivot metadata formats cells without rewriting their numeric values',()=>{
  const {definition,result}=chart('pivot')
  definition.query.dimensions.push({field:'month'});definition.advanced={pivotRowFields:['category'],pivotColFields:['month']};for(const row of result.rows)row.month='2026-01'
  const cfg=buildPivotDataConfig(definition,result,(key,value)=>String(formatMetric(value,undefined,key==='sales'?money:ratio)),{sales:'销售额'})
  assert.equal(cfg.meta.find(m=>m.field==='sales').name,'销售额');assert.equal(cfg.meta.find(m=>m.field==='sales').formatter(10000),'1.00万元')
  assert.equal(cfg.data[0].sales,10000)
})
test('invalid bounds, missing fixed targets and malformed decimal thresholds cannot be saved',()=>{
  assert.match(validateChartMetricPresentation(chart('combo',{comboAxes:{left:{min:10,max:0}}}).definition),/最大值/)
  assert.match(validateChartMetricPresentation(chart('bar',{referenceLines:[{kind:'fixed'}]}).definition),/原始数值/)
  assert.match(validateChartMetricPresentation(chart('bar',{metrics:[{key:'sales',rules:[{value:'abc'}]}]}).definition),/阈值/)
})
test('removal, replacement and insertion never shift metric styles onto a different field',()=>{
  const before=[{field:'amount',agg:'SUM',alias:'value_1'},{field:'rate',agg:'AVG',alias:'value_2'}]
  const after=[before[1]]
  const style={metrics:[{key:'value_1',format:money},{key:'value_2',format:ratio}],referenceLines:[{key:'value_1'},{key:'value_2'}]}
  retainChartMetricBindings(style,after,before)
  assert.equal(style.metrics.length,1);assert.equal(style.metrics[0].format,ratio)
  assert.equal(nextChartMetricAlias(after),'value_1')
  retainChartMetricBindings(style,[{...before[1],field:'other'}],after)
  assert.equal(style.metrics.length,0);assert.equal(style.referenceLines.length,0)
})


test('shared typography respects custom dark colors and saved metric styles without rewriting values',()=>{
 const appearance={theme:'dark',background:'#101828',cardBackground:'#1D2939',textColor:'#FCFAFF',borderColor:'#475467',palette:'warm',titleSize:14,radius:8,decimals:2,grouping:true}
 const before=structuredClone(appearance),fixture=chart('bar',{metrics:[{key:'sales',color:'#ABCDEF',format:money}]})
 const option=buildChartPreviewOption({type:'bar',definition:fixture.definition,result:fixture.result,labels:{sales:'销售额'},appearance,formatFor:()=>undefined})
 assert.equal(option.textStyle.fontFamily,BI_FONT_FAMILY);assert.equal(option.legend.textStyle.fontFamily,BI_FONT_FAMILY);assert.equal(option.tooltip.textStyle.fontFamily,BI_FONT_FAMILY)
 assert.equal(option.textStyle.color,'#FCFAFF');assert.equal(option.series[0].itemStyle.color,'#ABCDEF');assert.equal(option.yAxis[0]?.splitLine?.lineStyle?.color || option.yAxis.splitLine.lineStyle.color,'#475467')
 assert.equal(presentationVars(appearance)['--bi-link'],'#A8C1FF')
 assert.deepEqual(appearance,before);assert.equal(fixture.result.rows[0].sales,10000)
 const p=pivotTheme(fixture.definition,appearance);assert.equal(p.theme.dataCell.text.fontFamily,BI_FONT_FAMILY);assert.equal(p.theme.dataCell.cell.backgroundColor,'#1D2939')
 assert.equal(resolveBiTheme().text,'#182230');assert.equal(resolveBiTheme({theme:'dark'}).surface,'#1C2432')
})
