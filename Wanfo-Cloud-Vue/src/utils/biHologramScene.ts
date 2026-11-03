import * as THREE from 'three'

/** Static, schematic decoration only: adds no metrics, geographic boundaries or animated data. */
export function hologramBloomSettings(intensity:number) {
  const strength=(Number.isFinite(intensity)?Math.max(0,Math.min(100,intensity)):55)/100
  return {strength:strength*1.2,radius:.22+strength*.18,threshold:1}
}

export function decorateHologramBuilding(root:THREE.Object3D,intensity:number) {
  const s=(Number.isFinite(intensity)?Math.max(0,Math.min(100,intensity)):55)/100
  if(s===0)return
  const boxes:THREE.Mesh[]=[]
  root.traverse(object=>{if(object instanceof THREE.Mesh && object.geometry instanceof THREE.BoxGeometry && object.position.y>.1)boxes.push(object)})
  if(boxes.length){
    const line=new THREE.LineBasicMaterial({color:new THREE.Color('#43E9FF').multiplyScalar(1+s*2),transparent:true,opacity:.45+s*.5,depthWrite:false,blending:THREE.AdditiveBlending})
    for(const mesh of boxes){const edges=new THREE.LineSegments(new THREE.EdgesGeometry(mesh.geometry,25),line);edges.name='hologram-outline';mesh.add(edges)}
  }
  const grid=new THREE.GridHelper(14,28,'#27869B','#16435C')
  for(const m of Array.isArray(grid.material)?grid.material:[grid.material]){m.transparent=true;m.opacity=s*.35;m.depthWrite=false}
  grid.position.y=.012;grid.name='hologram-grid';root.add(grid)
  const ringMaterial=new THREE.MeshBasicMaterial({color:new THREE.Color('#36DFFD').multiplyScalar(1+s*3),transparent:true,opacity:s*.7,side:THREE.DoubleSide,depthWrite:false,blending:THREE.AdditiveBlending})
  for(const radius of [6.3,6.75]){const ring=new THREE.Mesh(new THREE.RingGeometry(radius,radius+.025,96),ringMaterial);ring.rotation.x=-Math.PI/2;ring.position.y=.018;ring.name='hologram-base-ring';root.add(ring)}
}
