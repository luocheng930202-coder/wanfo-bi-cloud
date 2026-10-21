import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
let bundle="function metricResultKey(m){return m.alias || (m.agg==='NONE'?m.field:m.agg.toLowerCase()+'_'+m.field)}\n"
for(const file of ['biPresentation.ts','biTablePresentation.ts']){const source=(await fs.readFile(new URL('../src/utils/'+file,import.meta.url),'utf8')).split('\n').filter(l=>!l.startsWith('import ')).join('\n');bundle+=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText+'\n'}
const {tableOutputs,retainTableColumns,tableBackground,validateTableStyle}=await import('data:text/javascript;base64,'+Buffer.from(bundle).toString('base64'))
const d={type:'table',query:{dimensions:[{field:'city',alias:'region'}],metrics:[{field:'amount',agg:'SUM',alias:'sales'}]}}
test('table backgrounds compare precise raw decimal values in rule order without coloring missing values',()=>{
 const style={tableColumns:[{key:'sales',backgroundRules:[{op:'GTE',value:'10000000000000000.01',color:'#DCFCE7'},{op:'LT',value:'0',color:'#FEE2E2'},{op:'EQ',value:'0',color:'#FFFFFF'}]}]}
 assert.equal(tableBackground(style,'sales','10000000000000000.01'),'#DCFCE7');assert.equal(tableBackground(style,'sales','10000000000000000.00'),undefined)
 assert.equal(tableBackground(style,'sales',-1),'#FEE2E2');assert.equal(tableBackground(style,'sales',0),'#FFFFFF');assert.equal(tableBackground(style,'sales',null),undefined);assert.equal(tableBackground(style,'region',1),undefined)
})
test('column settings follow field and aggregation identity, and deleted or replaced bindings are cleared',()=>{
 const previous=tableOutputs(d),style={tableColumns:[{key:'region',width:180},{key:'sales',width:160}]}
 const next=structuredClone(d);next.query.metrics[0].agg='AVG';retainTableColumns(style,tableOutputs(next),previous);assert.deepEqual(style.tableColumns,[{key:'region',width:180}])
 next.query.dimensions[0].field='county';retainTableColumns(style,tableOutputs(next),previous);assert.deepEqual(style.tableColumns,[])
})
test('table validation rejects stale bindings, invalid widths and background rules on dimensions',()=>{
 for(const columns of [[{key:'lost'}],[{key:'sales',width:59}],[{key:'sales',width:160.5}],[{key:'region',backgroundRules:[{op:'GT',value:'1',color:'#FFFFFF'}]}],[{key:'sales',backgroundRules:[{op:'GT',value:'x',color:'#FFFFFF'}]}]])assert.ok(validateTableStyle({...d,style:{tableColumns:columns}}))
 assert.equal(validateTableStyle({...d,style:{tableColumns:[{key:'region',fixed:'left',width:200},{key:'sales',align:'right'}]}}),null)
})
