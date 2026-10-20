import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const url=code=>'data:text/javascript;base64,'+Buffer.from(code).toString('base64')
const compile=async name=>ts.transpileModule(await fs.readFile(new URL('../src/utils/'+name+'.ts',import.meta.url),'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const chartTypes=url(await compile('biChartTypes'))
const resolveTypes=code=>code.replaceAll("'@/utils/biChartTypes'",JSON.stringify(chartTypes)).replaceAll("'./biChartTypes'",JSON.stringify(chartTypes))
const presentation=url(resolveTypes(await compile('biPresentation'))),templates=url(resolveTypes(await compile('biTemplates')))
const {exportTemplateFile,parseTemplateFile,parseLayoutTemplate,instantiateImportedTemplate,importedTemplate,TEMPLATE_MAX_BYTES,TEMPLATE_MAX_IMAGES}=await import(url(resolveTypes(await compile('biTemplateFiles')).replace("'./biPresentation'",JSON.stringify(presentation)).replace("'./biTemplates'",JSON.stringify(templates))))
const {defaultAppearance}=await import(presentation)
test('every packaged market template passes the actual frontend file parser',async()=>{
 const base='../../Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/'
 const index=JSON.parse(await fs.readFile(new URL(base+'bi/template-market/index.json',import.meta.url),'utf8'))
 for(const entry of index.templates){const raw=await fs.readFile(new URL(base+entry.resource.slice('classpath:'.length),import.meta.url),'utf8');assert.equal(parseLayoutTemplate(raw).name,entry.name)}
})
const style={showTitle:true,titleAlign:'left'},config={type:'table',dimensions:[],metrics:[{field:'secret_amount',agg:'SUM',alias:'old_sales'}],orderBys:[],limit:100,filterTree:{field:'secret_user',op:'EQ',value:'private-filter'}}
function source(){return {schemaVersion:1,name:'导出验证',kind:'screen',layout:'absolute',scene:{width:1920,height:1080,fit:'contain',refreshSeconds:10},appearance:defaultAppearance('dark'),interaction:{controls:[],links:[]},password:'private-password',payloads:{rows:['private-result']},panels:[
  {key:'original-parent',title:'容器',componentType:'container',x:20,y:120,w:900,h:800,locked:true,hidden:true,style,content:{container:{background:'#16243A',borderColor:'#38BDF8',stroke:1,radius:8,padding:12}}},
  {key:'original-child',parentKey:'original-parent',title:'指标',componentType:'kpi',x:40,y:140,w:400,h:300,locked:true,hidden:true,chartId:12345,chartConfig:config,style:{...style,metrics:[{key:'old_sales',rules:[]}]}},
  {key:'original-chart',title:'图表',componentType:'chart',x:1000,y:400,w:800,h:500,chartId:23456,chartConfig:{...config,type:'line'},style},
  {key:'original-text-a',title:'说明甲',componentType:'text',x:1000,y:100,w:400,h:120,groupKey:'original-group',style,content:{text:'原样保留的文字'}},
  {key:'original-text-b',title:'说明乙',componentType:'text',x:1450,y:100,w:400,h:120,groupKey:'original-group',style,content:{text:'其他说明'}},
  {key:'original-line',title:'分割线',componentType:'line',x:1000,y:250,w:800,h:30,style,content:{decoration:{color:'#38BDF8',stroke:2,pattern:'solid',radius:0,direction:'horizontal'}}}
]}}
const fixture=()=>exportTemplateFile(source())
function bindings(file){return Object.fromEntries(importedTemplate(file).slots.map(s=>[s.key,{chartId:77,config:{type:s.type,dimensions:s.kind==='kpi'?[]:[{field:'city',alias:'city'}],metrics:[{field:'amount',agg:'SUM',alias:'sales'}],orderBys:[],limit:100}}]))}
test('export removes bindings/results/ACL-like extras, preserves safe presentation and leaves source unchanged',()=>{
  const d=source(),before=JSON.stringify(d),file=exportTemplateFile(d),raw=JSON.stringify(file)
  assert.equal(JSON.stringify(d),before);for(const secret of ['original-parent','original-group','12345','23456','secret_amount','private-filter','private-password','private-result','old_sales','chartConfig','chartId','interaction','metrics'])assert.ok(!raw.includes(secret),secret)
  assert.equal(file.panels[1].parentKey,'p1');assert.equal(file.panels[3].groupKey,'g1');assert.equal(file.panels[4].groupKey,'g1');assert.equal(file.scene.refreshSeconds,0);assert.equal(file.panels[2].chartType,'line');assert.equal(file.panels[3].content.text,'原样保留的文字')
})
test('roundtrip reconstructs parents/groups with fresh IDs and independent bindings',()=>{
  const file=parseTemplateFile(JSON.stringify(fixture())),b=bindings(file),before=JSON.stringify(file);let n=0;const d=instantiateImportedTemplate(file,'新的实例',b,()=>`new-${++n}`),other=instantiateImportedTemplate(file,'第二实例',b,()=>`other-${++n}`)
  assert.equal(d.panels[1].parentKey,d.panels[0].key);assert.equal(d.panels[3].groupKey,d.panels[4].groupKey);assert.ok(d.panels[3].groupKey.startsWith('new-'));assert.ok(d.panels[1].hidden && d.panels[1].locked);assert.deepEqual(d.interaction,{controls:[],links:[]});assert.equal(d.panels[1].chartId,77);assert.equal(d.panels[2].chartConfig.type,'line');d.panels[1].chartConfig.metrics[0].field='edited';assert.equal(b.p2.config.metrics[0].field,'amount');assert.equal(JSON.stringify(file),before);assert.ok(d.panels.every(p=>!other.panels.some(x=>x.key===p.key)))
})
test('file size, depth, syntax, format and version are bounded',()=>{
  assert.throws(()=>parseTemplateFile('x'.repeat(TEMPLATE_MAX_BYTES+1)));assert.throws(()=>parseTemplateFile('{broken'));assert.throws(()=>parseTemplateFile('['.repeat(20)+'0'+']'.repeat(20)));for(const change of [{version:2},{format:'DataEase'}])assert.throws(()=>parseTemplateFile(JSON.stringify({...fixture(),...change})))
})
test('unknown credentials/data/executable fields and unsafe object keys reject',()=>{
  for(const change of [f=>f.password='secret',f=>f.panels[0].chartId=1,f=>f.panels[0].content.container.background='url(https://remote)',f=>f.panels[2].chartConfig=config,f=>f.panels[3].onclick='alert(1)',f=>f.appearance.background='javascript:bad']){const f=fixture();change(f);assert.throws(()=>parseTemplateFile(JSON.stringify(f)))}
  assert.throws(()=>parseTemplateFile(JSON.stringify(fixture()).replace('"version":1','"version":1,"__proto__":{"polluted":true}')));assert.equal({}.polluted,undefined)
})
test('invalid geometry, duplicate IDs, dangling/self/nested parents and layer blocks reject',()=>{
  for(const change of [f=>f.panels[0].x=-1,f=>f.panels[0].w=1920,f=>f.panels[1].key=f.panels[0].key,f=>f.panels[1].parentKey='missing',f=>f.panels[0].parentKey=f.panels[0].key,f=>f.panels.reverse(),f=>f.panels[1].x=20,f=>f.panels[1].locked=false]){const f=fixture();change(f);assert.throws(()=>parseTemplateFile(JSON.stringify(f)))}
})
test('groups and supported types are validated; remote or mismatched image payloads never load',()=>{
  for(const change of [f=>delete f.panels[4].groupKey,f=>f.panels[3].groupKey='bad key',f=>f.panels[3].componentType='image',f=>f.panels[3].content={image:'https://remote/image.png'},f=>f.panels[1].chartType='bar',f=>f.panels[0].content={text:'invalid'}]){const f=fixture();change(f);assert.throws(()=>parseTemplateFile(JSON.stringify(f)))}
})
const PIXEL='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ZkAAAAASUVORK5CYII='
function imagePanel(key,x=40){return {key,title:'标志',componentType:'image',x,y:20,w:120,h:80,locked:false,hidden:false,style,content:{image:PIXEL}}}
test('invalid packed images reject atomically; mixed dashboard kind still rejects',()=>{
  const d=source();d.panels.push({key:'img',title:'标志',componentType:'image',x:40,y:20,w:120,h:80,style,content:{image:'data:image/png;base64,private'}});const before=JSON.stringify(d);assert.throws(()=>exportTemplateFile(d),/图片/);assert.equal(JSON.stringify(d),before);assert.throws(()=>exportTemplateFile({...source(),kind:'dashboard'}))
})
test('validated png packs, remaps and instantiates without remote URLs or extra bindings',()=>{
  const d=source();d.panels.push(imagePanel('original-logo'));const before=JSON.stringify(d),file=exportTemplateFile(d),packed=file.panels.find(p=>p.componentType==='image')
  assert.equal(JSON.stringify(d),before);assert.equal(packed.content.image,PIXEL);assert.equal(packed.key,'p7')
  assert.ok(!JSON.stringify(file).includes('https://'));assert.ok(!JSON.stringify(file).includes('chartId'))
  const parsed=parseTemplateFile(JSON.stringify(file));let n=0;const created=instantiateImportedTemplate(parsed,'带图实例',bindings(parsed),()=>`img-${++n}`)
  assert.equal(created.panels.find(p=>p.componentType==='image').content.image,PIXEL)
  for(const bad of ['https://remote/image.png','data:image/svg+xml;base64,PHN2Zz4=','',PIXEL+'x']){const f=structuredClone(file);f.panels.find(p=>p.componentType==='image').content.image=bad;assert.throws(()=>parseTemplateFile(JSON.stringify(f)),/图片/)}
  const many=fixture();for(let i=0;i<TEMPLATE_MAX_IMAGES+1;i++)many.panels.push({key:'img'+i,title:'图',componentType:'image',x:20+i*140,y:20,w:120,h:80,locked:false,hidden:false,style,content:{image:PIXEL}});assert.throws(()=>parseTemplateFile(JSON.stringify(many)),/5张/)
})
function dashboardSource(){return {schemaVersion:1,name:'看板导出',kind:'dashboard',layout:'grid',appearance:defaultAppearance('light'),interaction:{controls:[],links:[]},panels:[
  {key:'kpi-a',title:'核心指标',componentType:'kpi',x:0,y:0,w:6,h:3,chartId:11,chartConfig:config,style},
  {key:'chart-a',title:'分类',componentType:'chart',x:6,y:0,w:6,h:6,chartId:22,chartConfig:{...config,type:'bar'},style},
  {key:'text-a',title:'说明',componentType:'text',x:0,y:3,w:6,h:3,style,content:{text:'保留文案'}}
]}}
test('dashboard export strips bindings and roundtrips on a 12-column grid',()=>{
  const d=dashboardSource(),before=JSON.stringify(d),file=exportTemplateFile(d),raw=JSON.stringify(file)
  assert.equal(file.format,'datacenter-dashboard-template');assert.equal(JSON.stringify(d),before)
  for(const secret of ['kpi-a','chart-a','11','22','secret_amount','chartConfig','chartId'])assert.ok(!raw.includes(secret),secret)
  const parsed=parseLayoutTemplate(JSON.stringify(file)),b=Object.fromEntries(importedTemplate(parsed).slots.map(s=>[s.key,{chartId:88,config:{type:s.type,dimensions:s.kind==='kpi'?[]:[{field:'city',alias:'city'}],metrics:[{field:'amount',agg:'SUM',alias:'sales'}],orderBys:[],limit:100}}]))
  let n=0;const created=instantiateImportedTemplate(parsed,'新看板',b,()=>`d${++n}`)
  assert.equal(created.kind,'dashboard');assert.equal(created.layout,'grid');assert.equal(created.panels[0].chartId,88);assert.equal(created.panels[2].content.text,'保留文案')
  d.panels.push({key:'img',title:'标志',componentType:'image',x:0,y:6,w:6,h:3,style,content:{image:'data:image/png;base64,xx'}});assert.throws(()=>exportTemplateFile(d),/图片/)
  const withImage=dashboardSource();withImage.panels.push({key:'logo',title:'标志',componentType:'image',x:0,y:6,w:6,h:3,style,locked:false,content:{image:PIXEL}})
  const packed=exportTemplateFile(withImage);assert.equal(packed.panels.at(-1).content.image,PIXEL)
  n=0;const round=instantiateImportedTemplate(packed,'带图看板',Object.fromEntries(importedTemplate(packed).slots.map(s=>[s.key,{chartId:88,config:{type:s.type,dimensions:s.kind==='kpi'?[]:[{field:'city',alias:'city'}],metrics:[{field:'amount',agg:'SUM',alias:'sales'}],orderBys:[],limit:100}}])),()=>`d${++n}`)
  assert.equal(round.panels.find(p=>p.componentType==='image').content.image,PIXEL)
})
test('backend nullable content fields are projected to the matching component type',()=>{
  const d=source();d.panels[3].content={text:'hello',image:null,decoration:null,container:null};const f=exportTemplateFile(d);assert.deepEqual(f.panels[3].content,{text:'hello'})
})
test('binding completeness, unknown slots, invalid KPI shape and duplicate generated IDs reject',()=>{
  const f=fixture(),b=bindings(f);delete b.p2;assert.throws(()=>instantiateImportedTemplate(f,'new',b));const extra=bindings(f);extra.unknown=extra.p2;assert.throws(()=>instantiateImportedTemplate(f,'new',extra));const bad=bindings(f);bad.p2.config.dimensions=[{field:'city'}];assert.throws(()=>instantiateImportedTemplate(f,'new',bad));assert.throws(()=>instantiateImportedTemplate(f,'new',bindings(f),()=> 'same'));assert.throws(()=>instantiateImportedTemplate(f,' ',bindings(f)))
})
test('number formats have backend-compatible limits and cannot contain controls or incompatible scaling',()=>{
  const format={mode:'number',scale:'none',decimals:2,grouping:true,fixed:true,prefix:'',suffix:'元',nullText:'—'};const f=fixture();f.panels[1].style.numberFormat=format;assert.equal(parseTemplateFile(JSON.stringify(f)).panels[1].style.numberFormat.suffix,'元')
  for(const patch of [{decimals:7},{prefix:'a'.repeat(21)},{prefix:'\n'},{mode:'percent',scale:'wan'}]){const bad=structuredClone(f);Object.assign(bad.panels[1].style.numberFormat,patch);assert.throws(()=>parseTemplateFile(JSON.stringify(bad)))}
})
