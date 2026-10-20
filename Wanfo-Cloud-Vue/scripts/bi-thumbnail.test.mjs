import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biThumbnail.ts',import.meta.url),'utf8')
const {thumbnailTask,notifyBiSaved,BI_SAVED_EVENT,versionedThumbnail}=await import('data:text/javascript;base64,'+Buffer.from(ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText).toString('base64'))
const settle=async()=>{for(let i=0;i<12;i++)await Promise.resolve()}

test('rapid pagination never exceeds two concurrent thumbnail jobs and skips obsolete cards',async()=>{
  let active=0,peak=0,current=true;const release=[],seen=[]
  const work=Array.from({length:10},(_,i)=>thumbnailTask(async()=>{seen.push(i);peak=Math.max(peak,++active);await new Promise(r=>release.push(r));active--;return i},()=>i<2 || current))
  assert.deepEqual(seen,[0,1]);current=false;release.splice(0).forEach(r=>r());await Promise.all(work)
  assert.equal(peak,2);assert.equal(active,0);assert.deepEqual(seen,[0,1])
})
test('failure frees queue slots, subsequent resources can still generate previews',async()=>{
  let finish;const slow=thumbnailTask(()=>new Promise(r=>finish=r),()=>true)
  const failed=thumbnailTask(async()=>{throw Error('denied')},()=>true)
  const next=thumbnailTask(async()=>42,()=>true)
  await assert.rejects(failed,/denied/);await settle();assert.equal(await next,42);finish(1);await slow
})
test('save notification identifies resource namespace and id without carrying image or business data',()=>{
  globalThis.window=new EventTarget();const events=[]
  window.addEventListener(BI_SAVED_EVENT,e=>events.push(e.detail))
  notifyBiSaved('chart',107);notifyBiSaved('dashboard',107)
  assert.deepEqual(events,[{kind:'chart',id:107},{kind:'dashboard',id:107}])
  delete globalThis.window
})

test('save during query regenerates from the new saved version instead of mixing definition and data',async()=>{
  let version=1,queries=0
  const result=await versionedThumbnail(async()=>({version,definition:'chart-'+version}),async()=>{queries++;if(queries===1)version=2;return 'data-'+version},()=>true)
  assert.deepEqual(result,{version:2,definition:'chart-2',data:'data-2'});assert.equal(queries,2)
})
test('continuous edits fail closed and cancelled cards stop querying',async()=>{
  let version=0
  await assert.rejects(versionedThumbnail(async()=>({version:version++,definition:'x'}),async()=>[],()=>true),/保存版本/)
  let current=true,queries=0
  assert.equal(await versionedThumbnail(async()=>{current=false;return {version:1,definition:'x'}},async()=>queries++,()=>current),undefined)
  assert.equal(queries,0)
})
