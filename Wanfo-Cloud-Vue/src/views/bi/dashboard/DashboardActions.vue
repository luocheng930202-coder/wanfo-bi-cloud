<template>
  <el-dropdown v-if="actions.length" trigger="click" :disabled="disabled" @command="command => emit('action', command)">
    <button type="button" class="board-more" :aria-label="`${board.name}的更多操作`" :disabled="disabled"><el-icon><MoreFilled /></el-icon></button>
    <template #dropdown><el-dropdown-menu class="board-menu">
      <el-dropdown-item v-for="action in actions" :key="action.value" :command="action.value" :icon="action.icon"
        :disabled="disabled" :divided="action.value === 'remove'" :class="{ 'board-menu-danger': action.value === 'remove' }">{{ action.label }}</el-dropdown-item>
    </el-dropdown-menu></template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { MoreFilled, View, EditPen, Promotion, CircleClose, Share, User, Delete } from '@element-plus/icons-vue'
import { checkPermi } from '@/utils/permission'
import type { DashboardSummary } from '@/api/bi/dashboard'
const props = defineProps<{ board: DashboardSummary; disabled?: boolean }>()
const emit = defineEmits<{ action: [command: string] }>()
const actions = computed(() => {
  const can = (resource: string, permission: string) => props.board.resourceActions?.includes(resource) && checkPermi([permission])
  return [
    { value: 'view', label: '查看', icon: View, visible: props.board.published && can('consume', 'bi:dashboard:consume') },
    { value: 'edit', label: '编辑', icon: EditPen, visible: can('edit', 'bi:dashboard:edit') },
    { value: 'publish', label: props.board.published ? '更新发布' : '发布', icon: Promotion, visible: can('publish', 'bi:dashboard:publish') },
    { value: 'offline', label: '下线', icon: CircleClose, visible: props.board.published && can('publish', 'bi:dashboard:publish') },
    { value: 'share', label: '分享', icon: Share, visible: props.board.published && can('publish', 'bi:dashboard:publish') },
    { value: 'grant', label: '授权', icon: User, visible: can('grant', 'bi:dashboard:grant') },
    { value: 'remove', label: '删除', icon: Delete, visible: can('remove', 'bi:dashboard:remove') }
  ].filter(action => action.visible)
})
</script>

<style scoped>
.board-more{border:0;background:transparent;color:#667085;display:grid;place-items:center;width:32px;height:32px;border-radius:6px;cursor:pointer;font-size:17px}.board-more>.el-icon{transform:rotate(90deg)}.board-more:hover{color:#3563e9;background:#edf2ff}.board-more:focus-visible{outline:2px solid #3563e9;outline-offset:2px}.board-more:disabled{opacity:.5;cursor:not-allowed}
</style>
<style>
.board-menu{min-width:142px;padding:7px!important}.board-menu .el-dropdown-menu__item{min-height:36px;border-radius:5px;font-size:14px}.board-menu .el-dropdown-menu__item .el-icon{font-size:16px}.board-menu .board-menu-danger{color:#d92d20}
</style>
