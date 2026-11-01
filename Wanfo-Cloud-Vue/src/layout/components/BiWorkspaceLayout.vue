<template>
  <div class="bi-workspace" :class="{ 'is-editor': isEditor }" :data-bi-theme="uiTheme">
    <aside v-if="!isEditor" class="bi-navigation">
      <div class="bi-brand-block">
        <router-link :to="homePath" class="bi-brand" aria-label="工作台首页">
          <el-icon><Histogram /></el-icon><span>万佛数据</span>
        </router-link>
        <div class="bi-domain-switch" role="tablist" aria-label="产品域">
          <button
            v-for="d in domains"
            :key="d.id"
            type="button"
            role="tab"
            class="bi-domain-btn"
            :class="{ active: domain === d.id }"
            :aria-selected="domain === d.id"
            @click="setDomain(d.id)"
          >{{ d.label }}</button>
        </div>
      </div>
      <nav :aria-label="domainLabel + '导航'">
        <template v-for="group in visibleGroups" :key="group.id">
          <div v-if="group.label" class="bi-nav-group-label">{{ group.label }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            :class="['bi-nav-link', { active: activePath === item.path }]"
            :aria-current="activePath === item.path ? 'page' : undefined"
          >
            <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
          </router-link>
        </template>
      </nav>
      <router-link to="/index" class="bi-admin-link"><el-icon><Back /></el-icon><span>返回管理后台</span></router-link>
    </aside>
    <div class="bi-workspace-main">
      <header v-if="!isEditor" class="bi-topbar">
        <span class="bi-mobile-brand"><el-icon><Histogram /></el-icon>{{ domainLabel }}</span>
        <div class="bi-mobile-domain" role="tablist" aria-label="产品域">
          <button
            v-for="d in domains"
            :key="'m-' + d.id"
            type="button"
            class="bi-domain-btn"
            :class="{ active: domain === d.id }"
            @click="setDomain(d.id)"
          >{{ d.label }}</button>
        </div>
        <div class="bi-topbar-actions">
          <HeaderNotice class="bi-notifications" />
          <el-dropdown trigger="click" @command="handleAccountCommand">
            <button type="button" class="bi-account" aria-label="用户菜单">
              <el-avatar :src="userStore.avatar" :size="30">{{ displayName.slice(0, 1) }}</el-avatar>
              <span>{{ displayName }}</span><el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item command="profile" :icon="User">个人中心</el-dropdown-item>
              <el-dropdown-item command="admin" :icon="Setting">管理后台</el-dropdown-item>
              <el-dropdown-item command="logout" divided :icon="SwitchButton">退出登录</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </header>
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  House, Grid, Coin, Files, Histogram, Collection, TrendCharts, Document, Download, List,
  DataLine, Back, ArrowDown, User, Setting, SwitchButton, Connection, Link, Reading, Monitor, Share, Key
} from '@element-plus/icons-vue'
import useUserStore from '@/store/modules/user'
import { checkPermi } from '@/utils/permission'
import { getBiUiTheme, subscribeBiUiTheme, type BiUiTheme } from '@/utils/biUiTheme'
import HeaderNotice from './HeaderNotice/index.vue'

type DomainId = 'analyze' | 'platform'
type NavItem = { path: string; label: string; icon: unknown; permission: string }
type NavGroup = { id: string; label: string; items: NavItem[] }

const DOMAIN_KEY = 'bi-workspace-domain'
const domains = [
  { id: 'analyze' as const, label: '分析' },
  { id: 'platform' as const, label: '数据中心' }
]

const router = useRouter()
const route = useRoute()
const activePath = computed(() => String(route.meta.activeMenu || route.path))
const isEditor = computed(() => Boolean(route.meta.workspaceEditor))
const userStore = useUserStore()
const displayName = computed(() => userStore.nickName || userStore.name || '用户')
const uiTheme = ref<BiUiTheme>(getBiUiTheme())
let unsubscribeTheme: (() => void) | undefined
onMounted(() => {
  uiTheme.value = getBiUiTheme()
  unsubscribeTheme = subscribeBiUiTheme((t) => { uiTheme.value = t })
})
onUnmounted(() => { unsubscribeTheme?.() })

const analyzeGroups: NavGroup[] = [
  {
    id: 'hub',
    label: '',
    items: [{ path: '/bi-admin/workbench', label: '工作台', icon: House, permission: '' }]
  },
  {
    id: 'visual',
    label: '看板与图表',
    items: [
      { path: '/bi-admin/dashboard', label: '看板管理', icon: Grid, permission: 'bi:dashboard:list' },
      { path: '/bi-admin/chart', label: '图表', icon: Histogram, permission: 'bi:chart:list' },
      { path: '/bi-admin/template', label: '模板中心', icon: Collection, permission: 'bi:template:list' }
    ]
  },
  {
    id: 'metric',
    label: '指标与填报',
    items: [
      { path: '/bi-admin/metric', label: '数据指标', icon: TrendCharts, permission: 'bi:metric:list' },
      { path: '/bi-admin/metric-set', label: '指标集', icon: Collection, permission: 'bi:metric:list' },
      { path: '/bi-admin/form', label: '填报管理', icon: Document, permission: 'bi:form:list' },
      { path: '/bi-admin/subscription', label: '报表订阅', icon: Document, permission: 'bi:subscription:list' }
    ]
  },
  {
    id: 'delivery',
    label: '交付',
    items: [
      { path: '/bi-admin/export', label: '导出中心', icon: Download, permission: 'bi:export:list' }
    ]
  }
]

const platformGroups: NavGroup[] = [
  {
    id: 'hub',
    label: '',
    items: [{ path: '/bi-admin/workbench', label: '工作台', icon: House, permission: '' }]
  },
  {
    id: 'asset',
    label: '资产',
    items: [
      { path: '/bi-admin/catalog', label: '数据资产目录', icon: DataLine, permission: 'bi:datasource:model' },
      { path: '/bi-admin/datasource', label: '数据源', icon: Coin, permission: 'bi:datasource:list' },
      { path: '/bi-admin/dataset', label: '数据集', icon: Files, permission: 'bi:dataset:list' },
      { path: '/bi-admin/lineage', label: '数据血缘', icon: Share, permission: 'bi:resource:admin' }
    ]
  },
  {
    id: 'build',
    label: '加工与装载',
    items: [
      { path: '/bi-admin/pipeline', label: '数据加工', icon: Connection, permission: 'bi:pipeline:list' },
      { path: '/bi-admin/warehouse', label: '数仓装载', icon: Monitor, permission: 'bi:resource:admin' },
      { path: '/bi-admin/task-center', label: '任务中心', icon: List, permission: 'bi:task:list' }
    ]
  },
  {
    id: 'semantic',
    label: '语义与开放',
    items: [
      { path: '/bi-admin/glossary', label: '业务术语表', icon: Reading, permission: 'bi:resource:admin' },
      { path: '/bi-admin/field-standard', label: '字段标准', icon: Key, permission: 'bi:resource:admin' },
      { path: '/bi-admin/data-api', label: '开放数据API', icon: Link, permission: 'bi:metric:list' }
    ]
  },
  {
    id: 'govern',
    label: '治理与运维',
    items: [
      { path: '/bi-admin/access-requests', label: '访问申请', icon: Document, permission: 'bi:datasource:model' },
      { path: '/bi-admin/query-monitor', label: '查询监控', icon: TrendCharts, permission: 'bi:resource:admin' },
      { path: '/bi-admin/backup-drills', label: '备份与演练', icon: Monitor, permission: 'bi:resource:admin' }
    ]
  }
]

function routeExists(path: string) {
  return router.getRoutes().some(r => r.path === path)
}

function filterGroups(groups: NavGroup[]): NavGroup[] {
  return groups
    .map(g => ({
      ...g,
      items: g.items.filter(item =>
        routeExists(item.path) && (!item.permission || checkPermi([item.permission]))
      )
    }))
    .filter(g => g.items.length > 0)
}

const domainCatalog: Record<DomainId, NavGroup[]> = {
  analyze: analyzeGroups,
  platform: platformGroups
}

function pathDomain(path: string): DomainId | null {
  if (path === '/bi-admin/workbench') return null
  for (const id of domains.map(d => d.id)) {
    if (domainCatalog[id].some(g => g.items.some(i => i.path === path))) return id
  }
  return null
}

function readStoredDomain(): DomainId {
  const v = localStorage.getItem(DOMAIN_KEY)
  return v === 'platform' || v === 'analyze' ? v : 'analyze'
}

const domain = ref<DomainId>(readStoredDomain())

function setDomain(id: DomainId) {
  domain.value = id
  localStorage.setItem(DOMAIN_KEY, id)
  if (activePath.value !== '/bi-admin/workbench' && pathDomain(activePath.value) !== id) {
    const first = filterGroups(domainCatalog[id])[0]?.items[0]?.path
    if (first) router.push(first)
  }
}

watch(activePath, (path) => {
  const d = pathDomain(path)
  if (d && d !== domain.value) {
    domain.value = d
    localStorage.setItem(DOMAIN_KEY, d)
  }
}, { immediate: true })

const visibleGroups = computed(() => filterGroups(domainCatalog[domain.value]))
const domainLabel = computed(() => domains.find(d => d.id === domain.value)?.label || '分析')
const homePath = computed(() => {
  const hub = visibleGroups.value.flatMap(g => g.items).find(i => i.path === '/bi-admin/workbench')
  return hub?.path || visibleGroups.value[0]?.items[0]?.path || '/index'
})

async function handleAccountCommand(command: string) {
  if (command === 'profile') return router.push('/user/profile')
  if (command === 'admin') return router.push('/index')
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '退出登录', {
      confirmButtonText: '退出登录', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  await userStore.logOut()
  window.location.href = '/login'
}
</script>

<style scoped>
.bi-workspace{
  --bi-content-height:calc(100dvh - 64px);
  --el-color-primary:#3563e9;--el-color-primary-light-3:#7292f0;--el-color-primary-light-5:#9ab1f4;
  --el-color-primary-light-7:#c2d0f8;--el-color-primary-light-8:#d7e0fb;--el-color-primary-light-9:#edf2ff;
  --el-color-primary-dark-2:#2a4fba;--el-border-radius-base:6px;
  --bi-surface:#fff;--bi-background:#f6f8fc;--bi-text:#182230;--bi-muted:#667085;--bi-border:#e6ebf3;
  display:flex;min-height:100dvh;color:var(--bi-text);background:var(--bi-background);font-family:var(--bi-font)
}
.bi-workspace.is-editor{--bi-content-height:100dvh}
.bi-workspace.is-editor .bi-workspace-main{margin-left:0}
.bi-navigation{
  padding:0;margin:0;line-height:normal;font-family:inherit;border-radius:0;width:200px;flex-shrink:0;
  background:var(--bi-surface);border-right:1px solid var(--bi-border);position:fixed;inset:0 auto 0 0;z-index:20;
  display:flex;flex-direction:column
}
.bi-brand-block{border-bottom:1px solid var(--bi-border);padding-bottom:10px}
.bi-brand{height:56px;display:flex;align-items:center;gap:10px;padding:0 18px;font-weight:700;font-size:18px;color:var(--bi-text);white-space:nowrap}
.bi-brand>.el-icon{font-size:26px;color:var(--el-color-primary)}
.bi-domain-switch{display:grid;grid-template-columns:1fr 1fr;gap:4px;margin:0 12px;padding:3px;background:var(--bi-background);border-radius:8px}
.bi-domain-btn{
  border:0;background:transparent;border-radius:6px;height:30px;font:inherit;font-size:13px;color:var(--bi-muted);
  cursor:pointer;transition:background .15s,color .15s,box-shadow .15s
}
.bi-domain-btn.active{background:var(--bi-surface);color:var(--el-color-primary);font-weight:600;box-shadow:0 1px 2px rgba(24,34,48,.08)}
.bi-domain-btn:hover:not(.active){color:var(--bi-text)}
.bi-navigation nav{padding:12px 8px 20px;overflow-y:auto;flex:1}
.bi-nav-group-label{
  margin:14px 12px 6px;font-size:11px;font-weight:600;letter-spacing:.04em;color:var(--bi-muted);text-transform:none;user-select:none
}
.bi-nav-link{
  display:flex;align-items:center;gap:12px;min-height:var(--bi-nav-item-height, 40px);padding:0 14px;margin:2px 0;border-radius:6px;
  color:#526079;font-size:14px;position:relative;transition:background .15s,color .15s
}
.bi-nav-link>span{white-space:nowrap}.bi-nav-link>.el-icon{font-size:18px;flex-shrink:0}
.bi-nav-link:hover{background:#f6f8fc;color:var(--el-color-primary)}
.bi-nav-link.active{background:#edf3ff;color:var(--el-color-primary);font-weight:600}
.bi-nav-link.active:before{content:'';position:absolute;width:3px;left:-8px;top:0;bottom:0;background:var(--el-color-primary)}
.bi-admin-link{display:flex;gap:12px;align-items:center;font-size:12px;color:var(--bi-muted);margin:12px 18px 20px;min-height:32px}
.bi-admin-link:hover{color:var(--el-color-primary)}
.bi-workspace-main{flex:1;min-width:0;margin-left:200px}
.bi-topbar{height:64px;border-bottom:1px solid var(--bi-border);display:flex;align-items:center;justify-content:flex-end;padding:0 28px;background:var(--bi-surface);gap:16px}
.bi-topbar-actions{display:flex;align-items:center;gap:20px;margin-left:auto}
.bi-account{display:flex;align-items:center;gap:10px;border:0;background:transparent;color:var(--bi-text);font:inherit;font-size:14px;cursor:pointer;padding:5px;border-radius:6px}
.bi-account>.el-icon{font-size:12px;color:var(--bi-muted)}
.bi-mobile-brand,.bi-mobile-domain{display:none}
.bi-notifications :deep(.notice-trigger){display:grid;place-items:center;width:32px;height:32px;font-size:19px;color:#526079;cursor:pointer;border-radius:6px}
.bi-notifications :deep(.notice-trigger:hover){background:var(--el-color-primary-light-9);color:var(--el-color-primary)}
.bi-workspace :deep(.app-main){min-height:calc(100dvh - 64px);overflow:visible;padding-bottom:0}
.bi-workspace.is-editor :deep(.app-main){min-height:100dvh}
.bi-workspace :deep(.app-main>.copyright){display:none}
.bi-account:focus-visible,.bi-nav-link:focus-visible,.bi-brand:focus-visible,.bi-admin-link:focus-visible,.bi-domain-btn:focus-visible{
  outline:2px solid var(--el-color-primary);outline-offset:2px
}
:global(html.dark) .bi-workspace{--bi-surface:#1c2432;--bi-background:#121a27;--bi-text:#e8edf5;--bi-muted:#a0aec0;--bi-border:#303c50}
:global(html.dark) .bi-nav-link{color:#b5c2d5}
:global(html.dark) .bi-nav-link.active,:global(html.dark) .bi-nav-link:hover{background:#283a60;color:#a8c1ff}
:global(html.dark) .bi-domain-switch{background:#121a27}
:global(html.dark) .bi-domain-btn.active{background:#283a60;color:#a8c1ff}
@media(min-width:1600px){.bi-navigation{width:220px}.bi-workspace-main{margin-left:220px}}
@media(max-width:1100px) and (min-width:701px){
  .bi-navigation{width:176px}.bi-workspace-main{margin-left:176px}
  .bi-brand{font-size:16px;padding:0 14px}.bi-nav-link{padding:0 12px;gap:10px}
}
@media(max-width:700px){
  .bi-workspace{--bi-content-height:calc(100dvh - 106px);display:block}
  .bi-navigation{width:auto;position:static;border-right:0}
  .bi-brand-block .bi-brand,.bi-brand-block .bi-domain-switch,.bi-admin-link{display:none}
  .bi-brand-block{border:0;padding:0}
  .bi-navigation nav{display:flex;overflow-x:auto;padding:6px 12px;border-bottom:1px solid var(--bi-border);gap:2px}
  .bi-nav-group-label{display:none}
  .bi-nav-link{white-space:nowrap;gap:7px;min-height:38px;padding:0 12px;margin:0}
  .bi-nav-link>.el-icon{font-size:17px}
  .bi-nav-link.active:before{display:none}
  .bi-workspace-main{margin-left:0}
  .bi-topbar{height:54px;padding:0 12px;justify-content:flex-start;flex-wrap:wrap;row-gap:6px}
  .bi-mobile-brand{display:flex;align-items:center;gap:8px;font-size:15px;font-weight:700}
  .bi-mobile-brand>.el-icon{color:var(--el-color-primary);font-size:20px}
  .bi-mobile-domain{display:grid;grid-template-columns:1fr 1fr;gap:3px;padding:2px;background:var(--bi-background);border-radius:8px;min-width:140px}
  .bi-mobile-domain .bi-domain-btn{height:28px;font-size:12px}
  .bi-workspace :deep(.app-main){min-height:calc(100dvh - 106px)}
}
@media(prefers-reduced-motion:reduce){.bi-nav-link,.bi-domain-btn{transition:none}}
</style>

<style lang="scss" src="../../views/bi/components/BiTheme.scss"></style>
