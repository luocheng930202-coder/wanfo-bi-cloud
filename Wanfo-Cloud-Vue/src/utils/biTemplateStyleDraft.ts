import type { DashboardDefinition } from '@/api/bi/dashboard'
import {parseLayoutTemplate,type LayoutTemplateFile} from './biTemplateFiles'

/** Small, self-contained navigation snapshot. Contains no panels, bindings or resource credentials. */
export function serializeTemplateStyle(file:LayoutTemplateFile):string {
  const checked=parseLayoutTemplate(JSON.stringify(file))
  return JSON.stringify({format:'bi-style-draft',version:1,name:checked.name,kind:checked.format==='datacenter-screen-template'?'screen':'dashboard',appearance:checked.appearance,...('scene' in checked?{scene:{...checked.scene,refreshSeconds:0}}:{})})
}

export function parseTemplateStyleDraft(raw:string):DashboardDefinition {
  if(raw.length>8192)throw Error('风格初始化信息过大')
  let value
  try{value=JSON.parse(raw)}catch{throw Error('风格初始化信息不是有效 JSON')}
  if(!value || typeof value!=='object' || Array.isArray(value) || Object.keys(value).some(key=>!['format','version','name','kind','appearance','scene'].includes(key)) || value.format!=='bi-style-draft' || value.version!==1 || !['screen','dashboard'].includes(value.kind))throw Error('风格初始化信息无效')
  const screen=value.kind==='screen'
  if(!screen && value.scene!==undefined)throw Error('看板风格不能携带大屏场景配置')
  // Reuse the strict template presentation validator; the sentinel never enters the draft.
  const file=parseLayoutTemplate(JSON.stringify({format:screen?'datacenter-screen-template':'datacenter-dashboard-template',version:1,name:value.name,appearance:value.appearance,...(screen?{scene:value.scene}:{}),panels:[{key:'style-validation',title:'',componentType:'text',x:0,y:0,w:screen?40:2,h:screen?40:2,locked:false,...(screen?{hidden:false}:{}),style:{showTitle:true,titleAlign:'left'},content:{text:''}}]}))
  return {schemaVersion:1,name:(file.name+' · 自由设计').slice(0,200),kind:screen?'screen':'dashboard',layout:screen?'absolute':'grid',appearance:file.appearance,...('scene' in file?{scene:{...file.scene,refreshSeconds:0}}:{}),panels:[],interaction:{controls:[],links:[]}}
}
