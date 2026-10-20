import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
async function api(file){
  let source=await fs.readFile(new URL('../src/api/bi/'+file,import.meta.url),'utf8')
  source=source.replace("import request from '@/utils/request'",'const request=(options:any)=>globalThis.thumbnailRequest(options)')
    .replace("import { notifyBiSaved } from '@/utils/biThumbnail'",'const notifyBiSaved=(kind:string,id:number)=>globalThis.thumbnailEvents.push({kind,id})')
  return import('data:text/javascript;base64,'+Buffer.from(ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText).toString('base64'))
}
const charts=await api('chart.ts'),boards=await api('dashboard.ts')
test('chart, grid dashboard and absolute screen save only invalidate their own successful saved resource',async()=>{
  globalThis.thumbnailEvents=[];const calls=[]
  globalThis.thumbnailRequest=async request=>{calls.push(request);return {code:200,data:{id:71,version:4}}}
  const chart={schemaVersion:1,name:'Chart',type:'bar',query:{datasetId:1}}
  const result=await charts.saveChart(chart,71,3)
  assert.deepEqual(result.data,{id:71,version:4});assert.equal(calls[0].method,'put');assert.equal(calls[0].data.version,3)
  for(const kind of ['dashboard','screen']){
    await boards.saveDashboard({schemaVersion:1,name:kind,kind,layout:kind==='screen'?'absolute':'grid',panels:[]},71,3)
  }
  assert.deepEqual(globalThis.thumbnailEvents,[{kind:'chart',id:71},{kind:'dashboard',id:71},{kind:'dashboard',id:71}])
  globalThis.thumbnailRequest=async()=>{throw Error('version conflict')}
  await assert.rejects(charts.saveChart(chart,71,3),/version conflict/)
  await assert.rejects(boards.saveDashboard({schemaVersion:1,name:'draft',panels:[]},71,3),/version conflict/)
  assert.equal(globalThis.thumbnailEvents.length,3)
  globalThis.thumbnailRequest=async()=>({code:200,data:{id:901,version:0}})
  await charts.saveChart(chart)
  assert.deepEqual(globalThis.thumbnailEvents.at(-1),{kind:'chart',id:901})
  delete globalThis.thumbnailEvents;delete globalThis.thumbnailRequest
})
