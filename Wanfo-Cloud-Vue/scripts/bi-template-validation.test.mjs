import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biTemplateValidation.ts',import.meta.url),'utf8')
const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022}}).outputText
const {templateValidationReport:report,templateValidationError:error}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'))
const config={metrics:[],limit:2}
const result=rows=>({rows,columns:['amount'],rowCount:rows.length,costMs:1})
test('empty results and null aggregates are warnings, while zero is valid data',()=>{
  assert.equal(report(result([]),config).state,'warning')
  assert.match(report(result([{amount:null}]),config,['amount']).message,/空值/)
  assert.equal(report(result([{amount:0}]),config,['amount']).state,'success')
})
test('limits are potential truncation warnings and missing payloads never count as success',()=>{
  assert.match(report(result([{amount:1},{amount:2}]),config).message,/可能存在未展示/)
  assert.equal(report(result([{amount:1}]),config).state,'success')
  assert.throws(()=>report(undefined,config))
})
test('errors distinguish access, expiry and timeouts without disclosing raw backend text',()=>{
  assert.match(error({response:{status:403}}),/权限/)
  assert.match(error({response:{status:401}}),/登录/)
  assert.match(error({code:'ECONNABORTED'}),/超时/)
  assert.match(error({code:'ERR_NETWORK'}),/网络/)
  assert.ok(!error({message:'SELECT secret FROM credentials'}).includes('secret'))
})
