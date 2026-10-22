import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biQueryRun.ts',import.meta.url),'utf8')
const js=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {createQueryRun,queryWaitMessage}=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'))
test('one active query rejects duplicate starts but allows retry after completion',()=>{
 const run=createQueryRun(),first=run.begin();assert.ok(first);assert.equal(run.begin(),undefined);assert.ok(run.isCurrent(first));assert.ok(run.finish(first));assert.equal(run.isCurrent(first),false);assert.ok(run.begin())
})
test('cancel aborts browser request; late finish cannot unlock or overwrite retry',()=>{
 const run=createQueryRun(),first=run.begin();run.cancel();assert.ok(first.signal.aborted);const second=run.begin();assert.ok(second);assert.equal(run.finish(first),false);assert.equal(run.isCurrent(first),false);assert.ok(run.isCurrent(second));assert.equal(run.begin(),undefined);assert.ok(run.finish(second));run.cancel()
})
test('external abort allows a new run without letting old completion release it',()=>{
 const run=createQueryRun(),first=run.begin();first.abort();const second=run.begin();assert.ok(second);assert.equal(run.finish(first),false);assert.ok(run.isCurrent(second))
})
test('slow query feedback starts at five seconds and uses bounded whole seconds',()=>{
 assert.match(queryWaitMessage(-10),/0 秒/);assert.doesNotMatch(queryWaitMessage(4999),/较长/);assert.match(queryWaitMessage(5000),/较长.*5 秒/);assert.match(queryWaitMessage(12800),/12 秒/)
})
