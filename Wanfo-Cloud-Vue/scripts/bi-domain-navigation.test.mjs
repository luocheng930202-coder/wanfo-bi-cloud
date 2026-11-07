import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/layout/components/BiWorkspaceLayout.vue',import.meta.url),'utf8')
const catalog=source.slice(source.indexOf('const analyzeGroups:'),source.indexOf('function readStoredDomain'))
const icons=[...new Set([...catalog.matchAll(/icon: (\w+)/g)].map(x=>x[1]))]
const context={routes:[],permissions:new Set()};globalThis.__biNavTest=context
const input=`const ctx=globalThis.__biNavTest;const systemMenus={value:[]};const router={getRoutes:()=>ctx.routes.map(path=>({path}))};const checkPermi=(ps)=>ps.some(p=>ctx.permissions.has(p));const computed=fn=>({get value(){return fn()}});const domains=['home','develop','govern','analyze','ops','system','monitor'].map(id=>({id}));const ${icons.map(x=>x+'={}').join(',')};\n`+catalog+'\nexport {filterGroups,pathDomain,domainCatalog,availableDomains}'
const js=ts.transpileModule(input,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const nav=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'))
test('domain menus filter missing routes and denied permissions before choosing entry',()=>{context.routes=['/bi-admin/workbench','/bi-admin/datasource','/bi-admin/dataset'];context.permissions=new Set(['bi:dataset:list']);assert.deepEqual(nav.filterGroups(nav.domainCatalog.develop)[0].items.map(x=>x.path),['/bi-admin/dataset']);assert.deepEqual(nav.availableDomains.value.map(x=>x.id),['home','develop']);context.permissions.clear();assert.deepEqual(nav.availableDomains.value.map(x=>x.id),['home'])})
test('active menu paths resolve to the correct top-level domain',()=>{for(const [path,domain] of [['workbench','home'],['datasource','develop'],['warehouse','develop'],['catalog','govern'],['lineage','govern'],['chart','analyze'],['dashboard','analyze'],['template','analyze'],['task-center','ops'],['subscription','ops']])assert.equal(nav.pathDomain('/bi-admin/'+path),domain);assert.equal(nav.pathDomain('/outside'),null)})
delete globalThis.__biNavTest

test('system and monitor pages resolve to their own domains; unrelated prefixes do not',()=>{for(const path of ['/system','/system/user','/system/role','/monitor','/monitor/online'])assert.equal(nav.pathDomain(path),path.startsWith('/system')?'system':'monitor');assert.equal(nav.pathDomain('/system-other'),null);assert.equal(nav.pathDomain('/monitoring'),null)})
