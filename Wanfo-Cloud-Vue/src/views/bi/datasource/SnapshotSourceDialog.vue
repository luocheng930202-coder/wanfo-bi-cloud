<template>
  <el-dialog v-model="visible" :title="editing ? '更新数据快照' : '导入数据源'" width="min(680px, calc(100vw - 24px))" destroy-on-close :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy">
    <el-alert :closable="false" type="info" title="导入成功后，在数据集选择此数据源的 data 表。更新失败会保留上一份快照。" />
    <el-form label-position="top" class="snapshot-form" @submit.prevent>
      <el-form-item label="名称"><el-input v-model="name" :disabled="editing" maxlength="100" /></el-form-item>
      <el-form-item label="Schema 别名"><el-input v-model="alias" :disabled="editing" placeholder="小写字母开头，例如 sales_api" maxlength="31" /></el-form-item>
      <template v-if="kind === 'api'">
        <el-form-item label="API 地址（GET JSON）"><el-input v-model="url" placeholder="https://api.example.com/data" /></el-form-item>
        <el-form-item label="数组路径"><el-input v-model="dataPath" placeholder="data.rows；根节点为数组时留空" /></el-form-item>
        <el-form-item label="请求头 JSON"><el-input v-model="headers" type="textarea" :rows="3" :placeholder="editing ? '留空保留已保存请求头；修改地址时须重新填写，{} 表示清空' : '{ &quot;Authorization&quot;: &quot;Bearer …&quot; }'" /></el-form-item>
        <p class="hint">接口域名须在服务端 bi.external-source.allowed-origins 中允许。当前接入完整对象数组，分页接口须先提供完整数据接口。请求头加密保存。</p>
      </template>
      <template v-else>
        <el-form-item :label="kind === 'csv' ? 'UTF-8 CSV 文件' : 'Excel 文件（XLS / XLSX）'">
          <input type="file" :accept="kind === 'csv' ? '.csv' : '.xls,.xlsx'" @change="selectFile" />
        </el-form-item>
        <el-form-item v-if="kind === 'excel'" label="工作表名称"><el-input v-model="sheet" placeholder="留空使用第一个工作表；请将公式转换为值" /></el-form-item>
      </template>
      <p class="hint">最多 5 MB、200 列、20000 行；不会静默截断。再次导入须保持字段和类型一致。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
    </el-form>
    <template #footer><el-button :disabled="busy" @click="visible = false">取消</el-button><el-button type="primary" :loading="busy" @click="save">{{ editing ? '更新快照' : '导入并保存' }}</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { importApiSource, importFileSource, snapshotSourceInfo, type BiDatasource } from '@/api/bi/datasource'
const emit = defineEmits<{ saved: [] }>()
const visible=ref(false), editing=ref(false), busy=ref(false), error=ref('')
const kind=ref('api'), name=ref(''), alias=ref(''), url=ref(''), dataPath=ref(''), headers=ref(''), sheet=ref('')
const id=ref<number>(), file=ref<File>()
let generation=0
async function open(type: string, row?: BiDatasource) {
  const current=++generation
  busy.value=false
  kind.value=type; id.value=row?.id; editing.value=!!row; name.value=row?.name || ''; alias.value=row?.schemaName || ''
  url.value='';dataPath.value='';headers.value='';sheet.value='';file.value=undefined;error.value=''
  visible.value=true
  if(row && type==='api') {
    busy.value=true
    try { const result=await snapshotSourceInfo(row.id);if(current===generation){url.value=result.data.url || '';dataPath.value=result.data.dataPath || ''} }
    catch { if(current===generation)error.value='读取 API 配置失败，请关闭后重试' }
    finally {if(current===generation)busy.value=false}
  }
}
function selectFile(event: Event){file.value=(event.target as HTMLInputElement).files?.[0]}
async function save(){
  if(busy.value)return
  error.value=''
  if(!name.value.trim() || !/^[a-z][a-z0-9_]{0,30}$/.test(alias.value)){error.value='请填写名称和有效的 Schema 别名';return}
  busy.value=true
  try {
    if(kind.value==='api') {
      let parsed:Record<string,string>|undefined=undefined
      if(headers.value.trim()){
        parsed=JSON.parse(headers.value)
        if(!parsed || Array.isArray(parsed) || typeof parsed!=='object' || Object.values(parsed).some(v=>typeof v!=='string'))throw new Error('请求头须为字符串键值对象')
      }
      await importApiSource({id:id.value,name:name.value.trim(),schemaName:alias.value,url:url.value.trim(),dataPath:dataPath.value.trim(),headers:parsed})
    } else {
      if(!file.value)throw new Error('请选择文件')
      if(file.value.size>5*1024*1024)throw new Error('文件不能超过 5 MB')
      const data=new FormData();data.append('file',file.value);data.append('name',name.value.trim());data.append('schemaName',alias.value);data.append('sheet',sheet.value)
      if(id.value)data.append('id',String(id.value))
      await importFileSource(data)
    }
    ElMessage.success('数据快照已保存');visible.value=false;emit('saved')
  } catch(e) {error.value=e instanceof Error?e.message:'导入失败，请检查数据后重试'}
  finally {busy.value=false}
}
defineExpose({open})
</script>
<style scoped>
.snapshot-form{margin-top:20px}.hint{font-size:12px;color:var(--el-text-color-secondary);line-height:1.7}
</style>
