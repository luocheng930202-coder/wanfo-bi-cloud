import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
let bundle=''
for(const file of ['biDashboardInteraction.ts','biDrill.ts']){
 const source=(await fs.readFile(new URL('../src/utils/'+file,import.meta.url),'utf8')).split('\n').filter(line=>!line.startsWith('import ')).join('\n')
 bundle+=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText+'\n'
}
const {dashboardDefaults,dashboardTransition,dashboardConditionChips,sameFilterState,resolveDrillPath,mergeDashboardInput}=await import('data:text/javascript;base64,'+Buffer.from(bundle).toString('base64'))
const controls=[{key:'channel',label:'渠道',type:'enum',defaultValue:'STORE'},{key:'range',label:'金额',type:'number',defaultValue:[0,100]}]
const state=()=>({draft:{channel:'TMALL',range:[10,50]},filters:{channel:'STORE',range:[0,20]},links:{category:'Food'},drills:{map:[{field:'province',value:'510000'}]}})
test('reset atomically restores independent published defaults and removes links, paths and unsaved input',()=>{
 const initial=state(),snapshot=JSON.stringify(initial),next=dashboardTransition(initial,{type:'reset'},controls)
 assert.deepEqual(next,{draft:{channel:'STORE',range:[0,100]},filters:{channel:'STORE',range:[0,100]},links:{},drills:{}})
 next.draft.range[0]=99;assert.equal(next.filters.range[0],0);assert.equal(controls[1].defaultValue[0],0);assert.equal(JSON.stringify(initial),snapshot)
})
test('removing one applied filter preserves other pending input and never silently applies it',()=>{
 const next=dashboardTransition(state(),{type:'filter-clear',key:'channel'},controls)
 assert.equal(next.draft.channel,null);assert.equal(next.filters.channel,null);assert.deepEqual(next.draft.range,[10,50]);assert.deepEqual(next.filters.range,[0,20]);assert.deepEqual(next.links,{});assert.deepEqual(next.drills,{})
 assert.equal(sameFilterState(next.draft,next.filters),false)
 const request=mergeDashboardInput(next.filters,next.links,next.drills);assert.deepEqual(request.filters.range,[0,20])
})
test('link replacement and removal clear obsolete drill paths while keeping unrelated links and draft filters',()=>{
 const initial=state();initial.links.other=0
 const linked=dashboardTransition(initial,{type:'link-set',values:{category:null}})
 assert.deepEqual(linked.links,{category:null,other:0});assert.deepEqual(linked.drills,{});assert.deepEqual(linked.filters,initial.filters)
 const removed=dashboardTransition(linked,{type:'link-clear',key:'category'});assert.deepEqual(removed.links,{other:0})
 assert.deepEqual(dashboardTransition(removed,{type:'links-clear'}).links,{})
 assert.equal(initial.links.category,'Food')
})
test('apply uses the draft once and clears dependent interactions without sharing range arrays',()=>{
 const initial=state(),next=dashboardTransition(initial,{type:'apply'});assert.deepEqual(next.filters,initial.draft);assert.deepEqual(next.links,{});assert.deepEqual(next.drills,{})
 next.draft.range[1]=999;assert.equal(next.filters.range[1],50)
})
test('condition summaries distinguish zeros, null links, source titles, ranges and human-readable map paths',()=>{
 const view={controls,panels:[{key:'source',title:'品类销售'},{key:'map',title:'区域分布'}],links:[{key:'category',sourcePanel:'source'}]}
 const input={filters:{channel:0,range:[null,200],unknown:'hidden'},links:{category:null},drills:{map:[{field:'province',value:'510000'},{field:'city',value:'510100'}]}}
 const payloads={map:{data:{mapContext:{breadcrumbs:[{code:'510000',name:'四川省'},{code:'510100',name:'成都市'}]}}}}
 const chips=dashboardConditionChips(view,input,payloads)
 assert.equal(chips.length,4);assert.equal(chips[0].label,'筛选 · 渠道：0');assert.match(chips[1].label,/不限 ～ 200/);assert.equal(chips[2].label,'联动 · 品类销售：空值');assert.equal(chips[3].label,'下钻 · 区域分布：四川省 › 成都市')
 assert.equal(chips.some(c=>c.label.includes('hidden')),false)
 assert.equal(dashboardConditionChips(view,{filters:{channel:'',range:[null,null]},links:{},drills:{map:[]}}).length,0)
})
test('semantic filter equality ignores key order and defaults cannot be mutated by input',()=>{
 assert.equal(sameFilterState({channel:null,range:[0,100]},{range:[0,100]}),true)
 assert.equal(sameFilterState({channel:0},{channel:null}),false)
 const defaults=dashboardDefaults(controls);defaults.range[0]=50;assert.equal(controls[1].defaultValue[0],0)
})
test('an explicit empty drill path overrides stale payload paths and returned segments are independent',()=>{
 const payload={presentation:{drillPath:[{field:'province',value:'四川'}]}}
 assert.deepEqual(resolveDrillPath([],payload),[]);assert.deepEqual(resolveDrillPath(undefined,payload),payload.presentation.drillPath)
 const source=[{field:'province',value:'广东'}],path=resolveDrillPath(source,payload);path[0].value='其他';assert.equal(source[0].value,'广东')
})
