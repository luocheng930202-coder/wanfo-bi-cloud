<template>
  <div class="resource-thumbnail" role="img" :aria-label="`${name}的封面`">
    <img v-if="url" :src="url" alt="" />
    <div v-else class="thumbnail-state"><el-icon><Picture /></el-icon><span>{{ fallback }}</span></div>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Picture } from '@element-plus/icons-vue'
import { fetchResourceCover } from '@/api/bi/resourceCover'
const props = defineProps<{ kind: 'chart' | 'dashboard'; id: number; version: number; name: string; coverUrl?: string; actions?: string[]; published?: boolean; refreshKey?: number; marker?: string }>()
const url = ref('')
const fallback = computed(() => props.marker === 'screen' ? '大屏' : props.kind === 'chart' ? '图表' : '看板')
let objectUrl = ''
let generation = 0
async function load() {
  const current = ++generation
  url.value = ''
  if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = '' }
  if (!props.coverUrl) return
  try {
    const blob = await fetchResourceCover(props.kind, props.id, props.version)
    if (current !== generation || !(blob instanceof Blob) || blob.size < 32 || blob.type.includes('json')) return
    objectUrl = URL.createObjectURL(blob)
    url.value = objectUrl
  } catch { if (current === generation) url.value = '' }
}
watch(() => [props.kind, props.id, props.version, props.coverUrl, props.refreshKey], () => { void load() }, { immediate: true })
onBeforeUnmount(() => { generation++; if (objectUrl) URL.revokeObjectURL(objectUrl) })
</script>
<style scoped>
.resource-thumbnail{position:relative;aspect-ratio:16/10;overflow:hidden;border-radius:7px;background:#edf1f7;display:flex;align-items:center;justify-content:center}
.resource-thumbnail img{width:100%;height:100%;object-fit:contain}
.thumbnail-state{display:flex;flex-direction:column;align-items:center;gap:8px;color:#8693a7;font-size:12px}
.thumbnail-state>.el-icon{font-size:25px}
</style>
