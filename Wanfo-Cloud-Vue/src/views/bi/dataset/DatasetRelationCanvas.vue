<template>
  <div class="relation-canvas" :class="{'drop-active':dragging}" @dragover.prevent="dragging=true" @dragleave.self="dragging=false" @drop.prevent="drop">
    <div class="canvas-caption"><span>关联画布</span><small>拖入主表及关联表 · 同一数据源 / 数据库</small></div>
    <div v-if="!table" class="canvas-empty"><div class="empty-symbol">▤ ＋ ▤</div><h3>拖入数据表，开始构建数据集</h3><p>也可以点击左侧数据表旁的添加按钮</p></div>
    <div v-else class="canvas-nodes">
      <div class="table-node primary-node"><div class="node-heading"><span>▤</span><strong>{{ sqlNode ? '自定义 SQL' : table }}</strong><el-tag size="small">{{ sqlNode ? 'SQL 节点' : '主表' }}</el-tag><el-button link type="danger" :disabled="disabled" aria-label="移除主表及全部关联" @click="$emit('clear')">×</el-button></div><div class="node-body"><span>{{ catalog }}</span><small>{{ fieldCount }} 个选用字段</small></div><el-button v-if="sqlNode" link type="primary" :disabled="disabled" @click="$emit('edit-sql')">编辑 SQL</el-button></div>
      <template v-for="(join,index) in joins" :key="index">
        <button class="relation-edge" :class="{incomplete:!join.leftKey || !join.rightKey}" @click="$emit('edit',index)"><span class="edge-line"></span><b>{{ join.joinType }}</b><small>{{ join.leftKey && join.rightKey ? `${join.leftKey} = ${join.rightKey}` : '点击配置关联条件' }}</small></button>
        <div class="table-node"><div class="node-heading"><span>▤</span><strong>{{ join.rightTable }}</strong><el-button link type="danger" :disabled="disabled" :aria-label="`移除关联 ${index+1}`" @click="$emit('remove',index)">×</el-button></div><div class="node-body"><span>关联 {{ index+1 }}</span><small>{{ join.rightFields.length }} 个字段</small></div><el-button link type="primary" :disabled="disabled" @click="$emit('edit',index)">配置关联</el-button></div>
      </template>
      <div class="add-hint">＋<small>继续拖入表<br/>添加关联</small></div>
    </div>
    <div class="canvas-footer"><span>{{ table ? `${joins.length+1} 张表 · ${joins.length} 条关联` : '尚未添加表' }}</span><span>支持 LEFT / INNER / RIGHT JOIN</span></div>
  </div>
</template>
<script setup lang="ts">
import {ref} from 'vue'
import type {DatasetJoinStep} from '@/api/bi/dataset'
defineProps<{table:string;catalog:string;fieldCount:number;joins:DatasetJoinStep[];disabled:boolean;sqlNode?:boolean}>()
const emit=defineEmits<{(e:'edit-sql'):void;(e:'clear'):void;(e:'add',name:string):void;(e:'edit',index:number):void;(e:'remove',index:number):void}>()
const dragging=ref(false)
function drop(event:DragEvent){dragging.value=false;const value=event.dataTransfer?.getData('application/wanfo-table');if(value)emit('add',value)}
</script>
<style scoped>
.relation-canvas{position:relative;min-height:300px;background-color:var(--el-fill-color-lighter);background-image:radial-gradient(var(--el-border-color) 1px,transparent 1px);background-size:16px 16px;border-bottom:1px solid var(--el-border-color-light);display:flex;flex-direction:column}.drop-active{outline:2px dashed var(--el-color-primary);outline-offset:-3px}.canvas-caption,.canvas-footer{display:flex;justify-content:space-between;padding:16px 24px;color:var(--el-text-color-secondary);font-size:12px}.canvas-caption span{font-weight:600;color:var(--el-text-color-primary)}.canvas-empty{margin:auto;text-align:center;padding:28px}.empty-symbol{font-size:38px;letter-spacing:8px;color:var(--el-color-primary-light-5)}.canvas-empty h3{font-weight:500;font-size:16px;margin:14px}.canvas-empty p{font-size:13px;color:var(--el-text-color-secondary)}.canvas-nodes{display:flex;align-items:center;padding:32px 32px 38px;overflow:auto;flex:1}.table-node{flex:0 0 220px;background:var(--el-bg-color);border:1px solid var(--el-border-color);border-radius:8px;box-shadow:0 4px 12px #00000006;padding:0 14px 10px}.primary-node{border-color:var(--el-color-primary)}.node-heading{display:flex;align-items:center;gap:8px;padding:14px 0;border-bottom:1px solid var(--el-border-color-extra-light)}.node-heading>span{color:var(--el-color-primary)}.node-heading strong{font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1}.node-body{display:flex;justify-content:space-between;font-size:12px;color:var(--el-text-color-secondary);padding:14px 0}.relation-edge{border:0;background:transparent;flex:0 0 160px;position:relative;text-align:center;cursor:pointer;color:var(--el-color-primary);padding:16px 4px}.edge-line{position:absolute;top:28px;left:0;width:100%;border-top:1px solid var(--el-color-primary-light-5)}.relation-edge b{position:relative;background:var(--el-bg-color);border:1px solid var(--el-color-primary-light-5);border-radius:14px;font-size:11px;padding:5px 12px}.relation-edge small{display:block;font-size:11px;margin-top:16px;overflow-wrap:anywhere}.incomplete{color:var(--el-color-warning)}.add-hint{flex:0 0 100px;text-align:center;font-size:26px;color:var(--el-text-color-placeholder);padding-left:24px}.add-hint small{display:block;font-size:12px;line-height:1.8}.canvas-footer{margin-top:auto;padding-top:8px}
</style>
