import * as THREE from 'three'

/** Shared geometries, materials and textures are released once per detached subtree. */
export function disposeThreeResources(root:THREE.Object3D) {
  const geometries=new Set<THREE.BufferGeometry>(),materials=new Set<THREE.Material>(),textures=new Set<THREE.Texture>()
  root.traverse(object=>{
    const renderable=object as THREE.Mesh
    // Sprite geometry belongs to Three.js itself and is shared across independent scenes.
    if(renderable.geometry && !(object instanceof THREE.Sprite))geometries.add(renderable.geometry)
    if(object instanceof THREE.DirectionalLight || object instanceof THREE.PointLight || object instanceof THREE.SpotLight)object.shadow.dispose()
    const material=renderable.material
    for(const item of Array.isArray(material)?material:material?[material]:[]){
      materials.add(item)
      for(const value of Object.values(item))if(value instanceof THREE.Texture)textures.add(value)
    }
  })
  textures.forEach(texture=>texture.dispose())
  materials.forEach(material=>material.dispose())
  geometries.forEach(geometry=>geometry.dispose())
}
