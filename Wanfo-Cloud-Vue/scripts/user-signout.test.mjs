import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=(await fs.readFile(new URL('../src/store/modules/user.ts',import.meta.url),'utf8')).split('\n').filter(line=>!line.startsWith('import ')).join('\n')
const compiled=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText.replace('export default useUserStore;','return useUserStore;')
test('sign-out clears local identity even when an expired credential cannot be revoked remotely',async()=>{
  let removed=0
  const options=new Function('defineStore','getToken','removeToken','logout',compiled)((id,options)=>options,()=>undefined,()=>removed++,async()=>{throw Error('expired')})
  const state={token:'test-only',roles:['admin'],permissions:['*:*:*']}
  await options.actions.logOut.call(state)
  assert.equal(state.token,'');assert.deepEqual(state.roles,[]);assert.deepEqual(state.permissions,[]);assert.equal(removed,1)
})
test('successful sign-out still attempts remote revocation and clears local identity',async()=>{
  let revoked=0,removed=0
  const options=new Function('defineStore','getToken','removeToken','logout',compiled)((id,options)=>options,()=>undefined,()=>removed++,async()=>{revoked++})
  const state={token:'test-only',roles:[],permissions:[]}
  await options.actions.logOut.call(state)
  assert.equal(revoked,1);assert.equal(removed,1);assert.equal(state.token,'')
})
