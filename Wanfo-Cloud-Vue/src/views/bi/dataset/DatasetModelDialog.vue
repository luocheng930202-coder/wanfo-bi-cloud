<template>
  <el-dialog :model-value="visible" :title="'数据集建模 · ' + name" width="min(920px, calc(100vw - 24px))" @update:model-value="emit('update:visible', $event)" @open="load">
    <el-tabs v-model="tab" @tab-change="tabChanged">
      <el-tab-pane label="关联表" name="join">
        <el-alert type="info" :closable="false" show-icon class="mb8" title="按顺序连接多张物理表；后续关系可引用前序关系输出的字段。首条关联字段使用 r_ 前缀，后续字段使用 r2_、r3_ 前缀。" />
        <div class="join-steps"><el-tag v-for="(step,index) in joinSteps" :key="index" :type="index===activeJoinIndex?'primary':'info'" effect="plain" class="join-step" @click="selectJoin(index)">关系 {{ index+1 }} · {{ step.rightTable || '未配置' }}<el-icon v-if="joinSteps.length>1" class="remove-step" @click.stop="removeJoin(index)"><Close /></el-icon></el-tag><el-button size="small" :disabled="joinSteps.length>=8" @click="addJoin">添加关系</el-button></div>
        <el-form label-width="100px">
          <el-form-item label="关联类型">
            <el-select v-model="activeJoin.joinType" style="width:160px">
              <el-option label="LEFT JOIN" value="LEFT" />
              <el-option label="INNER JOIN" value="INNER" />
              <el-option label="RIGHT JOIN" value="RIGHT" />
            </el-select>
          </el-form-item>
          <el-form-item label="右表" required>
            <el-select v-model="activeJoin.rightTable" filterable :loading="tablesLoading" placeholder="选择表" style="width:100%" @change="rightTableChanged">
              <el-option v-for="t in tables" :key="t.name" :value="t.name" :label="t.comment ? `${t.name} · ${t.comment}` : t.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="左表键" required>
            <el-select v-model="activeJoin.leftKey" filterable placeholder="左侧已建模字段" style="width:100%">
              <el-option v-for="f in relationshipFields" :key="f.originName" :value="f.originName" :label="f.showName && f.showName !== f.originName ? `${f.showName} (${f.originName})` : f.originName" />
            </el-select>
          </el-form-item>
          <el-form-item label="右表键" required>
            <el-select v-model="activeJoin.rightKey" filterable placeholder="右表关联键" style="width:100%">
              <el-option v-for="c in rightColumns" :key="c.name" :value="c.name" :label="c.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="右表字段">
            <el-table :data="rightColumns" border size="small" height="200" v-loading="columnsLoading">
              <el-table-column label="选择" width="65">
                <template #default="{ row }">
                  <el-checkbox :model-value="activeJoin.rightFields.includes(row.name)" @change="v => toggleRight(row.name, !!v)" />
                </template>
              </el-table-column>
              <el-table-column prop="name" label="字段" />
              <el-table-column prop="type" label="类型" width="120" />
              <el-table-column prop="comment" label="库表注释" min-width="120" show-overflow-tooltip />
            </el-table>
          </el-form-item>
        </el-form>
        <div class="footer">
          <el-button type="danger" plain :loading="saving" @click="clearJoin">移除关联</el-button>
          <el-button type="primary" :loading="saving" @click="saveJoin">应用全部关系</el-button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="显示名称" name="labels">
        <el-alert type="info" :closable="false" show-icon class="mb8" title="业务用户看到的是显示名称；查询与权限仍使用物理字段名（originName）。" />
        <el-table :data="physicalFields" border size="small" max-height="360">
          <el-table-column prop="originName" label="物理字段" min-width="140" />
          <el-table-column label="显示名称" min-width="180">
            <template #default="{ row }"><el-input v-model="row.showName" maxlength="200" @change="() => saveFieldLabel(row)" /></template>
          </el-table-column>
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <el-select v-model="row.dimFlag" style="width:90px" @change="() => saveFieldLabel(row)">
                <el-option label="维度" value="1" /><el-option label="度量" value="0" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="groupName" label="分组" width="90" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="计算字段" name="calc">
        <el-alert type="info" :closable="false" title="清洗步骤按表格顺序执行；后续字段可引用前序步骤。调整顺序时系统会检查依赖，避免把步骤移到依赖字段之前。每次查询都会从数据源重放已保存步骤。支持 TRIM、UPPER、LOWER、COALESCE、ROUND、ABS 等安全函数。" />
        <el-form :inline="true" class="mb8">
          <el-form-item label="字段名"><el-input v-model="calc.originName" placeholder="profit_rate" /></el-form-item>
          <el-form-item label="显示名"><el-input v-model="calc.showName" placeholder="利润率" /></el-form-item>
          <el-form-item label="维度">
            <el-select v-model="calc.dimFlag" style="width:100px"><el-option label="维度" value="1" /><el-option label="度量" value="0" /></el-select>
          </el-form-item>
        </el-form>
        <el-form-item label="表达式" label-width="100px">
          <el-input v-model="calc.formula" type="textarea" :rows="3" placeholder="如 [amount] / [qty]，支持 ROUND/ABS 等" />
        </el-form-item>
        <div class="calc-actions"><el-button type="primary" :loading="saving" @click="addCalc">添加清洗步骤</el-button><el-button :loading="replayLoading" @click="replaySteps">预览并回放步骤</el-button></div>
        <el-table :data="calcFields" border size="small" class="mt8">
          <el-table-column prop="originName" label="字段" />
          <el-table-column prop="showName" label="显示名" />
          <el-table-column prop="extFormula" label="表达式" show-overflow-tooltip />
          <el-table-column label="顺序" width="140"><template #default="{ row, $index }"><el-button link :disabled="$index===0" @click="moveCalc(row,-1)">上移</el-button><el-button link :disabled="$index===calcFields.length-1" @click="moveCalc(row,1)">下移</el-button></template></el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeCalc(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="质量检查" name="quality">
        <div class="quality-toolbar"><el-radio-group v-model="qualityFullScan" :disabled="qualityLoading"><el-radio-button :value="false">快速抽样</el-radio-button><el-radio-button :value="true">全量精确扫描</el-radio-button></el-radio-group><div><el-button v-if="quality" v-hasPermi="['bi:datasource:model']" :loading="savingRules" @click="saveQualityRules">保存规则</el-button><el-button type="primary" :loading="qualityLoading" @click="loadQuality">{{ qualityFullScan ? '执行全量扫描' : '运行抽样检查' }}</el-button></div></div>
        <el-alert v-if="qualityFullScan" type="warning" :closable="false" show-icon title="全量扫描会遍历当前数据权限可见的全部数据，可能执行较久；若超过两分钟将终止并返回错误，不会返回部分结果。" />
        <el-alert v-if="qualityScore?.hasResult" type="info" :closable="false" show-icon :title="`全量规则评分 ${qualityScore.score ?? '—'} · ${qualityScore.rulesPassed}/${qualityScore.rulesTotal} 条规则通过 · 最近完成 ${qualityScore.completedAt || '未知'} · ${qualityScore.warning}`" />
        <el-alert v-else type="info" :closable="false" show-icon title="数据画像指标基于当前用户可见范围；全量规则评分将在至少一次完整规则任务完成后显示。" />
        <el-alert v-if="quality" :type="quality.qualityStatus==='failed'?'warning':'info'" :closable="false" :title="`${quality.scanMode==='full'?`${quality.scannedRows} 行全量数据`:`${quality.sampledRows} 行样本`} · ${quality.duplicateRows} 行重复 · ${quality.ruleFailureCount} 项规则问题 · ${quality.ruleNote}`" />
        <el-table v-if="quality" :data="qualityRuleRows" border size="small" class="mt8"><el-table-column prop="field" label="字段" min-width="130"/><el-table-column label="必填" width="90"><template #default="{row}"><el-switch v-model="row.required"/></template></el-table-column><el-table-column label="唯一值" width="100"><template #default="{row}"><el-switch v-model="row.unique"/></template></el-table-column><el-table-column label="最大空值率" width="180"><template #default="{row}"><el-input-number v-model="row.maxNullRate" :min="0" :max="1" :step="0.05" :precision="2" :controls="false" placeholder="关闭"/><small>0～1</small></template></el-table-column><el-table-column :label="quality.scanMode==='full'?'全量检查':'抽样检查'" width="125"><template #default="{row}"><el-tag v-if="row.qualityStatus==='failed'" type="danger">未通过</el-tag><el-tag v-else-if="row.qualityStatus==='passed'" type="success">通过</el-tag><el-tag v-else type="info">未配置</el-tag></template></el-table-column><el-table-column label="问题" min-width="150"><template #default="{row}">{{ row.violations?.join('、')||'—' }}</template></el-table-column><el-table-column prop="nullCount" label="空值" width="75"/><el-table-column label="空值率" width="90"><template #default="{row}">{{ (row.nullRate*100).toFixed(1) }}%</template></el-table-column><el-table-column prop="distinctCount" label="不同值" width="90"/></el-table>
        <el-divider content-position="left">规则版本</el-divider>
        <div class="quality-toolbar"><span class="muted">已发布版本不可修改；从当前规则创建草稿后发布，会留下可追溯的定义版本。</span><el-button v-hasPermi="['bi:datasource:model']" :loading="versionSaving" @click="createRuleVersion">从当前规则创建草稿</el-button></div>
        <el-table :data="qualityVersions" border size="small" class="mt8">
          <el-table-column prop="version" label="版本" width="80"><template #default="{row}">v{{ row.version }}</template></el-table-column>
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='PUBLISHED'?'success':'warning'">{{ row.status==='PUBLISHED'?'已发布':'草稿' }}</el-tag></template></el-table-column>
          <el-table-column prop="createBy" label="创建人" width="110" />
          <el-table-column label="操作" width="210"><template #default="{row}"><el-button link @click="showVersionRules(row)">查看规则</el-button><el-button v-if="row.status==='PUBLISHED'" v-hasPermi="['bi:datasource:model']" link @click="copyRuleVersion(row)">复制草稿</el-button><el-button v-else v-hasPermi="['bi:datasource:model']" link type="primary" @click="publishRuleVersion(row)">发布</el-button></template></el-table-column>
        </el-table>
        <el-divider content-position="left">质量任务</el-divider>
        <div class="quality-toolbar">
          <div class="task-create"><el-input v-model="taskName" placeholder="任务名称" maxlength="160" /><el-select v-model="taskRuleSetId" placeholder="已发布规则版本" clearable><el-option v-for="row in qualityVersions.filter(item=>item.status==='PUBLISHED')" :key="row.id" :value="row.id" :label="`${row.name} · v${row.version}`" /></el-select><el-select v-model="taskScope" style="width:145px"><el-option label="全量扫描" value="FULL"/><el-option label="可见样本" value="SAMPLE"/></el-select><el-select v-model="taskScheduleType" style="width:130px"><el-option label="手动运行" value="MANUAL"/><el-option label="定时运行" value="INTERVAL"/></el-select><el-input-number v-if="taskScheduleType==='INTERVAL'" v-model="taskIntervalMinutes" :min="15" :max="10080" :step="15" controls-position="right" style="width:145px"/><el-input-number v-model="taskRetryLimit" :min="0" :max="3" controls-position="right" style="width:130px"/><span class="muted">重试上限</span><el-button v-hasPermi="['bi:datasource:model']" :loading="taskSaving" @click="createTask">创建质量任务</el-button></div>
        </div>
        <el-table :data="qualityTasks" border size="small" class="mt8">
          <el-table-column prop="name" label="任务" min-width="180"/><el-table-column label="规则版本" width="100"><template #default="{row}">v{{ row.ruleVersion }}</template></el-table-column><el-table-column label="扫描范围" width="110"><template #default="{row}">{{ row.scanScope==='FULL'?'全量':'样本' }}</template></el-table-column><el-table-column label="运行计划" width="140"><template #default="{row}">{{ row.scheduleType==='INTERVAL'?`每 ${row.scheduleIntervalMinutes} 分钟`:'手动' }}</template></el-table-column><el-table-column prop="retryLimit" label="重试上限" width="90"/><el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='ENABLED'?'success':'info'">{{ row.status==='ENABLED'?'启用':'暂停' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="220"><template #default="{row}"><el-button link type="primary" :disabled="row.status!=='ENABLED'" @click="runTask(row)">立即运行</el-button><el-button link @click="toggleTask(row)">{{ row.status==='ENABLED'?'暂停':'启用' }}</el-button><el-button link @click="showTaskRuns(row)">运行记录</el-button></template></el-table-column>
        </el-table>
        <el-divider content-position="left">质量趋势（近 30 天）</el-divider>
        <el-table :data="qualityTrend" border size="small" class="mt8"><el-table-column prop="scanDay" label="日期" width="130"/><el-table-column prop="runCount" label="运行数" width="100"/><el-table-column prop="passedRuns" label="通过" width="100"/><el-table-column prop="failedRuns" label="失败" width="100"/><el-table-column prop="issueCount" label="问题数" width="100"/><el-table-column prop="rowsScanned" label="扫描行数" min-width="130"/></el-table>
        <el-divider content-position="left">质量问题</el-divider>
        <el-table :data="qualityIssues" border size="small" class="mt8">
          <el-table-column prop="ruleCode" label="规则" width="170"/><el-table-column prop="runId" label="运行 ID" width="100"/><el-table-column prop="severity" label="级别" width="90"/><el-table-column label="状态" width="130"><template #default="{row}"><el-tag :type="row.issueStatus==='RESOLVED'?'success':row.issueStatus==='FIXED_PENDING'?'info':row.issueStatus==='CLAIMED'?'warning':'danger'">{{ row.issueStatus==='FIXED_PENDING'?'待复验':row.issueStatus }}</el-tag></template></el-table-column><el-table-column prop="summary" label="问题摘要" min-width="280" show-overflow-tooltip/><el-table-column prop="verifyRunId" label="复验运行" width="100"/><el-table-column prop="claimedBy" label="认领人" width="110"/><el-table-column label="操作" width="230"><template #default="{row}"><el-button v-if="row.issueStatus==='OPEN'" link type="primary" @click="claimIssue(row)">认领</el-button><el-button v-if="row.issueStatus==='CLAIMED'&&row.claimedBy===userStore.name" link type="success" @click="resolveIssue(row)">标记已修复</el-button><el-button link @click="showIssueHistory(row)">历史</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="replayVisible" title="清洗步骤回放预览" width="min(900px, calc(100vw - 36px))" append-to-body>
      <el-alert type="info" :closable="false" class="mb8" title="此预览会从数据源重新执行当前已保存的关联和计算步骤，仅展示前 20 行；不会写回源数据。" />
      <el-table v-if="replayResult" :data="replayResult.rows" border size="small" max-height="420"><el-table-column v-for="field in replayResult.columns" :key="field" :prop="field" :label="field" min-width="130" show-overflow-tooltip /></el-table>
      <el-empty v-else description="暂无回放结果" />
    </el-dialog>
    <el-dialog v-model="versionRulesVisible" title="质量规则版本详情" width="min(760px, calc(100vw - 36px))" append-to-body>
      <el-table :data="versionRules" border size="small"><el-table-column prop="code" label="规则编码" min-width="150"/><el-table-column prop="type" label="类型" width="140"/><el-table-column prop="field" label="字段" min-width="130"/><el-table-column prop="severity" label="级别" width="90"/><el-table-column label="配置" min-width="180"><template #default="{row}">{{ JSON.stringify(row.config||{}) }}</template></el-table-column></el-table>
    </el-dialog>
    <el-dialog v-model="taskRunsVisible" title="质量任务运行记录" width="min(900px, calc(100vw - 36px))" append-to-body>
      <el-table :data="taskRuns" border size="small"><el-table-column prop="id" label="运行 ID" width="100"/><el-table-column prop="attemptNo" label="尝试" width="70"/><el-table-column prop="createTime" label="触发时间" min-width="170"/><el-table-column prop="scanScope" label="范围" width="90"/><el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='SUCCEEDED'?'success':row.status==='FAILED'?'danger':row.status==='CANCELLED'?'info':'warning'">{{ row.status }}</el-tag></template></el-table-column><el-table-column prop="rowsScanned" label="扫描行数" width="100"/><el-table-column label="规则结果" width="140"><template #default="{row}">{{ row.rulesPassed }}/{{ row.rulesTotal }} 通过</template></el-table-column><el-table-column prop="failureSummary" label="说明" min-width="180" show-overflow-tooltip/></el-table>
    </el-dialog>
    <el-dialog v-model="issueHistoryVisible" title="质量问题处理历史" width="min(700px, calc(100vw - 36px))" append-to-body>
      <el-table :data="issueHistory" border size="small"><el-table-column prop="createTime" label="时间" min-width="170"/><el-table-column prop="actor" label="操作人" width="130"/><el-table-column prop="action" label="动作" width="110"/><el-table-column prop="note" label="说明" min-width="200" show-overflow-tooltip/></el-table>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import { getDataset, getDatasetQuality, getDatasetQualityScore, getDatasetQualityRules, saveDatasetQualityRules, listQualityRuleVersions, listQualityRuleVersionRules, createQualityRuleVersion, copyQualityRuleVersion, publishQualityRuleVersion, listQualityTasks, createQualityTask, setQualityTaskStatus, triggerQualityTask, listQualityTaskRuns, listQualityIssues, getQualityTrend, claimQualityIssue, resolveQualityIssue, listQualityIssueHistory, type DatasetQualityScore, type QualityIssue, type QualityIssueHistory, type QualityTask, type QualityTaskRun, type QualityTrendDay, listTables, listColumns, listModelSources, getDatasetModel, applyDatasetJoin, clearDatasetJoin, addCalculatedField, removeCalculatedField, reorderCalculatedFields, previewDataset, updateDatasetField, type DatasetField, type CatalogTable, type CatalogColumn, type DatasetJoinModel, type DatasetJoinStep, type DatasetQualityRule, type DatasetQualityColumn, type DatasetQualityReport, type QualityRuleVersion, type QualityRuleVersionInput, type PreviewResult } from '@/api/bi/dataset'
import useUserStore from '@/store/modules/user'

const props = defineProps<{ visible: boolean; datasetId: number; name: string }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'saved'): void }>()
const userStore=useUserStore()

const tab = ref('join')
const saving = ref(false)
const schemaName = ref('')
const sourceCatalog = ref('')
const leftFields = ref<DatasetField[]>([])
const tables = ref<CatalogTable[]>([])
const tablesLoading = ref(false)
const rightColumns = ref<CatalogColumn[]>([])
const columnsLoading = ref(false)
const blankJoin=():DatasetJoinStep=>({joinType:'LEFT',rightTable:'',leftKey:'',rightKey:'',rightFields:[]})
const joinSteps=ref<DatasetJoinStep[]>([blankJoin()]),activeJoinIndex=ref(0)
const activeJoin=computed(()=>joinSteps.value[activeJoinIndex.value]||joinSteps.value[0]||blankJoin())
const relationshipFields=computed(()=>{const generated=new Set<string>();joinSteps.value.forEach((step,index)=>step.rightFields.forEach(name=>generated.add((index===0?'r_':`r${index+1}_`)+name)));const rows=leftFields.value.filter(f=>f.extFlag!=='1'&&!generated.has(f.originName));const existing=new Set(rows.map(f=>f.originName));joinSteps.value.slice(0,activeJoinIndex.value).forEach((step,index)=>step.rightFields.forEach(name=>{const key=(index===0?'r_':`r${index+1}_`)+name;if(!existing.has(key)){rows.push({originName:key,showName:key,dimFlag:'1',extFlag:'0'});existing.add(key)}}));return rows})
const calc = reactive({ originName: '', showName: '', formula: '', dimFlag: '1' })
const calcFields = computed(() => leftFields.value.filter(f => f.extFlag === '1'))
const physicalFields = computed(() => leftFields.value.filter(f => f.extFlag !== '1'))
const replayVisible=ref(false),replayLoading=ref(false),replayResult=ref<PreviewResult>()
const quality=ref<DatasetQualityReport>(),qualityFullScan=ref(false),qualityLoading=ref(false),savingRules=ref(false)
const qualityRuleRows=ref<(DatasetQualityRule&DatasetQualityColumn)[]>([])
const qualityVersions=ref<QualityRuleVersion[]>([]),versionSaving=ref(false),versionRulesVisible=ref(false),versionRules=ref<QualityRuleVersionInput[]>([])
const qualityTasks=ref<QualityTask[]>([]),taskName=ref(''),taskRuleSetId=ref<number>(),taskScope=ref<'FULL'|'SAMPLE'>('FULL'),taskScheduleType=ref<'MANUAL'|'INTERVAL'>('MANUAL'),taskIntervalMinutes=ref(60),taskRetryLimit=ref(0),taskSaving=ref(false),taskRunsVisible=ref(false),taskRuns=ref<QualityTaskRun[]>([])
const qualityIssues=ref<QualityIssue[]>([]),issueHistoryVisible=ref(false),issueHistory=ref<QualityIssueHistory[]>([])
const qualityTrend=ref<QualityTrendDay[]>([])
const qualityScore=ref<DatasetQualityScore>()
async function loadQuality(){if(qualityLoading.value)return;qualityLoading.value=true;try{const [report,saved,versions,tasks,issues,trend,score]=await Promise.all([getDatasetQuality(props.datasetId,qualityFullScan.value),getDatasetQualityRules(props.datasetId),listQualityRuleVersions(props.datasetId),listQualityTasks(props.datasetId),listQualityIssues(props.datasetId),getQualityTrend(props.datasetId),getDatasetQualityScore(props.datasetId)]);quality.value=report.data;const configured=new Map((saved.data||[]).map(rule=>[rule.field,rule]));qualityRuleRows.value=report.data.columns.map(column=>({...column,required:configured.get(column.field)?.required||false,unique:configured.get(column.field)?.unique||false,maxNullRate:configured.get(column.field)?.maxNullRate??undefined}));qualityVersions.value=versions.data||[];qualityTasks.value=tasks.data||[];qualityIssues.value=issues.data||[];qualityTrend.value=trend.data||[];qualityScore.value=score.data}catch{ElMessage.error('质量检查失败，请确认数据集查询权限及数据源状态')}finally{qualityLoading.value=false}}
async function saveQualityRules(){if(savingRules.value)return;savingRules.value=true;try{const rules=qualityRuleRows.value.filter(row=>row.required||row.unique||row.maxNullRate!==undefined&&row.maxNullRate!==null).map(row=>({field:row.field,required:row.required,unique:row.unique,maxNullRate:row.maxNullRate}));await saveDatasetQualityRules(props.datasetId,rules);ElMessage.success('质量规则已保存');await loadQuality()}catch(e){ElMessage.error(e instanceof Error?e.message:'规则保存失败')}finally{savingRules.value=false}}
async function createRuleVersion(){if(versionSaving.value)return;versionSaving.value=true;try{const rules:QualityRuleVersionInput[]=[];for(const row of qualityRuleRows.value){if(row.required)rules.push({code:`${row.field}.not_null`,type:'NOT_NULL',field:row.field,severity:'ERROR',enabled:true,config:{emptyStringIsNull:true}});if(row.unique)rules.push({code:`${row.field}.unique`,type:'UNIQUE',field:row.field,severity:'ERROR',enabled:true,config:{}});if(row.maxNullRate!==undefined&&row.maxNullRate!==null)rules.push({code:`${row.field}.null_rate`,type:'NULL_RATE_MAX',field:row.field,severity:'WARN',enabled:true,config:{maxRate:row.maxNullRate}})}await createQualityRuleVersion(props.datasetId,`数据质量规则 ${new Date().toLocaleString()}`,rules,'由当前兼容规则创建');ElMessage.success('规则草稿已创建');qualityVersions.value=(await listQualityRuleVersions(props.datasetId)).data||[]}catch(e){ElMessage.error(e instanceof Error?e.message:'创建规则版本失败')}finally{versionSaving.value=false}}
async function showVersionRules(row:QualityRuleVersion){try{versionRules.value=(await listQualityRuleVersionRules(props.datasetId,row.id)).data||[];versionRulesVisible.value=true}catch(e){ElMessage.error(e instanceof Error?e.message:'读取规则版本失败')}}
async function copyRuleVersion(row:QualityRuleVersion){try{await copyQualityRuleVersion(props.datasetId,row.id);qualityVersions.value=(await listQualityRuleVersions(props.datasetId)).data||[];ElMessage.success('已复制为新草稿')}catch(e){ElMessage.error(e instanceof Error?e.message:'复制草稿失败')}}
async function publishRuleVersion(row:QualityRuleVersion){try{await ElMessageBox.confirm(`发布 v${row.version} 后该版本将不可修改，是否继续？`,'发布质量规则');await publishQualityRuleVersion(props.datasetId,row.id);qualityVersions.value=(await listQualityRuleVersions(props.datasetId)).data||[];ElMessage.success('质量规则版本已发布')}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(e instanceof Error?e.message:'发布规则失败')}}
async function createTask(){if(!taskRuleSetId.value||!taskName.value.trim()){ElMessage.warning('请填写任务名称并选择已发布规则版本');return}taskSaving.value=true;try{await createQualityTask(props.datasetId,{name:taskName.value.trim(),ruleSetId:taskRuleSetId.value,scanScope:taskScope.value,timeoutSeconds:120,scheduleType:taskScheduleType.value,intervalMinutes:taskIntervalMinutes.value,retryLimit:taskRetryLimit.value});taskName.value='';qualityTasks.value=(await listQualityTasks(props.datasetId)).data||[];ElMessage.success(taskScheduleType.value==='INTERVAL'?'定时质量任务已创建':'质量任务已创建')}catch(e){ElMessage.error(e instanceof Error?e.message:'创建质量任务失败')}finally{taskSaving.value=false}}
async function runTask(row:QualityTask){try{await triggerQualityTask(row.id);ElMessage.success('任务已加入执行队列');await showTaskRuns(row)}catch(e){ElMessage.error(e instanceof Error?e.message:'触发质量任务失败')}}
async function toggleTask(row:QualityTask){try{await setQualityTaskStatus(row.id,row.status==='ENABLED'?'PAUSED':'ENABLED');qualityTasks.value=(await listQualityTasks(props.datasetId)).data||[]}catch(e){ElMessage.error(e instanceof Error?e.message:'更新任务状态失败')}}
async function showTaskRuns(row:QualityTask){try{taskRuns.value=(await listQualityTaskRuns(row.id)).data||[];taskRunsVisible.value=true}catch(e){ElMessage.error(e instanceof Error?e.message:'读取任务记录失败')}}
async function claimIssue(row:QualityIssue){try{await claimQualityIssue(props.datasetId,row.id);qualityIssues.value=(await listQualityIssues(props.datasetId)).data||[];ElMessage.success('问题已认领')}catch(e){ElMessage.error(e instanceof Error?e.message:'认领问题失败')}}
async function resolveIssue(row:QualityIssue){try{const {value}=await ElMessageBox.prompt('填写修复说明。问题将进入「待复验」，须重新运行质量任务且同规则通过后才会关闭。','标记已修复（待复验）',{inputType:'textarea',inputValidator:value=>!!value?.trim()&&value.trim().length<=1000||'请输入不超过 1000 字的修复说明'});await resolveQualityIssue(props.datasetId,row.id,String(value));qualityIssues.value=(await listQualityIssues(props.datasetId)).data||[];ElMessage.success('已标记为待复验')}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(e instanceof Error?e.message:'标记已修复失败')}}
async function showIssueHistory(row:QualityIssue){try{issueHistory.value=(await listQualityIssueHistory(props.datasetId,row.id)).data||[];issueHistoryVisible.value=true}catch(e){ElMessage.error(e instanceof Error?e.message:'读取问题历史失败')}}

async function saveFieldLabel(row: DatasetField) {
  if (!row.id || !props.datasetId) return
  saving.value = true
  try {
    await updateDatasetField(props.datasetId, row.id, { showName: row.showName?.trim() || row.originName, dimFlag: row.dimFlag as '0' | '1' })
    ElMessage.success('已保存')
    emit('saved')
  } finally { saving.value = false }
}

async function load() {
  if (!props.datasetId) return
  const detail = await getDataset(props.datasetId)
  const row = detail.data!
  sourceCatalog.value = row.sourceCatalog || ''
  leftFields.value = row.fields || []
  const sources = await listModelSources()
  const source = sources.data?.find(s => s.id === row.datasourceId)
  schemaName.value = source?.schemaName || ''
  if (schemaName.value) {
    tablesLoading.value = true
    try {
      tables.value = (await listTables(schemaName.value, sourceCatalog.value)).data || []
    } finally { tablesLoading.value = false }
  }
  const model = await getDatasetModel(props.datasetId)
  const saved = model.data?.join as DatasetJoinModel | undefined
  const steps=saved?.joins?.length?saved.joins:saved?.rightTable?[{joinType:(saved.joinType||'LEFT') as DatasetJoinStep['joinType'],rightTable:saved.rightTable,leftKey:saved.leftKey||'',rightKey:saved.rightKey||'',rightFields:saved.rightFields||[]}]:[]
  joinSteps.value=steps.length?steps.map(s=>({...s,rightFields:[...(s.rightFields||[])]})):[blankJoin()];activeJoinIndex.value=0
  if (activeJoin.value.rightTable) await rightTableChanged()
}

function tabChanged(name:string|number){if(name==='quality'&&!quality.value)void loadQuality()}

function toggleRight(name: string, on: boolean) {
  const step=activeJoin.value
  if (on && !step.rightFields.includes(name)) step.rightFields.push(name)
  else step.rightFields = step.rightFields.filter(n => n !== name)
}

function selectJoin(index:number){activeJoinIndex.value=index;rightColumns.value=[];if(activeJoin.value.rightTable)void rightTableChanged()}
function addJoin(){if(joinSteps.value.length>=8)return;joinSteps.value.push(blankJoin());selectJoin(joinSteps.value.length-1)}
function removeJoin(index:number){if(joinSteps.value.length<=1)return;joinSteps.value.splice(index,1);selectJoin(Math.min(activeJoinIndex.value,joinSteps.value.length-1))}

async function rightTableChanged() {
  if (!schemaName.value || !activeJoin.value.rightTable) { rightColumns.value = []; return }
  columnsLoading.value = true
  try {
    rightColumns.value = (await listColumns(schemaName.value, activeJoin.value.rightTable, sourceCatalog.value)).data || []
  } finally { columnsLoading.value = false }
}

async function saveJoin() {
  if (joinSteps.value.some(step=>!step.rightTable||!step.leftKey||!step.rightKey||!step.rightFields.length)) { ElMessage.warning('请完整填写每条关联的表、左右键和输出字段'); return }
  saving.value = true
  try {
    const first=joinSteps.value[0]
    await applyDatasetJoin(props.datasetId, { ...first, joins:joinSteps.value.map(step=>({...step,rightFields:[...step.rightFields]})) })
    ElMessage.success(`已应用 ${joinSteps.value.length} 条关联`)
    emit('saved')
    await load()
  } finally { saving.value = false }
}

async function clearJoin() {
  await ElMessageBox.confirm('确定移除关联并删除右表字段？', '提示')
  saving.value = true
  try {
    await clearDatasetJoin(props.datasetId)
    ElMessage.success('已移除关联')
    joinSteps.value=[blankJoin()];activeJoinIndex.value=0
    emit('saved')
    await load()
  } finally { saving.value = false }
}

async function addCalc() {
  if (!calc.originName || !calc.formula) { ElMessage.warning('请填写字段名与表达式'); return }
  saving.value = true
  try {
    await addCalculatedField(props.datasetId, { ...calc })
    ElMessage.success('已添加')
    calc.originName = ''; calc.showName = ''; calc.formula = ''
    emit('saved')
    const detail = await getDataset(props.datasetId)
    leftFields.value = detail.data?.fields || []
  } finally { saving.value = false }
}

async function removeCalc(row: DatasetField) {
  if (!row.id) return
  saving.value = true
  try {
    await removeCalculatedField(props.datasetId, row.id)
    ElMessage.success('已删除')
    emit('saved')
    leftFields.value = leftFields.value.filter(f => f.id !== row.id)
  } finally { saving.value = false }
}

async function reloadFields(){const detail=await getDataset(props.datasetId);leftFields.value=detail.data?.fields||[]}
async function moveCalc(row:DatasetField,direction:number){const ordered=[...calcFields.value];const from=ordered.findIndex(f=>f.id===row.id),to=from+direction;if(from<0||to<0||to>=ordered.length)return;[ordered[from],ordered[to]]=[ordered[to]!,ordered[from]!];saving.value=true;try{await reorderCalculatedFields(props.datasetId,ordered.map(f=>f.id!).filter(Boolean));await reloadFields();ElMessage.success('清洗步骤顺序已更新')}catch(e){ElMessage.error(e instanceof Error?e.message:'步骤顺序无效，请先处理字段依赖')}finally{saving.value=false}}
async function replaySteps(){replayLoading.value=true;try{const result=await previewDataset(props.datasetId,20);replayResult.value=result.data;replayVisible.value=true}catch(e){ElMessage.error(e instanceof Error?e.message:'回放失败，请检查数据源和当前配置')}finally{replayLoading.value=false}}
</script>

<style scoped>
.footer { display: flex; gap: 8px; justify-content: flex-end; margin-top: 12px; }
.mb8 { margin-bottom: 8px; }
.mt8 { margin-top: 8px; }
.join-steps{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:0 0 14px 100px}.join-step{cursor:pointer}.remove-step{margin-left:6px;vertical-align:-2px}
.calc-actions{display:flex;gap:8px}
</style>
