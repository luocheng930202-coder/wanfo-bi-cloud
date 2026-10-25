import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'

let source=await fs.readFile(new URL('../src/utils/biEchartsPreview.ts',import.meta.url),'utf8')
source=source.split('\n').filter(line=>!line.startsWith('import ')).join('\n')
const compiled=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {hierarchyData}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))

test('hierarchy builder aggregates values at every ordered path depth',()=>{
  const dimensions=['region','city','store'].map(field=>({field}))
  const rows=[{region:'East',city:'A',store:'One',amount:4},{region:'East',city:'A',store:'Two',amount:6},{region:'East',city:'B',store:'Three',amount:5}]
  const data=hierarchyData(rows,dimensions,'amount',(row,key)=>Number(row[key]))
  assert.equal(data[0].value,15)
  assert.equal(data[0].children[0].value,10)
  assert.equal(data[0].children[0].children[1].value,6)
  assert.equal(data[0].children[1].children[0].name,'Three')
})
