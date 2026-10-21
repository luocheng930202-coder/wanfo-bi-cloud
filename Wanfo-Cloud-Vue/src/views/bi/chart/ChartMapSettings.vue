<template>
  <section aria-label="地图层级配置" v-loading="loading">
    <el-alert v-if="error" :title="error" type="warning" :closable="false"><el-button link @click="load">重新加载地图目录</el-button></el-alert>
    <el-form-item label="初始地图范围"><el-tree-select :model-value="scopeCode" :data="tree" check-strictly filterable node-key="value" aria-label="初始地图范围" style="width:100%" @update:model-value="setScope"/></el-form-item>
    <el-form-item label="地区值类型"><el-select :model-value="definition.advanced?.mapValueMode || 'name'" @change="v=>advanced().mapValueMode=v"><el-option value="code" label="行政区编码（推荐）"/><el-option value="name" label="地区名称"/></el-select></el-form-item>
    <el-form-item v-for="level in levels" :key="level.key" :label="level.label"><el-select :model-value="binding(level.key)" filterable clearable :placeholder="'选择'+level.label" @change="v=>setField(level.key,v)"><el-option v-for="f in fields" :key="f.name" :value="f.name" :label="(f.showName || f.name)+' · '+f.name"/></el-select></el-form-item>
    <el-alert v-if="missing" :title="missing" type="info" :closable="false"/>
    <p class="hint">范围或字段调整后点击“更新数据”，也可开启自动查询。点击地图中的地区可进入已绑定字段的下一层，使用上方面包屑返回。直辖市会直接进入区县；没有下级边界或字段时停止下钻，不分摊上级指标。</p>
    <p class="hint">名称匹配区域地图需绑定上级省市字段，避免同名区县误匹配。部分省份的地市层包含直辖县级地区，应提供该层对应编码或名称。</p>
    <p v-if="catalog" class="hint">边界版本：{{ catalog.version }}。仅加载当前范围；叶子节点显示本地区边界，乡镇层级不在本次范围内。</p>
  </section>
</template>
<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import type {ChartDefinition,Field} from '@/api/bi/chart'
import {fetchGeoCatalog} from '@/utils/biMapGeo'
import {geoTree,geoDisplayLevel,mapScopeCode,type GeoCatalogIndex} from '@/utils/biMapRegions'
const props=defineProps<{definition:ChartDefinition;fields:Field[]}>()
type Level='province'|'city'|'county'
const levels=[{key:'province' as const,label:'省级字段'},{key:'city' as const,label:'市级字段'},{key:'county' as const,label:'区县字段'}]
const catalog=ref<GeoCatalogIndex>(),loading=ref(false),error=ref('')
const tree=computed(()=>catalog.value?geoTree(catalog.value):[])
const scopeCode=computed(()=>mapScopeCode(props.definition.advanced?.mapScope || 'china-provinces'))
const displayLevel=computed(()=>catalog.value?geoDisplayLevel(catalog.value,scopeCode.value):'province')
function advanced(){return props.definition.advanced ||= {}}
function binding(level:Level){return props.definition.advanced?.mapFields?.[level] || (scopeCode.value==='100000' && level==='province'?props.definition.query.dimensions[0]?.field:undefined)}
function preserveRoot(){const fields=advanced().mapFields ||= {};if(scopeCode.value==='100000' && !fields.province && props.definition.query.dimensions[0])fields.province=props.definition.query.dimensions[0].field;return fields}
function setDimension(field:string){props.definition.query.dimensions=[{field,alias:field}];const keys=new Set(props.definition.query.metrics.map(m=>m.alias || (m.agg==='NONE'?m.field:m.agg.toLowerCase()+'_'+m.field)));props.definition.query.orderBys=(props.definition.query.orderBys || []).filter(o=>o.field===field || keys.has(o.field))}
function setScope(value:string){if(!value)return;const fields=preserveRoot();advanced().mapScope=value==='100000'?'china-provinces':value;const level=catalog.value?geoDisplayLevel(catalog.value,value):'province';if(fields[level])setDimension(fields[level]!)}
function setField(level:Level,value:string){const fields=preserveRoot();fields[level]=value || undefined;if(level===displayLevel.value && value)setDimension(value)}
const missing=computed(()=>!binding(displayLevel.value)?'当前范围需要配置'+levels.find(l=>l.key===displayLevel.value)?.label:!binding('city') || !binding('county')?'未绑定市县字段的层级不会继续下钻。':'')
async function load(){loading.value=true;error.value='';try{catalog.value=await fetchGeoCatalog()}catch{error.value='地图目录加载失败，请重试'}finally{loading.value=false}}
onMounted(load)
</script>
<style scoped>.hint{font-size:12px;line-height:1.7;color:var(--bi-muted,#667085)}</style>
