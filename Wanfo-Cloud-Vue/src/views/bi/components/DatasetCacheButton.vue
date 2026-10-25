<template>
  <el-button v-if="checkPermi(['bi:datasource:model'])" link @click="open">缓存设置</el-button>
  <el-dialog v-model="visible" title="数据集查询缓存" width="min(550px, calc(100vw - 24px))" append-to-body>
    <el-alert title="按当前用户及权限范围隔离缓存。每次命中前仍检查权限；外部数据库数据可能在缓存有效期内保持旧值。图表手动更新可跳过缓存，导出不使用此缓存。" type="info" :closable="false" />
    <el-form label-position="top" :disabled="busy" style="margin-top:18px"><el-form-item label="启用缓存"><el-switch v-model="enabled" /></el-form-item><el-form-item label="有效时间（秒）"><el-input-number v-model="ttl" :min="5" :max="300" :precision="0" /></el-form-item></el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="busy" @click="save">保存并清理旧缓存</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import {ref} from 'vue'
import {ElMessage} from 'element-plus'
import {checkPermi} from '@/utils/permission'
import request from '@/utils/request'
import type {BiResult} from '@/api/bi/response'
const props=defineProps<{id:number}>()
const visible=ref(false),busy=ref(false),enabled=ref(false),ttl=ref(300),error=ref('')
let generation=0
async function open(){const token=++generation;visible.value=true;busy.value=true;error.value='';try{const response:BiResult<{enabled:boolean;ttl:number}>=await request({url:`/bi/dataset/${props.id}/cache`});if(token===generation){enabled.value=response.data.enabled;ttl.value=response.data.ttl}}catch{if(token===generation)error.value='读取失败，需要数据源建模权限及数据集目录编辑权限'}finally{if(token===generation)busy.value=false}}
async function save(){if(busy.value)return;busy.value=true;try{await request({url:`/bi/dataset/${props.id}/cache`,method:'put',data:{enabled:enabled.value,ttl:ttl.value}});ElMessage.success('缓存设置已保存');visible.value=false}catch{error.value='保存失败，请检查权限和有效时间'}finally{busy.value=false}}
</script>
