<template>
  <div class="app-container">
    <el-card>
      <template #header><div class="toolbar"><div><strong>报表订阅</strong><div class="hint">按本地日历时区生成图表或已发布看板，通过站内通知、企业微信或邮件分发。</div></div><div><el-button @click="openNotifications">站内通知</el-button><el-button v-if="isAdmin" @click="openAudit">全局审计</el-button><el-button @click="refresh">刷新</el-button><el-button v-hasPermi="['bi:subscription:add']" type="primary" @click="openCreate">新建订阅</el-button></div></div></template>
      <el-alert title="邮件需配置 SMTP 和接收域名白名单；企业微信需管理员配置 HTTPS 群机器人。模板仅支持安全文本变量，失败重试 3 次后暂停。" type="info" :closable="false" class="mb8" />
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="name" label="订阅名称" min-width="150" />
        <el-table-column label="来源" min-width="145"><template #default="{row}">{{ row.sourceType==='chart'?'图表':'看板' }} #{{ row.sourceId }}</template></el-table-column>
        <el-table-column prop="format" label="格式" width="80" />
        <el-table-column label="通知方式" min-width="140"><template #default="{row}">{{ channelLabel(row.channels) }}</template></el-table-column>
        <el-table-column label="计划 / 时区" min-width="190"><template #default="{row}">{{ scheduleLabel(row.schedule) }}</template></el-table-column>
        <el-table-column prop="recipientCount" label="邮件收件人" width="105" />
        <el-table-column label="状态" width="105"><template #default="{row}"><el-tag :type="row.lastStatus==='succeeded'?'success':row.lastStatus==='failed'?'danger':row.lastStatus==='paused'?'info':'warning'">{{ statusText(row) }}</el-tag></template></el-table-column>
        <el-table-column prop="nextRun" label="下次运行（UTC）" width="180" />
        <el-table-column prop="lastMessage" label="最近说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right"><template #default="{row}">
          <el-button v-hasPermi="['bi:subscription:list']" link type="primary" @click="showHistory(row)">运行记录</el-button>
          <el-button v-hasPermi="['bi:subscription:edit']" link type="warning" @click="toggle(row)">{{ row.enabled?'暂停':'启用' }}</el-button>
          <el-button v-hasPermi="['bi:subscription:remove']" link type="danger" @click="remove(row)">删除</el-button>
        </template></el-table-column>
        <template #empty><el-empty description="暂无报表订阅" /></template>
      </el-table>
    </el-card>
    <el-dialog v-model="createVisible" title="新建报表订阅" width="700px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="订阅名称"><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="来源类型"><el-radio-group v-model="form.sourceType" @change="loadResources"><el-radio-button value="chart">图表</el-radio-button><el-radio-button value="dashboard">已发布看板</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="报表资源"><el-select v-model="form.sourceId" filterable style="width:100%" placeholder="选择可访问的已保存资源" @change="selectResource"><el-option v-for="item in resources" :key="item.id" :label="item.name+' (#'+item.id+')'" :value="item.id" /></el-select></el-form-item>
        <el-form-item v-if="form.sourceType==='dashboard'" label="看板组件"><el-select v-model="form.panelKey" clearable style="width:100%" placeholder="全部数据组件"><el-option v-for="panel in panelOptions" :key="panel.key" :label="panel.title+' ('+panel.key+')'" :value="panel.key"/></el-select></el-form-item>
        <el-form-item label="导出格式"><el-select v-model="form.format"><el-option label="Excel" value="xlsx"/><el-option label="CSV" value="csv"/><el-option label="PDF" value="pdf"/></el-select></el-form-item>
        <el-form-item label="通知方式"><el-checkbox-group v-model="form.channels"><el-checkbox value="inbox">站内通知</el-checkbox><el-checkbox value="wecom">企业微信</el-checkbox><el-checkbox value="email">邮件</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item v-if="form.channels.includes('email')" label="收件邮箱"><el-input v-model="recipientText" type="textarea" :rows="2" placeholder="多个邮箱用逗号分隔，最多 10 个" /></el-form-item>
        <el-form-item label="执行计划"><el-select v-model="form.schedule.kind"><el-option label="按间隔" value="interval"/><el-option label="每天" value="daily"/><el-option label="每周" value="weekly"/></el-select>
          <el-select v-if="form.schedule.kind==='interval'" v-model="form.schedule.intervalMinutes" class="ml8"><el-option label="每 15 分钟" :value="15"/><el-option label="每小时" :value="60"/><el-option label="每天" :value="1440"/><el-option label="每周" :value="10080"/></el-select>
          <el-time-picker v-else v-model="form.schedule.localTime" format="HH:mm" value-format="HH:mm" class="ml8" placeholder="本地执行时间" />
        </el-form-item>
        <el-form-item v-if="form.schedule.kind==='weekly'" label="执行星期"><el-checkbox-group v-model="form.schedule.weekdays"><el-checkbox v-for="day in weekdayOptions" :key="day.value" :value="day.value">{{ day.label }}</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item label="执行时区"><el-select v-model="form.schedule.timezone" filterable allow-create default-first-option style="width:100%" placeholder="选择或输入 IANA 时区"><el-option v-for="zone in timezoneOptions" :key="zone" :label="zone" :value="zone" /></el-select></el-form-item>
        <el-form-item label="通知标题"><el-input v-model="form.templateTitle" maxlength="200" /></el-form-item>
        <el-form-item label="通知正文"><el-input v-model="form.templateBody" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
        <div class="hint template-hint">模板变量：&#123;&#123;reportName&#125;&#125;、&#123;&#123;status&#125;&#125;、&#123;&#123;rowCount&#125;&#125;、&#123;&#123;startedAt&#125;&#125;</div>
      </el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">创建订阅</el-button></template>
    </el-dialog>
    <el-dialog v-model="historyVisible" title="订阅运行记录" width="780px"><el-table :data="historyRows" border><el-table-column prop="startedAt" label="开始时间" width="175"/><el-table-column prop="status" label="状态" width="100"/><el-table-column prop="attempt" label="尝试次数" width="100"/><el-table-column prop="recipientCount" label="邮件收件人数" width="115"/><el-table-column prop="message" label="说明" min-width="180"/><el-table-column prop="finishedAt" label="完成时间" width="175"/></el-table></el-dialog>
    <el-dialog v-model="notificationsVisible" title="站内通知" width="700px"><el-table :data="notifications" border><el-table-column prop="title" label="标题" min-width="180"/><el-table-column prop="body" label="内容" min-width="260"/><el-table-column prop="createdAt" label="时间" width="175"/><el-table-column label="状态" width="95"><template #default="{row}"><el-button v-if="!row.readAt" link type="primary" @click="markRead(row.id)">标记已读</el-button><el-tag v-else type="info">已读</el-tag></template></el-table-column></el-table></el-dialog>
    <el-dialog v-model="auditVisible" title="报表订阅全局审计（最近 200 条）" width="1050px"><el-table :data="auditRows" border><el-table-column prop="subscriptionName" label="订阅" min-width="150"/><el-table-column prop="ownerId" label="所有者" width="90"/><el-table-column prop="sourceType" label="来源" width="80"/><el-table-column prop="startedAt" label="开始时间" width="170"/><el-table-column prop="status" label="状态" width="90"/><el-table-column prop="attempt" label="尝试" width="70"/><el-table-column prop="rowCount" label="行数" width="80"/><el-table-column prop="recipientCount" label="收件人数" width="95"/><el-table-column prop="message" label="说明" min-width="160"/></el-table></el-dialog>
  </div>
</template>
<script setup lang="ts" name="BiSubscription">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCharts, type ChartSummary } from '@/api/bi/chart'
import { getDashboardView, listDashboards, type DashboardSummary } from '@/api/bi/dashboard'
import { checkPermi } from '@/utils/permission'
import { createSubscription, deleteSubscription, listSubscriptions, subscriptionHistory, updateSubscription, listSubscriptionNotifications, markSubscriptionNotificationRead, subscriptionAdminAudit, type ReportSubscription, type SubscriptionAudit, type SubscriptionCreate, type SubscriptionNotification, type SubscriptionSchedule } from '@/api/bi/subscription'
const rows=ref<ReportSubscription[]>([]),loading=ref(false),saving=ref(false),createVisible=ref(false),historyVisible=ref(false),notificationsVisible=ref(false),auditVisible=ref(false),resources=ref<{id:number;name:string}[]>([]),panelOptions=ref<{key:string;title:string}[]>([]),historyRows=ref<SubscriptionAudit[]>([]),notifications=ref<SubscriptionNotification[]>([]),auditRows=ref<SubscriptionAudit[]>([]),recipientText=ref('')
const isAdmin=checkPermi(['bi:resource:admin'])
const weekdayOptions=[{label:'周一',value:1},{label:'周二',value:2},{label:'周三',value:3},{label:'周四',value:4},{label:'周五',value:5},{label:'周六',value:6},{label:'周日',value:7}]
const timezoneOptions=['Asia/Shanghai','Asia/Tokyo','Asia/Singapore','Asia/Kolkata','Europe/London','Europe/Paris','America/New_York','America/Los_Angeles','UTC']
const form=reactive<SubscriptionCreate>({sourceType:'chart',sourceId:0,panelKey:'',format:'xlsx',name:'',recipients:[],intervalMinutes:1440,channels:['inbox'],schedule:{kind:'interval',intervalMinutes:1440,localTime:'09:00',weekdays:[],timezone:'Asia/Shanghai'},templateTitle:'报表订阅：{{reportName}}',templateBody:'报表 {{reportName}} {{status}}，数据行数：{{rowCount}}。'})
async function refresh(){loading.value=true;try{rows.value=(await listSubscriptions()).data||[]}finally{loading.value=false}}
async function loadResources(){form.sourceId=0;form.panelKey='';panelOptions.value=[];resources.value=form.sourceType==='chart'?((await listCharts()).data||[]).filter((v:ChartSummary)=>v.resourceActions?.includes('query')).map(v=>({id:v.id,name:v.name})):((await listDashboards()).data||[]).filter((v:DashboardSummary)=>v.published&&v.resourceActions?.includes('consume')).map(v=>({id:v.id,name:v.name}))}
async function selectResource(id:number){form.sourceId=id;form.panelKey='';panelOptions.value=[];if(form.sourceType==='dashboard'&&id){const view=(await getDashboardView(id)).data;panelOptions.value=(view.panels||[]).filter(p=>!p.componentType||['chart','kpi'].includes(p.componentType)).map(p=>({key:p.key,title:p.title||p.key}))}}
async function openCreate(){form.sourceType='chart';form.sourceId=0;form.panelKey='';form.name='';form.channels=['inbox'];form.schedule={kind:'interval',intervalMinutes:1440,localTime:'09:00',weekdays:[],timezone:'Asia/Shanghai'};recipientText.value='';await loadResources();createVisible.value=true}
async function save(){const recipients=recipientText.value.split(/[,，;；\s]+/).map(v=>v.trim()).filter(Boolean);if(!form.name.trim()||!form.sourceId||!form.channels.length||form.channels.includes('email')&&!recipients.length){ElMessage.warning('请填写名称、选择报表、通知方式和对应收件人');return}if(form.schedule.kind==='weekly'&&!form.schedule.weekdays.length){ElMessage.warning('每周计划至少选择一天');return}form.recipients=recipients;form.intervalMinutes=form.schedule.intervalMinutes;saving.value=true;try{await createSubscription({...form,recipients});ElMessage.success('订阅已创建');createVisible.value=false;await refresh()}finally{saving.value=false}}
async function toggle(row:ReportSubscription){await updateSubscription(row.id,{intervalMinutes:row.intervalMinutes,enabled:!row.enabled});ElMessage.success(row.enabled?'订阅已暂停':'订阅已启用');await refresh()}
async function remove(row:ReportSubscription){await ElMessageBox.confirm(`确定删除“${row.name}”订阅吗？`,'删除订阅',{type:'warning'});await deleteSubscription(row.id);ElMessage.success('已删除');await refresh()}
async function showHistory(row:ReportSubscription){historyRows.value=(await subscriptionHistory(row.id)).data||[];historyVisible.value=true}
async function openNotifications(){notifications.value=(await listSubscriptionNotifications()).data||[];notificationsVisible.value=true}
async function markRead(id:number){await markSubscriptionNotificationRead(id);const row=notifications.value.find(item=>item.id===id);if(row)row.readAt=new Date().toISOString()}
async function openAudit(){try{auditRows.value=(await subscriptionAdminAudit()).data||[];auditVisible.value=true}catch{ElMessage.error('当前账号没有全局审计权限')}}
function channelLabel(channels:string[]){return (channels||[]).map(c=>({email:'邮件',inbox:'站内通知',wecom:'企业微信'}[c]||c)).join('、')}
function scheduleLabel(schedule?:SubscriptionSchedule){if(!schedule)return '间隔计划';return schedule.kind==='interval'?`${schedule.intervalMinutes} 分钟`:`${schedule.kind==='daily'?'每天':'每周'} ${schedule.localTime} · ${schedule.timezone}${schedule.weekdays?.length?` · 周${schedule.weekdays.join('、')}`:''}`}
function statusText(row:ReportSubscription){return row.enabled?(row.lastStatus==='succeeded'?'已完成':row.lastStatus==='failed'?'运行失败':row.lastStatus==='running'?'生成中':'运行中'):'已暂停'}
onMounted(refresh)
</script>
<style scoped>.toolbar{display:flex;justify-content:space-between;align-items:center;gap:12px}.hint{margin-top:5px;color:#667085;font-size:12px}.template-hint{margin:0 0 0 110px}.ml8{margin-left:8px}</style>
