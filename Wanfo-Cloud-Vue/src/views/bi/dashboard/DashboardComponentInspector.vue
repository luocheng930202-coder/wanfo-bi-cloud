<template>
  <div class="component-inspector">
    <el-tabs v-model="subTab" stretch class="sub-tabs">
      <el-tab-pane label="布局" name="layout">
        <el-form label-position="top" :disabled="disabled" class="property-form">
          <template v-if="screen">
            <div class="property-section">容器归属</div>
            <el-select v-model="targetContainer" aria-label="目标容器"><el-option v-for="c in containers" :key="c.key" :value="c.key" :label="c.title || '未命名容器'"/></el-select>
            <div class="property-actions"><el-button :disabled="disabled || !targetContainer" @click="emit('attach')">放入容器</el-button><el-button :disabled="disabled || !panel.parentKey" @click="emit('detach')">移出容器</el-button></div>
          </template>
          <el-form-item label="组件名称"><el-input :model-value="panel.title" maxlength="200" @update:model-value="v => patch({ title: v })"/></el-form-item>
          <div class="property-section">{{ screen ? '位置与尺寸（像素）' : '位置与尺寸' }}</div>
          <div class="geometry-grid">
            <el-form-item v-for="(label, key) in geometryLabels" :key="key" :label="label">
              <el-input type="number" :model-value="panel[key]" @change="v => emit('geometry', key, Number(v))"/>
            </el-form-item>
          </div>
          <div v-if="screen" class="property-actions">
            <el-button :disabled="disabled" @click="emit('layer', -1)">下移一层</el-button>
            <el-button :disabled="disabled" @click="emit('layer', 1)">上移一层</el-button>
          </div>
          <el-divider/>
          <div class="property-actions">
            <el-button :disabled="disabled || duplicateDisabled" @click="emit('duplicate')">复制组件</el-button>
            <el-button type="danger" plain :disabled="disabled" @click="emit('remove')">删除组件</el-button>
          </div>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="样式" name="style">
        <el-form label-position="top" :disabled="disabled" class="property-form">
          <h3 class="inspector-section-title">基础设置</h3>
          <el-form-item label="组件名称"><el-input :model-value="panel.title" maxlength="200" aria-label="样式组件名称" @update:model-value="v => patch({ title: v })" /></el-form-item>
          <el-form-item v-if="panel.componentType !== 'border' && panel.componentType !== 'line'" label="显示标题" class="inline-switch"><el-switch :model-value="panel.style!.showTitle" @update:model-value="v => patchStyle({ showTitle: v })"/></el-form-item>
          <el-form-item v-if="panel.componentType !== 'border' && panel.componentType !== 'line'" label="标题对齐">
            <el-radio-group :model-value="panel.style!.titleAlign" class="title-align-control" aria-label="标题对齐" @update:model-value="v => patchStyle({ titleAlign: v })"><el-radio-button value="left">左对齐</el-radio-button><el-radio-button value="center">居中</el-radio-button><el-radio-button value="right">右对齐</el-radio-button></el-radio-group>
          </el-form-item>
          <el-form-item v-if="panel.componentType === 'text'" label="文本内容"><el-input :model-value="panel.content!.text" type="textarea" :rows="7" maxlength="2000" show-word-limit @update:model-value="v => patchContent({ text: v })"/></el-form-item>
          <template v-if="panel.content?.decoration">
            <div class="property-section">装饰样式</div>
            <el-form-item label="颜色"><el-color-picker :model-value="panel.content.decoration.color" color-format="hex" @update:model-value="v => patchDecoration({ color: v })"/></el-form-item>
            <el-form-item label="线宽"><el-input-number :model-value="panel.content.decoration.stroke" :min="1" :max="12" @update:model-value="v => patchDecoration({ stroke: v })"/></el-form-item>
            <el-form-item label="线型"><el-select :model-value="panel.content.decoration.pattern" @update:model-value="v => patchDecoration({ pattern: v })"><el-option label="实线" value="solid"/><el-option label="虚线" value="dashed"/><el-option label="点线" value="dotted"/></el-select></el-form-item>
            <el-form-item v-if="panel.componentType === 'border'" label="圆角"><el-input-number :model-value="panel.content.decoration.radius" :min="0" :max="80" @update:model-value="v => patchDecoration({ radius: v })"/></el-form-item>
            <el-form-item v-else-if="panel.componentType === 'line'" label="方向"><el-select :model-value="panel.content.decoration.direction" @update:model-value="v => patchDecoration({ direction: v })"><el-option label="水平" value="horizontal"/><el-option label="垂直" value="vertical"/></el-select></el-form-item>
          </template>
          <template v-if="panel.componentType === 'image'">
            <div class="property-section">图片内容</div>
            <slot name="image"/>
          </template>
          <DashboardNumberFormatEditor v-if="panel.chartId" :model-value="panel.style!.numberFormat" :appearance="appearance" @update:model-value="v => patchStyle({ numberFormat: v })"/>
          <DashboardMetricStyleEditor v-if="panel.chartId" :model-value="panel.style!.metrics" :metrics="metricKeys" :appearance="appearance" @update:model-value="v => patchStyle({ metrics: v })"/>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="数据" name="data">
        <div class="property-form">
          <template v-if="panel.chartId">
            <div class="property-section">数据绑定</div>
            <p class="source-name">{{ chartName }}</p>
            <p v-if="panel.componentType === 'kpi'" class="property-help">取当前组件第一个聚合度量，按当前权限与筛选汇总。</p>
            <el-button :disabled="disabled" @click="emit('replace-chart')">更换图表</el-button>
            <DashboardChartEditor :kind="kind" :layout="layout" :scene="scene" :panel="panel" :id="dashboardId" :version="version" :disabled="disabled" @apply="(_key, config, payload) => emit('chart-config', config, payload)"/>
          </template>
          <p v-else class="property-help">文本、图片与装饰组件不取数；图表与指标卡请从左侧添加并绑定已保存图表。</p>
        </div>
      </el-tab-pane>
      <el-tab-pane label="交互" name="interaction">
        <div class="property-form">
          <p class="property-help">筛选控件与图表联动在整板维度配置，发布后查看端统一生效。</p>
          <el-button :disabled="disabled" type="primary" plain @click="emit('open-interaction')">打开筛选与联动</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { DashboardAppearance, Panel, PanelChartConfig, PanelResult, ScreenScene } from '@/api/bi/dashboard'
import DashboardChartEditor from './DashboardChartEditor.vue'
import DashboardNumberFormatEditor from './DashboardNumberFormatEditor.vue'
import DashboardMetricStyleEditor from './DashboardMetricStyleEditor.vue'

const props = defineProps<{
  panel: Panel
  appearance: DashboardAppearance
  screen: boolean
  disabled?: boolean
  duplicateDisabled?: boolean
  chartName: string
  kind?: 'dashboard' | 'screen'
  layout?: 'grid' | 'absolute'
  scene?: ScreenScene
  dashboardId?: number
  version?: number
  containers: Panel[]
  targetContainer: string
  metricKeys: string[]
}>()
const emit = defineEmits<{
  (e: 'patch', patch: Partial<Panel>): void
  (e: 'geometry', key: 'x' | 'y' | 'w' | 'h', value: number): void
  (e: 'layer', step: number): void
  (e: 'duplicate'): void
  (e: 'remove'): void
  (e: 'replace-chart'): void
  (e: 'chart-config', config: PanelChartConfig | undefined, payload: PanelResult): void
  (e: 'open-interaction'): void
  (e: 'attach'): void
  (e: 'detach'): void
  (e: 'update:targetContainer', value: string): void
}>()
const subTab = ref('style')
const targetContainer = computed({
  get: () => props.targetContainer,
  set: (v: string) => emit('update:targetContainer', v)
})
const geometryLabels = computed(() =>
  props.screen
    ? { x: '横向位置', y: '纵向位置', w: '宽度（像素）', h: '高度（像素）' }
    : { x: '横向位置', y: '纵向位置', w: '宽度（列）', h: '高度（行）' }
)
watch(() => props.panel.key, () => { subTab.value = 'style' })
function patch(p: Partial<Panel>) { emit('patch', p) }
function patchStyle(p: Record<string, unknown>) { patch({ style: { ...props.panel.style!, ...p } }) }
function patchContent(p: Record<string, unknown>) { patch({ content: { ...props.panel.content, ...p } }) }
function patchDecoration(p: Record<string, unknown>) {
  patch({ content: { decoration: { ...props.panel.content!.decoration!, ...p } } })
}
</script>
<style scoped>
.sub-tabs :deep(.el-tabs__header){padding:0 18px;margin-bottom:10px}
.sub-tabs :deep(.el-tabs__item){font-size:14px;height:44px}
.property-form{padding:10px 20px 24px}
.property-section{font-size:14px;font-weight:650;color:#526078;margin:16px 0 10px}
.property-help{font-size:12px;color:#8993a5;line-height:1.8}
.source-name{font-size:12px;color:#546784}
.geometry-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 10px}
.property-actions{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}
.component-inspector :deep(.el-form-item__label){font-size:13px;color:#667085}.component-inspector :deep(.el-input__wrapper),.component-inspector :deep(.el-select__wrapper){min-height:35px;font-size:13px}.component-inspector :deep(.el-input-number){max-width:100%}.component-inspector :deep(.el-button){font-size:12px}.component-inspector :deep(.el-tabs__nav){width:100%}.component-inspector :deep(.el-tabs__item){flex:1;padding:0 12px}.component-inspector :deep(.el-tabs__nav-wrap:after){height:1px;background:#e6ebf3}.inspector-section-title{font-size:14px;font-weight:650;color:#182230;margin:2px 0 20px}.inline-switch{display:flex;align-items:center;justify-content:space-between}.inline-switch :deep(.el-form-item__label){margin-bottom:0!important;padding:0!important}.inline-switch :deep(.el-form-item__content){flex:none}.title-align-control{display:flex;width:100%}.title-align-control :deep(.el-radio-button){flex:1}.title-align-control :deep(.el-radio-button__inner){width:100%;padding:9px 8px}.component-inspector :deep(.format-editor),.component-inspector :deep(.metric-style-editor){padding-top:20px;border-top:1px solid #e6ebf3}.component-inspector :deep(.heading){font-size:13px}.component-inspector :deep(.hint){font-size:12px;line-height:1.8}</style>
