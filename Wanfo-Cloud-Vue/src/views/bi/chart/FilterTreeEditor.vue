<template>
  <div class="filter-node">
    <template v-if="isGroup">
      <div class="node-toolbar">
        <el-select :model-value="group.logic" @change="value => update({...group,logic:value})" aria-label="条件关系" style="width:110px"><el-option value="AND" label="全部满足"/><el-option value="OR" label="任一满足"/></el-select>
        <el-button link @click="append(false)">添加条件</el-button><el-button link :disabled="disabled || depth>=7" @click="append(true)">添加分组</el-button>
        <el-button link type="danger" @click="emit('remove')">移除组</el-button>
      </div>
      <FilterTreeEditor v-for="(child,index) in group.children" :key="index" :model-value="child" :fields="fields" :depth="depth+1" :disabled="disabled" @update:model-value="value => replace(index,value)" @remove="removeChild(index)" />
      <el-text v-if="!group.children.length" type="warning">请添加条件，或移除这个空分组</el-text>
    </template>
    <div v-else class="condition">
      <el-select :model-value="condition.field" filterable placeholder="选择字段" aria-label="筛选字段" @change="value => update({...condition,field:value,value:undefined})"><el-option v-for="f in fields" :key="f.name" :value="f.name" :label="f.showName || f.name"/></el-select>
      <el-select :model-value="condition.op" aria-label="筛选操作符" @change="changeOperator"><el-option v-for="(label,key) in operators" :key="key" :value="key" :label="label"/></el-select>
      <template v-if="condition.op==='BETWEEN'">
        <el-input :model-value="range[0]" placeholder="起始值" aria-label="起始值" @update:model-value="value => update({...condition,value:[value,range[1]]})"/>
        <el-input :model-value="range[1]" placeholder="结束值" aria-label="结束值" @update:model-value="value => update({...condition,value:[range[0],value]})"/>
      </template>
      <el-select v-else-if="['IN','NOT_IN'].includes(condition.op)" :model-value="list" multiple filterable allow-create default-first-option placeholder="输入值后按回车，可输入多个" aria-label="筛选值列表" @change="value => update({...condition,value})"/>
      <el-input v-else-if="!['IS_NULL','IS_NOT_NULL'].includes(condition.op)" :model-value="scalar" placeholder="固定值" aria-label="筛选值" maxlength="1000" @update:model-value="value => update({...condition,value})"/>
      <el-button link type="danger" @click="emit('remove')">移除条件</el-button>
    </div>
  </div>
</template>
<script setup lang="ts" name="FilterTreeEditor">
import { computed } from 'vue'
import type { FilterNode, Condition, Field } from '@/api/bi/chart'
const props=withDefaults(defineProps<{modelValue:FilterNode;fields:Field[];depth?:number;disabled?:boolean}>(),{depth:1,disabled:false})
const emit=defineEmits<{(event:'update:modelValue',value:FilterNode):void;(event:'remove'):void}>()
const operators:Record<string,string>={EQ:'等于',NE:'不等于',GT:'大于',GTE:'大于等于',LT:'小于',LTE:'小于等于',LIKE:'匹配',NOT_LIKE:'不匹配',IN:'包含于',NOT_IN:'不包含于',BETWEEN:'区间',IS_NULL:'为空',IS_NOT_NULL:'不为空'}
const isGroup=computed(()=>'logic' in props.modelValue && ['AND','OR'].includes(String(props.modelValue.logic)))
const group=computed(()=>props.modelValue as {logic:'AND'|'OR';children:FilterNode[]})
const condition=computed(()=>props.modelValue as Condition)
const range=computed(()=>Array.isArray(condition.value.value)?condition.value.value.map(String):['',''])
const list=computed(()=>Array.isArray(condition.value.value)?condition.value.value.map(String):[])
const scalar=computed(()=>condition.value.value==null?'':String(condition.value.value))
function update(value:FilterNode){emit('update:modelValue',value)}
function append(nested:boolean){update({...group.value,children:[...group.value.children,nested?{logic:'AND',children:[{field:'',op:'EQ',value:''}]}:{field:'',op:'EQ',value:''}]})}
function replace(index:number,value:FilterNode){const children=[...group.value.children];children[index]=value;update({...group.value,children})}
function removeChild(index:number){update({...group.value,children:group.value.children.filter((_,i)=>i!==index)})}
function changeOperator(op:string){update({...condition.value,op,value:['IN','NOT_IN','BETWEEN'].includes(op)?[]:undefined})}
</script>
<style scoped>
.filter-node{border-left:3px solid #d9e6f5;padding:10px;margin:8px 0;background:#f8fafc}.node-toolbar{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.condition{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.condition>.el-select,.condition>.el-input{width:180px;max-width:100%}
</style>
