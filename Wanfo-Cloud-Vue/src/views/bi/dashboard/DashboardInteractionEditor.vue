<template>
  <el-collapse><el-collapse-item title="看板筛选与图表联动" name="interaction">
    <el-alert v-if="error" :title="error" type="warning" :closable="false"/>
    <p class="hint">筛选可在草稿交互预览与已发布看板中应用。每个目标须显式选择字段且类型一致；候选值按编辑者或查看者各自的权限查询。</p>
    <el-button :disabled="modelValue.controls.length>=12" @click="addControl">添加筛选器</el-button>
    <div v-for="c in modelValue.controls" :key="c.key" class="binding-box">
      <div class="binding-row"><el-input v-model="c.label" placeholder="筛选名称" :aria-label="'筛选名称 '+c.key" maxlength="80"/><el-select v-model="c.type" aria-label="筛选类型" @change="c.defaultValue=null"><el-option label="文本精确匹配" value="text"/><el-option label="枚举" value="enum"/><el-option label="数值范围" value="number"/><el-option label="日期范围" value="date"/></el-select><el-button link type="danger" @click="removeControl(c.key)">移除筛选器</el-button></div>
      <div class="binding-row"><span>默认值</span><DashboardFilterInput v-model="c.defaultValue" :type="c.type" :label="c.label+'默认值'" author/></div>
      <div v-for="(t,i) in c.targets" :key="i" class="binding-row"><span>目标</span><el-select v-model="t.panel" aria-label="筛选目标组件" @change="t.field='' "><el-option v-for="p in panels" :key="p.key" :value="p.key" :label="p.title || p.key"/></el-select><el-select v-model="t.field" aria-label="筛选目标字段"><el-option v-for="f in fields[t.panel] || []" :key="f.name" :value="f.name" :label="(f.showName || f.name)+' · '+f.type"/></el-select><el-button link @click="c.targets.splice(i,1)">移除目标</el-button></div>
      <el-button link type="primary" @click="c.targets.push({panel:'',field:''})">添加筛选目标</el-button>
    </div>
    <el-divider/>
    <el-button :disabled="modelValue.links.length>=12" @click="addLink">添加图表联动</el-button>
    <p class="hint">点击来源图表的第一维度后立即筛选目标；待应用的表单条件不会提前生效。可清除联动，保留已应用筛选。</p>
    <div v-for="l in modelValue.links" :key="l.key" class="binding-box">
      <div class="binding-row"><span>来源</span><el-select v-model="l.sourcePanel" aria-label="联动来源组件" @change="l.sourceField=firstDimension[l.sourcePanel] || ''"><el-option v-for="p in panels" :key="p.key" :value="p.key" :label="p.title || p.key" :disabled="!firstDimension[p.key]"/></el-select><span>{{ l.sourceField || '第一维度' }}</span><el-button v-if="firstDimension[l.sourcePanel] && l.sourceField!==firstDimension[l.sourcePanel]" link type="warning" @click="l.sourceField=firstDimension[l.sourcePanel]">更新为当前第一维度 {{ firstDimension[l.sourcePanel] }}</el-button><el-button link type="danger" @click="removeLink(l.key)">移除联动</el-button></div>
      <div v-for="(t,i) in l.targets" :key="i" class="binding-row"><span>目标</span><el-select v-model="t.panel" aria-label="联动目标组件" @change="t.field='' "><el-option v-for="p in panels.filter(p=>p.key!==l.sourcePanel)" :key="p.key" :value="p.key" :label="p.title || p.key"/></el-select><el-select v-model="t.field" aria-label="联动目标字段"><el-option v-for="f in fields[t.panel] || []" :key="f.name" :value="f.name" :label="(f.showName || f.name)+' · '+f.type"/></el-select><el-button link @click="l.targets.splice(i,1)">移除目标</el-button></div>
      <el-button link type="primary" @click="l.targets.push({panel:'',field:''})">添加联动目标</el-button>
    </div>
  </el-collapse-item></el-collapse>
</template>
<script setup lang="ts">
import {reactive,ref,watch} from 'vue'
import {getChart,chartFields,type Field} from '@/api/bi/chart'
import type {DashboardInteraction,Panel} from '@/api/bi/dashboard'
import DashboardFilterInput from './DashboardFilterInput.vue'
const props=defineProps<{modelValue:DashboardInteraction;panels:Panel[]}>()
const fields=reactive<Record<string,Field[]>>(Object.create(null)),firstDimension=reactive<Record<string,string>>(Object.create(null)),error=ref('')
let generation=0
watch(()=>JSON.stringify(props.panels.map(p=>[p.key,p.chartId,p.chartConfig])),async()=>{const g=++generation;error.value='';for(const k of Object.keys(fields))delete fields[k];for(const k of Object.keys(firstDimension))delete firstDimension[k];try{for(const p of props.panels){if(!p.chartId || p.componentType==='text' || p.componentType==='image')continue;const d=(await getChart(p.chartId)).data;if(g!==generation)return;const f=(await chartFields(d.definition.query.datasetId)).data;if(g!==generation)return;fields[p.key]=f || [];firstDimension[p.key]=p.componentType==='kpi'?'':(p.chartConfig?.dimensions || d.definition.query.dimensions)[0]?.field || ''}}catch{if(g===generation)error.value='字段加载失败，请检查图表和数据集权限后重新打开；现有绑定仍保留'}},{immediate:true})
function addControl(){props.modelValue.controls.push({key:crypto.randomUUID(),label:'新筛选',type:'enum',defaultValue:null,targets:[{panel:'',field:''}]})}
function removeControl(key:string){props.modelValue.controls.splice(props.modelValue.controls.findIndex(c=>c.key===key),1)}
function addLink(){props.modelValue.links.push({key:crypto.randomUUID(),sourcePanel:'',sourceField:'',targets:[{panel:'',field:''}]})}
function removeLink(key:string){props.modelValue.links.splice(props.modelValue.links.findIndex(l=>l.key===key),1)}
</script>
<style scoped>.binding-box{border:1px solid var(--el-border-color);border-radius:6px;padding:12px;margin:12px 0}.binding-row{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:10px}.binding-row :deep(.el-input),.binding-row :deep(.el-select){width:210px}.hint{color:var(--el-text-color-secondary);line-height:1.7}</style>
