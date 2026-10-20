import type { ChartDefinition, Field } from '@/api/bi/chart'
import { saveChart } from '@/api/bi/chart'
import type { PanelChartConfig } from '@/api/bi/dashboard'
import type { TemplateBinding, TemplateSlot } from '@/utils/biTemplates'
import { analyzeBindingImpact as analyzeCore, isNumericFieldType, remapPanelConfig as remapCore, suggestPanelConfig as suggestCore } from '@/utils/biTemplateBindingCore'

export type TemplateBindingSource = 'chart' | 'dataset'
export { isNumericFieldType }
export function suggestPanelConfig(slot: TemplateSlot, fields: Field[]): PanelChartConfig {
  return suggestCore(slot, fields) as PanelChartConfig
}
export function analyzeBindingImpact(slot: TemplateSlot, config: PanelChartConfig, fields: Field[]): string[] {
  return analyzeCore(slot, config, fields)
}
export function remapPanelConfig(slot: TemplateSlot, config: PanelChartConfig, fields: Field[]): PanelChartConfig {
  return remapCore(slot, config, fields) as PanelChartConfig
}

export function bindingChartId(binding: TemplateBinding): number {
  if (!Number.isSafeInteger(binding.chartId) || (binding.chartId ?? 0) <= 0) throw new Error('绑定尚未生成图表引用')
  return binding.chartId!
}

export async function ensureBindingChart(binding: TemplateBinding, slot: TemplateSlot, dashboardName: string): Promise<TemplateBinding> {
  if (binding.source === 'chart') return binding
  if (!Number.isSafeInteger(binding.datasetId) || binding.datasetId! <= 0) throw new Error('请选择数据集')
  if (!binding.config?.metrics?.length && binding.config.type !== 'table') throw new Error('请完成字段映射')
  const chartName = `${dashboardName.trim() || '模板大屏'} · ${slot.label}`.slice(0, 200)
  const definition: ChartDefinition = {
    schemaVersion: 1,
    name: chartName,
    type: binding.config.type,
    query: {
      datasetId: binding.datasetId!,
      dimensions: binding.config.dimensions,
      metrics: binding.config.metrics,
      orderBys: binding.config.orderBys,
      limit: binding.config.limit,
      filterTree: binding.config.filterTree
    }
  }
  if (binding.chartId) {
    const updated = await saveChart(definition, binding.chartId, binding.chartVersion ?? 0)
    return { ...binding, chartId: updated.data.id, chartVersion: updated.data.version }
  }
  const created = await saveChart(definition, undefined, undefined, true)
  return { ...binding, chartId: created.data.id, chartVersion: created.data.version }
}
