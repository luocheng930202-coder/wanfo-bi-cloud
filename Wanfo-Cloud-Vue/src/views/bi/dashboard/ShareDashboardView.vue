<template>
  <div class="app-container">
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb8" />
    <el-card v-if="view">
      <template #header>
        <div class="toolbar">
          <span>{{ view.name }} <el-tag size="small">分享 · {{ scopeLabel }}</el-tag></span>
          <el-button :loading="loading" @click="reload">刷新</el-button>
        </div>
      </template>
      <div v-if="view.controls?.length" class="filter-bar">
        <div v-for="c in view.controls" :key="c.key" class="filter-item">
          <label>{{ c.label }}</label>
          <DashboardFilterInput v-model="filterInputs[c.key]" :type="c.type" :label="c.label" :options="filterOptions[c.key] || []" :loading="optionLoading[c.key]" @options="loadOptions(c.key)" />
        </div>
        <div class="toolbar">
          <el-button type="primary" @click="applyFilters">应用筛选</el-button>
          <el-button @click="resetFilters">恢复默认</el-button>
        </div>
      </div>
      <DashboardCanvas :scene="view.scene" :panels="visiblePanels" :appearance="view.appearance" readonly>
        <template #default="{ panel }">
          <DashboardCard :title="panel.title" :component-type="panel.componentType" :content="panel.content" :panel-style="panel.style" :appearance="view.appearance" :payload="payloads[panel.key]" :error="panelErrors[panel.key]" :loading="panelLoading[panel.key]" @refresh="refreshPanel(panel.key)" :linkable="view.links?.some(l => l.sourcePanel === panel.key)" :drill-fields="panel.drillFields" :drill-path="drillPath(panel.key)" @select="v => selectLink(panel.key, v)" @drill="p => drillDown(panel.key, p.field, p.value)" @drill-up="(lvl:number) => drillUp(panel.key, lvl)" />
        </template>
      </DashboardCanvas>
    </el-card>
    <el-dialog v-model="passwordDialog" title="访问密码" width="400px" :close-on-click-modal="false">
      <el-input v-model="password" type="password" show-password placeholder="请输入分享密码" @keyup.enter="reload" />
      <template #footer><el-button type="primary" @click="reload">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { runPanelQueue } from '@/utils/biPanelQueue'
import { getShareDashboardView, shareDashboardPanelData, shareDashboardControlOptions, type ShareDashboardView } from '@/api/bi/share'
import type { PanelResult, FilterValue, DashboardInput, DrillPathSegment } from '@/api/bi/dashboard'
import { appendDrillSegment, mergeDashboardInput, truncateDrillPath } from '@/utils/biDrill'
import DashboardCanvas from './DashboardCanvas.vue'
import DashboardCard from './DashboardCard.vue'
import DashboardFilterInput from './DashboardFilterInput.vue'

const route = useRoute()
const token = computed(() => String(route.params.token || ''))
const view = ref<ShareDashboardView | null>(null)
const password = ref('')
const passwordDialog = ref(false)
const loading = ref(false)
const error = ref('')
const payloads = reactive<Record<string, PanelResult>>(Object.create(null))
const panelErrors = reactive<Record<string, string>>(Object.create(null))
const panelLoading = reactive<Record<string, boolean>>(Object.create(null))
const filterInputs = reactive<Record<string, FilterValue>>(Object.create(null))
const appliedFilters = reactive<Record<string, FilterValue>>(Object.create(null))
const activeLinks = reactive<Record<string, string | number | null>>(Object.create(null))
const activeDrills = reactive<Record<string, DrillPathSegment[]>>(Object.create(null))
const filterOptions = reactive<Record<string, (string | number)[]>>(Object.create(null))
const optionLoading = reactive<Record<string, boolean>>(Object.create(null))

const scopeLabel = computed(() => view.value?.dataScope === 'publisher' ? '创建者数据' : '访问者数据')
const visiblePanels = computed(() => view.value?.kind === 'screen' ? [...(view.value?.panels || [])] : [...(view.value?.panels || [])].sort((a, b) => a.y - b.y || a.x - b.x))

function runtimeInput(): DashboardInput {
  return mergeDashboardInput(JSON.parse(JSON.stringify(appliedFilters)), JSON.parse(JSON.stringify(activeLinks)), JSON.parse(JSON.stringify(activeDrills)))
}
function drillPath(key: string) { return activeDrills[key] || [] }
function drillDown(panel: string, field: string, value: string | number | null) {
  activeDrills[panel] = appendDrillSegment(drillPath(panel), field, value)
  refreshPanel(panel)
}
function drillUp(panel: string, level: number) {
  activeDrills[panel] = truncateDrillPath(drillPath(panel), level)
  refreshPanel(panel)
}

function resetFilters() {
  for (const c of view.value?.controls || []) filterInputs[c.key] = c.defaultValue ?? null
}

async function reload() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    const r = await getShareDashboardView(token.value, password.value || undefined)
    view.value = r.data!
    passwordDialog.value = false
    resetFilters()
    Object.assign(appliedFilters, JSON.parse(JSON.stringify(filterInputs)))
    await refreshAll()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '加载失败'
    if (msg.includes('密码') || msg.includes('401')) {
      passwordDialog.value = true
      error.value = '需要输入分享访问密码'
    } else {
      error.value = msg
    }
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  if (!view.value) return
  await runPanelQueue(view.value.panels, p => refreshPanel(p.key, true))
}

function applyFilters() {
  Object.assign(appliedFilters, JSON.parse(JSON.stringify(filterInputs)))
  Object.keys(activeLinks).forEach(k => delete activeLinks[k])
  Object.keys(activeDrills).forEach(k => delete activeDrills[k])
  refreshAll()
}

function selectLink(panel: string, value: string | number | null) {
  for (const l of view.value?.links || []) if (l.sourcePanel === panel) activeLinks[l.key] = value
  refreshAll()
}

async function loadOptions(key: string) {
  if (!view.value) return
  optionLoading[key] = true
  try {
    const r = await shareDashboardControlOptions(token.value, key, view.value.revision, runtimeInput(), password.value || undefined)
    filterOptions[key] = r.data?.values || []
  } finally { optionLoading[key] = false }
}

async function refreshPanel(key: string, internal = false) {
  if (!internal && panelLoading[key]) return
  const current = view.value
  if (!current) return
  const type = current.panels.find(p => p.key === key)?.componentType
  if (type && !['chart', 'kpi'].includes(type)) return
  panelLoading[key] = true
  delete panelErrors[key]
  try {
    const r = await shareDashboardPanelData(token.value, key, current.revision, runtimeInput(), password.value || undefined)
    payloads[key] = r.data
  } catch {
    panelErrors[key] = '组件加载失败，请检查数据权限或分享范围'
  } finally { panelLoading[key] = false }
}

onMounted(reload)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.filter-bar { padding: 12px; border: 1px solid var(--el-border-color); border-radius: 6px; margin-bottom: 12px; }
.filter-item { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.filter-item label { min-width: 100px; }
.mb8 { margin-bottom: 8px; }
</style>
