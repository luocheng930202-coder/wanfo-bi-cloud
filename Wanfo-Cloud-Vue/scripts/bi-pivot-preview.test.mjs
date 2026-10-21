import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import { test } from 'node:test'
import {createRequire} from 'node:module'
const {PivotDataSet}=createRequire(import.meta.url)('@antv/s2/lib/data-set/pivot-data-set.js')

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
 for(const agg of ['AVG','COUNT_DISTINCT']){d.query.metrics[0].agg=agg;assert.equal(validatePivotConfig(d),null);assert.throws(()=>buildPivotOptions(d,{rows:[]},800),/服务端/)}
 d.style={pivotHierarchy:'tree'};assert.equal(validatePivotConfig(d),null)
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


test('server totals become native partial records, preserve exact values and never use client fallback',()=>{
 const d=structuredClone(definition);d.query.metrics=[{field:'amount',agg:'AVG',alias:'mean'},{field:'customer',agg:'COUNT_DISTINCT',alias:'buyers'}];d.style={pivotRowTotals:true,pivotColTotals:true,pivotHierarchy:'tree'}
 const result={rows:[{city:null,cat:'A',mean:'10',buyers:'2'},{city:'B',cat:'A',mean:'100',buyers:'1'}],rowCount:2,costMs:1,pivotTotals:{status:'complete',scope:'filtered',rowFields:['city'],colFields:['cat'],metricKeys:['mean','buyers'],detailTruncated:true,cells:[{rowDepth:0,colDepth:0,dimensions:{},values:{mean:'40.000000000000',buyers:'2'}},{rowDepth:1,colDepth:0,dimensions:{city:null},values:{mean:'10',buyers:'2'}},{rowDepth:0,colDepth:1,dimensions:{cat:'A'},values:{mean:'40',buyers:'2'}}]}}
 const before=JSON.stringify(result),cfg=buildPivotDataConfig(d,result)
 assert.equal(cfg.data.length,5);assert.deepEqual(cfg.data[2],{mean:'40.000000000000',buyers:'2'});assert.deepEqual(cfg.data[3],{city:null,mean:'10',buyers:'2'});assert.equal(JSON.stringify(result),before)
 const options=buildPivotOptions(d,result,800);assert.equal(options.hierarchyType,'tree');assert.match(options.totals.row.grandTotalsLabel,/筛选范围/)
 assert.ok(Number.isNaN(options.totals.row.calcGrandTotals.calcFunc({'$$extra$$':'mean'},[{'$$value$$':10},{'$$value$$':100}])))
 result.pivotTotals.status='unavailable';result.pivotTotals.reason='分组过多';const safe=buildPivotOptions(d,result,800);assert.equal(safe.hierarchyType,'grid');assert.equal(safe.totals.row.showGrandTotals,false);assert.equal(buildPivotDataConfig(d,result).data.length,2)
})

test('the actual S2 data set reads exact precomputed grand and subtotal cells in both value orientations',()=>{
 const d=structuredClone(definition);d.query.metrics=[{field:'amount',agg:'AVG',alias:'mean'},{field:'customer',agg:'COUNT_DISTINCT',alias:'buyers'}];d.style={pivotRowTotals:true,pivotColTotals:true,pivotHierarchy:'tree'}
 const result={rows:[{city:null,cat:'A',mean:'10',buyers:'2'},{city:'B',cat:'A',mean:'100',buyers:'1'}],rowCount:2,costMs:1,pivotTotals:{status:'complete',scope:'filtered',rowFields:['city'],colFields:['cat'],metricKeys:['mean','buyers'],detailTruncated:false,cells:[{rowDepth:0,colDepth:0,dimensions:{},values:{mean:'40.000000000000',buyers:'2'}},{rowDepth:1,colDepth:0,dimensions:{city:null},values:{mean:'10',buyers:'2'}},{rowDepth:0,colDepth:1,dimensions:{cat:'A'},values:{mean:'40',buyers:'2'}}]}}
 for(const valueInCols of [true,false]) {
  d.style.pivotValuesInCols=valueInCols
  const sheet={options:buildPivotOptions(d,result,800),store:new Map(),isHierarchyTreeType:()=>true,isHierarchyGridTreeType:()=>false,isValueInCols:()=>valueInCols}
  const dataSet=new PivotDataSet(sheet);dataSet.setDataCfg(buildPivotDataConfig(d,result))
  assert.equal(dataSet.getCellData({query:{'$$extra$$':'mean'},isTotals:true})['$$value$$'],'40.000000000000')
  assert.equal(dataSet.getCellData({query:{'$$extra$$':'buyers'},isTotals:true})['$$value$$'],'2')
  assert.equal(dataSet.getCellData({query:{city:null,'$$extra$$':'mean'},isTotals:true})['$$value$$'],'10')
  assert.ok(!dataSet.getDimensionValues('city').includes('$$total$$'))
 }
})
