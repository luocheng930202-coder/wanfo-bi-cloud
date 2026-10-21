<template>
  <div class="chart-editor">
    <aside class="chart-editor__left"><ChartFieldPool :fields="fields" :disabled="disabled || busy" @pick-dimension="onPickDimension" @pick-metric="onPickMetric"/></aside>
    <main class="chart-editor__center">
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb8"/>
      <el-alert v-if="fields.length===0 && !loading" title="没有可用字段，请检查数据集和列权限" type="warning" :closable="false" class="mb8"/>
      <ChartPreview :result="result" :definition="definition" :labels="labels"/>
    </main>
    <aside class="chart-editor__right">
      <ChartPropertyPanel :definition="definition" :fields="fields" :datasets="datasets" :labels="labels" :disabled="disabled || busy" :previewing="previewing" :can-preview="canPreview" :field-label="fieldLabel" @type-changed="emit('type-changed')" @dataset-changed="id => emit('dataset-changed', id)" @set-dimension="f => emit('set-dimension', f)" @add-metric="emit('add-metric')" @remove-metric="i => emit('remove-metric', i)" @set-sort="f => emit('set-sort', f)" @raw-table="emit('raw-table')" @ensure-scatter="emit('ensure-scatter')" @pivot-fields="emit('pivot-fields')" @preview="emit('preview')"/>
    </aside>
  </div>
</template>
<script setup lang="ts">
import type { ChartDefinition, ChartResult, Field } from '@/api/bi/chart'
import type { DatasetSummary } from '@/api/bi/dataset'
import ChartFieldPool from './ChartFieldPool.vue'
import ChartPropertyPanel from './ChartPropertyPanel.vue'
import ChartPreview from './ChartPreview.vue'
defineProps<{ definition: ChartDefinition; fields: Field[]; datasets: DatasetSummary[]; labels: Record<string,string>; result: ChartResult | null; error: string; loading: boolean; disabled?: boolean; busy?: boolean; previewing?: boolean; canPreview?: boolean; fieldLabel: (n: string) => string }>()
const emit = defineEmits<{ (e:'type-changed'):void; (e:'dataset-changed', id:number):void; (e:'set-dimension', field:string):void; (e:'add-metric'):void; (e:'remove-metric', index:number):void; (e:'set-sort', field:string):void; (e:'raw-table'):void; (e:'ensure-scatter'):void; (e:'pivot-fields'):void; (e:'preview'):void; (e:'pick-dimension', field:string):void; (e:'pick-metric', field:string):void }>()
function onPickDimension(name: string) { emit('pick-dimension', name); emit('set-dimension', name) }
function onPickMetric(name: string) { emit('pick-metric', name) }
</script>
<style scoped>
.chart-editor{display:grid;grid-template-columns:240px minmax(0,1fr) min(360px,34vw);min-height:520px;border:1px solid #e7ebf2;border-radius:12px;overflow:hidden;background:#fff}
.chart-editor__right{max-height:calc(var(--bi-content-height,100vh) - 180px);overflow:auto}.chart-editor__center{padding:16px;min-width:0;background:#f4f6fa}
@media (max-width: 1100px){.chart-editor{grid-template-columns:1fr}.chart-editor__left{display:none}.chart-editor__right{max-height:none}.chart-editor__center{min-height:380px}}
</style>
