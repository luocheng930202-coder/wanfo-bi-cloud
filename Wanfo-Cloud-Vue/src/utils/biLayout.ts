import type {Panel} from '@/api/bi/dashboard'
export type Alignment='left'|'center'|'right'|'top'|'middle'|'bottom'
export interface LayoutBounds {width:number;height:number;minimum:number;maxHeight:number;allowOverlap:boolean}
export const gridBounds:LayoutBounds={width:12,height:120,minimum:2,maxHeight:12,allowOverlap:false}
export function validLayout(panels:Panel[],bounds:LayoutBounds=gridBounds) {
  return panels.every((p,i)=>Number.isInteger(p.x)&&Number.isInteger(p.y)&&Number.isInteger(p.w)&&Number.isInteger(p.h)&&p.x>=0&&p.y>=0&&p.w>=bounds.minimum&&p.w<=bounds.width&&p.h>=bounds.minimum&&p.h<=bounds.maxHeight&&p.x+p.w<=bounds.width&&p.y+p.h<=bounds.height&&(bounds.allowOverlap || panels.slice(i+1).every(o=>p.x>=o.x+o.w||p.x+p.w<=o.x||p.y>=o.y+o.h||p.y+p.h<=o.y)))
}
/** Anchor is the first selected item; reject the entire command if any item is locked or would collide. */
export function alignPanels(panels:Panel[],keys:string[],mode:Alignment,bounds:LayoutBounds=gridBounds):Panel[] {
  const selected=keys.map(k=>panels.find(p=>p.key===k)).filter((p):p is Panel=>!!p)
  if(selected.length<2)throw new Error('请至少选择两个组件')
  if(selected.some(p=>p.locked))throw new Error('请先解锁所选组件')
  const anchor=selected[0],set=new Set(keys)
  const next=panels.map(p=>{if(!set.has(p.key))return {...p};const n={...p};if(mode==='left')n.x=anchor.x;if(mode==='right')n.x=anchor.x+anchor.w-p.w;if(mode==='center')n.x=Math.round(anchor.x+(anchor.w-p.w)/2);if(mode==='top')n.y=anchor.y;if(mode==='bottom')n.y=anchor.y+anchor.h-p.h;if(mode==='middle')n.y=Math.round(anchor.y+(anchor.h-p.h)/2);return n})
  if(!validLayout(next,bounds))throw new Error('对齐后会重叠或越界，已保留原布局')
  return next
}

function selection(panels:Panel[],keys:string[],minimum:number) {
  const unique=new Set(keys),selected=panels.filter(p=>unique.has(p.key))
  if(selected.length!==unique.size)throw new Error('所选组件已变化，请重新选择')
  if(selected.length<minimum)throw new Error('请至少选择'+minimum+'个组件')
  if(selected.some(p=>p.locked))throw new Error('请先解锁所选组件')
  return selected
}
/** Translate a group without allowing GridStack to push unrelated components. */
export function movePanels(panels:Panel[],keys:string[],dx:number,dy:number,bounds:LayoutBounds=gridBounds):Panel[] {
  selection(panels,keys,1)
  if(!Number.isInteger(dx)||!Number.isInteger(dy))throw new Error('移动距离须为整数栅格')
  const set=new Set(keys),next=panels.map(p=>set.has(p.key)?{...p,x:p.x+dx,y:p.y+dy}:{...p})
  if(!validLayout(next,bounds))throw new Error('移动后会重叠或越界，已保留原布局')
  return next
}
/** Keep the outer edges fixed; cumulative rounding limits integer-grid gap variation to one cell. */
export function distributePanels(panels:Panel[],keys:string[],axis:'horizontal'|'vertical',bounds:LayoutBounds=gridBounds):Panel[] {
  const horizontal=axis==='horizontal',position=horizontal?'x':'y',size=horizontal?'w':'h'
  const selected=selection(panels,keys,3).sort((a,b)=>a[position]-b[position] || a.key.localeCompare(b.key))
  const first=selected[0]
  const free=Math.max(...selected.map(p=>p[position]+p[size]))-first[position]-selected.reduce((sum,p)=>sum+p[size],0)
  if(free<0)throw new Error('所选组件在该方向空间不足，请先拉开两端组件')
  const positions=new Map<string,number>();let used=0
  selected.forEach((p,i)=>{positions.set(p.key,first[position]+used+Math.round(free*i/(selected.length-1)));used+=p[size]})
  const next=panels.map(p=>positions.has(p.key)?{...p,[position]:positions.get(p.key)!}:{...p})
  if(!validLayout(next,bounds))throw new Error('分布后会重叠或越界，已保留原布局')
  return next
}
