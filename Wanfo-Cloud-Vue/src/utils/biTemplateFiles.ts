import type {DashboardDefinition,Panel,PanelStyle,NumberFormat,DashboardAppearance} from '@/api/bi/dashboard'
import type {ChartDefinition} from '@/api/bi/chart'
import {CHART_TYPES} from '@/utils/biChartTypes'
import {normalizeDashboard,normalizeContainer,normalizeDecoration} from './biPresentation'
import {templateSlotPanel,type TemplateBinding,type ScreenTemplate} from './biTemplates'

export const TEMPLATE_MAX_BYTES=2*1024*1024
export const TEMPLATE_MAX_IMAGES=5
export const TEMPLATE_IMAGE_MAX_BYTES=262144
type FilePanel=Omit<Panel,'chartId'|'chartConfig'> & {chartType?:ChartDefinition['type']}
export interface ScreenTemplateFile {format:'datacenter-screen-template';version:1;name:string;panels:FilePanel[];scene:NonNullable<DashboardDefinition['scene']>;appearance:DashboardAppearance}
export interface DashboardTemplateFile {format:'datacenter-dashboard-template';version:1;name:string;panels:FilePanel[];appearance:DashboardAppearance}
export type LayoutTemplateFile=ScreenTemplateFile|DashboardTemplateFile
type Obj=Record<string,any>
function object(v:unknown,keys:string[],label:string):Obj {
  if(!v || typeof v!=='object' || Array.isArray(v) || Object.keys(v).some(k=>!keys.includes(k)))throw Error(label+'结构或字段不受支持')
  return v as Obj
}
function text(v:unknown,max:number,label:string,empty=false):string {if(typeof v!=='string' || v.length>max || (!empty && !v.trim()))throw Error(label+'无效');return v}
function integer(v:unknown,min:number,max:number,label:string):number {if(!Number.isInteger(v) || (v as number)<min || (v as number)>max)throw Error(label+'超出范围');return v as number}
function choice<T extends string>(v:unknown,values:readonly T[],label:string):T {if(!values.includes(v as T))throw Error(label+'不受支持');return v as T}
function bool(v:unknown,label:string):boolean {if(typeof v!=='boolean')throw Error(label+'无效');return v}
function color(v:unknown,label:string):string {if(typeof v!=='string' || !/^#[0-9a-fA-F]{6}$/.test(v))throw Error(label+'须为六位颜色');return v}
function key(v:unknown):string {if(typeof v!=='string' || !/^[A-Za-z0-9_-]{1,64}$/.test(v))throw Error('组件或组合标识无效');return v}
function numberFormat(v:unknown):NumberFormat {
  const n=object(v,['mode','scale','decimals','grouping','fixed','prefix','suffix','nullText'],'数值格式')
  if(n.mode==='percent' && n.scale!=='none')throw Error('百分比不能同时缩放单位')
  if([n.prefix,n.suffix,n.nullText].some(v=>typeof v==='string' && /[\u0000-\u001f\u007f-\u009f]/.test(v)))throw Error('格式文本不能包含控制字符')
  return {mode:choice(n.mode,['number','percent'],'数值模式'),scale:choice(n.scale,['none','thousand','wan','million'],'单位'),decimals:integer(n.decimals,0,6,'小数位'),grouping:bool(n.grouping,'千分位'),fixed:bool(n.fixed,'补零'),prefix:text(n.prefix,20,'前缀',true),suffix:text(n.suffix,20,'后缀',true),nullText:text(n.nullText,20,'空值文本',true)}
}
function style(v:unknown):PanelStyle {
  const s=object(v,['showTitle','titleAlign','numberFormat'],'组件样式')
  return {showTitle:bool(s.showTitle,'标题显示'),titleAlign:choice(s.titleAlign,['left','center','right'],'标题对齐'),...(s.numberFormat?{numberFormat:numberFormat(s.numberFormat)}:{})}
}
function appearance(v:unknown):DashboardAppearance {
  const a=object(v,['theme','background','cardBackground','textColor','borderColor','palette','titleSize','radius','decimals','grouping'],'主题')
  return {theme:choice(a.theme,['light','dark'],'主题'),background:color(a.background,'背景'),cardBackground:color(a.cardBackground,'卡片背景'),textColor:color(a.textColor,'文字颜色'),borderColor:color(a.borderColor,'边框颜色'),palette:choice(a.palette,['business','ocean','warm'],'配色'),titleSize:integer(a.titleSize,12,24,'标题字号'),radius:integer(a.radius,0,24,'圆角'),decimals:integer(a.decimals,0,6,'小数位'),grouping:bool(a.grouping,'千分位')}
}
function packedImage(v:unknown):string {
  if(typeof v!=='string' || !v)throw Error('模板图片不能为空')
  if(v.length>350000 || !/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}$/.test(v))throw Error('仅支持不超过 256KB 的 PNG/JPEG/WebP 图片')
  let bytes:Uint8Array
  try{const bin=atob(v.slice(v.indexOf(',')+1));bytes=Uint8Array.from(bin,c=>c.charCodeAt(0))}catch{throw Error('图片编码无效')}
  const png=v.startsWith('data:image/png;') && bytes.length>=8 && bytes[0]===137 && bytes[1]===80 && bytes[2]===78 && bytes[3]===71 && bytes[4]===13 && bytes[5]===10 && bytes[6]===26 && bytes[7]===10
  const jpeg=v.startsWith('data:image/jpeg;') && bytes.length>=3 && bytes[0]===255 && bytes[1]===216 && bytes[2]===255
  const webp=v.startsWith('data:image/webp;') && bytes.length>=12 && bytes[0]===82 && bytes[1]===73 && bytes[2]===70 && bytes[3]===70 && bytes[8]===87 && bytes[9]===69 && bytes[10]===66 && bytes[11]===80
  if(bytes.length>TEMPLATE_IMAGE_MAX_BYTES || !(png||jpeg||webp))throw Error('图片内容与格式不匹配')
  return v
}
function countPackedImages(panels:FilePanel[]){if(panels.filter(p=>p.componentType==='image').length>TEMPLATE_MAX_IMAGES)throw Error('模板最多打包5张图片')}
function parsedValue(raw:string):unknown {
  if(new TextEncoder().encode(raw).length>TEMPLATE_MAX_BYTES)throw Error('模板文件不能超过2MB')
  let value:unknown;try{value=JSON.parse(raw)}catch{throw Error('模板文件不是有效JSON')}
  const stack:[unknown,number][]=[[value,0]];let count=0
  while(stack.length){const [v,depth]=stack.pop()!;if(++count>4000 || depth>12)throw Error('模板结构过于复杂');if(v && typeof v==='object')for(const [k,item] of Object.entries(v)){if(['__proto__','constructor','prototype'].includes(k))throw Error('模板含不安全字段');stack.push([item,depth+1])}}
  return value
}
function parseScreenRoot(value:unknown):ScreenTemplateFile {
  const f=object(value,['format','version','name','panels','scene','appearance'],'模板文件')
  if(f.format!=='datacenter-screen-template' || f.version!==1)throw Error('不支持的模板格式或版本，请使用本项目v1模板')
  const scene=object(f.scene,['width','height','fit','refreshSeconds'],'画布')
  if(scene.refreshSeconds!==0)throw Error('模板自动刷新须关闭')
  const size={width:integer(scene.width,320,8192,'宽度'),height:integer(scene.height,240,8192,'高度'),fit:choice(scene.fit,['contain','width','none'],'适配'),refreshSeconds:0}
  if(!Array.isArray(f.panels) || f.panels.length<1 || f.panels.length>20)throw Error('模板须包含1至20个组件')
  const panels:FilePanel[]=f.panels.map((value:unknown)=>{
    const p=object(value,['key','title','componentType','x','y','w','h','style','content','locked','hidden','groupKey','parentKey','chartType'],'组件')
    const type=choice(p.componentType,['chart','kpi','text','image','border','line','container'],'组件类型')
    const panel:FilePanel={key:key(p.key),title:text(p.title,200,'组件标题',true),componentType:type,x:integer(p.x,0,size.width,'横坐标'),y:integer(p.y,0,size.height,'纵坐标'),w:integer(p.w,20,size.width,'组件宽度'),h:integer(p.h,20,size.height,'组件高度'),style:style(p.style),locked:bool(p.locked,'锁定状态'),hidden:bool(p.hidden,'隐藏状态')}
    if(panel.x+panel.w>size.width || panel.y+panel.h>size.height)throw Error('组件超出画布')
    if(p.groupKey!=null)panel.groupKey=key(p.groupKey);if(p.parentKey!=null)panel.parentKey=key(p.parentKey)
    if(type==='chart' || type==='kpi'){
      panel.chartType=choice<ChartDefinition['type']>(p.chartType,CHART_TYPES,'图表类型');if(type==='kpi' && panel.chartType!=='table')throw Error('指标卡仅支持单值表格聚合');if(p.content!=null)throw Error('数据槽不能携带内容')
    }else{
      if(p.chartType!=null)throw Error('静态组件不能携带图表类型')
      if(type==='image'){const c=object(p.content,['image'],'组件内容');panel.content={image:packedImage(c.image)}}
      else if(type==='text'){const c=object(p.content,['text'],'组件内容');panel.content={text:text(c.text,2000,'文本',true)}}
      else if(type==='container'){const c=object(p.content,['container'],'组件内容');const x=object(c.container,['background','borderColor','stroke','radius','padding'],'容器');panel.content={container:normalizeContainer(x)}}
      else{const c=object(p.content,['decoration'],'组件内容');const x=object(c.decoration,['color','stroke','pattern','radius','direction'],'装饰');panel.content={decoration:normalizeDecoration(x)}}
    }
    return panel
  })
  countPackedImages(panels)
  const file:ScreenTemplateFile={format:'datacenter-screen-template',version:1,name:text(f.name,200,'模板名称'),panels,scene:size,appearance:appearance(f.appearance)}
  normalizeDashboard({schemaVersion:1,name:file.name,kind:'screen',layout:'absolute',scene:file.scene,appearance:file.appearance,panels,interaction:{controls:[],links:[]}})
  return file
}
function parseDashboardRoot(value:unknown):DashboardTemplateFile {
  const f=object(value,['format','version','name','panels','appearance'],'模板文件')
  if(f.format!=='datacenter-dashboard-template' || f.version!==1)throw Error('不支持的模板格式或版本，请使用本项目v1看板模板')
  if(!Array.isArray(f.panels) || f.panels.length<1 || f.panels.length>20)throw Error('模板须包含1至20个组件')
  const panels:FilePanel[]=f.panels.map((value:unknown)=>{
    const p=object(value,['key','title','componentType','x','y','w','h','style','content','locked','chartType'],'组件')
    const type=choice(p.componentType,['chart','kpi','text','image'],'组件类型')
    const panel:FilePanel={key:key(p.key),title:text(p.title,200,'组件标题',true),componentType:type,x:integer(p.x,0,10,'横坐标'),y:integer(p.y,0,118,'纵坐标'),w:integer(p.w,2,12,'组件宽度'),h:integer(p.h,2,12,'组件高度'),style:style(p.style),locked:bool(p.locked,'锁定状态')}
    if(panel.x+panel.w>12 || panel.y+panel.h>120)throw Error('组件超出栅格')
    if(type==='chart' || type==='kpi'){
      panel.chartType=choice<ChartDefinition['type']>(p.chartType,CHART_TYPES,'图表类型');if(type==='kpi' && panel.chartType!=='table')throw Error('指标卡仅支持单值表格聚合');if(p.content!=null)throw Error('数据槽不能携带内容')
    }else{
      if(p.chartType!=null)throw Error('静态组件不能携带图表类型')
      if(type==='image'){const c=object(p.content,['image'],'组件内容');panel.content={image:packedImage(c.image)}}
      else{const c=object(p.content,['text'],'组件内容');panel.content={text:text(c.text,2000,'文本',true)}}
    }
    return panel
  })
  countPackedImages(panels)
  if(new Set(panels.map(p=>p.key)).size!==panels.length)throw Error('组件标识重复')
  if(!panels.every((p,i)=>panels.slice(i+1).every(o=>p.x>=o.x+o.w||p.x+p.w<=o.x||p.y>=o.y+o.h||p.y+p.h<=o.y)))throw Error('图表组件不能重叠')
  const file:DashboardTemplateFile={format:'datacenter-dashboard-template',version:1,name:text(f.name,200,'模板名称'),panels,appearance:appearance(f.appearance)}
  normalizeDashboard({schemaVersion:1,name:file.name,kind:'dashboard',layout:'grid',appearance:file.appearance,panels,interaction:{controls:[],links:[]}})
  return file
}
export function parseLayoutTemplate(raw:string):LayoutTemplateFile {
  const value=parsedValue(raw)
  const format=value && typeof value==='object' && !Array.isArray(value)?(value as {format?:unknown}).format:undefined
  if(format==='datacenter-dashboard-template')return parseDashboardRoot(value)
  return parseScreenRoot(value)
}
/** Strict local-file parser: no executable content, remote images or source bindings. */
export function parseTemplateFile(raw:string):ScreenTemplateFile {
  const file=parseLayoutTemplate(raw)
  if(file.format!=='datacenter-screen-template')throw Error('不支持的模板格式或版本，请使用本项目v1模板')
  return file
}
export function parseDashboardTemplateFile(raw:string):DashboardTemplateFile {
  const file=parseLayoutTemplate(raw)
  if(file.format!=='datacenter-dashboard-template')throw Error('不支持的模板格式或版本，请使用本项目v1看板模板')
  return file
}
function staticContent(p:Panel):NonNullable<FilePanel['content']> {
  if(p.componentType==='image')return {image:packedImage(p.content?.image)}
  if(p.componentType==='text')return {text:p.content?.text || ''}
  if(p.componentType==='container')return {container:normalizeContainer(p.content?.container)}
  return {decoration:normalizeDecoration(p.content?.decoration)}
}
function exportScreenFile(document:DashboardDefinition,types:Record<string,ChartDefinition['type']>):ScreenTemplateFile {
  const d=normalizeDashboard(JSON.parse(JSON.stringify(document))),keys=new Map(d.panels.map((p,i)=>[p.key,'p'+(i+1)])),groups=new Map<string,string>()
  const panels=d.panels.map(p=>{
    if(p.groupKey && !groups.has(p.groupKey))groups.set(p.groupKey,'g'+(groups.size+1))
    const result:FilePanel={key:keys.get(p.key)!,title:p.title,componentType:p.componentType,x:p.x,y:p.y,w:p.w,h:p.h,locked:!!p.locked,hidden:!!p.hidden,style:{showTitle:p.style?.showTitle!==false,titleAlign:p.style?.titleAlign || 'left',...(p.style?.numberFormat?{numberFormat:p.style.numberFormat}:{})}}
    if(p.parentKey)result.parentKey=keys.get(p.parentKey);if(p.groupKey)result.groupKey=groups.get(p.groupKey)
    if(p.componentType==='chart' || p.componentType==='kpi')result.chartType=p.componentType==='kpi'?'table':p.chartConfig?.type || types[p.key] || 'table'
    else result.content=staticContent(p)
    return result
  })
  countPackedImages(panels)
  return parseTemplateFile(JSON.stringify({format:'datacenter-screen-template',version:1,name:d.name,panels,scene:{...d.scene!,refreshSeconds:0},appearance:d.appearance}))
}
function exportDashboardFile(document:DashboardDefinition,types:Record<string,ChartDefinition['type']>):DashboardTemplateFile {
  if(document.panels.some(p=>p.componentType==='border' || p.componentType==='line' || p.componentType==='container' || p.groupKey || p.parentKey || p.hidden))throw Error('看板模板不支持装饰、容器、组合或隐藏图层')
  const d=normalizeDashboard(JSON.parse(JSON.stringify(document))),keys=new Map(d.panels.map((p,i)=>[p.key,'p'+(i+1)]))
  const panels=d.panels.map(p=>{
    const result:FilePanel={key:keys.get(p.key)!,title:p.title,componentType:p.componentType,x:p.x,y:p.y,w:p.w,h:p.h,locked:!!p.locked,style:{showTitle:p.style?.showTitle!==false,titleAlign:p.style?.titleAlign || 'left',...(p.style?.numberFormat?{numberFormat:p.style.numberFormat}:{})}}
    if(p.componentType==='chart' || p.componentType==='kpi')result.chartType=p.componentType==='kpi'?'table':p.chartConfig?.type || types[p.key] || 'table'
    else result.content=p.componentType==='image'?{image:packedImage(p.content?.image)}:{text:p.content?.text || ''}
    return result
  })
  countPackedImages(panels)
  return parseDashboardTemplateFile(JSON.stringify({format:'datacenter-dashboard-template',version:1,name:d.name,panels,appearance:d.appearance}))
}
/** Export layout only; reject unsupported assets instead of silently dropping them. */
export function exportTemplateFile(document:DashboardDefinition,types:Record<string,ChartDefinition['type']>={}):LayoutTemplateFile {
  if(document.kind==='screen')return exportScreenFile(document,types)
  return exportDashboardFile(document,types)
}
export function importedTemplate(file:LayoutTemplateFile):ScreenTemplate {
  const dashboard=file.format==='datacenter-dashboard-template'
  return {schemaVersion:1,id:'imported-file',name:file.name,category:dashboard?'导入看板模板':'导入模板',description:'本地文件布局 · 使用前须重新绑定数据',theme:file.appearance.theme,kind:dashboard?'dashboard':'screen',slots:file.panels.filter(p=>p.componentType==='chart' || p.componentType==='kpi').map(p=>({key:p.key,label:p.title,kind:p.componentType as 'chart'|'kpi',type:p.chartType!,x:p.x,y:p.y,w:p.w,h:p.h}))}
}
export function instantiateImportedTemplate(file:LayoutTemplateFile,name:string,bindings:Record<string,TemplateBinding>,factory:()=>string=()=>crypto.randomUUID()):DashboardDefinition {
  const checked=parseLayoutTemplate(JSON.stringify(file)),template=importedTemplate(checked),dashboard=checked.format==='datacenter-dashboard-template'
  text(name.trim(),200,dashboard?'看板名称':'大屏名称')
  if(Object.keys(bindings).some(k=>!template.slots.some(s=>s.key===k)))throw Error('存在未知绑定槽')
  const keys=new Map(checked.panels.map(p=>[p.key,key(factory())])),groups=new Map<string,string>()
  const panels:Panel[]=checked.panels.map(p=>{
    const slot=template.slots.find(s=>s.key===p.key),binding=bindings[p.key]
    if(slot && (!binding || !Number.isSafeInteger(binding.chartId) || binding.chartId!<=0 || (!binding.config?.metrics?.length && binding.config.type!=='table')))throw Error('请完整绑定并验证所有数据组件')
    if(slot?.kind==='kpi' && (binding.config.type!=='table' || binding.config.dimensions.length || binding.config.metrics.length!==1))throw Error('指标卡须绑定单个聚合度量')
    const {chartType,...layout}=p;const panel={...layout,...(slot?{chartId:binding.chartId,chartConfig:templateSlotPanel(slot,binding).chartConfig}:{}),key:keys.get(p.key)!}
    if(p.parentKey)panel.parentKey=keys.get(p.parentKey)
    if(p.groupKey){if(!groups.has(p.groupKey))groups.set(p.groupKey,key(factory()));panel.groupKey=groups.get(p.groupKey)}
    return panel
  })
  if(new Set([...keys.values(),...groups.values()]).size!==keys.size+groups.size)throw Error('生成的组件或组合标识重复')
  return normalizeDashboard({schemaVersion:1,name:name.trim(),kind:dashboard?'dashboard':'screen',layout:dashboard?'grid':'absolute',scene:checked.format==='datacenter-screen-template'?checked.scene:undefined,appearance:checked.appearance,panels,interaction:{controls:[],links:[]}})
}
