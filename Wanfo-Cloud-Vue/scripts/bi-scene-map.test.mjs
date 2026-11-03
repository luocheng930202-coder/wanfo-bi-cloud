import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {gunzipSync} from 'node:zlib'
import * as THREE from 'three'
import {test} from 'node:test'
const opts={compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}
const data=ts.transpileModule(await fs.readFile(new URL('../src/utils/biSceneData.ts',import.meta.url),'utf8'),opts).outputText
const source=(await fs.readFile(new URL('../src/utils/biSceneMap.ts',import.meta.url),'utf8')).split('\n').filter(line=>!line.includes("from './biSceneData'")).join('\n')
const {prepareSceneMap,sceneMapValues,sceneMapColor,sceneMapDrill}=await import('data:text/javascript;base64,'+Buffer.from(data+'\n'+ts.transpileModule(source,opts).outputText).toString('base64'))
const ring=[[100,20],[110,20],[110,30],[100,30],[100,20]]
const feature=(code='110000',coordinates=[ring])=>({type:'Feature',properties:{adcode:code,name:'测试地区'},geometry:{type:'Polygon',coordinates}})
const geo=(features=[feature()])=>({type:'FeatureCollection',features})
test('polygons, holes and multipolygons share a bounded display and safe SVG path',()=>{
  const result=prepareSceneMap(geo([feature('110000',[ring,[[102,22],[103,22],[103,23],[102,22]]]),{...feature('120000'),geometry:{type:'MultiPolygon',coordinates:[[ring]]}}]))
  assert.equal(result.length,2);assert.equal(result[0].polygons[0].length,2)
  for(const area of result)for(const polygon of area.polygons)for(const outline of polygon)for(const [x,y] of outline){assert.ok(Math.abs(x)<=6);assert.ok(Math.abs(y)<=4)}
  assert.match(result[0].path,/^M/);assert.equal((result[0].path.match(/Z/g)||[]).length,2)
})
test('invalid geometry and duplicate codes are rejected before rendering',()=>{
  assert.throws(()=>prepareSceneMap(geo([feature(),feature()])),/重复/)
  assert.throws(()=>prepareSceneMap(geo([feature('bad')])),/编码/)
  assert.throws(()=>prepareSceneMap(geo([feature('110000',[[[100,20],[110,20],[110,30],[101,20]]])])),/闭合/)
  assert.throws(()=>prepareSceneMap(geo([feature('110000',[[[NaN,20],...ring.slice(1)]])])),/坐标/)
})
test('data matching uses codes exclusively, keeps zero and ignores duplicate aggregates',()=>{
  const regions=prepareSceneMap(geo([feature(),feature('120000')]))
  const rows=[{code:110000,v:0},{code:'120000',v:20},{code:'120000',v:30},{code:'测试地区',v:99},{code:'999999',v:2},{code:'110000',v:null}]
  const result=sceneMapValues(regions,rows,'code','v',true)
  assert.equal(result.values.get('110000'),0);assert.equal(result.values.has('120000'),false)
  assert.equal(result.duplicates,1);assert.equal(result.unmatched,2);assert.equal(result.invalid,1)
  assert.equal(sceneMapValues(regions,rows,'code','v',false).values.size,0)
  assert.notEqual(sceneMapColor(undefined,10),sceneMapColor(0,10));assert.notEqual(sceneMapColor(-10,10),sceneMapColor(10,10))
})
test('the packaged national boundary loads including its auxiliary boundary feature',async()=>{
  const source=JSON.parse(await fs.readFile(new URL('../../Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/bi/geo/china-provinces.json',import.meta.url),'utf8'))
  const regions=prepareSceneMap(source)
  assert.equal(regions.length,source.features.length);assert.ok(regions.some(region=>region.code==='110000'))
  assert.ok(regions.some(region=>region.code.startsWith('boundary-')))
})
test('drill events use the server field and require a matched value plus an available child region',()=>{
  const context={field:'province_code',canDrillDown:true,drillableRegions:['110000','120000']},values=new Map([['110000',0]])
  assert.deepEqual(sceneMapDrill(context,'110000',values),{field:'province_code',value:'110000'})
  assert.equal(sceneMapDrill(context,'120000',values),undefined)
  assert.equal(sceneMapDrill(context,'999999',new Map([['999999',10]])),undefined)
  assert.equal(sceneMapDrill({...context,canDrillDown:false},'110000',values),undefined)
  assert.equal(sceneMapDrill(undefined,'110000',values),undefined)
})
test('packaged province and city boundaries triangulate into finite 3D meshes without a GPU',async()=>{
  for(const code of ['110000','320000','320500']){
    const raw=gunzipSync(await fs.readFile(new URL('../../Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/bi/geo/regions/'+code+'.json.gz',import.meta.url)))
    const regions=prepareSceneMap(JSON.parse(raw.toString('utf8')))
    assert.ok(regions.length>0)
    for(const region of regions.filter(item=>!item.code.startsWith('boundary-'))){
      const shapes=region.polygons.map(polygon=>{
        const shape=new THREE.Shape(polygon[0].map(([x,y])=>new THREE.Vector2(x,y)))
        shape.holes=polygon.slice(1).map(ring=>new THREE.Path(ring.map(([x,y])=>new THREE.Vector2(x,y))))
        return shape
      })
      const geometry=new THREE.ExtrudeGeometry(shapes,{depth:.22,bevelEnabled:false,steps:1,curveSegments:1})
      assert.ok(geometry.getAttribute('position').count>0)
      assert.ok([...geometry.getAttribute('position').array].every(Number.isFinite))
      geometry.dispose()
    }
  }
})
