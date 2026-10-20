<template>
  <div class="chart-config-entry"><el-tag size="small" effect="plain">{{ panel.chartConfig?'组件独立配置':'沿用源图表配置' }}</el-tag><el-button :disabled="disabled" @click="open">配置图表字段</el-button></div>
  <el-dialog v-model="visible" title="配置当前组件" width="min(820px,calc(100vw - 24px))" :close-on-click-modal="false">
    <el-alert title="仅修改当前组件。数据集与源图表固定筛选保持绑定，附加筛选与它们同时生效。" type="info" :closable="false"/>
    <p class="source">{{ source?.name }} · 数据集 #{{ source?.query.datasetId }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <el-form v-if="draft && !loading" label-position="top" :disabled="applying || disabled" class="config-form">
      <el-form-item label="图表类型"><el-select v-model="draft.type" aria-label="组件图表类型" :disabled="panel.componentType==='kpi'"><el-option v-for="(label,type) in types" :key="type" :value="type" :label="label"/></el-select></el-form-item>
      <el-form-item v-if="panel.componentType!=='kpi'" label="维度（按选择顺序显示）"><el-select :model-value="draft.dimensions.map(d=>d.field)" multiple filterable aria-label="组件维度" @change="setDimensions"><el-option v-for="f in fields" :key="f.name" :value="f.name" :label="f.showName || f.name"/></el-select><small v-if="draft.type!=='table' && draft.type!=='gauge'">多数图形须选择一个维度；仪表盘不需要维度。</small></el-form-item>
      <div class="section-title">度量 <el-button link :disabled="draft.metrics.length>=maxMetricsForType(panel.componentType==='kpi'?'table':draft.type)" @click="draft.metrics.push({field:'',agg:'SUM',alias:'metric_'+(draft.metrics.length+1)})">添加度量</el-button></div>
      <div v-for="(m,i) in draft.metrics" :key="i" class="config-row"><el-select v-model="m.field" filterable aria-label="组件度量字段"><el-option v-for="f in fields" :key="f.name" :value="f.name" :label="f.showName || f.name"/></el-select><el-select v-model="m.agg" aria-label="组件聚合方式"><el-option v-for="(label,key) in aggregations" :key="key" :value="key" :label="label"/></el-select><el-input v-model="m.alias" placeholder="输出别名" aria-label="组件度量别名"/><el-button link type="danger" @click="draft.metrics.splice(i,1)">移除</el-button></div>
      <div class="section-title">排序 <el-button link @click="draft.orderBys.push({field:'',order:'asc'})">添加排序</el-button></div>
      <div v-for="(o,i) in draft.orderBys" :key="i" class="config-row"><el-select v-model="o.field" aria-label="组件排序字段"><el-option v-for="f in outputs" :key="f" :value="f" :label="f"/></el-select><el-select v-model="o.order" aria-label="组件排序方向"><el-option value="asc" label="升序"/><el-option value="desc" label="降序"/></el-select><el-button link type="danger" @click="draft.orderBys.splice(i,1)">移除</el-button></div>
      <el-form-item label="最大返回行数"><el-input-number v-model="draft.limit" :min="1" :max="1000" :precision="0" aria-label="组件最大行数"/></el-form-item>
      <div class="section-title">组件附加筛选 <el-button v-if="!draft.filterTree" link @click="draft.filterTree={logic:'AND',children:[{field:'',op:'EQ',value:''}]}">添加筛选</el-button></div>
      <FilterTreeEditor v-if="draft.filterTree" v-model="draft.filterTree" :fields="fields" :disabled="applying || disabled" @remove="draft.filterTree=undefined"/>
      <p v-if="source?.query.filters?.length || source?.query.filterTree" class="source">源图表包含固定筛选，服务端会自动保留；这里仅编辑组件新增的条件。</p>
    </el-form><div v-if="loading" v-loading="true" style="height:120px"/>
    <template #footer><el-button :disabled="loading || applying || disabled || !panel.chartConfig" @click="apply(true)">恢复源图表配置</el-button><el-button :disabled="applying" @click="visible=false">取消</el-button><el-button type="primary" :loading="applying" :disabled="loading || !draft || disabled" @click="apply(false)">验证并应用</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import {computed,ref} from 'vue'
import {getChart,chartFields,type ChartDefinition,type Field} from '@/api/bi/chart'
import {previewDashboard,type Panel,type PanelChartConfig,type PanelResult,type ScreenScene} from '@/api/bi/dashboard'
import FilterTreeEditor from '../chart/FilterTreeEditor.vue'
const props=defineProps<{panel:Panel;kind?:'dashboard'|'screen';layout?:'grid'|'absolute';scene?:ScreenScene;id?:number;version?:number;disabled?:boolean}>()
const emit=defineEmits<{(event:'apply',key:string,config:PanelChartConfig|undefined,payload:PanelResult):void}>()
const visible=ref(false),loading=ref(false),applying=ref(false),error=ref(''),source=ref<ChartDefinition>(),fields=ref<Field[]>([]),draft=ref<PanelChartConfig>()
import { CHART_TYPE_LABELS, maxMetricsForType } from '@/utils/biChartTypes'
const types=CHART_TYPE_LABELS
const aggregations={SUM:'求和',AVG:'平均值',COUNT:'计数',COUNT_DISTINCT:'去重计数',MIN:'最小值',MAX:'最大值',NONE:'原值'}
const outputs=computed(()=>draft.value?[...draft.value.dimensions.map(d=>d.alias || d.field),...draft.value.metrics.map(m=>m.alias || (m.agg==='NONE'?m.field:m.agg.toLowerCase()+'_'+m.field))]:[])
function setDimensions(names:string[]){if(draft.value)draft.value.dimensions=names.map(field=>draft.value!.dimensions.find(d=>d.field===field) || {field,alias:field})}
async function open(){visible.value=true;loading.value=true;error.value='';draft.value=undefined;try{const d=(await getChart(props.panel.chartId!)).data.definition;source.value=d;fields.value=(await chartFields(d.query.datasetId)).data || [];draft.value=JSON.parse(JSON.stringify(props.panel.chartConfig || {type:d.type,dimensions:d.query.dimensions,metrics:d.query.metrics,orderBys:d.query.orderBys || [],limit:d.query.limit || 100,filterTree:undefined}));if(props.panel.componentType==='kpi'){draft.value!.type='table';draft.value!.dimensions=[];draft.value!.metrics=draft.value!.metrics.slice(0,1);draft.value!.orderBys=[]}}catch{error.value='字段加载失败，请检查源图表和数据集权限后重试'}finally{loading.value=false}}
async function apply(reset:boolean){if(!draft.value || props.disabled)return;applying.value=true;error.value='';const key=props.panel.key,config=reset?undefined:JSON.parse(JSON.stringify(draft.value));try{const panel={...JSON.parse(JSON.stringify(props.panel)),chartConfig:config};const result=(await previewDashboard({schemaVersion:1,name:'组件配置验证',kind:props.kind,layout:props.layout,scene:props.scene,panels:[panel],interaction:{controls:[],links:[]}},{filters:{},links:{}},props.id,props.version)).data;emit('apply',key,config,result.payloads[key]);visible.value=false}catch(e){error.value=e instanceof Error?e.message:'配置验证失败，请检查字段、聚合、排序或权限'}finally{applying.value=false}}
</script>
<style scoped>
.chart-config-entry{display:flex;gap:10px;flex-wrap:wrap;margin:12px 0}.config-form{margin-top:18px}.config-row{display:flex;gap:8px;margin-bottom:10px;flex-wrap:wrap}.config-row>.el-select{flex:1;min-width:140px}.config-row>.el-input{flex:1;min-width:120px}.section-title{display:flex;gap:12px;align-items:center;margin:18px 0 10px;font-weight:600;color:#526078}.source,small{font-size:12px;color:#7f8b9d;line-height:1.8}small{display:block;width:100%}
</style>
