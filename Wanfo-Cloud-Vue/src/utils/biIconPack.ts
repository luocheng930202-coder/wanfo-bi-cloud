/** Curated icon pack: library chips + runtime PNG data URLs (backend rejects SVG). */
export interface IconPackItem {id:string;label:string;glyph:string;bg:string}

export const iconPack:IconPackItem[]=[
  {id:'chart',label:'图表',glyph:'📊',bg:'#3563E9'},
  {id:'trend',label:'趋势',glyph:'📈',bg:'#0EA5E9'},
  {id:'alert',label:'告警',glyph:'⚠',bg:'#F59E0B'},
  {id:'ok',label:'达标',glyph:'✓',bg:'#10B981'},
  {id:'user',label:'用户',glyph:'人',bg:'#6366F1'},
  {id:'money',label:'金额',glyph:'¥',bg:'#D97706'},
  {id:'time',label:'时间',glyph:'时',bg:'#64748B'},
  {id:'loc',label:'位置',glyph:'地',bg:'#EF4444'},
  {id:'star',label:'亮点',glyph:'★',bg:'#EAB308'},
  {id:'gear',label:'配置',glyph:'设',bg:'#475569'},
  {id:'cloud',label:'云端',glyph:'云',bg:'#0284C7'},
  {id:'lock',label:'安全',glyph:'锁',bg:'#7C3AED'},
]

export function iconPngDataUrl(item:IconPackItem,size=128):string {
  const canvas=document.createElement('canvas')
  canvas.width=size;canvas.height=size
  const ctx=canvas.getContext('2d')
  if(!ctx)return 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ZkAAAAASUVORK5CYII='
  ctx.fillStyle=item.bg
  ctx.beginPath()
  const r=size*0.18
  ctx.moveTo(r,0);ctx.arcTo(size,0,size,size,r);ctx.arcTo(size,size,0,size,r);ctx.arcTo(0,size,0,0,r);ctx.arcTo(0,0,size,0,r);ctx.closePath();ctx.fill()
  ctx.fillStyle='#FFFFFF'
  ctx.font=`bold ${Math.round(size*0.38)}px "Segoe UI Symbol","PingFang SC","Microsoft YaHei",sans-serif`
  ctx.textAlign='center'
  ctx.textBaseline='middle'
  ctx.fillText(item.glyph.slice(0,2),size/2,size/2+1)
  return canvas.toDataURL('image/png')
}
