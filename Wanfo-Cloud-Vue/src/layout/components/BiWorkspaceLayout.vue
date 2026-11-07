<template>
  <div class="bi-workspace" :class="{'is-editor':isEditor}" :data-bi-theme="uiTheme">
    <header v-if="!isEditor" class="platform-header">
      <router-link to="/bi-admin/workbench" class="bi-brand"><img :src="wanfoBrandIcon" class="wanfo-brand-icon" alt=""/><span>万佛数据平台<small>WANFO DATA</small></span></router-link>
      <nav class="platform-domains" aria-label="业务模块">
        <button v-for="d in availableDomains" :key="d.id" type="button" :class="{active:domain===d.id}" :aria-current="domain===d.id?'page':undefined" @click="setDomain(d.id)">{{ d.label }}</button>
      </nav>
      <div class="platform-actions">
        <el-select :model-value="uiTheme" aria-label="平台主题" class="platform-theme" :teleported="false" @change="setBiUiTheme"><el-option v-for="t in BI_UI_THEME_OPTIONS" :key="t.id" :label="t.label" :value="t.id" /></el-select>
        <HeaderNotice class="bi-notifications" />
        <el-dropdown trigger="click" @command="handleAccountCommand"><button type="button" class="bi-account" aria-label="用户菜单"><el-avatar :src="userStore.avatar" :size="30">{{ displayName.slice(0,1) }}</el-avatar><span>{{ displayName }}</span><el-icon><ArrowDown /></el-icon></button><template #dropdown><el-dropdown-menu><el-dropdown-item command="profile">个人中心</el-dropdown-item><el-dropdown-item command="admin">管理后台</el-dropdown-item><el-dropdown-item command="logout" divided>退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
      </div>
    </header>
    <aside v-if="!isEditor" class="bi-navigation">
      <div class="platform-domain-label">{{ domainLabel }}</div>
      <nav aria-label="当前模块菜单"><template v-for="group in visibleGroups" :key="group.id"><router-link v-for="item in group.items" :key="item.path" :to="item.path" :class="['bi-nav-link',{active:activePath===item.path}]" :aria-current="activePath===item.path?'page':undefined"><el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span></router-link></template><el-menu v-if="domain==='system' || domain==='monitor'" :default-active="activePath" class="platform-system-menu" :unique-opened="true"><SidebarItem v-for="item in systemMenus.filter(r=>r.path==='/'+domain)" :key="item.path" :item="item" :base-path="item.path" /></el-menu></nav>
      <router-link to="/bi-admin/workbench" class="bi-admin-link"><el-icon><House /></el-icon><span>返回工作台</span></router-link>
    </aside>
    <div class="bi-workspace-main"><slot /></div>
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
import wanfoBrandIcon from '@/assets/bi/wanfo-brand-icon.png'
import useUserStore from '@/store/modules/user'
import usePermissionStore from '@/store/modules/permission'
import SidebarItem from './Sidebar/SidebarItem.vue'
import { checkPermi } from '@/utils/permission'
import { getBiUiTheme, setBiUiTheme, subscribeBiUiTheme, BI_UI_THEME_OPTIONS, type BiUiTheme } from '@/utils/biUiTheme'
import HeaderNotice from './HeaderNotice/index.vue'

type DomainId = 'home' | 'develop' | 'govern' | 'analyze' | 'ops' | 'system' | 'monitor'
type NavItem = { path: string; label: string; icon: unknown; permission: string }
type NavGroup = { id: string; label: string; items: NavItem[] }

const DOMAIN_KEY = 'bi-workspace-domain'
const domains: {id:DomainId;label:string}[] = [{id:'home',label:'工作台'},{id:'develop',label:'数据开发'},{id:'govern',label:'资产治理'},{id:'analyze',label:'分析应用'},{id:'ops',label:'运维服务'},{id:'system',label:'系统管理'},{id:'monitor',label:'系统监控'}]

const router = useRouter()
const route = useRoute()
const activePath = computed(() => String(route.meta.activeMenu || route.path))
const isEditor = computed(() => Boolean(route.meta.workspaceEditor))
const userStore = useUserStore()
const permissionStore=usePermissionStore()
const systemMenus=computed(()=>permissionStore.sidebarRouters.filter((r:any)=>!r.hidden && ['/system','/monitor'].includes(r.path) && r.children?.some((c:any)=>!c.hidden)))
const displayName = computed(() => userStore.nickName || userStore.name || '用户')
const uiTheme = ref<BiUiTheme>(getBiUiTheme())
let unsubscribeTheme: (() => void) | undefined
let previousBodyTheme:string|null=null
watch(uiTheme,t=>{document.body.setAttribute('data-bi-ui-theme',t)})
onMounted(() => {
  previousBodyTheme=document.body.getAttribute('data-bi-ui-theme')
  document.body.setAttribute('data-bi-ui-theme',uiTheme.value)
  uiTheme.value = getBiUiTheme()
  unsubscribeTheme = subscribeBiUiTheme((t) => { uiTheme.value = t })
})
onUnmounted(() => { unsubscribeTheme?.(); if(previousBodyTheme===null)document.body.removeAttribute('data-bi-ui-theme');else document.body.setAttribute('data-bi-ui-theme',previousBodyTheme) })

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

const catalogItems = [...analyzeGroups,...platformGroups].flatMap(g=>g.items)
function section(id:string, paths:string[]): NavGroup[] {return [{id,label:'',items:paths.map(path=>catalogItems.find(i=>i.path===`/bi-admin/${path}`)).filter((i):i is NavItem=>Boolean(i))}]}
const domainCatalog:Record<DomainId,NavGroup[]>={
 home:section('home',['workbench','task-center']),
 system:[],monitor:[],
 develop:section('develop',['datasource','dataset','pipeline','warehouse']),
 govern:section('govern',['catalog','lineage','field-standard','glossary','access-requests']),
 analyze:section('analyze',['chart','dashboard','metric','metric-set','template','form']),
 ops:section('ops',['task-center','query-monitor','data-api','subscription','export','backup-drills'])
}
const availableDomains=computed(()=>domains.filter(d=>d.id==='system'||d.id==='monitor'?systemMenus.value.some(r=>r.path==='/'+d.id):filterGroups(domainCatalog[d.id]).length>0))

function pathDomain(path: string): DomainId | null {
  if(path==='/system'||path.startsWith('/system/'))return 'system'
  if(path==='/monitor'||path.startsWith('/monitor/'))return 'monitor'
  if(path==='/bi-admin/workbench')return 'home'
  for (const id of domains.filter(d=>d.id!=='home').map(d => d.id)) {
    if (domainCatalog[id].some(g => g.items.some(i => i.path === path))) return id
  }
  return null
}

function readStoredDomain(): DomainId {
  let v:string|null=null
  try {v=localStorage.getItem(DOMAIN_KEY)}catch{/* unavailable browser storage */}
  return v==='platform'?'develop':domains.some(d=>d.id===v)?v as DomainId:'home'
}

const domain = ref<DomainId>(readStoredDomain())

function setDomain(id: DomainId) {
  domain.value = id
  try {localStorage.setItem(DOMAIN_KEY,id)}catch{/* navigation remains available */}
  if(id==='system'||id==='monitor'){
    const root=systemMenus.value.find(r=>r.path==='/'+id)
    function firstLeaf(node:any,base:string):string|undefined{for(const child of node.children||[]){if(child.hidden)continue;const path=child.path.startsWith('/')?child.path:base.replace(/\/$/,'')+'/'+child.path;const found=child.children?.length?firstLeaf(child,path):path;if(found)return found}}
    const target=root&&firstLeaf(root,root.path);if(target&&pathDomain(activePath.value)!==id)router.push(target);return
  }
  const first=filterGroups(domainCatalog[id])[0]?.items[0]?.path
  if(first && (pathDomain(activePath.value)!==id || id==='home')) router.push(first)

}

watch(activePath, (path) => {
  const d = pathDomain(path)
  if (d && d !== domain.value) {
    domain.value = d
    try {localStorage.setItem(DOMAIN_KEY,d)}catch{/* navigation remains available */}
  }
}, { immediate: true })

const visibleGroups = computed(() => filterGroups(domainCatalog[domain.value]))
const domainLabel = computed(() => domains.find(d => d.id === domain.value)?.label || '工作台')
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
.bi-workspace{--bi-content-height:calc(100dvh - 70px);min-height:100dvh;padding-top:70px;color:var(--bi-text);background:var(--bi-background);font-family:var(--bi-font)}.platform-header{position:fixed;inset:0 0 auto;height:70px;z-index:30;display:flex;align-items:center;gap:24px;padding:0 24px;border-bottom:1px solid var(--bi-border);background:var(--bi-surface)}.bi-brand{display:flex;align-items:center;gap:10px;font-size:18px;font-weight:700;color:var(--bi-text);padding-right:25px;border-right:1px solid var(--bi-border);white-space:nowrap}.bi-brand>.el-icon{font-size:30px;color:var(--el-color-primary)}.bi-brand small{display:block;font-size:9px;letter-spacing:2px;font-weight:400;color:var(--bi-muted)}.platform-domains{height:100%;display:flex;gap:12px}.platform-domains button{background:none;border:0;border-bottom:3px solid transparent;color:var(--bi-muted);font:inherit;font-size:15px;padding:0 12px;white-space:nowrap;cursor:pointer}.platform-domains button.active{color:var(--el-color-primary);font-weight:600;border-bottom-color:var(--el-color-primary)}.platform-actions{display:flex;align-items:center;gap:20px;margin-left:auto}.platform-theme{width:130px}.bi-account{display:flex;align-items:center;gap:8px;background:none;color:var(--bi-text);border:0;cursor:pointer;font:inherit}.bi-navigation{position:fixed;inset:70px auto 0 0;width:210px;display:flex;flex-direction:column;padding-top:22px;border-right:1px solid var(--bi-border);background:var(--bi-surface);z-index:20}.platform-domain-label{padding:0 22px 12px;color:var(--bi-muted);font-size:11px}.bi-navigation nav{padding:8px 12px;overflow:auto;flex:1}.bi-nav-link{display:flex;align-items:center;gap:12px;min-height:44px;padding:0 14px;margin:3px 0;border-radius:6px;color:var(--bi-muted);font-size:14px}.bi-nav-link>.el-icon{font-size:19px}.bi-nav-link.active,.bi-nav-link:hover{background:var(--el-color-primary-light-9);color:var(--el-color-primary)}.bi-admin-link{display:flex;gap:12px;padding:22px;border-top:1px solid var(--bi-border);color:var(--bi-muted)}.bi-workspace-main{margin-left:210px;min-width:0}.bi-workspace :deep(.app-main){min-height:var(--bi-content-height);overflow:visible;padding-bottom:0}.bi-workspace :deep(.app-main>.copyright){display:none}.bi-workspace.is-editor{padding-top:0;--bi-content-height:100dvh}.is-editor .bi-workspace-main{margin-left:0}.platform-domains button:focus-visible,.bi-nav-link:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:2px}
@media(max-width:1200px){.platform-header{gap:12px;padding:0 14px}.platform-domains{gap:0}.platform-domains button{font-size:13px;padding:0 9px}.bi-brand{font-size:15px;padding-right:14px}.platform-actions{gap:10px}.bi-account>span{display:none}}@media(max-width:850px){.bi-brand span{display:none}.platform-theme{width:105px}.bi-navigation{width:160px}.bi-workspace-main{margin-left:160px}.platform-domains button{padding:0 8px}}@media(max-width:650px){.platform-header{height:98px;flex-wrap:wrap;padding:8px 12px;gap:5px}.bi-brand{display:none}.platform-domains{height:36px;order:2;width:100%;overflow:auto}.platform-actions{margin-left:auto}.bi-workspace{padding-top:98px;--bi-content-height:calc(100dvh - 98px)}.bi-navigation{inset:98px 0 auto;width:auto;padding:0;border-right:0}.platform-domain-label,.bi-admin-link{display:none}.bi-navigation nav{display:flex;padding:5px 10px;overflow:auto;flex:none;border-bottom:1px solid var(--bi-border)}.bi-nav-link{white-space:nowrap;min-height:35px;padding:0 11px;margin:0}.bi-workspace-main{margin-left:0;padding-top:46px}.is-editor .bi-workspace-main{padding-top:0}}
</style>

<style lang="scss" src="../../views/bi/components/BiTheme.scss"></style>

<style scoped>.platform-system-menu{--el-menu-bg-color:var(--bi-surface);--el-menu-text-color:var(--bi-muted);--el-menu-hover-bg-color:var(--bi-soft);--el-menu-active-color:var(--el-color-primary);border:0;margin-top:14px;padding-top:10px;border-top:1px solid var(--bi-border)}.platform-system-menu :deep(.el-sub-menu__title){height:44px;border-radius:6px;padding-left:14px!important;font-size:14px}.platform-system-menu :deep(.el-menu-item){min-width:0;height:40px;border-radius:6px;font-size:13px}.platform-system-menu :deep(.nav-icon){width:18px;margin-right:12px}.platform-system-menu :deep(.is-active.el-menu-item){background:var(--el-color-primary-light-9)}</style>


<style scoped>.wanfo-brand-icon{width:38px;height:38px;object-fit:contain;flex-shrink:0}</style>

