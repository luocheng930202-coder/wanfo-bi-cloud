import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import * as THREE from 'three'
import {test} from 'node:test'
async function load(name){const source=await fs.readFile(new URL('../src/utils/'+name+'.ts',import.meta.url),'utf8');const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022}}).outputText.replace("'three'",JSON.stringify(import.meta.resolve('three')));return import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'))}
const {decorateHologramBuilding,hologramBloomSettings}=await load('biHologramScene')
const {disposeThreeResources}=await load('biThreeResources')
test('glow settings are bounded and zero disables bloom',()=>{
 assert.equal(hologramBloomSettings(0).strength,0)
 assert.equal(hologramBloomSettings(-50).strength,0)
 assert.equal(hologramBloomSettings(500).strength,1.2)
 assert.ok(Number.isFinite(hologramBloomSettings(NaN).strength))
})
test('building decoration preserves geometry, transforms and business metadata and releases shared resources once',()=>{
 const root=new THREE.Group(),mesh=new THREE.Mesh(new THREE.BoxGeometry(4,2,3),new THREE.MeshStandardMaterial())
 mesh.position.y=1;mesh.userData={school:'fictional-demo'};root.add(mesh)
 const position=mesh.position.clone(),geometry=mesh.geometry
 decorateHologramBuilding(root,55)
 assert.equal(mesh.geometry,geometry);assert.ok(mesh.position.equals(position));assert.deepEqual(mesh.userData,{school:'fictional-demo'})
 assert.equal(mesh.children.filter(n=>n.name==='hologram-outline').length,1)
 assert.equal(root.children.filter(n=>n.name==='hologram-base-ring').length,2)
 const resources=new Set();root.traverse(n=>{if(n.geometry)resources.add(n.geometry);for(const m of n.material?(Array.isArray(n.material)?n.material:[n.material]):[])resources.add(m)})
 const disposed=new Map();for(const resource of resources)resource.addEventListener('dispose',()=>disposed.set(resource,(disposed.get(resource)||0)+1))
 disposeThreeResources(root);assert.equal(disposed.size,resources.size);assert.ok([...disposed.values()].every(count=>count===1))
})
test('zero strength does not allocate decorative geometry',()=>{
 const root=new THREE.Group();decorateHologramBuilding(root,0);assert.equal(root.children.length,0)
})
