<template>
  <el-dialog :model-value="visible" title="SQL 参数设置" width="min(1080px, calc(100vw - 32px))" :close-on-click-modal="false" @update:model-value="$emit('update:visible',$event)" @open="reset">
    <p class="parameter-note">参数由 SQL 自动识别。每行填写一个值，无需 SQL 引号；多值参数用于 IN / NOT IN。可选参数缺值时，整个 $DE_PARAM 条件不参与筛选。</p>
    <el-table :data="rows" max-height="420" border>
      <el-table-column prop="name" label="参数" min-width="110"/>
      <el-table-column label="显示名称" min-width="120"><template #default="{row}"><el-input v-model="row.label" maxlength="100"/></template></el-table-column>
      <el-table-column label="类型" width="125"><template #default="{row}"><el-select v-model="row.type"><el-option label="文本" value="TEXT"/><el-option label="数值" value="NUMBER"/><el-option label="日期" value="DATE"/><el-option label="日期时间" value="DATETIME"/></el-select></template></el-table-column>
      <el-table-column label="多值" width="65"><template #default="{row}"><el-checkbox v-model="row.multiple" :aria-label="`${row.name} 多值`"/></template></el-table-column>
      <el-table-column label="必填" width="65"><template #default="{row}"><el-checkbox v-model="row.required" :aria-label="`${row.name} 必填`"/></template></el-table-column>
      <el-table-column label="默认值" min-width="155"><template #default="{row}"><el-input v-model="row.defaultText" type="textarea" :rows="2" :placeholder="placeholder(row.type)"/></template></el-table-column>
      <el-table-column label="默认值作用范围" min-width="145"><template #default="{row}"><el-select v-model="row.defaultScope"><el-option label="预览及查询" value="ALL"/><el-option label="仅编辑预览" value="EDIT"/></el-select></template></el-table-column>
      <el-table-column label="本次预览值" min-width="155"><template #default="{row}"><el-input v-model="row.previewText" type="textarea" :rows="2" placeholder="留空使用默认值（不保存）"/></template></el-table-column>
    </el-table>
    <el-empty v-if="!rows.length" description="SQL 中尚未发现 $DE_PARAM{...$[参数名]...} 条件" :image-size="60"/>
    <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-top:12px"/>
    <template #footer><el-button @click="$emit('update:visible',false)">取消</el-button><el-button type="primary" @click="apply">应用参数设置</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import {ref} from 'vue'
import type {SqlParameterDefinition} from '@/api/bi/dataset'
import {parameterLines} from '@/utils/biSqlParameters'
const props=defineProps<{visible:boolean;definitions:SqlParameterDefinition[];values:Record<string,string[]>}>()
const emit=defineEmits<{(e:'update:visible',value:boolean):void;(e:'apply',definitions:SqlParameterDefinition[],values:Record<string,string[]>):void}>()
const rows=ref<(SqlParameterDefinition & {defaultText:string;previewText:string})[]>([]),error=ref('')
function reset(){rows.value=props.definitions.map(p=>({...p,defaultText:(p.defaultValues || []).join('\n'),previewText:(props.values[p.name] || []).join('\n')}));error.value=''}
function placeholder(type:string){return type==='DATE'?'YYYY-MM-DD':type==='DATETIME'?'YYYY-MM-DD HH:mm:ss':'每行一个值'}
function apply(){
  const definitions:SqlParameterDefinition[]=[],values:Record<string,string[]>={};error.value=''
  for(const row of rows.value){const defaults=parameterLines(row.defaultText),preview=parameterLines(row.previewText);if(!row.multiple && (defaults.length>1 || preview.length>1)){error.value=`${row.name} 为单值参数，只能填写一行`;return}if(defaults.length>100 || preview.length>100){error.value='每个参数最多支持 100 个值';return}definitions.push({name:row.name,label:row.label,type:row.type,multiple:row.multiple,required:row.required,defaultValues:defaults,defaultScope:row.defaultScope});if(preview.length)values[row.name]=preview}
  emit('apply',definitions,values);emit('update:visible',false)
}
</script>
<style scoped>.parameter-note{font-size:12px;line-height:1.8;color:var(--el-text-color-secondary);margin:0 0 16px}</style>
