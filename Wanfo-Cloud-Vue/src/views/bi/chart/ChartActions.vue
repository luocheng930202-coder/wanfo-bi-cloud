<template>
  <el-dropdown trigger="click" :disabled="disabled" @command="command=>emit('action',command)">
    <button type="button" class="chart-more" :aria-label="`${chart.name}的更多操作`" :disabled="disabled"><el-icon><MoreFilled/></el-icon></button>
    <template #dropdown><el-dropdown-menu>
      <el-dropdown-item command="open" :icon="EditPen">{{ can('edit','bi:chart:edit') ? '打开编辑' : '打开查看' }}</el-dropdown-item>
      <el-dropdown-item v-if="can('grant','bi:chart:grant')" command="grant" :icon="User">授权管理</el-dropdown-item>
      <el-dropdown-item v-if="can('remove','bi:chart:remove')" command="remove" :icon="Delete" divided class="danger-action">删除图表</el-dropdown-item>
    </el-dropdown-menu></template>
  </el-dropdown>
</template>
<script setup lang="ts">
import { MoreFilled, EditPen, User, Delete } from '@element-plus/icons-vue'
import { checkPermi } from '@/utils/permission'
import type { ChartSummary } from '@/api/bi/chart'
const props=defineProps<{chart:ChartSummary;disabled?:boolean}>()
const emit=defineEmits<{action:[command:string]}>()
const can=(action:string,permission:string)=>props.chart.resourceActions.includes(action) && checkPermi([permission])
</script>
<style scoped>
.chart-more{border:0;background:transparent;color:#667085;display:grid;place-items:center;width:32px;height:32px;border-radius:6px;cursor:pointer;font-size:17px}.chart-more>.el-icon{transform:rotate(90deg)}.chart-more:hover{background:#edf2ff;color:#3563e9}.chart-more:focus-visible{outline:2px solid #3563e9}.chart-more:disabled{opacity:.5;cursor:not-allowed}.danger-action{color:#d92d20}
</style>
