<template>
  <div class="field-pool">
    <p class="pool-title">字段</p>
    <el-input v-model="keyword" size="small" clearable placeholder="搜索字段" class="mb8"/>
    <div v-if="!fields.length" class="pool-empty">选择数据集后显示可用字段</div>
    <section v-if="dimensions.length"><h4>维度</h4><button v-for="f in dimensions" :key="f.name" type="button" class="field-chip" :disabled="disabled" @click="emit('pick-dimension', f.name)">{{ label(f) }}</button></section>
    <section v-if="metrics.length"><h4>指标</h4><button v-for="f in metrics" :key="f.name" type="button" class="field-chip metric" :disabled="disabled" @click="emit('pick-metric', f.name)">{{ label(f) }}</button></section>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Field } from '@/api/bi/chart'
const props = defineProps<{ fields: Field[]; disabled?: boolean }>()
const emit = defineEmits<{ (e: 'pick-dimension', name: string): void; (e: 'pick-metric', name: string): void }>()
const keyword = ref('')
function numeric(field: Field) { return /(int|decimal|numeric|float|double|real|number)/i.test(field.type) }
const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return props.fields
  return props.fields.filter(f => f.name.toLowerCase().includes(q) || (f.showName || '').toLowerCase().includes(q))
})
const dimensions = computed(() => filtered.value.filter(f => !numeric(f) || f.dimFlag === '1'))
const metrics = computed(() => filtered.value.filter(f => numeric(f) || f.dimFlag === '0'))
function label(f: Field) { return f.showName && f.showName !== f.name ? `${f.showName}` : f.name }
</script>
<style scoped>
.field-pool{height:100%;display:flex;flex-direction:column;gap:8px;padding:12px;background:#f8fafc;border-right:1px solid #e7ebf2}
.pool-title{margin:0;font-weight:650;color:#263044}
.pool-empty{font-size:12px;color:#8993a5}
section h4{margin:10px 0 6px;font-size:12px;color:#526078}
.field-chip{display:block;width:100%;text-align:left;margin:0 0 6px;padding:8px 10px;border:1px solid #e0e6ef;border-radius:8px;background:#fff;color:#263044;cursor:pointer;font-size:13px}
.field-chip.metric{border-color:#d6e4ff;background:#f5f9ff}
.field-chip:disabled{opacity:.55;cursor:not-allowed}
</style>
