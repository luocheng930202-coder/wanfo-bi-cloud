<template>
  <h4 v-if="style.showTitle" :style="{color:appearance?.textColor || '#303133',textAlign:'center'}">{{ style.title || definition.name }}</h4>
  <el-table :data="result.rows" :border="style.tableBorder!==false" :stripe="!!style.tableStripe" max-height="420" size="small" :style="theme" :cell-style="cellStyle" :header-cell-style="headerStyle">
    <el-table-column v-if="linkable || drillable && canDrillDown" label="操作" width="88" fixed="left"><template #default="{row}"><el-button v-if="drillable && canDrillDown" link type="primary" @click="$emit('drill',row)">下钻</el-button><el-button v-else-if="linkable" link type="primary" @click="$emit('select',row)">选择</el-button></template></el-table-column>
    <el-table-column v-for="key in result.columns" :key="key" :prop="key" :label="labels[key] || key" :width="column(key)?.width" :min-width="column(key)?.width || 140" :align="column(key)?.align || (metrics.has(key)?'right':'left')" :fixed="column(key)?.fixed==='none'?false:column(key)?.fixed" :show-overflow-tooltip="!column(key)?.wrap">
      <template #default="{row}"><span :style="{display:'block',boxSizing:'border-box',minHeight:(style.tableRowHeight || 32)+'px',lineHeight:'20px',padding:Math.max(4,((style.tableRowHeight || 32)-20)/2)+'px 0',whiteSpace:column(key)?.wrap?'pre-wrap':'nowrap',overflowWrap:'anywhere',color:metrics.has(key)?metricColor(row[key],key,effectiveMetrics):undefined}">{{ text(row[key],key) }}</span></template>
    </el-table-column>
  </el-table>
</template>
<script setup lang="ts">
import {computed} from 'vue'
import {BI_FONT_FAMILY} from '@/utils/biTheme'
import type {CSSProperties} from 'vue'
import type {ChartDefinition,ChartResult} from '@/api/bi/chart'
import type {DashboardAppearance,NumberFormat,MetricStyle} from '@/api/bi/dashboard'
import {tableColumn,tableBackground} from '@/utils/biTablePresentation'
import {resolveChartMetricFormat,resolvedMetricStyles} from '@/utils/biChartPresentation'
import {metricResultKey} from '@/utils/biEchartsPreview'
import {displayNumber,formatMetric,metricColor} from '@/utils/biPresentation'
const props=defineProps<{definition:ChartDefinition;result:ChartResult;labels:Record<string,string>;appearance?:DashboardAppearance;numberFormat?:NumberFormat|null;metricStyles?:MetricStyle[];linkable?:boolean;drillable?:boolean;canDrillDown?:boolean}>()
defineEmits<{(e:'select',row:Record<string,unknown>):void;(e:'drill',row:Record<string,unknown>):void}>()
const style=computed(()=>props.definition.style || {})
const metrics=computed(()=>new Set(props.definition.query.metrics.map(metricResultKey)))
const effectiveMetrics=computed(()=>resolvedMetricStyles(style.value,props.metricStyles))
function column(key:string){return tableColumn(style.value,key)}
function cellStyle({row,column:c}:{row:Record<string,unknown>;column:{property:string}}){return {padding:'0 0',backgroundColor:tableBackground(style.value,c.property,row[c.property])}}
const headerStyle=computed(()=>({backgroundColor:style.value.tableHeaderBackground || (props.appearance?.theme==='dark'?'#25344A':'#F5F7FA'),color:style.value.tableHeaderColor || props.appearance?.textColor || '#475467',fontWeight:'600'}))
const theme=computed(()=>({fontFamily:BI_FONT_FAMILY,'--el-table-bg-color':props.appearance?.cardBackground || '#FFFFFF','--el-table-tr-bg-color':props.appearance?.cardBackground || '#FFFFFF','--el-table-text-color':props.appearance?.textColor || '#303133','--el-table-row-hover-bg-color':props.appearance?.theme==='dark'?'#334155':'#F5F7FA','--el-table-border-color':props.appearance?.borderColor || '#EBEEF5','--el-fill-color-lighter':props.appearance?.theme==='dark'?'#25344A':'#FAFAFA'} as CSSProperties))
function text(value:unknown,key:string){if(value==null && style.value.tableNullText!=null)return style.value.tableNullText;const format=resolveChartMetricFormat(style.value,key,props.metricStyles,props.numberFormat);return metrics.value.has(key) && format?formatMetric(value,props.appearance,format):displayNumber(value,props.appearance)}
</script>
