import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const src=await fs.readFile(new URL('../src/utils/biUiTheme.ts',import.meta.url),'utf8')
const js=ts.transpileModule(src,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const theme=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'))
test('four themes accept supported values and migrate legacy preferences safely',()=>{assert.deepEqual(theme.BI_UI_THEMES,['silver','violet','jade','ocean']);for(const value of theme.BI_UI_THEMES)assert.equal(theme.normalizeBiUiTheme(value),value);for(const [old,next] of [['restrained','silver'],['saas','silver'],['ops','ocean']])assert.equal(theme.normalizeBiUiTheme(old),next);for(const value of [null,undefined,{},'toString','__proto__','bad'])assert.equal(theme.normalizeBiUiTheme(value),'silver')})
test('selection persists, notifies and releases listeners; blocked storage is safe',()=>{const entries=new Map();globalThis.localStorage={getItem:k=>entries.get(k),setItem:(k,v)=>entries.set(k,v)};const calls=[];const off=theme.subscribeBiUiTheme(v=>calls.push(v));theme.setBiUiTheme('ocean');assert.equal(theme.getBiUiTheme(),'ocean');assert.deepEqual(calls,['ocean']);off();theme.setBiUiTheme('jade');assert.deepEqual(calls,['ocean']);globalThis.localStorage={getItem(){throw Error('blocked')},setItem(){throw Error('blocked')}};assert.equal(theme.getBiUiTheme(),'silver');assert.doesNotThrow(()=>theme.setBiUiTheme('violet'));delete globalThis.localStorage})
