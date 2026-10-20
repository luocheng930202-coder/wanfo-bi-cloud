<template>
  <div class="property-panel">
    <el-tabs v-model="tab" class="panel-tabs">
      <el-tab-pane label="数据" name="data">
        <el-form label-position="top" :disabled="disabled">
          <el-form-item label="图表名称" required><el-input v-model="definition.name" maxlength="200"/></el-form-item>
          <el-form-item label="图表类型">
            <ChartTypePicker v-model="definition.type" :disabled="disabled" @change="emit('type-changed')"/>
          </el-form-item>
          <el-form-item label="数据集" required><el-select v-model="definition.query.datasetId" filterable style="width:100%" @change="emit('dataset-changed', definition.query.datasetId)"><el-option v-for="d in datasets" :key="d.id" :value="d.id" :label="d.name"/></el-select></el-form-item>
          <template v-if="definition.type==='pivot'">
            <el-form-item label="行维度（1～3）"><el-select v-model="pivotRows" multiple filterable collapse-tags collapse-tags-tooltip :max-collapse-tags="2" style="width:100%" @change="emit('pivot-fields')"><el-option v-for="f in dimensionFields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)"/></el-select></el-form-item>
            <el-form-item label="列维度（1～2）"><el-select v-model="pivotCols" multiple filterable collapse-tags style="width:100%" @change="emit('pivot-fields')"><el-option v-for="f in dimensionFields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)" :disabled="pivotRows.includes(f.name)"/></el-select></el-form-item>
          </template>
          <el-form-item v-else label="维度">
            <el-select :model-value="definition.query.dimensions[0]?.field" filterable :clearable="allowsClearDimension(definition.type)" style="width:100%" @change="v => emit('set-dimension', v || '')"><el-option v-for="f in fields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)"/></el-select>
            <small v-if="definition.type==='map'" class="hint">地区维度取值须与省级名称一致（如 广东省、北京市、内蒙古自治区）。</small>
          </el-form-item>
          <div class="section-head"><span>度量</span><el-button v-if="definition.type==='table'" link @click="emit('raw-table')">原始字段</el-button><el-button v-if="definition.type==='scatter' && definition.query.metrics.length<2" link @click="emit('ensure-scatter')">补齐散点轴</el-button><el-button link :disabled="metricAddDisabled(definition.type, definition.query.metrics.length)" @click="emit('add-metric')">添加</el-button></div>
          <div v-for="(metric,index) in definition.query.metrics" :key="index" class="metric-row">
            <el-select v-model="metric.field" filterable><el-option v-for="f in fields" :key="f.name" :value="f.name" :label="fieldLabel(f.name)"/></el-select>
            <el-select v-model="metric.agg"><el-option v-for="(label,key) in aggregations" :key="key" :value="key" :label="label"/></el-select>
            <el-button link type="danger" @click="emit('remove-metric', index)">移除</el-button>
          </div>
          <el-divider content-position="left">筛选</el-divider>
          <FilterTreeEditor v-if="definition.query.filterTree" v-model="definition.query.filterTree" :fields="fields" :disabled="disabled" @remove="definition.query.filterTree=undefined"/>
          <el-button v-else size="small" @click="definition.query.filterTree={logic:'AND',children:[{field:'',op:'EQ',value:''}]}">添加筛选</el-button>
          <el-form-item label="排序"><el-select :model-value="definition.query.orderBys?.[0]?.field" clearable style="width:100%" @change="v => emit('set-sort', v || '')"><el-option v-for="(label,key) in labels" :key="key" :value="key" :label="label"/></el-select></el-form-item>
          <el-form-item label="最多行数"><el-input-number v-model="definition.query.limit" :min="1" :max="1000" :precision="0" style="width:100%"/></el-form-item>
          <el-button type="primary" :loading="previewing" :disabled="!canPreview" style="width:100%" @click="emit('preview')">更新数据</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="样式" name="style">
        <el-form label-position="top" :disabled="disabled">
          <el-form-item label="显示图例"><el-switch v-model="styleModel.showLegend"/></el-form-item>
          <el-form-item label="图例位置"><el-select v-model="styleModel.legendPosition" style="width:100%"><el-option value="top" label="顶部"/><el-option value="bottom" label="底部"/></el-select></el-form-item>
          <el-form-item label="显示标题"><el-switch v-model="styleModel.showTitle"/></el-form-item>
          <el-form-item v-if="styleModel.showTitle" label="标题文本"><el-input v-model="styleModel.title" maxlength="120" placeholder="默认使用图表名称"/></el-form-item>
          <el-form-item label="配色"><el-select v-model="styleModel.palette" style="width:100%"><el-option value="business" label="商务"/><el-option value="ocean" label="海洋"/><el-option value="warm" label="暖色"/></el-select></el-form-item>
          <el-form-item label="轴标签旋转"><el-input-number v-model="styleModel.axisLabelRotate" :min="0" :max="90" :precision="0" placeholder="自动"/></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="高级" name="advanced">
        <el-form label-position="top" :disabled="disabled">
          <el-form-item label="空值处理"><el-select v-model="advancedModel.nullMode" style="width:100%"><el-option value="keep" label="保持为空"/><el-option value="zero" label="置为 0"/><el-option value="hide" label="隐藏类目（饼图等）"/></el-select></el-form-item>
          <el-form-item label="折线平滑"><el-switch v-model="advancedModel.smoothLine"/></el-form-item>
          <template v-if="definition.type==='map'">
            <el-form-item label="地图范围">
              <el-select v-model="advancedModel.mapScope" style="width:100%"><el-option value="china-provinces" label="中国省级行政区"/></el-select>
            </el-form-item>
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
import type { ChartDefinition, Field } from '@/api/bi/chart'
import type { DatasetSummary } from '@/api/bi/dataset'
import FilterTreeEditor from './FilterTreeEditor.vue'
import ChartTypePicker from './ChartTypePicker.vue'
import { allowsClearDimension, metricAddDisabled, supportsDrill } from '@/utils/biChartTypes'
import { defaultChartAdvanced, defaultChartStyle, ensureChartPresentation } from '@/utils/biChartPresentation'
const props = defineProps<{ definition: ChartDefinition; fields: Field[]; datasets: DatasetSummary[]; labels: Record<string,string>; disabled?: boolean; previewing?: boolean; canPreview?: boolean; fieldLabel: (n: string) => string }>()
const emit = defineEmits<{ (e:'type-changed'):void; (e:'dataset-changed', id:number):void; (e:'set-dimension', field:string):void; (e:'add-metric'):void; (e:'remove-metric', index:number):void; (e:'set-sort', field:string):void; (e:'raw-table'):void; (e:'ensure-scatter'):void; (e:'pivot-fields'):void; (e:'preview'):void }>()
const tab = ref('data')
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
.property-panel{height:100%;background:#fff;border-left:1px solid #e7ebf2}
.panel-tabs :deep(.el-tabs__header){margin:0;padding:0 12px}
.panel-tabs :deep(.el-tabs__content){padding:12px;max-height:calc(100vh - 220px);overflow:auto}
.section-head{display:flex;align-items:center;justify-content:space-between;margin:8px 0}
.metric-row{display:flex;flex-direction:column;gap:6px;margin-bottom:8px}
.hint{display:block;margin-top:6px;color:#909399;font-size:12px;line-height:1.4}
</style>
