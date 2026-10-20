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
