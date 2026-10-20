<template>
  <div class="draft-preview">
    <div class="preview-heading"><span>按当前编辑者权限预览，未保存的筛选与联动配置也会生效；不改变发布版本。<small v-if="definition.scene" role="status">{{ refreshStatus }}</small></span><el-button :loading="loading" @click="run">刷新预览</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <div v-if="controls.length" class="draft-filters"><div v-for="c in controls" :key="c.key" class="draft-filter"><label>{{ c.label }}</label><DashboardFilterInput v-model="inputs[c.key]" :type="c.type" :label="'草稿'+c.label" :options="options[c.key] || []" :loading="optionLoading[c.key]" @options="loadOptions(c.key)"/><small v-if="optionErrors[c.key]">{{ optionErrors[c.key] }}</small></div><div class="filter-actions"><el-button type="primary" :disabled="loading" @click="apply">应用筛选</el-button><el-button @click="clear">清空输入</el-button><el-button @click="reset">恢复默认输入</el-button><span>{{ pending?'输入已修改，应用后生效':'显示已应用条件' }}</span></div></div>
    <div v-if="Object.keys(activeLinks).length" class="filter-actions"><el-tag v-for="(value,key) in activeLinks" :key="key" closable @close="removeLink(String(key))">联动：{{ value ?? '空值' }}</el-tag><el-button link @click="clearLinks">清除全部联动</el-button></div>
    <p v-if="links.length" class="preview-help">点击来源图表或明细“选择”，预览目标图表联动。</p>
    <DashboardCanvas :scene="definition.scene" :panels="definition.panels" :appearance="definition.appearance" readonly><template #default="{panel}"><DashboardCard :title="panel.title" :component-type="panel.componentType" :content="panel.content" :panel-style="panel.style" :appearance="definition.appearance" :payload="payloads[panel.key]" :loading="loading && !!panel.chartId" :linkable="links.some(l=>l.sourcePanel===panel.key)" :drill-path="drillPath(panel.key)" @select="v=>selectLink(panel.key,v)" @drill="p=>drillDown(panel.key,p.field,p.value)" @drill-up="lvl=>drillUp(panel.key,lvl)" @refresh="run"/></template></DashboardCanvas>
  </div>
</template>
<script setup lang="ts">
import {computed,reactive,ref,onMounted,onBeforeUnmount} from 'vue'
import {previewDashboard,previewDashboardOptions,type DashboardDefinition,type DashboardInput,type FilterValue,type PanelResult,type DrillPathSegment} from '@/api/bi/dashboard'
import {appendDrillSegment,mergeDashboardInput,truncateDrillPath} from '@/utils/biDrill'
import {useBiRefresh} from '@/composables/useBiRefresh'
import {normalizeDashboard} from '@/utils/biPresentation'
import DashboardFilterInput from './DashboardFilterInput.vue'
import DashboardCanvas from './DashboardCanvas.vue'
import DashboardCard from './DashboardCard.vue'
const props=defineProps<{definition:DashboardDefinition;id?:number;version?:number}>()
const definition=normalizeDashboard(JSON.parse(JSON.stringify(props.definition)))
const controls=definition.interaction?.controls || [],links=definition.interaction?.links || []
const inputs=reactive<Record<string,FilterValue>>(Object.create(null)),applied=reactive<Record<string,FilterValue>>(Object.create(null)),activeLinks=reactive<Record<string,string|number|null>>(Object.create(null)),activeDrills=reactive<Record<string,DrillPathSegment[]>>(Object.create(null))
const options=reactive<Record<string,(string|number)[]>>(Object.create(null)),optionLoading=reactive<Record<string,boolean>>(Object.create(null)),optionErrors=reactive<Record<string,string>>(Object.create(null)),payloads=reactive<Record<string,PanelResult>>(Object.create(null)),loading=ref(false),error=ref('')
const inFlight=ref(0)
const {refreshStatus}=useBiRefresh(computed(()=>definition.scene?.refreshSeconds || 0),run,()=>loading.value || inFlight.value>0)
let generation=0
const optionSequences:Record<string,number>=Object.create(null),pending=computed(()=>JSON.stringify(inputs)!==JSON.stringify(applied))
function replace<T>(target:Record<string,T>,value:Record<string,T>){for(const k of Object.keys(target))delete target[k];Object.assign(target,JSON.parse(JSON.stringify(value)))}
function reset(){replace(inputs,Object.fromEntries(controls.map(c=>[c.key,c.defaultValue??null])))}
function clear(){replace(inputs,Object.fromEntries(controls.map(c=>[c.key,null])))}
function body():DashboardInput{return mergeDashboardInput(JSON.parse(JSON.stringify(applied)),JSON.parse(JSON.stringify(activeLinks)),JSON.parse(JSON.stringify(activeDrills)))}
function drillPath(key:string){return activeDrills[key] || []}
function drillDown(panel:string,field:string,value:string|number|null){if(loading.value)return;activeDrills[panel]=appendDrillSegment(drillPath(panel),field,value);run()}
function drillUp(panel:string,level:number){if(loading.value)return;activeDrills[panel]=truncateDrillPath(drillPath(panel),level);run()}
async function run(){if(loading.value)return;const g=++generation;loading.value=true;error.value='';for(const k of Object.keys(optionLoading))optionLoading[k]=false;try{const r=await previewDashboard(definition,body(),props.id,props.version);if(g!==generation)return;replace(payloads,r.data.payloads)}catch{if(g===generation){replace(payloads,{});error.value='草稿预览失败，请检查字段绑定、图表权限或草稿版本；返回编辑后可调整配置'}}finally{if(g===generation)loading.value=false}}
function apply(){if(loading.value)return;replace(applied,inputs);replace(activeLinks,{});replace(activeDrills,{});run()}
function selectLink(panel:string,value:string|number|null){if(loading.value)return;for(const l of links)if(l.sourcePanel===panel)activeLinks[l.key]=value;run()}
function removeLink(key:string){if(loading.value)return;delete activeLinks[key];run()}
function clearLinks(){if(loading.value)return;replace(activeLinks,{});run()}
async function loadOptions(key:string){const g=generation,v=(optionSequences[key]||0)+1;optionSequences[key]=v;optionLoading[key]=true;optionErrors[key]='';inFlight.value++;try{const r=await previewDashboardOptions(key,definition,body(),props.id,props.version);if(g!==generation || v!==optionSequences[key])return;options[key]=r.data.values;optionErrors[key]=r.data.truncated?'仅显示前100项候选值':''}catch{if(g===generation && v===optionSequences[key]){options[key]=[];optionErrors[key]='候选值加载失败，请检查当前查询权限'}}finally{inFlight.value--;if(g===generation && v===optionSequences[key])optionLoading[key]=false}}
onMounted(()=>{reset();replace(applied,inputs);run()})
onBeforeUnmount(()=>{++generation})
</script>
<style scoped>.preview-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:14px;font-size:11px;color:#8b96a7;line-height:1.8}.draft-filters{background:white;border:1px solid #e0e6ef;padding:16px;border-radius:8px;margin:12px 0 18px}.draft-filter{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-bottom:12px}.draft-filter label{min-width:95px;font-size:12px;color:#65738a}.draft-filter :deep(.el-select),.draft-filter :deep(.el-input){max-width:270px}.draft-filter small{color:var(--el-color-danger)}.filter-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin:12px 0}.filter-actions .el-button{margin:0}.filter-actions>span,.preview-help{font-size:11px;color:#8c97a8}</style>
