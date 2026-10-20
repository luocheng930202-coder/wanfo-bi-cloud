<template>
  <el-collapse class="mb8"><el-collapse-item title="看板主题与样式" name="appearance">
    <div class="appearance-fields">
      <el-form-item label="主题"><el-select :model-value="value.theme" @change="changeTheme"><el-option label="浅色" value="light"/><el-option label="深色" value="dark"/></el-select></el-form-item>
      <el-form-item v-for="field in colors" :key="field.key" :label="field.label"><el-color-picker :model-value="value[field.key]" color-format="hex" @update:model-value="(color:string|null) => color && update(field.key,color)"/></el-form-item>
      <el-form-item label="图表配色"><el-select :model-value="value.palette" @change="(v:unknown) => update('palette',v)"><el-option label="商务" value="business"/><el-option label="海洋" value="ocean"/><el-option label="暖色" value="warm"/></el-select></el-form-item>
      <el-form-item label="标题字号"><el-input-number :model-value="value.titleSize" :min="12" :max="24" :precision="0" @change="(v:unknown) => update('titleSize',v)"/></el-form-item>
      <el-form-item label="卡片圆角"><el-input-number :model-value="value.radius" :min="0" :max="24" :precision="0" @change="(v:unknown) => update('radius',v)"/></el-form-item>
      <el-form-item label="数值最大小数位"><el-input-number :model-value="value.decimals" :min="0" :max="6" :precision="0" @change="(v:unknown) => update('decimals',v)"/></el-form-item>
      <el-form-item label="数字千分位"><el-switch :model-value="value.grouping" @change="(v:unknown) => update('grouping',v)"/></el-form-item>
    </div>
    <span class="format-note">数字格式仅影响显示，不改变原始数据、聚合与权限。主题切换会重置颜色，可继续自定义。</span>
  </el-collapse-item></el-collapse>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardAppearance } from '@/api/bi/dashboard'
import { defaultAppearance } from '@/utils/biPresentation'
const props=defineProps<{modelValue?:DashboardAppearance}>()
const emit=defineEmits<{(event:'update:modelValue',value:DashboardAppearance):void}>()
const value=computed(()=>props.modelValue || defaultAppearance())
const colors=[{key:'background',label:'画布背景'},{key:'cardBackground',label:'卡片背景'},{key:'textColor',label:'文字颜色'},{key:'borderColor',label:'边框颜色'}] as const
function update(key:keyof DashboardAppearance,v:unknown){if(v!==undefined && v!==null)emit('update:modelValue',{...value.value,[key]:v})}
function changeTheme(theme:'light'|'dark'){
  const d=defaultAppearance(theme)
  emit('update:modelValue',{...value.value,theme,background:d.background,cardBackground:d.cardBackground,textColor:d.textColor,borderColor:d.borderColor})
}
</script>
<style scoped>.appearance-fields{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:0 16px}.format-note{color:#606266;font-size:12px}</style>
