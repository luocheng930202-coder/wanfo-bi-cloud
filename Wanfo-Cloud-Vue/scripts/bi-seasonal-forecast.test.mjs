import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'

let source=await fs.readFile(new URL('../src/utils/biEchartsPreview.ts',import.meta.url),'utf8')
source=source.split('\n').filter(line=>!line.startsWith('import ')).join('\n')
const compiled=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {seasonalForecast}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))

test('seasonal naive forecast repeats the last seasonal cycle and builds empirical bounds',()=>{
  const result=seasonalForecast([10,20,11,23,14,26,18,32,20,34],2,4,'95')
  assert.deepEqual(result.forecast,[20,34,20,34])
  assert.deepEqual(result.lower,[14,28,14,28])
  assert.deepEqual(result.upper,[26,40,26,40])
  assert.equal(result.residualCount,8)
})

test('forecast refuses short or incomplete time series',()=>{
  assert.throws(()=>seasonalForecast([1,2,3],2,1,'95'),/至少需要/)
  assert.throws(()=>seasonalForecast([1,2,3,4,5,6,7,8,null],2,1,'80'),/存在空值/)
})
