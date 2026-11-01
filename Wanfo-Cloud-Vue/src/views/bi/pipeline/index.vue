<template>
  <div class="pipeline-page bi-page" v-loading="loading">
    <header class="page-head">
      <div><div class="eyebrow">DATA ENGINEERING</div><h1>数据加工流程</h1><p>用版本化 DAG 编排数据集、关联、清洗、质量门禁和数仓逻辑层。</p></div>
      <el-button v-hasPermi="['bi:pipeline:add']" type="primary" :icon="Plus" @click="createVisible = true">新建流程</el-button>
    </header>

    <div class="workspace">
      <aside class="pipeline-list">
        <div class="list-title">流程列表 <span>{{ pipelines.length }}</span></div>
        <button v-for="item in pipelines" :key="item.id" class="pipeline-item" :class="{ active: selected?.id === item.id }" @click="loadPipeline(item.id)">
          <span class="pipeline-mark"><el-icon><Connection /></el-icon></span>
          <span class="pipeline-copy"><strong>{{ item.name }}</strong><small>{{ item.pipelineCode }} · {{ item.status }}</small></span>
        </button>
        <el-empty v-if="!pipelines.length" description="还没有流程" :image-size="64" />
      </aside>

      <main v-if="selected" class="editor">
        <div class="editor-head">
          <div class="editor-title"><div><h2>{{ selected.name }}</h2><span>{{ selected.description || '未添加说明' }}</span></div>
            <el-select v-model="revisionId" placeholder="选择版本" class="revision-select" @change="loadRevision">
              <el-option v-for="revision in selected.revisions" :key="revision.id" :label="`v${revision.revision} · ${revision.status === 'DRAFT' ? '草稿' : '已发布'}${selected.currentRevisionId === revision.id ? ' · 当前生效' : ''}`" :value="revision.id" />
            </el-select>
          </div>
          <div class="editor-actions">
            <el-tag v-if="activeRevision" :type="activeRevision.status === 'DRAFT' ? 'warning' : selected.currentRevisionId === activeRevision.id ? 'success' : 'info'" effect="light">{{ activeRevision.status === 'DRAFT' ? '草稿版本' : selected.currentRevisionId === activeRevision.id ? '当前生效版本' : '历史发布版本' }}</el-tag>
            <el-button v-if="selected.revisions.length > 1" @click="openDiff">版本对比</el-button>
            <el-button v-if="activeRevision?.status === 'PUBLISHED' && selected.currentRevisionId !== activeRevision.id && checkPermi(['bi:pipeline:rollback']) && checkPermi(['bi:pipeline:edit'])" :loading="rollingBack" @click="rollbackRevision">回滚此版本</el-button>
            <el-button @click="openPublishHistory">发布历史</el-button>
            <el-button v-if="activeRevision" v-hasPermi="['bi:pipeline:edit']" :disabled="saving" @click="copyRevision">复制为草稿</el-button>
            <el-button v-if="activeRevision?.status === 'DRAFT'" v-hasPermi="['bi:pipeline:edit']" :loading="saving" @click="saveDraft">保存草稿</el-button>
            <el-button v-if="activeRevision?.status === 'DRAFT'" v-hasPermi="['bi:pipeline:publish']" type="primary" :loading="publishing" @click="publishRevision">校验并发布</el-button>
            <el-button v-if="activeRevision?.status === 'PUBLISHED'" v-hasPermi="['bi:pipeline:run']" type="success" :loading="submittingRun" @click="submitPreviewRun">运行预览</el-button>
            <el-button v-if="activeRevision?.status === 'PUBLISHED'" v-hasPermi="['bi:pipeline:run']" type="warning" :loading="submittingProduction" @click="openProductionDialog">生产运行</el-button>
            <el-button v-if="selected.currentRevisionId" v-hasPermi="['bi:pipeline:run']" @click="openScheduleDialog">生产调度</el-button>
          </div>
        </div>

        <div class="design-stage">
          <aside class="palette-panel">
            <div class="palette-title">节点库</div>
            <p class="palette-hint">拖到画布添加；端口连线</p>
            <button
              v-for="type in nodeTypes"
              :key="type.key"
              class="palette-item"
              type="button"
              :draggable="editable"
              :disabled="!editable"
              @dragstart="onPaletteDragStart($event, type.key)"
              @click="addNode(type.key)"
            >
              <span class="palette-icon"><el-icon><component :is="type.icon" /></el-icon></span>
              <span><strong>{{ type.label }}</strong><small>{{ type.hint }}</small></span>
            </button>
          </aside>
          <div class="canvas-panel">
            <PipelineFlowCanvas
              :nodes="nodes"
              :edges="edges"
              :editable="editable"
              :selected-key="selectedNodeKey"
              :type-label="nodeLabel"
              :node-summary="nodeSummary"
              @select="selectedNodeKey = $event"
              @add-node="onCanvasAddNode"
              @remove-node="removeNode"
              @move-node="onMoveNode"
              @add-edge="onAddEdge"
              @remove-edge="onRemoveEdge"
            />
            <div class="editor-foot">
              <span>拖拽编排 · 选中边后 Delete 可删除连线</span>
              <span>当前版本 {{ activeRevision ? `v${activeRevision.revision}` : '—' }} · {{ nodes.length }} 节点 · {{ edges.length }} 连线</span>
            </div>
          </div>
        </div>
        <div v-if="selectedNode" class="node-inspector">
          <div class="inspector-title"><div><strong>{{ selectedNode.name }}</strong><el-tag size="small">{{ nodeLabel(selectedNode.type) }}</el-tag></div><el-button text @click="selectedNodeKey = ''">收起</el-button></div>
          <div class="inspector-form">
            <el-form-item label="节点名称"><el-input v-model="selectedNode.name" :disabled="!editable" maxlength="160" /></el-form-item>
            <el-form-item v-if="selectedNode.type === 'SOURCE_DATASET'" label="数据集"><el-select :model-value="Number(selectedNode.config.datasetId || 0) || undefined" :disabled="!editable" filterable placeholder="选择有权限的数据集" @update:model-value="setConfigValue('datasetId', $event)"><el-option v-for="dataset in datasets" :key="dataset.id" :label="`${dataset.name} · #${dataset.id}`" :value="dataset.id" /></el-select></el-form-item>
            <el-form-item v-if="selectedNode.type === 'JOIN'" label="关联方式"><el-select :model-value="String(selectedNode.config.joinType || 'LEFT')" :disabled="!editable" @update:model-value="setConfigValue('joinType', $event)"><el-option v-for="join in ['INNER','LEFT','RIGHT','FULL']" :key="join" :label="join" :value="join" /></el-select></el-form-item>
            <el-form-item v-if="selectedNode.type === 'JOIN'" label="左侧关联字段"><el-input :model-value="fieldList(selectedNode.config.leftKeys)" :disabled="!editable" placeholder="字段名，多个用逗号分隔" @update:model-value="setFieldList('leftKeys', $event)" /></el-form-item>
            <el-form-item v-if="selectedNode.type === 'JOIN'" label="右侧关联字段"><el-input :model-value="fieldList(selectedNode.config.rightKeys)" :disabled="!editable" placeholder="字段名，多个用逗号分隔" @update:model-value="setFieldList('rightKeys', $event)" /></el-form-item>
            <el-form-item v-if="selectedNode.type === 'WAREHOUSE_PUBLISH'" label="数仓层"><el-select :model-value="String(selectedNode.config.layer || 'DWD')" :disabled="!editable" @update:model-value="setConfigValue('layer', $event)"><el-option v-for="layer in ['DWD','DWS','ADS']" :key="layer" :label="layer" :value="layer" /></el-select></el-form-item>
            <el-form-item v-if="selectedNode.type === 'WAREHOUSE_PUBLISH'" label="模型编码"><el-input :model-value="String(selectedNode.config.modelCode || '')" :disabled="!editable" maxlength="96" @update:model-value="setConfigValue('modelCode', $event)" /></el-form-item>
            <el-form-item v-if="selectedNode.type === 'WAREHOUSE_PUBLISH'" label="写入模式"><el-select :model-value="String(selectedNode.config.writeMode || 'REPLACE')" :disabled="!editable" @update:model-value="setConfigValue('writeMode', $event)"><el-option label="REPLACE（整表替换）" value="REPLACE" /><el-option label="APPEND（追加写入）" value="APPEND" /></el-select></el-form-item>
            <template v-if="selectedNode.type === 'QUALITY_GATE'">
              <el-form-item label="规则所属数据集"><el-select :model-value="Number(selectedNode.config.ruleSetDatasetId || 0) || undefined" :disabled="!editable" filterable placeholder="选择有查询权限的数据集" @update:model-value="setQualityDataset"><el-option v-for="dataset in datasets" :key="dataset.id" :label="`${dataset.name} · #${dataset.id}`" :value="dataset.id" /></el-select></el-form-item>
              <el-form-item label="已发布规则版本"><el-select :model-value="Number(selectedNode.config.ruleSetId || 0) || undefined" :disabled="!editable || !selectedNode.config.ruleSetDatasetId" filterable placeholder="选择规则版本" @update:model-value="setConfigValue('ruleSetId', $event)"><el-option v-for="version in qualityRuleSets" :key="version.id" :label="`v${version.version} · ${version.name}`" :value="version.id" /></el-select></el-form-item>
              <el-form-item label="最低通过率"><el-input-number :model-value="Number(selectedNode.config.minPassRate ?? 100)" :disabled="!editable" :min="0" :max="100" :precision="2" :step="1" @update:model-value="setConfigValue('minPassRate', $event)" /><span class="unit">%</span></el-form-item>
              <el-form-item label="阻断等级起点"><el-select :model-value="String(selectedNode.config.severity || 'ERROR')" :disabled="!editable" @update:model-value="setConfigValue('severity', $event)"><el-option v-for="level in ['INFO','WARN','ERROR','FATAL']" :key="level" :label="level" :value="level" /></el-select></el-form-item>
              <el-alert class="gate-note" title="预览门禁最多评估 100 行样本；失败会阻断后续节点，但不代表全量数据质量通过。" type="info" :closable="false" />
            </template>
            <div v-if="selectedNode.type === 'FILTER'" class="config-block">
              <div class="config-block-head"><strong>筛选条件</strong><el-button v-if="editable" size="small" @click="addFilterCondition">添加条件</el-button></div>
              <div v-for="(row, idx) in filterConditions" :key="idx" class="config-row">
                <el-input v-model="row.field" :disabled="!editable" placeholder="字段" />
                <el-select v-model="row.operator" :disabled="!editable"><el-option v-for="op in filterOperators" :key="op" :label="op" :value="op" /></el-select>
                <el-input v-model="row.value" :disabled="!editable || row.operator === 'IS_NULL' || row.operator === 'NOT_NULL'" placeholder="值" />
                <el-button v-if="editable" text type="danger" @click="removeFilterCondition(idx)">删除</el-button>
              </div>
              <p v-if="!filterConditions.length" class="config-empty">暂无条件；AND 关系生效</p>
            </div>
            <div v-if="selectedNode.type === 'TRANSFORM'" class="config-block">
              <div class="config-block-head"><strong>字段映射</strong><el-button v-if="editable" size="small" @click="addTransformMapping">添加映射</el-button></div>
              <div v-for="(row, idx) in transformMappings" :key="idx" class="config-row">
                <el-input v-model="row.source" :disabled="!editable" placeholder="源字段" />
                <span class="arrow">→</span>
                <el-input v-model="row.target" :disabled="!editable" placeholder="目标字段" />
                <el-button v-if="editable" text type="danger" @click="removeTransformMapping(idx)">删除</el-button>
              </div>
              <p v-if="!transformMappings.length" class="config-empty">空映射表示透传全部字段</p>
            </div>
            <p class="inspector-note">凭证和任意 SQL 不允许放入流程配置。保存和发布会重新执行服务端校验。</p>
          </div>
        </div>
        <section class="run-history">
          <div class="run-history-head"><div><strong>运行记录</strong><span>预览为只读样本（每来源最多 100 行）；生产运行会写入数仓逻辑层</span></div><el-button link :loading="runLoading" @click="loadRuns(selected.id)">刷新</el-button></div>
          <el-table :data="runRows" size="small" :empty-text="runLoading ? '读取中' : '暂无运行记录'">
            <el-table-column prop="id" label="运行号" width="86" />
            <el-table-column label="模式" width="92"><template #default="{ row }"><el-tag size="small" :type="row.runMode === 'PRODUCTION' ? 'warning' : 'info'" effect="plain">{{ runModeLabel(row.runMode) }}</el-tag></template></el-table-column>
            <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag size="small" :type="runTagType(row.status)">{{ runStatusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="版本" width="80"><template #default="{ row }">v{{ revisionNumber(row.revisionId) }}</template></el-table-column>
            <el-table-column prop="triggerBy" label="触发人" width="120" show-overflow-tooltip />
            <el-table-column prop="createTime" label="开始时间" min-width="160" />
            <el-table-column label="耗时" width="90"><template #default="{ row }">{{ row.durationMs == null ? '—' : `${row.durationMs} ms` }}</template></el-table-column>
            <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="showRun(row.id)">节点明细</el-button><el-button v-if="['PENDING','RUNNING'].includes(row.status)" v-hasPermi="['bi:pipeline:run']" link type="danger" @click="cancelRun(row.id)">取消</el-button></template></el-table-column>
            <el-table-column type="expand"><template #default="{ row }"><div v-if="row.errorSummary" class="run-error">{{ row.errorSummary }}</div></template></el-table-column>
          </el-table>
        </section>
      </main>
      <main v-else class="editor editor-empty"><el-empty description="选择左侧流程，或新建数据加工流程" /></main>
    </div>

    <el-dialog v-model="createVisible" title="新建数据加工流程" width="520px" destroy-on-close>
      <el-form label-position="top"><el-form-item label="流程编码"><el-input v-model="createForm.code" placeholder="例如 retail_daily" maxlength="96" /></el-form-item><el-form-item label="流程名称"><el-input v-model="createForm.name" maxlength="160" /></el-form-item><el-form-item label="说明"><el-input v-model="createForm.description" type="textarea" maxlength="1000" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="create">创建流程</el-button></template>
    </el-dialog>
    <el-dialog v-model="productionVisible" title="提交生产运行" width="520px" destroy-on-close>
      <el-alert type="warning" :closable="false" show-icon title="生产运行会按已发布 DAG 写入数仓逻辑层，请确认目标数据源与行数上限。" class="mb8" />
      <el-form label-width="120px">
        <el-form-item label="数仓目标" required><el-select v-model="productionForm.targetDatasourceId" filterable placeholder="选择 Doris / StarRocks / ClickHouse 目标" style="width:100%"><el-option v-for="ds in warehouseTargets" :key="ds.id" :label="`${ds.name} · ${ds.type}`" :value="ds.id" /></el-select></el-form-item>
        <el-form-item label="超时（秒）"><el-input-number v-model="productionForm.timeoutSeconds" :min="30" :max="1800" :step="30" /></el-form-item>
        <el-form-item label="行数上限"><el-input-number v-model="productionForm.maxRows" :min="1000" :max="1000000" :step="10000" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="productionVisible = false">取消</el-button><el-button type="primary" :loading="submittingProduction" @click="submitProductionRun">提交生产运行</el-button></template>
    </el-dialog>
    <el-dialog v-model="scheduleVisible" title="流程生产调度" width="640px" destroy-on-close @open="loadSchedule">
      <el-form label-width="110px">
        <el-form-item label="调度名称"><el-input v-model="scheduleForm.name" maxlength="160" placeholder="例如 nightly_retail_dwd" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="scheduleForm.enabled" /></el-form-item>
        <el-form-item label="绑定版本"><el-select v-model="scheduleForm.revisionId" clearable placeholder="留空则使用当前发布版本" style="width:100%"><el-option v-for="revision in selected?.revisions.filter(r => r.status === 'PUBLISHED') || []" :key="revision.id" :label="revisionLabel(revision)" :value="revision.id" /></el-select></el-form-item>
        <el-form-item label="数仓目标" required><el-select v-model="scheduleForm.targetDatasourceId" filterable style="width:100%"><el-option v-for="ds in warehouseTargets" :key="ds.id" :label="`${ds.name} · ${ds.type}`" :value="ds.id" /></el-select></el-form-item>
        <el-form-item label="执行计划"><el-select v-model="scheduleForm.schedule.kind"><el-option label="按间隔" value="interval" /><el-option label="每天" value="daily" /><el-option label="每周" value="weekly" /></el-select>
          <el-select v-if="scheduleForm.schedule.kind === 'interval'" v-model="scheduleForm.schedule.intervalMinutes" class="ml8"><el-option label="每 15 分钟" :value="15" /><el-option label="每小时" :value="60" /><el-option label="每天" :value="1440" /><el-option label="每周" :value="10080" /></el-select>
          <el-time-picker v-else v-model="scheduleForm.schedule.localTime" format="HH:mm" value-format="HH:mm" class="ml8" placeholder="本地执行时间" />
        </el-form-item>
        <el-form-item v-if="scheduleForm.schedule.kind === 'weekly'" label="执行星期"><el-checkbox-group v-model="scheduleForm.schedule.weekdays"><el-checkbox v-for="day in weekdayOptions" :key="day.value" :value="day.value">{{ day.label }}</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item label="执行时区"><el-select v-model="scheduleForm.schedule.timezone" filterable allow-create default-first-option style="width:100%"><el-option v-for="zone in timezoneOptions" :key="zone" :label="zone" :value="zone" /></el-select></el-form-item>
        <el-form-item label="超时（秒）"><el-input-number v-model="scheduleForm.timeoutSeconds" :min="30" :max="1800" /></el-form-item>
        <el-form-item label="行数上限"><el-input-number v-model="scheduleForm.maxRows" :min="1" :max="1000000" :step="10000" /></el-form-item>
        <el-alert v-if="scheduleMeta.lastMessage" type="info" :closable="false" :title="`最近调度：${scheduleMeta.lastStatus || '—'} · ${scheduleMeta.lastMessage}`" />
      </el-form>
      <template #footer>
        <el-button v-if="scheduleMeta.hasSchedule" type="danger" plain :loading="scheduleSaving" @click="disableSchedule">停用调度</el-button>
        <el-button @click="scheduleVisible = false">关闭</el-button>
        <el-button type="primary" :loading="scheduleSaving" @click="saveSchedule">保存调度</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="runDetailVisible" :title="selectedRun?.runMode === 'PRODUCTION' ? '生产运行节点明细' : '预览运行节点明细'" width="780px" destroy-on-close>
      <el-table v-if="selectedRun" :data="selectedRun.nodes || []" size="small">
        <el-table-column prop="nodeKey" label="节点" min-width="150" />
        <el-table-column label="类型" width="120"><template #default="{ row }">{{ nodeLabel(row.nodeType) }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="runTagType(row.status)">{{ runStatusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="inputRowCount" label="输入样本" width="100" />
        <el-table-column prop="outputRowCount" label="输出样本" width="100" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column label="节点结果" min-width="220"><template #default="{ row }"><pre v-if="row.summary" class="node-summary">{{ formatJson(row.summary) }}</pre><span v-else>—</span></template></el-table-column>
        <el-table-column prop="errorSummary" label="说明" min-width="180" show-overflow-tooltip />
      </el-table>
      <template #footer><el-button @click="runDetailVisible = false">关闭</el-button></template>
    </el-dialog>
    <el-dialog v-model="diffVisible" title="流程版本差异" width="840px" destroy-on-close>
      <div class="diff-controls">
        <el-select v-model="diffFromId" placeholder="基准版本"><el-option v-for="revision in selected?.revisions || []" :key="revision.id" :label="revisionLabel(revision)" :value="revision.id" /></el-select>
        <span>对比到</span>
        <el-select v-model="diffToId" placeholder="目标版本"><el-option v-for="revision in selected?.revisions || []" :key="revision.id" :label="revisionLabel(revision)" :value="revision.id" /></el-select>
        <el-button type="primary" :loading="diffLoading" @click="loadDiff">比较</el-button>
      </div>
      <el-alert v-if="diffResult" :title="diffResult.same ? '两个版本定义一致' : '发现版本差异'" :type="diffResult.same ? 'success' : 'info'" :closable="false" show-icon />
      <div v-if="diffResult" class="diff-grid">
        <div class="diff-card"><strong>节点</strong><span>新增 {{ diffResult.addedNodes.length }} · 删除 {{ diffResult.removedNodes.length }} · 修改 {{ diffResult.changedNodes.length }}</span><pre>{{ formatJson({ added: diffResult.addedNodes, removed: diffResult.removedNodes, changed: diffResult.changedNodes }) }}</pre></div>
        <div class="diff-card"><strong>连线</strong><span>新增 {{ diffResult.addedEdges.length }} · 删除 {{ diffResult.removedEdges.length }}</span><pre>{{ formatJson({ added: diffResult.addedEdges, removed: diffResult.removedEdges }) }}</pre></div>
        <div class="diff-card"><strong>流程参数与结构</strong><span>{{ diffResult.changedMetadata.length ? diffResult.changedMetadata.join('、') : '无变化' }}</span><pre>{{ formatJson({ from: diffResult.from, to: diffResult.to }) }}</pre></div>
      </div>
      <template #footer><el-button @click="diffVisible = false">关闭</el-button></template>
    </el-dialog>
    <el-dialog v-model="publishHistoryVisible" title="发布与回滚历史" width="760px" destroy-on-close>
      <el-table :data="publishHistoryRows" size="small" :empty-text="historyLoading ? '读取中' : '暂无发布记录'">
        <el-table-column label="操作" width="110"><template #default="{ row }"><el-tag size="small" :type="row.actionType === 'ROLLBACK' ? 'warning' : 'success'">{{ row.actionType === 'ROLLBACK' ? '回滚发布' : '发布' }}</el-tag></template></el-table-column>
        <el-table-column label="版本变更" min-width="130"><template #default="{ row }">v{{ revisionNumber(row.previousRevisionId) }} → v{{ revisionNumber(row.revisionId) }}</template></el-table-column>
        <el-table-column prop="actionBy" label="操作人" width="130" />
        <el-table-column prop="createTime" label="时间" min-width="170" />
        <el-table-column prop="reason" label="说明" min-width="180" show-overflow-tooltip />
      </el-table>
      <template #footer><el-button @click="publishHistoryVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="BiPipeline">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Connection, Coin, Filter, Finished, Operation, Plus, Share } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDatasets, listQualityRuleVersions, type DatasetSummary, type QualityRuleVersion } from '@/api/bi/dataset'
import { checkPermi } from '@/utils/permission'
import { cancelPipelineRun, copyPipelineRevision, createPipeline, diffPipelineRevisions, disablePipelineSchedule, getPipeline, getPipelineRun, getPipelineSchedule, listPipelinePublishHistory, listPipelineRuns, listPipelines, previewPipelineRevision, productionPipelineRevision, publishPipelineRevision, rollbackPipelineRevision, savePipelineRevision, savePipelineSchedule, type PipelineDefinition, type PipelineDiff, type PipelineEdge, type PipelineNode, type PipelinePublishHistory, type PipelineRevision, type PipelineRun, type PipelineScheduleSave } from '@/api/bi/pipeline'
import type { SubscriptionSchedule } from '@/api/bi/subscription'
import { listDatasources, type BiDatasource } from '@/api/bi/datasource'
import { unwrapBiData } from '@/api/bi/response'
import PipelineFlowCanvas from './PipelineFlowCanvas.vue'

const loading=ref(false);const saving=ref(false);const publishing=ref(false);const submittingRun=ref(false);const submittingProduction=ref(false);const productionVisible=ref(false);const scheduleVisible=ref(false);const scheduleSaving=ref(false);const rollingBack=ref(false);const diffVisible=ref(false);const diffLoading=ref(false);const diffFromId=ref<number>();const diffToId=ref<number>();const diffResult=ref<PipelineDiff>();const publishHistoryVisible=ref(false);const historyLoading=ref(false);const publishHistoryRows=ref<PipelinePublishHistory[]>([]);const runLoading=ref(false);const runRows=ref<PipelineRun[]>([]);const selectedRun=ref<PipelineRun>();const runDetailVisible=ref(false);const pipelines=ref<PipelineDefinition[]>([]);const datasets=ref<DatasetSummary[]>([]);const datasources=ref<BiDatasource[]>([]);
const weekdayOptions=[{label:'周一',value:1},{label:'周二',value:2},{label:'周三',value:3},{label:'周四',value:4},{label:'周五',value:5},{label:'周六',value:6},{label:'周日',value:7}]
const timezoneOptions=['Asia/Shanghai','Asia/Tokyo','UTC']
const productionForm=reactive({targetDatasourceId:undefined as number|undefined,timeoutSeconds:300,maxRows:100000})
const scheduleForm=reactive<{name:string;enabled:boolean;revisionId?:number;targetDatasourceId?:number;maxRows:number;timeoutSeconds:number;schedule:SubscriptionSchedule}>({name:'',enabled:true,maxRows:100000,timeoutSeconds:300,schedule:{kind:'interval',intervalMinutes:1440,localTime:'02:00',weekdays:[],timezone:'Asia/Shanghai'}})
const scheduleMeta=reactive({hasSchedule:false,lastStatus:'',lastMessage:''})
const warehouseTargets=computed(()=>datasources.value.filter(ds=>['clickhouse','ck','doris','starrocks'].includes(String(ds.type||'').toLowerCase())))
const qualityRuleSets=ref<QualityRuleVersion[]>([])
const selected=ref<PipelineDefinition>()
const revisionId=ref<number>()
const selectedNodeKey=ref('')
const createVisible=ref(false)
let runPollTimer:number|undefined
const createForm=reactive({code:'',name:'',description:''})
const filterOperators=['EQ','NE','GT','GTE','LT','LTE','CONTAINS','IS_NULL','NOT_NULL']
const nodeTypes=[
  {key:'SOURCE_DATASET',label:'数据集',hint:'流程起点',icon:Coin},
  {key:'JOIN',label:'关联',hint:'双路输入',icon:Share},
  {key:'FILTER',label:'筛选',hint:'条件过滤',icon:Filter},
  {key:'TRANSFORM',label:'转换',hint:'字段映射',icon:Operation},
  {key:'QUALITY_GATE',label:'质量门禁',hint:'样本验收',icon:Finished},
  {key:'WAREHOUSE_PUBLISH',label:'发布数仓',hint:'DWD/DWS/ADS',icon:Connection}
]
const activeRevision=computed(()=>selected.value?.revisions.find(item=>item.id===revisionId.value))
const nodes=computed(()=>activeRevision.value?.nodes||[])
const edges=computed(()=>activeRevision.value?.edges||[])
const selectedNode=computed(()=>nodes.value.find(node=>node.key===selectedNodeKey.value))
const editable=computed(()=>activeRevision.value?.status==='DRAFT'&&checkPermi(['bi:pipeline:edit']))
const filterConditions=computed(()=>{
  if(!selectedNode.value||selectedNode.value.type!=='FILTER') return [] as Array<{field:string;operator:string;value?:string}>
  if(!Array.isArray(selectedNode.value.config.conditions)) selectedNode.value.config.conditions=[]
  return selectedNode.value.config.conditions as Array<{field:string;operator:string;value?:string}>
})
const transformMappings=computed(()=>{
  if(!selectedNode.value||selectedNode.value.type!=='TRANSFORM') return [] as Array<{source:string;target:string}>
  if(!Array.isArray(selectedNode.value.config.mappings)) selectedNode.value.config.mappings=[]
  return selectedNode.value.config.mappings as Array<{source:string;target:string}>
})
watch(()=>Number(selectedNode.value?.config.ruleSetDatasetId||0),datasetId=>{void loadQualityRuleSets(datasetId)},{immediate:true})
onMounted(()=>{void loadList();void loadDatasets();void loadWarehouseTargets()});
async function loadWarehouseTargets(){try{datasources.value=unwrapBiData(await listDatasources())}catch{datasources.value=[]}}
function runRequestKey(){return globalThis.crypto?.randomUUID?.()||`run-${Date.now()}-${Math.random().toString(36).slice(2)}`}
function runModeLabel(mode:string){return mode==='PRODUCTION'?'生产':'预览'}
function openProductionDialog(){productionForm.targetDatasourceId=warehouseTargets.value[0]?.id;productionForm.timeoutSeconds=300;productionForm.maxRows=100000;productionVisible.value=true}
async function submitProductionRun(){const pipelineId=selected.value?.id,revision=activeRevision.value;if(!pipelineId||!revision||!productionForm.targetDatasourceId){ElMessage.warning('请选择数仓目标数据源');return}submittingProduction.value=true;try{await productionPipelineRevision(pipelineId,revision.id,{requestKey:runRequestKey(),timeoutSeconds:productionForm.timeoutSeconds,targetDatasourceId:productionForm.targetDatasourceId,maxRows:productionForm.maxRows});productionVisible.value=false;await loadRuns(pipelineId);ElMessage.success('生产运行已排队')}catch{ElMessage.error('无法提交生产运行：请确认运行权限、目标数据源及已发布版本')}finally{submittingProduction.value=false}}
async function openScheduleDialog(){scheduleVisible.value=true}
async function loadSchedule(){const pipelineId=selected.value?.id;if(!pipelineId)return;try{const row=unwrapBiData(await getPipelineSchedule(pipelineId));scheduleMeta.hasSchedule=Boolean(row?.id);scheduleMeta.lastStatus=String(row?.lastStatus||'');scheduleMeta.lastMessage=String(row?.lastMessage||'');if(row&&Object.keys(row).length){scheduleForm.name=String(row.name||`${selected.value?.pipelineCode||'pipeline'}_schedule`);scheduleForm.enabled=row.enabled!==false;scheduleForm.revisionId=row.revisionId;scheduleForm.targetDatasourceId=row.targetDatasourceId;scheduleForm.maxRows=Number(row.maxRows??100000);scheduleForm.timeoutSeconds=Number(row.timeoutSeconds??300);if(row.schedule)Object.assign(scheduleForm.schedule,row.schedule)}else{scheduleForm.name=`${selected.value?.pipelineCode||'pipeline'}_schedule`;scheduleForm.enabled=true;scheduleForm.revisionId=selected.value?.currentRevisionId;scheduleForm.targetDatasourceId=warehouseTargets.value[0]?.id}}catch{scheduleMeta.hasSchedule=false}}
async function saveSchedule(){const pipelineId=selected.value?.id;if(!pipelineId||!scheduleForm.targetDatasourceId){ElMessage.warning('请填写调度名称并选择数仓目标');return}if(scheduleForm.schedule.kind==='weekly'&&!scheduleForm.schedule.weekdays.length){ElMessage.warning('每周计划至少选择一天');return}scheduleSaving.value=true;try{const payload:PipelineScheduleSave={name:scheduleForm.name.trim()||`${selected.value?.pipelineCode}_schedule`,enabled:scheduleForm.enabled,revisionId:scheduleForm.revisionId,targetDatasourceId:scheduleForm.targetDatasourceId,maxRows:scheduleForm.maxRows,timeoutSeconds:scheduleForm.timeoutSeconds,schedule:scheduleForm.schedule,intervalMinutes:scheduleForm.schedule.intervalMinutes};await savePipelineSchedule(pipelineId,payload);await loadSchedule();ElMessage.success('生产调度已保存')}catch{ElMessage.error('保存调度失败，请检查版本、目标数据源与计划配置')}finally{scheduleSaving.value=false}}
async function disableSchedule(){const pipelineId=selected.value?.id;if(!pipelineId)return;try{await ElMessageBox.confirm('停用后将不再自动触发生产运行。','停用调度',{type:'warning'})}catch{return}scheduleSaving.value=true;try{await disablePipelineSchedule(pipelineId);await loadSchedule();scheduleForm.enabled=false;ElMessage.success('调度已停用')}catch{ElMessage.error('停用调度失败')}finally{scheduleSaving.value=false}}
onBeforeUnmount(()=>{if(runPollTimer)window.clearInterval(runPollTimer)})
async function loadDatasets(){try{datasets.value=unwrapBiData(await listDatasets())}catch{datasets.value=[]}}
async function loadList(){loading.value=true;try{pipelines.value=unwrapBiData(await listPipelines());if(selected.value)await loadPipeline(selected.value.id)}catch{ElMessage.error('读取流程列表失败')}finally{loading.value=false}}
async function loadPipeline(id:number){loading.value=true;try{const data=normalizePipeline(unwrapBiData(await getPipeline(id)));selected.value=data;revisionId.value=data.revisions.find(r=>r.status==='DRAFT')?.id||data.currentRevisionId||data.revisions[0]?.id;selectedNodeKey.value='';void loadRuns(id)}catch{ElMessage.error('读取流程详情失败')}finally{loading.value=false}}
function loadRevision(){selectedNodeKey.value=''}
async function create(){saving.value=true;try{const data=normalizePipeline(unwrapBiData(await createPipeline({...createForm})));createVisible.value=false;createForm.code='';createForm.name='';createForm.description='';selected.value=data;pipelines.value=unwrapBiData(await listPipelines());await loadPipeline(data.id);ElMessage.success('流程已创建')}catch{ElMessage.error('创建失败，请检查编码是否重复')}finally{saving.value=false}}
function newKey(type:string){const prefix=type.toLowerCase().replaceAll('_','-');let n=nodes.value.filter(item=>item.key.startsWith(prefix)).length+1;while(nodes.value.some(item=>item.key===`${prefix}-${n}`))n++;return `${prefix}-${n}`}
function defaultConfig(type:string):Record<string,unknown>{if(type==='SOURCE_DATASET')return {datasetId:null};if(type==='JOIN')return {joinType:'LEFT',leftKeys:[],rightKeys:[]};if(type==='WAREHOUSE_PUBLISH')return {layer:'DWD',modelCode:'',writeMode:'REPLACE'};if(type==='QUALITY_GATE')return {ruleSetDatasetId:null,ruleSetId:null,minPassRate:100,severity:'ERROR'};if(type==='FILTER')return {conditions:[]};return {mappings:[]}}
function addNode(type:string,x?:number,y?:number){if(!editable.value){ElMessage.warning('请先复制发布版本为草稿');return}const key=newKey(type);const px=x??(36+(nodes.value.length%3)*280);const py=y??(80+Math.floor(nodes.value.length/3)*140);nodes.value.push({key,type,name:nodeLabel(type),config:defaultConfig(type),inputSchema:{},outputSchema:{},x:px,y:py});selectedNodeKey.value=key}
function onPaletteDragStart(event:DragEvent,type:string){if(!editable.value){event.preventDefault();return}event.dataTransfer?.setData('application/pipeline-node-type',type);if(event.dataTransfer)event.dataTransfer.effectAllowed='copy'}
function onCanvasAddNode(payload:{type:string;x:number;y:number}){addNode(payload.type,payload.x,payload.y)}
function onMoveNode(payload:{key:string;x:number;y:number}){const node=nodes.value.find(n=>n.key===payload.key);if(node){node.x=payload.x;node.y=payload.y}}
function onAddEdge(edge:PipelineEdge){if(edges.value.some(e=>e.source===edge.source&&e.target===edge.target))return;edges.value.push(edge)}
function onRemoveEdge(payload:{source:string;target:string}){const revision=activeRevision.value;if(!revision)return;revision.edges=revision.edges.filter(e=>!(e.source===payload.source&&e.target===payload.target))}
function removeNode(key:string){const index=nodes.value.findIndex(n=>n.key===key);if(index>=0)nodes.value.splice(index,1);const revision=activeRevision.value;if(revision)revision.edges=revision.edges.filter(e=>e.source!==key&&e.target!==key);if(selectedNodeKey.value===key)selectedNodeKey.value=''}
function nodeLabel(type:string){return nodeTypes.find(item=>item.key===type)?.label||type}
function nodeSummary(node:PipelineNode){
  const cfg=node.config||{}
  if(node.type==='SOURCE_DATASET'){const id=Number(cfg.datasetId||0);const ds=datasets.value.find(d=>d.id===id);return ds?ds.name:(id?`#${id}`:'未选数据集')}
  if(node.type==='JOIN') return `${cfg.joinType||'LEFT'} · ${Array.isArray(cfg.leftKeys)?cfg.leftKeys.length:0} 键`
  if(node.type==='FILTER') return `${Array.isArray(cfg.conditions)?cfg.conditions.length:0} 条件`
  if(node.type==='TRANSFORM') return `${Array.isArray(cfg.mappings)?cfg.mappings.length:0} 映射`
  if(node.type==='QUALITY_GATE') return `通过率 ≥ ${cfg.minPassRate??100}%`
  if(node.type==='WAREHOUSE_PUBLISH') return `${cfg.layer||'DWD'} · ${cfg.modelCode||'未填编码'} · ${cfg.writeMode||'REPLACE'}`
  return ''
}
function addFilterCondition(){filterConditions.value.push({field:'',operator:'EQ',value:''})}
function removeFilterCondition(index:number){filterConditions.value.splice(index,1)}
function addTransformMapping(){transformMappings.value.push({source:'',target:''})}
function removeTransformMapping(index:number){transformMappings.value.splice(index,1)}
function setConfigValue(key:string,value:unknown){if(selectedNode.value)selectedNode.value.config[key]=value}
function setQualityDataset(value:number){if(!selectedNode.value)return;selectedNode.value.config.ruleSetDatasetId=value;delete selectedNode.value.config.ruleSetId;qualityRuleSets.value=[]}
async function loadQualityRuleSets(datasetId:number){if(!datasetId){qualityRuleSets.value=[];return}try{qualityRuleSets.value=unwrapBiData(await listQualityRuleVersions(datasetId)).filter(version=>version.status==='PUBLISHED')}catch{qualityRuleSets.value=[]}}
function fieldList(value:unknown){return Array.isArray(value)?value.join(', '):''}
function setFieldList(key:string,value:string){if(selectedNode.value)selectedNode.value.config[key]=value.split(',').map(item=>item.trim()).filter(Boolean)}
function definition(){return {nodes:nodes.value.map(node=>({key:node.key,type:node.type,name:node.name,config:node.config||{},inputSchema:node.inputSchema||{},outputSchema:node.outputSchema||{},x:node.x,y:node.y})),edges:edges.value.map(edge=>({...edge})),inputSchema:parseObject(activeRevision.value?.inputSchema),outputSchema:parseObject(activeRevision.value?.outputSchema),parameters:parseObject(activeRevision.value?.parameters),remark:activeRevision.value?.remark||'',expectedVersion:Number(activeRevision.value?.lockVersion??0)}}
function parseObject(value:unknown){if(typeof value==='string'){try{return JSON.parse(value)}catch{return {}}}return value&&typeof value==='object'?value:{}}
function normalizePipeline(value:PipelineDefinition):PipelineDefinition{return {...value,revisions:(value.revisions||[]).map(revision=>({...revision,inputSchema:parseObject(revision.inputSchema),outputSchema:parseObject(revision.outputSchema),parameters:parseObject(revision.parameters),nodes:(revision.nodes||[]).map(node=>({...node,key:(node as any).nodeKey||node.key,type:node.nodeType||node.type,x:Number(node.x??(node as any).positionX)||0,y:Number(node.y??(node as any).positionY)||0,config:parseObject(node.config),inputSchema:parseObject(node.inputSchema),outputSchema:parseObject(node.outputSchema)})),edges:(revision.edges||[]).map(edge=>({...edge,source:(edge as any).sourceNodeKey||edge.source,target:(edge as any).targetNodeKey||edge.target}))}))}}
async function saveDraft(){if(!selected.value||!activeRevision.value)return;saving.value=true;try{await savePipelineRevision(selected.value.id,activeRevision.value.id,definition());await loadPipeline(selected.value.id);ElMessage.success('草稿已保存，服务端 DAG 校验通过')}catch{ElMessage.error('保存失败：请检查来源数据集权限、节点配置和流程连线')}finally{saving.value=false}}
async function copyRevision(){const pipelineId=selected.value?.id,sourceId=activeRevision.value?.id;if(!pipelineId||!sourceId)return;saving.value=true;try{const data=normalizePipeline(unwrapBiData(await copyPipelineRevision(pipelineId,sourceId)));selected.value=data;revisionId.value=data.revisions.find(r=>r.status==='DRAFT')?.id;ElMessage.success('新草稿已创建')}catch{ElMessage.error('复制版本失败')}finally{saving.value=false}}
async function publishRevision(){const pipelineId=selected.value?.id,revision=activeRevision.value;if(!pipelineId||!revision)return;try{await ElMessageBox.confirm('发布后该版本将不可编辑。确定发布当前流程定义吗？','发布流程',{type:'warning'})}catch{return}publishing.value=true;try{const data=normalizePipeline(unwrapBiData(await publishPipelineRevision(pipelineId,revision.id)));selected.value=data;revisionId.value=data.revisions.find(r=>r.id===revision.id)?.id;await loadList();ElMessage.success('流程定义已校验并发布')}catch{ElMessage.error('发布失败：请检查所有节点均连接在来源与数仓目标之间')}finally{publishing.value=false}}
async function loadRuns(pipelineId:number){runLoading.value=true;try{runRows.value=unwrapBiData(await listPipelineRuns(pipelineId,20));if(runRows.value.some(run=>['PENDING','RUNNING'].includes(run.status))){if(!runPollTimer)runPollTimer=window.setInterval(()=>void loadRuns(pipelineId),2500)}else if(runPollTimer){window.clearInterval(runPollTimer);runPollTimer=undefined}}catch{runRows.value=[]}finally{runLoading.value=false}}
async function submitPreviewRun(){const pipelineId=selected.value?.id,revision=activeRevision.value;if(!pipelineId||!revision||revision.status!=='PUBLISHED')return;submittingRun.value=true;try{await previewPipelineRevision(pipelineId,revision.id,globalThis.crypto?.randomUUID?.()||`run-${Date.now()}-${Math.random().toString(36).slice(2)}`,30);await loadRuns(pipelineId);ElMessage.success('预览运行已排队')}catch{ElMessage.error('无法提交预览：请确认流程运行权限、数据集查询授权及节点配置')}finally{submittingRun.value=false}}
async function showRun(runId:number){try{selectedRun.value=unwrapBiData(await getPipelineRun(runId));runDetailVisible.value=true}catch{ElMessage.error('读取运行明细失败')}}
async function cancelRun(runId:number){try{await ElMessageBox.confirm('取消该预览运行？','取消运行',{type:'warning'});await cancelPipelineRun(runId);if(selected.value)await loadRuns(selected.value.id)}catch{}}
function revisionLabel(revision:PipelineRevision){return `v${revision.revision} · ${revision.status==='DRAFT'?'草稿':'已发布'}${selected.value?.currentRevisionId===revision.id?' · 当前生效':''}`}
function openDiff(){if(!selected.value)return;diffFromId.value=selected.value.currentRevisionId||selected.value.revisions.find(item=>item.status==='PUBLISHED')?.id;diffToId.value=revisionId.value;diffResult.value=undefined;diffVisible.value=true;if(diffFromId.value&&diffToId.value&&diffFromId.value!==diffToId.value)void loadDiff()}
async function loadDiff(){const pipelineId=selected.value?.id;if(!pipelineId||!diffFromId.value||!diffToId.value){ElMessage.warning('请选择两个要比较的版本');return}diffLoading.value=true;try{diffResult.value=unwrapBiData(await diffPipelineRevisions(pipelineId,diffFromId.value,diffToId.value))}catch{ElMessage.error('读取版本差异失败')}finally{diffLoading.value=false}}
async function rollbackRevision(){const pipelineId=selected.value?.id,revision=activeRevision.value;if(!pipelineId||!revision||revision.status!=='PUBLISHED')return;try{await ElMessageBox.confirm(`将从 v${revision.revision} 复制内容创建新的回滚草稿。当前生效版本不会改变，需检查并单独发布。继续吗？`,'创建回滚草稿',{type:'warning'})}catch{return}rollingBack.value=true;try{const data=normalizePipeline(unwrapBiData(await rollbackPipelineRevision(pipelineId,revision.id)));selected.value=data;revisionId.value=data.revisions.find(item=>item.rollbackFromRevisionId===revision.id&&item.status==='DRAFT')?.id;selectedNodeKey.value='';await loadList();ElMessage.success('回滚草稿已创建；检查差异并通过预览后，再单独发布生效')}catch{ElMessage.error('创建回滚草稿失败：请确认回滚权限和目标版本')}finally{rollingBack.value=false}}
async function openPublishHistory(){const pipelineId=selected.value?.id;if(!pipelineId)return;publishHistoryVisible.value=true;historyLoading.value=true;try{publishHistoryRows.value=unwrapBiData(await listPipelinePublishHistory(pipelineId,50))}catch{publishHistoryRows.value=[];ElMessage.error('读取发布历史失败')}finally{historyLoading.value=false}}
function formatJson(value:unknown){return JSON.stringify(value,null,2)}
function runStatusLabel(status:string){return ({PENDING:'排队中',RUNNING:'运行中',SUCCEEDED:'成功',FAILED:'失败',CANCELLED:'已取消',TIMED_OUT:'超时',SKIPPED:'跳过'} as Record<string,string>)[status]||status}
function runTagType(status:string):'primary'|'success'|'warning'|'info'|'danger'{return (({PENDING:'info',RUNNING:'warning',SUCCEEDED:'success',FAILED:'danger',CANCELLED:'info',TIMED_OUT:'danger',SKIPPED:'info'} as Record<string,'primary'|'success'|'warning'|'info'|'danger'>)[status]||'info')}
function revisionNumber(id:number){return selected.value?.revisions.find(item=>item.id===id)?.revision||'—'}
</script>

<style scoped lang="scss">
.pipeline-page {
  min-height: calc(100vh - 104px);
  padding: var(--bi-page-pad-y, 24px) var(--bi-page-pad-x, 28px) 32px;
  color: var(--bi-text, #1f2a3d);
  background: var(--bi-background, #f5f7fb);
}
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.eyebrow { font-size: 11px; letter-spacing: 1.8px; color: var(--el-color-primary, #3b6ef5); font-weight: 700; }
.page-head h1 { margin: 5px 0; font-size: 26px; letter-spacing: -0.02em; }
.page-head p { margin: 0; color: var(--bi-muted, #6f7d93); font-size: 13px; }
.workspace { display: grid; grid-template-columns: 248px minmax(0, 1fr); gap: 16px; min-height: calc(100vh - 200px); }
.pipeline-list, .editor {
  background: var(--bi-surface, #fff);
  border: 1px solid var(--bi-border, #e2e8f2);
  border-radius: var(--bi-radius-lg, 18px);
  box-shadow: var(--bi-shadow, 0 10px 28px rgba(28, 45, 78, 0.06));
}
.pipeline-list { padding: 14px 10px; overflow: auto; }
.list-title { padding: 5px 10px 13px; color: #748198; font-size: 12px; font-weight: 600; }
.list-title span { float: right; color: #a7b0c0; }
.pipeline-item {
  display: flex; align-items: center; width: 100%; gap: 10px; padding: 11px 10px; border: 0; border-radius: 12px;
  background: transparent; text-align: left; cursor: pointer; color: #34415a;
}
.pipeline-item:hover, .pipeline-item.active { background: #eef3ff; }
.pipeline-item.active { color: #2f5fe0; }
.pipeline-mark { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 10px; background: #f0f4fc; font-size: 17px; }
.pipeline-copy { min-width: 0; display: grid; gap: 3px; }
.pipeline-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.pipeline-copy small { color: #8a95a8; font-size: 11px; }
.editor { min-width: 0; padding: 18px; display: flex; flex-direction: column; gap: 14px; }
.editor-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.editor-title { display: flex; align-items: center; gap: 18px; min-width: 0; }
.editor-title h2 { margin: 0 0 5px; font-size: 18px; }
.editor-title span { font-size: 12px; color: #8993a5; }
.revision-select { width: 190px; }
.editor-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.design-stage { display: grid; grid-template-columns: 168px minmax(0, 1fr); gap: 12px; min-height: 620px; }
.palette-panel {
  padding: 12px 10px;
  border: 1px solid #e4ebf4;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff, #f7f9fd);
}
.palette-title { font-size: 12px; font-weight: 700; color: #5b6b84; margin-bottom: 4px; }
.palette-hint { margin: 0 0 10px; color: #94a0b3; font-size: 11px; line-height: 1.4; }
.palette-item {
  display: flex; align-items: center; gap: 8px; width: 100%; margin-bottom: 8px; padding: 9px 8px;
  border: 1px solid #e5ebf4; border-radius: 12px; background: #fff; text-align: left; cursor: grab; color: #314157;
}
.palette-item:disabled { opacity: 0.55; cursor: not-allowed; }
.palette-item:hover:not(:disabled) { border-color: #b9c9ea; box-shadow: 0 4px 12px rgba(45, 70, 120, 0.08); }
.palette-icon {
  width: 28px; height: 28px; display: grid; place-items: center; border-radius: 8px; background: #eef3ff; color: #3b6ef5; flex: none;
}
.palette-item span { display: grid; gap: 1px; min-width: 0; }
.palette-item strong { font-size: 12px; }
.palette-item small { color: #8b98ab; font-size: 10px; }
.canvas-panel { min-width: 0; display: grid; gap: 8px; }
.node-inspector {
  padding: 14px 16px;
  border: 1px solid #e4ebf4;
  border-radius: 14px;
  background: #fff;
}
.inspector-title, .inspector-title > div { display: flex; align-items: center; gap: 9px; }
.inspector-title { justify-content: space-between; }
.inspector-form { display: flex; align-items: flex-start; gap: 14px; flex-wrap: wrap; padding-top: 12px; }
.inspector-form :deep(.el-form-item) { margin: 0; min-width: 180px; }
.inspector-form :deep(.el-input-number), .inspector-form :deep(.el-select) { width: 190px; }
.inspector-note { align-self: center; margin: 0; color: #929daf; font-size: 11px; flex-basis: 100%; }
.config-block { flex-basis: 100%; padding: 12px; border: 1px dashed #d7e0ec; border-radius: 12px; background: #f8fafc; }
.config-block-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.config-row { display: grid; grid-template-columns: 1.2fr 0.9fr 1.2fr auto; gap: 8px; align-items: center; margin-bottom: 8px; }
.config-row .arrow { text-align: center; color: #8b98ab; }
.config-empty { margin: 0; color: #93a0b3; font-size: 12px; }
.editor-foot { display: flex; justify-content: space-between; color: #8c97aa; font-size: 11px; }
.editor-empty { display: grid; place-items: center; }
.diff-controls { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.diff-controls .el-select { width: 210px; }
.diff-grid { display: grid; gap: 12px; margin-top: 14px; }
.diff-card { min-width: 0; padding: 12px; border: 1px solid #e7ebf2; border-radius: 9px; background: #fbfcfe; }
.diff-card strong, .diff-card span { display: block; }
.diff-card span { margin-top: 5px; color: #77849a; font-size: 12px; }
.diff-card pre {
  max-height: 230px; overflow: auto; margin: 10px 0 0; padding: 10px; border-radius: 6px;
  background: #f0f3f8; color: #35425a; font-size: 11px; white-space: pre-wrap; word-break: break-word;
}
.unit { margin-left: 6px; color: #8c97aa; font-size: 12px; }
.gate-note { flex-basis: 100%; margin: 0; }
.node-summary {
  max-height: 180px; overflow: auto; margin: 0; padding: 8px; border-radius: 6px;
  background: #f5f7fb; color: #42506a; font-size: 11px; white-space: pre-wrap; word-break: break-word;
}
.run-history { margin-top: 4px; padding-top: 8px; border-top: 1px solid #e9edf4; }
.run-history-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.run-history-head span { display: block; margin-top: 4px; color: #8c97aa; font-size: 11px; }
.run-error { padding: 8px 12px; color: #b54747; font-size: 12px; }
.mb8 { margin-bottom: 12px; }
.ml8 { margin-left: 8px; }
@media (max-width: 1100px) {
  .pipeline-page { padding: 16px; }
  .workspace, .design-stage { grid-template-columns: 1fr; }
  .pipeline-list { max-height: 220px; }
  .editor-head { align-items: flex-start; flex-direction: column; }
  .config-row { grid-template-columns: 1fr; }
}
</style>
