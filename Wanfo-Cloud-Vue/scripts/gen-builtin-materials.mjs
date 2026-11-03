import fs from 'fs'
import path from 'path'
import {fileURLToPath} from 'url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const dir = path.join(root, 'src/assets/bi/materials')
const out = path.join(root, 'src/utils/biBuiltinMaterials.ts')

const items = [
  {id: 'board-1', label: '边框 1', category: 'board', file: 'board_1.svg', w: 420, h: 280, fill: '#38BDF8'},
  {id: 'board-2', label: '边框 2', category: 'board', file: 'board_2.svg', w: 420, h: 280, fill: '#38BDF8'},
  {id: 'board-3', label: '边框 3', category: 'board', file: 'board_3.svg', w: 420, h: 280, fill: '#38BDF8'},
  {id: 'board-4', label: '边框 4', category: 'board', file: 'board_4.svg', w: 420, h: 280, fill: '#38BDF8'},
  {id: 'board-5', label: '边框 5', category: 'board', file: 'board_5.svg', w: 420, h: 280, fill: '#38BDF8'},
  {id: 'board-6', label: '边框 6', category: 'board', file: 'board_6.svg', w: 420, h: 280, fill: '#38BDF8'},
  {id: 'board-7', label: '边框 7', category: 'board', file: 'board_7.svg', w: 420, h: 280, fill: '#38BDF8'},
  {id: 'board-8', label: '边框 8', category: 'board', file: 'board_8.svg', w: 420, h: 280, fill: '#38BDF8'},
  {id: 'board-9', label: '边框 9', category: 'board', file: 'board_9.svg', w: 420, h: 280, fill: '#38BDF8'},
  {id: 'shape-rect', label: '矩形', category: 'shape', file: 'graphical-rect.svg', w: 240, h: 180, fill: '#38BDF8'},
  {id: 'shape-circle', label: '圆形', category: 'shape', file: 'graphical-circular.svg', w: 180, h: 180, fill: '#38BDF8'},
  {id: 'shape-triangle', label: '三角形', category: 'shape', file: 'graphical-triangle.svg', w: 180, h: 160, fill: '#38BDF8'},
]

function toDataUrl(svg, fill) {
  let xml = svg.trim().replace(/^\uFEFF/, '')
  if (fill && /<svg\b/i.test(xml)) {
    xml = xml.replace(/<svg\b([^>]*)>/i, (m, attrs) => {
      if (/\sfill\s*=/i.test(attrs)) return m
      return `<svg fill="${fill}"${attrs}>`
    })
  }
  return 'data:image/svg+xml;base64,' + Buffer.from(xml, 'utf8').toString('base64')
}

const lines = [
  '/** Built-in screen materials from DataEase static SVG boards/shapes. Insert as image — no upload. */',
  "export type BuiltinMaterialCategory = 'board' | 'shape'",
  'export interface BuiltinMaterial {id:string;label:string;category:BuiltinMaterialCategory;w:number;h:number;image:string}',
  '',
  'export const builtinMaterials:BuiltinMaterial[]=[',
]

for (const it of items) {
  const svg = fs.readFileSync(path.join(dir, it.file), 'utf8')
  const image = toDataUrl(svg, it.fill)
  if (image.length > 720000) throw new Error(`${it.id} too large: ${image.length}`)
  lines.push(
    `  {id:${JSON.stringify(it.id)},label:${JSON.stringify(it.label)},category:${JSON.stringify(it.category)},w:${it.w},h:${it.h},image:${JSON.stringify(image)}},`,
  )
  console.log(it.id, 'chars', image.length)
}

lines.push(']')
lines.push('')
lines.push("export const builtinBoards=builtinMaterials.filter(m=>m.category==='board')")
lines.push("export const builtinShapes=builtinMaterials.filter(m=>m.category==='shape')")
lines.push('')

fs.writeFileSync(out, lines.join('\n'), 'utf8')
console.log('wrote', out, fs.statSync(out).size)
