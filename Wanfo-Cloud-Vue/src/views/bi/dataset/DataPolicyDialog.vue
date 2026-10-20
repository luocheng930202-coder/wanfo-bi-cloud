<template>
  <el-dialog :model-value="modelValue" :title="'数据权限 · ' + name" width="min(900px, calc(100vw - 24px))" @update:model-value="emit('update:modelValue', $event)">
    <el-alert title="命中主体的行条件全部同时满足；隐藏优先于脱敏。未命中规则不额外限制，管理员豁免。规则不影响原始数据源建模，请仅给可信人员建模权限。" :closable="false" type="info" class="mb8" />
    <el-form label-width="80px" v-loading="loading">
      <el-form-item label="限制对象"><el-select v-model="form.principalId" remote filterable :remote-method="search" placeholder="搜索用户、角色或部门" style="width:100%"><el-option v-for="p in principals" :key="p.id" :value="p.id" :label="`${p.type} · ${p.name}`" /></el-select></el-form-item>
      <el-form-item label="字段"><el-select v-model="form.fieldName" filterable style="width:100%"><el-option v-for="f in fields" :key="f" :value="f" :label="f" /></el-select></el-form-item>
      <el-form-item label="规则"><el-select v-model="form.ruleKind" style="width:100%"><el-option v-for="(label,key) in kinds" :key="key" :value="key" :label="label" /></el-select></el-form-item>
      <el-form-item v-if="needsValue" label="固定值"><el-input v-model="form.ruleValue" maxlength="1000" placeholder="固定值，不支持 SQL 表达式" /></el-form-item>
      <el-form-item><el-button type="primary" :loading="saving" :disabled="loading || !form.principalId || !form.fieldName" @click="add">添加限制</el-button></el-form-item>
    </el-form>
    <el-table :data="rules" border>
      <el-table-column label="主体" min-width="130"><template #default="{row}">{{ names[row.principalId] || `主体 #${row.principalId}` }}</template></el-table-column>
      <el-table-column prop="fieldName" label="字段" min-width="100" />
      <el-table-column label="规则" min-width="110"><template #default="{row}">{{ kinds[row.ruleKind] || row.ruleKind }}</template></el-table-column>
      <el-table-column prop="ruleValue" label="固定值" min-width="100" show-overflow-tooltip />
      <el-table-column label="操作" width="100"><template #default="{row}"><el-button link type="danger" :disabled="saving" @click="remove(row)">删除限制</el-button></template></el-table-column>
    </el-table>
    <template #footer><el-button @click="emit('update:modelValue',false)">关闭</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDataset, searchDatasetPrincipals, listDataPolicies, addDataPolicy, removeDataPolicy, type DataPolicyRule } from '@/api/bi/dataset'
import type { GrantPrincipal } from '@/api/bi/datasource'
const props=defineProps<{modelValue:boolean;datasetId:number;name:string}>()
const emit=defineEmits(['update:modelValue'])
const kinds:Record<string,string>={EQ:'等于',NE:'不等于',GT:'大于',GE:'大于等于',LT:'小于',LE:'小于等于',IS_NULL:'为空',IS_NOT_NULL:'不为空',DENY:'隐藏字段',MASK:'脱敏为 ***'}
const form=reactive({principalId:undefined as number|undefined,fieldName:'',ruleKind:'EQ',ruleValue:''})
const needsValue=computed(()=>!['IS_NULL','IS_NOT_NULL','DENY','MASK'].includes(form.ruleKind))
const principals=ref<GrantPrincipal[]>([]),rules=ref<DataPolicyRule[]>([]),fields=ref<string[]>([]),loading=ref(false),saving=ref(false)
const names=reactive<Record<number,string>>({})
let version=0,searchVersion=0
async function search(query='') {const v=++searchVersion;const r=await searchDatasetPrincipals(query);if(v!==searchVersion)return;principals.value=r.data||[];for(const p of principals.value)names[p.id]=`${p.type} · ${p.name}`}
watch(()=>[props.modelValue,props.datasetId],async()=>{
  const v=++version;++searchVersion
  if(!props.modelValue)return
  loading.value=true;rules.value=[];fields.value=[];Object.assign(form,{principalId:undefined,fieldName:'',ruleKind:'EQ',ruleValue:''})
  try {const [d,r]=await Promise.all([getDataset(props.datasetId),listDataPolicies(props.datasetId),search()]);if(v!==version)return;fields.value=(d.data.fields||[]).map(f=>f.originName);rules.value=r.data||[]}
  finally {if(v===version)loading.value=false}
})
async function add() {
  if(!form.principalId)return
  const id=props.datasetId,v=version;saving.value=true
  try {await addDataPolicy(id,{...form,principalId:form.principalId,ruleValue:needsValue.value?form.ruleValue:undefined});const r=await listDataPolicies(id);if(v===version){rules.value=r.data||[];ElMessage.success('限制已生效')}}finally{saving.value=false}
}
async function remove(row:DataPolicyRule) {
  const id=props.datasetId,v=version
  try {await ElMessageBox.confirm('删除后，对应主体将不再受这条规则限制。确认删除？','删除数据权限限制',{type:'warning'})}catch{return}
  if(v!==version || !props.modelValue)return
  saving.value=true
  try {await removeDataPolicy(id,row.id!);const r=await listDataPolicies(id);if(v===version){rules.value=r.data||[];ElMessage.success('限制已删除')}}finally{saving.value=false}
}
</script>
