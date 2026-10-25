<template>
  <el-button v-if="!hiddenTrigger && checkPermi([`bi:${type}:edit`])" link type="primary" @click.stop="open">历史版本</el-button>
  <el-dialog v-model="visible" title="历史版本与恢复" width="min(1000px, calc(100vw - 24px))" append-to-body :close-on-click-modal="!restoring" :close-on-press-escape="!restoring" :show-close="!restoring" @closed="generation++">
    <el-alert title="保留最近 50 个保存版本。历史记录从此功能启用后开始，首次修改时补存当前版本。恢复会创建新的草稿版本，不自动发布，也不恢复历史业务数据。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="rows" v-loading="loading" max-height="250">
      <el-table-column prop="version" label="版本" width="80"/><el-table-column label="操作" width="100"><template #default="{row}">{{ operations[row.operation] || row.operation }}</template></el-table-column>
      <el-table-column prop="actor" label="操作人"/><el-table-column label="记录时间（本地）"><template #default="{row}">{{ recordedAt(row.createdAt) }}</template></el-table-column>
      <el-table-column label="操作" width="110"><template #default="{row}"><el-button link type="primary" :disabled="restoring" @click="select(row.version)">比较配置</el-button></template></el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length && !error" description="暂无历史记录，下一次保存后开始记录" :image-size="70" />
    <template v-if="selected!==undefined && definition">
      <p>历史 v{{ selected }} → 当前 v{{ currentVersion }}：{{ differences.length }} 处差异（最多显示 200 处）</p>
      <el-table :data="differences" max-height="300"><el-table-column prop="path" label="配置路径"/><el-table-column prop="before" label="历史配置" show-overflow-tooltip/><el-table-column prop="after" label="当前配置" show-overflow-tooltip/></el-table>
      <details><summary>历史配置预览</summary><pre class="history-json">{{ JSON.stringify(definition,null,2) }}</pre></details>
    </template>
    <template #footer><el-button :disabled="restoring" @click="visible=false">关闭</el-button><el-button type="primary" :loading="restoring" :disabled="loading || !definition || selected===undefined" @click="restore">恢复为新草稿</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import {ref,computed} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import request from '@/utils/request'
import {checkPermi} from '@/utils/permission'
import {versionDifferences} from '@/utils/biVersionDiff'
import type {BiResult} from '@/api/bi/response'
const props=defineProps<{type:'chart'|'dashboard';id:number;hiddenTrigger?:boolean}>()
const emit=defineEmits<{restored:[]}>()
const visible=ref(false),loading=ref(false),restoring=ref(false),error=ref('')
const rows=ref<{version:number;operation:string;actor:string;createdAt:string}[]>([])
const selected=ref<number>(),currentVersion=ref(0),current=ref<unknown>(),definition=ref<unknown>()
const differences=computed(()=>versionDifferences(definition.value,current.value))
function recordedAt(value:string){value=value.trim();return /^\d{12,14}$/.test(value)?new Date(Number(value)).toLocaleString():value}
const operations:Record<string,string>={baseline:'启用时快照',create:'创建',save:'保存',publish:'发布',unpublish:'下线'}
let generation=0
const endpoint=()=>`/bi/resources/${props.type}/${props.id}/history`
async function open(){
  const token=++generation;visible.value=true;loading.value=true;rows.value=[];error.value='';selected.value=undefined;definition.value=undefined
  try{
    const [history,latest]:[BiResult<typeof rows.value>,BiResult<{version:number;definition:unknown}>]=await Promise.all([request({url:endpoint()}),request({url:props.type==='chart'?`/bi/chart/saved/${props.id}`:`/bi/dashboard/${props.id}/draft`})])
    if(token===generation){rows.value=history.data;current.value=latest.data.definition;currentVersion.value=latest.data.version}
  }catch{if(token===generation)error.value='历史记录读取失败，请确认编辑权限后重试'}finally{if(token===generation)loading.value=false}
}
async function select(version:number){
  const token=++generation;loading.value=true;definition.value=undefined;selected.value=undefined;error.value=''
  try{const response:BiResult<{definition:unknown}>=await request({url:`${endpoint()}/${version}`});if(token===generation){selected.value=version;definition.value=response.data.definition}}
  catch{if(token===generation)error.value='该历史版本不可读取，请刷新后重试'}finally{if(token===generation)loading.value=false}
}
async function restore(){
  if(restoring.value || selected.value===undefined || !definition.value)return
  try{await ElMessageBox.confirm(`将 v${selected.value} 的配置恢复为新草稿，当前已发布版本保持不变。确认恢复？`,'恢复历史配置',{type:'warning'})}catch{return}
  restoring.value=true
  try{await request({url:`${endpoint()}/${selected.value}/restore`,method:'post',data:{currentVersion:currentVersion.value}});ElMessage.success('已恢复为新的草稿版本');visible.value=false;emit('restored')}
  catch{error.value='恢复失败；资源可能已修改，或历史字段/权限已失效，请刷新并核对'}finally{restoring.value=false}
}
defineExpose({open})
</script>
<style scoped>.history-json{white-space:pre-wrap;overflow:auto;max-height:260px;word-break:break-all;font-size:12px}details{margin:14px 0}</style>
