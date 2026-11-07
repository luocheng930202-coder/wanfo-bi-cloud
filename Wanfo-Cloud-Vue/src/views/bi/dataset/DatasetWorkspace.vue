<template>
  <main class="dataset-workspace" v-loading="loading">
    <header class="workspace-header">
      <div><el-button link @click="router.push('/bi-admin/dataset')">← 返回数据集</el-button><h1>{{ detail?.name || '数据集工作区' }}</h1><p>数据准备 / 结构与预览</p></div>
      <div class="actions" v-if="detail">
        <el-button v-if="detail.editable" type="primary" plain @click="router.push(`/bi-admin/dataset-workspace/${detail.id}/edit`)">编辑数据集</el-button>
        <el-button @click="load">刷新定义</el-button>
        <el-button v-if="canModel" @click="modelVisible=true">建模与质量</el-button>
        <el-button v-if="canQuery && detail.type==='table' && checkPermi(['bi:chart:add'])" type="primary" @click="router.push({path:'/bi-admin/chart-workspace/new',query:{datasetId:detail.id}})">创建图表</el-button>
      </div>
    </header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><el-button link @click="load">重新加载</el-button></el-alert>
    <div v-if="detail" class="workspace-body">
      <aside class="field-panel">
        <h3>数据集字段 <small>{{ detail.fields.length }}</small></h3>
        <el-input v-model="search" clearable placeholder="搜索名称或物理字段" aria-label="搜索字段" />
        <section v-for="group in fieldGroups" :key="group.label">
          <h4>{{ group.label }} <small>{{ group.fields.length }}</small></h4>
          <div v-for="field in group.fields" :key="field.id || field.originName" class="field-item"><span class="field-symbol">{{ field.dimFlag==='1' ? '◇' : '#' }}</span><div><strong>{{ field.showName || field.originName }}</strong><small>{{ field.originName }}</small></div><el-tag v-if="field.extFlag==='1'" size="small" type="info">{{ field.groupName==='分组字段' ? '分组' : '计算' }}</el-tag></div>
        </section>
        <el-empty v-if="!filteredFields.length" description="没有匹配字段" :image-size="60" />
      </aside>
      <article class="content-panel">
        <section class="structure"><div class="section-heading"><h3>来源与关联结构</h3><el-tag>{{ detail.type==='table' ? '表数据集' : 'SQL 数据集' }}</el-tag></div>
          <div class="relation-flow"><div class="source-node"><small>主表 · 数据源 #{{ detail.datasourceId }}</small><strong>{{ detail.sourceKind==='sql' ? '自定义 SQL' : detail.sourceTable || detail.name }}</strong><span>{{ detail.sourceCatalog || '自定义 SQL' }}</span></div><template v-for="(join,index) in joins" :key="index"><div class="join-link">{{ join.joinType }} JOIN<span>{{ join.leftKey }} = {{ join.rightKey }}</span></div><div class="source-node"><small>关联表 {{ index+1 }}</small><strong>{{ join.rightTable }}</strong><span>{{ join.rightFields?.length || 0 }} 个关联字段</span></div></template></div>
          <p class="structure-note">{{ detail.warehouseModelId ? `已绑定数仓模型 #${detail.warehouseModelId}` : '未绑定数仓消费模型' }} · 结构来自已保存定义</p>
          <el-alert v-if="modelError" :title="modelError" type="warning" :closable="false" />
        </section>
        <section class="data-panel">
          <el-tabs v-model="tab">
            <el-tab-pane label="数据预览" name="preview">
              <el-collapse v-if="detail.sqlParameters?.length" class="query-parameters"><el-collapse-item title="SQL 查询参数" name="parameters"><p class="parameter-note">未修改时使用默认值；清空可选值将忽略对应条件。多值参数每行填写一个值。</p><el-form label-position="top"><el-form-item v-for="parameter in detail.sqlParameters" :key="parameter.name" :label="`${parameter.label || parameter.name}${parameter.required ? '（必填）' : ''}`"><el-input v-model="parameterInputs[parameter.name]" :type="parameter.multiple?'textarea':'text'" :rows="2" :disabled="previewLoading" :placeholder="parameter.type==='DATE'?'YYYY-MM-DD':parameter.type==='DATETIME'?'YYYY-MM-DD HH:mm:ss':'使用默认值；填写后覆盖'"/></el-form-item></el-form><el-button link @click="parameterInputs={}">恢复默认</el-button></el-collapse-item></el-collapse><div class="preview-toolbar"><span>最多查询 100 行样本，不代表全量记录数</span><el-button :disabled="!canQuery" :loading="previewLoading" type="primary" plain @click="runPreview">{{ preview ? '重新查询' : '查询预览' }}</el-button></div>
              <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" />
              <template v-if="preview"><p class="preview-meta">返回 {{ preview.rows.length }} 行 · 耗时 {{ preview.costMs }} ms</p><el-table :data="preview.rows" border max-height="430"><el-table-column v-for="column in preview.columns" :key="column" :prop="column" :label="previewColumnLabel(detail.fields,column)" min-width="150" show-overflow-tooltip /></el-table></template>
              <el-empty v-else :description="canQuery ? '点击查询预览，查看实际数据' : '当前账号没有查询权限'" :image-size="80" />
            </el-tab-pane>
            <el-tab-pane label="字段结构" name="fields"><el-table :data="filteredFields" max-height="480"><el-table-column prop="showName" label="显示名称" min-width="160"/><el-table-column prop="originName" label="物理字段" min-width="160"/><el-table-column prop="originType" label="类型"/><el-table-column label="角色"><template #default="{row}">{{ row.dimFlag==='1' ? '维度' : '指标' }}</template></el-table-column><el-table-column prop="groupName" label="分组"/></el-table></el-tab-pane>
            <el-tab-pane v-if="detail.unionSql || detail.sqlText" label="查询定义" name="sql"><pre>{{ detail.unionSql || detail.sqlText }}</pre></el-tab-pane>
          </el-tabs>
        </section>
      </article>
    </div>
    <el-dialog v-model="modelVisible" title="编辑提示" width="440px"><p>现有建模中的字段、关联、计算字段及质量规则会在操作保存后立即生效。请先检查关联图表的使用情况。</p><template #footer><el-button @click="modelVisible=false">取消</el-button><el-button type="primary" @click="modelVisible=false; editorVisible=true">进入建模</el-button></template></el-dialog>
    <DatasetModelDialog v-if="detail && canModel" v-model:visible="editorVisible" :dataset-id="detail.id" :name="detail.name" @saved="load" />
  </main>
</template>

<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {getDataset,listDatasets,getDatasetModel,previewDataset,type DatasetDetail,type DatasetJoinStep,type PreviewResult} from '@/api/bi/dataset'
import {checkPermi} from '@/utils/permission'
import {previewColumnLabel} from '@/utils/biFieldLabels'
import {parameterLines} from '@/utils/biSqlParameters'
import DatasetModelDialog from './DatasetModelDialog.vue'
const route=useRoute(),router=useRouter()
const detail=ref<DatasetDetail|null>(null),joins=ref<DatasetJoinStep[]>([]),actions=ref<string[]>([])
const loading=ref(false),error=ref(''),modelError=ref(''),search=ref(''),tab=ref('preview'),modelVisible=ref(false),editorVisible=ref(false)
const preview=ref<PreviewResult|null>(null),previewLoading=ref(false),previewError=ref('')
const parameterInputs=ref<Record<string,string>>({})
let loadVersion=0,previewVersion=0
watch(parameterInputs,()=>{++previewVersion;preview.value=null;previewLoading.value=false;previewError.value=''},{deep:true})
const canQuery=computed(()=>actions.value.includes('query') && checkPermi(['bi:dataset:query']))
const canModel=computed(()=>detail.value?.type==='table' && (detail.value.sourceKind!=='sql' || checkPermi(['bi:resource:admin'])) && checkPermi(['bi:datasource:model']) && checkPermi(['bi:dataset:add']))
const filteredFields=computed(()=>detail.value?.fields.filter(f=>`${f.showName} ${f.originName}`.toLowerCase().includes(search.value.trim().toLowerCase())) || [])
const fieldGroups=computed(()=>[{label:'维度',fields:filteredFields.value.filter(f=>f.dimFlag==='1')},{label:'指标',fields:filteredFields.value.filter(f=>f.dimFlag!=='1')}])
async function load(){
  const version=++loadVersion,id=Number(route.params.id)
  ++previewVersion;previewLoading.value=false;preview.value=null;previewError.value='';detail.value=null;parameterInputs.value={};actions.value=[];joins.value=[];error.value='';modelError.value='';loading.value=true
  try{
    const [result,list]=await Promise.all([getDataset(id),listDatasets()])
    if(version!==loadVersion)return
    detail.value=result.data;actions.value=list.data?.find(d=>d.id===id)?.resourceActions || []
    if(detail.value.modelJson){try{const saved=JSON.parse(detail.value.modelJson);joins.value=saved.joins?.length ? saved.joins : saved.rightTable ? [saved] : []}catch{modelError.value='已保存关联定义无法解析，请进入建模检查。'}}
    if(canModel.value){try{const model=await getDatasetModel(id);if(version===loadVersion){const join=model.data?.join;joins.value=join?.joins?.length ? join.joins : join?.rightTable ? [join as DatasetJoinStep] : []}}catch{if(version===loadVersion)modelError.value='关联结构读取失败，可刷新重试。'}}
  }catch{if(version===loadVersion)error.value='数据集加载失败，请检查访问权限或稍后重试。'}finally{if(version===loadVersion)loading.value=false}
}
async function runPreview(){
  if(!detail.value || !canQuery.value)return
  const version=++previewVersion,id=detail.value.id;previewLoading.value=true;previewError.value='';preview.value=null
  try{const values=detail.value?.sqlParameters?.length ? Object.fromEntries(Object.entries(parameterInputs.value).map(([name,text])=>[name,parameterLines(text)])) : undefined;const result=await previewDataset(id,100,values);if(version!==previewVersion)return;if(!result.data?.ok)throw new Error(result.data?.message || '预览失败');preview.value=result.data}catch(e){if(version===previewVersion)previewError.value=e instanceof Error ? e.message : '数据预览失败'}finally{if(version===previewVersion)previewLoading.value=false}
}
onBeforeUnmount(()=>{++loadVersion;++previewVersion})
watch(()=>route.params.id,()=>{editorVisible.value=false;modelVisible.value=false;void load()},{immediate:true})
</script>

<style scoped>
.dataset-workspace{box-sizing:border-box;width:100%;min-width:0;padding:24px;min-height:calc(100vh - 70px);background:var(--bi-page-bg,#f5f7fa);color:var(--el-text-color-primary)}.workspace-header,.section-heading,.preview-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px}.workspace-header{margin-bottom:24px}.workspace-header h1{margin:12px 0 6px;font-size:25px}.workspace-header p,.structure-note,.preview-meta,.preview-toolbar{color:var(--el-text-color-secondary);font-size:13px}.actions{display:flex;gap:8px;flex-wrap:wrap}.workspace-body{display:grid;grid-template-columns:250px minmax(0,1fr);gap:20px}.field-panel,.structure,.data-panel{background:var(--el-bg-color);border:1px solid var(--el-border-color-light);border-radius:12px;padding:20px}.field-panel h3,.section-heading h3{margin:0 0 16px;font-size:16px}.field-panel h4{font-size:13px;color:var(--el-text-color-secondary);margin:22px 0 10px}small{font-weight:400;color:var(--el-text-color-secondary)}.field-item{display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid var(--el-border-color-extra-light)}.field-item div{min-width:0;flex:1}.field-item strong,.field-item small{display:block;overflow-wrap:anywhere;font-size:13px}.field-item small{margin-top:4px;font-size:11px}.field-symbol{color:var(--el-color-primary)}.content-panel{min-width:0;display:grid;grid-template-columns:minmax(0,1fr);gap:20px;align-content:start}.relation-flow{display:flex;gap:14px;align-items:center;overflow:auto;padding:20px 0}.source-node{display:grid;gap:10px;min-width:180px;border:1px solid var(--el-color-primary-light-7);border-top:3px solid var(--el-color-primary);padding:16px;border-radius:8px}.source-node span,.join-link{font-size:12px;color:var(--el-text-color-secondary)}.join-link{min-width:120px;text-align:center}.join-link span{display:block;margin-top:8px}.preview-toolbar{margin:8px 0 16px}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px;line-height:1.7}@media(max-width:900px){.workspace-body{grid-template-columns:1fr}.field-panel{max-height:280px;overflow:auto}.workspace-header{align-items:flex-start;flex-direction:column}}
.parameter-note{font-size:12px;color:var(--el-text-color-secondary);line-height:1.8}.query-parameters .el-form{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px}
</style>
