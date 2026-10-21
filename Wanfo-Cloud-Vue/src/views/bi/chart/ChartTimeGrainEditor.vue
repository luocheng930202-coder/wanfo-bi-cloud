<template>
  <el-form-item v-if="dimension && (available || dimension.timeGrain)" label="时间粒度">
    <el-select :model-value="dimension.timeGrain||''" style="width:100%" @update:model-value="setGrain">
      <el-option value="" label="原始时间（不汇总）"/>
      <el-option v-for="grain in TIME_GRAINS" :key="grain.value" :value="grain.value" :label="grain.label" :disabled="!available || !!definition.advanced?.drillFields?.length"/>
    </el-select>
    <small class="time-hint">按源字段的日历时间汇总，不额外转换时区。周从周一开始，季度从 1/4/7/10 月开始；返回周期起始日期。未指定排序时按周期升序。</small>
    <small v-if="definition.advanced?.drillFields?.length" class="time-hint">需先清除下钻字段才能按时间汇总。</small>
  </el-form-item>
</template>
<script setup lang="ts">
import {computed} from 'vue'
import type {ChartDefinition,Field,TimeGrain} from '@/api/bi/chart'
import {TIME_GRAINS,temporalField,supportsTimeGrain} from '@/utils/biTimeGrain'
const props=defineProps<{definition:ChartDefinition;fields:Field[]}>()
const dimension=computed(()=>props.definition.query.dimensions[0])
const available=computed(()=>supportsTimeGrain(props.definition.type) && temporalField(props.fields.find(f=>f.name===dimension.value?.field)?.type||''))
function setGrain(value:string) {if(!dimension.value)return;if(value)dimension.value.timeGrain=value as TimeGrain;else delete dimension.value.timeGrain}
</script>
<style scoped>
.time-hint{display:block;margin-top:6px;color:var(--bi-muted,#667085);font-size:12px;line-height:1.5}
</style>
