<template>
   <div class="app-container bi-container">
      <el-alert
         type="info"
         :closable="false"
         show-icon
         class="mb8"
      >
         <template #title>
            <span class="text-bold">数据源</span>
            <span class="text-secondary">：BI 查询的唯一入口。schema 别名是 Calcite 中的引用名，创建后不可修改。</span>
         </template>
      </el-alert>

      <el-form :model="queryParams" :inline="true" class="mb8">
         <el-form-item label="名称">
            <el-input
               v-model="queryParams.name"
               placeholder="按名称搜索"
               clearable
               style="width: 200px"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item label="状态">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
               <el-option label="正常" value="0" />
               <el-option label="停用" value="1" />
            </el-select>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
         <el-form-item style="float: right">
            <el-button type="primary" icon="Plus" v-hasPermi="['bi:datasource:add']" @click="handleAdd">新增数据源</el-button>
            <el-button icon="Connection" v-hasPermi="['bi:datasource:test']" @click="handleProbeAll" :loading="probing">批量探测</el-button>
         </el-form-item>
      </el-form>

      <el-table
         v-loading="loading"
         :data="tableData"
         border
         stripe
      >
         <el-table-column prop="id" label="ID" width="70" align="center" />
         <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
         <el-table-column prop="type" label="类型" width="100" align="center">
            <template #default="{ row }">
               <el-tag size="small" effect="plain">{{ row.type }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column prop="schemaName" label="Schema 别名" width="130" align="center">
            <template #default="{ row }">
               <el-tag size="small" type="warning">{{ row.schemaName }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column prop="jdbcUrl" label="连接串" min-width="260" show-overflow-tooltip />
         <el-table-column prop="username" label="账号" width="110" align="center" />
         <el-table-column label="密码" width="90" align="center">
            <template #default="{ row }">
               <span v-if="row.passwordSet" class="text-secondary">{{ row.password || '****' }}</span>
               <span v-else class="text-danger">未设置</span>
            </template>
         </el-table-column>
         <el-table-column label="连通性" width="90" align="center">
            <template #default="{ row }">
               <el-tooltip :content="probeResult[row.schemaName] ? '连通正常' : (probed ? '探测失败' : '未探测')" placement="top">
                  <el-tag :type="probeResult[row.schemaName] ? 'success' : 'info'" size="small">
                     {{ probeResult[row.schemaName] ? '正常' : '—' }}
                  </el-tag>
               </el-tooltip>
            </template>
         </el-table-column>
         <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{ row }">
               <el-switch
                  :disabled="!checkPermi(['bi:datasource:edit']) || !row.resourceActions?.includes('edit')"
                  v-model="row.status"
                  active-value="0"
                  inactive-value="1"
                  @change="val => handleStatusChange(row, String(val))"
               />
            </template>
         </el-table-column>
         <el-table-column label="操作" width="310" align="center" fixed="right">
            <template #default="{ row }">
               <el-button link type="primary" icon="Edit" v-hasPermi="['bi:datasource:edit']" v-if="row.resourceActions?.includes('edit')" @click="handleUpdate(row)">编辑</el-button>
               <el-button link type="success" icon="VideoPlay" v-hasPermi="['bi:datasource:test']" v-if="row.resourceActions?.includes('test')" @click="handleTest(row)">测试</el-button>
               <el-button link type="danger" icon="Delete" v-hasPermi="['bi:datasource:remove']" v-if="row.resourceActions?.includes('remove')" @click="handleDelete(row)">删除</el-button>
               <el-button v-if="row.resourceActions?.includes('model') && checkPermi(['bi:dataset:add'])" v-hasPermi="['bi:datasource:model']" link type="primary" @click="$router.push({ path: '/bi-admin/dataset', query: { datasourceId: row.id } })">建数据集</el-button>
               <el-button v-if="row.resourceActions?.includes('grant')" v-hasPermi="['bi:datasource:grant']" link type="primary" @click="openGrants(row)">授权</el-button>
            </template>
         </el-table-column>
         <template #empty>
            <el-empty description="暂无数据源，点击右上角「新增数据源」" />
         </template>
      </el-table>

      <pagination
         v-show="total > 0"
         :total="total"
         v-model:page="queryParams.pageNum"
         v-model:limit="queryParams.pageSize"
         @pagination="getList"
      />

      <!-- 新增 / 编辑 -->
      <el-dialog :title="dialog.title" v-model="dialog.visible" :width="!form.id && createStep === 0 ? 'min(1100px, calc(100vw - 24px))' : 'min(720px, calc(100vw - 24px))'" @closed="resetForm">
         <el-steps v-if="!form.id" :active="createStep" align-center class="source-steps">
            <el-step title="选择数据源" />
            <el-step title="配置信息" />
         </el-steps>
         <div v-if="!form.id && createStep === 0" class="source-picker">
            <aside class="source-sidebar">
               <el-input v-model="sourceSearch" placeholder="搜索数据源" clearable prefix-icon="Search" aria-label="搜索数据源类型" />
               <nav class="source-categories" aria-label="数据源分类">
                  <button v-for="category in sourceCategories" :key="category.value" type="button"
                     :class="['source-category', { active: activeCategory === category.value }]"
                     :aria-pressed="activeCategory === category.value" @click="activeCategory = category.value">
                     <span>{{ category.label }}</span><span class="source-count">{{ categoryCount(category.value) }}</span>
                  </button>
               </nav>
            </aside>
            <section class="source-content" aria-label="可选数据源">
               <div class="source-heading"><strong>{{ activeCategoryLabel }}</strong><span>{{ filteredSourceTypes.length }} 种数据源</span></div>
               <div v-if="filteredSourceTypes.length" class="source-grid">
                  <button v-for="source in filteredSourceTypes" :key="source.value" type="button"
                     :class="['source-card', { selected: selectedSource === source.value }]"
                     :aria-pressed="selectedSource === source.value" @click="selectedSource = source.value" @dblclick="nextSourceStep">
                     <span class="source-icon" :style="{ color: source.color, backgroundColor: source.color + '14' }">{{ source.abbr }}</span>
                     <span class="source-card-copy"><strong>{{ source.label }}</strong><small>{{ source.description }}</small></span>
                     <el-icon v-if="selectedSource === source.value" class="source-check"><Check /></el-icon>
                  </button>
               </div>
               <el-empty v-else :description="sourceSearch.trim() ? '没有匹配的数据源，请调整搜索关键词' : activeCategory === 'recent' ? '暂无创建记录，成功创建后将在这里显示' : '该分类暂未接入数据源'" :image-size="88" />
            </section>
         </div>
         <div v-if="!form.id && createStep === 1" class="source-selected">已选择 <el-tag>{{ selectedSourceLabel }}</el-tag></div>
         <el-form v-if="form.id || createStep === 1" ref="formRef" :model="form" :rules="rules" label-width="110px">
            <el-row :gutter="16">
               <el-col :xs="24" :sm="12">
                  <el-form-item label="名称" prop="name">
                     <el-input v-model="form.name" placeholder="如：生产 Doris" />
                  </el-form-item>
               </el-col>
               <el-col :xs="24" :sm="12">
                  <el-form-item label="类型" prop="type">
                     <el-select v-model="form.type" disabled style="width: 100%">
                        <el-option v-for="t in DB_TYPES" :key="t.value" :label="t.label" :value="t.value" />
                     </el-select>
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row :gutter="16">
               <el-col :xs="24" :sm="12">
                  <el-form-item label="Schema 别名" prop="schemaName">
                     <el-input v-model="form.schemaName" placeholder="小写字母开头，如 prod" :disabled="!!form.id" />
                  </el-form-item>
               </el-col>
               <el-col :xs="24" :sm="12">
                  <el-form-item label="驱动类" prop="driverClass">
                     <el-input v-model="form.driverClass" placeholder="留空则按类型自动推导" />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-form-item label="连接串" prop="jdbcUrl">
               <el-input v-model="form.jdbcUrl" :placeholder="DB_TYPES.find(t => t.value === form.type)?.url || 'jdbc:…'" />
            </el-form-item>
            <el-row :gutter="16">
               <el-col :xs="24" :sm="12">
                  <el-form-item label="账号" prop="username">
                     <el-input v-model="form.username" autocomplete="off" />
                  </el-form-item>
               </el-col>
               <el-col :xs="24" :sm="12">
                  <el-form-item label="密码" prop="password">
                     <el-input
                        v-model="form.password"
                        type="password"
                        show-password
                        autocomplete="new-password"
                        :placeholder="form.id ? '留空使用已保存密码' : '请输入数据库密码（允许为空）'"
                     />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row :gutter="16">
               <el-col :xs="24" :sm="8">
                  <el-form-item label="池上限" prop="poolMaxTotal">
                     <el-input-number v-model="form.poolMaxTotal" :min="1" :max="100" style="width: 100%" />
                  </el-form-item>
               </el-col>
               <el-col :xs="24" :sm="8">
                  <el-form-item label="查询超时" prop="queryTimeout">
                     <el-input-number v-model="form.queryTimeout" :min="5" :max="3600" style="width: 100%" />
                     <div class="form-tip">秒</div>
                  </el-form-item>
               </el-col>
               <el-col :xs="24" :sm="8">
                  <el-form-item label="状态">
                     <el-radio-group v-model="form.status">
                        <el-radio value="0">正常</el-radio>
                        <el-radio value="1">停用</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
            </el-row>
            <el-form-item label="备注">
               <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
         </el-form>
         <el-alert v-if="connectionResult && (form.id || createStep === 1)" :type="connectionResult.ok ? 'success' : 'error'"
            :title="connectionResult.ok ? '连接成功' : '连接失败'" :closable="false" show-icon
            :description="connectionResult.ok ? `${connectionResult.databaseProduct || ''} · 耗时 ${connectionResult.costMs}ms` : connectionResult.message"
            class="mb8" />
         <template #footer>
            <div class="dialog-footer">
               <el-button
                  v-if="form.id || createStep === 1"
                  icon="Connection"
                  :loading="testing"
                  @click="handleProbeTest"
                  v-hasPermi="['bi:datasource:test']"
               >{{ testing ? '正在测试…' : '测试连接' }}</el-button>
               <el-button v-if="!form.id && createStep === 1" :disabled="submitting || testing" @click="createStep = 0">上一步</el-button>
               <el-button @click="dialog.visible = false">取消</el-button>
               <el-button v-if="!form.id && createStep === 0" type="primary" :disabled="!selectedSource" @click="nextSourceStep">下一步</el-button>
               <el-button v-if="form.id || createStep === 1" type="primary" :loading="submitting" :disabled="testing" @click="submitForm">{{ submitting ? '验证并保存…' : '保存' }}</el-button>
            </div>
         </template>
      </el-dialog>
      <el-dialog v-model="grantDialog.visible" :title="'数据源授权 · ' + grantDialog.name" width="min(640px, calc(100vw - 24px))">
         <el-alert title="操作需同时具备若依功能权限与此数据源的资源权限；所有者可管理自己的资源。" type="info" :closable="false" class="mb8" />
         <el-select v-model="grantDialog.principalId" filterable remote :remote-method="loadPrincipals" placeholder="搜索用户、角色或部门" style="width: 55%">
            <el-option v-for="p in grantPrincipals" :key="p.id" :value="p.id" :label="`${principalTypes[p.type] || p.type} · ${p.name}`" />
         </el-select>
         <el-select v-model="grantDialog.action" style="width: 25%">
            <el-option v-for="(label, action) in grantActions" :key="action" :value="action" :label="label" />
         </el-select>
         <el-button type="primary" :loading="grantSaving" :disabled="!grantDialog.principalId" @click="addGrant">授权</el-button>
         <el-table :data="resourceGrants" class="mt8">
            <el-table-column prop="displayName" label="对象" />
            <el-table-column label="类型"><template #default="{row}">{{ principalTypes[row.principalType] || row.principalType }}</template></el-table-column>
            <el-table-column label="操作"><template #default="{row}">{{ grantActions[row.action] || row.action }}</template></el-table-column>
            <el-table-column label="管理"><template #default="{row}"><el-button link type="danger" @click="removeGrant(row)">撤销</el-button></template></el-table-column>
         </el-table>
      </el-dialog>
   </div>
</template>

<script setup lang="ts" name="BiDatasource">
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { checkPermi } from '@/utils/permission'
import {
  listDatasources,
  createDatasource,
  updateDatasource,
  deleteDatasource,
  changeDatasourceStatus,
  probeDatasource,
  testSavedDatasource,
  probeAll,
  searchGrantPrincipals, listResourceGrants, grantResource, revokeResource,
  type ResourceGrant, type GrantPrincipal, type ConnectionTestResult,
  type BiDatasource,
  type DatasourceForm
} from '@/api/bi/datasource'

const DB_TYPES = [
  { value: 'mysql', label: 'MySQL', category: 'oltp', abbr: 'My', color: '#00758f', description: '关系型数据库', url: 'jdbc:mysql://host:3306/database' },
  { value: 'postgresql', label: 'PostgreSQL', category: 'oltp', abbr: 'PG', color: '#336791', description: '关系型数据库', url: 'jdbc:postgresql://host:5432/database' },
  { value: 'oracle', label: 'Oracle', category: 'oltp', abbr: 'Or', color: '#c74634', description: '关系型数据库', url: 'jdbc:oracle:thin:@//host:1521/service' },
  { value: 'sqlserver', label: 'SQL Server', category: 'oltp', abbr: 'SQL', color: '#b52d36', description: '关系型数据库', url: 'jdbc:sqlserver://host:1433;databaseName=database' },
  { value: 'h2', label: 'H2', category: 'oltp', abbr: 'H2', color: '#334e9e', description: '轻量级关系型数据库', url: 'jdbc:h2:~/database' },
  { value: 'doris', label: 'Doris', category: 'olap', abbr: 'Do', color: '#2876df', description: '实时分析数据库', url: 'jdbc:mysql://host:9030/database' },
  { value: 'starrocks', label: 'StarRocks', category: 'olap', abbr: 'SR', color: '#6849c9', description: '实时分析数据库', url: 'jdbc:mysql://host:9030/database' },
  { value: 'clickhouse', label: 'ClickHouse', category: 'olap', abbr: 'CH', color: '#997400', description: '列式分析数据库', url: 'jdbc:clickhouse://host:8123/database' }
]
const sourceCategories = [
  { value: 'recent', label: '最近创建' }, { value: 'all', label: '全部' },
  { value: 'oltp', label: 'OLTP' }, { value: 'olap', label: 'OLAP' },
  { value: 'lake', label: '数据湖' }, { value: 'api', label: 'API 数据' }, { value: 'file', label: '文件' }
]
const createStep = ref(0)
const sourceSearch = ref('')
const activeCategory = ref('all')
const selectedSource = ref('')
const recentSourceTypes = ref<string[]>([])
const activeCategoryLabel = computed(() => sourceCategories.find(c => c.value === activeCategory.value)?.label || '全部')
const selectedSourceLabel = computed(() => DB_TYPES.find(t => t.value === form.type)?.label || form.type)
function categoryTypes(category: string) {
  if (category === 'recent') return recentSourceTypes.value.flatMap(value => DB_TYPES.filter(t => t.value === value))
  return DB_TYPES.filter(t => category === 'all' || t.category === category)
}
function categoryCount(category: string) { return categoryTypes(category).length }
const filteredSourceTypes = computed(() => {
  const keyword = sourceSearch.value.trim().toLowerCase()
  return categoryTypes(activeCategory.value).filter(t => `${t.label} ${t.value} ${t.description}`.toLowerCase().includes(keyword))
})
function nextSourceStep() {
  const source = DB_TYPES.find(t => t.value === selectedSource.value)
  if (!source) return
  if (form.type !== source.value) {
    form.jdbcUrl = ''
    form.driverClass = ''
  }
  form.type = source.value
  createStep.value = 1
}

const loading = ref(false)
const submitting = ref(false)
const testing = ref(false)
const probing = ref(false)
const probed = ref(false)
const total = ref(0)
const tableData = ref<BiDatasource[]>([])
const probeResult = ref<Record<string, boolean>>({})
const formRef = ref()
const connectionResult = ref<ConnectionTestResult | null>(null)
let activeTest = 0

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  name: '',
  status: ''
})

const dialog = reactive({
  title: '',
  visible: false
})

const defaultForm = (): DatasourceForm => ({
  id: undefined,
  name: '',
  type: 'mysql',
  schemaName: '',
  jdbcUrl: '',
  driverClass: '',
  username: '',
  password: '',
  poolMaxTotal: 8,
  queryTimeout: 60,
  status: '0',
  remark: ''
})

const form = reactive<DatasourceForm>(defaultForm())
const connectionFingerprint = () => JSON.stringify([form.id, form.type, form.jdbcUrl, form.driverClass, form.username, form.password])
watch(connectionFingerprint, () => { connectionResult.value = null })

const rules = {
  name: [{ required: true, message: '名称必填', trigger: 'blur' }],
  type: [{ required: true, message: '类型必选', trigger: 'change' }],
  schemaName: [
    { required: true, message: 'Schema 别名必填', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]{0,30}$/,
      message: '只能是小写字母开头的字母数字下划线，不超过 31 字符',
      trigger: 'blur'
    }
  ],
  jdbcUrl: [
    { required: true, message: '连接串必填', trigger: 'blur' },
    { pattern: /^jdbc:/, message: '必须以 jdbc: 开头', trigger: 'blur' }
  ],
  username: [{ required: true, message: '账号必填', trigger: 'blur' }]
}

function getList() {
  loading.value = true
  listDatasources()
    .then(res => {
      let rows: BiDatasource[] = res.data || []
      recentSourceTypes.value = [...new Set([...rows].sort((a, b) =>
        (b.createTime || '').localeCompare(a.createTime || '') || b.id - a.id
      ).map(row => row.type))].slice(0, 6)
      if (queryParams.name) {
        rows = rows.filter(r => r.name.includes(queryParams.name))
      }
      if (queryParams.status) {
        rows = rows.filter(r => r.status === queryParams.status)
      }
      total.value = rows.length
      const start = (queryParams.pageNum - 1) * queryParams.pageSize
      tableData.value = rows.slice(start, start + queryParams.pageSize)
    })
    .finally(() => {
      loading.value = false
    })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.name = ''
  queryParams.status = ''
  handleQuery()
}

function resetForm() {
  activeTest++
  testing.value = false
  connectionResult.value = null
  Object.assign(form, defaultForm())
  formRef.value?.resetFields()
}

function handleAdd() {
  resetForm()
  createStep.value = 0
  sourceSearch.value = ''
  activeCategory.value = 'all'
  selectedSource.value = ''
  dialog.title = '新增数据源'
  dialog.visible = true
}

function handleUpdate(row: BiDatasource) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    name: row.name,
    type: row.type,
    schemaName: row.schemaName,
    jdbcUrl: row.jdbcUrl,
    driverClass: row.driverClass,
    username: row.username,
    password: '',
    poolMaxTotal: row.poolMaxTotal || 8,
    queryTimeout: row.queryTimeout || 60,
    status: row.status,
    remark: row.remark
  })
  dialog.title = '编辑数据源'
  dialog.visible = true
}

async function handleProbeTest() {
  if (testing.value) return
  try { await formRef.value?.validateField(['jdbcUrl', 'username']) } catch { return }
  const fingerprint = connectionFingerprint()
  const requestId = ++activeTest
  testing.value = true
  connectionResult.value = null
  try {
    const res = await probeDatasource({ ...form })
    if (requestId === activeTest && fingerprint === connectionFingerprint() && dialog.visible) {
      connectionResult.value = res.data
    }
  } catch {
    if (requestId === activeTest && fingerprint === connectionFingerprint() && dialog.visible) {
      connectionResult.value = { ok: false, costMs: 0, message: '测试未完成，请检查输入信息或稍后重试' }
    }
  } finally {
    if (requestId === activeTest) testing.value = false
  }
}

function submitForm() {
  formRef.value?.validate((valid:boolean) => {
    if (!valid) {
      return
    }
    submitting.value = true
    const payload = { ...form }
    const req = form.id ? updateDatasource(payload) : createDatasource(payload)
    req
      .then(res => {
        if (res.data?.rolledBack) {
          connectionResult.value = res.data.probe
          ElMessage.error('连接失败，配置未保存：' + (res.data?.probe?.message || ''))
          return
        }
        ElMessage.success(form.id ? '修改成功' : '新增成功')
        dialog.visible = false
        getList()
      })
      .finally(() => {
        submitting.value = false
      })
  })
}

function handleStatusChange(row: BiDatasource, status: string) {
  changeDatasourceStatus(row.id, status)
    .then(() => {
      ElMessage.success(status === '0' ? '已启用' : '已停用')
      getList()
    })
    .catch(() => {
      row.status = status === '0' ? '1' : '0'
    })
}

function handleTest(row: BiDatasource) {
  testSavedDatasource(row.schemaName)
    .then(res => {
      if (res.data?.ok) {
        ElMessage.success('连通正常 · ' + res.data.costMs + 'ms')
      } else {
        ElMessage.error('连通失败：' + (res.data?.message || ''))
      }
    })
}

function handleProbeAll() {
  probing.value = true
  probeAll()
    .then(res => {
      probeResult.value = res.data || {}
      probed.value = true
      const ok = Object.values(probeResult.value).filter(Boolean).length
      ElMessage.success('探测完成：' + ok + ' / ' + Object.keys(probeResult.value).length + ' 个数据源连通')
    })
    .finally(() => {
      probing.value = false
    })
}

function handleDelete(row: BiDatasource) {
  ElMessageBox.confirm(
    '确定删除数据源「' + row.name + '」？\n\n注意：已引用该数据源的数据集与图表需要人工确认。',
    '删除确认',
    { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
  )
    .then(() => deleteDatasource(row.id))
    .then(() => {
      ElMessage.success('删除成功')
      getList()
    })
    .catch(() => {})
}

const grantActions: Record<string, string> = { view: '查看', edit: '编辑/启停', test: '测试连接', remove: '删除', grant: '授权管理', model: '库表浏览与建模' }
const principalTypes: Record<string, string> = { user: '用户', role: '角色', organization: '部门' }
const grantDialog = reactive({ visible: false, id: 0, name: '', principalId: undefined as number | undefined, action: 'view' })
const grantPrincipals = ref<GrantPrincipal[]>([])
const resourceGrants = ref<ResourceGrant[]>([])
const grantSaving = ref(false)
let principalSearch = 0
async function loadPrincipals(query = '') {
  const sequence = ++principalSearch
  const res = await searchGrantPrincipals(query)
  if (sequence === principalSearch) grantPrincipals.value = res.data || []
}
async function reloadGrants() {
  resourceGrants.value = (await listResourceGrants(grantDialog.id)).data || []
}
async function openGrants(row: BiDatasource) {
  Object.assign(grantDialog, { visible: true, id: row.id, name: row.name, principalId: undefined, action: 'view' })
  resourceGrants.value = []
  await Promise.all([loadPrincipals(), reloadGrants()])
}
async function addGrant() {
  if (!grantDialog.principalId) return
  grantSaving.value = true
  try {
    await grantResource(grantDialog.id, grantDialog.principalId, grantDialog.action)
    await reloadGrants()
    ElMessage.success('授权成功')
  } finally { grantSaving.value = false }
}
async function removeGrant(row: ResourceGrant) {
  await revokeResource(grantDialog.id, row.principalId, row.action)
  await reloadGrants()
  ElMessage.success('已撤销授权')
}

onMounted(getList)
</script>

<style scoped>
.bi-container {
  padding-bottom: 40px;
}
.text-bold {
  font-weight: 600;
}
.text-secondary {
  color: var(--el-text-color-secondary);
  font-weight: 400;
  margin-left: 8px;
  font-size: 13px;
}
.text-danger {
  color: var(--el-color-danger);
}
.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.2;
}
.source-steps { margin: 8px auto 28px; max-width: 520px; }
.source-picker { display: flex; min-height: 360px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; overflow: hidden; }
.source-sidebar { width: 200px; flex-shrink: 0; padding: 20px 14px; border-right: 1px solid var(--el-border-color-lighter); }
.source-categories { display: grid; gap: 4px; margin-top: 18px; }
.source-category { display: flex; justify-content: space-between; align-items: center; border: 0; background: transparent; padding: 12px; border-radius: 5px; color: var(--el-text-color-regular); cursor: pointer; text-align: left; font: inherit; }
.source-category:hover { background: var(--el-fill-color-light); }
.source-category.active { color: var(--el-color-primary); background: var(--el-color-primary-light-9); font-weight: 600; }
.source-count { font-size: 12px; color: var(--el-text-color-secondary); }
.source-content { flex: 1; min-width: 0; padding: 22px; }
.source-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.source-heading strong { border-left: 3px solid var(--el-color-primary); padding-left: 9px; }
.source-heading > span { font-size: 12px; color: var(--el-text-color-secondary); }
.source-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.source-card { display: flex; position: relative; align-items: center; gap: 12px; padding: 20px 14px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); color: var(--el-text-color-primary); text-align: left; cursor: pointer; font: inherit; }
.source-card:hover, .source-card.selected { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.source-card:focus-visible, .source-category:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.source-icon { display: grid; place-items: center; width: 38px; height: 38px; flex-shrink: 0; border-radius: 8px; font-weight: 700; font-size: 15px; }
.source-card-copy { display: grid; gap: 6px; min-width: 0; }
.source-card-copy strong { font-size: 14px; overflow-wrap: anywhere; }
.source-card-copy small { font-size: 11px; color: var(--el-text-color-secondary); }
.source-check { position: absolute; right: 5px; top: 5px; color: var(--el-color-primary); }
.source-selected { margin-bottom: 20px; display: flex; gap: 12px; align-items: center; }
@media (max-width: 900px) { .source-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) {
  .source-picker { flex-direction: column; }
  .source-sidebar { width: auto; border-right: 0; border-bottom: 1px solid var(--el-border-color-lighter); }
  .source-categories { display: flex; overflow-x: auto; }
  .source-category { gap: 8px; white-space: nowrap; }
  .source-content { padding: 16px; }
  .source-grid { grid-template-columns: 1fr; }
}
.dialog-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>

