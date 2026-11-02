<template>
  <div ref="host" class="scene-host" role="img" :aria-label="sceneLabel">
    <div v-if="!webgl" class="scene-fallback">当前设备不支持 WebGL，已切换为场景预览。</div>
    <div v-else class="scene-hud">
      <span><i class="live-dot" />{{ sceneLabel }}</span>
      <span v-if="mode==='chart' && !dataBound">示例数据 · 绑定图表后显示真实数据</span><span v-else>拖动旋转 · 滚轮缩放</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import * as THREE from 'three'
import {OrbitControls} from 'three/addons/controls/OrbitControls.js'

type SceneRow=Record<string,unknown>
const props=defineProps<{mode:'map'|'building'|'chart';region:string;rows?:SceneRow[];dimension?:string;metric?:string;dataBound?:boolean}>()
const host=ref<HTMLElement>(),webgl=ref(true)
const sceneLabel=computed(()=>props.mode==='map'?'区域学校分布':props.mode==='chart'?'食堂采购量 3D 对比':'学校食堂建筑与设备')
let renderer:THREE.WebGLRenderer|undefined,scene:THREE.Scene,camera:THREE.PerspectiveCamera,controls:OrbitControls,observer:ResizeObserver,frame=0,root:THREE.Group

const mat=(color:string,emissive=color,roughness=.42)=>new THREE.MeshStandardMaterial({color,emissive,emissiveIntensity:.24,roughness,metalness:.14})
function box(parent:THREE.Object3D,x:number,y:number,z:number,w:number,h:number,d:number,material:THREE.Material){const mesh=new THREE.Mesh(new THREE.BoxGeometry(w,h,d),material);mesh.position.set(x,y,z);mesh.castShadow=true;mesh.receiveShadow=true;parent.add(mesh);return mesh}
function label(text:string,color='#b9eaff',size=32){const canvas=document.createElement('canvas');canvas.width=512;canvas.height=96;const ctx=canvas.getContext('2d')!;ctx.font=`600 ${size}px Microsoft YaHei, sans-serif`;ctx.fillStyle=color;ctx.shadowColor=color;ctx.shadowBlur=12;ctx.fillText(text,12,62);const texture=new THREE.CanvasTexture(canvas);const sprite=new THREE.Sprite(new THREE.SpriteMaterial({map:texture,transparent:true,depthWrite:false}));sprite.scale.set(3.3,.62,1);return sprite}
function clearRoot(){if(!root)return;root.traverse(object=>{if(object instanceof THREE.Mesh){object.geometry.dispose();const mats=Array.isArray(object.material)?object.material:[object.material];mats.forEach(m=>{if(m instanceof THREE.MeshStandardMaterial)m.dispose()})}if(object instanceof THREE.Sprite){object.material.map?.dispose();object.material.dispose()}});scene.remove(root);root=new THREE.Group();scene.add(root)}

function buildBuilding(){
  const ground=mat('#102c42','#0c607a'),wall=mat('#a8d8e5','#187b9b'),roof=mat('#187d9a','#00d9ff'),glass=mat('#40d9ff','#00aaff'),accent=mat('#f4b857','#df8a22'),dark=mat('#102334','#08121d')
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
}
function buildMap(){
  const floor=new THREE.Mesh(new THREE.PlaneGeometry(13,9),mat('#0b263a','#071827'));floor.rotation.x=-Math.PI/2;floor.position.y=-.13;root.add(floor)
  const grid=new THREE.GridHelper(13,26,'#1e7da0','#15445e');grid.position.y=-.1;root.add(grid)
  // Prototype geography: abstract district clusters. Replace with province/city/county GeoJSON in the data integration phase.
  const clusters=[[-4, -2,5],[-2,1,8],[.3,-1,7],[2.8,1.5,6],[4,-2,4]]
  clusters.forEach(([cx,cz,count],cluster)=>{
    for(let i=0;i<count;i++){
      const angle=i/count*Math.PI*2,r=.42+(i%3)*.19,x=cx+Math.cos(angle)*r,z=cz+Math.sin(angle)*r
      const height=.45+(i*cluster%5)*.22
      const color=cluster%2?'#24b7cc':'#48e1b0'
      const pin=new THREE.Mesh(new THREE.CylinderGeometry(.035,.17,height,6),mat(color,color));pin.position.set(x,height/2-.1,z);pin.castShadow=true;root.add(pin)
      const glow=new THREE.Mesh(new THREE.CircleGeometry(.26,20),new THREE.MeshBasicMaterial({color,transparent:true,opacity:.2,side:THREE.DoubleSide}));glow.rotation.x=-Math.PI/2;glow.position.set(x,-.08,z);root.add(glow)
    }
    const marker=label(['北城区','东城区','中心城区','南城区','西城区'][cluster],cluster%2?'#80eaff':'#98ffd1',22);marker.position.set(cx,.28,cz);root.add(marker)
  })
  const heading=label(props.region+' · 学校食堂分布','#c1ebff',30);heading.position.set(0,1.6,-3.6);root.add(heading)
}
function buildChart(){
  const base=mat('#15324a','#10263b'),axisMat=mat('#56a9c5','#207896'),colors=['#3fd4ef','#38e0b1','#5084ff','#b276ff','#ffb64b']
  box(root,0,-.08,0,12,.22,7,base)
  const records=(props.rows||[]).flatMap((row,i)=>{const raw=props.metric?row[props.metric]:undefined;const value=typeof raw==='number'?raw:typeof raw==='string'?Number(raw.replace(/,/g,'')):NaN;return Number.isFinite(value)?[{label:String((props.dimension?row[props.dimension]:undefined) ?? `项目${i+1}`).slice(0,14),value}]:[]}).slice(0,5)
  const values=records.length?records:props.dataBound?[]:[{label:'粮油',value:128},{label:'蔬果',value:186},{label:'肉禽',value:142},{label:'水产',value:211},{label:'乳品',value:165}]
  const maximum=Math.max(...values.map(item=>Math.abs(item.value)),1)
  for(let i=0;i<values.length;i++){
    const x=(i-(values.length-1)/2)*Math.min(2.05,8.2/Math.max(values.length-1,1)),h=Math.max(.45,Math.abs(values[i].value)/maximum*4.3)
    const bar=new THREE.Mesh(new THREE.BoxGeometry(.85,h,1.35),mat(colors[i],colors[i]));bar.position.set(x,h/2,0);bar.castShadow=true;root.add(bar)
    const cap=new THREE.Mesh(new THREE.BoxGeometry(.85,.08,1.35),new THREE.MeshBasicMaterial({color:'#d5fbff'}));cap.position.set(x,h+.04,0);root.add(cap)
    const txt=label(values[i].label,'#a9d7e8',20);txt.position.set(x,.16,1.7);root.add(txt)
    const formatted=new Intl.NumberFormat('zh-CN',{notation:'compact',maximumFractionDigits:1}).format(values[i].value)
    const val=label(formatted,'#e2f8ff',18);val.position.set(x,h+.48,0);root.add(val)
  }
  const line=new THREE.Line(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(-5,0,-2.2),new THREE.Vector3(5,0,-2.2)]),new THREE.LineBasicMaterial({color:'#51bad8'}));root.add(line)
  root.rotation.y=-.32
  const heading=label(props.dataBound && !values.length?'当前筛选下暂无数据':props.metric || '食堂采购量 3D 对比','#bfefff',30);heading.position.set(0,5.2,-1.6);root.add(heading)
}
function rebuild(){if(!scene || !root)return;clearRoot();if(props.mode==='map')buildMap();else if(props.mode==='chart')buildChart();else buildBuilding()}
function render(){if(!renderer||!scene||!camera)return;frame=requestAnimationFrame(render);controls?.update();renderer.render(scene,camera)}
function resize(){if(!host.value||!renderer||!camera)return;const width=host.value.clientWidth,height=host.value.clientHeight;if(!width||!height)return;renderer.setSize(width,height,false);camera.aspect=width/height;camera.updateProjectionMatrix()}
onMounted(()=>{
  try{renderer=new THREE.WebGLRenderer({antialias:true,alpha:true,powerPreference:'low-power'});renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,1.5));renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFSoftShadowMap;host.value?.prepend(renderer.domElement)}catch{webgl.value=false;return}
  scene=new THREE.Scene();scene.background=new THREE.Color('#071625');scene.fog=new THREE.Fog('#071625',13,28)
  camera=new THREE.PerspectiveCamera(42,1,.1,100);camera.position.set(10,8,11)
  scene.add(new THREE.HemisphereLight('#8bdfff','#102239',2))
  const key=new THREE.DirectionalLight('#a9efff',3);key.position.set(-5,12,8);key.castShadow=true;scene.add(key)
  const rim=new THREE.PointLight('#16c9ff',30,18);rim.position.set(4,5,-5);scene.add(rim)
  root=new THREE.Group();scene.add(root);controls=new OrbitControls(camera,renderer.domElement);controls.enableDamping=true;controls.minDistance=7;controls.maxDistance=22;controls.maxPolarAngle=Math.PI*.46;controls.target.set(0,.7,0)
  observer=new ResizeObserver(resize);if(host.value)observer.observe(host.value);rebuild();resize();render()
})
watch(()=>[props.mode,props.region,props.rows,props.dimension,props.metric,props.dataBound],rebuild)
onBeforeUnmount(()=>{cancelAnimationFrame(frame);observer?.disconnect();controls?.dispose();scene?.traverse(object=>{if(object instanceof THREE.Mesh){object.geometry.dispose();const materials=Array.isArray(object.material)?object.material:[object.material];materials.forEach(m=>m.dispose())}});renderer?.dispose();renderer?.domElement.remove()})
</script>

<style scoped>
.scene-host{position:relative;width:100%;height:100%;min-height:260px;overflow:hidden;background:radial-gradient(ellipse at 50% 46%,#123653 0,#091c2d 55%,#06111f 100%)}.scene-host :deep(canvas){position:absolute;inset:0;width:100%;height:100%;outline:0}.scene-hud{position:absolute;inset:12px 14px auto;z-index:2;display:flex;justify-content:space-between;color:#82a9c5;font-size:11px;pointer-events:none}.scene-hud>span:first-child{display:flex;align-items:center;gap:7px;color:#c5eaff}.live-dot{width:6px;height:6px;border-radius:50%;background:#35e0bb;box-shadow:0 0 10px #35e0bb}.scene-fallback{height:100%;display:grid;place-items:center;color:#a4bfd1;font-size:13px}
</style>
