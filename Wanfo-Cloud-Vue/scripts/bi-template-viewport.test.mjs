import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biTemplateViewport.ts',import.meta.url),'utf8')
const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022}}).outputText
const {templateGridSize,fitTemplateViewport}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'))
test('grid preview retains native row geometry including gaps and padding',()=>{
  assert.deepEqual(templateGridSize([{y:0,h:2},{y:4,h:3}]),{width:1200,height:554})
  assert.deepEqual(templateGridSize([]),{width:1200,height:86})
})
test('small, large and hidden preview hosts fit without clipping or invalid transforms',()=>{
  assert.deepEqual(fitTemplateViewport(300,200,1200,800),{scale:.25,x:0,y:0})
  assert.deepEqual(fitTemplateViewport(1200,1000,1200,800),{scale:1,x:0,y:100})
  assert.deepEqual(fitTemplateViewport(1600,800,1200,800),{scale:1,x:200,y:0})
  for(const size of [0,-1,NaN,Infinity])assert.deepEqual(fitTemplateViewport(size,100,1200,800),{scale:0,x:0,y:0})
})
