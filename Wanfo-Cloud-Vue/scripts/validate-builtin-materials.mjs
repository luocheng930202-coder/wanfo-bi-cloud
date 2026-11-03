import fs from 'fs'
import path from 'path'
import {fileURLToPath} from 'url'

const src = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '../src/utils/biBuiltinMaterials.ts'), 'utf8')
const images = [...src.matchAll(/image:"(data:image\/svg\+xml;base64,[A-Za-z0-9+/=]+)"/g)].map((m) => m[1])
const SVG_UNSAFE = /<script|javascript:|data:text\/html|foreignObject|<(?:iframe|embed|object|link|meta|base)\b|\bon[a-z]+\s*=/i
for (const image of images) {
  const bin = Buffer.from(image.slice(image.indexOf(',') + 1), 'base64')
  const xml = bin.toString('utf8').replace(/^\uFEFF/, '').trim()
  const lower = xml.toLowerCase()
  if (!(lower.startsWith('<svg') || lower.startsWith('<?xml')) || !lower.includes('<svg')) throw new Error('invalid svg start')
  if (!lower.includes('xmlns=') || !lower.includes('http://www.w3.org/2000/svg')) throw new Error('no xmlns: ' + xml.slice(0, 80))
  if (SVG_UNSAFE.test(xml)) throw new Error('unsafe')
  if (bin.length > 524288) throw new Error('too big')
}
console.log('validated', images.length, 'builtins')
