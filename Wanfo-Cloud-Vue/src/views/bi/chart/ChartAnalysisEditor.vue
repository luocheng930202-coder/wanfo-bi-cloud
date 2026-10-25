<template>
  <section v-if="['funnel','radar','scatter','line','area'].includes(definition.type)" aria-label="图表专属分析">
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
    <template v-if="['line','area'].includes(definition.type)">
      <el-form-item label="趋势拟合"><el-select :model-value="analysis.trendMethod" clearable placeholder="关闭" @update:model-value="setTrendMethod"><el-option value="linear" label="线性回归"/><el-option value="moving-average" label="3 点移动平均"/><el-option value="seasonal" label="季节性朴素预测"/></el-select></el-form-item>
      <el-form-item v-if="analysis.trendMethod==='linear'" label="线性外推周期"><el-input-number v-model="analysis.forecastPeriods" :min="0" :max="12" :precision="0"/><small class="hint">0 表示不外推；按结果顺序把类别索引作为等间距时间。</small></el-form-item>
      <template v-if="analysis.trendMethod==='seasonal'">
        <el-form-item label="季节长度（周期数）"><el-input-number v-model="analysis.seasonLength" :min="2" :max="366" :precision="0"/></el-form-item>
        <el-form-item label="预测周期"><el-input-number v-model="analysis.forecastPeriods" :min="1" :max="12" :precision="0"/></el-form-item>
        <el-form-item label="经验预测区间"><el-select v-model="analysis.forecastConfidence"><el-option value="80" label="80%"/><el-option value="90" label="90%"/><el-option value="95" label="95%"/></el-select></el-form-item>
        <el-form-item label="业务指标口径说明"><el-input v-model="analysis.forecastMetricDefinition" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="例如：按下单日期自然月汇总已支付订单含税金额，退款按发生月冲减"/></el-form-item>
        <small class="hint">需使用一个时间粒度和一个聚合指标，并明确缺失周期补空/补零。至少需 2 个完整季节周期且无空值；预测重复最近一季的周期值，区间按历史季节朴素回测绝对误差分位数计算。该经验区间不代表因果预测或统计学保证，请人工核对指标定义、季节长度及业务口径。</small>
      </template>
      <el-form-item label="异常点检测"><el-switch v-model="analysis.anomalyDetection"/></el-form-item>
      <el-form-item v-if="analysis.anomalyDetection" label="异常检测算法"><el-select v-model="analysis.anomalyMethod"><el-option value="stddev" label="均值与标准差"/><el-option value="mad" label="中位数与 MAD（稳健）"/></el-select></el-form-item>
      <el-form-item v-if="analysis.anomalyDetection" :label="analysis.anomalyMethod==='mad'?'MAD 阈值':'标准差阈值'"><el-input-number v-model="analysis.anomalySensitivity" :min="1" :max="4" :step="0.5"/></el-form-item>
      <small v-if="analysis.trendMethod!=='seasonal'" class="hint">趋势只拟合当前已查询序列，外推按类别等间距处理。MAD 使用 1.4826×中位绝对偏差缩放。</small>
    </template>
  </section>
</template>
<script setup lang="ts">
import {computed,watch} from 'vue'
import type {ChartDefinition,ChartAnalysis} from '@/api/bi/chart'
import {metricResultKey} from '@/utils/biEchartsPreview'
const props=defineProps<{definition:ChartDefinition;labels:Record<string,string>}>()
const analysis=computed<ChartAnalysis>(()=>{props.definition.advanced??={};return props.definition.advanced.analysis??={}})
function setTrendMethod(value:'linear'|'moving-average'|'seasonal'|undefined){analysis.value.trendMethod=value;if(value==='seasonal'){analysis.value.seasonLength??=12;analysis.value.forecastPeriods??=3;analysis.value.forecastConfidence??='95'}}
function ensureAxes(){if(!analysis.value.radarNormalize)return;analysis.value.radarAxes=props.definition.query.metrics.map(m=>{const key=metricResultKey(m);return analysis.value.radarAxes?.find(a=>a.key===key)||{key,min:0,max:100}})}
watch(()=>props.definition.query.metrics.map(metricResultKey).join('|'),ensureAxes)
</script>
<style scoped>.hint{display:block;color:var(--bi-muted,#667085);font-size:12px;line-height:1.6}.axis-settings{padding:10px;border:1px solid var(--bi-border,#e6ebf3);border-radius:6px;margin-bottom:12px}.axis-settings strong{display:block;margin-bottom:8px}</style>
