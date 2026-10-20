<template>
 <el-button v-if="checkPermi(['bi:asset:list'])" :disabled="disabled || busy" @click="open">从素材库选择</el-button>
 <el-dialog v-model="visible" title="图片素材库" width="min(760px,calc(100vw - 24px))" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
  <p class="hint">选入后图片随看板保存，删除库中素材不会影响已保存的看板。支持 256KB 内 PNG/JPEG，尺寸不超过 4096。「暂存上传」不入素材库，关闭窗口且未使用将在 24 小时内回收。</p>
  <el-alert v-if="error" :title="error" type="error" :closable="false"/>
  <div v-if="checkPermi(['bi:asset:add','bi:dashboard:edit','bi:dashboard:add'])" class="upload-row"><el-input v-model="name" maxlength="200" placeholder="素材名称" aria-label="素材名称" :disabled="busy"/><label class="file-label">选择图片<input type="file" accept="image/png,image/jpeg,image/webp" :disabled="busy" @change="fileChanged"/></label><el-button :disabled="busy || !image" @click="uploadStaging">暂存上传</el-button><el-button v-if="checkPermi(['bi:asset:add'])" :disabled="busy || !image || !name.trim()" @click="upload">上传到素材库</el-button></div>
  <div class="upload-row"><el-input v-model="searchText" maxlength="200" placeholder="按素材名称搜索" aria-label="素材搜索" clearable :disabled="busy" @keyup.enter="search" @clear="search"/><el-button :disabled="busy" @click="search">搜索素材</el-button></div>
  <el-table :data="items" v-loading="busy" empty-text="暂无可用素材，上传后可在不同看板复用"><el-table-column prop="name" label="名称"/><el-table-column label="大小" width="90"><template #default="{row}">{{ Math.ceil(row.byteSize/1024) }} KB</template></el-table-column><el-table-column label="操作" width="240"><template #default="{row}"><el-button link :disabled="busy" @click="select(row.id)">预览</el-button><el-button v-if="can(row,'edit')" link :disabled="busy" @click="rename(row)">重命名</el-button><el-button v-if="can(row,'grant')" link :disabled="busy" @click="grantAsset=row">授权</el-button><el-button v-if="can(row,'remove')" link type="danger" :disabled="busy" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
  <el-pagination v-if="total>20" v-model:current-page="page" :page-size="20" :total="total" layout="prev,pager,next" :disabled="busy" @current-change="load"/>
  <div v-if="chosen" class="asset-preview"><img :src="chosen.image" :alt="chosen.name"/><p>{{ chosen.name }}</p></div>
  <template #footer><el-button :disabled="busy" @click="visible=false">取消</el-button><el-button type="primary" :disabled="busy || !chosen || disabled" @click="use">使用此素材</el-button></template>
 </el-dialog>
 <AssetGrantDialog v-if="grantAsset" :key="grantAsset.id" :asset="grantAsset" @close="grantAsset=undefined;load()"/>
</template>
<script setup lang="ts">
import {ref,watch} from 'vue'
import {ElMessageBox} from 'element-plus'
import {checkPermi} from '@/utils/permission'
import AssetGrantDialog from './AssetGrantDialog.vue'
import {listAssets,getAsset,uploadAsset,uploadStagingAsset,releaseStagingAssets,deleteAsset,renameAsset,type Asset} from '@/api/bi/asset'
import {trackStagingAsset,untrackStagingAsset} from '@/utils/biStagingAssets'
const props=defineProps<{disabled?:boolean}>(),emit=defineEmits<{(e:'select',image:string):void}>()
const searchText=ref(''),grantAsset=ref<Asset>()
const visible=ref(false),busy=ref(false),error=ref(''),name=ref(''),image=ref(''),items=ref<Asset[]>([]),total=ref(0),page=ref(1),chosen=ref<Asset>()
let generation=0
const dialogStaging=ref<number[]>([])
async function releaseDialogStaging(){const ids=[...dialogStaging.value];dialogStaging.value=[];if(!ids.length)return;ids.forEach(untrackStagingAsset);try{await releaseStagingAssets(ids)}catch{ids.forEach(trackStagingAsset)}}
async function load(){const g=++generation;busy.value=true;error.value='';try{const r=(await listAssets(page.value,searchText.value)).data;if(g===generation){items.value=r.items;total.value=r.total}}catch{if(g===generation)error.value='素材加载失败，请检查权限后重试'}finally{if(g===generation)busy.value=false}}
function open(){visible.value=true;chosen.value=undefined;image.value='';name.value='';page.value=1;searchText.value='';dialogStaging.value=[];load()}
function can(asset:Asset,action:string){return checkPermi(['bi:asset:'+action]) && !!asset.resourceActions?.includes(action)}
function search(){page.value=1;chosen.value=undefined;load()}
async function rename(asset:Asset){let value:string;try{value=(await ElMessageBox.prompt('请输入新素材名称','重命名素材',{inputValue:asset.name,inputValidator:v=>!!v?.trim() && v.trim().length<=200 || '名称须为 1～200 字符'})).value}catch{return}busy.value=true;error.value='';try{await renameAsset(asset.id,value.trim(),asset.name);if(chosen.value?.id===asset.id)chosen.value.name=value.trim();await load()}catch(e){error.value=e instanceof Error?e.message:'重命名失败，请刷新后重试'}finally{busy.value=false}}
async function fileChanged(e:Event){const input=e.target as HTMLInputElement,file=input.files?.[0];input.value='';image.value='';if(!file)return;if(file.size>262144 || !['image/png','image/jpeg','image/webp'].includes(file.type)){error.value='请选择 256KB 内 PNG/JPEG/WebP 图片';return}busy.value=true;try{image.value=await new Promise<string>((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(String(reader.result));reader.onerror=reject;reader.readAsDataURL(file)});name.value=file.name.slice(0,200);error.value=''}catch{error.value='图片读取失败'}finally{busy.value=false}}
async function uploadStaging(){if(!image.value || busy.value)return;busy.value=true;error.value='';try{const r=(await uploadStagingAsset(image.value,name.value.trim() || '暂存图片')).data;trackStagingAsset(r.id);dialogStaging.value.push(r.id);image.value='';chosen.value={id:r.id,name:r.name,byteSize:r.byteSize,image:r.image};name.value=''}catch(e){error.value=e instanceof Error?e.message:'暂存失败，请检查图片或权限'}finally{busy.value=false}}
async function select(id:number){busy.value=true;error.value='';chosen.value=undefined;try{chosen.value=(await getAsset(id)).data}catch{error.value='素材不存在或没有读取权限'}finally{busy.value=false}}
async function upload(){busy.value=true;error.value='';try{const r=(await uploadAsset(name.value.trim(),image.value)).data;image.value='';name.value='';page.value=1;await load();await select(r.id)}catch(e){error.value=e instanceof Error?e.message:'上传失败，请检查图片格式、尺寸或权限'}finally{busy.value=false}}
async function remove(asset:Asset){try{await ElMessageBox.confirm('删除库中素材“'+asset.name+'”？已保存的看板保留其图片副本。','删除素材',{type:'warning'})}catch{return}busy.value=true;try{await deleteAsset(asset.id);if(chosen.value?.id===asset.id)chosen.value=undefined;if(items.value.length===1 && page.value>1)page.value--;await load()}catch{error.value='删除失败，请检查资源权限'}finally{busy.value=false}}
async function use(){if(!chosen.value?.image || busy.value || props.disabled)return;busy.value=true;error.value='';try{let image=chosen.value.image;if(dialogStaging.value.includes(chosen.value.id))dialogStaging.value=dialogStaging.value.filter(id=>id!==chosen.value!.id);else{const asset=(await getAsset(chosen.value.id)).data;image=asset.image!}emit('select',image);visible.value=false}catch{chosen.value=undefined;error.value='素材已失效或权限已撤销，请刷新后重新选择'}finally{busy.value=false}}
watch(visible,v=>{if(!v)void releaseDialogStaging()})
</script>
<style scoped>
.hint{font-size:12px;color:#7f8b9d;line-height:1.8}.upload-row{display:flex;gap:10px;flex-wrap:wrap;margin:18px 0}.upload-row>.el-input{flex:1;min-width:180px}.file-label{display:flex;align-items:center;position:relative;border:1px solid #dce3ef;border-radius:4px;padding:0 12px;min-height:32px;cursor:pointer;font-size:12px}.file-label input{position:absolute;width:100%;height:100%;inset:0;opacity:0;cursor:pointer}.asset-preview{text-align:center;margin:18px 0;background:#f4f6fa;border-radius:8px;padding:15px}.asset-preview img{max-width:100%;max-height:180px;object-fit:contain}.asset-preview p{font-size:12px;color:#69788e}
</style>
