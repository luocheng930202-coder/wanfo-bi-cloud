<template>
  <el-dialog v-model="visible" :title="screen?'导出大屏布局模板':'导出看板布局模板'" width="min(560px,calc(100vw - 24px))" :close-on-click-modal="false">
    <p>{{ screen?'导出当前编辑状态的主题、文字、布局、图层与容器关系。':'导出当前编辑状态的主题、文字与栅格布局。' }}</p>
    <el-alert title="数据绑定、查询条件、筛选与联动、自动刷新、按字段格式及阈值规则将移除。使用者须重新绑定数据；文件不会包含查询结果或权限。" type="info" :closable="false"/>
    <p>已校验的 PNG/JPEG/WebP 会打包进文件（每张不超过 256KB，最多 5 张）。远程地址、SVG 与空图片会拒绝导出。文本和标题会原样保留。</p>
    <el-form label-position="top" class="save-form"><el-form-item label="保存到模板库的名称"><el-input v-model="name" maxlength="200" aria-label="私有模板名称"/></el-form-item><el-form-item label="分类"><el-input v-model="category" maxlength="64" aria-label="私有模板分类"/></el-form-item></el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button :loading="saving" :disabled="!name.trim() || saving" @click="save">保存到模板库</el-button><el-button type="primary" @click="download">下载布局模板</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import {ref} from 'vue'
import type {DashboardDefinition} from '@/api/bi/dashboard'
import type {ChartDefinition} from '@/api/bi/chart'
import {exportTemplateFile} from '@/utils/biTemplateFiles'
import {saveTemplate} from '@/api/bi/template'
const visible=defineModel<boolean>({required:true})
const props=defineProps<{definition:DashboardDefinition;types:Record<string,ChartDefinition['type']>}>()
const error=ref('')
const name=ref(props.definition.name || '未命名模板')
const category=ref('我的模板')
const saving=ref(false)
const screen=props.definition.kind==='screen'
function file(){return exportTemplateFile(props.definition,props.types)}
function download(){
  error.value=''
  try{const exported=file(),url=URL.createObjectURL(new Blob([JSON.stringify(exported,null,2)],{type:'application/json'})),anchor=document.createElement('a');anchor.href=url;anchor.download=exported.name.replace(/[\\/:*?"<>|\u0000-\u001f]/g,'_').slice(0,80)+(screen?'.bi-screen.json':'.bi-dashboard.json');document.body.appendChild(anchor);anchor.click();anchor.remove();setTimeout(()=>URL.revokeObjectURL(url),0);visible.value=false}
  catch(e){error.value=e instanceof Error?e.message:'模板导出失败'}
}
async function save(){
  if(!name.value.trim() || saving.value)return;saving.value=true;error.value=''
  try{const exported=file();await saveTemplate({name:name.value.trim(),category:category.value.trim() || '我的模板',description:exported.name,content:exported});visible.value=false}
  catch(e){error.value=e instanceof Error?e.message:'保存到模板库失败，请检查权限后重试'}finally{saving.value=false}
}
</script>
<style scoped>p{font-size:13px;color:#64748b;line-height:1.8}.save-form{margin-top:12px}</style>
