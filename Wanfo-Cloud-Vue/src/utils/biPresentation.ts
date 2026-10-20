import type { DashboardAppearance, DashboardDefinition, NumberFormat, PanelStyle, MetricStyle, Decoration, ContainerStyle, Panel } from '@/api/bi/dashboard'

export function defaultAppearance(theme: 'light' | 'dark' = 'light'): DashboardAppearance {
  const dark = theme === 'dark'
  return { theme, background: dark ? '#111827' : '#F2F5F9', cardBackground: dark ? '#1F2937' : '#FFFFFF',
    textColor: dark ? '#F3F4F6' : '#303133', borderColor: dark ? '#4B5563' : '#DCE3EC',
    palette: 'business', titleSize: 14, radius: 6, decimals: 2, grouping: true }
}

export function normalizeDashboard(d: DashboardDefinition): DashboardDefinition {
  if (d.schemaVersion !== 1 || (d.kind && !['dashboard','screen'].includes(d.kind)) || (d.kind==='screen'?(d.layout!=='absolute' || !d.scene):(d.layout && d.layout!=='grid')) || !Array.isArray(d.panels)) {
    throw new Error('当前客户端不支持此看板协议，请升级后再编辑')
  }
  if(d.kind==='screen' && (!Number.isInteger(d.scene?.width) || !Number.isInteger(d.scene?.height) || d.scene!.width<320 || d.scene!.width>8192 || d.scene!.height<240 || d.scene!.height>8192 || !['contain','width','none'].includes(d.scene!.fit)))throw new Error('大屏尺寸或适配方式无效')
  for(const p of d.panels)if(p.componentType==='border' || p.componentType==='line'){if(d.kind!=='screen' || p.content?.text!=null || p.content?.image!=null)throw new Error('装饰组件仅支持大屏且不能携带文本或图片');normalizeDecoration(p.content?.decoration)}
  validateContainerLayout(d.panels,d.kind)
  const hidden=new Set(d.panels.filter(p=>p.hidden).map(p=>p.key)),visibility=new Map<string,boolean>()
  for(const p of d.panels){if(p.hidden!=null && typeof p.hidden!=='boolean')throw new Error('隐藏状态无效');if(p.hidden && d.kind!=='screen')throw new Error('仅大屏支持隐藏图层');if(p.groupKey){if(visibility.has(p.groupKey) && visibility.get(p.groupKey)!==!!p.hidden)throw new Error('组合须整体隐藏或显示');visibility.set(p.groupKey,!!p.hidden)}}
  if(d.interaction?.controls.some(c=>c.targets.some(t=>hidden.has(t.panel))) || d.interaction?.links.some(l=>hidden.has(l.sourcePanel) || l.targets.some(t=>hidden.has(t.panel))))throw new Error('隐藏前请先解除组件的筛选和联动绑定')
  const groups=new Map<string,number>()
  for(const p of d.panels)if(p.groupKey!=null){if(d.kind!=='screen' || !/^[A-Za-z0-9_-]{1,64}$/.test(p.groupKey))throw new Error('组合标识无效');groups.set(p.groupKey,(groups.get(p.groupKey)||0)+1)}
  if([...groups.values()].some(n=>n<2))throw new Error('组合须至少包含两个组件')
  const seconds=d.scene?.refreshSeconds ?? 0
  if(d.kind==='screen' && (!Number.isInteger(seconds) || (seconds!==0 && (seconds<10 || seconds>3600))))throw new Error('刷新间隔须为0或10至3600秒')
  if (d.panels.some(p => p.componentType && !['chart','kpi','text','image','border','line','container'].includes(p.componentType))) throw new Error('存在不支持的看板组件')
  return { ...d, interaction: {controls: [], links: [], ...d.interaction}, kind: d.kind || 'dashboard', layout: d.kind==='screen'?'absolute':'grid', scene:d.kind==='screen'?{...d.scene!}:undefined, appearance: { ...defaultAppearance(d.appearance?.theme), ...d.appearance },
    panels: d.panels.map(p => ({ ...p, componentType: p.componentType || 'chart', locked: p.locked ?? false, hidden:p.hidden ?? false, style: { showTitle: true, titleAlign: 'left', ...p.style } })) }
}

export function presentationVars(value?: DashboardAppearance) {
  const a = { ...defaultAppearance(value?.theme), ...value }
  return { '--bi-background': a.background, '--bi-card': a.cardBackground, '--bi-text': a.textColor,
    '--bi-border': a.borderColor, '--bi-radius': `${a.radius}px`, '--bi-title-size': `${a.titleSize}px` }
}

export function chartPalette(value?: DashboardAppearance) {
  if (value?.palette === 'ocean') return ['#38BDF8', '#2DD4BF', '#818CF8', '#60A5FA', '#A78BFA']
  if (value?.palette === 'warm') return ['#F59E0B', '#F97316', '#EF4444', '#A78BFA', '#EC4899']
  return ['#409EFF', '#67C23A', '#E6A23C', '#A071D9', '#F56C6C']
}

export function displayNumber(value: unknown, appearance?: DashboardAppearance) {
  if (!appearance || typeof value !== 'number' || !Number.isFinite(value)) return value
  return new Intl.NumberFormat('zh-CN', { useGrouping: appearance.grouping, maximumFractionDigits: appearance.decimals }).format(value)
}

/** Decimal integer arithmetic keeps large integer/decimal strings exact; presentation never changes query values. */
export function formatMetric(value:unknown,appearance?:DashboardAppearance,format?:NumberFormat|null):unknown {
  if(!format)return value==null?'—':displayNumber(value,appearance)
  if(value==null)return format.nullText
  if(typeof value!=='number' && typeof value!=='string')return value
  if(typeof value==='number' && !Number.isFinite(value))return value
  const text=String(value),match=/^([+-]?)([0-9]+)(?:\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?$/.exec(text)
  if(!match || text.length>500)return value
  const exponent=Number(match[4] || 0);if(Math.abs(exponent)>308)return value
  const decimals=Math.max(0,Math.min(6,Math.trunc(format.decimals))),shift=format.mode==='percent'?-2:({none:0,thousand:3,wan:4,million:6}[format.scale])
  let integer=BigInt(match[2]+(match[3] || ''));const power=decimals-(match[3]?.length || 0)+exponent-shift
  if(power>=0)integer*=10n**BigInt(power)
  else {const divisor=10n**BigInt(-power);integer=(integer+divisor/2n)/divisor}
  const digits=integer.toString().padStart(decimals+1,'0');let whole=decimals?digits.slice(0,-decimals):digits,fraction=decimals?digits.slice(-decimals):''
  if(!format.fixed)fraction=fraction.replace(/0+$/,'')
  if(format.grouping)whole=whole.replace(/\B(?=(\d{3})+(?!\d))/g,',')
  const unit=format.mode==='percent'?'%':({none:'',thousand:'千',wan:'万',million:'百万'}[format.scale])
  return format.prefix+(match[1]==='-' && integer!==0n?'-':'')+whole+(fraction?'.'+fraction:'')+unit+format.suffix
}

export function metricFormat(style:PanelStyle|undefined,key:string){return style?.metrics?.find(m=>m.key===key)?.format || style?.numberFormat}
function decimalParts(value:unknown):{integer:bigint;places:number}|null {
  if(typeof value!=='string' && typeof value!=='number' || typeof value==='number' && !Number.isFinite(value))return null
  const text=String(value),m=/^([+-]?)([0-9]+)(?:\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?$/.exec(text)
  if(!m || text.length>500 || Math.abs(Number(m[4]||0))>308)return null
  return {integer:BigInt((m[1]==='-'?'-':'')+m[2]+(m[3]||'')),places:(m[3]?.length||0)-Number(m[4]||0)}
}
export function metricColor(value:unknown,key:string,styles?:MetricStyle[]){
  const n=decimalParts(value);if(!n)return undefined
  for(const rule of styles?.find(m=>m.key===key)?.rules || []){const t=decimalParts(rule.value);if(!t)continue;const places=Math.max(n.places,t.places),a=n.integer*10n**BigInt(places-n.places),b=t.integer*10n**BigInt(places-t.places)
    const matched=rule.op==='GT'?a>b:rule.op==='GTE'?a>=b:rule.op==='LT'?a<b:rule.op==='LTE'?a<=b:rule.op==='EQ'?a===b:rule.op==='NE'?a!==b:false
    if(matched)return rule.color
  }return undefined
}

export function normalizeDecoration(value?:Partial<Decoration>):Decoration {
 const d={color:'#38BDF8',stroke:2,pattern:'solid' as const,radius:8,direction:'horizontal' as const,...value}
 if(!/^#[0-9a-fA-F]{6}$/.test(d.color) || !Number.isInteger(d.stroke) || d.stroke<1 || d.stroke>12 || !Number.isInteger(d.radius) || d.radius<0 || d.radius>80 || !['solid','dashed','dotted'].includes(d.pattern) || !['horizontal','vertical'].includes(d.direction))throw new Error('装饰样式超出支持范围')
 return d
}

export function normalizeContainer(value?:Partial<ContainerStyle>):ContainerStyle {
 const c={background:'#16243A',borderColor:'#38BDF8',stroke:1,radius:8,padding:12,...value}
 if(!(c.background==='transparent' || /^#[0-9a-fA-F]{6}$/.test(c.background)) || !/^#[0-9a-fA-F]{6}$/.test(c.borderColor) || !Number.isInteger(c.stroke) || c.stroke<0 || c.stroke>12 || !Number.isInteger(c.radius) || c.radius<0 || c.radius>80 || !Number.isInteger(c.padding) || c.padding<0 || c.padding>40)throw new Error('容器样式超出支持范围')
 return c
}
export function validateContainerLayout(panels:Panel[],kind?:string){
 const keys=new Set(panels.map(p=>p.key));if(keys.size!==panels.length)throw new Error('组件标识重复')
 for(const p of panels){
  if(p.componentType==='container' || p.parentKey!=null){if(kind!=='screen' || p.groupKey)throw new Error('容器及成员仅支持大屏且须先解组')}
  if(p.componentType==='container'){if(p.parentKey || p.content?.text!=null || p.content?.image!=null || p.content?.decoration!=null)throw new Error('不支持嵌套容器或混用内容');const c=normalizeContainer(p.content?.container);if(p.w<2*c.padding+20 || p.h<2*c.padding+20)throw new Error('容器尺寸不足以容纳内边距')}
  if(p.parentKey==null)continue
  const parent=panels.find(v=>v.key===p.parentKey),i=panels.indexOf(p),pi=parent?panels.indexOf(parent):-1
  if(!parent || parent===p || parent.componentType!=='container' || parent.parentKey || pi>=i)throw new Error('容器父级或层序无效')
  if(panels.slice(pi+1,i).some(v=>v.parentKey!==parent.key))throw new Error('容器成员须连续排列')
  const pad=normalizeContainer(parent.content?.container).padding
  if(p.x<parent.x+pad || p.y<parent.y+pad || p.x+p.w>parent.x+parent.w-pad || p.y+p.h>parent.y+parent.h-pad)throw new Error('成员超出容器内边距范围')
  if(parent.hidden && !p.hidden)throw new Error('请整体显示容器');if(parent.locked && !p.locked)throw new Error('请先解锁容器')
 }
}
