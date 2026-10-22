import type {DashboardInput,DashboardView,FilterValue,PanelResult,ViewControl} from '@/api/bi/dashboard'

export interface DashboardRuntimeState extends DashboardInput {draft:Record<string,FilterValue>;drills:NonNullable<DashboardInput['drills']>}
export type InteractionAction={type:'apply'}|{type:'reset'}|{type:'links-clear'}|{type:'filter-clear';key:string}|{type:'link-clear';key:string}|{type:'link-set';values:DashboardInput['links']}
const copy=<T>(value:T):T=>JSON.parse(JSON.stringify(value))
export function dashboardDefaults(controls:ViewControl[]=[]):Record<string,FilterValue>{return Object.fromEntries(controls.map(c=>[c.key,copy(c.defaultValue??null)]))}
export function dashboardTransition(state:DashboardRuntimeState,action:InteractionAction,controls:ViewControl[]=[]):DashboardRuntimeState {
  const next=copy(state)
  if(action.type==='reset'){next.draft=dashboardDefaults(controls);next.filters=copy(next.draft);next.links={};next.drills={}}
  else if(action.type==='apply'){next.filters=copy(next.draft);next.links={};next.drills={}}
  else if(action.type==='filter-clear'){next.draft[action.key]=null;next.filters[action.key]=null;next.links={};next.drills={}}
  else {if(action.type==='links-clear')next.links={};else if(action.type==='link-clear')delete next.links[action.key];else Object.assign(next.links,action.values);next.drills={}}
  return next
}
export function hasFilterValue(value:FilterValue|undefined){return Array.isArray(value)?value.some(v=>v!=null && v!==''):value!=null && value!==''}
export function filterValueLabel(value:FilterValue):string {
  if(Array.isArray(value))return value.map(v=>v==null || v===''?'不限':String(v)).join(' ～ ')
  return value==null?'空值':String(value)
}
export function sameFilterState(a:Record<string,FilterValue>,b:Record<string,FilterValue>){
  const keys=[...new Set([...Object.keys(a),...Object.keys(b)])].sort();return keys.every(k=>JSON.stringify(a[k]??null)===JSON.stringify(b[k]??null))
}
export function dashboardConditionChips(view:DashboardView|null|undefined,state:DashboardInput,payloads:Record<string,PanelResult>={}){
  const chips:{id:string;kind:'filter'|'link'|'drill';key:string;label:string}[]=[]
  if(!view)return chips
  for(const c of view.controls||[])if(hasFilterValue(state.filters[c.key]))chips.push({id:'filter:'+c.key,kind:'filter',key:c.key,label:'筛选 · '+c.label+'：'+filterValueLabel(state.filters[c.key])})
  for(const link of view.links||[])if(Object.prototype.hasOwnProperty.call(state.links,link.key)){
    const title=view.panels.find(p=>p.key===link.sourcePanel)?.title || '图表'
    chips.push({id:'link:'+link.key,kind:'link',key:link.key,label:'联动 · '+title+'：'+filterValueLabel(state.links[link.key])})
  }
  for(const panel of view.panels){const path=state.drills?.[panel.key];if(!Array.isArray(path) || !path.length)continue;const crumbs=payloads[panel.key]?.data.mapContext?.breadcrumbs||[]
    const names=path.map(s=>crumbs.find(c=>String(c.code)===String(s.value))?.name || filterValueLabel(s.value))
    chips.push({id:'drill:'+panel.key,kind:'drill',key:panel.key,label:'下钻 · '+(panel.title||'图表')+'：'+names.join(' › ')})
  }return chips
}
