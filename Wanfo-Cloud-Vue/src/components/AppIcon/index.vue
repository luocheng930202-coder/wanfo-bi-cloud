<template>
  <span class="app-icon-wrap" :class="className" :style="wrapStyle">
    <el-icon v-if="parsed.kind === 'ep'" :style="iconStyle">
      <component :is="parsed.name" />
    </el-icon>
    <svg-icon v-else-if="parsed.kind === 'svg'" :icon-class="parsed.name" :color="color" />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { parseIconKey } from '@/utils/iconRegistry'

const props = withDefaults(defineProps<{
  iconClass?: string
  className?: string
  color?: string
  size?: number | string
}>(), {
  iconClass: '',
  className: '',
  color: '',
  size: ''
})

const parsed = computed(() => parseIconKey(props.iconClass))
const iconStyle = computed(() => {
  if (!props.size) return undefined
  const n = typeof props.size === 'number' ? `${props.size}px` : props.size
  return { fontSize: n }
})
const wrapStyle = computed(() => {
  if (!props.size || parsed.value.kind !== 'svg') return undefined
  const n = typeof props.size === 'number' ? `${props.size}px` : props.size
  return { width: n, height: n, fontSize: n }
})
</script>

<style scoped lang="scss">
.app-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  vertical-align: -0.15em;
  line-height: 1;
  :deep(.svg-icon) {
    width: 1em;
    height: 1em;
  }
}
</style>
