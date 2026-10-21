import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biTemplates.ts',import.meta.url),'utf8')
const {screenTemplates,dashboardTemplates,getScreenTemplate,getDashboardTemplate,instantiateScreenTemplate,instantiateDashboardTemplate}=await import('data:text/javascript;base64,'+Buffer.from(ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText).toString('base64'))
const config={type:'table',dimensions:[],metrics:[{field:'amount',agg:'SUM',alias:'sales'}],orderBys:[],limit:100}
function bindings(id){return Object.fromEntries(getScreenTemplate(id).slots.map(slot=>[slot.key,{source:'chart',chartId:42,config:structuredClone(slot.kind==='kpi'?config:{...config,type:slot.type,dimensions:[{field:'city',alias:'city'}]})}]))}

test('template instantiation preserves calendar grouping without sharing source state',()=>{
  const id=screenTemplates[0].id,b=bindings(id),slot=getScreenTemplate(id).slots.find(s=>s.kind==='chart')
  b[slot.key].config.dimensions=[{field:'event_date',alias:'period',timeGrain:'month',timeFill:'zero'}]
  let key=0;const result=instantiateScreenTemplate(id,'时间模板',b,()=>`t${++key}`)
  const panel=result.panels.find(p=>p.chartConfig?.dimensions[0]?.field==='event_date')
  assert.equal(panel.chartConfig.dimensions[0].timeGrain,'month');assert.equal(panel.chartConfig.dimensions[0].timeFill,'zero')
  panel.chartConfig.dimensions[0].timeGrain='year';assert.equal(b[slot.key].config.dimensions[0].timeGrain,'month')
})
test('curated templates contain layout slots and no business IDs or data',()=>{
  assert.equal(screenTemplates.length,2)
  for(const template of screenTemplates){assert.equal(template.schemaVersion,1);assert.equal(new Set(template.slots.map(s=>s.key)).size,4);assert.ok(!/chartId|datasetId|password|payload|rows|grant|assetId/.test(JSON.stringify(template)));for(const s of template.slots){assert.ok(s.x>=0 && s.y>=0 && s.x+s.w<=1920 && s.y+s.h<=1080)}}
})
test('unknown IDs and protocol versions reject; returned catalog copies are independent',()=>{
  assert.throws(()=>getScreenTemplate('unknown'));assert.throws(()=>getScreenTemplate('business-overview',2));const t=getScreenTemplate('business-overview');t.slots[0].w=0;assert.equal(getScreenTemplate(t.id).slots[0].w,900)
})
test('instantiation creates fresh component IDs, resets interactions and remains independent',()=>{
  for(const t of screenTemplates){let serial=0;const b=bindings(t.id),before=JSON.stringify(b),a=instantiateScreenTemplate(t.id,' 我的大屏 ',b,()=>`a${++serial}`),other=instantiateScreenTemplate(t.id,'另一个实例',b,()=>`b${++serial}`);assert.equal(a.name,'我的大屏');assert.equal(a.panels.length,5);assert.equal(a.scene.refreshSeconds,0);assert.deepEqual(a.interaction,{controls:[],links:[]});assert.ok(a.panels.every(p=>!other.panels.some(o=>o.key===p.key)));a.panels[1].chartConfig.metrics[0].field='changed';assert.equal(JSON.stringify(b),before);assert.equal(other.panels[1].chartConfig.metrics[0].field,'amount')}
})
test('missing slots, unknown slots, invalid chart IDs and blank names cannot instantiate',()=>{
  const id='business-overview',b=bindings(id);delete b['metric-a'];assert.throws(()=>instantiateScreenTemplate(id,'new',b));const extra=bindings(id);extra.unknown={source:'chart',chartId:42,config};assert.throws(()=>instantiateScreenTemplate(id,'new',extra));for(const n of [0,-1,1.5,NaN]){const bad=bindings(id);bad['metric-a'].chartId=n;assert.throws(()=>instantiateScreenTemplate(id,'new',bad))}const missingChart=bindings(id);missingChart['metric-a']={source:'dataset',datasetId:9,config};assert.throws(()=>instantiateScreenTemplate(id,'new',missingChart));assert.throws(()=>instantiateScreenTemplate(id,' ',bindings(id)));assert.throws(()=>instantiateScreenTemplate(id,'a'.repeat(201),bindings(id)))
})
test('KPI bindings reject dimensions, multiple metrics and non-table configuration',()=>{
  for(const patch of [{dimensions:[{field:'city'}]},{metrics:[...config.metrics,...config.metrics]},{type:'bar'},{metrics:[]}]){const b=bindings('business-overview');Object.assign(b['metric-a'].config,patch);assert.throws(()=>instantiateScreenTemplate('business-overview','new',b))}
})
test('duplicate generated keys cannot create an ambiguous document',()=>{
  assert.throws(()=>instantiateScreenTemplate('business-overview','new',bindings('business-overview'),()=> 'same'))
})
function dashboardBindings(id){return Object.fromEntries(getDashboardTemplate(id).slots.map(slot=>[slot.key,{source:'chart',chartId:42,config:structuredClone(slot.kind==='kpi'?config:{...config,type:slot.type,dimensions:[{field:'city',alias:'city'}]})}]))}
test('curated dashboard templates use 12-column grid slots without business IDs',()=>{
  assert.equal(dashboardTemplates.length,2)
  for(const template of dashboardTemplates){assert.equal(template.kind,'dashboard');assert.equal(new Set(template.slots.map(s=>s.key)).size,4);assert.ok(!/chartId|datasetId|password|payload|rows|grant|assetId/.test(JSON.stringify(template)));for(const s of template.slots){assert.ok(s.x>=0 && s.y>=0 && s.w>=2 && s.h>=2 && s.x+s.w<=12 && s.y+s.h<=120)}}
})
test('dashboard instantiation is independent and rejects incomplete KPI bindings',()=>{
  let serial=0;const b=dashboardBindings('grid-business-overview'),before=JSON.stringify(b),a=instantiateDashboardTemplate('grid-business-overview',' 我的看板 ',b,()=>`a${++serial}`)
  assert.equal(a.kind,'dashboard');assert.equal(a.layout,'grid');assert.equal(a.scene,undefined);assert.equal(a.panels.length,4);assert.deepEqual(a.interaction,{controls:[],links:[]});a.panels[0].chartConfig.metrics[0].field='changed';assert.equal(JSON.stringify(b),before)
  const bad=dashboardBindings('grid-business-overview');bad['metric-a'].config.dimensions=[{field:'city'}];assert.throws(()=>instantiateDashboardTemplate('grid-business-overview','new',bad));assert.throws(()=>getDashboardTemplate('business-overview'))
})
