import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biSceneData.ts',import.meta.url),'utf8')
const code=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {sceneData,sceneBarGeometry}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'))
test('unbound, unconfigured, empty and invalid inputs never generate demonstration data',()=>{
  assert.equal(sceneData([{v:10}],undefined,'v',false).state,'unbound')
  assert.equal(sceneData([],undefined,undefined,true).state,'unconfigured')
  assert.equal(sceneData([],undefined,'v',true).state,'empty')
  const result=sceneData([null,'',true,'1,2','NaN',Infinity,{},[]].map(v=>({v})),undefined,'v',true)
  assert.equal(result.state,'invalid');assert.equal(result.invalid,8);assert.deepEqual(result.records,[])
})
test('numeric values preserve zero, negatives and valid grouped numbers without mutating rows',()=>{
  const rows=[{name:'零',v:0},{name:'退款',v:'-123.5'},{name:'合计',v:'1,234.50'},{name:'指数',v:'2e2'}]
  const before=JSON.stringify(rows),result=sceneData(rows,'name','v',true)
  assert.deepEqual(result.records.map(row=>row.value),[0,-123.5,1234.5,200]);assert.equal(result.invalid,0)
  assert.equal(JSON.stringify(rows),before)
})
test('query order and display limits are explicit, invalid rows do not consume the display quota',()=>{
  const result=sceneData([{v:null},...Array.from({length:7},(_,v)=>({v}))],undefined,'v',true)
  assert.deepEqual(result.records.map(row=>row.value),[0,1,2,3,4]);assert.equal(result.invalid,1);assert.equal(result.omitted,2)
})
test('signed bars have a true zero baseline and no artificial minimum magnitude',()=>{
  assert.deepEqual(sceneBarGeometry(0,100),{height:0,centerY:0,endY:0})
  const positive=sceneBarGeometry(100,100),negative=sceneBarGeometry(-100,100)
  assert.equal(positive.height,4.3);assert.equal(negative.height,positive.height)
  assert.equal(negative.centerY,-positive.centerY);assert.equal(negative.endY,-positive.endY)
  assert.ok(sceneBarGeometry(1,100).height<.1)
})
