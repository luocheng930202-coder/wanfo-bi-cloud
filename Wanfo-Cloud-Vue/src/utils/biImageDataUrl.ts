/** Client-side image data-URL rules aligned with DashboardContent / asset upload. */
export const IMAGE_MAX_BYTES = 524288
export const IMAGE_MAX_CHARS = 720000
export const IMAGE_ACCEPT = 'image/png,image/jpeg,image/webp,image/gif,image/svg+xml'
export const IMAGE_ACCEPT_TYPES = ['image/png','image/jpeg','image/webp','image/gif','image/svg+xml'] as const
const SVG_UNSAFE = /<script|javascript:|data:text\/html|foreignObject|<(?:iframe|embed|object|link|meta|base)\b|\bon[a-z]+\s*=/i

export function validateImageDataUrl(image:string):string {
  if(!image)throw Error('图片内容不能为空')
  if(image.length>IMAGE_MAX_CHARS || !/^data:image\/(png|jpeg|webp|gif|svg\+xml);base64,[A-Za-z0-9+/]+={0,2}$/.test(image))
    throw Error('仅支持不超过 512KB 的 PNG/JPEG/WebP/GIF/SVG 图片')
  let bytes:Uint8Array
  try{const bin=atob(image.slice(image.indexOf(',')+1));bytes=Uint8Array.from(bin,c=>c.charCodeAt(0))}catch{throw Error('图片编码无效')}
  if(!bytes.length || bytes.length>IMAGE_MAX_BYTES)throw Error('图片内容与格式不匹配')
  const png=image.startsWith('data:image/png;') && bytes.length>=8 && bytes[0]===137 && bytes[1]===80 && bytes[2]===78 && bytes[3]===71 && bytes[4]===13 && bytes[5]===10 && bytes[6]===26 && bytes[7]===10
  const jpeg=image.startsWith('data:image/jpeg;') && bytes.length>=3 && bytes[0]===255 && bytes[1]===216 && bytes[2]===255
  const webp=image.startsWith('data:image/webp;') && bytes.length>=12 && bytes[0]===82 && bytes[1]===73 && bytes[2]===70 && bytes[3]===70 && bytes[8]===87 && bytes[9]===69 && bytes[10]===66 && bytes[11]===80
  const gif=image.startsWith('data:image/gif;') && bytes.length>=6 && bytes[0]===71 && bytes[1]===73 && bytes[2]===70 && bytes[3]===56 && (bytes[4]===55 || bytes[4]===57) && bytes[5]===97
  if(image.startsWith('data:image/svg+xml;')){
    const xml=new TextDecoder().decode(bytes).replace(/^\uFEFF/,'').trim()
    const lower=xml.toLowerCase()
    if(!(lower.startsWith('<svg') || lower.startsWith('<?xml')) || !lower.includes('<svg'))throw Error('SVG 内容无效')
    if(!lower.includes('xmlns=') || !lower.includes('http://www.w3.org/2000/svg'))throw Error('SVG 内容无效')
    if(SVG_UNSAFE.test(xml))throw Error('SVG 含有不安全内容')
    return image
  }
  if(!(png||jpeg||webp||gif))throw Error('图片内容与格式不匹配')
  return image
}

export async function readImageFileAsDataUrl(file:File):Promise<string> {
  if(!IMAGE_ACCEPT_TYPES.includes(file.type as typeof IMAGE_ACCEPT_TYPES[number]) && !/\.(png|jpe?g|webp|gif|svg)$/i.test(file.name))
    throw Error('请选择 PNG/JPEG/WebP/GIF/SVG 图片')
  if(file.size>IMAGE_MAX_BYTES)throw Error('图片不能超过 512KB')
  const dataUrl=await new Promise<string>((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(String(r.result));r.onerror=reject;r.readAsDataURL(file)})
  // Some browsers read SVG as utf8 data URL; normalize to base64 form expected by backend.
  if(dataUrl.startsWith('data:image/svg+xml;') && !dataUrl.includes(';base64,')){
    const raw=decodeURIComponent(dataUrl.slice(dataUrl.indexOf(',')+1))
    const b64=btoa(unescape(encodeURIComponent(raw)))
    return validateImageDataUrl('data:image/svg+xml;base64,'+b64)
  }
  return validateImageDataUrl(dataUrl)
}
