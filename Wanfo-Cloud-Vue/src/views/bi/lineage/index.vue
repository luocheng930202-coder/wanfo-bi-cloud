<template>
  <div class="app-container lineage-page" v-loading="loading">
    <header class="page-head">
      <div>
        <h1>数据血缘</h1>
        <p>重建平台血缘边，并按资产查看上下游关系。细粒度图谱也可在「数据目录」资产详情中打开。</p>
      </div>
      <div class="actions">
        <el-button @click="loadAssets">刷新资产</el-button>
        <el-button v-hasPermi="['bi:resource:admin']" type="primary" :loading="rebuilding" @click="rebuild">重建血缘</el-button>
        <el-button @click="goCatalog">打开数据目录</el-button>
      </div>
    </header>

    <el-alert class="mb12" type="info" :closable="false" show-icon
      title="血缘覆盖数据集、指标、管道发布与仓表绑定等关系；重建可能需要数十秒，请勿频繁触发。" />

    <el-form :inline="true" class="mb12" @submit.prevent>
      <el-form-item label="资产类型">
        <el-select v-model="filters.type" clearable placeholder="全部" style="width:160px" @change="search">
          <el-option label="数据集" value="DATASET" />
          <el-option label="指标" value="METRIC" />
          <el-option label="图表" value="CHART" />
          <el-option label="看板" value="DASHBOARD" />
          <el-option label="数据源" value="DATASOURCE" />
          <el-option label="仓表" value="WAREHOUSE_TABLE" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="filters.q" clearable placeholder="名称" style="width:220px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="assets" border max-height="560">
      <el-table-column prop="name" label="资产名称" min-width="200" />
      <el-table-column prop="assetType" label="类型" width="140" />
      <el-table-column prop="urn" label="URN" min-width="240" show-overflow-tooltip />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openGraph(row)">查看血缘</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无资产，请先在数据目录回填或扫描" /></template>
    </el-table>

    <el-dialog v-model="graphVisible" :title="`${current?.name || '资产'} · 血缘`" width="min(1080px, 96vw)" destroy-on-close>
      <div class="toolbar mb12">
        <el-radio-group v-model="query.direction" @change="loadGraph">
          <el-radio-button value="both">上下游</el-radio-button>
          <el-radio-button value="upstream">仅上游</el-radio-button>
          <el-radio-button value="downstream">仅下游</el-radio-button>
        </el-radio-group>
        <el-select v-model="query.depth" style="width:100px;margin-left:12px" @change="loadGraph">
          <el-option v-for="d in [1,2,3,4,5,6]" :key="d" :label="`${d} 层`" :value="d" />
        </el-select>
        <el-checkbox v-model="query.includeStale" style="margin-left:12px" @change="loadGraph">包含失效</el-checkbox>
        <el-button style="margin-left:12px" :loading="graphLoading" @click="loadGraph">刷新</el-button>
      </div>
      <el-table v-loading="graphLoading" :data="graph?.edges || []" border max-height="420">
        <el-table-column label="源" min-width="180">
          <template #default="{ row }">{{ nodeName(row.sourceAssetId) }}</template>
        </el-table-column>
        <el-table-column prop="edgeKind" label="关系" width="120" />
        <el-table-column label="目标" min-width="180">
          <template #default="{ row }">{{ nodeName(row.targetAssetId) }}</template>
        </el-table-column>
        <el-table-column prop="sourceMethod" label="来源" width="140" />
        <el-table-column label="置信度" width="90">
          <template #default="{ row }">{{ Math.round(Number(row.confidence) * 100) }}%</template>
        </el-table-column>
        <template #empty><el-empty description="暂无血缘边" :image-size="72" /></template>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLineageGraph, rebuildCatalogLineage, searchCatalogAssets, type CatalogAsset, type LineageGraph } from '@/api/bi/catalog'
import { unwrapBiData } from '@/api/bi/response'

const router = useRouter()
const loading = ref(false)
const rebuilding = ref(false)
const graphLoading = ref(false)
const allAssets = ref<CatalogAsset[]>([])
const filters = reactive({ type: '', q: '' })
const graphVisible = ref(false)
const current = ref<CatalogAsset | null>(null)
const graph = ref<LineageGraph | null>(null)
const query = reactive({ direction: 'both' as 'both' | 'upstream' | 'downstream', depth: 3, includeStale: false })

const assets = computed(() => {
  const q = filters.q.trim().toLowerCase()
  return allAssets.value.filter(a => {
    if (filters.type && a.assetType !== filters.type) return false
    if (q && !(a.name || '').toLowerCase().includes(q) && !(a.urn || '').toLowerCase().includes(q)) return false
    return true
  }).slice(0, 200)
})

const nodeMap = computed(() => {
  const map = new Map<number, CatalogAsset | { id: number; name: string }>()
  for (const n of graph.value?.nodes || []) map.set(n.id, n as any)
  return map
})

function nodeName(id: number) {
  return nodeMap.value.get(id)?.name || `#${id}`
}

async function loadAssets() {
  loading.value = true
  try {
    const page = unwrapBiData(await searchCatalogAssets({
      sourceKind: 'BI_RESOURCE',
      type: (filters.type || undefined) as any,
      query: filters.q || undefined,
      page: 1,
      pageSize: 100
    }))
    allAssets.value = page?.items || []
  } catch {
    allAssets.value = []
    ElMessage.error('加载资产失败')
  } finally {
    loading.value = false
  }
}

function search() { loadAssets() }

async function rebuild() {
  rebuilding.value = true
  try {
    await rebuildCatalogLineage()
    ElMessage.success('血缘重建已提交/完成')
  } catch {
    ElMessage.error('重建失败')
  } finally {
    rebuilding.value = false
  }
}

async function openGraph(row: CatalogAsset) {
  current.value = row
  graphVisible.value = true
  await loadGraph()
}

async function loadGraph() {
  if (!current.value?.id) return
  graphLoading.value = true
  try {
    const res = await getLineageGraph(current.value.id, query)
    graph.value = res.data
  } catch {
    ElMessage.error('加载血缘失败')
  } finally {
    graphLoading.value = false
  }
}

function goCatalog() {
  router.push('/bi-admin/catalog')
}

onMounted(loadAssets)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 16px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.page-head p { margin: 0; color: var(--el-text-color-secondary); max-width: 720px; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
.mb12 { margin-bottom: 12px; }
.toolbar { display: flex; align-items: center; flex-wrap: wrap; }
</style>
