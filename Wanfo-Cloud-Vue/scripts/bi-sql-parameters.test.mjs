import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biSqlParameters.ts',import.meta.url),'utf8')
const code=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {discoverSqlParameters,syncSqlParameters,parameterLines}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'))
test('discovers quoted and multi parameters once, ignoring external strings and comments',()=>{
 const sql="select '$DE_PARAM{ignore=$[X]}' from t where $DE_PARAM{name=substring('$[P]',1,5)} and $DE_PARAM{name in ($[Q]) or name='$[P]'} -- $DE_PARAM{x=$[Z]}"
 assert.deepEqual(discoverSqlParameters(sql),['P','Q']);assert.equal(syncSqlParameters(sql,[])[1].multiple,true)
})
test('retains explicit settings and removes definitions no longer referenced',()=>{
 const configured={name:'P',label:'Name',type:'TEXT',multiple:false,required:true,defaultValues:['a'],defaultScope:'EDIT'}
 assert.deepEqual(syncSqlParameters('select * from t where $DE_PARAM{name=$[P]}',[configured,{name:'OLD'}]),[configured])
})
test('handles literal braces and rejects malformed names or unfinished conditions',()=>{
 assert.deepEqual(discoverSqlParameters("select * from t where $DE_PARAM{name='a}b' and id=$[P]}"),['P'])
 for(const sql of ['where $DE_PARAM{x=$[]}','where $DE_PARAM{x=$[P]','where $DE_PARAM{a={ $[P] }}'])assert.throws(()=>discoverSqlParameters(sql))
})
test('multi input preserves quotes and whitespace without SQL syntax assembly',()=>{
 assert.deepEqual(parameterLines("O'Reilly\n north \n\n"),["O'Reilly",' north '])
})
