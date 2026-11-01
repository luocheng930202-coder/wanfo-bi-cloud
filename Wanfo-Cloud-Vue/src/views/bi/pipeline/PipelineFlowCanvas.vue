<template>
  <div
    class="flow-shell"
    :class="{ readonly: !editable }"
    @dragover.prevent
    @drop="onDrop"
  >
    <VueFlow
      :nodes="flowNodes"
      :edges="flowEdges"
      :node-types="(nodeTypes as any)"
      :nodes-draggable="editable"
      :nodes-connectable="editable"
      :elements-selectable="true"
      :edges-updatable="editable"
      :delete-key-code="editable ? (['Backspace', 'Delete'] as any) : null"
      :min-zoom="0.4"
      :max-zoom="1.6"
      fit-view-on-init
      class="flow-board"
      @node-click="onNodeClick"
      @pane-click="emit('select', '')"
      @connect="onConnect"
      @node-drag-stop="onNodeDragStop"
      @nodes-delete="onNodesDelete"
      @edges-delete="onEdgesDelete"
    >
      <Background :gap="18" :size="1" color="#d5deeb" />
      <Controls position="bottom-left" :show-interactive="false" />
    </VueFlow>
    <div v-if="!nodes.length" class="empty-hint">
      <strong>从左侧节点库拖入画布</strong>
      <span>从输出端口拖到输入端口完成连线</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw } from 'vue'
import {
  Position,
  VueFlow,
  type Connection,
  type Edge,
  type Node,
  type NodeDragEvent
} from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import type { PipelineEdge, PipelineNode } from '@/api/bi/pipeline'
import PipelineFlowNode from './PipelineFlowNode.vue'

const props = defineProps<{
  nodes: PipelineNode[]
  edges: PipelineEdge[]
  editable: boolean
  selectedKey?: string
  typeLabel: (type: string) => string
  nodeSummary: (node: PipelineNode) => string
}>()

const emit = defineEmits<{
  select: [key: string]
  'add-node': [payload: { type: string; x: number; y: number }]
  'remove-node': [key: string]
  'move-node': [payload: { key: string; x: number; y: number }]
  'add-edge': [edge: PipelineEdge]
  'remove-edge': [payload: { source: string; target: string }]
}>()

const nodeTypes = { pipeline: markRaw(PipelineFlowNode) }

const flowNodes = computed(() =>
  props.nodes.map((node) => ({
    id: node.key,
    type: 'pipeline',
    position: { x: Number(node.x) || 0, y: Number(node.y) || 0 },
    selected: props.selectedKey === node.key,
    data: {
      key: node.key,
      type: node.type,
      label: node.name,
      typeLabel: props.typeLabel(node.type),
      summary: props.nodeSummary(node),
      readonly: !props.editable,
      onRemove: (key: string) => emit('remove-node', key)
    },
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
    draggable: props.editable
  })) as Node[]
)

const flowEdges = computed<Edge[]>(() =>
  props.edges.map((edge, index) => ({
    id: `${edge.source}->${edge.target}-${index}`,
    source: edge.source,
    target: edge.target,
    sourceHandle: edge.sourcePort || 'out',
    targetHandle: edge.targetPort || 'in',
    type: 'smoothstep',
    animated: false,
    style: { stroke: '#7f93b3', strokeWidth: 2 },
    selectable: props.editable,
    updatable: props.editable
  }))
)

function onNodeClick(event: { node: Node }) {
  emit('select', String(event.node.id))
}

function onConnect(connection: Connection) {
  if (!props.editable || !connection.source || !connection.target) return
  if (connection.source === connection.target) return
  if (props.edges.some((e) => e.source === connection.source && e.target === connection.target)) return
  emit('add-edge', {
    source: connection.source,
    sourcePort: connection.sourceHandle || 'out',
    target: connection.target,
    targetPort: connection.targetHandle || 'in',
    type: 'DATA'
  })
}

function onNodeDragStop(event: NodeDragEvent) {
  if (!props.editable) return
  const node = event.node
  emit('move-node', {
    key: String(node.id),
    x: Math.round(node.position.x),
    y: Math.round(node.position.y)
  })
}

function onNodesDelete(deleted: Node[]) {
  if (!props.editable) return
  for (const node of deleted) emit('remove-node', String(node.id))
}

function onEdgesDelete(deleted: Edge[]) {
  if (!props.editable) return
  for (const edge of deleted) {
    emit('remove-edge', { source: String(edge.source), target: String(edge.target) })
  }
}

function onDrop(event: DragEvent) {
  if (!props.editable) return
  const type = event.dataTransfer?.getData('application/pipeline-node-type')
  if (!type) return
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  emit('add-node', {
    type,
    x: Math.max(24, event.clientX - bounds.left - 100),
    y: Math.max(24, event.clientY - bounds.top - 30)
  })
}
</script>

<style scoped>
.flow-shell {
  position: relative;
  height: 620px;
  min-height: 520px;
  border: 1px solid #dfe6f1;
  border-radius: 16px;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(59, 110, 245, 0.08), transparent 42%),
    linear-gradient(180deg, #f7f9fc 0%, #eef2f8 100%);
}
.flow-shell.readonly { filter: saturate(0.92); }
.flow-board { width: 100%; height: 100%; }
.empty-hint {
  pointer-events: none;
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  gap: 6px;
  text-align: center;
  color: #7d8ba0;
}
.empty-hint strong { color: #4d5d77; font-size: 15px; }
.empty-hint span { font-size: 12px; }
:deep(.vue-flow__controls) {
  border: 1px solid #d9e1ed;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 6px 16px rgba(30, 45, 74, 0.08);
}
:deep(.vue-flow__edge-path) { stroke: #7f93b3; }
:deep(.vue-flow__edge.selected .vue-flow__edge-path) { stroke: #3b6ef5; }
</style>
