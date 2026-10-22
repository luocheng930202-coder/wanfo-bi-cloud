import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=(await fs.readFile(new URL('../src/utils/biQueryCancellation.ts',import.meta.url),'utf8')).split('\n').filter(l=>!l.startsWith('import ')).join('\n')
const js=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {createQueryCancellation}=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'))
const settle=()=>new Promise(resolve=>setImmediate(resolve))
test('only abort sends a cancel, exactly once for one signal reused across reads',async()=>{
 const sent=[],c=createQueryCancellation(async key=>sent.push(key),()=> 'ticket-a'),signal=new AbortController();assert.deepEqual(c.headers(),{});assert.equal(c.headers(signal.signal)['X-BI-Query-Key'],'ticket-a');c.headers(signal.signal);await settle();assert.deepEqual(sent,[]);signal.abort();signal.abort();await settle();assert.deepEqual(sent,['ticket-a'])
})
test('retry has an independent key and cancelling old request cannot cancel retry',async()=>{
 const sent=[];let id=0;const c=createQueryCancellation(async key=>sent.push(key),()=>String(++id)),a=new AbortController(),b=new AbortController();assert.notDeepEqual(c.headers(a.signal),c.headers(b.signal));a.abort();await settle();assert.deepEqual(sent,['1']);assert.equal(b.signal.aborted,false)
})
test('cancel-before-send and failed cancellation do not leak rejected promises',async()=>{
 let called=0;const c=createQueryCancellation(async()=>{called++;throw new Error('offline')},()=> 'ticket'),controller=new AbortController();controller.abort();c.headers(controller.signal);c.headers(controller.signal);await settle();assert.equal(called,1)
})
