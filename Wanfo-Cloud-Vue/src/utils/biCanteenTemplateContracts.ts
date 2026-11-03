import type { PanelChartConfig, NumberFormat } from '@/api/bi/dashboard'
import type { Field } from '@/api/bi/chart'
import { isNumericFieldType } from './biTemplateBindingCore'

// These contracts apply only to the curated canteen layout, never to arbitrary imported slot keys.
export const CANTEEN_TEMPLATE_ID = 'market-market-screen-school-canteen-2d'
export function canteenNumberFormat(key: string): NumberFormat | undefined {
  const suffixes: Record<string, string> = { 'purchase-total': '元', 'meal-count': '人次', 'school-count': '所', 'acceptance-rate': '', 'purchase-category': '元', 'expense-structure': '元', 'meal-trend': '人次' }
  if (!(key in suffixes)) return
  return { mode: key === 'acceptance-rate' ? 'percent' : 'number', scale: 'none', decimals: ['meal-count', 'school-count', 'meal-trend'].includes(key) ? 0 : 2, grouping: true, fixed: true, prefix: '', suffix: suffixes[key], nullText: '暂无数据' }
}
export const canteenContracts: Record<string, { fields: string; measure: string; unit: string; filter: string; agg?: string }> = {
  'purchase-total': { fields: '采购金额（数值）', measure: '对采购明细金额求和；先确认订单与明细没有重复关联', unit: '元，可按展示需要换算万元', filter: '采购日期、学校、地区；排除取消订单', agg: 'SUM' },
  'meal-count': { fields: '用餐人次（数值）', measure: '对用餐人次求和；同一学生多餐计多次', unit: '人次', filter: '用餐日期、学校、地区；排除撤销记录', agg: 'SUM' },
  'school-count': { fields: '学校唯一编码', measure: '学校编码去重计数；不能对学校名称求和', unit: '所', filter: '与其他组件保持相同统计期间和地区范围', agg: 'COUNT_DISTINCT' },
  'acceptance-rate': { fields: '验收合格标识（数值，合格为 1，不合格为 0）', measure: '每行一条有效验收记录，对 0/1 标识取平均；不能平均各校已汇总的合格率', unit: '百分比，原值为 0～1', filter: '验收日期、学校、地区；排除待验收与撤销记录', agg: 'AVG' },
  'purchase-category': { fields: '食材分类（维度）＋采购金额（数值）', measure: '按食材分类汇总采购金额', unit: '元', filter: '沿用采购金额指标的日期与有效订单范围', agg: 'SUM' },
  'school-region': { fields: '行政区编码（维度）＋业务度量', measure: '选择省、市或县对应的编码；明确展示学校数、金额或人次', unit: '随所选度量配置', filter: '按当前用户地区权限及统一统计期间过滤' },
  'expense-structure': { fields: '费用分类（维度）＋费用金额（数值）', measure: '按费用分类汇总；分类必须互斥，避免重复计费', unit: '元', filter: '费用日期、学校、地区；明确是否含税及退款口径', agg: 'SUM' },
  'meal-trend': { fields: '用餐日期（时间维度）＋用餐人次（数值）', measure: '按日期升序汇总用餐人次；统一日、周或月粒度', unit: '人次', filter: '统一时区、统计期间、学校和地区范围', agg: 'SUM' },
  'acceptance-events': { fields: '采购单号、学校、验收日期、验收状态等明细字段', measure: '一行对应一个约定的明细粒度；不以查询条数代替业务总量', unit: '金额字段为元', filter: '按业务日期和当前学校/地区权限过滤；明确明细截断条数' }
}

export function canteenBindingError(key: string, config: PanelChartConfig, fields: Field[]): string | undefined {
  const contract = canteenContracts[key]
  if (!contract) return
  if (contract.agg && (config.metrics.length !== 1 || config.metrics[0].agg !== contract.agg)) {
    return `此槽位要求一个 ${contract.agg} 度量。${contract.measure}`
  }
  if (['purchase-category', 'school-region', 'expense-structure', 'meal-trend'].includes(key) && config.dimensions.length !== 1) {
    return '此槽位要求一个分类、行政区编码或时间维度，请按槽位说明调整字段映射'
  }
  const known = new Map(fields.map(field => [field.name, field]))
  const used = [...config.dimensions, ...config.metrics].map(item => item.field)
  if (used.some(name => !known.has(name))) return '绑定字段不存在或当前用户不可见，请重新选择数据字段'
  if (config.metrics.some(metric => !['COUNT', 'COUNT_DISTINCT'].includes(metric.agg) && !isNumericFieldType(known.get(metric.field)?.type || ''))) {
    return '金额、人次及验收标识的聚合字段必须为数值类型，请先在数据集中转换字段类型'
  }
  if (key === 'meal-trend') {
    const dimension = config.dimensions[0]
    if (!/(date|time)/i.test(known.get(dimension.field)?.type || '')) return '用餐趋势必须使用日期或时间类型字段；文本日期请先在数据集中转换'
    if (!config.orderBys.some(order => order.field === dimension.field && order.order.toUpperCase() === 'ASC')) return '用餐趋势须按时间维度升序排列'
  }
}
