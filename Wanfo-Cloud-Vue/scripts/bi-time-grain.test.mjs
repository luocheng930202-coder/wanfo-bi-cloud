import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=(await fs.readFile(new URL('../src/utils/biTimeGrain.ts',import.meta.url),'utf8')).split('\n').filter(line=>!line.startsWith('import ')).join('\n')
const compiled=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {TIME_GRAINS,temporalField,validateTimeGrain}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))
const fields=[{name:'date',type:'TIMESTAMP'},{name:'text',type:'VARCHAR'}]
function chart(grain='month'){return {type:'line',query:{dimensions:[{field:'date',timeGrain:grain}],metrics:[]}}}
test('only supported calendar field types and grains are accepted',()=>{
  for(const type of ['DATE','datetime','TIMESTAMP(6)'])assert.equal(temporalField(type),true)
  for(const type of ['VARCHAR','TIME','TIMESTAMP WITH TIME ZONE','INT'])assert.equal(temporalField(type),false)
  for(const g of TIME_GRAINS)assert.equal(validateTimeGrain(chart(g.value),fields),null)
  assert.match(validateTimeGrain(chart('hour'),fields),/有效/)
})
test('invalid combinations give a correction instead of losing a stored bucket',()=>{
  const c=chart();c.query.dimensions[0].field='text';assert.match(validateTimeGrain(c,fields),/日期/)
  c.query.dimensions[0].field='date';c.type='map';assert.match(validateTimeGrain(c,fields),/图表类型/)
  c.type='line';c.advanced={drillFields:['date','text']};assert.match(validateTimeGrain(c,fields),/下钻/)
  delete c.query.dimensions[0].timeGrain;assert.equal(validateTimeGrain(c,fields),null)
})

test('gap filling requires a calendar aggregate and chronological sorting',()=>{
  const c=chart();c.query.metrics=[{field:'value',agg:'SUM'}];c.query.dimensions[0].timeFill='zero'
  assert.equal(validateTimeGrain(c,fields),null)
  c.query.orderBys=[{field:'value',order:'desc'}];assert.match(validateTimeGrain(c,fields),/时间排序/)
  c.query.orderBys=[{field:'date',order:'desc'}];assert.equal(validateTimeGrain(c,fields),null)
  c.query.metrics[0].agg='NONE';assert.match(validateTimeGrain(c,fields),/聚合/)
  c.query.metrics[0].agg='SUM';delete c.query.dimensions[0].timeGrain;assert.match(validateTimeGrain(c,fields),/粒度/)
  c.query.dimensions[0].timeFill='unexpected';assert.match(validateTimeGrain(c,fields),/缺失周期/)
})
