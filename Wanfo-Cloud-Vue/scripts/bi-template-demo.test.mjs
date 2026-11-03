import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biTemplateDemo.ts',import.meta.url),'utf8')
const code=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {templateDemoPayloads}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'))
test('all current built-in template data panels have isolated, renderable demo fixtures',async()=>{
  for(const name of ['market-dashboard-retail','market-screen-ops','market-screen-school-canteen-2d']){
    const file=JSON.parse(await fs.readFile(new URL('../../Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/bi/template-market/'+name+'.json',import.meta.url),'utf8'))
    const before=JSON.stringify(file),payloads=templateDemoPayloads(file)
    for(const panel of file.panels.filter(panel=>['chart','kpi'].includes(panel.componentType))){
      const payload=payloads[panel.key];assert.ok(payload,panel.key);assert.equal(payload.presentation.type,panel.chartType)
      assert.ok(payload.data.rows.length>0);assert.equal(payload.data.rowCount,payload.data.rows.length)
      assert.ok(payload.presentation.metrics.every(key=>payload.data.columns.includes(key)))
      assert.equal(payload.data.rows[0].demo_value instanceof Number,false)
    }
    assert.equal(JSON.stringify(file),before)
    const raw=JSON.stringify(payloads);for(const key of ['chartId','datasetId','filterTree','password'])assert.ok(!raw.includes(key))
    const first=Object.values(payloads)[0];first.data.rows[0].demo_value=-999
    assert.notEqual(Object.values(templateDemoPayloads(file))[0].data.rows[0].demo_value,-999)
  }
})
test('unsupported components remain placeholders and percentages stay ratios',()=>{
  const file={panels:[{key:'gauge',componentType:'chart',chartType:'gauge'},{key:'static',componentType:'text'},{key:'rate',componentType:'kpi',chartType:'table',style:{numberFormat:{mode:'percent'}}}]}
  const result=templateDemoPayloads(file)
  assert.deepEqual(Object.keys(result),['rate']);assert.ok(result.rate.data.rows[0].demo_value>0 && result.rate.data.rows[0].demo_value<1)
})
