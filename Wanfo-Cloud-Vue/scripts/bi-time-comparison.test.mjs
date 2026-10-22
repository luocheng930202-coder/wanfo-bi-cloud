import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
let bundle=''
for(const file of ['biTheme.ts','biPresentation.ts','biChartTypes.ts','biChartPresentation.ts','biEchartsPreview.ts','biTimeGrain.ts','biTimeComparison.ts']){
 const source=(await fs.readFile(new URL('../src/utils/'+file,import.meta.url),'utf8')).split('\n').filter(line=>!line.startsWith('import ')).join('\n')
 bundle+=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText+'\n'
}
const {comparisonDefinition,comparisonLabels,comparisonRate,buildChartPreviewOption,validateTimeGrain}=await import('data:text/javascript;base64,'+Buffer.from(bundle).toString('base64'))
const chart=()=>({schemaVersion:1,name:'月销售',type:'line',query:{datasetId:1,timeComparison:'previous',dimensions:[{field:'day',timeGrain:'month',timeFill:'zero'}],metrics:[{field:'value',agg:'AVG',alias:'sales'}]}})
const result=()=>({columns:['day','sales','base','diff','rate'],rows:[{day:'2026-01-01',sales:10,base:null,diff:null,rate:null},{day:'2026-02-01',sales:0,base:10,diff:null,rate:null}],rowCount:2,costMs:1,timeFill:{mode:'zero',status:'complete',periods:['2026-02-01'],addedRows:1,sourceRows:1},timeComparison:{mode:'previous',label:'环比',status:'complete',fields:[{key:'sales',baseline:'base',difference:'diff',rate:'rate'}]}})
test('comparison adds a baseline series without mutating the saved query or adding rate to value axes',()=>{
 const c=chart(),r=result(),copy=JSON.stringify(c),view=comparisonDefinition(c,r)
 assert.equal(JSON.stringify(c),copy);assert.deepEqual(view.query.metrics.map(m=>m.alias),['sales','base'])
 assert.equal(comparisonLabels(r,{sales:'平均销售额'}).base,'平均销售额 · 环比基期')
 assert.equal(comparisonRate(r,'rate','0.125'),'12.50%');assert.equal(comparisonRate(r,'rate',null),'—');assert.equal(comparisonRate(r,'sales',1),undefined)
 r.timeComparison.status='skipped';assert.equal(comparisonDefinition(c,r),c)
 r.timeComparison.status='complete';r.timeComparison.mode='year';assert.equal(comparisonDefinition(c,r),c)
})
test('missing baseline is never coerced to zero and real baseline survives synthetic current rows',()=>{
 const c=chart();c.advanced={nullMode:'zero'};const r=result(),d=comparisonDefinition(c,r)
 const o=buildChartPreviewOption({type:'line',definition:d,result:r,labels:comparisonLabels(r,{sales:'销售'}),formatFor:()=>undefined})
 assert.deepEqual(o.series[0].data,[10,0]);assert.deepEqual(o.series[1].data,[null,10])
 assert.doesNotMatch(o.tooltip.formatter([{seriesIndex:1,dataIndex:1,name:'2026-02-01'}]),/非实际/)
})
test('comparison settings validate type, grain, sort, metric and period limits',()=>{
 const c=chart(),fields=[{name:'day',type:'DATE'}];assert.equal(validateTimeGrain(c,fields),null)
 c.type='stack';assert.match(validateTimeGrain(c,fields),/关闭比较/);c.type='line'
 c.query.orderBys=[{field:'sales',order:'desc'}];assert.match(validateTimeGrain(c,fields),/时间排序/);c.query.orderBys=[]
 c.query.metrics[0].agg='NONE';assert.match(validateTimeGrain(c,fields),/聚合/);c.query.metrics[0].agg='AVG'
 c.query.limit=1001;assert.match(validateTimeGrain(c,fields),/1000/);c.query.limit=1000
 delete c.query.dimensions[0].timeGrain;assert.match(validateTimeGrain(c,fields),/时间维度/)
})
