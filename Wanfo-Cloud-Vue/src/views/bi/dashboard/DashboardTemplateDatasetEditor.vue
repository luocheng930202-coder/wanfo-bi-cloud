<template>
  <div class="chart-config-entry">
    <el-tag size="small" effect="plain">数据集直绑</el-tag>
    <el-button :disabled="disabled" @click="open">配置字段映射</el-button>
  </div>
  <el-dialog v-model="visible" title="数据集字段映射" width="min(820px,calc(100vw - 24px))" :close-on-click-modal="false">
    <el-alert title="直接选择当前数据集字段。保存验证时会创建或更新一张专用图表供大屏组件引用，不会修改其他已保存图表。" type="info" :closable="false"/>
    <p class="source">数据集 #{{ datasetId }}</p>
    <el-alert v-if="impact.length" :title="impact.join('；')" type="warning" :closable="false"/>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <el-form v-if="draft && !loading" label-position="top" :disabled="applying || disabled" class="config-form">
      <el-form-item label="图表类型"><el-select v-model="draft.type" aria-label="组件图表类型" :disabled="slot.kind==='kpi'"><el-option v-for="(label,type) in types" :key="type" :value="type" :label="label"/></el-select></el-form-item>
      <el-form-item v-if="slot.kind!=='kpi'" label="维度（按选择顺序显示）"><el-select :model-value="draft.dimensions.map(d=>d.field)" multiple filterable aria-label="组件维度" @change="setDimensions"><el-option v-for="f in fields" :key="f.name" :value="f.name" :label="f.showName || f.name"/></el-select></el-form-item>
      <div class="section-title">度量 <el-button link :disabled="draft.metrics.length>=maxMetricsForType(slot.kind==='kpi'?'table':draft.type)" @click="draft.metrics.push({field:'',agg:'SUM',alias:'metric_'+(draft.metrics.length+1)})">添加度量</el-button></div>
      <div v-for="(m,i) in draft.metrics" :key="i" class="config-row"><el-select v-model="m.field" filterable aria-label="组件度量字段"><el-option v-for="f in fields" :key="f.name" :value="f.name" :label="f.showName || f.name"/></el-select><el-select v-model="m.agg" aria-label="组件聚合方式"><el-option v-for="(label,key) in aggregations" :key="key" :value="key" :label="label"/></el-select><el-input v-model="m.alias" placeholder="输出别名" aria-label="组件度量别名"/><el-button link type="danger" @click="draft.metrics.splice(i,1)">移除</el-button></div>
      <div class="section-title">排序 <el-button link @click="draft.orderBys.push({field:'',order:'asc'})">添加排序</el-button></div>
      <div v-for="(o,i) in draft.orderBys" :key="i" class="config-row"><el-select v-model="o.field" aria-label="组件排序字段"><el-option v-for="f in outputs" :key="f" :value="f" :label="f"/></el-select><el-select v-model="o.order" aria-label="组件排序方向"><el-option value="asc" label="升序"/><el-option value="desc" label="降序"/></el-select><el-button link type="danger" @click="draft.orderBys.splice(i,1)">移除</el-button></div>
      <el-form-item label="最大返回行数"><el-input-number v-model="draft.limit" :min="1" :max="1000" :precision="0" aria-label="组件最大行数"/></el-form-item>
      <div class="section-title">附加筛选 <el-button v-if="!draft.filterTree" link @click="draft.filterTree={logic:'AND',children:[{field:'',op:'EQ',value:''}]}">添加筛选</el-button></div>
      <FilterTreeEditor v-if="draft.filterTree" v-model="draft.filterTree" :fields="fields" :disabled="applying || disabled" @remove="draft.filterTree=undefined"/>
    </el-form><div v-if="loading" v-loading="true" style="height:120px"/>
    <template #footer><el-button :disabled="loading || applying || disabled" @click="applySuggest">恢复推荐映射</el-button><el-button :disabled="applying" @click="visible=false">取消</el-button><el-button type="primary" :loading="applying" :disabled="loading || !draft || disabled" @click="apply">应用映射</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {chartFields,type Field} from '@/api/bi/chart'
import type {PanelChartConfig} from '@/api/bi/dashboard'
import {analyzeBindingImpact,suggestPanelConfig} from '@/utils/biTemplateBinding'
import type {TemplateSlot} from '@/utils/biTemplates'
import FilterTreeEditor from '../chart/FilterTreeEditor.vue'
const props=defineProps<{slot:TemplateSlot;datasetId:number;config:PanelChartConfig;disabled?:boolean}>()
const emit=defineEmits<{(event:'apply',config:PanelChartConfig):void}>()
const visible=ref(false),loading=ref(false),applying=ref(false),error=ref(''),fields=ref<Field[]>([]),draft=ref<PanelChartConfig>()
import { CHART_TYPE_LABELS, maxMetricsForType } from '@/utils/biChartTypes'
const types=CHART_TYPE_LABELS
const aggregations={SUM:'求和',AVG:'平均值',COUNT:'计数',COUNT_DISTINCT:'去重计数',MIN:'最小值',MAX:'最大值',NONE:'原值'}
const outputs=computed(()=>draft.value?[...draft.value.dimensions.map(d=>d.alias || d.field),...draft.value.metrics.map(m=>m.alias || (m.agg==='NONE'?m.field:m.agg.toLowerCase()+'_'+m.field))]:[])
const impact=computed(()=>draft.value?analyzeBindingImpact(props.slot,draft.value,fields.value):[])
function setDimensions(names:string[]){if(draft.value)draft.value.dimensions=names.map(field=>draft.value!.dimensions.find(d=>d.field===field) || {field,alias:field})}
async function open(){visible.value=true;loading.value=true;error.value='';try{fields.value=(await chartFields(props.datasetId)).data || [];draft.value=JSON.parse(JSON.stringify(props.config));if(props.slot.kind==='kpi'){draft.value!.type='table';draft.value!.dimensions=[];draft.value!.metrics=draft.value!.metrics.slice(0,1);draft.value!.orderBys=[]}}catch{error.value='字段加载失败，请检查数据集权限后重试'}finally{loading.value=false}}
function applySuggest(){if(!fields.value.length)return;draft.value=suggestPanelConfig(props.slot,fields.value)}
function apply(){if(!draft.value || props.disabled)return;const issues=analyzeBindingImpact(props.slot,draft.value,fields.value);if(issues.length){error.value=issues.join('；');return}emit('apply',JSON.parse(JSON.stringify(draft.value)));visible.value=false}
watch(()=>props.config,v=>{if(!visible.value)draft.value=JSON.parse(JSON.stringify(v))},{deep:true})
</script>
<style scoped>
.chart-config-entry{display:flex;gap:10px;flex-wrap:wrap;margin:12px 0}.config-form{margin-top:18px}.config-row{display:flex;gap:8px;margin-bottom:10px;flex-wrap:wrap}.config-row>.el-select{flex:1;min-width:140px}.config-row>.el-input{flex:1;min-width:120px}.section-title{display:flex;gap:12px;align-items:center;margin:18px 0 10px;font-weight:600;color:#526078}.source{font-size:12px;color:#7f8b9d;line-height:1.8}
</style>
