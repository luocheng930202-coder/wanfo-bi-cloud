<template>
  <div class="app-container">
    <el-card>
      <template #header><div class="toolbar"><div><strong>报表订阅</strong><div class="hint">按计划生成已保存图表或已发布看板，并通过 SMTP 邮件发送；每次运行都会重新检查当前权限。</div></div><div><el-button @click="refresh">刷新</el-button><el-button v-hasPermi="['bi:subscription:add']" type="primary" @click="openCreate">新建订阅</el-button></div></div></template>
      <el-alert title="需要管理员配置 bi.report-subscription.enabled=true、Spring Mail SMTP 参数和 bi.report-subscription.allowed-domains 收件人域名白名单。附件最大 8 MB；连续失败 3 次后自动暂停。" type="info" :closable="false" class="mb8" />
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="name" label="订阅名称" min-width="160" />
        <el-table-column label="来源" min-width="160"><template #default="{row}">{{ row.sourceType==='chart'?'图表':'看板' }} #{{ row.sourceId }}</template></el-table-column>
        <el-table-column prop="format" label="格式" width="80" />
        <el-table-column prop="recipientCount" label="收件人" width="90" />
        <el-table-column label="间隔" width="110"><template #default="{row}">{{ intervalLabel(row.intervalMinutes) }}</template></el-table-column>
        <el-table-column label="状态" width="115"><template #default="{row}"><el-tag :type="row.lastStatus==='succeeded'?'success':row.lastStatus==='failed'?'danger':row.lastStatus==='paused'?'info':'warning'">{{ statusText(row) }}</el-tag></template></el-table-column>
        <el-table-column prop="nextRun" label="下次运行" width="175" />
        <el-table-column prop="lastMessage" label="最近说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right"><template #default="{row}">
          <el-button v-hasPermi="['bi:subscription:list']" link type="primary" @click="showHistory(row)">运行记录</el-button>
          <el-button v-hasPermi="['bi:subscription:edit']" link type="warning" @click="toggle(row)">{{ row.enabled?'暂停':'启用' }}</el-button>
          <el-button v-hasPermi="['bi:subscription:remove']" link type="danger" @click="remove(row)">删除</el-button>
        </template></el-table-column>
        <template #empty><el-empty description="暂无报表订阅" /></template>
      </el-table>
    </el-card>
    <el-dialog v-model="createVisible" title="新建报表订阅" width="620px" destroy-on-close>
      <el-form label-width="105px">
        <el-form-item label="订阅名称"><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="来源类型"><el-radio-group v-model="form.sourceType" @change="loadResources"><el-radio-button value="chart">图表</el-radio-button><el-radio-button value="dashboard">已发布看板</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="报表资源"><el-select v-model="form.sourceId" filterable style="width:100%" placeholder="选择可访问的已保存资源" @change="selectResource"><el-option v-for="item in resources" :key="item.id" :label="item.name+' (#'+item.id+')'" :value="item.id" /></el-select></el-form-item>
        <el-form-item v-if="form.sourceType==='dashboard'" label="看板组件"><el-select v-model="form.panelKey" clearable style="width:100%" placeholder="全部数据组件"><el-option v-for="panel in panelOptions" :key="panel.key" :label="panel.title+' ('+panel.key+')'" :value="panel.key"/></el-select></el-form-item>
        <el-form-item label="导出格式"><el-select v-model="form.format"><el-option label="Excel" value="xlsx"/><el-option label="CSV" value="csv"/><el-option label="PDF" value="pdf"/></el-select></el-form-item>
        <el-form-item label="收件邮箱"><el-input v-model="recipientText" type="textarea" :rows="2" placeholder="多个邮箱用逗号分隔，最多 10 个" /></el-form-item>
        <el-form-item label="执行间隔"><el-select v-model="form.intervalMinutes"><el-option label="每 15 分钟" :value="15"/><el-option label="每小时" :value="60"/><el-option label="每天" :value="1440"/><el-option label="每周" :value="10080"/></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">创建订阅</el-button></template>
    </el-dialog>
    <el-dialog v-model="historyVisible" title="订阅运行记录" width="780px"><el-table :data="historyRows" border><el-table-column prop="startedAt" label="开始时间" width="175"/><el-table-column prop="status" label="状态" width="100"/><el-table-column prop="attempt" label="尝试次数" width="100"/><el-table-column prop="recipientCount" label="收件人数" width="95"/><el-table-column prop="message" label="说明" min-width="180"/><el-table-column prop="finishedAt" label="完成时间" width="175"/></el-table></el-dialog>
  </div>
</template>
<script setup lang="ts" name="BiSubscription">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCharts, type ChartSummary } from '@/api/bi/chart'
import { getDashboardView, listDashboards, type DashboardSummary } from '@/api/bi/dashboard'
import { createSubscription, deleteSubscription, listSubscriptions, subscriptionHistory, updateSubscription, type ReportSubscription, type SubscriptionAudit, type SubscriptionCreate } from '@/api/bi/subscription'
const rows=ref<ReportSubscription[]>([]),loading=ref(false),saving=ref(false),createVisible=ref(false),historyVisible=ref(false),resources=ref<{id:number;name:string}[]>([]),panelOptions=ref<{key:string;title:string}[]>([]),historyRows=ref<SubscriptionAudit[]>([]),recipientText=ref('')
const form=reactive<SubscriptionCreate>({sourceType:'chart',sourceId:0,panelKey:'',format:'xlsx',name:'',recipients:[],intervalMinutes:1440})
async function refresh(){loading.value=true;try{rows.value=(await listSubscriptions()).data||[]}finally{loading.value=false}}
async function loadResources(){form.sourceId=0;form.panelKey='';panelOptions.value=[];resources.value=form.sourceType==='chart'?((await listCharts()).data||[]).filter((v:ChartSummary)=>v.resourceActions?.includes('query')).map(v=>({id:v.id,name:v.name})):((await listDashboards()).data||[]).filter((v:DashboardSummary)=>v.published&&v.resourceActions?.includes('consume')).map(v=>({id:v.id,name:v.name}))}
async function selectResource(id:number){form.sourceId=id;form.panelKey='';panelOptions.value=[];if(form.sourceType==='dashboard'&&id){const view=(await getDashboardView(id)).data;panelOptions.value=(view.panels||[]).filter(p=>!p.componentType||['chart','kpi'].includes(p.componentType)).map(p=>({key:p.key,title:p.title||p.key}))}}
async function openCreate(){form.sourceType='chart';form.sourceId=0;form.panelKey='';form.name='';recipientText.value='';await loadResources();createVisible.value=true}
async function save(){const recipients=recipientText.value.split(/[,，;；\s]+/).map(v=>v.trim()).filter(Boolean);if(!form.name.trim()||!form.sourceId||!recipients.length){ElMessage.warning('请填写名称、选择报表并设置收件人');return}saving.value=true;try{await createSubscription({...form,recipients});ElMessage.success('订阅已创建');createVisible.value=false;await refresh()}finally{saving.value=false}}
async function toggle(row:ReportSubscription){await updateSubscription(row.id,{intervalMinutes:row.intervalMinutes,enabled:!row.enabled});ElMessage.success(row.enabled?'订阅已暂停':'订阅已启用');await refresh()}
async function remove(row:ReportSubscription){await ElMessageBox.confirm(`确定删除“${row.name}”订阅吗？`,'删除订阅',{type:'warning'});await deleteSubscription(row.id);ElMessage.success('已删除');await refresh()}
async function showHistory(row:ReportSubscription){historyRows.value=(await subscriptionHistory(row.id)).data||[];historyVisible.value=true}
function intervalLabel(n:number){return n===15?'15 分钟':n===60?'1 小时':n===1440?'1 天':n===10080?'1 周':`${n} 分钟`}
function statusText(row:ReportSubscription){return row.enabled?(row.lastStatus==='succeeded'?'已发送':row.lastStatus==='failed'?'发送失败':row.lastStatus==='running'?'生成中':'运行中'):'已暂停'}
onMounted(refresh)
</script>
<style scoped>.toolbar{display:flex;justify-content:space-between;align-items:center;gap:12px}.hint{margin-top:5px;color:#667085;font-size:12px}</style>
