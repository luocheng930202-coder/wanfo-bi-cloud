<template>
  <div v-if="readonly" class="view-grid bi-surface" :style="presentationVars(appearance)">
    <article v-for="panel in panels" :key="panel.key" class="view-panel" :class="{'compact-panel':panel.componentType && panel.componentType!=='chart'}" :style="{gridColumn:`${panel.x+1} / span ${panel.w}`,gridRow:`${panel.y+1} / span ${panel.h}`,'--thumbnail-column':`${panel.x+1} / span ${panel.w}`,'--thumbnail-row':`${panel.y+1} / span ${panel.h}`}"><slot :panel="panel"/></article>
  </div>
  <div v-else class="canvas-scroll bi-surface" :style="presentationVars(appearance)"><div ref="host" class="grid-stack">
    <div v-for="panel in panels" :key="panel.key" class="grid-stack-item" :gs-id="panel.key" :gs-x="panel.x" :gs-y="panel.y" :gs-w="panel.w" :gs-h="panel.h" gs-min-w="2" gs-min-h="2" gs-max-h="12">
      <div class="grid-stack-item-content" :class="{'is-selected':selectedKey===panel.key || selectedKeys?.includes(panel.key),'is-locked':panel.locked}" tabindex="0" :aria-label="'选择组件 '+panel.key" @pointerdown="selectPointer(panel.key,$event)" @keydown="selectKeyboard(panel.key,$event)"><slot :panel="panel"/></div>
    </div>
  </div></div>
</template>
<script setup lang="ts" generic="T extends PanelLayout">
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { GridStack, type GridItemHTMLElement } from 'gridstack'
import 'gridstack/dist/gridstack.min.css'
import type { DashboardAppearance,PanelLayout } from '@/api/bi/dashboard'
import { presentationVars } from '@/utils/biPresentation'
const props=withDefaults(defineProps<{panels:T[];readonly?:boolean;disabled?:boolean;appearance?:DashboardAppearance;selectedKey?:string;selectedKeys?:string[]}>(),{readonly:false,disabled:false})
const emit=defineEmits<{(event:'change',items:PanelLayout[]):void;(event:'select',key:string,additive?:boolean):void}>()
const host=ref<HTMLElement>()
let grid:GridStack|undefined,syncing=false,generation=0
function selectPointer(key:string,event:PointerEvent){if(event.button!==0)return;emit('select',key,event.ctrlKey || event.metaKey || event.shiftKey)}
function selectKeyboard(key:string,event:KeyboardEvent){if(event.target===event.currentTarget && ['Enter',' '].includes(event.key)){event.preventDefault();emit('select',key,event.ctrlKey || event.metaKey || event.shiftKey)}}
function destroy(){grid?.destroy(false);grid=undefined}
watch(()=>[props.readonly,props.disabled,JSON.stringify(props.panels)],async()=>{
  const v=++generation;await nextTick();if(v!==generation)return
  if(props.readonly || !host.value){destroy();return}
  syncing=true
  try {
    if(!grid){
      grid=GridStack.init({column:12,cellHeight:70,margin:8,float:true,handle:'.panel-handle',animate:false,disableDrag:props.disabled,disableResize:props.disabled,maxRow:120},host.value)
      grid.on('change',()=>{if(syncing || props.disabled)return;const items=grid!.getGridItems().map(el=>{const n=el.gridstackNode!;return {key:String(n.id),x:n.x||0,y:n.y||0,w:n.w||1,h:n.h||2}});emit('change',items)})
    }
    grid.enableMove(!props.disabled);grid.enableResize(!props.disabled)
    for(const n of [...grid.engine.nodes])if(n.el && !props.panels.some(p=>p.key===String(n.id)))grid.removeWidget(n.el,false,false)
    grid.batchUpdate()
    for(const p of props.panels){const el=Array.from(host.value.children).find(el=>el.getAttribute('gs-id')===p.key) as GridItemHTMLElement|undefined;if(!el)continue;if(!el.gridstackNode)grid.makeWidget(el);grid.update(el,{id:p.key,x:p.x,y:p.y,w:p.w,h:p.h,minW:2,minH:2,maxH:12,noMove:!!p.locked || props.disabled,noResize:!!p.locked || props.disabled,locked:!!p.locked})}
    grid.batchUpdate(false)
  }finally{syncing=false}
},{immediate:true})
onBeforeUnmount(()=>{++generation;destroy()})
</script>
<style scoped>
.bi-surface{background:var(--bi-background);color:var(--bi-text);padding:8px;border-radius:6px}.canvas-scroll{overflow-x:auto;border:1px dashed var(--bi-border);min-height:140px}.grid-stack{min-width:720px}.grid-stack-item-content,.view-panel{background:var(--bi-card);border:1px solid var(--bi-border);border-radius:var(--bi-radius);overflow:auto}.view-grid{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));grid-auto-rows:70px;gap:8px}.view-panel{min-width:0}
.bi-surface :deep(.el-table){--el-table-bg-color:var(--bi-card);--el-table-tr-bg-color:var(--bi-card);--el-table-header-bg-color:var(--bi-card);--el-table-text-color:var(--bi-text);--el-table-header-text-color:var(--bi-text);--el-table-border-color:var(--bi-border);--el-table-row-hover-bg-color:var(--bi-background)}
.bi-surface :deep(.el-empty__description p){color:var(--bi-text)}
.bi-surface :deep(.el-table th.el-table__cell){background-color:var(--bi-card)!important;color:var(--bi-text)}
.bi-surface :deep(.panel-header .el-button.is-link:not(.el-button--danger)){color:var(--bi-text)}
.grid-stack-item-content.is-selected{outline:2px solid #3370ff;outline-offset:-2px;box-shadow:0 0 0 3px #3370ff18}
.grid-stack-item-content.is-locked{border-style:dashed}.is-locked :deep(.panel-handle){cursor:default}
@media(max-width:767px){.view-grid{grid-template-columns:minmax(0,1fr);grid-auto-rows:auto}.view-panel{grid-column:1!important;grid-row:auto!important;min-height:420px}.view-panel.compact-panel{min-height:0}}
</style>
