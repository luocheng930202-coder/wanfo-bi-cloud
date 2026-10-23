<template>
  <section v-if="['funnel','radar','scatter'].includes(definition.type)" aria-label="图表专属分析">
    <el-divider content-position="left">专属分析</el-divider>
    <template v-if="definition.type==='funnel'">
      <el-form-item label="转化率口径"><el-select v-model="analysis.funnelRate" clearable placeholder="不显示转化率"><el-option value="adjacent" label="相对上一阶段"/><el-option value="first" label="相对首阶段"/></el-select></el-form-item>
      <el-form-item label="固定阶段顺序"><el-input :model-value="analysis.funnelStages?.join('\n')||''" type="textarea" :rows="4" placeholder="每行一个阶段，须与分类值一致" @update:model-value="v=>analysis.funnelStages=String(v).split('\n').map(s=>s.trim()).filter(Boolean)"/></el-form-item>
      <el-form-item v-if="analysis.funnelStages?.length" label="缺失阶段明确补零"><el-switch v-model="analysis.funnelFillZero"/></el-form-item>
      <small class="hint">未列出的实际阶段会追加显示；未开启补零时，缺失阶段会提示。零分母显示 —，阶段增长会标记异常。</small>
    </template>
    <template v-if="definition.type==='radar'">
      <el-form-item label="按指标范围标准化至 0～100"><el-switch v-model="analysis.radarNormalize" @change="ensureAxes"/></el-form-item>
      <template v-if="analysis.radarNormalize">
        <div v-for="axis in analysis.radarAxes" :key="axis.key" class="axis-settings"><strong>{{ labels[axis.key]||axis.key }}</strong>
          <el-form-item label="原始下限"><el-input-number v-model="axis.min" :min="-1e12" :max="1e12"/></el-form-item>
          <el-form-item label="原始上限"><el-input-number v-model="axis.max" :min="-1e12" :max="1e12"/></el-form-item>
          <el-form-item label="目标值（原始单位，可选）"><el-input-number v-model="axis.target" :min="axis.min" :max="axis.max"/></el-form-item>
        </div>
        <small class="hint">得分 =（原值 − 下限）/（上限 − 下限）× 100。超出范围或缺失值会提示，不静默补零。所有指标配置目标值后显示目标轮廓。</small>
      </template>
    </template>
    <template v-if="definition.type==='scatter'">
      <el-form-item label="四象限参考线"><el-switch v-model="analysis.scatterQuadrants"/></el-form-item>
      <template v-if="analysis.scatterQuadrants"><el-form-item label="X 分界（留空取均值）"><el-input-number v-model="analysis.scatterXSplit" :min="-1e12" :max="1e12"/></el-form-item><el-form-item label="Y 分界（留空取均值）"><el-input-number v-model="analysis.scatterYSplit" :min="-1e12" :max="1e12"/></el-form-item></template>
      <el-form-item label="数据缩放"><el-switch v-model="analysis.scatterZoom"/></el-form-item>
      <el-form-item label="X 对数轴"><el-switch v-model="analysis.scatterLogX"/></el-form-item><el-form-item label="Y 对数轴"><el-switch v-model="analysis.scatterLogY"/></el-form-item>
      <small class="hint">对数轴只接受正数。在数据页添加第三个指标即可控制气泡大小，数值必须非负。</small>
    </template>
  </section>
</template>
<script setup lang="ts">
import {computed,watch} from 'vue'
import type {ChartDefinition,ChartAnalysis} from '@/api/bi/chart'
import {metricResultKey} from '@/utils/biEchartsPreview'
const props=defineProps<{definition:ChartDefinition;labels:Record<string,string>}>()
const analysis=computed<ChartAnalysis>(()=>{props.definition.advanced??={};return props.definition.advanced.analysis??={}})
function ensureAxes(){if(!analysis.value.radarNormalize)return;analysis.value.radarAxes=props.definition.query.metrics.map(m=>{const key=metricResultKey(m);return analysis.value.radarAxes?.find(a=>a.key===key)||{key,min:0,max:100}})}
watch(()=>props.definition.query.metrics.map(metricResultKey).join('|'),ensureAxes)
</script>
<style scoped>.hint{display:block;color:var(--bi-muted,#667085);font-size:12px;line-height:1.6}.axis-settings{padding:10px;border:1px solid var(--bi-border,#e6ebf3);border-radius:6px;margin-bottom:12px}.axis-settings strong{display:block;margin-bottom:8px}</style>
