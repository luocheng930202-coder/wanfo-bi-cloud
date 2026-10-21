<template>
  <section aria-label="参考线配置">
    <el-divider content-position="left">参考线</el-divider>
    <p class="hint">固定值使用指标原始单位；均值、最小值和最大值基于当前查询返回的数据计算。</p>
    <div v-for="(line,i) in definition.style?.referenceLines || []" :key="i" class="reference-row">
      <el-form-item label="对应指标"><el-select v-model="line.key"><el-option v-for="(label,key) in metrics" :key="key" :value="key" :label="label"/></el-select></el-form-item>
      <el-form-item label="参考线类型"><el-select v-model="line.kind"><el-option value="fixed" label="固定目标值"/><el-option value="average" label="当前结果均值"/><el-option value="min" label="当前结果最小值"/><el-option value="max" label="当前结果最大值"/></el-select></el-form-item>
      <el-form-item v-if="line.kind==='fixed'" label="原始数值"><el-input-number v-model="line.value" :min="-1e12" :max="1e12"/></el-form-item>
      <el-form-item label="名称"><el-input v-model="line.name" maxlength="40" placeholder="目标值"/></el-form-item>
      <el-form-item label="颜色"><el-color-picker v-model="line.color"/></el-form-item>
      <el-form-item label="线型"><el-select v-model="line.lineType"><el-option value="solid" label="实线"/><el-option value="dashed" label="虚线"/><el-option value="dotted" label="点线"/></el-select></el-form-item>
      <el-button link type="danger" @click="definition.style!.referenceLines!.splice(i,1)">删除参考线</el-button>
    </div>
    <el-button size="small" :disabled="!Object.keys(metrics).length || (definition.style?.referenceLines?.length || 0)>=8" @click="add">添加参考线</el-button>
  </section>
</template>
<script setup lang="ts">
import {computed} from 'vue'
import type {ChartDefinition} from '@/api/bi/chart'
import {metricResultKey} from '@/utils/biEchartsPreview'
const props=defineProps<{definition:ChartDefinition;labels:Record<string,string>}>()
const metrics=computed(()=>Object.fromEntries(props.definition.query.metrics.map(m=>{const key=metricResultKey(m);return [key,props.labels[key] || key]})))
function add(){const key=Object.keys(metrics.value)[0];if(!key)return;const style=props.definition.style ||= {};const lines=style.referenceLines ||= [];lines.push({key,kind:'fixed',value:0,name:'目标值',color:'#F59E0B',lineType:'dashed'})}
</script>
<style scoped>.hint{font-size:12px;color:var(--bi-muted,#667085);line-height:1.7}.reference-row{padding:12px;background:#f6f8fc;border:1px solid #e7ebf2;border-radius:8px;margin-bottom:12px}</style>
