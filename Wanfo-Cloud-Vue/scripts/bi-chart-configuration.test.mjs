import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biChartTypes.ts',import.meta.url),'utf8')
const compiled=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {chartConfigurationIssues:check}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))
const fields=[{name:'region',type:'VARCHAR'},{name:'amount',type:'DECIMAL'},{name:'date',type:'DATE'}]
const chart=(type='bar')=>({type,query:{datasetId:1,dimensions:[{field:'region'}],metrics:[{field:'amount',agg:'SUM'}]}})
const errors=d=>check(d,fields).filter(i=>i.severity==='error')
test('valid chart stays queryable and semantically invalid data has advisory warnings only',()=>{
  assert.deepEqual(errors(chart()),[])
  const empty=chart();empty.query.metrics=[];assert.equal(errors(empty).length,1)
  const pie=chart('pie');assert.deepEqual(errors(pie),[]);assert.ok(check(pie,fields).some(i=>i.code==='nonnegative' && i.severity==='warning'))
})
test('missing or revoked fields cannot silently reach query/save',()=>{
  const d=chart();d.query.metrics[0].field='removed';assert.ok(errors(d).some(i=>i.code==='field'))
  d.query.datasetId=0;assert.ok(errors(d).some(i=>i.code==='dataset'))
})
test('text field aggregation recommends count, while count remains valid',()=>{
  const d=chart();d.query.metrics[0].field='region';assert.ok(errors(d).some(i=>i.code==='numeric'))
  d.query.metrics[0].agg='COUNT';assert.deepEqual(errors(d),[])
})
test('duplicate dimension and colliding result aliases are rejected',()=>{
  const d=chart();d.query.dimensions.push({field:'region'});assert.ok(errors(d).some(i=>i.code==='duplicate'))
  d.query.dimensions.pop();d.query.metrics[0].alias='region';assert.ok(errors(d).some(i=>i.code==='duplicate'))
})
test('scatter requires two axes and optional third bubble role without blocking legacy raw values',()=>{
  const d=chart('scatter');assert.ok(errors(d).some(i=>i.code==='shape'))
  d.query.metrics=[{field:'amount',agg:'NONE',alias:'x'},{field:'amount',agg:'NONE',alias:'y'}];assert.deepEqual(errors(d),[])
})
test('pivot row/column collisions and unfinished filters are actionable',()=>{
  const d=chart('pivot');d.advanced={pivotRowFields:['region'],pivotColFields:['region']};assert.ok(errors(d).some(i=>i.code==='duplicate'))
  d.advanced.pivotColFields=['date'];d.query.filterTree={field:'amount',op:'GT',value:''};assert.ok(errors(d).some(i=>i.code==='filter'))
  d.query.filterTree.value=0;assert.deepEqual(errors(d),[])
})
test('specialized chart types require their own field roles and raw-value modes',()=>{
  const heat={type:'heatmap',query:{datasetId:1,dimensions:[{field:'region'},{field:'date'}],metrics:[{field:'amount',agg:'SUM'}]}}
  assert.deepEqual(errors(heat),[])
  const hist={type:'histogram',query:{datasetId:1,dimensions:[],metrics:[{field:'amount',agg:'NONE'}]}}
  assert.deepEqual(errors(hist),[])
  const box={type:'boxplot',query:{datasetId:1,dimensions:[{field:'region'}],metrics:[{field:'amount',agg:'NONE'}]}}
  assert.deepEqual(errors(box),[])
  const geo={type:'geo-map',query:{datasetId:1,dimensions:[],metrics:[{field:'amount',agg:'NONE'},{field:'amount',agg:'SUM',alias:'lat'}]}}
  assert.ok(errors(geo).some(i=>i.code==='shape'))
})
test('treemap and sunburst accept ordered multi-level paths with distinct dimensions',()=>{
  const hierarchyFields=[...fields,{name:'channel',type:'VARCHAR'},{name:'product',type:'VARCHAR'}]
  const hierarchy={type:'sunburst',query:{datasetId:1,dimensions:[{field:'region'},{field:'channel'},{field:'date'}],metrics:[{field:'amount',agg:'SUM'}]}}
  assert.deepEqual(check(hierarchy,hierarchyFields).filter(i=>i.severity==='error'),[])
  hierarchy.query.dimensions.push({field:'region'});assert.ok(errors(hierarchy).some(i=>i.code==='shape'||i.code==='duplicate'))
  const tree={type:'treemap',query:{datasetId:1,dimensions:[{field:'region'},{field:'channel'},{field:'date'}],metrics:[{field:'amount',agg:'SUM'}]}}
  assert.deepEqual(check(tree,hierarchyFields).filter(i=>i.severity==='error'),[])
})
