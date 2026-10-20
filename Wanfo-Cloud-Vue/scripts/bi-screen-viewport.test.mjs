import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biScreenViewport.ts',import.meta.url),'utf8')
const {rulerTicks,guidePosition,snapAxis}=await import('data:text/javascript;base64,'+Buffer.from(ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText).toString('base64'))
test('ruler chooses readable ticks at zoom extremes without unbounded DOM',()=>{for(const scale of [.02,.25,.5,1,2,10]){const ticks=rulerTicks(8192,scale);assert.equal(ticks[0],0);assert.ok(ticks.length<=1025);if(ticks.length>1)assert.ok((ticks[1]-ticks[0])*scale>=80);assert.ok(ticks.at(-1)<=8192)}})
test('guide pointer coordinates account for viewport origin, zoom and bounds',()=>{assert.equal(guidePosition(300,100,.5,1000),400);assert.equal(guidePosition(300,200,2,1000),50);assert.equal(guidePosition(0,100,.5,1000),0);assert.equal(guidePosition(2000,100,.5,1000),1000)})
test('move snaps nearest leading, center or trailing edge',()=>{assert.equal(snapAxis(97,50,500,[100],6),100);assert.equal(snapAxis(72,50,500,[100],6),75);assert.equal(snapAxis(47,50,500,[100],6),50);assert.equal(snapAxis(97,50,500,[100,98],6),98)})
test('snap tolerance is measured in screen pixels at each zoom',()=>{assert.equal(snapAxis(90,20,500,[100],6/.5),90);assert.equal(snapAxis(89,100,500,[100],6/.5),100);assert.equal(snapAxis(89,100,500,[100],6/2),89)})
test('resize snaps only far edge and respects minimum and scene bounds',()=>{assert.equal(snapAxis(20,77,500,[100],6,true),80);assert.equal(snapAxis(20,77,500,[20],6,true),77);assert.equal(snapAxis(480,20,500,[501],6,true),20);assert.equal(snapAxis(0,20,500,[18],6,true),20)})
test('invalid guides ignored and move cannot overflow',()=>{assert.equal(snapAxis(0,100,500,[-1,NaN,501,2.5],6),0);assert.equal(snapAxis(0,100,500,[99],6),0);assert.equal(snapAxis(400,100,500,[401],6),400)})
