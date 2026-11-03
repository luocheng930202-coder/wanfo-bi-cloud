import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const opts={compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}
const chartTypes=await fs.readFile(new URL('../src/utils/biChartTypes.ts',import.meta.url),'utf8')
let core=await fs.readFile(new URL('../src/utils/biTemplateBindingCore.ts',import.meta.url),'utf8')
core=core.split('\n').filter(line=>!line.includes('@/utils/biChartTypes')).join('\n')
const bundle=ts.transpileModule(chartTypes,opts).outputText+'\n'+ts.transpileModule(core,opts).outputText
const tpl=await fs.readFile(new URL('../src/utils/biTemplates.ts',import.meta.url),'utf8')
const {analyzeBindingImpact,suggestPanelConfig,remapPanelConfig,isNumericFieldType}=await import('data:text/javascript;base64,'+Buffer.from(bundle).toString('base64'))
const {getScreenTemplate}=await import('data:text/javascript;base64,'+Buffer.from(ts.transpileModule(tpl,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText).toString('base64'))
const canteenSource=(await fs.readFile(new URL('../src/utils/biCanteenTemplateContracts.ts',import.meta.url),'utf8')).split('\n').filter(line=>!line.includes("from './biTemplateBindingCore'")).join('\n')
const {canteenBindingError,canteenNumberFormat}=await import('data:text/javascript;base64,'+Buffer.from(bundle+'\n'+ts.transpileModule(canteenSource,opts).outputText).toString('base64'))
test('canteen units preserve raw ratios and avoid assigning units to mixed metric panels',()=>{
  assert.equal(canteenNumberFormat('acceptance-rate').mode,'percent')
  assert.equal(canteenNumberFormat('acceptance-rate').suffix,'')
  assert.equal(canteenNumberFormat('purchase-total').suffix,'元')
  assert.equal(canteenNumberFormat('meal-count').decimals,0)
  assert.equal(canteenNumberFormat('school-count').suffix,'所')
  assert.equal(canteenNumberFormat('school-region'),undefined)
  assert.equal(canteenNumberFormat('acceptance-events'),undefined)
})

test('canteen contracts reject unavailable and nonnumeric amounts but permit text school identifiers',()=>{
  const config={type:'table',dimensions:[],metrics:[{field:'city',agg:'SUM'}],orderBys:[],limit:100}
  assert.match(canteenBindingError('purchase-total',config,fields),/数值类型/)
  assert.match(canteenBindingError('purchase-total',{...config,metrics:[{field:'missing',agg:'SUM'}]},fields),/不可见/)
  assert.equal(canteenBindingError('school-count',{...config,metrics:[{field:'city',agg:'COUNT_DISTINCT'}]},fields),undefined)
  assert.match(canteenBindingError('acceptance-rate',{...config,metrics:[{field:'amount',agg:'SUM'}]},fields),/AVG/)
})

test('canteen trend requires a typed date and ascending time ordering',()=>{
  const config={type:'line',dimensions:[{field:'day'}],metrics:[{field:'amount',agg:'SUM'}],orderBys:[],limit:100}
  assert.match(canteenBindingError('meal-trend',config,[...fields,{name:'day',type:'varchar'}]),/日期或时间/)
  const dated=[...fields,{name:'day',type:'timestamp'}]
  assert.match(canteenBindingError('meal-trend',config,dated),/升序/)
  assert.equal(canteenBindingError('meal-trend',{...config,orderBys:[{field:'day',order:'ASC'}]},dated),undefined)
})
const fields=[{name:'city',type:'varchar',dimFlag:'1'},{name:'amount',type:'decimal',dimFlag:'0'},{name:'qty',type:'int',dimFlag:'0'}]
test('field typing helpers and suggestions respect slot shape',()=>{
  assert.equal(isNumericFieldType('decimal'),true);assert.equal(isNumericFieldType('varchar'),false)
  const slot=getScreenTemplate('business-overview').slots.find(s=>s.key==='analysis-a')
  const bar=suggestPanelConfig(slot,fields);assert.equal(bar.type,'bar');assert.equal(bar.dimensions[0].field,'city');assert.equal(bar.metrics[0].field,'amount')
  const kpi=getScreenTemplate('business-overview').slots.find(s=>s.key==='metric-a')
  const card=suggestPanelConfig(kpi,fields);assert.equal(card.dimensions.length,0);assert.equal(card.metrics.length,1)
})
test('impact analysis catches missing fields and invalid kpi or pie shapes',()=>{
  const kpi=getScreenTemplate('business-overview').slots[0]
  const bad={type:'table',dimensions:[{field:'city'}],metrics:[{field:'amount',agg:'SUM'}],orderBys:[],limit:100}
  assert.ok(analyzeBindingImpact(kpi,bad,fields).some(x=>x.includes('指标卡不能配置维度')))
  const pie={...getScreenTemplate('business-overview').slots[2],type:'pie'}
  const cfg=suggestPanelConfig(pie,fields);cfg.metrics.push({field:'qty',agg:'SUM',alias:'qty'})
  assert.ok(analyzeBindingImpact(pie,cfg,fields).some(x=>x.includes('饼图')))
  const missing=suggestPanelConfig(getScreenTemplate('business-overview').slots[2],fields);missing.dimensions[0].field='ghost'
  assert.ok(analyzeBindingImpact(getScreenTemplate('business-overview').slots[2],missing,fields).some(x=>x.includes('不存在')))
})
test('remap keeps shared field names when switching dataset',()=>{
  const slot=getScreenTemplate('business-overview').slots.find(s=>s.key==='analysis-a')
  const cfg=suggestPanelConfig(slot,fields)
  const alt=[{name:'city',type:'varchar',dimFlag:'1'},{name:'amount',type:'decimal',dimFlag:'0'},{name:'extra',type:'varchar',dimFlag:'1'}]
  const remapped=remapPanelConfig(slot,cfg,alt)
  assert.equal(remapped.dimensions[0].field,'city')
  assert.equal(remapped.metrics[0].field,'amount')
})
