import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import * as THREE from 'three'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biThreeResources.ts',import.meta.url),'utf8')
const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022}}).outputText.replace("'three'",JSON.stringify(import.meta.resolve('three')))
const {disposeThreeResources}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'))
test('shared mesh, line and sprite resources dispose once without touching global sprite geometry',()=>{
  const root=new THREE.Group(),geometry=new THREE.BoxGeometry(),texture=new THREE.Texture(),material=new THREE.MeshBasicMaterial({map:texture}),lineGeometry=new THREE.BufferGeometry(),lineMaterial=new THREE.LineBasicMaterial(),spriteMaterial=new THREE.SpriteMaterial({map:texture})
  root.add(new THREE.Mesh(geometry,material),new THREE.Mesh(geometry,[material]),new THREE.Line(lineGeometry,lineMaterial))
  const sprite=new THREE.Sprite(spriteMaterial);root.add(sprite)
  const resources=[geometry,texture,material,lineGeometry,lineMaterial,spriteMaterial],counts=resources.map(()=>0)
  resources.forEach((resource,index)=>resource.addEventListener('dispose',()=>counts[index]++))
  let globalGeometryDisposals=0
  const listener=()=>globalGeometryDisposals++
  sprite.geometry.addEventListener('dispose',listener)
  disposeThreeResources(root)
  sprite.geometry.removeEventListener('dispose',listener)
  assert.deepEqual(counts,[1,1,1,1,1,1]);assert.equal(globalGeometryDisposals,0)
})
test('light shadow targets and scenes with non-renderable nodes clean up safely',()=>{
  const root=new THREE.Scene(),light=new THREE.DirectionalLight(),target=new THREE.WebGLRenderTarget(1,1)
  let releases=0;target.addEventListener('dispose',()=>releases++)
  light.shadow.map=target;root.add(light,new THREE.Object3D())
  disposeThreeResources(root);assert.equal(releases,1)
})
