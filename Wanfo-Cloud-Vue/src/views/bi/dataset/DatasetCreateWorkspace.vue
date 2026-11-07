<template>
  <main class="dataset-builder">
    <header class="builder-header"><div class="header-title"><el-button link @click="router.push(editing ? `/bi-admin/dataset-workspace/${editId}/view` : '/bi-admin/dataset')">←</el-button><strong>{{ name || '未命名数据集' }}</strong><el-tag size="small" type="info">{{ editing ? '编辑中' : '未保存' }}</el-tag></div><div><el-button :disabled="!table || busy || saving" @click="settingsVisible=true">设置</el-button><el-button type="primary" :disabled="!canCreate || !selected.length || busy" :loading="saving" @click="settingsVisible=true">{{ editing ? '检查并保存' : '保存数据集' }}</el-button></div></header>
    <el-alert v-if="!canCreate" title="当前账号没有创建表数据集的权限" type="warning" :closable="false"/>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <div v-if="canCreate && (!editing || editRevision)" class="builder-body">
      <aside class="source-sidebar" v-loading="busy">
        <h3>选择数据源</h3><el-select v-model="sourceId" filterable placeholder="请选择数据源" :disabled="saving" @change="changeSource"><el-option v-for="item in sources" :key="item.id" :value="item.id" :label="item.name"/></el-select>
        <el-select v-model="catalog" filterable placeholder="选择数据库" :disabled="!sourceId || saving" @change="changeCatalog"><el-option v-for="item in catalogs" :key="item" :value="item" :label="item"/></el-select>
        <div class="table-list-title"><h3>数据表</h3><small>{{ tables.length }}</small></div><el-input v-model="tableSearch" clearable placeholder="通过表名称搜索" aria-label="搜索数据表"/>
        <p class="sidebar-hint">拖拽或点击 ＋ 添加到画布</p>
        <div class="sql-entry" :draggable="isAdmin && !!sourceId && !saving" @dragstart="startDrag($event,'__custom_sql__')"><span>SQL</span><el-button link :disabled="!isAdmin || !sourceId || saving" @click="openSql">自定义 SQL</el-button><small>{{ isAdmin ? '＋' : '管理员' }}</small></div>
        <div class="table-list"><div v-for="item in visibleTables" :key="item.name" class="table-row" :class="{active:item.name===table}" :draggable="!saving && !busy" @dragstart="startDrag($event,item.name)"><span class="table-icon">▤</span><div :title="item.comment || item.name"><strong>{{ item.name }}</strong><small v-if="item.comment">{{ item.comment }}</small></div><el-button link :disabled="saving || busy" :aria-label="`添加表 ${item.name}`" @click="addTable(item.name)">＋</el-button></div><el-empty v-if="!visibleTables.length" :description="sourceId ? '暂无匹配数据表' : '请先选择数据源'" :image-size="60"/></div>
      </aside>
      <article class="builder-content">
        <DatasetRelationCanvas :table="table" :catalog="catalog" :field-count="selected.length" :joins="joins" :sql-node="isSqlMode" @edit-sql="openSql" :disabled="saving || busy" @add="addTable" @clear="clearCanvas" @edit="editJoin" @remove="removeJoin"/>
        <section class="preview-section">
          <div class="preview-heading"><el-tabs v-model="activeTab"><el-tab-pane label="数据预览" name="preview"/><el-tab-pane :label="`批量管理 · ${selected.length} 个主表字段`" name="fields"/></el-tabs><div><el-button :disabled="!selected.length || saving || busy || totalFields>=200" @click="openDerived('calc')">新建计算字段</el-button><el-button :disabled="!selected.length || saving || busy || totalFields>=200" @click="openDerived('group')">新建分组字段</el-button><el-button v-if="table" :disabled="saving || busy" @click="activeTab='fields'">配置字段</el-button><el-button type="primary" plain :loading="previewLoading" :disabled="!selected.length || busy || saving" @click="runPreview">{{ isSqlMode ? '刷新 SQL 结果' : (joins.length || derivedFields.length) && isAdmin ? '刷新完整结果' : '刷新主表数据' }}</el-button></div></div>
          <template v-if="activeTab==='preview'"><div class="preview-summary"><span>{{ isSqlMode || ((joins.length || derivedFields.length) && isAdmin) ? '当前节点及已配置关联的完整预览，最多 100 行。' : (joins.length || derivedFields.length) ? '此处预览主表样本；完整关联和派生字段结果保存后查询。' : '预览最多 100 行样本，不代表全量记录数。' }}</span><span v-if="preview">{{ preview.rows.length }} 行 · {{ preview.costMs }} ms</span></div><el-alert v-if="previewError" :title="previewError" type="error" :closable="false"/><el-table v-if="preview" :data="preview.rows" border max-height="420"><el-table-column v-for="column in preview.columns" :key="column" :prop="column" :label="fields.find(f=>f.name===column)?.showName || derivedFields.find(f=>f.originName===column)?.showName || column" min-width="160" show-overflow-tooltip/></el-table><el-empty v-else :description="table ? '点击刷新主表数据，查看实际样本' : '添加数据表后查看数据'" :image-size="80"/></template>
          <template v-else><div class="field-toolbar"><el-button link :disabled="saving" @click="selectFields(true)">选择前 200 个字段</el-button><el-button link :disabled="saving" @click="selectFields(false)">清空</el-button><el-input v-model="search" clearable placeholder="搜索字段" aria-label="搜索字段" style="max-width:240px"/><span>主表与关联字段合计最多 200 个</span></div><el-table :data="visibleFields" max-height="420" row-key="name"><el-table-column label="选用" width="65"><template #default="{row}"><el-checkbox :model-value="row.selected" :disabled="saving || (!row.selected && totalFields>=200)" :aria-label="`选择 ${row.name}`" @change="row.selected=Boolean($event)"/></template></el-table-column><el-table-column prop="name" label="物理字段" min-width="150"/><el-table-column prop="type" label="字段类型" width="120"/><el-table-column label="显示名称" min-width="180"><template #default="{row}"><el-input v-model="row.showName" :disabled="saving || !row.selected" maxlength="200"/></template></el-table-column><el-table-column label="维度 / 指标" width="140"><template #default="{row}"><el-select v-model="row.dimFlag" :disabled="saving || !row.selected"><el-option label="维度" value="1"/><el-option label="指标" value="0"/></el-select></template></el-table-column></el-table><template v-if="derivedFields.length"><h4>派生字段（按顺序执行）</h4><el-table :data="derivedFields"><el-table-column prop="originName" label="字段标识"/><el-table-column prop="showName" label="显示名称"/><el-table-column label="类型"><template #default="{row}">{{ row.kind==='group'?'分组字段':'计算字段' }}</template></el-table-column><el-table-column label="定义" min-width="200" show-overflow-tooltip><template #default="{row}">{{ row.kind==='group' ? `${row.sourceField} · ${row.groups?.length || 0} 个分组` : row.formula }}</template></el-table-column><el-table-column label="操作" width="120"><template #default="{row,$index}"><el-button link :disabled="saving" @click="openDerived(row.kind,$index)">编辑</el-button><el-button link type="danger" :disabled="saving" @click="removeDerived($index)">删除</el-button></template></el-table-column></el-table></template></template>
        </section>
      </article>
    </div>
    <DerivedFieldDialog v-model:visible="derivedVisible" :kind="derivedKind" :fields="expressionFields" :used-names="reservedFieldNames" :initial="derivedIndex>=0 ? derivedFields[derivedIndex] : undefined" @apply="applyDerived"/>
    <el-dialog v-model="sqlVisible" title="自定义 SQL" width="min(920px, calc(100vw - 32px))" :close-on-click-modal="false" @closed="++sqlGeneration;sqlLoading=false"><div class="sql-source-bar"><span>数据源：<strong>{{ source?.name }}</strong></span><div><el-popover placement="bottom" :width="540" trigger="click"><template #reference><el-button link>变量语法</el-button></template><p>1、SQL 变量只能在 WHERE 条件中使用</p><p>2、示例：</p><pre style="white-space:pre-wrap;overflow-wrap:anywhere">{{ parameterExample }}</pre></el-popover><el-button link type="primary" :disabled="sqlLoading" @click="editParameters">SQL 参数（{{ parameterCount }}）</el-button></div></div><SqlEditor v-if="sqlVisible" v-model="sqlDraft" :schema="sqlSchema" :default-schema="source?.schemaName" :disabled="sqlLoading" @execute="applySql"/><div class="sql-editor-hint"><span>输入关键字或表名自动提示，表名后的“.”可提示字段</span><span>Ctrl + Space 提示 · Ctrl + Enter 执行</span></div><el-alert v-if="sqlError" :title="sqlError" type="error" :closable="false"/><template #footer><el-button :disabled="sqlLoading" @click="sqlVisible=false">取消</el-button><el-button type="primary" :loading="sqlLoading" :disabled="!sqlDraft.trim()" @click="applySql">执行并解析字段</el-button></template></el-dialog>
    <SqlParameterDialog v-model:visible="parameterVisible" :definitions="draftParameters" :values="draftParameterValues" @apply="applyParameters"/>
    <el-dialog v-model="settingsVisible" :title="editing ? '保存修改' : '保存数据集'" width="480px" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving"><el-form label-position="top" :disabled="saving"><el-form-item label="名称" required><el-input v-model="name" maxlength="200" placeholder="请输入数据集名称"/></el-form-item><el-form-item label="保存目录"><el-tree-select v-model="groupId" :data="groups" node-key="id" :props="{label:'name',children:'children'}" check-strictly clearable placeholder="根目录"/></el-form-item><el-form-item v-if="isAdmin && !isSqlMode" label="数仓消费模型"><el-select v-model="warehouseModelId" clearable filterable placeholder="可选：绑定数仓门禁"><el-option v-for="model in matchingModels" :key="model.id" :value="model.id" :label="`${model.name} · ${model.modelCode}`"/></el-select></el-form-item><el-form-item label="说明"><el-input v-model="remark" type="textarea" maxlength="500"/></el-form-item></el-form><p class="muted">{{ joins.length+1 }} 张表 · {{ joins.length }} 条关联 · {{ totalFields }} 个字段。{{ editing ? '保存后更新原数据集，保留现有引用。' : '保存前不会写入数据集。' }}</p><template #footer><el-button :disabled="saving" @click="settingsVisible=false">继续编辑</el-button><el-button type="primary" :loading="saving" @click="save">{{ editing ? '检查影响并保存' : '保存并打开' }}</el-button></template></el-dialog>
    <el-dialog v-model="impactVisible" title="数据集变更影响检查" width="min(760px, calc(100vw - 32px))" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving"><template v-if="editImpact"><el-alert v-for="(item,index) in editImpact.blockers" :key="`b${index}`" :title="item" type="error" :closable="false" style="margin-bottom:10px"/><el-alert v-for="(item,index) in editImpact.warnings" :key="`w${index}`" :title="item" type="warning" :closable="false" style="margin-bottom:10px"/><p class="muted">保持原数据集编号和未变化字段编号。删除或修改已引用字段类型时，须先处理下游依赖。</p><el-table v-if="editImpact.references.length" :data="editImpact.references" max-height="300"><el-table-column prop="type" label="资源类型" width="110"/><el-table-column prop="name" label="引用资源"/><el-table-column label="受影响字段"><template #default="{row}">{{ row.fields.join('、') || '字段兼容；请核对统计口径' }}</template></el-table-column></el-table></template><template #footer><el-button :disabled="saving" @click="impactVisible=false">返回修改</el-button><el-button v-if="editImpact && !editImpact.blockers.length" type="primary" :loading="saving" @click="confirmEdit">确认影响并保存</el-button></template></el-dialog>
    <el-dialog v-model="joinVisible" title="关联设置" width="580px"><el-alert title="按画布顺序依次关联，左键来自主表或前面关联表的已选字段。每张关联表最多选 50 个字段。" :closable="false" type="info"/><el-form v-if="editingJoin" label-position="top" class="join-form"><el-form-item label="关联表"><el-input :model-value="editingJoin.rightTable" disabled/></el-form-item><el-form-item label="关联方式"><el-radio-group v-model="editingJoin.joinType"><el-radio-button value="LEFT">左连接</el-radio-button><el-radio-button value="INNER">内连接</el-radio-button><el-radio-button value="RIGHT">右连接</el-radio-button></el-radio-group></el-form-item><div class="join-keys"><el-form-item label="左侧字段" required><el-select v-model="editingJoin.leftKey" filterable><el-option v-for="key in leftKeys" :key="key" :value="key" :label="key"/></el-select></el-form-item><span>=</span><el-form-item label="右表字段" required><el-select v-model="editingJoin.rightKey" filterable :loading="joinLoading"><el-option v-for="field in rightColumns" :key="field.name" :value="field.name" :label="field.name"/></el-select></el-form-item></div><el-form-item label="选用右表字段" required><el-select v-model="editingJoin.rightFields" multiple filterable :multiple-limit="50" :loading="joinLoading"><el-option v-for="field in rightColumns" :key="field.name" :value="field.name" :label="field.name"/></el-select></el-form-item></el-form><template #footer><el-button @click="joinVisible=false">关闭</el-button><el-button type="primary" :disabled="joinLoading" @click="confirmJoin">确定</el-button></template></el-dialog>
  </main>
</template>

<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {onBeforeRouteLeave,useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {checkPermi} from '@/utils/permission'
import {listModelSources,getGroupTree,listCatalogs,listTables,listColumns,previewTable,previewSqlNode,previewWorkspaceDataset,createWorkspaceDataset,getDatasetWorkspace,checkDatasetWorkspace,updateDatasetWorkspace,listWarehouseTableModels,type ModelDatasource,type DatasetGroupNode,type CatalogTable,type CatalogColumn,type PreviewResult,type WarehouseTableModelOption,type DatasetJoinStep,type DatasetDerivedField,type SqlParameterDefinition,type DatasetEditDraft,type DatasetEditImpact} from '@/api/bi/dataset'
import SqlParameterDialog from './SqlParameterDialog.vue'
import {discoverSqlParameters,syncSqlParameters} from '@/utils/biSqlParameters'
import SqlEditor from './SqlEditor.vue'
import DerivedFieldDialog from './DerivedFieldDialog.vue'
import DatasetRelationCanvas from './DatasetRelationCanvas.vue'
const route=useRoute(),router=useRouter()
const editing=computed(()=>Number(route.params?.id)>0),editId=computed(()=>Number(route.params?.id))
const editRevision=ref(''),initialState=ref(''),impactVisible=ref(false),editImpact=ref<DatasetEditImpact|null>(null),pendingDraft=ref<DatasetEditDraft|null>(null)
const title=computed(()=>editing.value?'编辑数据集':'新建数据集')
const canCreate=computed(()=>(editing.value || checkPermi(['bi:dataset:add'])) && checkPermi(['bi:datasource:model'])),isAdmin=computed(()=>checkPermi(['bi:resource:admin']))
const name=ref(''),remark=ref(''),groupId=ref<number>(),sourceId=ref<number>(),catalog=ref(''),table=ref(''),warehouseModelId=ref<number>(),search=ref('')
const sources=ref<ModelDatasource[]>([]),groups=ref<DatasetGroupNode[]>([]),catalogs=ref<string[]>([]),tables=ref<CatalogTable[]>([]),models=ref<WarehouseTableModelOption[]>([])
type Field=CatalogColumn & {selected:boolean;showName:string;dimFlag:'0'|'1'}
const fields=ref<Field[]>([]),busy=ref(false),saving=ref(false),error=ref(''),previewLoading=ref(false),previewError=ref(''),preview=ref<PreviewResult|null>(null)
let generation=0,queryGeneration=0,joinGeneration=0,created=false
const sqlVisible=ref(false),sqlDraft=ref(''),sqlText=ref(''),sqlLoading=ref(false),sqlError=ref('')
const isSqlMode=computed(()=>!!sqlText.value)
const parameterVisible=ref(false),sqlParameters=ref<SqlParameterDefinition[]>([]),draftParameters=ref<SqlParameterDefinition[]>([]),sqlParameterValues=ref<Record<string,string[]>>({}),draftParameterValues=ref<Record<string,string[]>>({})
const parameterCount=computed(()=>{try{return discoverSqlParameters(sqlDraft.value).length}catch{return 0}})
const parameterExample="select * from table where $DE_PARAM{ name = substring('$[PARAM1]',1,5)} and $DE_PARAM{ name in ($[PARAM2]) }"
let sqlGeneration=0,hintGeneration=0,hintTimer:ReturnType<typeof setTimeout>|undefined
const sqlHintColumns=ref<Record<string,string[]>>({})
const sqlSchema=computed(()=>source.value ? {[source.value.schemaName]:Object.fromEntries(tables.value.map(t=>[t.name,sqlHintColumns.value[t.name] || []]))} : {})
const derivedFields=ref<DatasetDerivedField[]>([]),derivedVisible=ref(false),derivedKind=ref<'calc'|'group'>('calc'),derivedIndex=ref(-1)
const baseAvailableFields=computed(()=>[...selected.value.map(f=>({name:f.name,label:f.showName})),...joins.value.flatMap((j,i)=>j.rightFields.map(f=>({name:`${i===0?'r':`r${i+1}`}_${f}`,label:f})))])
const reservedFieldNames=computed(()=>[...baseAvailableFields.value.map(f=>f.name),...derivedFields.value.filter((_,index)=>index!==derivedIndex.value).map(f=>f.originName)])
const expressionFields=computed(()=>[...baseAvailableFields.value,...derivedFields.value.slice(0,derivedIndex.value<0?undefined:derivedIndex.value).map(f=>({name:f.originName,label:f.showName || f.originName}))])
const tableSearch=ref(''),activeTab=ref('preview'),settingsVisible=ref(false),joinVisible=ref(false),joinLoading=ref(false),joinIndex=ref(-1)
const joins=ref<DatasetJoinStep[]>([]),rightColumns=ref<CatalogColumn[]>([]),editingJoin=ref<DatasetJoinStep|null>(null)
const visibleTables=computed(()=>tables.value.filter(t=>`${t.name} ${t.comment || ''}`.toLowerCase().includes(tableSearch.value.trim().toLowerCase())))
const totalFields=computed(()=>selected.value.length+joins.value.reduce((n,j)=>n+j.rightFields.length,0)+derivedFields.value.length)
const leftKeys=computed(()=>[...selected.value.map(f=>f.name),...joins.value.slice(0,joinIndex.value).flatMap((j,i)=>j.rightFields.map(f=>`${i===0?'r':`r${i+1}`}_${f}`))])
const selected=computed(()=>fields.value.filter(f=>f.selected)),source=computed(()=>sources.value.find(s=>s.id===sourceId.value))
const visibleFields=computed(()=>fields.value.filter(f=>`${f.name} ${f.comment || ''} ${f.showName}`.toLowerCase().includes(search.value.trim().toLowerCase())))
const matchingModels=computed(()=>models.value.filter(m=>m.status==='ACTIVE' && m.tableName.toLowerCase()===table.value.toLowerCase()))
const fingerprint=()=>JSON.stringify([sourceId.value,catalog.value,table.value,sqlText.value,sqlParameters.value,sqlParameterValues.value,joins.value,derivedFields.value,selected.value.map(f=>f.name)])
watch(fingerprint,()=>{++queryGeneration;preview.value=null;previewError.value='';previewLoading.value=false})
function selectFields(value:boolean){fields.value.forEach((f,index)=>f.selected=value && index<200)}
async function changeSource(){
  const version=++generation;catalog.value='';table.value='';sqlParameters.value=[];draftParameters.value=[];sqlParameterValues.value={};draftParameterValues.value={};parameterVisible.value=false;derivedFields.value=[];sqlHintColumns.value={};++hintGeneration;sqlText.value='';sqlVisible.value=false;sqlLoading.value=false;++sqlGeneration;joins.value=[];joinVisible.value=false;++joinGeneration;warehouseModelId.value=undefined;catalogs.value=[];tables.value=[];fields.value=[];error.value=''
  if(!source.value)return
  busy.value=true
  try{const result=await listCatalogs(source.value.schemaName);if(version!==generation)return;catalogs.value=result.data || [];if(catalogs.value.length===1){catalog.value=catalogs.value[0];await changeCatalog()}}catch{if(version===generation)error.value='数据库列表读取失败，请重新选择数据源重试。'}finally{if(version===generation)busy.value=false}
}
async function changeCatalog(){
  const version=++generation;table.value='';sqlParameters.value=[];draftParameters.value=[];sqlParameterValues.value={};draftParameterValues.value={};parameterVisible.value=false;derivedFields.value=[];sqlHintColumns.value={};++hintGeneration;sqlText.value='';sqlVisible.value=false;sqlLoading.value=false;++sqlGeneration;joins.value=[];joinVisible.value=false;++joinGeneration;warehouseModelId.value=undefined;tables.value=[];fields.value=[];error.value='';if(!source.value || !catalog.value)return
  busy.value=true
  try{const result=await listTables(source.value.schemaName,catalog.value);if(version===generation)tables.value=result.data || []}catch{if(version===generation)error.value='表列表读取失败，请重新选择数据库重试。'}finally{if(version===generation)busy.value=false}
}
async function changeTable(){
  const version=++generation;fields.value=[];warehouseModelId.value=undefined;error.value='';if(!source.value || !table.value)return
  busy.value=true
  try{const result=await listColumns(source.value.schemaName,table.value,catalog.value);if(version===generation)fields.value=(result.data || []).map((f,index)=>({...f,selected:index<200,showName:f.comment?.trim() || f.name,dimFlag:f.suggestAs==='metric' ? '0' : '1'}))}catch{if(version===generation)error.value='字段读取失败，请重新选择来源表重试。'}finally{if(version===generation)busy.value=false}
}
async function runPreview(){
  if(!source.value || !selected.value.length)return
  const version=++queryGeneration,snapshot=fingerprint();previewLoading.value=true;previewError.value='';preview.value=null
  try{const result=isSqlMode.value ? await previewSqlNode({datasourceId:sourceId.value!,sourceCatalog:catalog.value,sql:sqlText.value,fields:selected.value.map(f=>f.name),model:{joins:joins.value,derivedFields:derivedFields.value,sqlParameters:sqlParameters.value},parameterValues:sqlParameterValues.value,maxRows:100}) : (joins.value.length || derivedFields.value.length) && isAdmin.value ? await previewWorkspaceDataset({datasourceId:sourceId.value!,sourceCatalog:catalog.value,sourceTable:table.value,fields:selected.value.map(f=>f.name)},{joins:joins.value,derivedFields:derivedFields.value}) : await previewTable(source.value.schemaName,{catalog:catalog.value,table:table.value,fields:selected.value.map(f=>f.name),maxRows:100});if(version!==queryGeneration || snapshot!==fingerprint())return;if(!result.data?.ok)throw Error(result.data?.message || '数据预览失败');preview.value=result.data;activeTab.value='preview'}catch(e){if(version===queryGeneration)previewError.value=e instanceof Error ? e.message : '数据预览失败'}finally{if(version===queryGeneration)previewLoading.value=false}
}
async function save(){
  if(saving.value || busy.value || !canCreate.value)return
  if(!name.value.trim() || !sourceId.value || (!catalog.value && !isSqlMode.value) || !table.value || !selected.value.length){ElMessage.warning('请填写名称、来源并选择字段');return}
  if(totalFields.value>200){ElMessage.warning('主表和关联表字段合计不能超过 200 个');return}
  for(let i=0;i<joins.value.length;i++){const j=joins.value[i];const available=[...selected.value.map(f=>f.name),...joins.value.slice(0,i).flatMap((p,k)=>p.rightFields.map(f=>`${k===0?'r':`r${k+1}`}_${f}`))];if(!j.rightKey || !j.rightFields.length || !available.includes(j.leftKey)){ElMessage.warning(`请完成第 ${i+1} 条关联设置`);return}}
  saving.value=true
  try{if(editing.value){await prepareEdit();return}const result=await createWorkspaceDataset({name:name.value.trim(),remark:remark.value,groupId:groupId.value,datasourceId:sourceId.value,sourceCatalog:catalog.value,sourceTable:table.value,warehouseModelId:warehouseModelId.value,fields:selected.value.map(f=>({name:f.name,showName:f.showName.trim() || f.name,dimFlag:f.dimFlag}))},{joins:joins.value,...(derivedFields.value.length ? {derivedFields:derivedFields.value} : {}),...(isSqlMode.value ? {sourceSql:sqlText.value,...(sqlParameters.value.length ? {sqlParameters:sqlParameters.value} : {})} : {})},sqlParameterValues.value);created=true;ElMessage.success('数据集已创建');const id=Number((result.data as {id?:number})?.id);await router.replace(id>0 ? `/bi-admin/dataset-workspace/${id}/view` : '/bi-admin/dataset')}catch(e){error.value=e instanceof Error?e.message:'保存失败，请重试'}finally{saving.value=false}
}
function editDraft():DatasetEditDraft{return {dataset:{name:name.value.trim(),remark:remark.value,groupId:groupId.value,datasourceId:sourceId.value!,sourceCatalog:catalog.value,sourceTable:table.value,warehouseModelId:warehouseModelId.value},fields:selected.value.map(f=>({name:f.name,showName:f.showName,dimFlag:f.dimFlag})),model:{joins:JSON.parse(JSON.stringify(joins.value)),derivedFields:JSON.parse(JSON.stringify(derivedFields.value)),...(isSqlMode.value?{sourceSql:sqlText.value,sqlParameters:JSON.parse(JSON.stringify(sqlParameters.value))}:{})},parameterValues:JSON.parse(JSON.stringify(sqlParameterValues.value)),revision:editRevision.value}}
async function prepareEdit(){
  if(!editRevision.value)throw Error('编辑定义尚未加载，不能保存')
  const draft=editDraft();const report=(await checkDatasetWorkspace(editId.value,draft)).data
  editImpact.value=report;pendingDraft.value={...draft,impactToken:report.token};settingsVisible.value=false;impactVisible.value=true
}
async function confirmEdit(){
  if(!pendingDraft.value || !editImpact.value || editImpact.value.blockers.length || saving.value)return
  saving.value=true;error.value=''
  try{await updateDatasetWorkspace(editId.value,pendingDraft.value);created=true;impactVisible.value=false;ElMessage.success('数据集已更新');await router.replace(`/bi-admin/dataset-workspace/${editId.value}/view`)}catch(e){error.value=e instanceof Error?e.message:'保存失败，请重新检查影响';impactVisible.value=false}finally{saving.value=false}
}
const editState=()=>JSON.stringify([name.value,remark.value,groupId.value,sourceId.value,catalog.value,table.value,warehouseModelId.value,sqlText.value,sqlParameters.value,joins.value,derivedFields.value,fields.value.map(f=>[f.name,f.selected,f.showName,f.dimFlag])])
async function loadEditing(){
  const definition=(await getDatasetWorkspace(editId.value)).data
  const row=definition.dataset;sourceId.value=row.datasourceId
  if(!sources.value.some(s=>s.id===row.datasourceId))throw Error('原数据源不可编辑，请先恢复数据源授权或连接')
  await changeSource();catalog.value=row.sourceCatalog || '';await changeCatalog()
  if(error.value)throw Error(error.value)
  const model=definition.model
  name.value=row.name;remark.value=row.remark || '';groupId.value=row.groupId || undefined;warehouseModelId.value=row.warehouseModelId || undefined
  sqlText.value=model.sourceSql || '';table.value=sqlText.value?'自定义 SQL':row.sourceTable
  const saved=new Map(definition.baseFields.map(f=>[f.originName,f]))
  let available:CatalogColumn[]
  if(sqlText.value)available=definition.baseFields.map(f=>({name:f.originName,type:f.originType || 'VARCHAR',suggestAs:f.dimFlag==='0'?'metric':'dimension',comment:'',nullable:true,size:0,primaryKey:false}))
  else available=(await listColumns(source.value!.schemaName,table.value,catalog.value)).data
  const found=new Set(available.map(f=>f.name));if([...saved.keys()].some(key=>!found.has(key)))throw Error('已选源字段发生变化，请先处理结构变更后再编辑')
  fields.value=available.map(f=>({...f,selected:saved.has(f.name),showName:saved.get(f.name)?.showName || f.comment?.trim() || f.name,dimFlag:(saved.get(f.name)?.dimFlag || (f.suggestAs==='metric'?'0':'1')) as '0'|'1'}))
  joins.value=JSON.parse(JSON.stringify(model.joins || []));derivedFields.value=JSON.parse(JSON.stringify(model.derivedFields || []));sqlParameters.value=JSON.parse(JSON.stringify(model.sqlParameters || []));editRevision.value=definition.revision
  await nextTick();initialState.value=editState();activeTab.value='fields'
}




function openDerived(kind:'calc'|'group',index=-1){if(saving.value || !selected.value.length)return;derivedKind.value=kind;derivedIndex.value=index;derivedVisible.value=true}
function applyDerived(value:DatasetDerivedField){if(derivedIndex.value<0){if(totalFields.value>=200){ElMessage.warning('字段总数不能超过 200');return}derivedFields.value.push(value)}else derivedFields.value[derivedIndex.value]=value;activeTab.value='fields'}
async function removeDerived(index:number){try{await ElMessageBox.confirm('移除此字段及其后的派生字段，避免依赖引用失效。是否继续？','移除派生字段',{type:'warning'});derivedFields.value.splice(index)}catch{}}
watch(sqlDraft,()=>{
  if(hintTimer)clearTimeout(hintTimer)
  hintTimer=setTimeout(async()=>{
    if(!sqlVisible.value || !source.value)return
    const version=++hintGeneration,schema=source.value.schemaName,scope=catalog.value
    const mentioned=[...sqlDraft.value.replace(/["`]/g,'').matchAll(/\b(?:FROM|JOIN)\s+(?:[\w]+\.)?([\w\u4e00-\u9fff]+)/gi)].map(m=>m[1].toLowerCase())
    const needed=tables.value.filter(t=>mentioned.includes(t.name.toLowerCase()) && !sqlHintColumns.value[t.name]).slice(0,8)
    await Promise.allSettled(needed.map(async t=>{const result=await listColumns(schema,t.name,scope);if(version===hintGeneration && sqlVisible.value)sqlHintColumns.value={...sqlHintColumns.value,[t.name]:(result.data || []).map(c=>c.name)}}))
  },300)
})

function editParameters(){try{draftParameters.value=syncSqlParameters(sqlDraft.value,draftParameters.value);parameterVisible.value=true}catch(error){sqlError.value=error instanceof Error?error.message:'参数解析失败'}}
function applyParameters(definitions:SqlParameterDefinition[],values:Record<string,string[]>){draftParameters.value=definitions;draftParameterValues.value=values}
async function openSql(){
  if(!isAdmin.value || !source.value || saving.value || busy.value)return
  if(table.value && !isSqlMode.value){try{await ElMessageBox.confirm('自定义 SQL 将作为主节点，替换当前主表及关联配置。是否继续？','添加自定义 SQL',{type:'warning'})}catch{return}}
  draftParameters.value=structuredClone(sqlParameters.value.map(p=>({...p,defaultValues:[...p.defaultValues]})));draftParameterValues.value=JSON.parse(JSON.stringify(sqlParameterValues.value))
  sqlDraft.value=sqlText.value || `SELECT * FROM "${source.value.schemaName.replaceAll('"','""')}"."表名"`;
  sqlError.value='';sqlVisible.value=true
}
async function applySql(){
  if(!sourceId.value || !sqlDraft.value.trim() || sqlLoading.value)return
  const version=++sqlGeneration,sourceSnapshot=sourceId.value,text=sqlDraft.value;sqlLoading.value=true;sqlError.value=''
  try{
    draftParameters.value=syncSqlParameters(text,draftParameters.value)
    const names=new Set(draftParameters.value.map(p=>p.name));const values=Object.fromEntries(Object.entries(draftParameterValues.value).filter(([name])=>names.has(name)))
    const result=await previewSqlNode({datasourceId:sourceSnapshot,sourceCatalog:catalog.value,sql:text,model:{sqlParameters:draftParameters.value},parameterValues:values,maxRows:100})
    if(version!==sqlGeneration || sourceSnapshot!==sourceId.value || text!==sqlDraft.value || !sqlVisible.value)return
    if(!result.data?.ok || !result.data.fieldMetadata?.length)throw Error(result.data?.message || 'SQL 没有可用字段')
    const old=new Map(fields.value.map(f=>[f.name,f]));if(!isSqlMode.value){joins.value=[];derivedFields.value=[]}
    sqlText.value=result.data.normalizedSql;sqlParameters.value=JSON.parse(JSON.stringify(draftParameters.value));sqlParameterValues.value=values;table.value='自定义 SQL';warehouseModelId.value=undefined
    fields.value=result.data.fieldMetadata.map((f,index)=>({...f,selected:old.get(f.name)?.selected ?? index<200,showName:old.get(f.name)?.showName || f.name,dimFlag:old.get(f.name)?.dimFlag || (f.suggestAs==='metric' ? '0' : '1')}))
    await nextTick();if(version!==sqlGeneration || sourceSnapshot!==sourceId.value)return
    preview.value=joins.value.length || derivedFields.value.length ? null : result.data;sqlVisible.value=false;activeTab.value='fields'
  }catch(e){if(version===sqlGeneration)sqlError.value=e instanceof Error ? e.message : 'SQL 解析失败'}finally{if(version===sqlGeneration)sqlLoading.value=false}
}

async function clearCanvas(){
  if(saving.value || busy.value)return
  try{await ElMessageBox.confirm('移除主表将同时清空全部字段与关联配置。是否继续？','清空画布',{type:'warning'});++generation;++joinGeneration;table.value='';sqlParameters.value=[];draftParameters.value=[];sqlParameterValues.value={};draftParameterValues.value={};parameterVisible.value=false;derivedFields.value=[];sqlHintColumns.value={};++hintGeneration;sqlText.value='';sqlVisible.value=false;sqlLoading.value=false;++sqlGeneration;fields.value=[];joins.value=[];warehouseModelId.value=undefined;joinVisible.value=false}catch{}
}
function startDrag(event:DragEvent,value:string){if(saving.value || busy.value){event.preventDefault();return}event.dataTransfer?.setData('application/wanfo-table',value);if(event.dataTransfer)event.dataTransfer.effectAllowed='copy'}
async function addTable(value:string){
  if(value==='__custom_sql__'){await openSql();return}
  if(saving.value || busy.value || !tables.value.some(t=>t.name===value))return
  if(!table.value){table.value=value;await changeTable();return}
  if(joins.value.length>=8){ElMessage.warning('最多支持 8 条关联');return}
  joins.value.push({joinType:'LEFT',rightTable:value,leftKey:'',rightKey:'',rightFields:[]});await editJoin(joins.value.length-1)
}
async function editJoin(index:number){
  if(saving.value || !source.value || !joins.value[index])return
  const version=++joinGeneration;joinIndex.value=index;editingJoin.value={...joins.value[index],rightFields:[...joins.value[index].rightFields]};rightColumns.value=[];joinVisible.value=true;joinLoading.value=true
  try{const result=await listColumns(source.value.schemaName,joins.value[index].rightTable,catalog.value);if(version===joinGeneration)rightColumns.value=result.data || []}catch{if(version===joinGeneration)ElMessage.warning('关联表字段读取失败，请关闭后重试')}finally{if(version===joinGeneration)joinLoading.value=false}
}
function confirmJoin(){
  const join=editingJoin.value
  if(!join || !leftKeys.value.includes(join.leftKey) || !rightColumns.value.some(c=>c.name===join.rightKey) || !join.rightFields.length || join.rightFields.some(f=>!rightColumns.value.some(c=>c.name===f))){ElMessage.warning('请选择有效的关联键和右表字段');return}
  joins.value[joinIndex.value]={...join,rightFields:[...join.rightFields]};joinVisible.value=false
}
async function removeJoin(index:number){
  if(saving.value)return
  try{await ElMessageBox.confirm('移除该关联及其后的关联，避免下游字段引用失效。是否继续？','移除关联',{type:'warning'});joins.value.splice(index);joinVisible.value=false;++joinGeneration}catch{}
}

onBeforeRouteLeave(async()=>{if(created || editing.value && initialState.value===editState())return true;if(saving.value)return false;if(!name.value && !table.value)return true;try{await ElMessageBox.confirm('离开后，本页尚未保存的修改将丢失。是否离开？','离开数据集编辑',{type:'warning',confirmButtonText:'离开',cancelButtonText:'继续配置'});return true}catch{return false}})
onBeforeUnmount(()=>{++generation;++queryGeneration;++joinGeneration;++sqlGeneration;++hintGeneration;if(hintTimer)clearTimeout(hintTimer)})
onMounted(async()=>{if(!canCreate.value)return;busy.value=true;const version=++generation;try{const [s,g]=await Promise.all([listModelSources(),getGroupTree()]);if(version!==generation)return;sources.value=s.data || [];groups.value=g.data || [];const id=Number(route.query.groupId);if(Number.isSafeInteger(id) && id>0)groupId.value=id;if(isAdmin.value){try{models.value=(await listWarehouseTableModels()).data || []}catch{ElMessage.warning('数仓模型列表加载失败，可创建普通数据集或刷新重试')}}if(version!==generation)return;if(editing.value){await loadEditing();return}const initial=Number(route.query.datasourceId);if(sources.value.some(s=>s.id===initial)){sourceId.value=initial;await changeSource()}if(route.query.mode==='sql' && source.value)await openSql()}catch(e){error.value=e instanceof Error?e.message:'初始化失败，请刷新页面重试。'}finally{busy.value=false}})
</script>

<style scoped>
.dataset-builder{background:var(--el-bg-color);min-height:calc(100vh - 68px);color:var(--el-text-color-primary)}.builder-header{height:64px;display:flex;align-items:center;justify-content:space-between;padding:0 24px;border-bottom:1px solid var(--el-border-color-light)}.header-title{display:flex;align-items:center;gap:14px}.header-title strong{font-size:16px}.builder-body{display:grid;grid-template-columns:250px minmax(0,1fr);min-height:calc(100vh - 132px)}.source-sidebar{padding:20px 16px;border-right:1px solid var(--el-border-color-light);min-width:0}.source-sidebar h3{font-size:13px;font-weight:600;margin:0 0 12px}.source-sidebar>.el-select{margin-bottom:12px}.el-select,.el-tree-select{width:100%}.table-list-title{display:flex;justify-content:space-between;margin-top:14px}.table-list-title small,.sidebar-hint{font-size:12px;color:var(--el-text-color-secondary)}.sidebar-hint{margin:10px 0 8px}.table-list{max-height:calc(100vh - 390px);min-height:220px;overflow:auto}.table-row{display:flex;gap:10px;align-items:center;padding:10px 8px;border-radius:5px;cursor:grab}.table-row:hover,.table-row.active{background:var(--el-color-primary-light-9)}.table-icon{color:var(--el-color-primary);font-size:19px}.table-row>div{flex:1;min-width:0}.table-row strong{font-size:12px;font-weight:400;display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.table-row small{display:block;font-size:11px;color:var(--el-text-color-secondary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;margin-top:4px}.builder-content{min-width:0}.preview-section{padding:0 24px 24px}.preview-heading{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}.preview-heading :deep(.el-tabs__header){margin-bottom:0}.preview-heading :deep(.el-tabs__nav-wrap:after){display:none}.preview-summary{display:flex;justify-content:space-between;color:var(--el-text-color-secondary);font-size:12px;padding:14px 0}.field-toolbar{display:flex;align-items:center;gap:12px;margin:14px 0;font-size:12px;color:var(--el-text-color-secondary);flex-wrap:wrap}.muted{font-size:12px;line-height:1.8;color:var(--el-text-color-secondary)}.join-form{margin-top:16px}.join-keys{display:grid;grid-template-columns:1fr 20px 1fr;gap:12px;align-items:center}.join-keys>span{text-align:center}@media(max-width:850px){.builder-body{grid-template-columns:210px minmax(0,1fr)}.builder-header{padding:0 16px}.preview-section{padding:0 16px 16px}}@media(max-width:600px){.builder-body{grid-template-columns:1fr}.table-list{max-height:180px;min-height:0}.source-sidebar{border-right:0;border-bottom:1px solid var(--el-border-color-light)}.preview-summary{gap:12px;flex-wrap:wrap}}
.sql-entry{display:flex;align-items:center;gap:10px;padding:10px 8px;margin:8px 0;border-bottom:1px solid var(--el-border-color-light);cursor:grab}.sql-entry>span{font-size:10px;font-weight:700;color:var(--el-color-primary);padding:4px;border:1px solid var(--el-color-primary-light-7);border-radius:4px}.sql-entry small{margin-left:auto;color:var(--el-text-color-secondary);font-size:11px}.sql-editor :deep(textarea){font-family:Consolas,monospace;line-height:1.7;font-size:13px}
.sql-source-bar,.sql-editor-hint{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:12px;color:var(--el-text-color-secondary);padding:0 0 12px}.sql-editor-hint{font-size:11px;padding:10px 0 0;flex-wrap:wrap}.preview-heading>div{display:flex;gap:6px;flex-wrap:wrap}.preview-heading>div .el-button{margin-left:0}
</style>
