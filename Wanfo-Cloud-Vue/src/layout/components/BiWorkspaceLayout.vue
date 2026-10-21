<template>
  <div class="bi-workspace">
    <aside class="bi-navigation">
      <router-link :to="homePath" class="bi-brand" aria-label="数据中心首页">
        <el-icon><Histogram /></el-icon><span>数据中心</span>
      </router-link>
      <nav aria-label="数据中心导航">
        <router-link v-for="item in navigation" :key="item.path" :to="item.path"
          :class="['bi-nav-link', { active: activePath === item.path }]" :aria-current="activePath === item.path ? 'page' : undefined">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
        </router-link>
      </nav>
      <router-link to="/index" class="bi-admin-link"><el-icon><Back /></el-icon><span>返回管理后台</span></router-link>
    </aside>
    <div class="bi-workspace-main">
      <header class="bi-topbar">
        <span class="bi-mobile-brand"><el-icon><Histogram /></el-icon>数据中心</span>
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { House, Grid, Coin, Files, Histogram, Collection, TrendCharts, Document, Download, Back, ArrowDown, User, Setting, SwitchButton } from '@element-plus/icons-vue'
import useUserStore from '@/store/modules/user'
import { checkPermi } from '@/utils/permission'
import HeaderNotice from './HeaderNotice/index.vue'

const router = useRouter()
const route = useRoute()
const activePath = computed(()=>String(route.meta.activeMenu || route.path))
const userStore = useUserStore()
const displayName = computed(() => userStore.nickName || userStore.name || '用户')
const items = [
  { path: '/bi-admin/workbench', label: '工作台', icon: House, permission: '' },
  { path: '/bi-admin/dashboard', label: '看板管理', icon: Grid, permission: 'bi:dashboard:list' },
  { path: '/bi-admin/datasource', label: '数据源', icon: Coin, permission: 'bi:datasource:list' },
  { path: '/bi-admin/dataset', label: '数据集', icon: Files, permission: 'bi:dataset:list' },
  { path: '/bi-admin/chart', label: '图表', icon: Histogram, permission: 'bi:chart:list' },
  { path: '/bi-admin/template', label: '模板中心', icon: Collection, permission: 'bi:template:list' },
  { path: '/bi-admin/metric', label: '数据指标', icon: TrendCharts, permission: 'bi:metric:list' },
  { path: '/bi-admin/form', label: '填报管理', icon: Document, permission: 'bi:form:list' },
  { path: '/bi-admin/export', label: '导出中心', icon: Download, permission: 'bi:export:list' }
]
const navigation = computed(() => items.filter(item =>
  router.getRoutes().some(r => r.path === item.path) && (!item.permission || checkPermi([item.permission]))
))
const homePath = computed(() => navigation.value[0]?.path || '/index')
async function handleAccountCommand(command: string) {
  if (command === 'profile') return router.push('/user/profile')
  if (command === 'admin') return router.push('/index')
  if (command !== 'logout') return
  try { await ElMessageBox.confirm('确定退出登录吗？', '退出登录', { confirmButtonText: '退出登录', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  await userStore.logOut()
  window.location.href = '/login'
}
</script>

<style scoped>
.bi-workspace{--bi-content-height:calc(100dvh - 64px);--el-color-primary:#3563e9;--el-color-primary-light-3:#7292f0;--el-color-primary-light-5:#9ab1f4;--el-color-primary-light-7:#c2d0f8;--el-color-primary-light-8:#d7e0fb;--el-color-primary-light-9:#edf2ff;--el-color-primary-dark-2:#2a4fba;--el-border-radius-base:6px;--bi-surface:#fff;--bi-background:#f6f8fc;--bi-text:#182230;--bi-muted:#667085;--bi-border:#e6ebf3;display:flex;min-height:100dvh;color:var(--bi-text);background:var(--bi-background);font-family:var(--bi-font)}
.bi-navigation{padding:0;margin:0;line-height:normal;font-family:inherit;border-radius:0;width:190px;flex-shrink:0;background:var(--bi-surface);border-right:1px solid var(--bi-border);position:fixed;inset:0 auto 0 0;z-index:20;display:flex;flex-direction:column}
.bi-brand{height:64px;display:flex;align-items:center;gap:12px;padding:0 24px;border-bottom:1px solid var(--bi-border);font-weight:700;font-size:20px;color:var(--bi-text);white-space:nowrap}
.bi-brand>.el-icon{font-size:30px;color:var(--el-color-primary)}
.bi-navigation nav{padding:28px 8px;overflow-y:auto;flex:1}
.bi-nav-link{display:flex;align-items:center;gap:16px;min-height:52px;padding:0 20px;margin:3px 0;border-radius:6px;color:#526079;font-size:16px;position:relative;transition:background .15s,color .15s}
.bi-nav-link>span{white-space:nowrap}.bi-nav-link>.el-icon{font-size:20px;flex-shrink:0}.bi-nav-link:hover{background:#f6f8fc;color:var(--el-color-primary)}
.bi-nav-link.active{background:#edf3ff;color:var(--el-color-primary);font-weight:600}.bi-nav-link.active:before{content:'';position:absolute;width:3px;left:-8px;top:0;bottom:0;background:var(--el-color-primary)}
.bi-admin-link{display:flex;gap:12px;align-items:center;font-size:12px;color:var(--bi-muted);margin:16px 24px 24px;min-height:32px}.bi-admin-link:hover{color:var(--el-color-primary)}
.bi-workspace-main{flex:1;min-width:0;margin-left:190px}.bi-topbar{height:64px;border-bottom:1px solid var(--bi-border);display:flex;align-items:center;justify-content:flex-end;padding:0 32px;background:var(--bi-surface)}.bi-topbar-actions{display:flex;align-items:center;gap:20px}
.bi-account{display:flex;align-items:center;gap:10px;border:0;background:transparent;color:var(--bi-text);font:inherit;font-size:14px;cursor:pointer;padding:5px;border-radius:6px}.bi-account>.el-icon{font-size:12px;color:var(--bi-muted)}.bi-mobile-brand{display:none}
.bi-notifications :deep(.notice-trigger){display:grid;place-items:center;width:32px;height:32px;font-size:19px;color:#526079;cursor:pointer;border-radius:6px}.bi-notifications :deep(.notice-trigger:hover){background:var(--el-color-primary-light-9);color:var(--el-color-primary)}
.bi-workspace :deep(.app-main){min-height:calc(100dvh - 64px);overflow:visible;padding-bottom:0}.bi-workspace :deep(.app-main>.copyright){display:none}
.bi-account:focus-visible,.bi-nav-link:focus-visible,.bi-brand:focus-visible,.bi-admin-link:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:2px}
:global(html.dark) .bi-workspace{--bi-surface:#1c2432;--bi-background:#121a27;--bi-text:#e8edf5;--bi-muted:#a0aec0;--bi-border:#303c50}:global(html.dark) .bi-nav-link{color:#b5c2d5}:global(html.dark) .bi-nav-link.active,:global(html.dark) .bi-nav-link:hover{background:#283a60;color:#a8c1ff}
@media(min-width:1600px){.bi-navigation{width:210px}.bi-workspace-main{margin-left:210px}}
@media(max-width:1100px) and (min-width:701px){.bi-navigation{width:166px}.bi-workspace-main{margin-left:166px}.bi-brand{font-size:18px;padding:0 18px;gap:8px}.bi-nav-link{padding:0 15px;gap:12px}}
@media(max-width:700px){.bi-workspace{--bi-content-height:calc(100dvh - 106px);display:block}.bi-navigation{width:auto;position:static;border-right:0}.bi-brand,.bi-admin-link{display:none}.bi-navigation nav{display:flex;overflow-x:auto;padding:6px 12px;border-bottom:1px solid var(--bi-border)}.bi-nav-link{white-space:nowrap;gap:7px;min-height:38px;padding:0 12px;margin:0}.bi-nav-link>.el-icon{font-size:17px}.bi-nav-link.active:before{display:none}.bi-workspace-main{margin-left:0}.bi-topbar{height:54px;padding:0 16px;justify-content:space-between}.bi-mobile-brand{display:flex;align-items:center;gap:8px;font-size:16px;font-weight:700}.bi-mobile-brand>.el-icon{color:var(--el-color-primary);font-size:22px}.bi-workspace :deep(.app-main){min-height:calc(100dvh - 106px)}}
@media(prefers-reduced-motion:reduce){.bi-nav-link{transition:none}}
</style>

<style lang="scss" src="../../views/bi/components/BiTheme.scss"></style>
