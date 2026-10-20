import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biPanelQueue.ts',import.meta.url),'utf8')
const {runPanelQueue}=await import('data:text/javascript;base64,'+Buffer.from(ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText).toString('base64'))
const settle=async()=>{for(let i=0;i<8;i++)await Promise.resolve()}
test('twenty slow panels are dispatched once, at most two concurrently',async()=>{
  let active=0,peak=0;const pending=[],seen=[]
  const round=runPanelQueue(Array.from({length:20},(_,i)=>i),async key=>{seen.push(key);peak=Math.max(peak,++active);await new Promise(r=>pending.push(r));active--})
  assert.equal(seen.length,2)
  for(let i=0;i<20;i++){assert.ok(pending.length);pending.shift()();await settle()}
  await round;assert.equal(peak,2);assert.equal(active,0);assert.deepEqual(seen,Array.from({length:20},(_,i)=>i))
})
test('invalidating a round stops queued work after in-flight requests settle',async()=>{
  let current=true;const pending=[],seen=[]
  const round=runPanelQueue([1,2,3,4],async key=>{seen.push(key);await new Promise(r=>pending.push(r))},()=>current)
  current=false;pending.forEach(r=>r());await round;assert.deepEqual(seen,[1,2])
})
test('partial failures remain isolated and retry dispatches only failed panels',async()=>{
  const payloads={},errors={},calls=[];let fail=true
  async function task(key){calls.push(key);delete errors[key];try{if(fail && key%2===0)throw Error('offline');payloads[key]=key*10}catch{delete payloads[key];errors[key]='unavailable'}}
  await runPanelQueue([1,2,3,4],task);assert.deepEqual(payloads,{1:10,3:30});assert.deepEqual(Object.keys(errors),['2','4'])
  fail=false;await runPanelQueue(Object.keys(errors).map(Number),task);assert.deepEqual(calls,[1,2,3,4,2,4]);assert.deepEqual(errors,{});assert.equal(payloads[1],10)
})
test('empty and already obsolete rounds do not request data',async()=>{
  let calls=0;await runPanelQueue([],async()=>calls++);await runPanelQueue([1,2],async()=>calls++,()=>false);assert.equal(calls,0)
})
