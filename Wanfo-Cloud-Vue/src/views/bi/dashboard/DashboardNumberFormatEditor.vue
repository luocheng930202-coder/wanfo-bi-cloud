<template>
 <div class="format-editor"><div class="heading">数值格式 <el-switch :model-value="!!modelValue" aria-label="启用组件数值格式" @change="toggle"/></div><p class="hint">仅影响度量显示，原始查询值和联动值保持不变。</p>
 <template v-if="modelValue"><el-form-item label="显示方式"><el-select v-model="modelValue.mode" aria-label="指标显示方式" @change="modelValue!.scale='none'"><el-option value="number" label="数值"/><el-option value="percent" label="百分比（原值 × 100）"/></el-select></el-form-item>
 <el-form-item label="单位缩放"><el-select v-model="modelValue.scale" aria-label="指标单位缩放" :disabled="modelValue.mode==='percent'"><el-option v-for="(label,key) in units" :key="key" :value="key" :label="label"/></el-select></el-form-item>
 <el-form-item label="小数位"><el-input-number v-model="modelValue.decimals" :min="0" :max="6" :precision="0" aria-label="指标小数位"/></el-form-item>
 <el-form-item label="补齐小数位"><el-switch v-model="modelValue.fixed" aria-label="补齐指标小数位"/></el-form-item><el-form-item label="千分位"><el-switch v-model="modelValue.grouping" aria-label="指标千分位"/></el-form-item>
 <el-form-item label="前缀"><el-input v-model="modelValue.prefix" maxlength="20" aria-label="指标前缀"/></el-form-item><el-form-item label="后缀"><el-input v-model="modelValue.suffix" maxlength="20" aria-label="指标后缀"/></el-form-item><el-form-item label="空值显示"><el-input v-model="modelValue.nullText" maxlength="20" aria-label="指标空值显示"/></el-form-item>
 <p class="sample">示例：{{ formatMetric('12345.678',appearance,modelValue) }}<br/>空值：{{ formatMetric(null,appearance,modelValue) }}</p></template></div>
</template>
<script setup lang="ts">
import type {NumberFormat,DashboardAppearance} from '@/api/bi/dashboard'
import {formatMetric} from '@/utils/biPresentation'
const props=defineProps<{modelValue?:NumberFormat|null;appearance?:DashboardAppearance}>(),emit=defineEmits<{(e:'update:modelValue',v:NumberFormat|undefined):void}>()
const units={none:'不缩放',thousand:'千（÷ 1000）',wan:'万（÷ 10000）',million:'百万（÷ 1000000）'}
function toggle(enabled:string|number|boolean){emit('update:modelValue',enabled===true?{mode:'number',scale:'none',decimals:props.appearance?.decimals ?? 2,grouping:props.appearance?.grouping ?? true,fixed:true,prefix:'',suffix:'',nullText:'—'}:undefined)}
</script>
<style scoped>.format-editor{margin-top:24px}.heading{display:flex;align-items:center;justify-content:space-between;font-size:12px;font-weight:600;color:#526078}.hint,.sample{font-size:11px;line-height:1.8;color:#8993a5}.sample{padding:10px;background:#f4f7fc;border-radius:6px}</style>
