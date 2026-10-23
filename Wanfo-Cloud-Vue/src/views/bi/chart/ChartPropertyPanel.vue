<template>
  <div class="property-panel">
    <el-tabs v-model="tab" class="panel-tabs">
      <el-tab-pane label="数据" name="data">
        <el-form label-position="top" :disabled="disabled">
          <el-alert :title="capabilities.hint" type="info" :closable="false" class="mb8"/>
          <el-form-item label="图表名称" required><el-input v-model="definition.name" maxlength="200"/></el-form-item>
          <el-form-item label="图表类型">
            <ChartTypePicker v-model="definition.type" :disabled="disabled" @change="emit('type-changed')"/>
          </el-form-item>
          <el-form-item label="数据集" required><el-select v-model="definition.query.datasetId" filterable style="width:100%" @change="emit('dataset-changed', definition.query.datasetId)"><el-option v-for="d in datasets" :key="d.id" :value="d.id" :label="d.name"/></el-select></el-form-item>
          <template v-if="definition.type==='pivot'">
            <el-form-item label="行维度（1～3）"><el-select v-model="pivotRows" multiple filterable collapse-tags collapse-tags-tooltip :max-collapse-tags="2" style="width:100%" @change="emit('pivot-fields')"><el-option v-for="f in dimensionFields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)"/></el-select></el-form-item>
            <el-form-item label="列维度（1～2）"><el-select v-model="pivotCols" multiple filterable collapse-tags style="width:100%" @change="emit('pivot-fields')"><el-option v-for="f in dimensionFields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)" :disabled="pivotRows.includes(f.name)"/></el-select></el-form-item>
          </template>
          <el-form-item v-else-if="capabilities.dimension" :label="capabilities.dimension">
            <el-select :model-value="definition.query.dimensions[0]?.field" filterable :clearable="allowsClearDimension(definition.type)" style="width:100%" @change="v => emit('set-dimension', v || '')"><el-option v-for="f in fields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)"/></el-select>
            <small v-if="definition.type==='map'" class="hint">地区值须与当前地图层级一致；可在高级页绑定省市县字段，推荐使用行政区编码。</small>
          </el-form-item>
          <el-form-item v-if="supportsSeriesDimension(definition.type)" label="系列维度（可选）">
            <el-select :model-value="definition.query.dimensions[1]?.field" clearable filterable @change="setSeries"><el-option v-for="f in dimensionFields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)" :disabled="f.name===definition.query.dimensions[0]?.field"/></el-select>
            <small class="hint">例如按月份展示销售额，再按渠道拆分系列。缺失组合保持为空；时间补齐、同比环比请先移除系列维度。</small>
          </el-form-item>
          <ChartTimeGrainEditor :definition="definition" :fields="fields"/>
          <div class="section-head"><span>{{ capabilities.metric }}</span><el-button v-if="definition.type==='table'" link @click="emit('raw-table')">原始字段</el-button><el-button v-if="definition.type==='scatter' && definition.query.metrics.length<2" link @click="emit('ensure-scatter')">补齐散点轴</el-button><el-button link :disabled="metricAddDisabled(definition.type, definition.query.metrics.length)" @click="emit('add-metric')">添加</el-button></div>
          <div v-for="(metric,index) in definition.query.metrics" :key="index" class="metric-row">
            <span v-if="definition.type==='scatter'" class="metric-axis">{{ index===0 ? 'X 轴' : 'Y 轴' }}</span><el-select v-model="metric.field" filterable @change="normalizeAggregation(index)"><el-option v-for="f in fields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)"/></el-select>
            <el-select v-model="metric.agg"><el-option v-for="key in metricAggregations(index)" :key="key" :value="key" :label="aggregations[key]"/></el-select>
            <el-button link type="danger" @click="emit('remove-metric', index)">移除</el-button>
          </div>
          <el-divider content-position="left">筛选</el-divider>
          <FilterTreeEditor v-if="definition.query.filterTree" v-model="definition.query.filterTree" :fields="fields" :disabled="disabled" @remove="definition.query.filterTree=undefined"/>
          <el-button v-else size="small" @click="definition.query.filterTree={logic:'AND',children:[{field:'',op:'EQ',value:''}]}">添加筛选</el-button>
          <el-form-item label="排序"><el-select :model-value="definition.query.orderBys?.[0]?.field" clearable style="width:100%" @change="v => emit('set-sort', v || '')"><el-option v-for="(label,key) in labels" :key="key" :value="key" :label="label"/></el-select></el-form-item>
          <el-form-item v-if="definition.query.orderBys?.length" label="排序方向"><el-select v-model="definition.query.orderBys[0].order" style="width:100%"><el-option value="asc" label="升序"/><el-option value="desc" label="降序"/></el-select></el-form-item>
          <small class="hint">清除排序时，时间汇总默认按周期升序；其他查询保留原默认排序。</small>
          <el-form-item label="最多行数"><el-input-number v-model="definition.query.limit" :min="1" :max="1000" :precision="0" style="width:100%"/></el-form-item>
          <el-button type="primary" :loading="previewing" :disabled="!canPreview" style="width:100%" @click="emit('preview')">更新数据</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="样式" name="style">
        <el-form label-position="top" :disabled="disabled">
          <ChartMetricStyleEditor :definition="definition" :labels="labels"/>
          <section v-if="presets.length" class="style-presets" aria-label="图表样式预设">
            <h4>推荐样式</h4><button v-for="preset in presets" :key="preset.id" type="button" :disabled="disabled" :title="preset.description" @click="applyPreset(preset)"><strong>{{ preset.name }}</strong><small>{{ preset.description }}</small></button>
            <el-button link :disabled="disabled" @click="resetShape">恢复图形默认</el-button>
          </section>
          <el-form-item v-if="capabilities.legend" label="显示图例"><el-switch v-model="styleModel.showLegend"/></el-form-item>
          <el-form-item v-if="capabilities.legend && styleModel.showLegend" label="图例位置"><el-select v-model="styleModel.legendPosition" style="width:100%"><el-option value="top" label="顶部"/><el-option value="bottom" label="底部"/></el-select></el-form-item>
          <el-form-item label="显示标题"><el-switch v-model="styleModel.showTitle"/></el-form-item>
          <el-form-item v-if="styleModel.showTitle" label="标题文本"><el-input v-model="styleModel.title" maxlength="120" placeholder="默认使用图表名称"/></el-form-item>
          <el-form-item v-if="definition.type!=='table' && definition.type!=='pivot'" label="配色"><el-select v-model="styleModel.palette" style="width:100%"><el-option value="business" label="商务"/><el-option value="ocean" label="海洋"/><el-option value="warm" label="暖色"/></el-select></el-form-item>
          <el-form-item v-if="capabilities.axis && definition.type!=='scatter'" label="轴标签旋转"><el-input-number v-model="styleModel.axisLabelRotate" :min="0" :max="90" :precision="0" placeholder="自动"/></el-form-item>
          <el-divider v-if="presets.length" content-position="left">图形细节</el-divider>
          <el-form-item v-if="barType || lineType || pieType || ['funnel','scatter'].includes(definition.type)" label="显示数据标签"><el-switch :model-value="styleModel.showLabels??(pieType || definition.type==='funnel')" @update:model-value="v=>styleModel.showLabels=!!v"/></el-form-item>
          <ChartSpecializedStyleEditor :definition="definition"/>
          <ChartTableStyleEditor :definition="definition" :labels="labels"/>
          <ChartPivotStyleEditor :definition="definition"/>
          <template v-if="barType">
            <el-form-item label="柱条最大宽度"><el-input-number v-model="styleModel.barMaxWidth" :min="4" :max="120" :precision="0" placeholder="默认 44"/></el-form-item>
            <el-form-item v-if="!['stack','percent'].includes(definition.type)" label="柱条圆角"><el-input-number v-model="styleModel.barRadius" :min="0" :max="30" :precision="0" placeholder="默认 5"/></el-form-item>
          </template>
          <template v-if="lineType">
            <el-form-item label="线宽"><el-input-number v-model="styleModel.lineWidth" :min="1" :max="10" :precision="0" placeholder="默认 3"/></el-form-item>
            <el-form-item label="显示数据点"><el-switch :model-value="styleModel.showPoints!==false" @update:model-value="v=>styleModel.showPoints=!!v"/></el-form-item>
            <el-form-item v-if="styleModel.showPoints!==false" label="数据点大小"><el-input-number v-model="styleModel.symbolSize" :min="2" :max="20" :precision="0" placeholder="默认 6"/></el-form-item>
            <el-form-item v-if="definition.type==='area'" label="面积透明度"><el-slider :model-value="styleModel.areaOpacity??0.18" :min="0" :max="1" :step="0.01" @update:model-value="v=>styleModel.areaOpacity=Number(v)"/></el-form-item>
          </template>
          <template v-if="pieType">
            <el-form-item label="外半径（%）"><el-input-number v-model="styleModel.pieOuterRadius" :min="Math.max(20,(definition.type==='ring'?(styleModel.pieInnerRadius??52):0)+1)" :max="90" :precision="0" placeholder="默认 70"/></el-form-item>
            <el-form-item v-if="definition.type==='ring'" label="内半径（%）"><el-input-number v-model="styleModel.pieInnerRadius" :min="0" :max="Math.min(80,(styleModel.pieOuterRadius??72)-1)" :precision="0" placeholder="默认 52"/></el-form-item>
            <el-form-item v-if="definition.type==='ring'" label="中心显示合计"><el-switch v-model="styleModel.ringShowTotal"/></el-form-item>
            <el-form-item v-if="styleModel.showLabels!==false" label="标签位置"><el-select :model-value="styleModel.pieLabelPosition??'outside'" @update:model-value="v=>styleModel.pieLabelPosition=v"><el-option label="外侧名称" value="outside"/><el-option label="内部占比" value="inside"/></el-select></el-form-item>
            <el-form-item label="起始角度"><el-input-number v-model="styleModel.startAngle" :min="0" :max="360" :precision="0" placeholder="默认 90"/></el-form-item>
          </template>
          <template v-if="definition.type==='gauge'">
            <el-form-item label="仪表样式"><el-select :model-value="styleModel.gaugeStyle??'pointer'" @update:model-value="v=>styleModel.gaugeStyle=v"><el-option label="经典指针" value="pointer"/><el-option label="进度弧" value="progress"/></el-select></el-form-item>
            <el-form-item label="最小值"><el-input-number v-model="styleModel.gaugeMin" :min="-1e12" :max="1e12" placeholder="默认 0"/></el-form-item>
            <el-form-item label="最大值"><el-input-number v-model="styleModel.gaugeMax" :min="-1e12" :max="1e12" placeholder="留空为自动范围"/></el-form-item>
            <small class="hint">上下限使用指标原始单位。固定范围外的数值仍显示原值，弧线按范围绘制。</small>
          </template>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="高级" name="advanced">
        <el-form label-position="top" :disabled="disabled">
          <ChartReferenceLineEditor v-if="['bar','hbar','line','area','stack','combo'].includes(definition.type)" :definition="definition" :labels="labels"/>
          <el-form-item label="空值处理"><el-select v-model="advancedModel.nullMode" style="width:100%"><el-option value="keep" label="保持为空"/><el-option value="zero" label="置为 0"/><el-option value="hide" label="隐藏类目（饼图等）"/></el-select></el-form-item>
          <el-form-item v-if="capabilities.smooth" label="折线平滑"><el-switch v-model="advancedModel.smoothLine"/></el-form-item>
          <template v-if="definition.type==='map'">
            <ChartMapSettings :definition="definition" :fields="fields"/>
          </template>
          <el-form-item v-if="supportsDrill(definition.type)" label="下钻维度（2～5）">
            <el-select v-model="drillFields" multiple filterable collapse-tags :max-collapse-tags="3" style="width:100%">
              <el-option v-for="f in dimensionFields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)"/>
            </el-select>
            <small class="hint">第一项须与「数据」页维度一致；启用下钻后该图不能作为看板联动来源。</small>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { BAR_TYPES,LINE_TYPES,PIE_TYPES,chartStylePresets,applyChartStylePreset,type ChartStylePreset } from '@/utils/biChartStylePresets'
import { CHART_CAPABILITIES, allowedAggregations } from '@/utils/biChartCapabilities'
import type { ChartDefinition, Field } from '@/api/bi/chart'
import type { DatasetSummary } from '@/api/bi/dataset'
import FilterTreeEditor from './FilterTreeEditor.vue'
import ChartTypePicker from './ChartTypePicker.vue'
import ChartMetricStyleEditor from './ChartMetricStyleEditor.vue'
import ChartReferenceLineEditor from './ChartReferenceLineEditor.vue'
import ChartPivotStyleEditor from './ChartPivotStyleEditor.vue'
import ChartTableStyleEditor from './ChartTableStyleEditor.vue'
import ChartSpecializedStyleEditor from './ChartSpecializedStyleEditor.vue'
import ChartMapSettings from './ChartMapSettings.vue'
import ChartTimeGrainEditor from './ChartTimeGrainEditor.vue'
import {metricResultKey} from '@/utils/biEchartsPreview'
import { allowsClearDimension, metricAddDisabled, supportsDrill, supportsSeriesDimension } from '@/utils/biChartTypes'
import { defaultChartAdvanced, defaultChartStyle, ensureChartPresentation,retainChartMetricBindings } from '@/utils/biChartPresentation'
const props = defineProps<{ definition: ChartDefinition; fields: Field[]; datasets: DatasetSummary[]; labels: Record<string,string>; disabled?: boolean; previewing?: boolean; canPreview?: boolean; fieldLabel: (n: string) => string }>()
const emit = defineEmits<{ (e:'type-changed'):void; (e:'dataset-changed', id:number):void; (e:'set-dimension', field:string):void; (e:'add-metric'):void; (e:'remove-metric', index:number):void; (e:'set-sort', field:string):void; (e:'raw-table'):void; (e:'ensure-scatter'):void; (e:'pivot-fields'):void; (e:'preview'):void }>()
function setSeries(field:string){const q=props.definition.query;if(!q.dimensions[0])return;q.dimensions=q.dimensions.slice(0,1);if(field && field!==q.dimensions[0].field)q.dimensions.push({field,alias:'series'});q.orderBys=[]}
const tab = ref('data')
watch(()=>props.definition.query.metrics.map(m=>({...m})),(current,previous)=>retainChartMetricBindings(props.definition.style,current,previous))
const barType=computed(()=>BAR_TYPES.includes(props.definition.type))
const lineType=computed(()=>LINE_TYPES.includes(props.definition.type))
const pieType=computed(()=>PIE_TYPES.includes(props.definition.type))
const presets=computed(()=>chartStylePresets(props.definition.type))
function applyPreset(preset:ChartStylePreset){ensureChartPresentation(props.definition);applyChartStylePreset(props.definition.style!,preset)}
function resetShape(){ensureChartPresentation(props.definition);applyChartStylePreset(props.definition.style!)}
const capabilities=computed(()=>CHART_CAPABILITIES[props.definition.type])
function metricAggregations(index:number){const field=props.fields.find(f=>f.name===props.definition.query.metrics[index]?.field);return allowedAggregations(field?.type||'',props.definition.type)}
function normalizeAggregation(index:number){const metric=props.definition.query.metrics[index];if(!metricAggregations(index).includes(metric.agg))metric.agg='COUNT'}
const aggregations: Record<string,string> = { COUNT:'计数', COUNT_DISTINCT:'去重计数', SUM:'求和', AVG:'平均值', MIN:'最小值', MAX:'最大值', NONE:'原始值' }
ensureChartPresentation(props.definition)
function numeric(field: Field) { return /(int|decimal|numeric|float|double|real|number)/i.test(field.type) }
const dimensionFields = computed(() => props.fields.filter(f => !numeric(f) || f.dimFlag === '1'))
const pivotRows = computed({
  get: () => props.definition.advanced?.pivotRowFields || [],
  set: (v: string[]) => { ensureChartPresentation(props.definition); props.definition.advanced!.pivotRowFields = v.slice(0, 3) }
})
const pivotCols = computed({
  get: () => props.definition.advanced?.pivotColFields || [],
  set: (v: string[]) => { ensureChartPresentation(props.definition); props.definition.advanced!.pivotColFields = v.filter(f => !pivotRows.value.includes(f)).slice(0, 2) }
})
const styleModel = computed(() => props.definition.style!)
const advancedModel = computed(() => props.definition.advanced!)
const drillFields = computed({
  get: () => props.definition.advanced?.drillFields?.filter(Boolean) || [],
  set: (v: string[]) => {
    ensureChartPresentation(props.definition)
    const dim = props.definition.query.dimensions[0]?.field
    const ordered = dim && v.includes(dim) ? [dim, ...v.filter(f => f !== dim)] : v
    props.definition.advanced!.drillFields = ordered.length >= 2 ? ordered.slice(0, 5) : (ordered.length ? ordered : [])
  }
})
watch(() => props.definition.type, () => { ensureChartPresentation(props.definition) })
watch(() => props.definition.query.dimensions[0]?.field, (dim) => {
  if (!dim || !props.definition.advanced?.drillFields?.length) return
  const list = props.definition.advanced.drillFields
  if (list[0] !== dim) props.definition.advanced.drillFields = [dim, ...list.filter(f => f !== dim)].slice(0, 5)
})
</script>
<style scoped>
.style-presets{margin-bottom:18px;padding:12px;background:var(--bi-soft,#f8fafc);border-radius:8px}.style-presets h4{margin:0 0 8px}.style-presets>button:not(.el-button){width:100%;text-align:left;background:var(--bi-surface,#fff);border:1px solid var(--bi-border,#dce4f2);border-radius:6px;padding:10px;margin-bottom:8px;cursor:pointer}.style-presets strong,.style-presets small{display:block}.style-presets small{margin-top:4px;line-height:1.5;color:#667085}.style-presets>button:hover{border-color:#3370ff}.property-panel{height:100%;background:var(--bi-surface,#fff);border-left:1px solid var(--bi-border,#e7ebf2)}
.panel-tabs :deep(.el-tabs__header){margin:0;padding:0 12px}
.panel-tabs :deep(.el-tabs__content){padding:12px;max-height:calc(var(--bi-content-height,100vh) - 220px);overflow:auto}
.section-head{display:flex;align-items:center;justify-content:space-between;margin:8px 0}
.metric-row{display:flex;flex-direction:column;gap:6px;margin-bottom:8px}
.hint{display:block;margin-top:6px;color:var(--bi-muted,#667085);font-size:12px;line-height:1.4}
</style>

