<template>
  <main class="catalog-page bi-page" v-loading="loading">
    <section class="catalog-heading bi-page-header">
      <div>
        <h1 class="bi-page-title">数据资产目录</h1>
        <p class="bi-page-desc">统一浏览数据源、表和字段结构，查看扫描状态与元数据版本。</p>
      </div>
      <div class="heading-actions">
        <el-button v-if="canBackfill" @click="openAudit">审计记录</el-button>
        <el-button v-if="canBackfill" :loading="lineageRebuildLoading" @click="rebuildLineage">重建血缘</el-button>
        <el-button v-if="canBackfill" :loading="backfillLoading" @click="previewBackfill">回填 BI 资源</el-button>
        <el-button v-if="canWarehouse" :disabled="!warehouseTargets.length" @click="openWarehouse">批量入仓</el-button>
        <el-button v-if="canScan" :icon="Setting" :disabled="!sources.length" :title="sources.length ? '' : '当前账号没有获授权的数据源'" @click="openScans">扫描管理</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadAssets">刷新</el-button>
      </div>
    </section>

    <el-alert v-if="!canView" type="warning" :closable="false" show-icon title="需要数据源建模权限才能查看资产目录。" />
    <template v-else>
      <section class="catalog-toolbar bi-filter-bar">
        <el-radio-group v-model="sourceKind" @change="switchSourceKind">
          <el-radio-button label="CONNECTOR">数据库资产</el-radio-button>
          <el-radio-button label="BI_RESOURCE">BI 资源</el-radio-button>
        </el-radio-group>
        <el-select v-if="sourceKind === 'CONNECTOR'" v-model="filters.datasourceId" clearable placeholder="全部有权数据源" class="source-filter" @change="search">
          <el-option v-for="source in sources" :key="source.id" :label="source.name" :value="source.id" />
        </el-select>
        <el-select v-model="filters.type" clearable placeholder="全部资产类型" class="type-filter" @change="search">
          <template v-if="sourceKind === 'CONNECTOR'">
            <el-option label="Schema" value="SCHEMA" />
            <el-option label="数据表" value="TABLE" />
            <el-option label="字段" value="COLUMN" />
          </template>
          <template v-else>
            <el-option label="数据源" value="DATASOURCE" />
            <el-option label="API 数据源快照" value="API_SOURCE" />
            <el-option label="数据集" value="DATASET" />
            <el-option label="数据集字段" value="DATASET_FIELD" />
            <el-option label="图表" value="CHART" />
            <el-option label="看板 / 大屏" value="DASHBOARD" />
            <el-option label="指标" value="METRIC" />
          </template>
        </el-select>
        <el-input v-model="filters.query" clearable placeholder="搜索资产名称或完整名称" class="asset-search" @keyup.enter="search" @clear="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" :icon="Search" @click="search">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </section>

      <el-table :data="assets" class="catalog-table" row-key="id" :empty-text="assetEmptyText">
        <el-table-column label="资产名称" min-width="240">
          <template #default="{ row }">
            <div class="asset-name">
              <span class="asset-icon" :class="row.assetType"><el-icon><component :is="assetIcon(row.assetType)" /></el-icon></span>
              <span class="asset-name-text"><strong>{{ row.name }}</strong><small>{{ row.qualifiedName || row.urn }}</small></span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110"><template #default="{ row }"><el-tag effect="plain" size="small">{{ typeLabel(row.assetType) }}</el-tag></template></el-table-column>
        <el-table-column label="来源" width="180"><template #default="{ row }">{{ row.sourceKind === 'BI_RESOURCE' ? biSourceLabel(row.assetType) : sourceName(row.sourceId) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? '正常' : '已变化' }}</el-tag></template></el-table-column>
        <el-table-column label="最近发现" width="175"><template #default="{ row }">{{ formatTime(row.lastSeenAt) }}</template></el-table-column>
        <el-table-column label="操作" width="225" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openVersions(row)">版本历史</el-button>
            <el-button link type="primary" @click="openLineage(row)">血缘</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="catalog-pagination">
        <span>共 {{ total }} 项资产</span>
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" :page-sizes="[20, 50, 100]" layout="sizes, prev, pager, next, jumper" @change="loadAssets" />
      </div>
    </template>

    <el-drawer v-model="detailVisible" :title="detailAsset?.name || '资产详情'" size="min(680px, 94vw)" destroy-on-close>
      <template v-if="detailAsset">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="类型">{{ typeLabel(detailAsset.assetType) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detailAsset.sourceKind === 'BI_RESOURCE' ? biSourceLabel(detailAsset.assetType) : sourceName(detailAsset.sourceId) }}</el-descriptions-item>
          <el-descriptions-item label="完整名称">{{ detailAsset.qualifiedName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{ detailAsset.description || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailAsset.status === 'ACTIVE' ? '正常' : '已变化' }}</el-descriptions-item>
          <el-descriptions-item label="最近发现">{{ formatTime(detailAsset.lastSeenAt) }}</el-descriptions-item>
          <el-descriptions-item label="URN"><code class="urn-value">{{ detailAsset.urn }}</code></el-descriptions-item>
        </el-descriptions>
        <div class="detail-actions">
          <el-button v-for="action in consumeActions" :key="action.key" type="primary" plain @click="runConsumeAction(action)">{{ action.label }}</el-button>
          <el-button v-if="detailAsset.sourceKind !== 'BI_RESOURCE' || detailAsset.assetType === 'API_SOURCE'" type="primary" plain @click="openSource(detailAsset.sourceId)">打开来源数据源</el-button>
          <el-button v-if="detailAsset.sourceKind === 'CONNECTOR' && detailAsset.assetType === 'TABLE'" type="warning" plain @click="openSchemaChanges(detailAsset)">结构变化与影响</el-button>
          <el-button v-if="detailAsset.sourceKind === 'CONNECTOR' && detailAsset.assetType === 'TABLE'" type="danger" plain :loading="impactPackLoading" @click="openImpactPack(detailAsset)">运维影响包</el-button>
          <el-button v-if="detailAsset.sourceKind === 'CONNECTOR' && detailAsset.assetType === 'COLUMN'" v-hasPermi="['bi:resource:admin']" type="success" plain :loading="onlineSampleLoading" @click="runOnlineSample(detailAsset)">JDBC 在线抽样</el-button>
        </div>
        <section class="metadata-section">
          <div class="section-title"><h3>当前元数据</h3><span v-if="detailVersions[0]">版本 v{{ detailVersions[0].versionNo }}</span></div>
          <el-empty v-if="!detailVersions.length" description="尚无已保存的元数据版本" :image-size="72" />
          <el-table v-else :data="metadataRows(detailVersions[0].metadata)" size="small" max-height="420">
            <el-table-column prop="key" label="属性" width="180" />
            <el-table-column prop="value" label="值" min-width="220" show-overflow-tooltip />
          </el-table>
        </section>
      </template>
    </el-drawer>

    <el-dialog v-model="impactPackVisible" :title="`${impactPack?.asset?.name || '资产'} · 运维影响包`" width="min(1080px, 96vw)" destroy-on-close>
      <template v-if="impactPack">
        <el-alert :type="impactSeverityType(impactPack.summary.severity)" :closable="false" show-icon :title="`严重度 ${impactPack.summary.severity}`">
          <template #default>
            数据集 {{ impactPack.summary.datasetCount }} · 指标 {{ impactPack.summary.metricCount }} · 图表 {{ impactPack.summary.chartCount }} · 看板 {{ impactPack.summary.dashboardCount }} · API {{ impactPack.summary.dataApiCount }} · 流程 {{ impactPack.summary.pipelineCount }} · 装载 {{ impactPack.summary.warehouseLoadCount }} · CDC {{ impactPack.summary.cdcJobCount }}
            <span v-if="impactPack.summary.redactedConsumerCount"> · 脱敏占位 {{ impactPack.summary.redactedConsumerCount }}</span>
          </template>
        </el-alert>
        <el-alert v-if="impactPack.summary.redactedConsumerCount" class="impact-note" type="info" :closable="false" :title="`已脱敏 ${impactPack.summary.redactedConsumerCount} 个无权消费者占位（名称/状态不泄露）`" />
        <el-alert v-if="impactPack.summary.lineageIncompleteHint" class="impact-note" type="warning" :closable="false" title="血缘可能不完整：已结合物理表数据集扇出；请人工复核无权或未登记依赖。" />
        <h3 class="pack-section-title">变更检查清单</h3>
        <ol class="pack-checklist"><li v-for="(item, idx) in impactPack.checklist" :key="idx">{{ item }}</li></ol>
        <el-tabs class="impact-tabs">
          <el-tab-pane :label="`指标 (${impactPack.metrics.length})`" name="metrics">
            <el-table :data="impactPack.metrics" max-height="280" empty-text="无可见指标"><el-table-column prop="code" label="编码" min-width="120" /><el-table-column prop="name" label="名称" min-width="160" /><el-table-column prop="publishStatus" label="发布" width="110" /><el-table-column prop="datasetId" label="数据集" width="100" /></el-table>
          </el-tab-pane>
          <el-tab-pane :label="`Data API (${impactPack.dataApis.length})`" name="apis">
            <el-table :data="impactPack.dataApis" max-height="280" empty-text="无关联 API"><el-table-column prop="apiCode" label="编码" min-width="140" /><el-table-column prop="name" label="名称" min-width="160" /><el-table-column prop="status" label="状态" width="120" /><el-table-column prop="metricCode" label="指标" min-width="120" /></el-table>
          </el-tab-pane>
          <el-tab-pane :label="`流程 (${impactPack.pipelines.length})`" name="pipelines">
            <el-table :data="impactPack.pipelines" max-height="280" empty-text="无可见流程"><el-table-column prop="pipelineName" label="流程" min-width="180" /><el-table-column prop="nodeName" label="节点" min-width="140" /><el-table-column prop="pipelineStatus" label="状态" width="110" /></el-table>
          </el-tab-pane>
          <el-tab-pane :label="`装载/CDC (${impactPack.warehouseLoads.length + impactPack.cdcJobs.length})`" name="loads">
            <el-table :data="impactPack.warehouseLoads" max-height="180" empty-text="无装载记录"><el-table-column prop="id" label="运行" width="80" /><el-table-column prop="targetTable" label="目标表" min-width="160" /><el-table-column prop="status" label="状态" width="110" /></el-table>
            <el-table :data="impactPack.cdcJobs" max-height="180" empty-text="无 CDC 编排" class="pack-secondary-table"><el-table-column prop="jobCode" label="CDC" min-width="140" /><el-table-column prop="targetTable" label="目标" min-width="160" /><el-table-column prop="status" label="状态" width="110" /></el-table>
          </el-tab-pane>
          <el-tab-pane :label="`图表/看板 (${impactPack.charts.length + impactPack.dashboards.length})`" name="bi">
            <el-table :data="impactPack.charts" max-height="180" empty-text="无可见图表"><el-table-column prop="id" label="图表" width="90" /><el-table-column prop="name" label="名称" min-width="180" /><el-table-column prop="datasetId" label="数据集" width="100" /></el-table>
            <el-table :data="impactPack.dashboards" max-height="180" empty-text="无可见看板" class="pack-secondary-table"><el-table-column prop="dashboardId" label="看板" width="90" /><el-table-column prop="dashboardName" label="名称" min-width="180" /><el-table-column prop="published" label="已发布" width="90" /></el-table>
          </el-tab-pane>
        </el-tabs>
        <p class="pack-note">{{ impactPack.note }}</p>
      </template>
      <el-empty v-else-if="!impactPackLoading" description="未能加载运维影响包" />
      <template #footer>
        <el-button :disabled="!impactPack" @click="copyImpactPack">复制 JSON</el-button>
        <el-button type="primary" @click="impactPackVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="schemaChangesVisible" :title="`${schemaChangeAsset?.name || '数据表'} · 结构变化记录`" width="min(900px, 96vw)" destroy-on-close>
      <el-table :data="schemaChangeEvents" v-loading="schemaChangesLoading" max-height="480" empty-text="该表暂无结构变化记录">
        <el-table-column label="变化时间" min-width="170"><template #default="{ row }">{{ formatTime(row.observedAt) }}</template></el-table-column>
        <el-table-column label="版本" width="130"><template #default="{ row }">v{{ schemaVersionNo(row.currentVersionId) }}<span class="muted"> ← v{{ schemaVersionNo(row.previousVersionId) }}</span></template></el-table-column>
        <el-table-column label="字段变化" min-width="260"><template #default="{ row }"><div class="change-tags"><el-tag v-for="label in schemaChangeLabels(row)" :key="label" size="small" effect="plain">{{ label }}</el-tag></div></template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right"><template #default="{ row }"><el-button link type="primary" :loading="impactLoadingId === row.id" @click="openSchemaImpact(row)">影响预览</el-button></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="schemaChangesVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="schemaImpactVisible" title="结构变化影响预览" width="min(1080px, 96vw)" destroy-on-close>
      <template v-if="schemaImpact">
        <el-alert :type="schemaImpact.reviewRequired ? 'warning' : 'info'" :closable="false" show-icon :title="schemaImpact.reviewRequired ? '检测到字段删除或类型变化，建议先核对下游依赖' : '以下内容根据当前已登记的血缘关系推导'">
          <template #default>检测字段：{{ schemaImpact.affectedFields.join('、') || '表级结构' }}；影响 {{ schemaImpact.counts.assets }} 项资源、{{ schemaImpact.counts.datasetFields }} 个数据集字段、{{ schemaImpact.counts.qualityRules }} 条质量规则、{{ schemaImpact.counts.qualityTasks }} 个质量任务、{{ schemaImpact.counts.pipelines }} 条流程。</template>
        </el-alert>
        <el-alert v-if="schemaImpact.renameCandidates.length" class="impact-note" type="warning" :closable="false" title="以下为同位置且同类型字段的低置信度重命名候选，请人工确认后再迁移依赖。">
          <div class="change-tags"><el-tag v-for="candidate in schemaImpact.renameCandidates" :key="`${candidate.from}-${candidate.to}`" size="small" effect="plain">{{ candidate.from }} → {{ candidate.to }}</el-tag></div>
        </el-alert>
        <el-alert v-if="schemaImpact.truncated" class="impact-note" type="warning" :closable="false" title="影响范围达到图谱上限，结果可能不完整；请拆分检查或联系管理员扩展分析。" />
        <el-card v-if="canMigrateSchema && schemaImpact.datasetFields.length" class="migration-card" shadow="never">
          <template #header><div class="migration-heading"><strong>安全迁移数据集字段</strong><el-button size="small" :loading="migrationLoading" @click="loadMigrationPlans()">刷新计划</el-button></div></template>
          <el-alert type="info" :closable="false" title="选择目标字段完成重命名；选择“删除”仅在字段无依赖时允许。生成计划后需审批并执行，已执行计划可以回滚。" />
          <el-table :data="schemaImpact.datasetFields" size="small" max-height="260" class="migration-table">
            <el-table-column prop="datasetId" label="数据集" width="100" />
            <el-table-column prop="fieldName" label="原字段" min-width="160" />
            <el-table-column label="目标字段 / 操作" min-width="220"><template #default="{ row }"><el-select v-model="migrationTargets[migrationKey(row)]" placeholder="选择变更后字段" clearable><el-option v-for="name in migrationOptions" :key="name" :label="name" :value="name" /><el-option label="删除字段（检查依赖）" value="__DROP__" /></el-select></template></el-table-column>
          </el-table>
          <div class="migration-actions"><el-button type="primary" :disabled="!schemaImpact.datasetFields.length" :loading="migrationLoading" @click="createMigration">生成审批计划</el-button></div>
          <el-table v-if="migrationPlans.length" :data="migrationPlans" size="small" max-height="220">
            <el-table-column prop="planNo" label="计划" width="90"><template #default="{ row }">#{{ row.planNo }}</template></el-table-column><el-table-column label="状态" width="130"><template #default="{ row }">{{ migrationStatus(row.status) }}</template></el-table-column>
            <el-table-column label="操作" min-width="250"><template #default="{ row }"><el-button v-if="row.status === 'DRAFT'" link type="primary" @click="approveMigration(row)">审批</el-button><el-button v-if="row.status === 'APPROVED'" link type="primary" @click="executeMigration(row)">执行</el-button><el-button v-if="row.status === 'EXECUTED'" link type="warning" @click="rollbackMigration(row)">回滚</el-button><span v-if="row.status === 'BLOCKED'" class="muted">{{ row.blockers?.join('；') || '存在未解决的安全阻断项' }}</span><span v-if="row.status === 'FAILED'" class="muted">{{ row.executionError }}</span></template></el-table-column>
          </el-table>
        </el-card>
        <el-tabs v-model="schemaImpactTab" class="impact-tabs">
          <el-tab-pane :label="`数据集字段 (${schemaImpact.datasetFields.length})`" name="fields">
            <el-table :data="schemaImpact.datasetFields" max-height="380" empty-text="没有匹配的数据集字段"><el-table-column prop="datasetId" label="数据集 ID" width="110" /><el-table-column prop="fieldName" label="源字段" min-width="180" /><el-table-column prop="displayName" label="展示名称" min-width="180" /><el-table-column prop="fieldType" label="当前类型" min-width="130" /><el-table-column label="影响" width="170"><template #default="{ row }"><el-tag size="small" :type="fieldImpactTagType(String(row.impactStatus))">{{ fieldImpactLabel(String(row.impactStatus)) }}</el-tag></template></el-table-column></el-table>
          </el-tab-pane>
          <el-tab-pane :label="`下游资源 (${schemaImpact.impactNodes.length})`" name="assets">
            <el-table :data="schemaImpact.impactNodes" max-height="380" empty-text="没有可见的下游资源"><el-table-column prop="assetType" label="类型" width="150" /><el-table-column prop="name" label="名称" min-width="220" /><el-table-column prop="qualifiedName" label="完整名称" min-width="260" show-overflow-tooltip /></el-table>
          </el-tab-pane>
          <el-tab-pane :label="`质量规则 (${schemaImpact.qualityRules.length})`" name="rules">
            <el-table :data="schemaImpact.qualityRules" max-height="380" empty-text="没有匹配的质量规则"><el-table-column prop="ruleSetName" label="规则集" min-width="160" /><el-table-column prop="ruleCode" label="规则编码" min-width="150" /><el-table-column prop="fieldName" label="字段" min-width="140" /><el-table-column prop="ruleType" label="规则类型" min-width="140" /><el-table-column prop="severity" label="级别" width="100" /></el-table>
          </el-tab-pane>
          <el-tab-pane :label="`质量任务 (${schemaImpact.qualityTasks.length})`" name="tasks">
            <el-table :data="schemaImpact.qualityTasks" max-height="380" empty-text="没有关联的质量任务"><el-table-column prop="name" label="任务名称" min-width="200" /><el-table-column prop="status" label="状态" width="120" /><el-table-column prop="scheduleType" label="调度方式" width="140" /></el-table>
          </el-tab-pane>
          <el-tab-pane :label="`数据流程 (${schemaImpact.pipelines.length})`" name="pipelines">
            <el-table :data="schemaImpact.pipelines" max-height="380" empty-text="没有可见的数据流程"><el-table-column prop="pipelineName" label="流程名称" min-width="200" /><el-table-column prop="nodeName" label="来源节点" min-width="180" /><el-table-column prop="revision" label="生效版本" width="110" /><el-table-column prop="pipelineStatus" label="状态" width="120" /></el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else-if="!schemaImpactLoading" description="未能加载影响预览" />
      <template #footer><el-button @click="schemaImpactVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="lineageVisible" :title="`${lineageRoot?.name || '资产'} · 数据血缘`" width="min(1080px, 96vw)" destroy-on-close>
      <div class="lineage-toolbar">
        <el-radio-group v-model="lineageQuery.direction" @change="loadLineage"><el-radio-button value="both">上下游</el-radio-button><el-radio-button value="upstream">仅上游</el-radio-button><el-radio-button value="downstream">仅下游</el-radio-button></el-radio-group>
        <el-select v-model="lineageQuery.depth" class="lineage-depth" @change="loadLineage"><el-option v-for="depth in [1, 2, 3, 4, 5, 6]" :key="depth" :label="`${depth} 层`" :value="depth" /></el-select>
        <el-checkbox v-model="lineageQuery.includeStale" @change="loadLineage">包含已失效关系</el-checkbox>
        <el-button :icon="Refresh" :loading="lineageLoading" @click="loadLineage">刷新图谱</el-button>
      </div>
      <el-alert v-if="lineageGraph?.truncated" class="lineage-limit-note" type="warning" :closable="false" show-icon title="图谱已达到节点或关系上限，请缩小查询深度或沿单一方向查看。" />
      <div v-if="lineageGraph?.edges.length" class="lineage-list">
        <div v-for="edge in lineageGraph.edges" :key="edge.id" class="lineage-edge-row">
          <div class="lineage-node"><strong>{{ lineageNodeName(edge.sourceAssetId) }}</strong><small>{{ typeLabel(lineageNodes.get(edge.sourceAssetId)?.assetType || '') }}</small></div>
          <div class="lineage-relation"><span>{{ lineageKindLabel(edge.edgeKind) }}</span><el-icon><Right /></el-icon><small>{{ edge.sourceMethod }} · 置信度 {{ Math.round(Number(edge.confidence) * 100) }}%</small></div>
          <div class="lineage-node"><strong>{{ lineageNodeName(edge.targetAssetId) }}</strong><small>{{ typeLabel(lineageNodes.get(edge.targetAssetId)?.assetType || '') }}</small></div>
          <el-tag size="small" :type="edge.effectiveStatus === 'ACTIVE' ? 'success' : 'warning'">{{ edge.effectiveStatus === 'ACTIVE' ? '有效' : '已失效' }}</el-tag>
        </div>
      </div>
      <el-empty v-else-if="!lineageLoading" description="该资产当前没有可见的血缘关系" :image-size="90" />
      <template #footer><span class="lineage-footer">共 {{ lineageGraph?.nodes.length || 0 }} 个可见资产节点 · {{ lineageGraph?.edges.length || 0 }} 条关系</span><el-button @click="lineageVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-drawer v-model="versionsVisible" :title="`${versionAsset?.name || '资产'} · 版本历史`" size="min(760px, 96vw)" destroy-on-close>
      <el-timeline v-if="detailVersions.length" class="version-timeline">
        <el-timeline-item v-for="version in detailVersions" :key="version.id" :timestamp="formatTime(version.observedAt)" placement="top" :type="version.changes?.changeType === 'CREATED' ? 'primary' : 'success'">
          <el-card shadow="never" class="version-card">
            <div class="version-heading"><strong>版本 v{{ version.versionNo }}</strong><el-tag size="small" effect="plain">{{ changeTypeLabel(version.changes?.changeType) }}</el-tag></div>
            <div v-if="version.changes?.fields" class="change-tags">
              <el-tag v-for="field in version.changes.fields.added || []" :key="`a-${field}`" type="success" size="small">新增 {{ field }}</el-tag>
              <el-tag v-for="field in version.changes.fields.changed || []" :key="`c-${field}`" type="warning" size="small">变更 {{ field }}</el-tag>
              <el-tag v-for="field in version.changes.fields.removed || []" :key="`r-${field}`" type="danger" size="small">移除 {{ field }}</el-tag>
              <span v-if="!(version.changes.fields.added?.length || version.changes.fields.changed?.length || version.changes.fields.removed?.length)" class="muted">结构属性无差异</span>
            </div>
            <div class="version-hash">结构指纹：{{ version.schemaHash }}</div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无版本记录" />
    </el-drawer>

    <el-dialog v-model="scanVisible" title="元数据扫描管理" width="min(1040px, 96vw)" top="5vh" destroy-on-close @closed="stopPolling">
      <div class="scan-source-row">
        <span>数据源</span>
        <el-select v-model="scanSourceId" placeholder="选择数据源" class="scan-source-select" @change="loadScanData">
          <el-option v-for="source in sources" :key="source.id" :label="`${source.name} · ${source.type}`" :value="source.id" />
        </el-select>
        <el-button :icon="Refresh" :disabled="!scanSourceId" @click="loadScanData">刷新配置和运行记录</el-button>
      </div>
      <el-alert class="scan-scope-note" type="info" :closable="false" show-icon title="完整扫描不会读取业务数据行，只采集 schema、表和字段结构。仅完整且无失败的扫描会推断缺失资产。" />
      <el-card v-if="canScan && scanSourceId" shadow="never" class="scan-create-card">
        <template #header><div class="section-title"><h3>新建扫描配置</h3><span>可留空范围以扫描全部表</span></div></template>
        <el-form :model="scanForm" label-width="105px" class="scan-form">
          <el-form-item label="配置名称"><el-input v-model="scanForm.name" maxlength="200" show-word-limit placeholder="例如：PostgreSQL 每日结构扫描" /></el-form-item>
          <el-form-item label="包含表"><el-input v-model="scanForm.includeTables" type="textarea" :rows="2" placeholder="可选，表名用逗号或换行分隔；留空扫描全部" /></el-form-item>
          <el-form-item label="排除表"><el-input v-model="scanForm.excludeTables" type="textarea" :rows="2" placeholder="可选，表名用逗号或换行分隔" /></el-form-item>
          <div class="scan-form-actions"><el-button type="primary" :loading="savingScan" @click="saveScan">创建配置</el-button></div>
        </el-form>
      </el-card>
      <el-tabs v-model="scanTab" class="scan-tabs">
        <el-tab-pane label="扫描配置" name="definitions">
          <el-table :data="scanDefinitions" v-loading="scanLoading" empty-text="该数据源还没有扫描配置">
            <el-table-column prop="name" label="配置名称" min-width="200" />
            <el-table-column label="范围" min-width="240"><template #default="{ row }">{{ scopeLabel(row.scopeJson) }}</template></el-table-column>
            <el-table-column label="状态" width="95"><template #default="{ row }"><el-tag :type="row.status === '0' ? 'success' : 'info'" size="small">{{ row.status === '0' ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :loading="triggeringId === row.id" :disabled="row.status !== '0'" @click="runScan(row)">立即扫描</el-button>
                <el-button link @click="toggleScan(row)">{{ row.status === '0' ? '停用' : '启用' }}</el-button>
                <el-button link type="danger" @click="removeScan(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="运行记录" name="runs">
          <el-table :data="scanRuns" v-loading="scanLoading" empty-text="暂无扫描运行记录">
            <el-table-column prop="id" label="运行 ID" width="90" />
            <el-table-column label="状态" width="135"><template #default="{ row }"><el-tag :type="runTagType(row.status)" size="small">{{ runStatusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="进度" width="160"><template #default="{ row }"><el-progress :percentage="progress(row)" :status="row.status === 'FAILED' ? 'exception' : undefined" :stroke-width="8" /></template></el-table-column>
            <el-table-column label="对象" width="115"><template #default="{ row }">{{ row.progressCompleted }}/{{ row.progressTotal }}</template></el-table-column>
            <el-table-column label="成功 / 失败" width="115"><template #default="{ row }">{{ row.successCount }} / {{ row.failureCount }}</template></el-table-column>
            <el-table-column label="开始时间" min-width="165"><template #default="{ row }">{{ formatTime(row.startedAt || row.createTime) }}</template></el-table-column>
            <el-table-column label="结果摘要" min-width="230" show-overflow-tooltip>
              <template #default="{ row }">{{ runSummary(row) || row.errorSummary || '—' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <template #footer><el-button @click="scanVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="auditVisible" title="数据资产目录审计" width="min(1120px, 96vw)" destroy-on-close>
      <div class="audit-filter-bar">
        <el-input v-model="auditFilters.actor" clearable placeholder="操作者" class="audit-filter-input" @keyup.enter="searchAudit" />
        <el-input v-model="auditFilters.action" clearable placeholder="操作类型（精确）" class="audit-filter-input" @keyup.enter="searchAudit" />
        <el-select v-model="auditFilters.outcome" clearable placeholder="全部结果" class="audit-filter-input">
          <el-option label="成功" value="SUCCESS" /><el-option label="失败" value="FAILED" /><el-option label="拒绝" value="REJECTED" />
        </el-select>
        <el-date-picker v-model="auditFilters.from" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间" class="audit-filter-date" />
        <el-date-picker v-model="auditFilters.to" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="结束时间" class="audit-filter-date" />
        <el-button type="primary" @click="searchAudit">查询</el-button><el-button @click="resetAuditFilters">重置</el-button>
      </div>
      <el-alert class="audit-retention" type="info" :closable="false" title="在线审计可配置保留 365～3650 天；归档记录长期保留，并可通过以上条件统一检索。">
        <template #default>
          <div class="audit-retention-controls">
            <span>在线保留天数</span>
            <el-input-number v-model="auditRetentionDays" :min="365" :max="3650" :step="30" controls-position="right" />
            <el-button size="small" :loading="auditRetentionSaving" @click="saveAuditRetention">保存策略</el-button>
            <span v-if="auditRetentionUpdatedAt" class="audit-retention-meta">最近更新：{{ auditRetentionUpdatedAt }}（{{ auditRetentionUpdatedBy || 'system' }}）</span>
          </div>
        </template>
      </el-alert>
      <el-table :data="auditRows" v-loading="auditLoading" max-height="560" empty-text="暂无审计记录">
        <el-table-column prop="createdAt" label="时间" min-width="165" />
        <el-table-column prop="actorName" label="操作者" width="120" />
        <el-table-column prop="action" label="动作" min-width="190" />
        <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.outcome === 'SUCCESS' ? 'success' : row.outcome === 'FAILED' ? 'danger' : 'warning'" size="small">{{ row.outcome }}</el-tag></template></el-table-column>
        <el-table-column label="对象" min-width="150"><template #default="{ row }">{{ row.subjectType }}<span v-if="row.subjectId"> #{{ row.subjectId }}</span></template></el-table-column>
        <el-table-column prop="sourceId" label="来源 ID" width="110" />
        <el-table-column prop="traceId" label="Trace ID" min-width="170" show-overflow-tooltip />
        <el-table-column prop="summaryJson" label="摘要" min-width="260" show-overflow-tooltip />
      </el-table>
      <div class="audit-pagination"><span>共 {{ auditTotal }} 条</span><el-pagination v-model:current-page="auditPage" v-model:page-size="auditPageSize" :total="auditTotal" layout="prev, pager, next" @change="loadAudit" /></div>
      <template #footer><el-button @click="auditVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="warehouseVisible" title="ClickHouse 批量入仓" width="min(920px, 96vw)" destroy-on-close @closed="stopWarehousePolling">
      <el-alert class="scan-scope-note" type="warning" :closable="false" show-icon :title="warehouseForm.loadMode === 'INCREMENTAL' ? '增量模式按非空水位字段读取变化，并按业务键原子合并；源端更新需递增水位，源端删除不会自动同步。单次最多 10 万行，失败时目标表和检查点保持可重试。' : '当前仅管理员可执行未套用行级策略的物理表全量刷新：源端只读，先写入临时表并校验行数，再原子替换 ODS 目标表；单次最多 10 万行，超过上限或结构不兼容时保留原目标表。'" />
      <el-form :model="warehouseForm" label-width="120px" class="scan-form">
        <el-form-item label="源数据源">
          <el-select v-model="warehouseForm.sourceDatasourceId" placeholder="选择已授权的 JDBC 业务源" class="scan-source-select" @change="loadWarehouseTables">
            <el-option v-for="source in warehouseSources" :key="source.id" :label="`${source.name} · ${source.type}`" :value="source.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="源表">
          <el-select v-model="warehouseForm.sourceTable" placeholder="选择源表" filterable class="scan-source-select" :loading="warehouseTablesLoading">
            <el-option v-for="table in warehouseTables" :key="table.name" :label="`${table.name} · ${table.type}`" :value="table.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="ClickHouse 目标">
          <el-select v-model="warehouseForm.targetDatasourceId" placeholder="选择已授权的 ODS ClickHouse 目标" class="scan-source-select" @change="loadWarehouseRuns">
            <el-option v-for="target in warehouseTargets" :key="target.id" :label="`${target.name} · ${target.sourceSchema}`" :value="target.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="ODS 目标表"><el-input v-model="warehouseForm.targetTable" maxlength="128" placeholder="例如 retail_order_header" /></el-form-item>
        <el-form-item label="入仓模式">
          <el-radio-group v-model="warehouseForm.loadMode"><el-radio-button value="FULL">全量刷新</el-radio-button><el-radio-button value="INCREMENTAL">增量合并</el-radio-button></el-radio-group>
        </el-form-item>
        <template v-if="warehouseForm.loadMode === 'INCREMENTAL'">
          <el-form-item label="水位字段"><el-input v-model="warehouseForm.watermarkColumn" maxlength="128" placeholder="非空数字或日期时间字段，如 updated_at" /></el-form-item>
          <el-form-item label="业务键字段"><el-input v-model="warehouseForm.keyColumnsText" maxlength="1000" placeholder="逗号分隔，如 order_id 或 tenant_id,order_id" /></el-form-item>
        </template>
        <el-form-item label="最大行数"><el-input-number v-model="warehouseForm.maxRows" :min="1" :max="100000" :step="10000" /></el-form-item>
        <div class="scan-form-actions">
          <el-button :icon="Refresh" :disabled="!warehouseForm.targetDatasourceId" :loading="warehouseLoading" @click="loadWarehouseRuns">刷新运行记录</el-button>
          <el-button :disabled="!warehouseForm.targetDatasourceId" :loading="warehouseModelSubmitting" @click="refreshRetailModel">刷新零售月汇总</el-button>
          <el-button type="primary" :loading="warehouseSubmitting" :disabled="!warehouseReady" @click="triggerWarehouse">{{ warehouseForm.loadMode === 'INCREMENTAL' ? '开始增量入仓' : '全量刷新入仓' }}</el-button>
        </div>
      </el-form>
      <el-divider />
      <div class="section-title"><h3>最近运行</h3><span>最多显示 100 条</span></div>
      <el-table :data="warehouseRuns" v-loading="warehouseLoading" empty-text="暂无入仓运行记录" max-height="330">
        <el-table-column prop="id" label="运行 ID" width="90" />
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="warehouseTagType(row.status)" size="small">{{ warehouseStatusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="源表" min-width="150"><template #default="{ row }">{{ row.sourceTable }}</template></el-table-column>
        <el-table-column label="目标表" min-width="190"><template #default="{ row }">{{ row.targetDatabase }}.{{ row.targetTable }}</template></el-table-column>
        <el-table-column label="写入行数" width="115"><template #default="{ row }">{{ Number(row.rowCount || 0).toLocaleString() }}</template></el-table-column>
        <el-table-column label="模式" width="100"><template #default="{ row }">{{ row.loadMode === 'INCREMENTAL' ? '增量' : row.loadMode === 'MODEL' ? '模型' : '全量' }}</template></el-table-column>
        <el-table-column label="发布方式" width="120"><template #default="{ row }">{{ row.status === 'SUCCEEDED' ? (row.targetReplaced ? '原子发布' : '首次发布') : '—' }}</template></el-table-column>
        <el-table-column label="时间" min-width="165"><template #default="{ row }">{{ formatTime(row.startedAt || row.createTime) }}</template></el-table-column>
        <el-table-column prop="errorSummary" label="失败摘要" min-width="200" show-overflow-tooltip />
      </el-table>
      <el-alert v-if="warehouseReconciliation" class="scan-scope-note" :type="warehouseReconciliation.status === 'PASS' ? 'success' : 'warning'" :closable="false" show-icon>
        <template #title>最近一次 retail-v1 对账 · {{ warehouseReconciliation.status === 'PASS' ? '通过' : '待确认' }} · {{ warehouseReconciliation.checkScope === 'FULL' ? '全量' : '抽样' }}</template>
        <div>{{ warehouseReconciliation.summary }}；明细 {{ Number(warehouseReconciliation.sourceRowCount).toLocaleString() }} 行 → 汇总 {{ Number(warehouseReconciliation.targetRowCount).toLocaleString() }} 行；销售额 {{ warehouseReconciliation.sourceSalesAmount }} / {{ warehouseReconciliation.targetSalesAmount }}；净数量 {{ warehouseReconciliation.sourceNetQuantity }} / {{ warehouseReconciliation.targetNetQuantity }}；来源最新更新时间 {{ warehouseReconciliation.sourceMaxUpdatedAt || '暂无' }}（{{ freshnessLabel(warehouseReconciliation.freshnessAgeSeconds) }}）。</div>
      </el-alert>
      <template #footer><el-button @click="warehouseVisible = false">关闭</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts" name="BiCatalog">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Coin, Grid, Refresh, Right, Search, Setting, Tickets } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { checkPermi } from '@/utils/permission'
import { unwrapBiData } from '@/api/bi/response'
import { listEnabledDatasources, type BiDatasource } from '@/api/bi/datasource'
import { listTables, type CatalogTable } from '@/api/bi/dataset'
import { getRetailMonthlyReconciliation, getWarehouseLoadRun, listWarehouseLoadRuns, refreshRetailMonthlyModel, triggerWarehouseLoad, type WarehouseLoadRun, type WarehouseReconciliation } from '@/api/bi/warehouse'
import {
  approveSchemaMigrationPlan, createSchemaMigrationPlan, executeSchemaMigrationPlan, rollbackSchemaMigrationPlan, listSchemaMigrationPlans,
  createMetadataScan, deleteMetadataScan, getCatalogAsset, getCatalogConsumeLinks, getMetadataScanRun, listCatalogAssetVersions, previewCatalogBackfill, runCatalogBackfill,
  getLineageGraph, getOpsImpactPack, getSchemaChangeImpact, getCatalogAuditRetention, listCatalogAudit, listMetadataScans, listMetadataScanRuns, listSchemaChangeEvents, rebuildCatalogLineage, searchCatalogAssets, triggerMetadataScan, updateCatalogAuditRetention, updateMetadataScan,
  type CatalogAsset, type CatalogAssetType, type CatalogAssetVersion, type CatalogAuditRow, type CatalogConsumeAction, type LineageGraph, type MetadataScanDefinition,
  type OpsImpactPack, type SchemaChangeEvent, type SchemaChangeImpact, type SchemaMigrationPlan,
  type MetadataScanRun, type MetadataScanSaveRequest
} from '@/api/bi/catalog'

const route = useRoute()
const router = useRouter()
const canView = computed(() => checkPermi(['bi:datasource:model']))
const canScan = computed(() => checkPermi(['bi:datasource:model']))
const canBackfill = computed(() => checkPermi(['bi:resource:admin']))
const canWarehouse = computed(() => checkPermi(['bi:resource:admin']))
const canMigrateSchema = computed(() => checkPermi(['bi:resource:admin']))
const assetEmptyText = computed(() => {
  if (sourceKind.value === 'BI_RESOURCE') return '没有可见的 BI 资源，或尚未执行资源回填。'
  if (!sources.value.length) return '当前账号没有获授权的数据源，请联系管理员授予数据源查看权限。'
  return '当前授权数据源暂无资产；可在扫描管理中创建配置并执行扫描。'
})
const backfillLoading = ref(false)
const auditVisible = ref(false)
const auditLoading = ref(false)
const auditRows = ref<CatalogAuditRow[]>([])
const auditPage = ref(1)
const auditPageSize = ref(20)
const auditTotal = ref(0)
const auditFilters = reactive<{ actor: string; action: string; outcome: string; from?: string; to?: string }>({ actor: '', action: '', outcome: '' })
const auditRetentionDays = ref(365)
const auditRetentionUpdatedAt = ref('')
const auditRetentionUpdatedBy = ref('')
const auditRetentionSaving = ref(false)
const sources = ref<BiDatasource[]>([])
const assets = ref<CatalogAsset[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const sourceKind = ref<'CONNECTOR' | 'BI_RESOURCE'>('CONNECTOR')
const filters = reactive<{ datasourceId?: number; type?: CatalogAssetType; query: string }>({ query: '' })
const sourceNames = computed(() => new Map(sources.value.map(source => [source.id, source.name])))
const warehouseSources = computed(() => sources.value.filter(source => !['clickhouse', 'ck'].includes(source.type.toLowerCase())))
const warehouseTargets = computed(() => sources.value.filter(source => ['clickhouse', 'ck'].includes(source.type.toLowerCase()) && source.sourceSchema === 'wanfo_wh_ods'))

const detailVisible = ref(false)
const versionsVisible = ref(false)
const detailAsset = ref<CatalogAsset | null>(null)
const consumeActions = ref<CatalogConsumeAction[]>([])
const versionAsset = ref<CatalogAsset | null>(null)
const detailVersions = ref<CatalogAssetVersion[]>([])
const schemaChangesVisible = ref(false)
const schemaChangesLoading = ref(false)
const schemaChangeAsset = ref<CatalogAsset | null>(null)
const schemaChangeEvents = ref<SchemaChangeEvent[]>([])
const impactLoadingId = ref<number | null>(null)
const schemaImpactVisible = ref(false)
const schemaImpactLoading = ref(false)
const schemaImpact = ref<SchemaChangeImpact | null>(null)
const schemaImpactTab = ref<'fields' | 'assets' | 'rules' | 'tasks' | 'pipelines'>('fields')
const impactPackVisible = ref(false)
const impactPackLoading = ref(false)
const impactPack = ref<OpsImpactPack | null>(null)
const onlineSampleLoading = ref(false)
const migrationLoading = ref(false)
const migrationPlans = ref<SchemaMigrationPlan[]>([])
const migrationTargets = reactive<Record<string,string>>({})
const migrationOptions = computed(() => {
  const fields = (schemaImpact.value?.event.details.fields || {}) as Record<string, unknown>
  const names = Array.isArray(fields.added) ? fields.added.map(String) : []
  return [...new Set([...names, ...(schemaImpact.value?.renameCandidates || []).map(candidate => candidate.to)])]
})
const lineageVisible = ref(false)
const lineageLoading = ref(false)
const lineageRebuildLoading = ref(false)
const lineageRoot = ref<CatalogAsset | null>(null)
const lineageGraph = ref<LineageGraph | null>(null)
const lineageQuery = reactive<{ direction: 'both' | 'upstream' | 'downstream'; depth: number; includeStale: boolean }>({ direction: 'both', depth: 3, includeStale: false })
const lineageNodes = computed(() => new Map((lineageGraph.value?.nodes || []).map(node => [node.id, node])))

const warehouseVisible = ref(false)
const warehouseLoading = ref(false)
const warehouseSubmitting = ref(false)
const warehouseModelSubmitting = ref(false)
const warehouseTablesLoading = ref(false)
const warehouseTables = ref<CatalogTable[]>([])
const warehouseRuns = ref<WarehouseLoadRun[]>([])
const warehouseReconciliation = ref<WarehouseReconciliation>()
const warehouseForm = reactive<{ sourceDatasourceId?: number; targetDatasourceId?: number; sourceTable: string; targetTable: string; maxRows: number; loadMode: 'FULL' | 'INCREMENTAL'; watermarkColumn: string; keyColumnsText: string }>({ sourceTable: '', targetTable: '', maxRows: 100000, loadMode: 'FULL', watermarkColumn: '', keyColumnsText: '' })
const warehouseReady = computed(() => Boolean(warehouseForm.sourceDatasourceId && warehouseForm.targetDatasourceId && warehouseForm.sourceTable && warehouseForm.targetTable.trim()
  && (warehouseForm.loadMode === 'FULL' || (warehouseForm.watermarkColumn.trim() && warehouseForm.keyColumnsText.split(',').some(key => key.trim())))))
let warehousePollTimer: ReturnType<typeof setTimeout> | undefined
let warehousePollAttempts = 0

const scanVisible = ref(false)
const scanLoading = ref(false)
const savingScan = ref(false)
const triggeringId = ref<number | null>(null)
const scanSourceId = ref<number>()
const scanTab = ref<'definitions' | 'runs'>('definitions')
const scanDefinitions = ref<MetadataScanDefinition[]>([])
const scanRuns = ref<MetadataScanRun[]>([])
const scanForm = reactive({ name: '', includeTables: '', excludeTables: '' })
let pollTimer: ReturnType<typeof setTimeout> | undefined
let pollAttempts = 0

function unwrap<T>(response: unknown): T {
  return unwrapBiData(response as never) as T
}

async function previewBackfill() {
  backfillLoading.value = true
  try {
    const preview = unwrap<import('@/api/bi/catalog').CatalogBackfillPreview>(await previewCatalogBackfill())
    const breakdown = [`数据源 ${preview.DATASOURCE}`, `API 快照 ${preview.API_SOURCE}`, `数据集 ${preview.DATASET}`, `字段 ${preview.DATASET_FIELD}`, `图表 ${preview.CHART}`, `看板/大屏 ${preview.DASHBOARD}`, `指标 ${preview.METRIC}`].join('，')
    await ElMessageBox.confirm(`预计处理 ${preview.total} 项（${breakdown}）。仅回填资源名称、类型和引用 ID，不复制 SQL、图表定义或凭据。继续执行幂等回填？`, 'BI 资源目录回填', { type: 'info', confirmButtonText: '执行回填' })
    const result = unwrap<{ assetsProcessed: number; versionsCreated: number }>(await runCatalogBackfill())
    ElMessage.success(`回填完成：处理 ${result.assetsProcessed} 项，新增 ${result.versionsCreated} 个版本`)
    sourceKind.value = 'BI_RESOURCE'
    filters.datasourceId = undefined
    filters.type = undefined
    page.value = 1
    await loadAssets()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : 'BI 资源回填失败')
  } finally { backfillLoading.value = false }
}

async function openAudit() {
  auditVisible.value = true
  auditPage.value = 1
  await Promise.all([loadAudit(), loadAuditRetention()])
}

async function loadAudit() {
  auditLoading.value = true
  try {
    const result = unwrap<{ items: CatalogAuditRow[]; total: number }>(await listCatalogAudit({
      page: auditPage.value, pageSize: auditPageSize.value,
      actor: auditFilters.actor.trim() || undefined, action: auditFilters.action.trim() || undefined,
      outcome: auditFilters.outcome || undefined, from: auditFilters.from || undefined, to: auditFilters.to || undefined
    }))
    auditRows.value = result.items || []
    auditTotal.value = result.total || 0
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '审计记录加载失败') }
  finally { auditLoading.value = false }
}

function searchAudit() { auditPage.value = 1; void loadAudit() }
function resetAuditFilters() { Object.assign(auditFilters, { actor: '', action: '', outcome: '', from: undefined, to: undefined }); searchAudit() }

async function loadAuditRetention() {
  try {
    const policy = unwrap<import('@/api/bi/catalog').CatalogAuditRetentionPolicy>(await getCatalogAuditRetention())
    auditRetentionDays.value = policy.liveRetentionDays || 365
    auditRetentionUpdatedAt.value = policy.updatedAt || ''
    auditRetentionUpdatedBy.value = policy.updatedBy || ''
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '审计保留策略加载失败') }
}

async function saveAuditRetention() {
  auditRetentionSaving.value = true
  try {
    const policy = unwrap<import('@/api/bi/catalog').CatalogAuditRetentionPolicy>(await updateCatalogAuditRetention(auditRetentionDays.value))
    auditRetentionDays.value = policy.liveRetentionDays
    auditRetentionUpdatedAt.value = policy.updatedAt || ''
    auditRetentionUpdatedBy.value = policy.updatedBy || ''
    ElMessage.success('审计在线保留策略已更新')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '审计保留策略更新失败') }
  finally { auditRetentionSaving.value = false }
}

async function loadSources() {
  const response = await listEnabledDatasources()
  sources.value = unwrap<BiDatasource[]>(response) || []
}

async function openWarehouse() {
  if (!canScan.value) return
  warehouseVisible.value = true
  warehousePollAttempts = 0
  if (!warehouseForm.sourceDatasourceId || !warehouseSources.value.some(source => source.id === warehouseForm.sourceDatasourceId))
    warehouseForm.sourceDatasourceId = warehouseSources.value[0]?.id
  if (!warehouseForm.targetDatasourceId || !warehouseTargets.value.some(source => source.id === warehouseForm.targetDatasourceId))
    warehouseForm.targetDatasourceId = warehouseTargets.value[0]?.id
  await Promise.all([loadWarehouseTables(), loadWarehouseRuns()])
}

async function loadWarehouseTables() {
  warehouseForm.sourceTable = ''
  warehouseTables.value = []
  if (!warehouseForm.sourceDatasourceId) return
  const source = sources.value.find(item => item.id === warehouseForm.sourceDatasourceId)
  if (!source) return
  warehouseTablesLoading.value = true
  try {
    warehouseTables.value = unwrap<CatalogTable[]>(await listTables(source.schemaName)) || []
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '源表列表加载失败') }
  finally { warehouseTablesLoading.value = false }
}

async function loadWarehouseRuns() {
  stopWarehousePolling()
  if (!warehouseForm.targetDatasourceId) { warehouseRuns.value = []; warehouseReconciliation.value = undefined; return }
  warehouseLoading.value = true
  try {
    const [runsResult, reconciliationResult] = await Promise.all([
      listWarehouseLoadRuns(warehouseForm.targetDatasourceId),
      getRetailMonthlyReconciliation(warehouseForm.targetDatasourceId)
    ])
    warehouseRuns.value = unwrap<WarehouseLoadRun[]>(runsResult) || []
    warehouseReconciliation.value = unwrap<WarehouseReconciliation>(reconciliationResult) || undefined
  }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '入仓运行记录加载失败') }
  finally { warehouseLoading.value = false }
}

async function triggerWarehouse() {
  if (!warehouseReady.value) { ElMessage.warning('请选择源表和数仓目标，并填写目标表名'); return }
  const keyColumns = warehouseForm.keyColumnsText.split(',').map(key => key.trim()).filter(Boolean)
  if (warehouseForm.loadMode === 'INCREMENTAL' && new Set(keyColumns.map(key => key.toLowerCase())).size !== keyColumns.length) { ElMessage.warning('业务键字段不能重复'); return }
  try {
    await ElMessageBox.confirm(
      warehouseForm.loadMode === 'INCREMENTAL'
        ? `将从“${sourceName(warehouseForm.sourceDatasourceId!)}.${warehouseForm.sourceTable}”按水位字段 ${warehouseForm.watermarkColumn.trim()} 读取变化，并按业务键 ${keyColumns.join(', ')} 合并到 ClickHouse ODS 表“${warehouseForm.targetTable.trim()}”，最多读取 ${warehouseForm.maxRows.toLocaleString()} 行。删除记录不会自动同步。继续吗？`
        : `将从“${sourceName(warehouseForm.sourceDatasourceId!)}.${warehouseForm.sourceTable}”全量刷新到 ClickHouse ODS 表“${warehouseForm.targetTable.trim()}”，最多读取 ${warehouseForm.maxRows.toLocaleString()} 行。目标表仅在行数和结构校验通过后原子替换。继续吗？`,
      warehouseForm.loadMode === 'INCREMENTAL' ? '确认增量入仓' : '确认全量入仓', { type: 'warning', confirmButtonText: '开始入仓' })
    warehouseSubmitting.value = true
    const request = {
      sourceDatasourceId: warehouseForm.sourceDatasourceId!, targetDatasourceId: warehouseForm.targetDatasourceId!,
      sourceTable: warehouseForm.sourceTable, targetTable: warehouseForm.targetTable.trim(), maxRows: warehouseForm.maxRows,
      loadMode: warehouseForm.loadMode, watermarkColumn: warehouseForm.watermarkColumn.trim(), keyColumns
    }
    const run = unwrap<WarehouseLoadRun>(await triggerWarehouseLoad(request))
    ElMessage.success(`入仓任务已提交，运行 ID：${run.id}`)
    await loadWarehouseRuns()
    if (run.status === 'PENDING' || run.status === 'RUNNING') startWarehousePolling(run.id)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '入仓任务提交失败')
  } finally { warehouseSubmitting.value = false }
}

async function refreshRetailModel() {
  if (!warehouseForm.targetDatasourceId) { ElMessage.warning('请先选择 ClickHouse ODS 数据源'); return }
  try {
    await ElMessageBox.confirm(
      '将从已发布的 DWD 零售订单明细和组织、商品历史维度重建 DWS 月度汇总。维度缺失、多重匹配或目标模型 DDL 未安装时会阻止发布；旧汇总在校验成功前保持可用。',
      '刷新零售月度模型', { type: 'warning', confirmButtonText: '开始刷新' })
    warehouseModelSubmitting.value = true
    const run = unwrap<WarehouseLoadRun>(await refreshRetailMonthlyModel(warehouseForm.targetDatasourceId))
    ElMessage.success(`模型刷新任务已提交，运行 ID：${run.id}`)
    await loadWarehouseRuns()
    if (run.status === 'PENDING' || run.status === 'RUNNING') startWarehousePolling(run.id)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '模型刷新任务提交失败')
  } finally { warehouseModelSubmitting.value = false }
}

function startWarehousePolling(id: number) {
  stopWarehousePolling()
  warehousePollAttempts = 0
  const poll = async () => {
    warehousePollAttempts++
    try {
      const latest = unwrap<WarehouseLoadRun>(await getWarehouseLoadRun(id))
      const index = warehouseRuns.value.findIndex(run => run.id === id)
      if (index >= 0) warehouseRuns.value.splice(index, 1, latest)
      else warehouseRuns.value.unshift(latest)
      if (latest.status === 'PENDING' || latest.status === 'RUNNING') {
        if (warehousePollAttempts < 240) warehousePollTimer = setTimeout(poll, 2500)
      } else if (latest.status === 'SUCCEEDED') {
        ElMessage.success(`入仓完成：${Number(latest.rowCount).toLocaleString()} 行已发布`)
      } else if (latest.status === 'FAILED') {
        ElMessage.error(latest.errorSummary || '入仓失败，目标表未发布')
      }
    } catch { /* keep polling state visible; users can refresh manually */ }
  }
  warehousePollTimer = setTimeout(poll, 1200)
}

function stopWarehousePolling() { if (warehousePollTimer) clearTimeout(warehousePollTimer); warehousePollTimer = undefined }
function warehouseStatusLabel(status: string) { return ({ PENDING: '排队中', RUNNING: '运行中', SUCCEEDED: '成功', FAILED: '失败', INTERRUPTED: '服务中断' } as Record<string, string>)[status] || status }
function warehouseTagType(status: string) { return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'INTERRUPTED' ? 'warning' : 'info' }
function freshnessLabel(value?: number) {
  if (value == null) return '暂无新鲜度数据'
  const seconds = Math.max(0, Number(value))
  if (seconds < 60) return `${seconds} 秒前`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟前`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} 小时前`
  return `${Math.floor(seconds / 86400)} 天前`
}

async function loadAssets() {
  if (!canView.value) return
  loading.value = true
  try {
    const response = await searchCatalogAssets({
      sourceKind: sourceKind.value,
      datasourceId: filters.datasourceId,
      type: filters.type,
      query: filters.query.trim() || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    const data = unwrap<{ items?: CatalogAsset[]; total?: number }>(response)
    assets.value = data.items || []
    total.value = data.total || 0
  } catch (error) {
    assets.value = []
    total.value = 0
    ElMessage.error(error instanceof Error ? error.message : '资产目录加载失败')
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; void loadAssets() }
function switchSourceKind() { filters.datasourceId = undefined; filters.type = undefined; page.value = 1; void loadAssets() }
function resetFilters() { filters.datasourceId = undefined; filters.type = undefined; filters.query = ''; search() }
function sourceName(id: number) { return sourceNames.value.get(id) || `数据源 #${id}` }
function biSourceLabel(type: string) { return ({ DATASOURCE: 'BI 数据源', API_SOURCE: 'API 数据源快照', DATASET: 'BI 数据集', DATASET_FIELD: 'BI 数据集字段', CHART: 'BI 图表', DASHBOARD: 'BI 看板 / 大屏', METRIC: 'BI 指标' } as Record<string, string>)[type] || 'BI 资源' }
function openSource(id: number) { void router.push({ path: '/bi-admin/datasource', query: { datasourceId: String(id) } }) }
function typeLabel(type: string) { return ({ SCHEMA: 'Schema', TABLE: '数据表', COLUMN: '字段', DATASOURCE: '数据源', API_SOURCE: 'API 快照', DATASET: '数据集', DATASET_FIELD: '数据集字段', CHART: '图表', DASHBOARD: '看板 / 大屏', METRIC: '指标' } as Record<string, string>)[type] || type }
function assetIcon(type: string) { return type === 'SCHEMA' ? Coin : type === 'TABLE' ? Grid : Tickets }
function formatTime(value?: string) { if (!value) return '—'; const date = new Date(value.replace(' ', 'T')); return Number.isNaN(date.getTime()) ? value : date.toLocaleString() }
function metadataRows(metadata?: Record<string, unknown>) {
  return Object.entries(metadata || {}).map(([key, value]) => ({ key, value: value === null || value === undefined ? '—' : typeof value === 'object' ? JSON.stringify(value) : String(value) }))
}
function changeTypeLabel(type?: string) { return type === 'CREATED' ? '首次发现' : type === 'METADATA_CHANGED' ? '结构变更' : type || '元数据快照' }
function lineageNodeName(id: number) { return lineageNodes.value.get(id)?.name || `资产 #${id}` }
function lineageKindLabel(kind: string) { return ({ DERIVES: '派生', CONTAINS: '包含', MATERIALIZES: '物化', REFERENCES: '引用' } as Record<string, string>)[kind] || kind }
function schemaVersionNo(id?: number) { return detailVersions.value.find(version => version.id === id)?.versionNo ?? id ?? '—' }
function schemaChangeLabels(event: SchemaChangeEvent) {
  const fields = event.details?.fields || {}
  const labels: string[] = []
  for (const field of Array.isArray(fields.added) ? fields.added : []) labels.push(`新增 ${String(field)}`)
  for (const field of Array.isArray(fields.removed) ? fields.removed : []) labels.push(`删除 ${String(field)}`)
  for (const field of Array.isArray(fields.typeChanges) ? fields.typeChanges : []) {
    if (field && typeof field === 'object') { const value = field as Record<string, unknown>; labels.push(`类型 ${String(value.field)}：${String(value.beforeType)} → ${String(value.afterType)}`) }
  }
  for (const field of Array.isArray(fields.changed) ? fields.changed : []) labels.push(`属性变化 ${String(field)}`)
  return labels.length ? labels.slice(0, 8) : ['表结构变化']
}
function fieldImpactLabel(status: string) { return ({ SOURCE_FIELD_ADDED: '源端新增', SOURCE_FIELD_REMOVED: '源端已删除', SOURCE_TYPE_CHANGED: '类型已变化', SOURCE_FIELD_METADATA_CHANGED: '字段属性已变化', NOT_IN_DATASET_MODEL: '尚未加入数据集' } as Record<string, string>)[status] || status }
function fieldImpactTagType(status: string) { return status === 'SOURCE_FIELD_REMOVED' || status === 'SOURCE_TYPE_CHANGED' ? 'danger' : status === 'NOT_IN_DATASET_MODEL' ? 'warning' : 'info' }

async function openSchemaChanges(row: CatalogAsset) {
  schemaChangeAsset.value = row
  schemaChangesVisible.value = true
  schemaChangesLoading.value = true
  try { schemaChangeEvents.value = unwrap<SchemaChangeEvent[]>(await listSchemaChangeEvents(row.id)) || [] }
  catch (error) { schemaChangeEvents.value = []; ElMessage.error(error instanceof Error ? error.message : '结构变化记录加载失败') }
  finally { schemaChangesLoading.value = false }
}

function impactSeverityType(severity: string) {
  if (severity === 'BLOCKING') return 'error'
  if (severity === 'REVIEW') return 'warning'
  if (severity === 'COMPATIBLE') return 'success'
  return 'info'
}

async function runOnlineSample(row: CatalogAsset) {
  onlineSampleLoading.value = true
  try {
    const { onlineSampleValidate } = await import('@/api/bi/fieldStandard')
    const res = await onlineSampleValidate(row.id, 100)
    const data = (res as any)?.data || res
    const summary = data?.summary || {}
    const ok = data?.passed
    const msg = `标准 ${data?.standardCode || ''} · 抽样 ${summary.sampled ?? 0} · 通过 ${summary.passed ?? 0} · 失败 ${summary.failed ?? 0}`
    if (ok) ElMessage.success(`JDBC 在线抽样通过：${msg}`)
    else ElMessage.error(`JDBC 在线抽样未通过：${msg}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'JDBC 在线抽样失败')
  } finally {
    onlineSampleLoading.value = false
  }
}

async function openImpactPack(row: CatalogAsset) {
  impactPackVisible.value = true
  impactPackLoading.value = true
  impactPack.value = null
  try { impactPack.value = unwrap<OpsImpactPack>(await getOpsImpactPack(row.id)) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '运维影响包加载失败') }
  finally { impactPackLoading.value = false }
}

async function copyImpactPack() {
  if (!impactPack.value) return
  try {
    await navigator.clipboard.writeText(JSON.stringify(impactPack.value, null, 2))
    ElMessage.success('影响包 JSON 已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择内容')
  }
}

async function openSchemaImpact(event: SchemaChangeEvent) {
  impactLoadingId.value = event.id
  schemaImpactVisible.value = true
  schemaImpactLoading.value = true
  schemaImpact.value = null
  schemaImpactTab.value = 'fields'
  try { schemaImpact.value = unwrap<SchemaChangeImpact>(await getSchemaChangeImpact(event.id)); await loadMigrationPlans(event.id) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '影响预览加载失败') }
  finally { schemaImpactLoading.value = false; impactLoadingId.value = null }
}

function migrationKey(row: Record<string, unknown>) { return `${row.datasetId}:${row.fieldName}` }
function migrationStatus(status: string) { return ({ DRAFT: '待审批', BLOCKED: '有阻断项', APPROVED: '已审批', EXECUTED: '已执行', FAILED: '执行失败', ROLLED_BACK: '已回滚' } as Record<string,string>)[status] || status }
async function loadMigrationPlans(eventId = schemaImpact.value?.event.id) {
  if (!eventId || !canMigrateSchema.value) return
  migrationLoading.value = true
  try { migrationPlans.value = unwrap<SchemaMigrationPlan[]>(await listSchemaMigrationPlans(eventId)) || [] }
  catch (error) { migrationPlans.value = []; if (schemaImpactVisible.value) ElMessage.error(error instanceof Error ? error.message : '迁移计划加载失败') }
  finally { migrationLoading.value = false }
}
async function createMigration() {
  const eventId = schemaImpact.value?.event.id
  if (!eventId || !schemaImpact.value) return
  const mappings = schemaImpact.value.datasetFields.map(row => ({ datasetId: Number(row.datasetId), sourceField: String(row.fieldName), targetField: migrationTargets[migrationKey(row)] === '__DROP__' ? null : migrationTargets[migrationKey(row)] || null }))
  if (mappings.some(item => !item.targetField) && mappings.some(item => migrationTargets[`${item.datasetId}:${item.sourceField}`] !== '__DROP__')) { ElMessage.warning('请为每个受影响字段选择目标字段或明确选择删除'); return }
  migrationLoading.value = true
  try { const result=unwrap<SchemaMigrationPlan>(await createSchemaMigrationPlan(eventId,mappings)); ElMessage.success(result.status === 'BLOCKED' ? '计划已创建，但存在安全阻断项' : '迁移计划已生成，等待审批'); await loadMigrationPlans(eventId) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '迁移计划创建失败') }
  finally { migrationLoading.value = false }
}
async function approveMigration(plan: SchemaMigrationPlan) { try { await ElMessageBox.confirm('审批后该计划可执行数据集字段映射。是否继续？','审批迁移计划',{type:'warning'}); await approveSchemaMigrationPlan(plan.id); ElMessage.success('迁移计划已审批'); await loadMigrationPlans() } catch (error) { if(error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '审批失败') } }
async function executeMigration(plan: SchemaMigrationPlan) { try { await ElMessageBox.confirm('将按计划更新数据集字段映射并重建查询定义。是否执行？','执行迁移计划',{type:'warning'}); await executeSchemaMigrationPlan(plan.id); ElMessage.success('迁移执行完成'); await loadMigrationPlans() } catch (error) { if(error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '执行失败') } }
async function rollbackMigration(plan: SchemaMigrationPlan) { try { await ElMessageBox.confirm('将恢复该计划执行前保存的字段映射和查询定义。是否回滚？','回滚迁移计划',{type:'warning'}); await rollbackSchemaMigrationPlan(plan.id); ElMessage.success('迁移计划已回滚'); await loadMigrationPlans() } catch (error) { if(error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '回滚失败') } }

async function openLineage(row: CatalogAsset) {
  lineageRoot.value = row
  lineageVisible.value = true
  lineageQuery.direction = 'both'
  lineageQuery.depth = 3
  lineageQuery.includeStale = false
  await loadLineage()
}

async function loadLineage() {
  if (!lineageRoot.value) return
  lineageLoading.value = true
  try { lineageGraph.value = unwrap<LineageGraph>(await getLineageGraph(lineageRoot.value.id, lineageQuery)) }
  catch (error) { lineageGraph.value = null; ElMessage.error(error instanceof Error ? error.message : '数据血缘加载失败') }
  finally { lineageLoading.value = false }
}

async function rebuildLineage() {
  try {
    await ElMessageBox.confirm('将依据目录层级和已登记的 BI 资源引用重建表级血缘；不读取或修改业务表数据。继续吗？', '重建数据血缘', { type: 'info', confirmButtonText: '开始重建' })
    lineageRebuildLoading.value = true
    const result = unwrap<Record<string, unknown>>(await rebuildCatalogLineage())
    ElMessage.success(`血缘重建完成：扫描 ${Number(result.assetsScanned || 0)} 项资产，当前关系 ${Number(result.activeEdgesObserved || 0)} 条`)
    if (lineageRoot.value) await loadLineage()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '血缘重建失败')
  } finally {
    lineageRebuildLoading.value = false
  }
}

async function openDetail(row: CatalogAsset) {
  detailVisible.value = true
  detailAsset.value = row
  detailVersions.value = []
  consumeActions.value = []
  try {
    const [assetResponse, versionResponse, linksResponse] = await Promise.all([
      getCatalogAsset(row.id), listCatalogAssetVersions(row.id), getCatalogConsumeLinks(row.id)
    ])
    detailAsset.value = unwrap<CatalogAsset>(assetResponse)
    detailVersions.value = unwrap<CatalogAssetVersion[]>(versionResponse) || []
    consumeActions.value = (unwrap<{ consumeActions?: CatalogConsumeAction[] }>(linksResponse)?.consumeActions || [])
      .filter(action => action.enabled !== false)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资产详情加载失败')
  }
}

function runConsumeAction(action: CatalogConsumeAction) {
  const query: Record<string, string> = {}
  Object.entries(action.query || {}).forEach(([key, value]) => { query[key] = String(value) })
  router.push({ path: action.path, query })
}

async function openVersions(row: CatalogAsset) {
  versionAsset.value = row
  versionsVisible.value = true
  detailVersions.value = []
  try { detailVersions.value = unwrap<CatalogAssetVersion[]>(await listCatalogAssetVersions(row.id)) || [] }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '版本历史加载失败') }
}

async function openScans() {
  if (!canScan.value) return
  scanVisible.value = true
  scanSourceId.value = filters.datasourceId || sources.value[0]?.id
  scanTab.value = 'definitions'
  if (scanSourceId.value) await loadScanData()
}

async function loadScanData() {
  stopPolling()
  if (!scanSourceId.value) { scanDefinitions.value = []; scanRuns.value = []; return }
  scanLoading.value = true
  try {
    const [definitions, runs] = await Promise.all([listMetadataScans(scanSourceId.value), listMetadataScanRuns(scanSourceId.value)])
    scanDefinitions.value = unwrap<MetadataScanDefinition[]>(definitions) || []
    scanRuns.value = unwrap<MetadataScanRun[]>(runs) || []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '扫描配置或运行记录加载失败')
  } finally {
    scanLoading.value = false
  }
}

function splitTables(value: string) {
  return [...new Set(value.split(/[，,\n\r]+/).map(name => name.trim()).filter(Boolean))]
}

async function saveScan() {
  if (!scanSourceId.value || !scanForm.name.trim()) { ElMessage.warning('请填写配置名称并选择数据源'); return }
  const includeTables = splitTables(scanForm.includeTables)
  const excludeTables = splitTables(scanForm.excludeTables)
  if (includeTables.length > 500 || excludeTables.length > 500) { ElMessage.warning('每个扫描范围最多 500 张表'); return }
  if (includeTables.some(name => excludeTables.includes(name))) { ElMessage.warning('同一张表不能同时包含和排除'); return }
  savingScan.value = true
  const request: MetadataScanSaveRequest = { name: scanForm.name.trim(), scope: { includeTables, excludeTables }, status: '0' }
  try {
    unwrap(await createMetadataScan(scanSourceId.value, request))
    scanForm.name = ''; scanForm.includeTables = ''; scanForm.excludeTables = ''
    ElMessage.success('扫描配置已创建')
    await loadScanData()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '扫描配置创建失败') }
  finally { savingScan.value = false }
}

async function toggleScan(row: MetadataScanDefinition) {
  const scope = parseScope(row.scopeJson)
  try {
    unwrap(await updateMetadataScan(row.id, { name: row.name, scope, status: row.status === '0' ? '1' : '0' }))
    ElMessage.success(row.status === '0' ? '扫描配置已停用' : '扫描配置已启用')
    await loadScanData()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '扫描配置更新失败') }
}

function parseScope(value: string): MetadataScanSaveRequest['scope'] {
  try {
    const scope = JSON.parse(value || '{}')
    return { includeTables: Array.isArray(scope.includeTables) ? scope.includeTables : [], excludeTables: Array.isArray(scope.excludeTables) ? scope.excludeTables : [] }
  } catch { return { includeTables: [], excludeTables: [] } }
}

function scopeLabel(value: string) {
  const scope = parseScope(value)
  if (!scope.includeTables.length && !scope.excludeTables.length) return '全部数据表'
  const parts: string[] = []
  if (scope.includeTables.length) parts.push(`包含 ${scope.includeTables.length} 张`)
  if (scope.excludeTables.length) parts.push(`排除 ${scope.excludeTables.length} 张`)
  return parts.join(' · ')
}

async function removeScan(row: MetadataScanDefinition) {
  try {
    await ElMessageBox.confirm(`删除扫描配置“${row.name}”？已有运行历史会保留。`, '删除扫描配置', { type: 'warning' })
    unwrap(await deleteMetadataScan(row.id))
    ElMessage.success('扫描配置已删除')
    await loadScanData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '扫描配置删除失败')
  }
}

async function runScan(row: MetadataScanDefinition) {
  triggeringId.value = row.id
  scanTab.value = 'runs'
  try {
    const run = unwrap<MetadataScanRun>(await triggerMetadataScan(row.id))
    ElMessage.success(`扫描已进入队列，运行 ID ${run.id}`)
    await loadScanData()
    pollRun(run.id)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '无法启动扫描') }
  finally { triggeringId.value = null }
}

function pollRun(id: number) {
  stopPolling()
  pollAttempts = 0
  const poll = async () => {
    pollAttempts++
    try {
      const latest = unwrap<MetadataScanRun>(await getMetadataScanRun(id))
      scanRuns.value = [latest, ...scanRuns.value.filter(run => run.id !== id)].slice(0, 100)
      await loadAssets()
      if (latest.status === 'PENDING' || latest.status === 'RUNNING') {
        if (pollAttempts < 120) pollTimer = setTimeout(poll, 2500)
      } else if (latest.status === 'SUCCEEDED') {
        ElMessage.success('元数据扫描完成，资产目录已刷新')
      } else if (latest.status === 'PARTIAL_SUCCESS') {
        ElMessage.warning('扫描部分完成，请查看运行记录中的失败摘要')
      } else if (latest.status === 'FAILED') {
        ElMessage.error(latest.errorSummary || '元数据扫描失败')
      }
    } catch { /* keep the current run row visible; users can refresh manually */ }
  }
  pollTimer = setTimeout(poll, 1200)
}

function stopPolling() { if (pollTimer) clearTimeout(pollTimer); pollTimer = undefined }
function progress(run: MetadataScanRun) { return run.progressTotal > 0 ? Math.min(100, Math.round(run.progressCompleted * 100 / run.progressTotal)) : run.status === 'SUCCEEDED' ? 100 : 0 }
function runStatusLabel(status: string) { return ({ PENDING: '排队中', RUNNING: '运行中', SUCCEEDED: '成功', PARTIAL_SUCCESS: '部分成功', FAILED: '失败' } as Record<string, string>)[status] || status }
function runTagType(status: string) { return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'PARTIAL_SUCCESS' ? 'warning' : 'info' }
function runSummary(run: MetadataScanRun) {
  if (!run.resultSummaryJson) return ''
  try {
    const value = JSON.parse(run.resultSummaryJson)
    return `${value.tableCount ?? 0} 张表 · ${value.columnsDiscovered ?? 0} 个字段 · ${value.assetsUpserted ?? 0} 项资产`
  } catch { return '' }
}

onMounted(async () => {
  if (!canView.value) return
  try {
    await loadSources()
    const requestedSource = Number(route.query.datasourceId)
    if (Number.isSafeInteger(requestedSource) && sources.value.some(source => source.id === requestedSource)) filters.datasourceId = requestedSource
    await loadAssets()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '目录页面初始化失败') }
})
onUnmounted(() => { stopPolling(); stopWarehousePolling() })
</script>

<style scoped>
.catalog-page{min-height:calc(100dvh - 64px);padding:var(--bi-page-pad-y,24px) var(--bi-page-pad-x,32px) 42px;background:var(--bi-background,#f6f8fc);color:var(--bi-text,#182230)}
.migration-card{margin-top:16px}.migration-heading{display:flex;align-items:center;justify-content:space-between}.migration-table{margin-top:12px}.migration-table .el-select{width:100%}.migration-actions{display:flex;justify-content:flex-end;padding:12px 0}
.impact-note{margin:12px 0}.pack-section-title{margin:16px 0 8px;font-size:15px}.pack-checklist{margin:0 0 14px;padding-left:20px;line-height:1.7;color:var(--bi-muted,#667085)}.pack-secondary-table{margin-top:12px}.pack-note{margin-top:12px;font-size:12px;color:var(--bi-muted,#667085)}
.detail-actions{flex-wrap:wrap;gap:8px}
.catalog-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:20px;margin-bottom:24px}.catalog-heading h1{margin:0 0 6px;font-size:30px;line-height:1.3;font-weight:700;letter-spacing:-.4px}.catalog-heading p{margin:0;color:var(--bi-muted,#667085);font-size:14px;line-height:1.7}.heading-actions{display:flex;gap:10px;flex-shrink:0}
.catalog-toolbar{display:flex;align-items:center;gap:12px;margin-bottom:18px}.source-filter{width:230px}.type-filter{width:155px}.asset-search{width:min(380px,34vw)}
.catalog-table{border:1px solid var(--bi-border,#e6ebf3);border-radius:var(--bi-radius-lg,10px);--el-table-header-bg-color:var(--bi-surface,#fff);--el-table-bg-color:var(--bi-surface,#fff);--el-table-tr-bg-color:var(--bi-surface,#fff);--el-table-border-color:var(--bi-border,#e6ebf3)}.catalog-table :deep(th.el-table__cell){height:50px;color:var(--bi-muted,#667085);font-weight:600}.catalog-table :deep(td.el-table__cell){height:68px}
.asset-name{display:flex;align-items:center;gap:12px;min-width:0}.asset-icon{display:grid;place-items:center;width:36px;height:36px;border-radius:9px;background:#eef3ff;color:#3563e9;font-size:18px;flex:none}.asset-icon.SCHEMA{background:#f1edff;color:#6941c6}.asset-icon.COLUMN{background:#e9f8f4;color:#14866d}.asset-name-text{display:grid;gap:3px;min-width:0}.asset-name-text strong,.asset-name-text small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.asset-name-text strong{font-weight:600}.asset-name-text small{font-size:12px;color:var(--bi-muted,#667085)}
.catalog-pagination{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:18px 2px;color:var(--bi-muted,#667085);font-size:13px}.detail-actions{display:flex;justify-content:flex-end;margin-top:16px}.metadata-section{margin-top:26px}.section-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-title h3{margin:0;font-size:16px;font-weight:650}.section-title span,.muted{color:var(--bi-muted,#667085);font-size:12px}.urn-value{word-break:break-all;white-space:normal;font-size:12px;color:#526174}
.lineage-toolbar{display:flex;align-items:center;gap:14px;flex-wrap:wrap;margin-bottom:16px}.lineage-depth{width:110px}.lineage-limit-note{margin-bottom:14px}.lineage-list{display:grid;gap:10px;max-height:58vh;overflow:auto}.lineage-edge-row{display:grid;grid-template-columns:minmax(0,1fr) 190px minmax(0,1fr) 78px;align-items:center;gap:14px;padding:14px;border:1px solid var(--bi-border,#e6ebf3);border-radius:10px;background:var(--bi-surface,#fff)}.lineage-node{display:grid;gap:5px;min-width:0}.lineage-node strong,.lineage-node small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.lineage-node strong{font-size:13px}.lineage-node small,.lineage-relation small{font-size:11px;color:var(--bi-muted,#667085)}.lineage-relation{display:grid;grid-template-columns:auto 18px;align-items:center;gap:5px 8px;color:#3563e9;font-size:12px;font-weight:600}.lineage-relation small{grid-column:1/-1;font-weight:400}.lineage-footer{margin-right:auto;color:var(--bi-muted,#667085);font-size:12px}
.audit-pagination{display:flex;align-items:center;justify-content:space-between;padding-top:16px;color:var(--bi-muted,#667085);font-size:13px}
.audit-filter-bar{display:flex;align-items:center;gap:9px;flex-wrap:wrap;margin-bottom:14px}.audit-filter-input{width:160px}.audit-filter-date{width:205px}.audit-retention{margin-bottom:14px}.audit-retention-controls{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.audit-retention-controls .el-input-number{width:150px}.audit-retention-meta{color:var(--bi-muted,#667085);font-size:12px}
.version-timeline{padding:8px 8px 0}.version-card{border-color:var(--bi-border,#e6ebf3)}.version-card :deep(.el-card__body){padding:14px 16px}.version-heading{display:flex;align-items:center;gap:10px}.change-tags{display:flex;flex-wrap:wrap;gap:7px;margin-top:12px}.version-hash{margin-top:12px;font:11px/1.6 ui-monospace,monospace;color:var(--bi-muted,#667085);word-break:break-all}
.scan-source-row{display:flex;align-items:center;gap:12px;margin-bottom:14px}.scan-source-row>span{font-size:13px;font-weight:600}.scan-source-select{width:320px}.scan-scope-note{margin-bottom:16px}.scan-create-card{margin-bottom:18px;border-color:var(--bi-border,#e6ebf3)}.scan-form :deep(.el-form-item){margin-bottom:12px}.scan-form-actions{display:flex;justify-content:flex-end}.scan-tabs{margin-top:8px}.scan-tabs :deep(.el-table){margin-top:8px}
@media(max-width:900px){.catalog-page{padding:24px 20px}.catalog-toolbar{flex-wrap:wrap}.asset-search{width:auto;flex:1;min-width:220px}.source-filter{flex:1;min-width:190px}}
@media(max-width:600px){.catalog-page{padding:20px 14px}.catalog-heading{display:block}.catalog-heading h1{font-size:25px}.heading-actions{margin-top:16px}.catalog-toolbar{padding:12px}.catalog-toolbar>*{width:100%!important}.catalog-pagination{display:block}.catalog-pagination .el-pagination{margin-top:12px;justify-content:center}.scan-source-row{align-items:flex-start;flex-wrap:wrap}.scan-source-select{width:100%}.scan-form :deep(.el-form-item__label){width:90px!important}.audit-filter-input,.audit-filter-date{width:100%}.audit-retention-controls>*{max-width:100%}}
@media(max-width:700px){.lineage-edge-row{grid-template-columns:minmax(0,1fr) 32px minmax(0,1fr);gap:8px;padding:11px}.lineage-edge-row>.el-tag{grid-column:1/-1;justify-self:end}.lineage-relation{grid-template-columns:auto 18px}.lineage-relation small{font-size:10px}}
</style>
