<template>
 <div class="metric-style-editor"><div class="heading">度量格式与阈值 <el-button link :disabled="!available.length || rows.length>=20" @click="add">添加度量样式</el-button></div><p class="hint">独立格式优先于组件格式。阈值比较原始数值，按顺序命中第一条；空值不着色。共用数值轴仍采用组件格式。</p>
 <div v-for="(m,i) in rows" :key="i" class="metric-box"><el-select v-model="m.key" aria-label="样式度量"><el-option v-for="key in metrics" :key="key" :value="key" :label="key" :disabled="rows.some((row,j)=>j!==i && row.key===key)"/></el-select><p v-if="!metrics.includes(m.key)" class="stale">度量 {{ m.key }} 已失效，请更新选择或移除此样式。</p><el-button link type="danger" @click="remove(i)">移除度量样式</el-button>
 <DashboardNumberFormatEditor v-model="m.format" :appearance="appearance"/>
 <div class="heading">阈值颜色 <el-button link :disabled="m.rules.length>=8" @click="m.rules.push({op:'GTE',value:'0',color:'#F56C6C'})">添加阈值</el-button></div>
 <div v-for="(r,j) in m.rules" :key="j" class="rule"><el-select v-model="r.op" aria-label="阈值比较"><el-option v-for="(label,op) in operators" :key="op" :value="op" :label="label"/></el-select><el-input v-model="r.value" maxlength="100" placeholder="原始数值" aria-label="阈值原始数值"/><el-input v-model="r.color" type="color" aria-label="阈值颜色"/><el-button link type="danger" @click="m.rules.splice(j,1)">移除阈值</el-button></div>
 </div></div>
</template>
<script setup lang="ts">
import {computed} from 'vue'
import type {MetricStyle,DashboardAppearance} from '@/api/bi/dashboard'
import DashboardNumberFormatEditor from './DashboardNumberFormatEditor.vue'
const props=defineProps<{modelValue?:MetricStyle[];metrics:string[];appearance?:DashboardAppearance}>(),emit=defineEmits<{(e:'update:modelValue',v:MetricStyle[]):void}>()
const rows=computed(()=>props.modelValue || []),available=computed(()=>props.metrics.filter(k=>!rows.value.some(m=>m.key===k)))
const operators={GTE:'大于等于',GT:'大于',LTE:'小于等于',LT:'小于',EQ:'等于',NE:'不等于'}
function add(){if(available.value.length)emit('update:modelValue',[...rows.value,{key:available.value[0],rules:[]}])}
function remove(i:number){emit('update:modelValue',rows.value.filter((_,j)=>i!==j))}
</script>
<style scoped>.metric-style-editor{margin-top:24px}.heading{display:flex;align-items:center;justify-content:space-between;font-size:12px;font-weight:600;color:#526078;gap:6px}.hint,.stale{font-size:11px;line-height:1.8;color:#8993a5}.stale{color:#d97706}.metric-box{border:1px solid #e2e8f0;border-radius:6px;padding:10px;margin-top:12px}.rule{display:flex;flex-direction:column;gap:6px;border-top:1px solid #e2e8f0;padding-top:10px;margin-top:10px}</style>
