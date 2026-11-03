import type { Field } from '@/api/bi/chart'
import type { FilterControl, FilterValue } from '@/api/bi/dashboard'
export const templateFilterKinds = [
  { key: 'date', label: '统计日期', hint: '为每个组件选择对应业务日期，统一使用同一统计期间。' },
  { key: 'school', label: '学校', hint: '选择学校唯一编码字段，各数据集须采用相同编码体系。' },
  { key: 'region', label: '地区', hint: '选择同一行政层级的地区编码字段，不用展示名称替代编码。' }
] as const
export type TemplateFilterKey = typeof templateFilterKinds[number]['key']
export type TemplateFilterSelection = Partial<Record<TemplateFilterKey, boolean>>
export type TemplateFilterMappings = Record<string, Partial<Record<TemplateFilterKey, string>>>
export type TemplateFilterDefaults = Partial<Record<TemplateFilterKey, FilterValue>>
function defaultValue(key:TemplateFilterKey, type:string, value:FilterValue|undefined):FilterValue {
  if(value == null || value === '')return null
  if(key === 'date') {
    if(!Array.isArray(value) || value.length!==2 || !value.every(item=>typeof item==='string' && /^\d{4}-\d{2}-\d{2}$/.test(item) && Number.isFinite(Date.parse(item)) && new Date(item).toISOString().slice(0,10)===item) || String(value[0])>String(value[1]))throw Error('统计日期默认值须为有效且升序的起止日期')
    return [...value] as FilterValue
  }
  if(Array.isArray(value))throw Error('学校和地区默认值须为单个编码')
  if(type==='number' && (!/^[+-]?\d+(\.\d+)?$/.test(String(value)) || String(value).replace(/[^0-9]/g,'').length>30 || (String(value).split('.')[1]?.length || 0)>12))throw Error('数值编码默认值须为最多 30 位数字、小数位不超过 12 位的数字')
  if(type==='text' && (typeof value!=='string' || value.length>256))throw Error('文本编码默认值须为不超过 256 字的文本')
  return value
}
function family(type:string):string {
  if (/(int|decimal|numeric|float|double|real)/i.test(type)) return 'number'
  if (type.toUpperCase() === 'DATE') return 'date'
  if (/(char|text|string)/i.test(type)) return 'text'
  return 'unsupported'
}

export function templateFilterControls(
  slots: {key:string;label:string}[], panelKeys: string[], enabled: TemplateFilterSelection,
  mappings: TemplateFilterMappings, fields: Record<string, Field[]>, defaults:TemplateFilterDefaults = {}
): FilterControl[] {
  if (!templateFilterKinds.some(kind => enabled[kind.key])) return []
  if (!slots.length || slots.length > 20 || panelKeys.length !== slots.length || new Set(panelKeys).size !== panelKeys.length) throw Error('筛选须绑定 1～20 个有效组件，且组件与模板一致')
  return templateFilterKinds.filter(kind => enabled[kind.key]).map(kind => ({
    key: 'template-' + kind.key, label: kind.label, type: kind.key === 'date' ? 'date' : 'enum', defaultValue: defaultValue(kind.key,family(fields[slots[0].key]?.find(item=>item.name===mappings[slots[0].key]?.[kind.key])?.type || ''),defaults[kind.key]),
    targets: slots.map((slot, index) => {
      const field = mappings[slot.key]?.[kind.key]
      const metadata = fields[slot.key]?.find(item => item.name === field)
      if (!field || !metadata) throw Error(`${slot.label}：请为“${kind.label}”选择当前可见字段`)
      if (kind.key === 'date' && family(metadata.type) !== 'date') throw Error(`${slot.label}：统一日期筛选须使用 DATE 字段，请先把时间戳或文本日期转换为日期字段`)
      if (kind.key !== 'date' && !['text','number'].includes(family(metadata.type))) throw Error(`${slot.label}：${kind.label}须使用文本或数值编码字段`)
      const first = fields[slots[0].key]?.find(item => item.name === mappings[slots[0].key]?.[kind.key])
      if (first && family(first.type) !== family(metadata.type)) throw Error(`${kind.label}：各组件的编码字段类型须一致，请统一为文本或数值类型`)
      return {panel: panelKeys[index], field}
    })
  }))
}
