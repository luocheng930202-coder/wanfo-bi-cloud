import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
import {createRouter,createMemoryHistory} from 'vue-router'
const compile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const load=s=>import('data:text/javascript;base64,'+Buffer.from(compile(s)).toString('base64'))
const helpers=await load(await fs.readFile(new URL('../src/utils/biWorkspaceNavigation.ts',import.meta.url),'utf8'))
let source=await fs.readFile(new URL('../src/router/index.ts',import.meta.url),'utf8')
source=source.slice(0,source.indexOf('const router = createRouter')).split('\n').filter(s=>!s.startsWith('import ')).join('\n')
const {constantRoutes}=await load('const Layout={name:"Layout"};\n'+source)
const router=createRouter({history:createMemoryHistory(),routes:constantRoutes})

test('chart and dashboard workspaces are rendered beneath the existing BI layout',()=>{
  for(const path of [helpers.chartWorkspacePath(),helpers.chartWorkspacePath(120,'edit'),helpers.dashboardWorkspacePath(),helpers.dashboardWorkspacePath(107,'view'),helpers.dashboardWorkspacePath(107)]) {
    const resolved=router.resolve(path)
    assert.equal(resolved.matched.length,2,path)
    assert.equal(resolved.matched[0].components.default.name,'Layout')
    assert.equal(resolved.meta.workspaceEditor,true)
    assert.ok(['/bi-admin/chart','/bi-admin/dashboard'].includes(resolved.meta.activeMenu))
  }
})
test('old bookmarks redirect to the embedded workspace while preserving creation options',()=>{
  for(const [oldPath,expected] of [['/bi-chart/120/view',helpers.chartWorkspacePath(120)],['/bi-chart/new',helpers.chartWorkspacePath()],['/bi-designer/107',helpers.dashboardWorkspacePath(107)],['/bi-designer?kind=screen',helpers.dashboardWorkspacePath()]]) {
    const location=router.resolve(oldPath)
    const target=location.matched.at(-1).redirect(location)
    assert.equal(target.path,expected)
    assert.deepEqual(target.query,location.query)
  }
})
test('list position is restored per library and per account without storing resource data',()=>{
  const values=new Map()
  globalThis.sessionStorage={getItem:k=>values.get(k)||null,setItem:(k,v)=>values.set(k,v)}
  const state={search:'零售',filter:'published',display:'list',page:3,scroll:420}
  helpers.saveLibraryPosition('dashboard','1',state)
  assert.deepEqual(helpers.readLibraryPosition('dashboard','1'),state)
  assert.equal(helpers.readLibraryPosition('chart','1').search,'')
  assert.equal(helpers.readLibraryPosition('dashboard','2').search,'')
  assert.deepEqual(Object.keys(JSON.parse([...values.values()][0])).sort(),['display','filter','page','scroll','search'])
})
test('invalid or unavailable session storage cannot break navigation',()=>{
  globalThis.sessionStorage={getItem:()=>'{broken',setItem:()=>{throw Error('disabled')}}
  assert.equal(helpers.readLibraryPosition('chart','1').page,1)
  assert.doesNotThrow(()=>helpers.saveLibraryPosition('chart','1',{}))
  sessionStorage.getItem=()=>JSON.stringify({search:23,filter:null,page:-1,scroll:-20,display:'other'})
  assert.deepEqual(helpers.readLibraryPosition('chart','1'),{search:'',filter:'all',page:1,scroll:0,display:'grid'})
  delete globalThis.sessionStorage
})
