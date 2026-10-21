<template>
  <section aria-label="逐指标样式" class="metric-settings">
    <el-divider content-position="left">逐指标设置</el-divider>
    <el-empty v-if="!metrics.length" description="请先添加指标" :image-size="45"/>
    <template v-else>
      <el-form-item label="配置指标"><el-select v-model="selectedKey" aria-label="配置指标"><el-option v-for="m in metrics" :key="m.key" :value="m.key" :label="labels[m.key] || m.key"/></el-select></el-form-item>
      <p class="hint">格式绑定当前字段与聚合；移除或更换指标会清除对应设置。仅改变显示，不修改查询和联动原值。</p>
      <div class="presets"><el-button size="small" @click="preset('number')">数量</el-button><el-button size="small" @click="preset('money')">金额（万元）</el-button><el-button size="small" @click="preset('percent')">百分比</el-button></div>
      <DashboardNumberFormatEditor :model-value="entry?.format" @update:model-value="v=>patch({format:v})"/>
      <el-form-item v-if="['bar','hbar','stack','percent','line','area','combo'].includes(definition.type)" label="系列颜色"><el-color-picker :model-value="entry?.color" aria-label="指标系列颜色" @update:model-value="v=>patch({color:v || undefined})"/></el-form-item>
      <template v-if="definition.type==='combo'">
        <el-form-item label="系列图形"><el-select :model-value="entry?.seriesType || (selectedIndex===0?'bar':'line')" aria-label="系列图形" @change="v=>patch({seriesType:v})"><el-option value="bar" label="柱状"/><el-option value="line" label="折线"/></el-select></el-form-item>
        <el-form-item label="坐标轴归属"><el-select :model-value="entry?.axis || (selectedIndex===0?'left':'right')" aria-label="坐标轴归属" @change="v=>patch({axis:v})"><el-option value="left" label="左轴"/><el-option value="right" label="右轴"/></el-select></el-form-item>
        <p class="hint">不同单位建议分配到不同轴；同轴格式不一致时轴标签显示原始数值，提示框仍按指标格式显示。</p>
      </template>
      <el-button link @click="resetMetric">恢复当前指标默认</el-button>
      <template v-if="supportsRules">
        <el-divider content-position="left">指标阈值颜色</el-divider>
        <p class="hint">按原始数值比较，第一条命中的规则生效。<template v-if="['line','area','combo'].includes(definition.type)">折线按数据点着色，需开启显示数据点。</template></p>
        <div v-for="(rule,i) in entry?.rules || []" :key="i" class="rule-row">
          <el-select v-model="rule.op" aria-label="阈值比较"><el-option v-for="(label,key) in comparisons" :key="key" :value="key" :label="label"/></el-select>
          <el-input v-model="rule.value" maxlength="100" placeholder="原始数值" aria-label="阈值原始数值"/>
          <el-color-picker v-model="rule.color" aria-label="阈值颜色"/><el-button link type="danger" @click="removeRule(i)">删除</el-button>
        </div>
        <el-button size="small" :disabled="(entry?.rules?.length || 0)>=8" @click="patch({rules:[...(entry?.rules || []),{op:'GTE',value:'0',color:'#F56C6C'}]})">添加阈值</el-button>
      </template>
    </template>
    <template v-if="definition.type==='combo'">
      <el-divider content-position="left">左右轴范围</el-divider>
      <template v-for="axis in axes" :key="axis.key">
        <el-form-item :label="axis.label+'最小值'"><el-input-number :model-value="definition.style?.comboAxes?.[axis.key]?.min" :min="-1e12" :max="1e12" placeholder="自动" @update:model-value="v=>setAxis(axis.key,'min',v)"/></el-form-item>
        <el-form-item :label="axis.label+'最大值'"><el-input-number :model-value="definition.style?.comboAxes?.[axis.key]?.max" :min="-1e12" :max="1e12" placeholder="自动" @update:model-value="v=>setAxis(axis.key,'max',v)"/></el-form-item>
      </template>
      <p class="hint">范围使用原始数值；百分比原值 0～1 对应显示 0%～100%。清空后自动计算。</p>
    </template>
  </section>
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {ChartDefinition,ChartMetricStyle} from '@/api/bi/chart'
import {metricResultKey} from '@/utils/biEchartsPreview'
import DashboardNumberFormatEditor from '../dashboard/DashboardNumberFormatEditor.vue'
const props=defineProps<{definition:ChartDefinition;labels:Record<string,string>}>()
const metrics=computed(()=>props.definition.query.metrics.map(m=>({key:metricResultKey(m)})))
const selectedKey=ref('')
watch(metrics,values=>{if(!values.some(m=>m.key===selectedKey.value))selectedKey.value=values[0]?.key || ''},{immediate:true})
const selectedIndex=computed(()=>metrics.value.findIndex(m=>m.key===selectedKey.value))
const entry=computed(()=>props.definition.style?.metrics?.find(m=>m.key===selectedKey.value))
const supportsRules=computed(()=>['bar','hbar','stack','line','area','combo','pie','ring','funnel','table','pivot'].includes(props.definition.type))
const comparisons={GT:'大于',GTE:'大于等于',LT:'小于',LTE:'小于等于',EQ:'等于',NE:'不等于'}
const axes=[{key:'left' as const,label:'左轴'},{key:'right' as const,label:'右轴'}]
function patch(value:Partial<ChartMetricStyle>){if(!selectedKey.value)return;const style=props.definition.style ||= {};const all=style.metrics ||= [];const current=all.find(m=>m.key===selectedKey.value);if(current)Object.assign(current,value);else all.push({key:selectedKey.value,...value})}
function preset(kind:string){patch({format:{mode:kind==='percent'?'percent':'number',scale:kind==='money'?'wan':'none',decimals:kind==='number'?0:2,grouping:true,fixed:true,prefix:'',suffix:kind==='money'?'元':'',nullText:'—'}})}
function resetMetric(){if(props.definition.style?.metrics)props.definition.style.metrics=props.definition.style.metrics.filter(m=>m.key!==selectedKey.value)}
function removeRule(index:number){patch({rules:entry.value?.rules?.filter((_,i)=>i!==index)})}
function setAxis(axis:'left'|'right',bound:'min'|'max',value:number|undefined){const style=props.definition.style ||= {};const axes=style.comboAxes ||= {};const range=axes[axis] ||= {};range[bound]=value}
</script>
<style scoped>.hint{font-size:12px;color:var(--bi-muted,#667085);line-height:1.7}.presets{display:flex;flex-wrap:wrap;gap:6px}.presets .el-button{margin:0}.rule-row{display:flex;flex-wrap:wrap;align-items:center;gap:6px;margin-bottom:12px}.rule-row>.el-select,.rule-row>.el-input{width:100%}</style>
