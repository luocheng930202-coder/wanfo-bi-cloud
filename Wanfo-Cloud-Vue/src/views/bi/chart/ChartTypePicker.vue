<template>
  <div class="chart-type-picker" :class="{ expanded, disabled }">
    <button type="button" class="chart-type-current" :disabled="disabled" :aria-expanded="expanded" @click="toggle">
      <span class="chart-type-current__icon"><ChartTypeIcon :type="modelValue"/></span>
      <span class="chart-type-current__text">
        <span class="chart-type-current__label">{{ label(modelValue) }}</span>
        <span class="chart-type-current__hint">{{ expanded ? '点击收起类型列表' : '点击展开选择图表类型' }}</span>
      </span>
      <el-icon class="chart-type-current__chevron" :class="{ rotated: expanded }"><ArrowDown/></el-icon>
    </button>
    <div v-show="expanded" class="chart-type-panel">
      <section v-for="group in groups" :key="group.title" class="chart-type-group">
        <h4 class="chart-type-group__title">{{ group.title }}</h4>
        <div class="chart-type-grid">
          <button
            v-for="t in group.types"
            :key="t"
            type="button"
            class="chart-type-card"
            :class="{ active: modelValue === t }"
            :disabled="disabled"
            :aria-pressed="modelValue === t"
            @click="pick(t)"
          >
            <span class="chart-type-card__icon"><ChartTypeIcon :type="t"/></span>
            <span class="chart-type-card__label">{{ label(t) }}</span>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import ChartTypeIcon from './ChartTypeIcon.vue'
import { CHART_TYPE_GROUPS, CHART_TYPE_LABELS, type ChartType, isChartType } from '@/utils/biChartTypes'

const props = defineProps<{ modelValue: ChartType; disabled?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: ChartType): void; (e: 'change'): void }>()

const expanded = ref(true)
const groups = CHART_TYPE_GROUPS

function label(type: ChartType | string) {
  return isChartType(type) ? CHART_TYPE_LABELS[type] : String(type)
}

function toggle() {
  if (props.disabled) return
  expanded.value = !expanded.value
}

function pick(type: ChartType) {
  if (props.disabled || props.modelValue === type) {
    expanded.value = false
    return
  }
  emit('update:modelValue', type)
  emit('change')
  expanded.value = false
}
</script>
<style scoped>
.chart-type-picker{border:1px solid #e0e6ef;border-radius:10px;background:#f8fafc;overflow:hidden}
.chart-type-picker.disabled{opacity:.65;pointer-events:none}
.chart-type-current{display:flex;align-items:center;gap:10px;width:100%;padding:10px 12px;border:0;background:#fff;cursor:pointer;text-align:left}
.chart-type-current:disabled{cursor:not-allowed}
.chart-type-current__icon{flex:0 0 56px;height:36px;padding:4px 6px;border-radius:8px;background:#eef4ff}
.chart-type-current__text{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.chart-type-current__label{font-weight:600;color:#263044;font-size:14px}
.chart-type-current__hint{font-size:12px;color:#8993a5}
.chart-type-current__chevron{color:#8993a5;transition:transform .2s ease}
.chart-type-current__chevron.rotated{transform:rotate(180deg)}
.chart-type-panel{padding:10px 12px 12px;border-top:1px solid #e7ebf2;max-height:min(420px,50vh);overflow:auto}
.chart-type-group{margin-bottom:12px}
.chart-type-group:last-child{margin-bottom:0}
.chart-type-group__title{margin:0 0 8px;font-size:12px;font-weight:600;color:#526078}
.chart-type-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
@media (min-width:420px){.chart-type-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}
.chart-type-card{display:flex;flex-direction:column;align-items:center;gap:6px;padding:8px 6px;border:1px solid #e0e6ef;border-radius:10px;background:#fff;cursor:pointer;transition:border-color .15s,box-shadow .15s,background .15s}
.chart-type-card:hover:not(:disabled){border-color:#b8ccf5;box-shadow:0 2px 8px rgba(51,112,255,.08)}
.chart-type-card.active{border-color:#3370ff;background:#f0f6ff;box-shadow:0 0 0 1px #3370ff inset}
.chart-type-card:disabled{cursor:not-allowed;opacity:.55}
.chart-type-card__icon{width:100%;height:34px;padding:2px 8px}
.chart-type-card__label{font-size:12px;line-height:1.3;color:#263044;text-align:center}
</style>
