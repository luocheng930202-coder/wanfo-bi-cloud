import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import { test } from 'node:test'

const opts = { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }
let pivotSrc = await fs.readFile(new URL('../src/utils/biPivotPreview.ts', import.meta.url), 'utf8')
pivotSrc = pivotSrc.split('\n').filter(line => !line.startsWith('import ')).join('\n')
const metricStub = `export function metricResultKey(m){return m.alias || (m.agg==='NONE'?m.field:m.agg.toLowerCase()+'_'+m.field)}`
const bundle = metricStub + '\n' + ts.transpileModule(pivotSrc, opts).outputText
const { validatePivotConfig, buildPivotDataConfig, syncPivotQueryDimensions,pivotTotalValue,buildPivotOptions } = await import(
  'data:text/javascript;base64,' + Buffer.from(bundle).toString('base64')
)

const definition = {
  schemaVersion: 1,
  name: 'p',
  type: 'pivot',
  query: {
    datasetId: 1,
    dimensions: [{ field: 'city', alias: 'city' }, { field: 'cat', alias: 'cat' }],
    metrics: [{ field: 'amount', agg: 'SUM', alias: 'sum_amount' }],
    limit: 100
  },
  advanced: { pivotRowFields: ['city'], pivotColFields: ['cat'] }
}

test('validate and build pivot config', () => {
  assert.equal(validatePivotConfig(definition), null)
  const cfg = buildPivotDataConfig(definition, { columns: [], rows: [{ city: '杭州', cat: 'A', sum_amount: 9 }], rowCount: 1, costMs: 1 })
  assert.deepEqual(cfg.fields.rows, ['city'])
  assert.deepEqual(cfg.fields.columns, ['cat'])
  assert.deepEqual(cfg.fields.values, ['sum_amount'])
})

test('sync dimensions from pivot fields', () => {
  syncPivotQueryDimensions(definition)
  assert.deepEqual(definition.query.dimensions.map(d => d.field), ['city', 'cat'])
})


test('mixed pivot metrics use their own aggregate and never average aggregates or count groups',()=>{
 const d=structuredClone(definition);d.query.metrics=[{field:'a',agg:'SUM',alias:'sales'},{field:'id',agg:'COUNT',alias:'orders'},{field:'a',agg:'MIN',alias:'minimum'},{field:'a',agg:'MAX',alias:'maximum'}]
 assert.equal(pivotTotalValue(d,'sales',[0.1,0.2,null]),0.3);assert.equal(pivotTotalValue(d,'orders',[2,10]),12);assert.equal(pivotTotalValue(d,'minimum',[12,-2,null]),-2);assert.equal(pivotTotalValue(d,'maximum',[-2,12]),12)
 assert.ok(Number.isNaN(pivotTotalValue(d,'minimum',[null,undefined])))
 assert.throws(()=>pivotTotalValue(d,'sales',['9007199254740992',1]),/安全/)
 d.query.metrics[0].agg='AVG';assert.throws(()=>pivotTotalValue(d,'sales',[10,100]),/明细/)
})
test('pivot totals reject unsupported aggregates and extra grouping dimensions',()=>{
 const d=structuredClone(definition);d.style={pivotRowTotals:true};assert.equal(validatePivotConfig(d),null)
 for(const agg of ['AVG','COUNT_DISTINCT']){d.query.metrics[0].agg=agg;assert.match(validatePivotConfig(d),/重新聚合/)}
 d.style={pivotHierarchy:'tree'};assert.match(validatePivotConfig(d),/重新聚合/)
 d.style={};d.query.dimensions.push({field:'extra'});assert.match(validatePivotConfig(d),/完全一致/)
})
test('pivot options bind subtotal dimensions, raw aggregate callbacks and current result labels',()=>{
 const d=structuredClone(definition);d.query.dimensions.unshift({field:'province',alias:'p'});d.advanced.pivotRowFields=['province','city'];d.style={pivotRowTotals:true,pivotRowSubtotals:true,pivotColorScale:true,pivotValuesInCols:false,pivotHierarchy:'tree'}
 const result={rows:[{p:'浙江',city:'杭州',cat:'A',sum_amount:10}],columns:[],rowCount:1,costMs:1}
 const o=buildPivotOptions(d,result,800);assert.deepEqual(o.totals.row.subTotalsDimensions,['p']);assert.equal(o.totals.row.showSubTotals,true);assert.equal(o.totals.col.showSubTotals,false);assert.match(o.totals.row.grandTotalsLabel,/当前结果/)
 assert.equal(o.totals.row.calcGrandTotals.calcFunc({'$$extra$$':'sum_amount'},[{'$$value$$':10},{'$$value$$':20}]),30)
 assert.equal(o.conditions.background[0].mapping(null),undefined);assert.equal(o.conditions.background[0].mapping(10).fill,'#8aadf5')
 const cfg=buildPivotDataConfig(d,result,String,{p:'省份'});assert.equal(cfg.fields.valueInCols,false);assert.equal(cfg.meta.find(m=>m.field==='p').name,'省份')
 assert.throws(()=>buildPivotDataConfig(d,{...result,rows:[...result.rows,...result.rows]}),/重复分组/)
})
