<template>
 <el-dialog :model-value="true" :title="'素材授权 · '+asset.name" width="min(700px,calc(100vw - 24px))" append-to-body :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="emit('close')">
 <p class="hint">查看权限允许预览并复制到看板；撤权阻止后续读取，不追回已保存的图片副本。功能资格仍需在若依角色中配置，编辑/删除/授权还需要素材查看权限。</p><el-alert v-if="error" :title="error" type="error" :closable="false"/>
 <div class="grant-row"><el-select v-model="principal" filterable remote :remote-method="search" placeholder="搜索用户、角色或部门" aria-label="素材授权主体" :disabled="busy"><el-option v-for="p in principals" :key="p.id" :value="p.id" :label="p.type+' · '+p.name"/></el-select><el-select v-model="action" aria-label="素材授权操作" :disabled="busy"><el-option v-for="(label,key) in actions" :key="key" :value="key" :label="label"/></el-select><el-button type="primary" :disabled="busy || !principal" @click="add">添加授权</el-button></div>
 <el-table :data="grants" v-loading="busy"><el-table-column prop="displayName" label="授权对象"/><el-table-column label="权限"><template #default="{row}">{{ actions[row.action] || row.action }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="danger" :disabled="busy" @click="remove(row)">撤销</el-button></template></el-table-column></el-table>
 <template #footer><el-button :disabled="busy" @click="emit('close')">关闭授权</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {searchAssetPrincipals,assetGrants,grantAsset,revokeAssetGrant,type Asset} from '@/api/bi/asset'
import type {GrantPrincipal,ResourceGrant} from '@/api/bi/datasource'
const props=defineProps<{asset:Asset}>(),emit=defineEmits<{(e:'close'):void}>()
const principal=ref<number>(),action=ref('view'),principals=ref<GrantPrincipal[]>([]),grants=ref<ResourceGrant[]>([]),busy=ref(false),error=ref('')
const actions:Record<string,string>={view:'查看和复用',edit:'重命名',remove:'删除',grant:'授权管理'}
let generation=0
async function search(query=''){const g=++generation;try{const r=await searchAssetPrincipals(query);if(g===generation)principals.value=r.data || []}catch{if(g===generation)error.value='主体搜索失败'}}
async function load(){grants.value=(await assetGrants(props.asset.id)).data || []}
async function add(){if(!principal.value)return;busy.value=true;error.value='';try{await grantAsset(props.asset.id,principal.value,action.value);await load()}catch{error.value='授权失败，请检查功能与资源资格'}finally{busy.value=false}}
async function remove(row:ResourceGrant){busy.value=true;error.value='';try{await revokeAssetGrant(props.asset.id,row.principalId,row.action);await load()}catch{error.value='撤销失败，请刷新或重新打开'}finally{busy.value=false}}
onMounted(async()=>{busy.value=true;try{await Promise.all([load(),search()])}catch{error.value='授权列表加载失败'}finally{busy.value=false}})
</script>
<style scoped>.hint{font-size:12px;color:#7f8b9d;line-height:1.8}.grant-row{display:flex;gap:8px;flex-wrap:wrap;margin:18px 0}.grant-row>.el-select{min-width:170px;flex:1}</style>
