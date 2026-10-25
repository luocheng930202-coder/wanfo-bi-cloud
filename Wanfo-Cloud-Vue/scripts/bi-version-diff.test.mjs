import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biVersionDiff.ts',import.meta.url),'utf8')
const code=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {versionDifferences:diff}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'))
test('version comparison reports changes to nested panel positions and removed fields',()=>{
  const result=diff({name:'Old',panels:[{x:1,title:'A'}]},{name:'New',panels:[{x:2}]})
  assert.deepEqual(result.map(r=>r.path),['name','panels.0.x','panels.0.title'])
  assert.equal(result[2].after,'（不存在）')
})
test('comparison does not flag object key order and bounds large textual and structural changes',()=>{
  assert.deepEqual(diff({a:1,b:2},{b:2,a:1}),[])
  assert.ok(diff({a:'x'.repeat(10000)},{a:'other'})[0].before.length<=180)
  assert.equal(diff(Array(300).fill(1),Array(300).fill(2)).length,200)
})
