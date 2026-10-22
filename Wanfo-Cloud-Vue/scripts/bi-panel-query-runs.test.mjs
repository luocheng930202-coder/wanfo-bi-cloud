import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biPanelQueryRuns.ts',import.meta.url),'utf8')
const js=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {createPanelQueryRuns}=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'))
test('panels run independently; duplicate starts rejected and elapsed times isolated',()=>{
 const r=createPanelQueryRuns(),a=r.begin('a',100),b=r.begin('b',800);assert.ok(a&&b);assert.equal(r.begin('a'),undefined);assert.equal(r.elapsed('a',1500),1400);assert.equal(r.elapsed('b',1500),700);assert.equal(r.size,2);assert.equal(r.elapsed('missing'),0)
})
test('stop affects one panel; retry rejects late response and completion from old request',()=>{
 const r=createPanelQueryRuns(),a=r.begin('a'),b=r.begin('b');assert.ok(r.cancel('a'));assert.ok(a.signal.aborted);assert.equal(b.signal.aborted,false);const retry=r.begin('a');assert.equal(r.current('a',a),false);assert.equal(r.finish('a',a),false);assert.ok(r.current('a',retry));assert.ok(r.current('b',b));assert.equal(r.begin('a'),undefined);assert.equal(r.cancel('missing'),false)
})
test('changing scope cancels all requests; late finally cannot release a new scope',()=>{
 const r=createPanelQueryRuns(),a=r.begin('a'),b=r.begin('b');r.cancelAll();assert.ok(a.signal.aborted&&b.signal.aborted);assert.equal(r.size,0);const next=r.begin('a');assert.equal(r.finish('a',a),false);assert.ok(r.current('a',next));assert.ok(r.finish('a',next));assert.equal(r.size,0)
})

test('canceling one running panel frees its queue slot without interrupting siblings',async()=>{
 const queueSource=await fs.readFile(new URL('../src/utils/biPanelQueue.ts',import.meta.url),'utf8')
 const queueJs=ts.transpileModule(queueSource,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
 const {runPanelQueue}=await import('data:text/javascript;base64,'+Buffer.from(queueJs).toString('base64'))
 const r=createPanelQueryRuns(),release={},accepted=[];let peak=0
 const work=runPanelQueue(['a','b','c'],async key=>{
  const controller=r.begin(key);peak=Math.max(peak,r.size)
  await new Promise(resolve=>{release[key]=resolve;controller.signal.addEventListener('abort',()=>resolve(),{once:true})})
  if(r.current(key,controller))accepted.push(key)
  r.finish(key,controller)
 })
 assert.deepEqual(Object.keys(release),['a','b']);r.cancel('a');await new Promise(resolve=>setImmediate(resolve));assert.ok(release.c);assert.equal(r.size,2);release.b();release.c();await work;assert.deepEqual(accepted.sort(),['b','c']);assert.equal(peak,2);assert.equal(r.size,0)
})
