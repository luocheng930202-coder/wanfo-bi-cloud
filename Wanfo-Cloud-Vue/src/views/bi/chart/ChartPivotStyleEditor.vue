<template>
  <section v-if="definition.type==='pivot'" aria-label="透视表样式">
    <el-divider content-position="left">表头与布局</el-divider>
    <el-form-item label="行表头层次"><el-select :model-value="style.pivotHierarchy || 'grid'" @change="v=>style.pivotHierarchy=v"><el-option value="grid" label="平铺多层表头"/><el-option value="tree" label="可折叠树形表头" :disabled="!canTotal"/></el-select></el-form-item>
    <el-form-item label="指标放在列头"><el-switch :model-value="style.pivotValuesInCols!==false" @update:model-value="v=>style.pivotValuesInCols=!!v"/></el-form-item>
    <el-form-item label="数据行高"><el-input-number :model-value="style.pivotRowHeight || 32" :min="28" :max="80" :precision="0" @update:model-value="v=>style.pivotRowHeight=v"/></el-form-item>
    <el-form-item label="数据列宽"><el-input-number :model-value="style.pivotColumnWidth || 120" :min="60" :max="400" :precision="0" @update:model-value="v=>style.pivotColumnWidth=v"/></el-form-item>
    <el-form-item label="透视表头背景"><el-color-picker :model-value="style.pivotHeaderBackground || '#F1F5F9'" @update:model-value="v=>style.pivotHeaderBackground=v || undefined"/></el-form-item>
    <el-form-item label="透视表头文字"><el-color-picker :model-value="style.pivotHeaderColor || '#303133'" @update:model-value="v=>style.pivotHeaderColor=v || undefined"/></el-form-item>
    <el-divider content-position="left">当前结果汇总</el-divider>
    <el-alert v-if="!canTotal" title="AVG及去重计数需从明细重新聚合；当前组合仅支持平铺表头，不开放小计/总计。原始分组结果仍正常展示。" type="info" :closable="false"/>
    <el-form-item label="行总计"><el-switch :model-value="!!style.pivotRowTotals" :disabled="!canTotal" @update:model-value="v=>style.pivotRowTotals=!!v"/></el-form-item>
    <el-form-item label="列总计"><el-switch :model-value="!!style.pivotColTotals" :disabled="!canTotal" @update:model-value="v=>style.pivotColTotals=!!v"/></el-form-item>
    <el-form-item label="行层级小计"><el-switch :model-value="!!style.pivotRowSubtotals" :disabled="!canTotal || rows<2" @update:model-value="v=>style.pivotRowSubtotals=!!v"/></el-form-item>
    <el-form-item label="列层级小计"><el-switch :model-value="!!style.pivotColSubtotals" :disabled="!canTotal || cols<2" @update:model-value="v=>style.pivotColSubtotals=!!v"/></el-form-item>
    <el-form-item label="汇总放在前面"><el-switch :model-value="!!style.pivotTotalsFirst" @update:model-value="v=>style.pivotTotalsFirst=!!v"/></el-form-item>
    <p class="hint">SUM求和、COUNT累加分组计数、MIN/MAX取极值。树形父级节点也按此口径汇总。仅汇总已加载结果；查询达到行数上限可能不完整。小计需至少两层对应维度；更换为不支持的聚合时自动关闭汇总。</p>
    <el-divider content-position="left">条件色阶</el-divider>
    <el-form-item label="启用透视色阶"><el-switch :model-value="!!style.pivotColorScale" @update:model-value="v=>style.pivotColorScale=!!v"/></el-form-item>
    <template v-if="style.pivotColorScale">
      <el-form-item label="透视低值颜色"><el-color-picker :model-value="style.pivotLowColor || '#EFF6FF'" @update:model-value="v=>style.pivotLowColor=v || undefined"/></el-form-item>
      <el-form-item label="透视高值颜色"><el-color-picker :model-value="style.pivotHighColor || '#2563EB'" @update:model-value="v=>style.pivotHighColor=v || undefined"/></el-form-item>
      <p class="hint">按各指标当前结果范围独立着色，汇总单元格沿用该范围；不同单位不混用同一范围。空值不着色，文字阈值可在逐指标设置中配置。</p>
    </template>
  </section>
</template>
<script setup lang="ts">
import {computed,watch} from 'vue'
import type {ChartDefinition} from '@/api/bi/chart'
import {pivotTotalsAvailable} from '@/utils/biPivotPreview'
const props=defineProps<{definition:ChartDefinition}>()
const style=computed(()=>props.definition.style!)
const canTotal=computed(()=>pivotTotalsAvailable(props.definition))
const rows=computed(()=>props.definition.advanced?.pivotRowFields?.length || 0),cols=computed(()=>props.definition.advanced?.pivotColFields?.length || 0)
watch([canTotal,rows,cols],([available,r,c])=>{if(!available){if(style.value.pivotHierarchy==='tree')style.value.pivotHierarchy='grid';style.value.pivotRowTotals=false;style.value.pivotColTotals=false}if(!available || r<2)style.value.pivotRowSubtotals=false;if(!available || c<2)style.value.pivotColSubtotals=false})
</script>
<style scoped>.hint{font-size:12px;color:var(--bi-muted,#667085);line-height:1.7;margin:8px 0 18px}</style>
