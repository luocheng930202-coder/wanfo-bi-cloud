<template>
  <div class="board-cover">
    <BiResourceThumbnail kind="dashboard" :id="board.id" :name="board.name" :version="board.version" :actions="board.resourceActions" :published="board.published" :refresh-key="refreshKey" />
    <div class="cover-actions">
      <el-button v-if="board.published && can('consume', 'bi:dashboard:consume')" :icon="View" :disabled="disabled" @click="emit('action', 'view')">查看</el-button>
      <el-button v-if="can('edit', 'bi:dashboard:edit')" :icon="EditPen" :disabled="disabled" @click="emit('action', 'edit')">编辑</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { View, EditPen } from '@element-plus/icons-vue'
import type { DashboardSummary } from '@/api/bi/dashboard'
import { checkPermi } from '@/utils/permission'
import BiResourceThumbnail from '../components/BiResourceThumbnail.vue'
const props = defineProps<{ board: DashboardSummary; disabled?: boolean; refreshKey?:number }>()
const emit = defineEmits<{ action: [command: string] }>()
const can = (resource: string, permission: string) => props.board.resourceActions?.includes(resource) && checkPermi([permission])
</script>

<style scoped>
.board-cover{position:relative;aspect-ratio:16/10;border-radius:7px;overflow:hidden;background:var(--bi-background,#f6f8fc)}.cover-actions{position:absolute;inset:0;pointer-events:none;display:flex;justify-content:center;align-items:center;gap:8px;background:#1023444d;opacity:0;transition:opacity .16s}.cover-actions:empty{display:none}.cover-actions .el-button{pointer-events:auto;margin:0;min-width:76px;border:0;color:#254dcc}.board-cover:hover .cover-actions,.board-cover:focus-within .cover-actions{opacity:1}
@media(hover:none){.cover-actions{opacity:1;background:transparent;inset:auto auto 8px 8px}.cover-actions .el-button{min-width:54px;height:28px;padding:0 9px}}
@media(prefers-reduced-motion:reduce){.cover-actions{transition:none}}
</style>
