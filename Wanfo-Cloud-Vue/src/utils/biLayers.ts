import type {Panel,DashboardInteraction} from '@/api/bi/dashboard'

export function expandedSelection(panels:Panel[],keys:string[]){
  const groups=new Set(panels.filter(p=>keys.includes(p.key) && p.groupKey).map(p=>p.groupKey))
  const chosen=panels.filter(p=>keys.includes(p.key) || (p.groupKey && groups.has(p.groupKey))).map(p=>p.key)
  return panels.filter(p=>chosen.includes(p.key) || (p.parentKey && chosen.includes(p.parentKey))).map(p=>p.key)
}
export function groupPanels(panels:Panel[],keys:string[],groupKey:string){
  const chosen=panels.filter(p=>keys.includes(p.key))
  if(chosen.length<2 || chosen.length!==new Set(keys).size)throw Error('请至少选择两个有效组件')
  if(chosen.some(p=>p.locked || p.groupKey || p.parentKey || p.componentType==='container'))throw Error('请先解锁并解组所选组件')
  if(!/^[A-Za-z0-9_-]{1,64}$/.test(groupKey))throw Error('组合标识无效')
  return panels.map(p=>keys.includes(p.key)?{...p,groupKey}:{...p})
}
export function ungroupPanels(panels:Panel[],keys:string[]){
  const expanded=expandedSelection(panels,keys)
  if(panels.some(p=>expanded.includes(p.key) && p.locked))throw Error('请先解锁所选组件')
  return panels.map(p=>expanded.includes(p.key)?{...p,groupKey:undefined}:{...p})
}
export function reorderLayers(panels:Panel[],keys:string[],direction:'top'|'bottom'){
  const expanded=expandedSelection(panels,keys),chosen=panels.filter(p=>expanded.includes(p.key)),others=panels.filter(p=>!expanded.includes(p.key))
  if(!chosen.length || chosen.some(p=>p.locked))throw Error('请选择组件并先解锁')
  if(chosen.some(p=>p.parentKey && !expanded.includes(p.parentKey)))throw Error('容器成员请移出后调整层级')
  return direction==='top'?[...others,...chosen]:[...chosen,...others]
}
export function setPanelsHidden(panels:Panel[],keys:string[],hidden:boolean,interaction?:DashboardInteraction){
  const selected=expandedSelection(panels,keys)
  if(!selected.length || panels.some(p=>selected.includes(p.key) && p.locked))throw Error('请选择组件并先解锁')
  if(!hidden && panels.some(p=>selected.includes(p.key) && p.parentKey && !selected.includes(p.parentKey) && panels.some(v=>v.key===p.parentKey && v.hidden)))throw Error('请先显示容器')
  if(hidden && (interaction?.controls.some(c=>c.targets.some(t=>selected.includes(t.panel))) || interaction?.links.some(l=>selected.includes(l.sourcePanel) || l.targets.some(t=>selected.includes(t.panel)))))throw Error('隐藏前请先解除所选组件的筛选和联动绑定')
  return panels.map(p=>selected.includes(p.key)?{...p,hidden}:{...p})
}
