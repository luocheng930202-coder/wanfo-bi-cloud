<template>
  <el-button v-if="!hiddenTrigger" link type="primary" @click.stop="open">引用关系</el-button>
  <el-dialog v-model="visible" title="资源引用与变更影响" width="min(900px, calc(100vw - 24px))" append-to-body @closed="generation++">
    <div v-loading="loading">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template v-if="graph">
        <el-alert :title="graph.notice" type="info" :closable="false" />
        <p>当前可见的下游资源：{{ graph.downstream.length }} 个。修改上游字段或删除资源前，请先核对这些引用。</p>
        <el-alert v-if="graph.truncated" title="关系较多，当前仅显示部分结果，请缩小到具体资源查看。" type="warning" :closable="false" />
        <el-table :data="graph.nodes" max-height="340">
          <el-table-column label="类型" width="110"><template #default="{row}">{{ labels[row.type] }}</template></el-table-column>
          <el-table-column prop="name" label="资源名称" />
          <el-table-column label="关系" width="130"><template #default="{row}">{{ row.key===graph?.root?'当前资源':graph?.downstream.includes(row.key)?'受影响的下游':'上游或关联资源' }}</template></el-table-column>
        </el-table>
        <el-table :data="graph.edges" max-height="250">
          <el-table-column label="来源"><template #default="{row}">{{ nodeName(row.source) }}</template></el-table-column>
          <el-table-column label="引用方"><template #default="{row}">{{ nodeName(row.target) }}</template></el-table-column>
          <el-table-column label="绑定" width="100"><template #default="{row}">{{ row.kind==='draft'?'草稿':row.kind==='published'?'发布快照':'数据绑定' }}</template></el-table-column>
        </el-table>
      </template>
    </div>
    <template #footer><el-button @click="visible=false">关闭</el-button><el-button :loading="loading" @click="open">刷新关系</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import request from '@/utils/request'
import type { BiResult } from '@/api/bi/response'
type Kind='datasource'|'dataset'|'chart'|'dashboard'
interface Graph{root:string;nodes:{key:string;type:Kind;id:number;hiddenTrigger?:boolean;name:string}[];edges:{source:string;target:string;kind:string}[];downstream:string[];notice:string;truncated:boolean}
const props=defineProps<{type:Kind;id:number;hiddenTrigger?:boolean}>()
const visible=ref(false),loading=ref(false),error=ref(''),graph=ref<Graph>()
const labels:Record<string,string>={datasource:'数据源',dataset:'数据集',chart:'图表',dashboard:'看板 / 大屏'}
let generation=0
function nodeName(key:string){return graph.value?.nodes.find(n=>n.key===key)?.name || key}
async function open(){
  const current=++generation;visible.value=true;loading.value=true;error.value='';graph.value=undefined
  try{const response:BiResult<Graph>=await request({url:`/bi/resources/${props.type}/${props.id}/impact`});if(current===generation)graph.value=response.data}
  catch{if(current===generation)error.value='引用关系读取失败，请确认权限后重试'}
  finally{if(current===generation)loading.value=false}
}
defineExpose({open})
</script>
