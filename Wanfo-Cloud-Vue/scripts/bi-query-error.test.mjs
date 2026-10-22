import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const src=await fs.readFile(new URL('../src/utils/biQueryError.ts',import.meta.url),'utf8')
const js=ts.transpileModule(src,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {biQueryError,queryErrorText,isBiQueryUrl}=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'))
test('query errors preserve safe type and server correlation while stripping raw data',()=>{
 const id='fb69d056-a0a0-40ea-840b-b9c562047fa5';const e=biQueryError({code:503,errorType:'DATASOURCE_UNAVAILABLE',requestId:id,msg:'jdbc:secret password=secret'});assert.equal(e.kind,'DATASOURCE_UNAVAILABLE');assert.match(queryErrorText(e),new RegExp(id));assert.doesNotMatch(queryErrorText(e),/jdbc|secret/)
})
test('transport timeouts, network, permissions, stale version and invalid config differ',()=>{
 for(const [input,kind] of [[{code:'ECONNABORTED'},'QUERY_TIMEOUT'],[{code:'ETIMEDOUT'},'QUERY_TIMEOUT'],[{code:'ERR_NETWORK'},'NETWORK_UNAVAILABLE'],[{response:{status:403}},'ACCESS_DENIED'],[{code:409},'VERSION_CHANGED'],[{code:400},'QUERY_INVALID'],[{code:401},'SESSION_EXPIRED']])assert.equal(biQueryError(input).kind,kind)
})
test('unknown payload and malicious identifiers never become user-facing text',()=>{
 for(const value of [null,'SQL secret',{errorType:'toString',msg:'SQL secret',requestId:'<img onerror=secret>'},{response:{data:{errorType:'UNKNOWN',requestId:'password=secret'}}}]){assert.equal(biQueryError(value).kind,'QUERY_FAILED');assert.doesNotMatch(queryErrorText(value),/secret|<img|SQL/)}
})
test('only exact BI query routes use structured handling',()=>{
 for(const url of ['/bi/chart/data','/bi/chart/map-preview','/bi/chart/saved/1/data','/bi/dashboard/1/panels/a/data','/bi/dashboard/1/controls/a/options'])assert.ok(isBiQueryUrl(url));for(const url of [undefined,'/bi/chart/saved','/system/user','/bi/chart/data/extra','https://remote/bi/chart/data'])assert.equal(isBiQueryUrl(url),false)
})
