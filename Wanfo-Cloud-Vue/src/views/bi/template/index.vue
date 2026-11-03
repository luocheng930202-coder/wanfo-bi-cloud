<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="toolbar">
          <span>模板中心</span>
          <el-button :loading="loading" @click="refresh">刷新</el-button>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon class="mb8"
        title="先预览模板，再选择完整布局或仅使用视觉风格。我的模板包含自己保存、导入及获授权使用的模板。" />
      <el-tabs v-model="tab">
        <el-tab-pane label="模板市场" name="market">
          <div class="market-filters">
            <el-input v-model="marketSearch" clearable placeholder="搜索市场模板" style="max-width:320px" />
            <el-select v-model="marketKind" clearable placeholder="全部类型" style="width:140px">
              <el-option value="dashboard" label="看板" />
              <el-option value="screen" label="大屏" />
            </el-select>
          </div>
          <el-row :gutter="16">
            <el-col v-for="item in filteredMarket" :key="item.id" :xs="24" :sm="12" :lg="8">
              <el-card shadow="hover" class="market-card">
                <div class="market-head">
                  <strong>{{ item.name }}</strong>
                  <el-tag size="small">{{ item.kind === 'dashboard' ? '看板' : '大屏' }}</el-tag>
                </div>
                <p class="market-meta">{{ item.category }} · {{ item.provider === 'remote' ? '远程' : '内置' }}</p>
                <p class="market-desc">{{ item.description }}</p>
                <el-button type="primary" link @click="previewMarket(item)">预览模板 →</el-button>
              </el-card>
            </el-col>
          </el-row>
          <el-empty v-if="!filteredMarket.length && !loading" description="没有匹配的市场模板" />
        </el-tab-pane>
        <el-tab-pane label="我的模板" name="local">
          <el-table :data="localRows" v-loading="loading" border>
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="category" label="分类" width="120" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ row.kind === 'dashboard' ? '看板' : '大屏' }}</template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
            <el-table-column prop="createBy" label="创建人" width="110" />
            <el-table-column label="操作" width="360" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="previewLocal(row)">预览模板</el-button>
                <el-button v-hasPermi="['bi:template:list']" link @click="downloadLocal(row)">下载</el-button>
                <el-button v-if="row.resourceActions?.includes('edit')" v-hasPermi="['bi:template:edit']" link @click="openEdit(row)">编辑</el-button>
                <el-button v-if="row.resourceActions?.includes('grant')" v-hasPermi="['bi:template:grant']" link @click="openGrants(row)">授权</el-button>
                <el-button v-if="row.resourceActions?.includes('remove')" v-hasPermi="['bi:template:remove']" link type="danger" @click="removeLocal(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty><el-empty description="暂无我的模板，可在编辑器中将设计保存为模板" /></template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="editor.visible" title="编辑模板信息" width="min(520px, calc(100vw - 24px))">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="editor.name" maxlength="200" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="editor.category" maxlength="64" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="editor.description" type="textarea" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">取消</el-button>
        <el-button type="primary" :loading="editor.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="marketPreview.visible" :fullscreen="previewFullscreen" :title="marketPreview.row?.name || '模板预览'" width="min(1000px, calc(100vw - 24px))" destroy-on-close>
      <template v-if="marketPreview.row">
        <div class="market-preview-meta">
          <el-tag>{{ marketPreview.row.kind === 'dashboard' ? '看板' : '大屏' }}</el-tag>
          <el-tag type="info">{{ marketPreview.row.category }}</el-tag>
          <span>{{ marketPreview.row.provider === 'mine' ? '我的模板' : marketPreview.row.provider === 'remote' ? '远程模板' : '内置模板' }}</span>
          <el-button size="small" @click="previewFullscreen=!previewFullscreen">{{ previewFullscreen?'退出全屏':'全屏预览' }}</el-button>
        </div>
        <p class="market-preview-desc">{{ marketPreview.row.description }}</p>
        <el-radio-group v-model="previewMode" class="mb8" aria-label="模板预览方式"><el-radio-button value="effect">效果演示</el-radio-button><el-radio-button value="layout">布局结构</el-radio-button></el-radio-group>
        <DashboardTemplatePreview v-if="marketPreview.template && marketPreview.file" :template="marketPreview.template" :file="marketPreview.file" :demonstration="previewMode==='effect'" :style="previewFullscreen?{maxHeight:'calc(100vh - 260px)'}:{}" />
        <el-skeleton v-else-if="marketPreview.loading" :rows="5" animated />
        <el-alert v-else :title="marketPreview.error || '模板预览加载失败，请重试'" type="error" :closable="false" />
        <el-alert class="market-preview-hint" type="info" :closable="false" :title="previewMode==='effect'?'效果演示使用独立示例数据，不代表真实业务，也不会写入新草稿。常用图表展示示例，暂不支持的类型显示占位。':'布局结构不包含业务数据。完整布局进入绑定向导；仅用风格进入空白编辑器，带入基础背景、配色、字号、圆角与画布尺寸。'" />
      </template>
      <template #footer>
        <el-button @click="marketPreview.visible = false">返回</el-button>
        <el-button v-hasPermi="['bi:dashboard:add']" :disabled="!marketPreview.file || marketPreview.loading" @click="useTemplateStyle">仅使用视觉风格</el-button>
        <el-button v-hasPermi="['bi:dashboard:add']" type="primary" :disabled="!marketPreview.template || marketPreview.loading" @click="useMarketTemplate">使用完整布局</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="grantDialog.visible" :title="'模板授权 · ' + grantDialog.name" width="min(720px, calc(100vw - 24px))">
      <div class="toolbar mb8">
        <el-select v-model="grantDialog.principalId" remote filterable :remote-method="searchPrincipals" placeholder="搜索用户、角色或部门" style="min-width:240px">
          <el-option v-for="p in principals" :key="p.id" :value="p.id" :label="p.type + ' · ' + p.name" />
        </el-select>
        <el-select v-model="grantDialog.action" style="width:130px">
          <el-option v-for="(label, key) in grantActions" :key="key" :value="key" :label="label" />
        </el-select>
        <el-button :disabled="!grantDialog.principalId" :loading="grantSaving" @click="addGrant">授权</el-button>
      </div>
      <el-table :data="grants" border>
        <el-table-column prop="displayName" label="对象" />
        <el-table-column label="权限"><template #default="{ row }">{{ grantActions[row.action] || row.action }}</template></el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }"><el-button link type="danger" :disabled="grantSaving" @click="removeGrant(row)">撤销</el-button></template>
        </el-table-column>
      </el-table>
    </el-dialog>
    <DashboardTemplatePicker v-if="templateOpen" v-model="templateOpen" :kind="templateKind" :initial-select-id="templatePickId" />
  </div>
</template>

<script setup lang="ts" name="BiTemplateCenter">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listTemplateSummaries, getTemplate, saveTemplate, deleteTemplate,
  listTemplateMarket, getMarketTemplate, searchTemplatePrincipals, listTemplateGrants, grantTemplate, revokeTemplateGrant,
  type TemplateSummary, type MarketTemplateEntry
} from '@/api/bi/template'
import type { GrantPrincipal, ResourceGrant } from '@/api/bi/datasource'
import DashboardTemplatePreview from '@/views/bi/dashboard/DashboardTemplatePreview.vue'
import DashboardTemplatePicker from '@/views/bi/dashboard/DashboardTemplatePicker.vue'
import { importedTemplate, parseLayoutTemplate, type LayoutTemplateFile } from '@/utils/biTemplateFiles'
import type { ScreenTemplate } from '@/utils/biTemplates'
import {serializeTemplateStyle} from '@/utils/biTemplateStyleDraft'

const router=useRouter()
const previewMode=ref<'effect'|'layout'>('effect'),previewFullscreen=ref(false)
type PreviewEntry=Omit<MarketTemplateEntry,'provider'> & {provider:MarketTemplateEntry['provider']|'mine';localId?:number}
let previewSequence=0

const templateOpen = ref(false)
const templateKind = ref<'screen' | 'dashboard'>('screen')
const templatePickId = ref('')
const tab = ref<'local' | 'market'>('market')
const loading = ref(false)
const localRows = ref<TemplateSummary[]>([])
const marketRows = ref<MarketTemplateEntry[]>([])
const marketSearch = ref('')
const marketKind = ref<'' | 'screen' | 'dashboard'>('')
const marketPreview = reactive<{
  visible: boolean
  loading: boolean
  error: string
  row?: PreviewEntry
  file?: LayoutTemplateFile
  template?: ScreenTemplate
}>({ visible: false, loading: false, error: '' })

const filteredMarket = computed(() => marketRows.value.filter(row => {
  const q = marketSearch.value.trim()
  const text = row.name + row.description + row.category
  return (!marketKind.value || row.kind === marketKind.value) && (!q || text.includes(q))
}))

async function refresh() {
  loading.value = true
  try {
    const [local, market] = await Promise.all([listTemplateSummaries(), listTemplateMarket()])
    localRows.value = local.data || []
    marketRows.value = market.data || []
  } catch {
    ElMessage.error('加载模板失败，请检查权限或稍后重试')
  } finally {
    loading.value = false
  }
}

function previewMarket(row:MarketTemplateEntry){return openTemplatePreview(row,async()=>(await getMarketTemplate(row.id)).data.content)}
function previewLocal(row:TemplateSummary){return openTemplatePreview({id:String(row.id),localId:row.id,name:row.name,category:row.category,description:row.description,kind:row.kind || 'screen',provider:'mine'},async()=>(await getTemplate(row.id)).data.content)}
async function openTemplatePreview(row:PreviewEntry,load:()=>Promise<LayoutTemplateFile>){
  const sequence=++previewSequence
  previewMode.value='effect';previewFullscreen.value=false
  marketPreview.visible=true;marketPreview.loading=true;marketPreview.error='';marketPreview.row=row;marketPreview.file=undefined;marketPreview.template=undefined
  try{
    const content=await load();if(sequence!==previewSequence || !marketPreview.visible)return
    const file=parseLayoutTemplate(JSON.stringify(content))
    if(row.provider!=='mine' && (row.kind==='dashboard')!==(file.format==='datacenter-dashboard-template'))throw Error('模板类型与目录信息不一致')
    if(row.provider==='mine')marketPreview.row={...row,kind:file.format==='datacenter-dashboard-template'?'dashboard':'screen'}
    marketPreview.file=file
    marketPreview.template={...importedTemplate(file),id:row.provider==='mine'?'saved-'+row.id:'market-'+row.id,name:row.name,category:row.category,description:row.description}
  }catch{if(sequence===previewSequence)marketPreview.error='模板预览加载失败，请检查权限或稍后重试'}finally{if(sequence===previewSequence)marketPreview.loading=false}
}
function useMarketTemplate(){
  const row=marketPreview.row;if(!row || !marketPreview.template || marketPreview.loading)return
  marketPreview.visible=false;templateKind.value=row.kind;templatePickId.value=row.provider==='mine'?'saved-'+row.localId:'market-'+row.id;templateOpen.value=true
}
async function useTemplateStyle(){
  if(!marketPreview.file || marketPreview.loading)return
  try{
    const snapshot=serializeTemplateStyle({...marketPreview.file,name:marketPreview.row?.name || marketPreview.file.name})
    const kind=marketPreview.file.format==='datacenter-screen-template'?'screen':'dashboard'
    const failure=await router.push({path:'/bi-admin/dashboard-workspace/new',query:{kind,styleOnly:'1'},state:{biStyleDraft:snapshot}})
    if(failure)throw Error('未能打开编辑器')
    marketPreview.visible=false
  }catch{ElMessage.error('无法应用模板风格，请重新预览后重试')}
}
async function downloadLocal(row: TemplateSummary) {
  try {
    const detail = (await getTemplate(row.id)).data
    const blob = new Blob([JSON.stringify(detail.content, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = (row.name || 'template').replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_').slice(0, 80) + (row.kind === 'dashboard' ? '.bi-dashboard.json' : '.bi-screen.json')
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    setTimeout(() => URL.revokeObjectURL(url), 0)
  } catch {
    ElMessage.error('下载失败')
  }
}

const editor = reactive({ visible: false, id: 0, name: '', category: '', description: '', saving: false })
async function openEdit(row: TemplateSummary) {
  try {
    const detail = (await getTemplate(row.id)).data
    Object.assign(editor, { visible: true, id: row.id, name: detail.name, category: detail.category, description: detail.description, saving: false })
  } catch {
    ElMessage.error('无法加载模板')
  }
}
async function saveEdit() {
  if (!editor.name.trim()) return
  editor.saving = true
  try {
    const detail = (await getTemplate(editor.id)).data
    await saveTemplate({ name: editor.name.trim(), category: editor.category.trim() || '我的模板', description: editor.description.trim(), content: detail.content }, editor.id)
    editor.visible = false
    await refresh()
    ElMessage.success('已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    editor.saving = false
  }
}

async function removeLocal(row: TemplateSummary) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.name}」？`, '删除模板', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteTemplate(row.id)
    await refresh()
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

const grantActions: Record<string, string> = { view: '查看', use: '使用', edit: '编辑', remove: '删除', grant: '授权管理' }
const grantDialog = reactive({ visible: false, id: 0, name: '', principalId: undefined as number | undefined, action: 'use' })
const principals = ref<GrantPrincipal[]>([])
const grants = ref<ResourceGrant[]>([])
const grantSaving = ref(false)
let principalVersion = 0
async function searchPrincipals(query = '') {
  const v = ++principalVersion
  const r = await searchTemplatePrincipals(query)
  if (v === principalVersion) principals.value = r.data || []
}
async function openGrants(row: TemplateSummary) {
  Object.assign(grantDialog, { visible: true, id: row.id, name: row.name, principalId: undefined, action: 'use' })
  grants.value = []
  await Promise.all([searchPrincipals(), reloadGrants()])
}
async function reloadGrants() {
  const id = grantDialog.id
  const r = await listTemplateGrants(id)
  if (id === grantDialog.id) grants.value = r.data || []
}
async function addGrant() {
  if (!grantDialog.principalId) return
  grantSaving.value = true
  try {
    await grantTemplate(grantDialog.id, grantDialog.principalId, grantDialog.action)
    await reloadGrants()
    ElMessage.success('已授权')
  } finally {
    grantSaving.value = false
  }
}
async function removeGrant(row: ResourceGrant) {
  grantSaving.value = true
  try {
    await revokeTemplateGrant(grantDialog.id, row.principalId, row.action)
    await reloadGrants()
    ElMessage.success('已撤销')
  } finally {
    grantSaving.value = false
  }
}

onMounted(refresh)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.mb8 { margin-bottom: 12px; }
.market-filters { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.market-card { margin-bottom: 16px; min-height: 168px; }
.market-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.market-meta { font-size: 12px; color: #94a3b8; margin: 8px 0; }
.market-desc { font-size: 13px; color: #64748b; line-height: 1.6; min-height: 48px; }
.market-preview-meta { display: flex; align-items: center; gap: 8px; color: #64748b; font-size: 13px; }
.market-preview-desc { margin: 12px 0 16px; color: #475569; line-height: 1.7; }
.market-preview-hint { margin-top: 14px; }
</style>
