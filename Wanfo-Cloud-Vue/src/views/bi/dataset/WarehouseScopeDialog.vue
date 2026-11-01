<template>
  <el-dialog :model-value="modelValue" :title="'数仓组织范围 · ' + name" width="min(920px, calc(100vw - 24px))" @update:model-value="emit('update:modelValue',$event)">
    <el-alert type="warning" :closable="false" show-icon class="mb8" title="启用后，数据集查询会同时限定租户字段和登录人所在若依组织及已映射的下级组织。映射不完整只会缩小可见范围；无法解析登录组织时拒绝返回数据。管理员仍可查看完整结果。" />
    <el-form label-width="110px" v-loading="loading">
      <el-form-item label="模型版本" required><el-input v-model="binding.modelVersion" maxlength="64" placeholder="例如 retail-v1" /></el-form-item>
      <el-form-item label="租户字段" required><el-select v-model="binding.tenantField" filterable style="width:100%"><el-option v-for="field in fields" :key="field" :value="field" :label="field" /></el-select></el-form-item>
      <el-form-item label="组织字段" required><el-select v-model="binding.organizationField" filterable style="width:100%"><el-option v-for="field in fields" :key="field" :value="field" :label="field" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" :loading="saving" :disabled="!binding.modelVersion.trim() || !binding.tenantField || !binding.organizationField || binding.tenantField===binding.organizationField" @click="saveBinding">保存并启用范围过滤</el-button><el-button v-if="configured" type="danger" plain :loading="saving" @click="removeBinding">关闭此数据集的自动范围过滤</el-button></el-form-item>
    </el-form>
    <el-divider />
    <template v-if="configured">
      <div class="scope-editor">
        <el-input v-model="mapping.modelVersion" disabled style="width:150px" />
        <el-select v-model="mapping.principalId" remote filterable :remote-method="searchOrganizations" placeholder="搜索若依组织" style="min-width:220px">
          <el-option v-for="item in organizations" :key="item.id" :value="item.id" :label="item.name" />
        </el-select>
        <el-input v-model="mapping.tenantId" placeholder="tenant_id" style="width:160px" />
        <el-input v-model="mapping.warehouseOrganizationId" placeholder="warehouse org_id" style="width:190px" />
        <el-button type="primary" :disabled="!mapping.principalId || !mapping.tenantId.trim() || !mapping.warehouseOrganizationId.trim()" :loading="saving" @click="addMapping">添加映射</el-button>
      </div>
      <el-table :data="mappings" border size="small" class="mt8">
        <el-table-column prop="principalId" label="若依组织主体 ID" width="150" />
        <el-table-column label="组织名称" min-width="160"><template #default="{row}">{{ principalNames[row.principalId] || '—' }}</template></el-table-column>
        <el-table-column prop="tenantId" label="数仓 tenant_id" min-width="140" />
        <el-table-column prop="warehouseOrganizationId" label="数仓 org_id" min-width="150" />
        <el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="danger" :disabled="saving" @click="removeMapping(row)">删除</el-button></template></el-table-column>
        <template #empty><el-empty description="该模型版本还没有组织映射；非管理员查询将返回空数据" /></template>
      </el-table>
    </template>
    <template #footer><el-button @click="emit('update:modelValue',false)">关闭</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDataset, searchDatasetPrincipals, getWarehouseScopeBinding, saveWarehouseScopeBinding, deleteWarehouseScopeBinding, listWarehouseOrganizationMappings, addWarehouseOrganizationMapping, deleteWarehouseOrganizationMapping, type WarehouseScopeBinding, type WarehouseOrganizationMapping } from '@/api/bi/dataset'
import type { GrantPrincipal } from '@/api/bi/datasource'

const props=defineProps<{modelValue:boolean;datasetId:number;name:string}>()
const emit=defineEmits(['update:modelValue'])
const loading=ref(false),saving=ref(false),fields=ref<string[]>([]),mappings=ref<WarehouseOrganizationMapping[]>([]),organizations=ref<GrantPrincipal[]>([])
const binding=reactive({modelVersion:'',tenantField:'tenant_id',organizationField:'org_id'})
const mapping=reactive({modelVersion:'',principalId:undefined as number|undefined,tenantId:'',warehouseOrganizationId:''})
const principalNames=reactive<Record<number,string>>({})
const configured=computed(()=>!!binding.modelVersion && !!binding.tenantField && !!binding.organizationField)
let version=0
async function refreshMappings(){if(!configured.value)return;mappings.value=(await listWarehouseOrganizationMappings(binding.modelVersion)).data||[]}
async function searchOrganizations(query=''){
  const result=await searchDatasetPrincipals(query);organizations.value=(result.data||[]).filter(p=>p.type==='organization')
  for(const p of organizations.value)principalNames[p.id]=p.name
}
watch(()=>[props.modelValue,props.datasetId],async()=>{
  const v=++version;if(!props.modelValue)return
  loading.value=true;mappings.value=[];fields.value=[]
  try{
    const [dataset,current]=await Promise.all([getDataset(props.datasetId),getWarehouseScopeBinding(props.datasetId)])
    if(v!==version)return
    fields.value=(dataset.data?.fields||[]).map(f=>f.originName)
    Object.assign(binding,current.data||{modelVersion:'',tenantField:'tenant_id',organizationField:'org_id'})
    mapping.modelVersion=binding.modelVersion
    await Promise.all([refreshMappings(),searchOrganizations()])
  }finally{if(v===version)loading.value=false}
})
async function saveBinding(){saving.value=true;try{const result=await saveWarehouseScopeBinding(props.datasetId,{...binding});Object.assign(binding,result.data);mapping.modelVersion=binding.modelVersion;await refreshMappings();ElMessage.success('数仓组织范围绑定已启用')}finally{saving.value=false}}
async function removeBinding(){try{await ElMessageBox.confirm('移除后，此数据集恢复原有数据权限规则，不再自动按数仓租户和组织过滤。确认继续？','关闭范围过滤',{type:'warning'})}catch{return}saving.value=true;try{await deleteWarehouseScopeBinding(props.datasetId);Object.assign(binding,{modelVersion:'',tenantField:'tenant_id',organizationField:'org_id'});mappings.value=[];ElMessage.success('已关闭自动范围过滤')}finally{saving.value=false}}
async function addMapping(){if(!mapping.principalId)return;saving.value=true;try{const result=await addWarehouseOrganizationMapping(binding.modelVersion,{principalId:mapping.principalId,tenantId:mapping.tenantId.trim(),warehouseOrganizationId:mapping.warehouseOrganizationId.trim()});mappings.value=result.data||[];mapping.principalId=undefined;mapping.tenantId='';mapping.warehouseOrganizationId='';ElMessage.success('组织映射已保存')}finally{saving.value=false}}
async function removeMapping(row:WarehouseOrganizationMapping){try{await ElMessageBox.confirm('删除后，属于此组织的非管理员查询将不再包含该组织范围。确认删除？','删除组织映射',{type:'warning'})}catch{return}saving.value=true;try{await deleteWarehouseOrganizationMapping(binding.modelVersion,row.id);await refreshMappings();ElMessage.success('组织映射已删除')}finally{saving.value=false}}
</script>

<style scoped>
.scope-editor{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.mt8{margin-top:8px}
</style>
