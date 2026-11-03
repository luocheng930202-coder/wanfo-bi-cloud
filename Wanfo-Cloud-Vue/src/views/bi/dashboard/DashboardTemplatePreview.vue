<template>
  <div ref="previewRoot" class="template-preview" :class="[layoutFile?.appearance.theme || template.theme,{'is-demo':demonstration}]" :style="previewAspect" :role="demonstration?'group':'img'" :aria-label="template.name+(demonstration?'效果演示，非真实业务数据':'布局示意，无业务数据')">
    <span class="preview-watermark">{{ demonstration?'效果演示 · 非真实业务数据':'布局示意 · 无业务数据' }}</span>
    <div v-if="layoutFile" class="rendered-layout" :inert="!demonstration">
      <div class="preview-surface" :style="surfaceStyle">
        <DashboardCanvas :scene="layoutFile.format==='datacenter-screen-template'?{...layoutFile.scene,fit:'contain'}:undefined" :panels="layoutFile.panels" :appearance="layoutFile.appearance" fixed-layout readonly compact>
          <template #default="{panel}"><DashboardCard :title="panel.title" :component-type="panel.componentType" :content="panel.content" :panel-style="panel.style" :appearance="layoutFile?.appearance" :payload="demoPayloads[panel.key]" :demonstration="demonstration" :thumbnail="!demonstration" disabled refresh-disabled /></template>
        </DashboardCanvas>
      </div>
    </div>
    <el-empty v-else description="加载模板后显示布局" :image-size="40" />
  </div>
</template>
<script setup lang="ts">
import {computed,ref,onMounted,onBeforeUnmount} from 'vue'
import {templateAppearance,type ScreenTemplate} from '@/utils/biTemplates'
import type {LayoutTemplateFile} from '@/utils/biTemplateFiles'
import DashboardCanvas from './DashboardCanvas.vue'
import DashboardCard from './DashboardCard.vue'
import {templateGridSize,fitTemplateViewport} from '@/utils/biTemplateViewport'
import {templateDemoPayloads} from '@/utils/biTemplateDemo'
const props=defineProps<{template:ScreenTemplate;file?:LayoutTemplateFile;demonstration?:boolean}>()
const layoutFile=computed<LayoutTemplateFile|undefined>(()=>{
  if(props.file)return props.file
  if(!props.template.slots.length)return
  const template=props.template
  const panels=template.slots.map(slot=>({key:slot.key,title:slot.label,componentType:slot.kind,chartType:slot.type,x:slot.x,y:slot.y,w:slot.w,h:slot.h,style:{showTitle:true,titleAlign:'left' as const}}))
  const common={version:1 as const,name:template.name,appearance:templateAppearance(template.theme),panels}
  if(template.kind==='dashboard')return {...common,format:'datacenter-dashboard-template'}
  return {...common,format:'datacenter-screen-template',scene:{width:1920,height:1080,fit:'contain',refreshSeconds:0},panels:[{key:'preview-heading',title:template.name,componentType:'text',x:40,y:20,w:1840,h:90,content:{text:template.description},style:{showTitle:true,titleAlign:'left'}},...panels]}
})
const previewRoot=ref<HTMLElement>()
const demoPayloads=computed(()=>props.demonstration && layoutFile.value?templateDemoPayloads(layoutFile.value):{})
const viewport=ref({width:0,height:0})
let observer:ResizeObserver|undefined
onMounted(()=>{if(previewRoot.value){observer=new ResizeObserver(entries=>{const rect=entries[0]?.contentRect;if(rect)viewport.value={width:rect.width,height:rect.height}});observer.observe(previewRoot.value)}})
onBeforeUnmount(()=>observer?.disconnect())
const gridSize=computed(()=>templateGridSize(layoutFile.value?.panels || []))
const previewAspect=computed(()=>({aspectRatio:layoutFile.value?.format==='datacenter-screen-template'?layoutFile.value.scene.width+'/'+layoutFile.value.scene.height:layoutFile.value?gridSize.value.width+'/'+gridSize.value.height:'16/9'}))
const surfaceStyle=computed(()=>{
  if(layoutFile.value?.format!=='datacenter-dashboard-template')return {width:'100%',height:'100%'}
  const native=gridSize.value,fit=fitTemplateViewport(viewport.value.width,viewport.value.height,native.width,native.height)
  return {position:'absolute' as const,width:native.width+'px',height:native.height+'px',transformOrigin:'0 0',transform:`translate(${fit.x}px,${fit.y}px) scale(${fit.scale})`}
})
</script>
<style scoped>
.template-preview{aspect-ratio:16/9;position:relative;border-radius:10px;overflow:hidden;background:#071625;color:#e3f5ff;border:1px solid #1b5268}
.template-preview.light{background:#f1f5f9;color:#20324d;border-color:#d7e2f0}
.preview-watermark{position:absolute;right:1.2%;top:1.5%;z-index:2;padding:3px 6px;border:1px solid #5591a866;border-radius:3px;background:#061a2bc9;color:#92b4c6;font-size:clamp(6px,.55vw,9px);letter-spacing:.3px}
.rendered-layout{position:absolute;inset:0;pointer-events:none}
.is-demo .rendered-layout{pointer-events:auto}
.rendered-layout :deep(.screen-editor){height:100%}
.rendered-layout :deep(.screen-frame){height:100%;min-height:0}
.rendered-layout :deep(.screen-display-tools){display:none}
</style>
