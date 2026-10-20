<template>
  <div class="template-preview" :class="template.theme" :style="file && 'scene' in file?{aspectRatio:file.scene.width+'/'+file.scene.height}:{}" role="img" :aria-label="template.name+'布局示意，无业务数据'">
    <div v-if="!file && !dashboard" class="mock-title">{{ template.name }}<small>布局示意 · 无业务数据</small></div>
    <div v-for="slot in slots" :key="slot.key" class="mock-slot" :style="{left:slot.x/width*100+'%',top:slot.y/height*100+'%',width:slot.w/width*100+'%',height:slot.h/height*100+'%'}">
      <span>{{ slot.label }}</span><strong v-if="slot.kind==='kpi'">— —</strong>
      <img v-else-if="slot.kind==='image' && slot.content?.image" :src="slot.content.image" alt="" class="mock-image"/>
      <div v-else-if="slot.kind==='image'" class="mock-image-empty">未打包图片</div>
      <div v-else-if="slot.type==='bar'" class="mock-bars"><i v-for="(height,i) in [34,64,47,82,58]" :key="i" :style="{height:height+'%'}"/></div>
      <div v-else-if="slot.kind==='chart'" class="mock-table"><i v-for="i in 4" :key="i"/></div>
    </div>
  </div>
</template>
<script setup lang="ts">
import {computed} from 'vue'
import type {ScreenTemplate} from '@/utils/biTemplates'
import type {LayoutTemplateFile} from '@/utils/biTemplateFiles'
const props=defineProps<{template:ScreenTemplate;file?:LayoutTemplateFile}>()
const dashboard=computed(()=>props.template.kind==='dashboard' || props.file?.format==='datacenter-dashboard-template')
const slots=computed(()=>props.file?props.file.panels.filter(p=>!p.hidden).map(p=>({key:p.key,label:p.title,kind:p.componentType,type:p.chartType,x:p.x,y:p.y,w:p.w,h:p.h,content:p.content})):props.template.slots.map(s=>({...s,content:undefined})))
const width=computed(()=>props.file && 'scene' in props.file?props.file.scene.width:dashboard.value?12:1920)
const height=computed(()=>props.file && 'scene' in props.file?props.file.scene.height:dashboard.value?Math.max(12,...slots.value.map(s=>s.y+s.h),12):1080)
</script>
<style scoped>
.template-preview{aspect-ratio:16/9;position:relative;border-radius:10px;overflow:hidden;background:#0b1426;color:#e7f0ff;border:1px solid #284361}.template-preview.light{background:#f1f5f9;color:#20324d;border-color:#d7e2f0}.mock-title{position:absolute;left:2.1%;right:2.1%;top:3%;font-size:clamp(12px,1.4vw,20px);font-weight:600;display:flex;justify-content:space-between;align-items:center}.mock-title small{font-size:10px;opacity:.6;font-weight:400}.mock-slot{position:absolute;box-sizing:border-box;background:#14243b;border:1px solid #284361;border-radius:5px;padding:2%;display:flex;flex-direction:column;gap:8px;font-size:11px;overflow:hidden}.light .mock-slot{background:white;border-color:#d7e2f0}.mock-slot strong{font-size:22px;color:#38bdf8}.mock-image{width:100%;flex:1;min-height:0;object-fit:contain;border-radius:4px;display:block}.mock-image-empty{flex:1;display:flex;align-items:center;justify-content:center;opacity:.55}.mock-bars{display:flex;flex:1;min-height:0;align-items:end;gap:8%;padding:5% 8%;border-bottom:1px solid #53647b}.mock-bars i{flex:1;background:linear-gradient(#38bdf8,#3370ff);border-radius:3px 3px 0 0}.mock-table{display:flex;flex-direction:column;justify-content:space-around;flex:1}.mock-table i{height:10%;background:#64748b33;border-top:1px solid #64748b44}
</style>
