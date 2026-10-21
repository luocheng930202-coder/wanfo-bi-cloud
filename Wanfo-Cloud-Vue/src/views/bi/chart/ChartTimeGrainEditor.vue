<template>
  <el-form-item v-if="dimension && (available || dimension.timeGrain)" label="时间粒度">
    <el-select :model-value="dimension.timeGrain||''" style="width:100%" @update:model-value="setGrain">
      <el-option value="" label="原始时间（不汇总）"/>
      <el-option v-for="grain in TIME_GRAINS" :key="grain.value" :value="grain.value" :label="grain.label" :disabled="!available || !!definition.advanced?.drillFields?.length"/>
    </el-select>
    <small class="time-hint">按源字段的日历时间汇总，不额外转换时区。周从周一开始，季度从 1/4/7/10 月开始；返回周期起始日期。未指定排序时按周期升序。</small>
    <small v-if="definition.advanced?.drillFields?.length" class="time-hint">需先清除下钻字段才能按时间汇总。</small>
  </el-form-item>
  <el-form-item v-if="dimension?.timeGrain" label="缺失周期">
    <el-select :model-value="dimension.timeFill||'none'" style="width:100%" @update:model-value="v=>dimension!.timeFill=v">
      <el-option value="none" label="保持原结果（不补齐）"/>
      <el-option value="null" label="补空值（折线保留断点）"/>
      <el-option value="zero" label="补零（明确按 0 展示）"/>
    </el-select>
    <small class="time-hint">只补齐当前查询结果起止周期之间的缺口，已有零值和空值不变；空结果不生成记录。仅支持按时间排序。若结果可能截断或补齐后超出行数上限，将保留原结果并提示。</small>
  </el-form-item>
</template>
<script setup lang="ts">
import {computed} from 'vue'
import type {ChartDefinition,Field,TimeGrain} from '@/api/bi/chart'
import {TIME_GRAINS,temporalField,supportsTimeGrain} from '@/utils/biTimeGrain'
const props=defineProps<{definition:ChartDefinition;fields:Field[]}>()
const dimension=computed(()=>props.definition.query.dimensions[0])
const available=computed(()=>supportsTimeGrain(props.definition.type) && temporalField(props.fields.find(f=>f.name===dimension.value?.field)?.type||''))
function setGrain(value:string) {if(!dimension.value)return;if(value)dimension.value.timeGrain=value as TimeGrain;else {delete dimension.value.timeGrain;delete dimension.value.timeFill}}
</script>
<style scoped>
.time-hint{display:block;margin-top:6px;color:var(--bi-muted,#667085);font-size:12px;line-height:1.5}
</style>
