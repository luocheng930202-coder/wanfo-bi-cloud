import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biPresentation.ts',import.meta.url),'utf8')
const {normalizeDecoration,normalizeDashboard}=await import('data:text/javascript;base64,'+Buffer.from(ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText).toString('base64'))
test('bounded defaults and supported patterns/directions',()=>{assert.equal(normalizeDecoration().stroke,2);for(const pattern of ['solid','dashed','dotted'])for(const direction of ['horizontal','vertical'])assert.equal(normalizeDecoration({pattern,direction,stroke:12,radius:80}).pattern,pattern)})
test('executable colors, unsupported styles and noninteger bounds reject',()=>{for(const value of [{color:'url(x)'},{color:'#FFF'},{stroke:0},{stroke:13},{stroke:1.5},{radius:-1},{radius:81},{pattern:'double'},{direction:'rotate'}])assert.throws(()=>normalizeDecoration(value))})
test('screen accepts decorations, grid and mixed content reject',()=>{const panel={key:'frame',title:'Frame',x:0,y:0,w:300,h:200,componentType:'border',content:{decoration:normalizeDecoration()}},doc={schemaVersion:1,name:'Screen',kind:'screen',layout:'absolute',scene:{width:640,height:480,fit:'contain'},panels:[panel]};assert.equal(normalizeDashboard(doc).panels[0].content.decoration.color,'#38BDF8');assert.throws(()=>normalizeDashboard({...doc,kind:'dashboard',layout:'grid',scene:undefined}));assert.throws(()=>normalizeDashboard({...doc,panels:[{...panel,content:{text:'bad'}}]}))})
