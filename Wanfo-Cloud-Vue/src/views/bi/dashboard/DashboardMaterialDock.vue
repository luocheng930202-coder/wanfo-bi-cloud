<template>
  <div class="material-dock">
    <section class="builtin-block">
      <h3>内置边框</h3>
      <div class="material-grid">
        <button v-for="m in filteredBoards" :key="m.id" type="button" class="material-card dark" :disabled="disabled" @click="pickBuiltin(m)">
          <img :src="m.image" :alt="m.label"/>
          <strong>{{ m.label }}</strong>
        </button>
      </div>
      <h3>内置图形</h3>
      <div class="material-grid">
        <button v-for="m in filteredShapes" :key="m.id" type="button" class="material-card dark" :disabled="disabled" @click="pickBuiltin(m)">
          <img :src="m.image" :alt="m.label"/>
          <strong>{{ m.label }}</strong>
        </button>
      </div>
    </section>
    <div class="material-toolbar">
      <el-input v-model="searchText" maxlength="200" clearable placeholder="搜索素材" aria-label="搜索素材" @keyup.enter="search" @clear="search"/>
      <el-button :disabled="busy || !canList" @click="search">搜索</el-button>
    </div>
    <p v-if="!canList" class="hint">缺少素材库权限时仍可使用上方内置素材；上传/复用需 bi:asset:list。</p>
    <template v-else>
      <div v-if="canUpload" class="upload-row">
        <el-input v-model="name" maxlength="200" placeholder="素材名称" aria-label="素材名称" :disabled="busy"/>
        <label class="file-label">选择图片<input type="file" :accept="imageAccept" :disabled="busy" @change="fileChanged"/></label>
        <el-button size="small" :disabled="busy || !image" @click="uploadStaging">暂存</el-button>
        <el-button v-if="checkPermi(['bi:asset:add'])" size="small" :disabled="busy || !image || !name.trim()" @click="uploadLibrary">入库</el-button>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice"/>
      <h3 class="library-title">素材库</h3>
      <div v-loading="busy" class="material-grid">
        <button v-for="row in items" :key="row.id" type="button" class="material-card" :disabled="busy || disabled" @click="pick(row.id)">
          <img v-if="thumbs[row.id]" :src="thumbs[row.id]" :alt="row.name"/>
          <span v-else class="thumb-placeholder">加载中</span>
          <strong>{{ row.name }}</strong>
        </button>
        <p v-if="!busy && !items.length" class="hint">暂无自定义素材，可上传或使用上方内置边框。</p>
      </div>
      <el-pagination v-if="total>20" v-model:current-page="page" :page-size="20" :total="total" layout="prev,pager,next" small :disabled="busy" @current-change="load"/>
    </template>
  </div>
</template>
<script setup lang="ts">
import {ref,computed,watch,onMounted} from 'vue'
import {checkPermi} from '@/utils/permission'
import {listAssets,getAsset,uploadAsset,uploadStagingAsset,type Asset} from '@/api/bi/asset'
import {trackStagingAsset} from '@/utils/biStagingAssets'
import {IMAGE_ACCEPT,readImageFileAsDataUrl} from '@/utils/biImageDataUrl'
import {builtinBoards,builtinShapes,type BuiltinMaterial} from '@/utils/biBuiltinMaterials'
const props=defineProps<{disabled?:boolean;active?:boolean}>()
const emit=defineEmits<{(e:'select',image:string,name:string,size?:{w:number;h:number}):void}>()
const canList=checkPermi(['bi:asset:list'])
const canUpload=checkPermi(['bi:asset:add','bi:dashboard:edit','bi:dashboard:add'])
const imageAccept=IMAGE_ACCEPT
const searchText=ref(''),busy=ref(false),error=ref(''),name=ref(''),image=ref(''),items=ref<Asset[]>([]),total=ref(0),page=ref(1)
const thumbs=ref<Record<number,string>>({})
const q=computed(()=>searchText.value.trim().toLowerCase())
const filteredBoards=computed(()=>!q.value?builtinBoards:builtinBoards.filter(m=>m.label.toLowerCase().includes(q.value) || m.id.includes(q.value)))
const filteredShapes=computed(()=>!q.value?builtinShapes:builtinShapes.filter(m=>m.label.toLowerCase().includes(q.value) || m.id.includes(q.value)))
let generation=0
async function load(){
  if(!canList)return
  const g=++generation;busy.value=true;error.value=''
  try{
    const r=(await listAssets(page.value,searchText.value)).data
    if(g!==generation)return
    items.value=r.items;total.value=r.total
    for(const row of r.items){
      if(thumbs.value[row.id])continue
      try{const a=(await getAsset(row.id)).data;if(a.image)thumbs.value={...thumbs.value,[row.id]:a.image}}catch{/* skip */}
    }
  }catch{if(g===generation)error.value='素材加载失败'}
  finally{if(g===generation)busy.value=false}
}
function search(){page.value=1;load()}
function pickBuiltin(m:BuiltinMaterial){if(props.disabled)return;emit('select',m.image,m.label,{w:m.w,h:m.h})}
async function pick(id:number){
  if(props.disabled || busy.value)return
  busy.value=true;error.value=''
  try{const a=(await getAsset(id)).data;if(!a.image)throw Error('素材无图片');emit('select',a.image,a.name)}
  catch{error.value='素材不可用或无权限'}
  finally{busy.value=false}
}
async function fileChanged(e:Event){
  const input=e.target as HTMLInputElement,file=input.files?.[0];input.value='';image.value=''
  if(!file)return
  busy.value=true
  try{image.value=await readImageFileAsDataUrl(file);name.value=file.name.slice(0,200);error.value=''}
  catch(err){error.value=err instanceof Error?err.message:'请选择 512KB 内 PNG/JPEG/WebP/GIF/SVG'}finally{busy.value=false}
}
async function uploadStaging(){
  if(!image.value || busy.value)return
  busy.value=true;error.value=''
  try{
    const r=(await uploadStagingAsset(image.value,name.value.trim() || '暂存图片')).data
    trackStagingAsset(r.id);emit('select',r.image,r.name);image.value='';name.value=''
  }catch(e){error.value=e instanceof Error?e.message:'暂存失败'}
  finally{busy.value=false}
}
async function uploadLibrary(){
  if(!image.value || !name.value.trim() || busy.value)return
  busy.value=true;error.value=''
  try{await uploadAsset(name.value.trim(),image.value);image.value='';name.value='';page.value=1;await load()}
  catch(e){error.value=e instanceof Error?e.message:'上传失败'}
  finally{busy.value=false}
}
watch(()=>props.active,v=>{if(v)load()})
onMounted(()=>{if(props.active)load()})
</script>
<style scoped>
.material-dock{display:flex;flex-direction:column;gap:8px;min-height:0;flex:1;padding:0 10px 12px;overflow:auto}
.builtin-block h3,.library-title{margin:8px 0 6px;font-size:12px;font-weight:600;color:#64748b}
.material-toolbar{display:flex;gap:6px}.material-toolbar .el-input{flex:1}
.upload-row{display:flex;flex-wrap:wrap;gap:6px;align-items:center}
.file-label{position:relative;display:inline-flex;align-items:center;border:1px solid #dce3ef;border-radius:4px;padding:0 8px;height:28px;font-size:12px;cursor:pointer}
.file-label input{position:absolute;inset:0;opacity:0;cursor:pointer}
.hint{font-size:11px;color:#98a2b3;line-height:1.6;margin:4px 0}
.notice{margin:4px 0}
.material-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;padding:2px}
.material-card{border:1px solid #eef1f6;background:#fafbfd;border-radius:8px;padding:6px;cursor:pointer;text-align:left;font:inherit}
.material-card.dark img{background:#0f172a}
.material-card:hover:not(:disabled){border-color:#9cb7fa;background:#f6f8ff}
.material-card:disabled{opacity:.5;cursor:not-allowed}
.material-card img,.thumb-placeholder{display:block;width:100%;height:64px;object-fit:contain;background:#eef2f7;border-radius:4px}
.thumb-placeholder{display:grid;place-items:center;font-size:10px;color:#98a2b3}
.material-card strong{display:block;margin-top:6px;font-size:11px;color:#344563;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
</style>
