import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biRefresh.ts',import.meta.url),'utf8')
const {createRefreshLoop}=await import('data:text/javascript;base64,'+Buffer.from(ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText).toString('base64'))
function harness(task=async()=>{},busy=()=>false){
  let now=0,next=0,calls=0;const timers=new Map()
  const clock={set(fn,ms){const id=++next;timers.set(id,{at:now+ms,fn});return id},clear(id){timers.delete(id)}}
  const loop=createRefreshLoop(async()=>{calls++;await task()},busy,clock)
  async function advance(ms){const end=now+ms;while(true){const entry=[...timers].sort((a,b)=>a[1].at-b[1].at)[0];if(!entry || entry[1].at>end)break;now=entry[1].at;timers.delete(entry[0]);entry[1].fn();await settle()}now=end}
  return {loop,advance,timers,get calls(){return calls}}
}
async function settle(){for(let i=0;i<8;i++)await Promise.resolve()}
test('disabled, invalid intervals and disposal never query',async()=>{const h=harness();for(const n of [0,1,9,3601,NaN]){h.loop.configure(n);await h.advance(50000)}assert.equal(h.calls,0);h.loop.configure(10);h.loop.dispose();await h.advance(50000);assert.equal(h.calls,0);assert.equal(h.timers.size,0)})
test('slow requests never overlap and missed intervals are not replayed',async()=>{let resolve;const h=harness(()=>new Promise(r=>resolve=r));h.loop.configure(10);await h.advance(10000);assert.equal(h.calls,1);await h.advance(100000);assert.equal(h.calls,1);assert.equal(h.timers.size,0);resolve();await settle();await h.advance(9999);assert.equal(h.calls,1);await h.advance(1);assert.equal(h.calls,2);h.loop.dispose();resolve();await settle();assert.equal(h.timers.size,0)})
test('hidden/deactivated stops scheduling and resume runs once',async()=>{const h=harness();h.loop.configure(10);h.loop.visibility(false);await h.advance(100000);assert.equal(h.calls,0);h.loop.visibility(true);await h.advance(0);assert.equal(h.calls,1);assert.equal(h.timers.size,1);h.loop.visibility(true);await h.advance(9999);assert.equal(h.calls,1);h.loop.dispose()})
test('foreground work delays automatic refresh without accumulating tasks',async()=>{let busy=true;const h=harness(async()=>{},()=>busy);h.loop.configure(10);await h.advance(60000);assert.equal(h.calls,0);assert.equal(h.timers.size,1);busy=false;await h.advance(1000);assert.equal(h.calls,1);h.loop.dispose()})
test('failure retries after interval',async()=>{let fail=true;const h=harness(async()=>{if(fail)throw Error('offline')});h.loop.configure(10);await h.advance(10000);assert.equal(h.calls,1);fail=false;await h.advance(10000);assert.equal(h.calls,2);h.loop.dispose()})
test('hiding in flight permits settlement but starts no further request',async()=>{let resolve;const h=harness(()=>new Promise(r=>resolve=r));h.loop.configure(10);await h.advance(10000);h.loop.visibility(false);resolve();await settle();await h.advance(100000);assert.equal(h.calls,1);assert.equal(h.timers.size,0);h.loop.visibility(true);await h.advance(0);assert.equal(h.calls,2);h.loop.dispose();resolve();await settle();assert.equal(h.timers.size,0)})
test('changing interval or disabling in flight does not leave a timer',async()=>{let resolve;const h=harness(()=>new Promise(r=>resolve=r));h.loop.configure(10);await h.advance(10000);h.loop.configure(0);resolve();await settle();await h.advance(100000);assert.equal(h.calls,1);assert.equal(h.timers.size,0);h.loop.dispose()})
