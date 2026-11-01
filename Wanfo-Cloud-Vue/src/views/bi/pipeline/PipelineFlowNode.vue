<template>
  <div class="flow-node" :class="['kind-' + kindClass, { selected: selected, readonly }]">
    <Handle v-if="showTarget" id="in" type="target" :position="Position.Left" :connectable="!readonly" class="port port-in" />
    <span class="icon"><el-icon><component :is="icon" /></el-icon></span>
    <div class="meta">
      <strong>{{ data.label }}</strong>
      <small>{{ data.typeLabel }}</small>
      <em v-if="data.summary">{{ data.summary }}</em>
    </div>
    <button v-if="!readonly" class="remove" type="button" title="删除节点" @click.stop="data.onRemove?.(data.key)">×</button>
    <Handle v-if="showSource" id="out" type="source" :position="Position.Right" :connectable="!readonly" class="port port-out" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { Coin, Connection, Filter, Finished, Operation, Share } from '@element-plus/icons-vue'

const props = defineProps<{
  id: string
  selected?: boolean
  data: {
    key: string
    type: string
    label: string
    typeLabel: string
    summary?: string
    readonly?: boolean
    onRemove?: (key: string) => void
  }
}>()

const readonly = computed(() => Boolean(props.data.readonly))
const kindClass = computed(() => String(props.data.type || '').toLowerCase())
const showTarget = computed(() => props.data.type !== 'SOURCE_DATASET')
const showSource = computed(() => props.data.type !== 'WAREHOUSE_PUBLISH')
const icon = computed(() => {
  switch (props.data.type) {
    case 'SOURCE_DATASET': return Coin
    case 'JOIN': return Share
    case 'FILTER': return Filter
    case 'TRANSFORM': return Operation
    case 'QUALITY_GATE': return Finished
    case 'WAREHOUSE_PUBLISH': return Connection
    default: return Connection
  }
})
</script>

<style scoped>
.flow-node {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 210px;
  max-width: 260px;
  padding: 12px 14px 12px 12px;
  border: 1px solid #d7e0ec;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  box-shadow: 0 8px 22px rgba(28, 45, 78, 0.08);
  color: #243247;
}
.flow-node.selected {
  border-color: #3b6ef5;
  box-shadow: 0 0 0 3px rgba(59, 110, 245, 0.16), 0 10px 24px rgba(28, 45, 78, 0.12);
}
.flow-node.readonly { opacity: 0.92; }
.icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #eef3ff;
  color: #3b6ef5;
  font-size: 17px;
  flex: none;
}
.kind-join .icon { background: #e8f8f1; color: #1b9a6d; }
.kind-filter .icon, .kind-transform .icon { background: #f3edff; color: #7a4fd1; }
.kind-quality_gate .icon { background: #fff4e5; color: #c67a14; }
.kind-warehouse_publish .icon { background: #e7f7f2; color: #17866f; }
.meta { min-width: 0; display: grid; gap: 2px; }
.meta strong {
  font-size: 13px;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta small { color: #8290a5; font-size: 11px; }
.meta em {
  color: #5a6b84;
  font-size: 11px;
  font-style: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.remove {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: #9aa7ba;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  padding: 2px 4px;
}
.remove:hover { color: #d14343; }
.port {
  width: 10px !important;
  height: 10px !important;
  border: 2px solid #fff !important;
  background: #6f86a8 !important;
}
.port-out { background: #3b6ef5 !important; }
.port-in { background: #1b9a6d !important; }
</style>
