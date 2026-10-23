<template>
  <main class="query-monitor">
    <el-result v-if="!authorized" icon="warning" title="无查询监控权限" sub-title="仅 BI 资源管理员可查看和停止查询"/>
    <template v-else>
      <header><div><h1>查询监控</h1><p>查看查询负载与慢查询记录，通过排查编号定位问题。</p></div><div class="actions"><el-switch v-model="autoRefresh" active-text="每 5 秒刷新"/><el-button :loading="loading" @click="load">刷新</el-button></div></header>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon/>
      <template v-if="data">
        <div class="stats"><article><span>运行中</span><strong>{{data.active}}<small> / {{data.limits.maxActive}}</small></strong></article><article><span>排队中</span><strong>{{data.waiting}}<small> / {{data.limits.maxWaiting}}</small></strong></article><article><span>单用户并发上限</span><strong>{{data.limits.perUser}}</strong></article><article><span>单数据源并发上限</span><strong>{{data.limits.perSource}}</strong></article></div>
        <el-alert :title="data.scope==='cluster'?'集群视图：展示共享 Redis 协调范围内的查询；保留最近 200 条完成记录，闲置 24 小时后过期。':'单实例视图：当前服务最近 200 条记录，重启后清空。'" type="info" :closable="false"/>
        <section><h2>运行中查询</h2><el-table :data="data.running" row-key="requestId" empty-text="当前没有运行中的查询"><el-table-column prop="requestId" label="排查编号" min-width="285"/><el-table-column label="来源" width="110"><template #default="{row}">{{sourceName(row.source)}}</template></el-table-column><el-table-column label="已用时" width="120"><template #default="{row}">{{row.durationMs}} ms</template></el-table-column><el-table-column prop="datasourceCount" label="数据源数" width="105"/><el-table-column label="状态" width="130"><template #default="{row}"><el-tag :type="row.slow?'warning':'info'">{{statusName(row.status)}}{{row.slow?' · 慢':''}}</el-tag></template></el-table-column><el-table-column label="操作" width="110"><template #default="{row}"><el-button link type="danger" :disabled="row.status!=='running'" :loading="cancelling===row.requestId" @click="stop(row)">停止查询</el-button></template></el-table-column></el-table></section>
        <section><div class="section-title"><h2>最近查询</h2><el-checkbox v-model="onlySlow">仅慢查询</el-checkbox></div><el-table :data="recent" empty-text="暂无符合条件的记录"><el-table-column prop="requestId" label="排查编号" min-width="285"/><el-table-column label="来源" width="110"><template #default="{row}">{{sourceName(row.source)}}</template></el-table-column><el-table-column label="开始时间" min-width="180"><template #default="{row}">{{new Date(row.startedAt).toLocaleString()}}</template></el-table-column><el-table-column label="耗时" width="120"><template #default="{row}">{{row.durationMs}} ms</template></el-table-column><el-table-column label="结果" width="150"><template #default="{row}">{{statusName(row.status)}}{{row.slow?' · 慢查询':''}}</template></el-table-column></el-table></section>
        <p class="privacy">慢查询阈值 {{data.limits.slowMillis}} ms。记录不包含 SQL、查询参数或连接凭据。停止为取消请求，等待驱动响应和资源释放后记录才结束。最近刷新：{{updatedAt}}</p>
      </template>
    </template>
  </main>
</template>
<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,ref} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {checkPermi} from '@/utils/permission'
import {queryMonitor,cancelMonitoredQuery,type QueryMonitor,type QueryMonitorRow} from '@/api/bi/queryMonitor'
const authorized=computed(()=>checkPermi(['bi:resource:admin']))
const data=ref<QueryMonitor>(),error=ref(''),loading=ref(false),autoRefresh=ref(false),onlySlow=ref(false),cancelling=ref(''),updatedAt=ref('')
const recent=computed(()=>data.value?.recent.filter(r=>!onlySlow.value||r.slow)||[])
let controller:AbortController|undefined,timer:ReturnType<typeof setInterval>|undefined,disposed=false
function sourceName(value:string){return ({chart:'图表',dashboard:'看板',background:'后台任务'} as Record<string,string>)[value]||value}
function statusName(value:string){return ({running:'运行中',cancelling:'取消处理中',completed:'已完成',failed:'执行失败',QUERY_TIMEOUT:'执行超时',QUERY_CANCELLED:'已取消',lease_expired:'节点租约已过期'} as Record<string,string>)[value]||value}
async function load(){if(!authorized.value||loading.value||disposed)return;loading.value=true;error.value='';controller=new AbortController();try{const response=await queryMonitor(controller.signal);if(!disposed){data.value=response.data;updatedAt.value=new Date().toLocaleTimeString()}}catch(e){if(!disposed && !controller.signal.aborted)error.value=e instanceof Error?e.message:'监控数据加载失败，请重试'}finally{if(!disposed)loading.value=false}}
async function stop(row:QueryMonitorRow){try{await ElMessageBox.confirm('停止此查询？数据库资源释放可能需要等待驱动响应。','停止查询',{type:'warning',confirmButtonText:'停止查询',cancelButtonText:'返回'})}catch{return}cancelling.value=row.requestId;try{const response=await cancelMonitoredQuery(row.requestId);ElMessage.info(response.data.accepted?'已请求取消查询':'查询可能已结束，请刷新确认');await load()}catch{/* request layer reports failure */}finally{cancelling.value=''}}
onMounted(()=>{void load();timer=setInterval(()=>{if(autoRefresh.value && document.visibilityState==='visible')void load()},5000)})
onBeforeUnmount(()=>{disposed=true;controller?.abort();if(timer)clearInterval(timer)})
</script>
<style scoped>.query-monitor{padding:28px 32px;color:var(--bi-text,#182230)}header,.actions,.section-title{display:flex;align-items:center;justify-content:space-between;gap:16px}h1{margin:0;font-size:28px}header p,.privacy{color:var(--bi-muted,#667085);line-height:1.7}header{margin-bottom:24px}.stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin:20px 0}.stats article,section{background:var(--bi-surface,#fff);border:1px solid var(--bi-border,#e6ebf3);border-radius:10px;padding:20px}.stats span{display:block;color:var(--bi-muted,#667085)}.stats strong{display:block;margin-top:12px;font-size:30px}.stats small{font-size:15px;font-weight:400;color:var(--bi-muted,#667085)}section{margin-top:20px}h2{font-size:17px;margin:0 0 16px}.section-title h2{margin:0}.section-title{margin-bottom:16px}.privacy{font-size:12px}@media(max-width:800px){.query-monitor{padding:20px 16px}.stats{grid-template-columns:repeat(2,minmax(0,1fr))}header{align-items:flex-start;flex-direction:column}.actions{flex-wrap:wrap}}</style>
