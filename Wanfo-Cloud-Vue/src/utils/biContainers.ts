import type {Panel} from '@/api/bi/dashboard'
import {validateContainerLayout,normalizeContainer} from './biPresentation'

export function arrangeContainers(panels:Panel[]){
  const next=panels.filter(p=>!p.parentKey).flatMap(p=>[p,...panels.filter(c=>c.parentKey===p.key)])
  if(next.length!==panels.length || new Set(next.map(p=>p.key)).size!==next.length)throw Error('容器关系无效，未调整组件')
  return next
}
export function attachPanels(panels:Panel[],keys:string[],parentKey:string){
  const parent=panels.find(p=>p.key===parentKey),selected=panels.filter(p=>keys.includes(p.key))
  if(!parent || parent.componentType!=='container' || parent.hidden || parent.locked || selected.length!==new Set(keys).size || !selected.length)throw Error('请选择可用容器和组件')
  if(selected.some(p=>p.key===parentKey || p.componentType==='container' || p.groupKey || p.locked || p.hidden))throw Error('请先解锁、显示并解组成员，不能嵌套容器')
  const next=arrangeContainers(panels.map(p=>keys.includes(p.key)?{...p,parentKey}:{...p}))
  validateContainerLayout(next,'screen');return next
}
export function detachPanels(panels:Panel[],keys:string[]){
  const selected=panels.filter(p=>keys.includes(p.key))
  if(selected.some(p=>p.locked || (p.parentKey && panels.some(v=>v.key===p.parentKey && (v.locked || v.hidden)))))throw Error('请先解锁并显示容器')
  const next=arrangeContainers(panels.map(p=>keys.includes(p.key)?{...p,parentKey:undefined}:{...p}))
  validateContainerLayout(next,'screen');return next
}
export function editContainer(panels:Panel[],key:string,style:Partial<NonNullable<Panel['content']>['container']>){
  const next=panels.map(p=>p.key===key?{...p,content:{container:normalizeContainer(style)}}:{...p})
  validateContainerLayout(next,'screen');return next
}
