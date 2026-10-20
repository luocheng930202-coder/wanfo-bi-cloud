<template>
  <div v-if="type==='number'" class="range-input">
    <el-input :model-value="range[0]" inputmode="decimal" maxlength="48" :aria-label="label+'起值'" placeholder="起值" @update:model-value="v=>setRange(0,v)"/><span>至</span><el-input :model-value="range[1]" inputmode="decimal" maxlength="48" :aria-label="label+'止值'" placeholder="止值" @update:model-value="v=>setRange(1,v)"/>
  </div>
  <el-date-picker v-else-if="type==='date'" :model-value="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" :aria-label="label" @update:model-value="v=>emit('update:modelValue',v || null)"/>
  <el-select v-else-if="type==='enum' && !author" :model-value="modelValue" clearable filterable :loading="loading" :aria-label="label" placeholder="选择候选值" @visible-change="v=>v && emit('options')" @update:model-value="v=>emit('update:modelValue',v===''?null:v)"><el-option v-for="(v,i) in options" :key="i" :value="v" :label="String(v)"/></el-select>
  <el-input v-else :model-value="scalar" clearable maxlength="256" :aria-label="label" :placeholder="author?'默认值（可留空）':'精确匹配，留空不过滤'" @update:model-value="v=>emit('update:modelValue',v===''?null:v)"/>
</template>
<script setup lang="ts">
import {computed} from 'vue'
import type {FilterValue,FilterControl} from '@/api/bi/dashboard'
const props=defineProps<{modelValue:FilterValue;type:FilterControl['type'];label:string;author?:boolean;options?:(string|number)[];loading?:boolean}>()
const emit=defineEmits<{(e:'update:modelValue',v:FilterValue):void;(e:'options'):void}>()
const scalar=computed(()=>Array.isArray(props.modelValue)?'':props.modelValue ?? '')
const dateRange=computed(()=>Array.isArray(props.modelValue) && props.modelValue.every(v=>typeof v==='string')?props.modelValue as string[]:null)
const range=computed(()=>Array.isArray(props.modelValue)?props.modelValue:[null,null])
function setRange(i:number,v:string|undefined|null){const next=[...range.value] as [string|number|null,string|number|null];next[i]=v==='' || v==null?null:v;emit('update:modelValue',next.every(v=>v===null)?null:next)}
</script>
<style scoped>.range-input{display:flex;align-items:center;gap:6px}.range-input :deep(.el-input){width:120px}</style>
