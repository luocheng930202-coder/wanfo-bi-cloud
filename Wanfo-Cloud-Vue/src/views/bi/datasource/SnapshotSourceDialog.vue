<template>
  <el-dialog v-model="visible" :title="editing ? '更新数据快照' : '导入数据源'" width="min(680px, calc(100vw - 24px))" destroy-on-close :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy">
    <el-alert :closable="false" type="info" title="导入成功后，在数据集选择此数据源的 data 表。更新失败会保留上一份快照。" />
    <el-form label-position="top" class="snapshot-form" :disabled="busy" @submit.prevent>
      <el-form-item label="名称"><el-input v-model="name" :disabled="editing" maxlength="100" /></el-form-item>
      <el-form-item label="Schema 别名"><el-input v-model="alias" :disabled="editing" placeholder="小写字母开头，例如 sales_api" maxlength="31" /></el-form-item>
      <template v-if="kind === 'api'">
        <el-form-item label="API 地址"><el-input v-model="url" placeholder="https://api.example.com/data" /></el-form-item>
        <el-form-item label="请求方式"><el-select v-model="method"><el-option label="GET" value="GET"/><el-option label="POST" value="POST"/></el-select></el-form-item>
        <el-form-item v-if="method === 'POST'" label="POST JSON 参数"><el-input v-model="bodyText" type="textarea" :rows="4" placeholder="{ &quot;page&quot;: 1 }"/></el-form-item>
        <el-form-item label="展开嵌套对象"><el-switch v-model="flatten"/><small class="hint">对象字段展开为 a.b 列。</small></el-form-item>
        <el-form-item label="展开嵌套数组（可选）"><el-input v-model="expandArrayPath" placeholder="如 items 或 order.lines；留空不展开"/><small class="hint">相对于每条 API 数据记录填写。每个子对象生成一行，父字段会重复；空数组跳过。建议同时开启“展开嵌套对象”。</small></el-form-item>
        <el-divider content-position="left">OAuth 2.0（客户端凭据）</el-divider>
        <el-form-item label="令牌地址"><el-input v-model="oauth.tokenUrl" placeholder="https://api.example.com/oauth/token"/></el-form-item>
        <template v-if="oauth.tokenUrl">
          <el-form-item label="Client ID"><el-input v-model="oauth.clientId" autocomplete="off"/></el-form-item>
          <el-form-item label="Client Secret"><el-input v-model="oauth.clientSecret" type="password" show-password autocomplete="new-password" :placeholder="oauth.secretSet ? '留空保留已保存密钥' : '请输入密钥'"/></el-form-item>
          <el-form-item label="Scope（可选）"><el-input v-model="oauth.scope"/></el-form-item>
          <el-form-item label="令牌字段"><el-input v-model="oauth.tokenField" placeholder="access_token"/></el-form-item>
        </template>
        <el-form-item label="数组路径"><el-input v-model="dataPath" placeholder="data.rows；根节点为数组时留空" /></el-form-item>
        <el-form-item label="请求头 JSON"><el-input v-model="headers" type="textarea" :rows="3" :placeholder="editing ? '留空保留已保存请求头；修改地址时须重新填写，{} 表示清空' : '{ &quot;Authorization&quot;: &quot;Bearer …&quot; }'" /></el-form-item>
        <el-form-item label="自动分页"><el-switch v-model="paged" /></el-form-item>
        <template v-if="paged">
          <el-form-item label="分页方式"><el-radio-group v-model="paginationMode"><el-radio-button value="page">页码分页</el-radio-button><el-radio-button value="cursor">游标分页</el-radio-button></el-radio-group></el-form-item>
          <el-row v-if="paginationMode==='page'" :gutter="12"><el-col :span="12"><el-form-item label="页码参数名"><el-input v-model="pagination.pageParam" /></el-form-item></el-col><el-col :span="12"><el-form-item label="页大小参数名"><el-input v-model="pagination.sizeParam" /></el-form-item></el-col></el-row>
          <el-row v-else :gutter="12"><el-col :span="12"><el-form-item label="游标参数名"><el-input v-model="pagination.cursorParam" placeholder="cursor"/></el-form-item></el-col><el-col :span="12"><el-form-item label="下一游标 JSON 路径"><el-input v-model="pagination.nextCursorPath" placeholder="data.nextCursor"/></el-form-item></el-col></el-row>
          <el-row :gutter="12"><el-col :span="12"><el-form-item label="页大小参数名"><el-input v-model="pagination.sizeParam" /></el-form-item></el-col><el-col :span="12"><el-form-item label="每页行数"><el-input-number v-model="pagination.pageSize" :min="1" :max="1000" :precision="0" /></el-form-item></el-col></el-row>
          <el-row v-if="paginationMode==='page'" :gutter="12"><el-col :span="12"><el-form-item label="起始页"><el-select v-model="pagination.startPage"><el-option :value="0" label="0"/><el-option :value="1" label="1"/></el-select></el-form-item></el-col></el-row>
          <el-form-item v-if="paginationMode==='cursor'" label="初始游标（可选）"><el-input v-model="pagination.initialCursor" /></el-form-item>
          <el-form-item label="最多页数"><el-input-number v-model="pagination.maxPages" :min="1" :max="100" :precision="0" /></el-form-item>
          <el-form-item label="总行数路径"><el-input v-model="pagination.totalPath" placeholder="如 data.total；留空尝试识别 total / totalCount" /></el-form-item>
        </template>
        <el-form-item label="自动刷新间隔（分钟）"><el-input-number v-model="refreshMinutes" :min="0" :max="1440" :precision="0" /><small class="hint">0 为关闭，启用时为 5～1440 分钟；后台约每分钟检查一次，失败保留旧数据。</small></el-form-item>
        <p v-if="scheduleStatus" class="hint">上次自动刷新状态：{{ scheduleStatus==='success'?'成功':scheduleStatus.startsWith('failed')?'失败，已保留旧数据':'尚未执行' }}</p>
        <p class="hint">接口域名须在服务端 bi.external-source.allowed-origins 中允许。支持页码或下一游标分页，累计最多 5 MB、20000 行；达到页数上限仍未完成会拒绝更新。请求头加密保存。</p>
      </template>
      <template v-else>
        <el-form-item :label="kind === 'csv' ? 'UTF-8 CSV 文件' : 'Excel 文件（XLS / XLSX）'">
          <input type="file" :disabled="busy" :accept="kind === 'csv' ? '.csv' : '.xls,.xlsx'" @change="selectFile" />
        </el-form-item>
        <el-form-item v-if="kind === 'excel'" label="工作表名称"><el-input v-model="sheet" placeholder="留空使用第一个工作表；请将公式转换为值" /></el-form-item>
        <el-form-item v-if="editing" label="更新方式"><el-radio-group v-model="fileMode"><el-radio value="replace">覆盖快照</el-radio><el-radio value="append">按行追加</el-radio><el-radio value="replace-structure">覆盖并变更结构</el-radio></el-radio-group></el-form-item>
        <el-alert v-if="fileMode==='replace-structure'" title="结构变更会替换字段列表，关联数据集和图表可能需要重新同步字段。" type="warning" :closable="false"/>
        <el-form-item label="异常行处理"><el-radio-group v-model="invalidMode"><el-radio value="reject">发现异常则拒绝整批</el-radio><el-radio value="skip">跳过异常行并报告</el-radio></el-radio-group></el-form-item>
        <el-form-item label="重复行处理"><el-select v-model="dedupeMode" :disabled="dedupeMode==='keys'&&!previewColumns.length"><el-option label="保留全部记录" value="none"/><el-option label="按所有字段去重" value="all-columns"/><el-option label="按选定字段去重" value="keys"/></el-select></el-form-item>
        <el-form-item v-if="dedupeMode==='keys'" label="去重键"><el-select v-model="dedupeKeys" multiple filterable style="width:100%" placeholder="选择一列或多列作为业务键"><el-option v-for="column in previewColumns.filter(c=>c.targetName)" :key="column.targetName" :label="column.targetName" :value="column.targetName"/></el-select></el-form-item>
        <el-alert v-if="fileMode==='append' && dedupeMode==='none'" title="当前按行追加且不去重；如重复导入，请选择全部字段或业务键去重。业务键去重保留最后出现的记录。" type="warning" :closable="false" />
        <el-button :loading="busy" :disabled="!file" @click="previewFile">预览并映射字段</el-button>
        <template v-if="previewColumns.length">
          <p>本次文件 {{ previewCount }} 行；已自动建议 {{ suggestedMappingCount }} 个唯一字段映射（忽略大小写和分隔符差异）。请核对后保存；仍可改名、对齐旧字段或忽略，重名目标会被拒绝。</p>
          <el-table :data="previewColumns" max-height="260"><el-table-column prop="name" label="源字段" min-width="130"/><el-table-column label="目标字段" min-width="190"><template #default="{row}"><el-select v-model="row.targetName" filterable allow-create default-first-option clearable placeholder="输入或选择目标字段"><el-option label="忽略此字段" value=""/><el-option v-for="column in existingColumns" :key="column.name" :label="column.name+'（'+column.type+'）'" :value="column.name"/></el-select></template></el-table-column><el-table-column label="类型" width="150"><template #default="{row}"><el-select v-model="row.type" :disabled="editing && fileMode!=='replace-structure' && existingColumns.some(c=>c.name===row.targetName)"><el-option v-for="option in columnTypes" :key="option.value" :label="option.label" :value="option.value" /></el-select></template></el-table-column></el-table>
          <el-table :data="previewRows" max-height="230"><el-table-column v-for="(column,index) in previewColumns" :key="column.name" :label="column.name" :prop="String(index)" min-width="110" show-overflow-tooltip /></el-table>
          <el-alert v-if="editing && fileMode==='replace-structure'" :title="schemaChangeSummary" type="warning" :closable="false" class="mt8"/>
        </template>
      </template>
      <p class="hint">最多 5 MB、200 列、20000 行；不会静默截断。再次导入须保持字段和类型一致。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
    </el-form>
    <template #footer><el-button :disabled="busy" @click="visible = false">取消</el-button><el-button type="primary" :loading="busy" @click="save">{{ editing ? '更新快照' : '导入并保存' }}</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref,reactive,watch,computed } from 'vue'
import { ElMessage,ElMessageBox } from 'element-plus'
import { importApiSource, importFileSource, snapshotSourceInfo, previewFileSource, type BiDatasource } from '@/api/bi/datasource'
const emit = defineEmits<{ saved: [] }>()
const visible=ref(false), editing=ref(false), busy=ref(false), error=ref('')
const kind=ref('api'), name=ref(''), alias=ref(''), url=ref(''), dataPath=ref(''), headers=ref(''), sheet=ref('')
const id=ref<number>(), file=ref<File>()
const paged=ref(false),paginationMode=ref<'page'|'cursor'>('page'),refreshMinutes=ref(0),scheduleStatus=ref(''),fileMode=ref('replace'),invalidMode=ref('reject'),dedupeMode=ref('none'),dedupeKeys=ref<string[]>([]),method=ref('GET'),bodyText=ref('{}'),flatten=ref(false),expandArrayPath=ref('')
const oauth=reactive({tokenUrl:'',clientId:'',clientSecret:'',scope:'',tokenField:'access_token',secretSet:false})
const pagination=reactive({pageParam:'page',sizeParam:'pageSize',pageSize:100,startPage:1,maxPages:100,totalPath:'',cursorParam:'',nextCursorPath:'',initialCursor:''})
const previewColumns=ref<{name:string;type:string;targetName:string}[]>([]),existingColumns=ref<{name:string;type:string}[]>([]),previewRows=ref<(string|null)[][]>([]),previewCount=ref(0),suggestedMappingCount=ref(0)
const schemaChangeSummary=computed(()=>{const before=existingColumns.value.map(c=>c.name),after=previewColumns.value.map(c=>c.targetName).filter(Boolean),kept=after.filter(c=>before.includes(c)),added=after.filter(c=>!before.includes(c)),missing=before.filter(c=>!after.includes(c));return `结构预览：保留 ${kept.length} 个既有字段，新增 ${added.length} 个，移除 ${missing.length} 个。${added.length?` 新增：${added.join('、')}。`:''}${missing.length?` 移除：${missing.join('、')}。`:''}`})
watch(previewColumns,()=>{const valid=new Set(previewColumns.value.map(c=>c.targetName).filter(Boolean));dedupeKeys.value=dedupeKeys.value.filter(key=>valid.has(key))},{deep:true})
const columnTypes=[{value:'VARCHAR',label:'文本'},{value:'DECIMAL',label:'数值'},{value:'BOOLEAN',label:'布尔'},{value:'DATE',label:'日期'},{value:'TIMESTAMP',label:'日期时间'}]
watch(sheet,()=>{previewColumns.value=[];previewRows.value=[]})
let generation=0
async function open(type: string, row?: BiDatasource) {
  const current=++generation
  busy.value=false
  paged.value=false;refreshMinutes.value=0;scheduleStatus.value='';fileMode.value='replace';invalidMode.value='reject';dedupeMode.value='none';dedupeKeys.value=[];existingColumns.value=[];method.value='GET';bodyText.value='{}';flatten.value=false;expandArrayPath.value='';Object.assign(oauth,{tokenUrl:'',clientId:'',clientSecret:'',scope:'',tokenField:'access_token',secretSet:false});previewColumns.value=[];previewRows.value=[];previewCount.value=0
  paginationMode.value='page';Object.assign(pagination,{pageParam:'page',sizeParam:'pageSize',pageSize:100,startPage:1,maxPages:100,totalPath:'',cursorParam:'',nextCursorPath:'',initialCursor:''})
  kind.value=type; id.value=row?.id; editing.value=!!row; name.value=row?.name || ''; alias.value=row?.schemaName || ''
  url.value='';dataPath.value='';headers.value='';sheet.value='';file.value=undefined;error.value=''
  visible.value=true
  if(row && type==='api') {
    busy.value=true
    try { const result=await snapshotSourceInfo(row.id);if(current===generation){url.value=result.data.url || '';dataPath.value=result.data.dataPath || '';expandArrayPath.value=result.data.expandArrayPath || '';paged.value=!!result.data.pagination;if(result.data.pagination){Object.assign(pagination,result.data.pagination);paginationMode.value=result.data.pagination.cursorParam?'cursor':'page'}refreshMinutes.value=result.data.schedule?.intervalMinutes || 0;scheduleStatus.value=result.data.schedule?.lastStatus || '';method.value=result.data.method || 'GET';bodyText.value=JSON.stringify(result.data.body || {},null,2);flatten.value=!!result.data.flatten;if(result.data.oauth)Object.assign(oauth,result.data.oauth,{clientSecret:''})} }
    catch { if(current===generation)error.value='读取 API 配置失败，请关闭后重试' }
    finally {if(current===generation)busy.value=false}
  }
}
function selectFile(event: Event){file.value=(event.target as HTMLInputElement).files?.[0];previewColumns.value=[];existingColumns.value=[];previewRows.value=[];dedupeKeys.value=[];error.value=''}
function fileData(){
  const data=new FormData();if(file.value)data.append('file',file.value);data.append('name',name.value.trim());data.append('schemaName',alias.value);data.append('sheet',sheet.value)
  if(id.value)data.append('id',String(id.value));return data
}
async function previewFile(){
  if(!file.value || busy.value)return
  busy.value=true;error.value=''
  try{
    const response=await previewFileSource(fileData());existingColumns.value=response.data.existingColumns||[]
    const normalize=(value:string)=>value.normalize('NFKC').toLocaleLowerCase().replace(/[\s\p{P}\p{S}_]+/gu,'')
    const targets=new Map<string,string[]>();for(const column of existingColumns.value){const key=normalize(column.name);const matches=targets.get(key)||[];matches.push(column.name);targets.set(key,matches)}
    const used=new Set<string>();suggestedMappingCount.value=0
    previewColumns.value=response.data.columns.map(column=>{const matches=targets.get(normalize(column.name))||[];let targetName=column.name;if(matches.length===1&&!used.has(matches[0])){targetName=matches[0];used.add(targetName);suggestedMappingCount.value++}return{...column,targetName}})
    previewRows.value=response.data.rows;previewCount.value=response.data.rowCount
  }
  catch{error.value='文件预览失败，请检查编码、表头、工作表及文件大小'}finally{busy.value=false}
}
async function save(){
  if(busy.value)return
  error.value=''
  if(!name.value.trim() || !/^[a-z][a-z0-9_]{0,30}$/.test(alias.value)){error.value='请填写名称和有效的 Schema 别名';return}
  busy.value=true
  let rejected=0
  try {
    if(kind.value==='api') {
      let parsed:Record<string,string>|undefined=undefined
      if(headers.value.trim()){
        parsed=JSON.parse(headers.value)
        if(!parsed || Array.isArray(parsed) || typeof parsed!=='object' || Object.values(parsed).some(v=>typeof v!=='string'))throw new Error('请求头须为字符串键值对象')
      }
      let body:Record<string,unknown>|undefined=undefined;if(method.value==='POST'){body=JSON.parse(bodyText.value||'{}');if(!body||Array.isArray(body)||typeof body!=='object')throw new Error('POST 参数须为 JSON 对象')}
      const auth=oauth.tokenUrl.trim()?{tokenUrl:oauth.tokenUrl.trim(),clientId:oauth.clientId.trim(),clientSecret:oauth.clientSecret,scope:oauth.scope.trim(),tokenField:oauth.tokenField.trim()||'access_token'}:undefined
      await importApiSource({id:id.value,name:name.value.trim(),schemaName:alias.value,url:url.value.trim(),dataPath:dataPath.value.trim(),headers:parsed,pagination:paged.value?{...pagination}:undefined,refreshMinutes:refreshMinutes.value,method:method.value,body,flatten:flatten.value,expandArrayPath:expandArrayPath.value.trim(),oauth:auth})
    } else {
      if(!file.value)throw new Error('请选择文件')
      if(file.value.size>5*1024*1024)throw new Error('文件不能超过 5 MB')
      if(!previewColumns.value.length)throw new Error('请先预览文件并检查字段映射')
      if(dedupeMode.value==='keys'&&!dedupeKeys.value.length)throw new Error('请选择至少一个去重键字段')
      if(editing.value&&fileMode.value==='replace-structure'&&existingColumns.value.length){try{await ElMessageBox.confirm(`${schemaChangeSummary.value}\n\n继续后将替换快照字段结构，依赖数据集可能需要重新同步。`,'确认结构变更',{type:'warning',confirmButtonText:'替换结构',cancelButtonText:'取消'})}catch{return}}
      const data=fileData();data.append('mode',fileMode.value);data.append('invalidMode',invalidMode.value);data.append('dedupeMode',dedupeMode.value)
      data.append('columnMapping',JSON.stringify(Object.fromEntries(previewColumns.value.map(c=>[c.name,c.targetName]))));if(dedupeMode.value==='keys')data.append('dedupeKeys',JSON.stringify(dedupeKeys.value))
      const mappedTypes=Object.fromEntries(previewColumns.value.filter(c=>c.targetName).map(c=>[c.targetName,c.type]));if(Object.keys(mappedTypes).length)data.append('columnTypes',JSON.stringify(mappedTypes))
      const response=await importFileSource(data);rejected=response.data.rejectedRows||0;const duplicates=response.data.duplicateRows||0;if(rejected){const details=(response.data.rowErrors||[]).slice(0,12).map(item=>item.message).join('\n');await ElMessageBox.alert(`共跳过 ${rejected} 行异常数据。${response.data.rowErrors?.length===100?'仅列出前 100 条问题。':''}\n\n${details}`,'导入异常行报告',{confirmButtonText:'知道了',type:'warning',customClass:'bi-import-report'})}if(duplicates)ElMessage.info(`已按所选策略移除 ${duplicates} 行重复记录`)
    }
    visible.value=false;emit('saved');if(!rejected)ElMessage.success('数据快照已保存')
  } catch(e) {error.value=e instanceof Error?e.message:'导入失败，请检查数据后重试'}
  finally {busy.value=false}
}
defineExpose({open})
</script>
<style scoped>
.snapshot-form{margin-top:20px}.hint{font-size:12px;color:var(--el-text-color-secondary);line-height:1.7}
</style>
