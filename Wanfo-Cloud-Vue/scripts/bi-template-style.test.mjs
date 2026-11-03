import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
Error.stackTraceLimit=0
const url=code=>'data:text/javascript;base64,'+Buffer.from(code).toString('base64')
const compile=async name=>ts.transpileModule(await fs.readFile(new URL('../src/utils/'+name+'.ts',import.meta.url),'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const imageData=url(await compile('biImageDataUrl'))
const chartTypes=url(await compile('biChartTypes'))
const resolve=code=>code.replaceAll("'@/utils/biChartTypes'",JSON.stringify(chartTypes)).replaceAll("'./biChartTypes'",JSON.stringify(chartTypes))
const presentation=url(resolve(await compile('biPresentation'))),templates=url(resolve(await compile('biTemplates')))
const files=url(resolve(await compile('biTemplateFiles')).replace("'./biPresentation'",JSON.stringify(presentation)).replace("'./biTemplates'",JSON.stringify(templates)).replace("'./biImageDataUrl'",JSON.stringify(imageData)))
const {serializeTemplateStyle,parseTemplateStyleDraft}=await import(url((await compile('biTemplateStyleDraft')).replace("'./biTemplateFiles'",JSON.stringify(files))))
const load=async name=>JSON.parse(await fs.readFile(new URL('../../Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/bi/template-market/'+name+'.json',import.meta.url),'utf8'))

test('hologram theme and zero-to-full intensity roundtrip without changing bindings or legacy themes',async()=>{
 const {hologramAppearance,resolveHologram,presentationVars,defaultAppearance}=await import(presentation)
 const file=await load('market-screen-school-canteen-2d')
 for(const intensity of [0,55,100]){
  file.appearance={...hologramAppearance(),glowIntensity:intensity}
  const draft=parseTemplateStyleDraft(serializeTemplateStyle(file))
  assert.equal(draft.appearance.visualPreset,'hologram-ice-v1');assert.equal(draft.appearance.glowIntensity,intensity)
  assert.deepEqual(draft.panels,[])
  const vars=presentationVars(draft.appearance)
  assert.equal(vars['--bi-number-glow']==='none',intensity===0)
 }
 for(const invalid of [-1,101,1.5,'55']){file.appearance.glowIntensity=invalid;assert.throws(()=>serializeTemplateStyle(file))}
 assert.equal(resolveHologram(defaultAppearance()).strength,0)
 assert.equal(presentationVars(defaultAppearance())['--bi-panel-shadow'],'none')
})

test('component typography and transparent surface survive template roundtrip with bounded values',async()=>{
 const {parseLayoutTemplate}=await import(files)
 const file=await load('market-screen-school-canteen-2d')
 file.panels[0].style={showTitle:false,titleAlign:'left',fontSize:34,textColor:'#DDF5FF',transparent:true}
 assert.deepEqual(parseLayoutTemplate(JSON.stringify(file)).panels[0].style,file.panels[0].style)
 for(const patch of [{fontSize:97},{fontSize:9},{textColor:'url(unsafe)'},{transparent:'true'}]){
  const invalid=structuredClone(file);Object.assign(invalid.panels[0].style,patch)
  assert.throws(()=>parseLayoutTemplate(JSON.stringify(invalid)))
 }
 const {panelSurfaceVars}=await import(presentation)
 assert.deepEqual(panelSurfaceVars(),{})
 assert.equal(panelSurfaceVars({transparent:true}).background,'transparent')
})

test('visual preset survives style-only creation and rejects arbitrary styles',async()=>{
 const file=await load('market-screen-school-canteen-2d')
 const draft=parseTemplateStyleDraft(serializeTemplateStyle(file))
 assert.equal(draft.appearance.visualPreset,'tech-blue-v1')
 const {presentationVars,defaultAppearance}=await import(presentation)
 const tech=presentationVars(draft.appearance),basic=presentationVars(defaultAppearance())
 assert.match(tech['--bi-canvas-fill'],/radial-gradient/)
 assert.equal(basic['--bi-canvas-fill'],basic['--bi-background'])
 assert.equal(tech['--bi-card'],file.appearance.cardBackground)
 file.appearance.visualPreset='url(https://example.com)'
 assert.throws(()=>serializeTemplateStyle(file))
})
test('style-only screen snapshot carries appearance and dimensions but no layout, bindings or refresh policy',async()=>{
  const file=await load('market-screen-school-canteen-2d')
  const before=JSON.stringify(file),raw=serializeTemplateStyle(file),draft=parseTemplateStyleDraft(raw)
  assert.deepEqual(draft.appearance,file.appearance);assert.equal(draft.kind,'screen');assert.equal(draft.layout,'absolute')
  assert.equal(draft.scene.width,1920);assert.equal(draft.scene.height,1080);assert.equal(draft.scene.refreshSeconds,0)
  assert.deepEqual(draft.panels,[]);assert.deepEqual(draft.interaction,{controls:[],links:[]})
  assert.ok(!raw.includes('purchase-total'));assert.ok(!raw.includes('chartId'));assert.ok(!raw.includes('panels'))
  assert.equal(JSON.stringify(file),before)
  const invalid=structuredClone(file);invalid.scene.refreshSeconds=60
  assert.throws(()=>serializeTemplateStyle(invalid),/刷新须关闭/)
})
test('grid style keeps its canvas kind and snapshots remain independent after reload or edits',async()=>{
  const file=await load('market-dashboard-retail'),raw=serializeTemplateStyle(file)
  const first=parseTemplateStyleDraft(raw),second=parseTemplateStyleDraft(raw)
  assert.equal(first.kind,'dashboard');assert.equal(first.layout,'grid');assert.equal(first.scene,undefined)
  first.appearance.background='#000000';first.panels.push({key:'new'})
  assert.deepEqual(second.appearance,file.appearance);assert.deepEqual(second.panels,[])
})
test('unknown payloads, unsupported versions and invalid nested style fields are rejected',async()=>{
  const packet=JSON.parse(serializeTemplateStyle(await load('market-screen-school-canteen-2d')))
  for(const change of [value=>value.version=2,value=>value.kind='custom',value=>value.chartId=123,value=>value.appearance.background='url(https://outside/image)',value=>value.appearance.password='secret',value=>value.scene.width=-1]){
    const changed=structuredClone(packet);change(changed);assert.throws(()=>parseTemplateStyleDraft(JSON.stringify(changed)))
  }
  assert.throws(()=>parseTemplateStyleDraft('x'.repeat(8193)))
  assert.throws(()=>parseTemplateStyleDraft('null'))
  assert.throws(()=>parseTemplateStyleDraft('{bad json'))
})
