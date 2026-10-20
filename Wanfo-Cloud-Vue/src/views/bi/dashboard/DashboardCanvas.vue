<template><DashboardScreenCanvas v-if="scene" :panels="panels" :scene="scene" :compact="compact" @zoom="value=>emit('zoom',value)" :readonly="readonly" :disabled="disabled" :appearance="appearance" :selected-key="selectedKey" :selected-keys="selectedKeys" :zoom="zoom" @select="(key,additive)=>emit('select',key,additive)" @change="items=>emit('change',items)"><template #default="{panel}"><slot :panel="panel"/></template></DashboardScreenCanvas><DashboardGridCanvas v-else :panels="panels" :readonly="readonly" :disabled="disabled" :appearance="appearance" :selected-key="selectedKey" :selected-keys="selectedKeys" @select="(key,additive)=>emit('select',key,additive)" @change="items=>emit('change',items)"><template #default="{panel}"><slot :panel="panel"/></template></DashboardGridCanvas></template>
<script setup lang="ts" generic="T extends PanelLayout">
import type {PanelLayout,DashboardAppearance,ScreenScene} from '@/api/bi/dashboard'
import DashboardGridCanvas from './DashboardGridCanvas.vue'
import DashboardScreenCanvas from './DashboardScreenCanvas.vue'
defineProps<{panels:T[];scene?:ScreenScene;zoom?:number;compact?:boolean;readonly?:boolean;disabled?:boolean;appearance?:DashboardAppearance;selectedKey?:string;selectedKeys?:string[]}>()
const emit=defineEmits<{(event:'zoom',value:number):void;(event:'change',items:PanelLayout[]):void;(event:'select',key:string,additive?:boolean):void}>()
</script>
