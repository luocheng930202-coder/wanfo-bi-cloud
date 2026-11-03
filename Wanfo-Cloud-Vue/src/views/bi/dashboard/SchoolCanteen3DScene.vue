<template>
  <div ref="host" class="scene-host" role="group" :aria-label="sceneLabel">
    <div v-if="!webgl" class="scene-fallback" role="status">
      <svg v-if="mode==='map' && mapRegions.length" viewBox="-6.5 -4.5 13 9" role="img" aria-label="当前行政区二维边界"><path v-for="area in mapRegions" :key="area.code" :d="area.path" :fill="area.code.startsWith('boundary-')?'none':sceneMapColor(mapValues.values.get(area.code),mapMaximum)" fill-rule="evenodd" stroke="#7da2b5" stroke-width=".015"><title>{{ area.name }}：{{ mapValues.values.has(area.code)?mapValues.values.get(area.code):'无匹配数据' }}</title></path></svg>
      <svg v-else viewBox="0 0 320 150" aria-hidden="true"><path d="M30 115 160 55 290 115 160 145Z" fill="#10394c" stroke="#39b9d0"/><path d="M85 100V45L160 15 235 45V100L160 132Z" fill="#16425b" stroke="#57c9df"/><path d="M85 45 160 78 235 45M160 78V132" fill="none" stroke="#57c9df"/><path d="M105 62V90M130 74V102M185 74V102M210 62V90" stroke="#7ddaf1" stroke-width="6"/></svg>
      <strong>{{ sceneLabel }} · 静态示意</strong>
      <span>{{ fallbackReason }}</span>
      <small>{{ mode==='map' && mapRegions.length?'行政边界按经纬度平面展示，不用于测绘；灰色为无匹配数据。':'示意图不是实际建筑或地理边界；同屏二维图表可继续使用。' }}</small>
      <div v-if="dataBound && fallbackRows.length" class="fallback-values"><span v-for="(row,index) in fallbackRows" :key="index">{{ row.label }}：{{ row.value }}</span></div>
      <el-button size="small" @click="initialize">重试加载 3D</el-button>
    </div>
    <div v-else class="scene-hud">
      <span><i class="live-dot" />{{ sceneLabel }}</span>
      <span class="scene-state">{{ sceneStatus }}<small>{{ interactionHint }}</small></span>
    </div>
    <p v-if="effectWarning && webgl" class="effect-warning" role="status">{{ effectWarning }}</p>
    <div v-if="mode==='map'" class="map-summary">
      <small>{{ sceneStatus }} · 灰色无数据，青色为非负值，红色为负值</small>
      <el-button v-if="mapError" size="small" :loading="mapLoading" @click="loadMapBoundary">重试地图边界</el-button>
      <span v-if="selectedMapEntry">已选：{{ selectedMapEntry.name }} · {{ selectedMapEntry.value }}</span>
      <details v-if="mapEntries.length"><summary>地区指标（{{ mapEntries.length }} 个匹配地区）</summary><ul><li v-for="item in mapEntries.slice(0,200)" :key="item.code">{{ item.name }}：{{ item.value }} <el-button v-if="sceneMapDrill(mapContext,item.code,mapValues.values)" link size="small" :disabled="loading || interactionDisabled" @click="drillMapRegion(item.code)">查看下级</el-button><el-button v-if="linkable" link size="small" :disabled="loading || interactionDisabled" @click="selectMapRegion(item.code)">筛选此地区</el-button></li></ul><small v-if="mapEntries.length>200">仅列出前 200 个地区，地图颜色仍覆盖全部匹配地区。</small></details>
    </div>
    <details v-if="mode==='chart' && webgl && parsedData.records.length" class="map-summary"><summary>图表数据（{{ parsedData.records.length }} 项）</summary><ul><li v-for="(item,index) in parsedData.records" :key="index">{{ item.label }}：{{ item.value }}</li></ul></details>
  </div>
</template>

<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,onActivated,onDeactivated,ref,shallowRef,watch} from 'vue'
import * as THREE from 'three'
import {OrbitControls} from 'three/addons/controls/OrbitControls.js'
import {EffectComposer} from 'three/addons/postprocessing/EffectComposer.js'
import {RenderPass} from 'three/addons/postprocessing/RenderPass.js'
import {UnrealBloomPass} from 'three/addons/postprocessing/UnrealBloomPass.js'
import {OutputPass} from 'three/addons/postprocessing/OutputPass.js'
import {decorateHologramBuilding,hologramBloomSettings} from '@/utils/biHologramScene'
import {disposeThreeResources} from '@/utils/biThreeResources'
import {sceneData,sceneBarGeometry} from '@/utils/biSceneData'
import {prepareSceneMap,sceneMapValues,sceneMapColor,sceneMapDrill,type SceneMapRegion} from '@/utils/biSceneMap'
import {fetchMapGeo} from '@/utils/biMapGeo'
import type {MapContext} from '@/api/bi/chart'

type SceneRow=Record<string,unknown>
const props=withDefaults(defineProps<{holographic?:boolean;glowIntensity?:number;mode:'map'|'building'|'chart';region:string;rows?:SceneRow[];dimension?:string;metric?:string;mapContext?:MapContext;mapValueMode?:'name'|'code';dataBound?:boolean;loading?:boolean;linkable?:boolean;interactionDisabled?:boolean;cameraPreset?:'orbit'|'top'|'front';rotateEnabled?:boolean;zoomEnabled?:boolean}>(),{cameraPreset:'orbit',rotateEnabled:true,zoomEnabled:true})
const emit=defineEmits<{(event:'drill',value:{field:string;value:string}):void;(event:'select',value:string):void}>()
const selectedMapCode=ref<string>()
const host=ref<HTMLElement>(),webgl=ref(true),effectWarning=ref('')
const fallbackReason=ref('当前设备无法创建 3D 场景，已切换静态预览。')
const parsedData=computed(()=>sceneData(props.rows || [],props.dimension,props.metric,!!props.dataBound))
const mapRegions=shallowRef<SceneMapRegion[]>([]),mapLoading=ref(false),mapError=ref('')
const mapValues=computed(()=>sceneMapValues(mapRegions.value,props.rows || [],props.dimension,props.metric,props.mapValueMode==='code'))
const mapMaximum=computed(()=>Math.max(1,...[...mapValues.value.values.values()].map(value=>Math.abs(value))))
const mapEntries=computed(()=>mapRegions.value.filter(area=>mapValues.value.values.has(area.code)).map(area=>({code:area.code,name:area.name,value:mapValues.value.values.get(area.code)})))
const selectedMapEntry=computed(()=>mapEntries.value.find(area=>area.code===selectedMapCode.value))
const fallbackRows=computed(()=>parsedData.value.records)
const interactionHint=computed(()=>[props.rotateEnabled?'拖动旋转':'',props.zoomEnabled?'滚轮缩放':'',props.mode==='map' && props.mapContext?.canDrillDown && !props.interactionDisabled?'双击有数据地区查看下级':''].filter(Boolean).join(' · ') || '固定视角')
const sceneStatus=computed(()=>{
  if(props.mode==='map'){
    if(!props.mapContext)return '请绑定行政区地图图表，使用地区编码模式'
    if(mapLoading.value)return '正在加载当前行政区边界'
    if(mapError.value)return mapError.value
    if(props.mapValueMode!=='code')return '边界已加载；地区指标须切换为编码模式绑定'
    const values=mapValues.value
    return `已匹配 ${values.values.size} 个地区 · 经纬度平面展示，非测绘投影${values.unmatched?' · '+values.unmatched+' 条地区未匹配':''}${values.invalid?' · '+values.invalid+' 条无效值':''}${values.duplicates?' · '+values.duplicates+' 个重复编码未着色':''}`
  }
  if(props.mode==='building')return '程序化建筑示意 · 非实际学校模型'
  if(props.loading)return '正在查询授权数据'
  const data=parsedData.value
  if(data.state==='unbound')return '未绑定数据 · 请配置授权图表'
  if(data.state==='unconfigured')return '未取得度量字段 · 请查询并检查字段绑定'
  if(data.state==='empty')return '查询成功 · 当前筛选下无数据'
  if(data.state==='invalid')return '没有有效数值 · 请检查度量字段类型'
  return `显示 ${data.records.length} 项${data.omitted?' · 另有 '+data.omitted+' 项未展示（按查询顺序）':''}${data.invalid?' · 已跳过 '+data.invalid+' 条无效值':''}`
})
const sceneLabel=computed(()=>props.mode==='map'?'行政区指标地图':props.mode==='chart'?'数据指标 3D 对比':'学校食堂建筑与设备')
let renderer:THREE.WebGLRenderer|undefined,scene:THREE.Scene,camera:THREE.PerspectiveCamera,controls:OrbitControls,observer:ResizeObserver,frame=0,root:THREE.Group

const mat=(color:string,emissive=color,roughness=.42)=>new THREE.MeshStandardMaterial({color,emissive,emissiveIntensity:.24,roughness,metalness:.14})
function box(parent:THREE.Object3D,x:number,y:number,z:number,w:number,h:number,d:number,material:THREE.Material){const mesh=new THREE.Mesh(new THREE.BoxGeometry(w,h,d),material);mesh.position.set(x,y,z);mesh.castShadow=true;mesh.receiveShadow=true;parent.add(mesh);return mesh}
function label(text:string,color='#b9eaff',size=32){const canvas=document.createElement('canvas');canvas.width=512;canvas.height=96;const ctx=canvas.getContext('2d')!;ctx.font=`600 ${size}px Microsoft YaHei, sans-serif`;ctx.fillStyle=color;ctx.shadowColor=color;ctx.shadowBlur=12;ctx.fillText(text,12,62);const texture=new THREE.CanvasTexture(canvas);const sprite=new THREE.Sprite(new THREE.SpriteMaterial({map:texture,transparent:true,depthWrite:false}));sprite.scale.set(3.3,.62,1);return sprite}
function clearRoot(){if(!root)return;disposeThreeResources(root);scene.remove(root);root=new THREE.Group();scene.add(root)}

function buildBuilding(){
  const ground=mat('#102c42','#0c607a'),wall=mat('#a8d8e5','#187b9b'),roof=mat('#187d9a','#00d9ff'),glass=mat('#40d9ff','#00aaff'),accent=mat('#f4b857','#df8a22'),dark=mat('#102334','#08121d')
  if(props.holographic){
    const s=hologramBloomSettings(props.glowIntensity ?? 55).strength/1.2
    wall.setValues({color:'#1E6886',emissive:'#167A98',emissiveIntensity:s*.16,transparent:true,opacity:.48,depthWrite:false,roughness:.16,metalness:.2})
    roof.setValues({color:'#14516F',emissive:'#0B8EA5',emissiveIntensity:s*.24,transparent:true,opacity:.38,depthWrite:false,roughness:.12})
    glass.setValues({color:'#145B74',emissive:'#35DFFF',emissiveIntensity:s*1.8,roughness:.1})
  }
  box(root,0,-.18,0,12,.35,8,ground)
  box(root,-1.25,.95,0,6.4,2.1,5.1,wall)
  box(root,-1.25,2.08,0,6.8,.22,5.45,roof)
  box(root,3.35,.7,.15,2.8,1.55,4.25,wall)
  box(root,3.35,1.56,.15,3.05,.2,4.5,roof)
  box(root,0,.06,3.15,11,.16,.45,dark)
  for(let col=0;col<5;col++)for(let row=0;row<2;row++)box(root,-3.45+col*1.08,1.15+row*.58,2.59,.57,.34,.07,glass)
  for(let col=0;col<3;col++)for(let row=0;row<2;row++)box(root,2.35+col*.82,.78+row*.46,2.29,.43,.26,.07,glass)
  box(root,-3.15,.65,-2.68,.38,1.25,.16,accent)
  const walk=box(root,.3,.08,4.05,1.1,.1,2.3,accent);walk.rotation.y=.12
  for(let i=0;i<5;i++){
    const tree=new THREE.Group();tree.position.set(-5+i*2.45,.02,-3.45)
    const trunk=new THREE.Mesh(new THREE.CylinderGeometry(.07,.1,.48,8),mat('#986d4a'));trunk.position.y=.24;tree.add(trunk)
    const crown=new THREE.Mesh(new THREE.IcosahedronGeometry(.42,1),mat(i%2?'#2bcba2':'#46d88c','#168252'));crown.position.y=.62;tree.add(crown);root.add(tree)
  }
  const tag=label('智慧校园 · 食堂建筑');tag.position.set(0,3.3,0);root.add(tag)
  const kitchen=label('后厨', '#ffd182',25);kitchen.position.set(3.35,2.2,1.7);root.add(kitchen)
  const dining=label('学生餐厅','#8ee9ff',25);dining.position.set(-1,2.8,2.5);root.add(dining)
  root.rotation.y=-.2
  if(props.holographic)decorateHologramBuilding(root,props.glowIntensity ?? 55)
}
function rebuildMapSafely(){if(renderer && webgl.value && props.mode==='map'){try{rebuild()}catch{degrade('地图场景渲染失败，已切换二维边界预览')}}}
let mapVersion=0,mapRequest:AbortController|undefined
async function loadMapBoundary(){
  const version=++mapVersion;mapRequest?.abort();mapRegions.value=[];selectedMapCode.value=undefined;mapError.value=''
  const scope=props.mode==='map'?props.mapContext?.scope:undefined
  mapLoading.value=!!scope
  rebuildMapSafely()
  if(!scope)return
  const request=new AbortController();mapRequest=request
  try{const geo=await fetchMapGeo(scope,request.signal);if(version!==mapVersion)return;mapRegions.value=prepareSceneMap(geo);rebuildMapSafely()}
  catch{if(version===mapVersion && !request.signal.aborted){mapError.value='地图边界加载失败，请检查资源和查询权限';rebuildMapSafely()}}
  finally{if(version===mapVersion)mapLoading.value=false}
}
function drillMapRegion(code:string){if(props.loading || props.interactionDisabled)return;const target=sceneMapDrill(props.mapContext,code,mapValues.value.values);if(target)emit('drill',target)}
function selectMapRegion(code:string){if(props.loading || props.interactionDisabled || !mapValues.value.values.has(code))return;selectedMapCode.value=code;needsRender=true;for(const object of root?.children || []){if(object instanceof THREE.Mesh && object.userData.code && object.material instanceof THREE.MeshStandardMaterial)object.material.emissiveIntensity=(object.userData.code===code ? 0.65 : 0.24)}if(props.linkable)emit('select',code)}
let pointerStart:{x:number;y:number}|undefined
function mapHit(event:MouseEvent):string|undefined {
  if(!renderer || !camera || !root || props.mode!=='map' || props.loading || props.interactionDisabled)return
  const rect=renderer.domElement.getBoundingClientRect();if(!rect.width || !rect.height)return
  const ray=new THREE.Raycaster();ray.setFromCamera(new THREE.Vector2((event.clientX-rect.left)/rect.width*2-1,-(event.clientY-rect.top)/rect.height*2+1),camera)
  return ray.intersectObjects(root.children,false).map(hit=>hit.object.userData.code as string|undefined).find(code=>!!code && mapValues.value.values.has(code))
}
function mapPointerDown(event:PointerEvent){pointerStart=event.button===0?{x:event.clientX,y:event.clientY}:undefined}
function mapPointerUp(event:PointerEvent){const start=pointerStart;pointerStart=undefined;if(!start || Math.hypot(event.clientX-start.x,event.clientY-start.y)>5)return;const code=mapHit(event);if(code)selectMapRegion(code)}
function mapDoubleClick(event:MouseEvent){const code=mapHit(event);if(code)drillMapRegion(code)}
function buildMap(){
  if(!mapRegions.value.length){
    const grid=new THREE.GridHelper(13,26,'#1e7da0','#15445e');grid.position.y=-.1;root.add(grid)
    const heading=label(props.mapContext?'地图边界加载中或不可用':'请绑定行政区地图图表','#c1ebff',26);heading.position.set(0,1.6,-2);root.add(heading)
    return
  }
  for(const area of mapRegions.value){
    const shapes=area.polygons.map(polygon=>{
      const shape=new THREE.Shape(polygon[0].map(([x,y])=>new THREE.Vector2(x,y)))
      shape.holes=polygon.slice(1).map(ring=>new THREE.Path(ring.map(([x,y])=>new THREE.Vector2(x,y))))
      return shape
    })
    const color=sceneMapColor(mapValues.value.values.get(area.code),mapMaximum.value)
    const mesh=area.code.startsWith('boundary-')?undefined:new THREE.Mesh(new THREE.ExtrudeGeometry(shapes,{depth:.22,bevelEnabled:false,steps:1,curveSegments:1}),mat(color,color))
    if(mesh){mesh.material.emissiveIntensity=(selectedMapCode.value===area.code ? 0.65 : 0.24);mesh.rotation.x=-Math.PI/2;mesh.userData={code:area.code,name:area.name};root.add(mesh)}
    const segments:number[]=[]
    for(const polygon of area.polygons)for(const ring of polygon)for(let i=1;i<ring.length;i++)segments.push(ring[i-1][0],.225,-ring[i-1][1],ring[i][0],.225,-ring[i][1])
    const geometry=new THREE.BufferGeometry();geometry.setAttribute('position',new THREE.Float32BufferAttribute(segments,3))
    root.add(new THREE.LineSegments(geometry,new THREE.LineBasicMaterial({color:'#75b0c4',transparent:true,opacity:.7})))
  }
  const title=props.mapContext?.breadcrumbs.at(-1)?.name || props.mapContext?.rootName || '行政区地图'
  const heading=label(title+' · 授权地区指标','#c1ebff',28);heading.position.set(0,1.4,-4.2);root.add(heading)
}
function buildChart(){
  const base=mat('#15324a','#10263b'),colors=['#3fd4ef','#38e0b1','#5084ff','#b276ff','#ffb64b']
  box(root,0,-4.65,0,12,.22,7,base)
  const values=parsedData.value.records
  const maximum=Math.max(...values.map(item=>Math.abs(item.value)),1)
  for(let i=0;i<values.length;i++){
    const x=(i-(values.length-1)/2)*Math.min(2.05,8.2/Math.max(values.length-1,1)),geometry=sceneBarGeometry(values[i].value,maximum),h=geometry.height,color=values[i].value<0?'#f58d8d':colors[i]
    if(h>0){const bar=new THREE.Mesh(new THREE.BoxGeometry(.85,h,1.35),mat(color,color));bar.position.set(x,geometry.centerY,0);bar.castShadow=true;root.add(bar)}
    const cap=new THREE.Mesh(new THREE.BoxGeometry(.85,(values[i].value===0 ? 0.005 : 0.08),1.35),new THREE.MeshBasicMaterial({color:'#d5fbff'}));cap.position.set(x,geometry.endY+(values[i].value===0?0:values[i].value<0?-.04:.04),0);root.add(cap)
    const txt=label(values[i].label,'#a9d7e8',20);txt.position.set(x,.16,1.7);root.add(txt)
    const formatted=new Intl.NumberFormat('zh-CN',{notation:'compact',maximumSignificantDigits:6}).format(values[i].value)
    const val=label(formatted,'#e2f8ff',18);val.position.set(x,geometry.endY+(values[i].value<0?-.48:.48),0);root.add(val)
  }
  const line=new THREE.Line(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(-5,0,-2.2),new THREE.Vector3(5,0,-2.2)]),new THREE.LineBasicMaterial({color:'#51bad8'}));root.add(line)
  root.rotation.y=-.32
  const heading=label(values.length?(props.metric || '授权度量')+' · 0 为基线':sceneStatus.value,'#bfefff',30);heading.position.set(0,5.2,-1.6);root.add(heading)
}
function rebuild(){if(!scene || !root)return;needsRender=true;clearRoot();if(props.mode==='map')buildMap();else if(props.mode==='chart')buildChart();else buildBuilding()}
function applyControls(){
  if(!camera || !controls)return
  controls.enableRotate=props.rotateEnabled
  controls.enableZoom=props.zoomEnabled
  controls.target.set(0,.7,0)
  if(props.cameraPreset==='top')camera.position.set(0,17,.01)
  else if(props.cameraPreset==='front')camera.position.set(0,5,17)
  else camera.position.set(10,8,11)
  camera.lookAt(controls.target)
  controls.update()
}
let active=true,inViewport=true,unmounted=false,needsRender=true
let composer:EffectComposer|undefined,bloom:UnrealBloomPass|undefined
function disposeEffects(){for(const pass of composer?.passes || [])pass.dispose();composer?.dispose();bloom=undefined;composer=undefined}
function configureEffects(){
  if(!renderer || !scene || !camera)return
  const settings=hologramBloomSettings(props.glowIntensity ?? 55)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,props.holographic?1:1.5))
  renderer.toneMapping=props.holographic?THREE.ACESFilmicToneMapping:THREE.NoToneMapping
  const color=props.holographic?'#020C18':'#071625'
  scene.background=new THREE.Color(color);if(scene.fog instanceof THREE.Fog)scene.fog.color.set(color)
  const key=scene.getObjectByName('bi-key-light') as THREE.DirectionalLight|undefined
  if(key){key.color.set(props.holographic?'#BCF7FF':'#A9EFFF');key.intensity=props.holographic?1.8:3}
  if(!props.holographic || settings.strength===0){disposeEffects();effectWarning.value='';needsRender=true;return}
  try{
    if(!composer){composer=new EffectComposer(renderer);composer.addPass(new RenderPass(scene,camera));bloom=new UnrealBloomPass(new THREE.Vector2(1,1),settings.strength,settings.radius,settings.threshold);composer.addPass(bloom);composer.addPass(new OutputPass())}
    composer.setPixelRatio(renderer.getPixelRatio())
    if(bloom){bloom.strength=settings.strength;bloom.radius=settings.radius;bloom.threshold=settings.threshold}
    effectWarning.value='';needsRender=true
  }catch{disposeEffects();effectWarning.value='当前设备未启用三维泛光，轮廓与基础场景仍可使用。'}
}
let intersection:IntersectionObserver|undefined
function stopFrame(){cancelAnimationFrame(frame);frame=0}
function release(){
  stopFrame();disposeEffects();observer?.disconnect();controls?.dispose()
  if(scene){disposeThreeResources(scene);scene.clear()}
  if(renderer){const old=renderer;renderer=undefined;old.domElement.removeEventListener('webglcontextlost',contextLost);old.domElement.removeEventListener('pointerdown',mapPointerDown);old.domElement.removeEventListener('pointerup',mapPointerUp);old.domElement.removeEventListener('dblclick',mapDoubleClick);pointerStart=undefined;old.dispose();try{old.forceContextLoss()}catch{/* Context may already be unavailable. */}old.domElement.remove()}
}
function degrade(reason:string){webgl.value=false;fallbackReason.value=reason;release()}
function contextLost(event:Event){event.preventDefault();degrade('显卡连接已中断，已切换静态预览。可点击重试恢复 3D。')}
function render(){
  frame=0
  if(!renderer || !scene || !camera || !webgl.value || !active || !inViewport || document.hidden || unmounted)return
  try{const cameraChanged=controls?.update();if(cameraChanged || needsRender){if(composer){try{composer.render()}catch{disposeEffects();renderer.setRenderTarget(null);effectWarning.value='三维泛光已关闭，保留基础场景。';renderer.render(scene,camera)}}else renderer.render(scene,camera);needsRender=false}if(renderer && webgl.value && active && inViewport && !document.hidden)frame=requestAnimationFrame(render)}catch{degrade('3D 渲染失败，已切换静态预览。')}
}
function syncAnimation(){if(!active || !inViewport || document.hidden || !webgl.value)stopFrame();else if(!frame){needsRender=true;render()}}
function resize(){if(!host.value || !renderer || !camera)return;const width=host.value.clientWidth,height=host.value.clientHeight;if(!width || !height)return;try{renderer.setSize(width,height,false);camera.aspect=width/height;camera.updateProjectionMatrix();composer?.setSize(width,height);needsRender=true}catch{degrade('场景尺寸更新失败，请重试加载 3D。')}}
function initialize(){
  if(unmounted)return
  release();webgl.value=true
  try{
    renderer=new THREE.WebGLRenderer({antialias:true,alpha:true,powerPreference:'low-power'})
    renderer.domElement.setAttribute('aria-hidden','true')
    renderer.domElement.addEventListener('webglcontextlost',contextLost);renderer.domElement.addEventListener('pointerdown',mapPointerDown);renderer.domElement.addEventListener('pointerup',mapPointerUp);renderer.domElement.addEventListener('dblclick',mapDoubleClick)
    renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,1.5));renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFSoftShadowMap;host.value?.prepend(renderer.domElement)
    scene=new THREE.Scene();scene.background=new THREE.Color('#071625');scene.fog=new THREE.Fog('#071625',13,28)
    camera=new THREE.PerspectiveCamera(42,1,.1,100);camera.position.set(10,8,11)
    scene.add(new THREE.HemisphereLight('#8bdfff','#102239',2))
    const key=new THREE.DirectionalLight('#a9efff',3);key.name='bi-key-light';key.position.set(-5,12,8);key.castShadow=true;scene.add(key)
    const rim=new THREE.PointLight('#16c9ff',30,18);rim.position.set(4,5,-5);scene.add(rim)
    root=new THREE.Group();scene.add(root);controls=new OrbitControls(camera,renderer.domElement);controls.enableDamping=true;controls.minDistance=7;controls.maxDistance=22;controls.maxPolarAngle=Math.PI*.49
    observer=new ResizeObserver(resize);if(host.value)observer.observe(host.value);applyControls();configureEffects();rebuild();resize();syncAnimation()
  }catch{degrade('当前设备不支持 3D，或场景初始化失败，已切换静态预览。')}
}
onMounted(()=>{
  document.addEventListener('visibilitychange',syncAnimation)
  if(typeof IntersectionObserver!=='undefined' && host.value){intersection=new IntersectionObserver(entries=>{inViewport=entries[0]?.isIntersecting ?? false;syncAnimation()});intersection.observe(host.value)}
  initialize()
})
onActivated(()=>{active=true;syncAnimation()})
onDeactivated(()=>{active=false;stopFrame()})
watch(()=>[props.holographic,props.glowIntensity,props.mode,props.region,props.rows,props.dimension,props.metric,props.dataBound,props.cameraPreset,props.rotateEnabled,props.zoomEnabled,props.loading,props.mapValueMode],(next,previous)=>{const lightingOnly=previous && next.slice(2).every((value,index)=>value===previous[index+2]);if(selectedMapCode.value && !mapValues.value.values.has(selectedMapCode.value))selectedMapCode.value=undefined;if(!webgl.value)return;try{if(!lightingOnly)applyControls();configureEffects();rebuild()}catch{degrade('场景更新失败，已切换静态预览。')}})
watch(()=>[props.mode,props.mapContext?.scope],()=>{void loadMapBoundary()},{immediate:true})
onBeforeUnmount(()=>{++mapVersion;mapRequest?.abort();unmounted=true;intersection?.disconnect();document.removeEventListener('visibilitychange',syncAnimation);release()})
</script>

<style scoped>
.scene-host{position:relative;width:100%;height:100%;min-height:260px;overflow:hidden;background:radial-gradient(ellipse at 50% 46%,#123653 0,#091c2d 55%,#06111f 100%)}.scene-host :deep(canvas){position:absolute;inset:0;width:100%;height:100%;outline:0}.scene-hud{position:absolute;inset:12px 14px auto;z-index:2;display:flex;justify-content:space-between;color:#82a9c5;font-size:11px;pointer-events:none}.scene-hud>span:first-child{display:flex;align-items:center;gap:7px;color:#c5eaff}.live-dot{width:6px;height:6px;border-radius:50%;background:#35e0bb;box-shadow:0 0 10px #35e0bb}.scene-fallback{height:100%;display:grid;place-items:center;color:#a4bfd1;font-size:13px}
</style>

<style scoped>.scene-fallback{position:relative;box-sizing:border-box;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:10px;padding:18px;text-align:center;overflow:auto}.scene-fallback svg{width:min(65%,280px);max-height:150px;flex-shrink:0}.scene-fallback small{opacity:.7;max-width:360px}.fallback-values{display:flex;flex-wrap:wrap;justify-content:center;gap:8px;font-size:12px;max-width:100%;overflow-wrap:anywhere}</style>

<style scoped>.scene-state{text-align:right;max-width:65%;line-height:1.6}.scene-state small{display:block;opacity:.7}</style>

<style scoped>.map-summary{position:absolute;bottom:8px;left:10px;right:10px;z-index:3;color:#bddce8;font-size:11px;line-height:1.6;background:#071625d9;padding:6px;border-radius:4px;max-height:45%;overflow:auto}.map-summary summary{cursor:pointer}.map-summary ul{columns:2;margin:4px 0;padding-left:20px}.scene-host:has(.map-summary) .scene-fallback{padding-bottom:60px}</style>

<style scoped>.effect-warning{position:absolute;bottom:8px;left:12px;right:12px;margin:0;font-size:11px;color:#b8d4df;z-index:3;background:#061522dd;padding:5px 8px}</style>
