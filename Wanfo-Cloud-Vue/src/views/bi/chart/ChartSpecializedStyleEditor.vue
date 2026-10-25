<template>
  <template v-if="definition.type==='map'">
    <el-form-item label="显示色阶图例"><el-switch :model-value="style.showLegend!==false" @update:model-value="v=>style.showLegend=!!v"/></el-form-item>
    <el-form-item label="底图主题"><el-select :model-value="style.mapBaseStyle||'light'" @change="v=>style.mapBaseStyle=v"><el-option value="light" label="浅色边界"/><el-option value="dark" label="深色边界"/><el-option value="outline" label="纯边界"/></el-select></el-form-item>
    <el-form-item label="坐标投影"><el-select :model-value="style.mapProjection||'raw'" @change="v=>style.mapProjection=v"><el-option value="raw" label="原始经纬度"/><el-option value="mercator" label="墨卡托"/></el-select></el-form-item>
    <p class="hint">底图来自内置行政区边界数据，不依赖在线瓦片；墨卡托投影适合横向比较区域形状，纬度会限制在 ±85.05°。</p>
    <el-form-item label="地图色阶"><el-select :model-value="style.mapColorMode || 'continuous'" @change="v=>setMapMode(v)"><el-option value="continuous" label="连续渐变"/><el-option value="piecewise" label="自定义分段"/></el-select></el-form-item>
    <el-form-item v-if="style.mapColorMode==='piecewise'" label="分段边界"><div><div v-for="(value,i) in style.mapBreaks" :key="i" style="display:flex;gap:8px;margin-bottom:8px"><el-input-number :model-value="value" :precision="2" @update:model-value="v=>setBreak(i,v)"/><el-button link @click="removeBreak(i)">移除</el-button></div><el-button :disabled="(style.mapBreaks?.length || 0)>=5" @click="addBreak">添加边界</el-button><p class="hint">按原始数值从小到大填写，边界计入右侧区间；最多 5 个边界。金额单位设置不会改变分段口径。</p></div></el-form-item>
    <template v-if="style.mapColorMode==='piecewise' && style.mapBreaks?.length">
      <el-form-item v-for="(_,i) in style.mapBreaks.length+1" :key="i" :label="pieceLabel(i)"><el-color-picker :model-value="mapPieceColor(style,i)" :aria-label="'第'+(i+1)+'段颜色'" @update:model-value="v=>setPiece(i,v)"/></el-form-item>
      <el-button link @click="style.mapPieceColors=undefined">恢复逐段渐变色</el-button>
      <p class="hint">修改分段边界会恢复自动渐变，可重新为每一段设色。清空单段颜色则仅恢复该段的自动色。</p>
    </template>
    <el-form-item label="低值颜色"><el-color-picker :model-value="style.mapLowColor || '#e0f3ff'" @update:model-value="v=>style.mapLowColor=v || undefined"/></el-form-item>
    <el-form-item label="高值颜色"><el-color-picker :model-value="style.mapHighColor || '#006edd'" @update:model-value="v=>style.mapHighColor=v || undefined"/></el-form-item>
    <el-form-item label="无数据颜色"><el-color-picker :model-value="style.mapNoDataColor || '#eeeeee'" @update:model-value="v=>style.mapNoDataColor=v || undefined"/></el-form-item>
    <el-form-item label="边界颜色"><el-color-picker :model-value="style.mapBorderColor || '#a0a6ad'" @update:model-value="v=>style.mapBorderColor=v || undefined"/></el-form-item>
    <el-form-item label="显示地区标签"><el-switch :model-value="!!style.mapShowLabels" @update:model-value="v=>style.mapShowLabels=!!v"/></el-form-item>
    <el-form-item v-if="style.mapShowLabels" label="地区标签内容"><el-select :model-value="style.mapLabelContent || 'name'" @change="v=>style.mapLabelContent=v"><el-option value="name" label="地区名称"/><el-option value="value" label="格式化数值"/><el-option value="nameValue" label="名称与数值"/></el-select></el-form-item>
    <el-form-item label="显示地图提示框"><el-switch :model-value="style.mapShowTooltip!==false" @update:model-value="v=>style.mapShowTooltip=!!v"/></el-form-item>
    <p class="hint">标签自动隐藏重叠项。无数据区域保持独立底色，提示框显示“无数据”；编码模式的明细同时显示地区名称与原始编码。</p>
  </template>
  <template v-if="definition.type==='funnel'">
    <el-form-item label="漏斗对齐"><el-select :model-value="style.funnelAlign || 'center'" @change="v=>style.funnelAlign=v"><el-option value="left" label="左对齐"/><el-option value="center" label="居中"/><el-option value="right" label="右对齐"/></el-select></el-form-item>
    <el-form-item label="阶段间距"><el-input-number :model-value="style.funnelGap ?? 0" :min="0" :max="30" :precision="0" @update:model-value="v=>style.funnelGap=v"/></el-form-item>
    <el-form-item label="最小宽度（%）"><el-input-number :model-value="style.funnelMinSize ?? 0" :min="0" :max="Math.min(80,(style.funnelMaxSize ?? 100)-1)" :precision="0" @update:model-value="v=>style.funnelMinSize=v"/></el-form-item>
    <el-form-item label="最大宽度（%）"><el-input-number :model-value="style.funnelMaxSize ?? 100" :min="Math.max(20,(style.funnelMinSize ?? 0)+1)" :max="100" :precision="0" @update:model-value="v=>style.funnelMaxSize=v"/></el-form-item>
    <template v-if="style.showLabels!==false">
      <el-form-item label="漏斗标签位置"><el-select :model-value="style.funnelLabelPosition || 'right'" @change="v=>style.funnelLabelPosition=v"><el-option value="inside" label="内部"/><el-option value="left" label="左侧"/><el-option value="right" label="右侧"/></el-select></el-form-item>
      <el-form-item label="漏斗标签内容"><el-select :model-value="style.funnelLabelContent || 'name'" @change="v=>style.funnelLabelContent=v"><el-option value="name" label="阶段名称"/><el-option value="value" label="格式化数值"/><el-option value="nameValue" label="名称与数值"/></el-select></el-form-item>
    </template>
    <p class="hint">阶段顺序沿用查询返回顺序，可在数据页设置排序；不会按数值大小自动重排。标签数值继承逐指标格式。</p>
  </template>
  <template v-if="definition.type==='radar'">
    <el-form-item label="雷达网格形状"><el-select :model-value="style.radarShape || 'polygon'" @change="v=>style.radarShape=v"><el-option value="polygon" label="多边形"/><el-option value="circle" label="圆形"/></el-select></el-form-item>
    <el-form-item label="网格层数"><el-input-number :model-value="style.radarSplitNumber ?? 5" :min="2" :max="10" :precision="0" @update:model-value="v=>style.radarSplitNumber=v"/></el-form-item>
    <el-form-item label="显示网格底色"><el-switch :model-value="style.radarShowSplitArea!==false" @update:model-value="v=>style.radarShowSplitArea=!!v"/></el-form-item>
    <el-form-item label="显示指标名称"><el-switch :model-value="style.radarShowAxisName!==false" @update:model-value="v=>style.radarShowAxisName=!!v"/></el-form-item>
    <el-form-item label="轮廓线宽"><el-input-number :model-value="style.lineWidth ?? 2" :min="1" :max="10" :precision="0" @update:model-value="v=>style.lineWidth=v"/></el-form-item>
    <el-form-item label="填充透明度"><el-slider :model-value="style.areaOpacity ?? 0" :min="0" :max="1" :step="0.01" @update:model-value="v=>style.areaOpacity=Number(v)"/></el-form-item>
    <el-form-item label="显示雷达数据点"><el-switch :model-value="style.showPoints!==false" @update:model-value="v=>style.showPoints=!!v"/></el-form-item>
    <el-form-item v-if="style.showPoints!==false" label="雷达数据点大小"><el-input-number :model-value="style.symbolSize ?? 8" :min="2" :max="20" :precision="0" @update:model-value="v=>style.symbolSize=v"/></el-form-item>
    <p class="hint">各指标坐标范围按当前结果独立计算，包含负值；这不等同于评分归一化。不同量纲的比较需先明确业务口径。</p>
  </template>
  <template v-if="definition.type==='scatter'">
    <el-form-item label="散点形状"><el-select :model-value="style.scatterSymbol || 'circle'" @change="v=>style.scatterSymbol=v"><el-option value="circle" label="圆形"/><el-option value="rect" label="方形"/><el-option value="roundRect" label="圆角方形"/><el-option value="triangle" label="三角形"/><el-option value="diamond" label="菱形"/></el-select></el-form-item>
    <el-form-item label="散点大小"><el-input-number :model-value="style.scatterSize ?? 10" :min="2" :max="60" :precision="0" @update:model-value="v=>style.scatterSize=v"/></el-form-item>
    <el-form-item label="散点透明度"><el-slider :model-value="style.scatterOpacity ?? 0.8" :min="0.05" :max="1" :step="0.05" @update:model-value="v=>style.scatterOpacity=Number(v)"/></el-form-item>
    <el-form-item label="散点描边宽度"><el-input-number :model-value="style.scatterBorderWidth ?? 0" :min="0" :max="5" :precision="0" @update:model-value="v=>style.scatterBorderWidth=v"/></el-form-item>
    <el-form-item v-if="(style.scatterBorderWidth ?? 0)>0" label="散点描边颜色"><el-color-picker :model-value="style.scatterBorderColor || '#FFFFFF'" @update:model-value="v=>style.scatterBorderColor=v || undefined"/></el-form-item>
    <el-form-item v-if="style.showLabels" label="散点标签内容"><el-select :model-value="style.scatterLabelContent || 'category'" @change="v=>style.scatterLabelContent=v"><el-option value="category" label="分类名称 / 点序号"/><el-option value="coordinates" label="格式化 X、Y 数值"/></el-select></el-form-item>
    <p class="hint">点大小为固定样式，不代表第三个指标；密集分布建议减小点尺寸和透明度。提示框保留点标识及 X、Y 原值的格式化展示。</p>
  </template>
  <template v-if="definition.type==='heatmap'">
    <el-form-item label="低值颜色"><el-color-picker :model-value="style.heatmapLowColor || '#E8F3FF'" @update:model-value="v=>style.heatmapLowColor=v || undefined"/></el-form-item>
    <el-form-item label="高值颜色"><el-color-picker :model-value="style.heatmapHighColor || '#2864DC'" @update:model-value="v=>style.heatmapHighColor=v || undefined"/></el-form-item>
    <el-form-item label="显示色阶"><el-switch :model-value="style.heatmapShowScale!==false" @update:model-value="v=>style.heatmapShowScale=!!v"/></el-form-item>
    <p class="hint">色阶按当前查询结果中的指标最小值和最大值映射。</p>
  </template>
  <template v-if="definition.type==='treemap'">
    <el-form-item label="初始层级"><el-input-number :model-value="style.treemapLeafDepth ?? 1" :min="1" :max="Math.max(1,definition.query.dimensions.length)" :precision="0" @update:model-value="v=>style.treemapLeafDepth=v"/></el-form-item>
    <el-form-item label="显示层级导航"><el-switch :model-value="style.treemapShowBreadcrumb!==false" @update:model-value="v=>style.treemapShowBreadcrumb=!!v"/></el-form-item>
    <p class="hint">需要配置两个维度才能展示父级和子级分类。</p>
  </template>
  <template v-if="definition.type==='sankey'">
    <el-form-item label="节点间距"><el-input-number :model-value="style.sankeyNodeGap ?? 10" :min="0" :max="40" :precision="0" @update:model-value="v=>style.sankeyNodeGap=v"/></el-form-item>
    <el-form-item label="连线弯曲度"><el-slider :model-value="style.sankeyCurveness ?? 0.5" :min="0" :max="1" :step="0.05" @update:model-value="v=>style.sankeyCurveness=Number(v)"/></el-form-item>
    <p class="hint">流向由查询中的第一个维度指向第二个维度；同一来源和目标的流量会合并。</p>
  </template>
  <template v-if="definition.type==='boxplot'">
    <el-form-item label="横向箱线图"><el-switch :model-value="!!style.boxplotHorizontal" @update:model-value="v=>style.boxplotHorizontal=!!v"/></el-form-item>
    <el-form-item label="显示异常值"><el-switch :model-value="style.boxplotOutliers!==false" @update:model-value="v=>style.boxplotOutliers=!!v"/></el-form-item>
    <template v-if="style.boxplotOutliers!==false">
      <el-form-item label="异常值判定倍数"><el-input-number :model-value="style.boxplotOutlierIqrMultiplier??1.5" :min="1" :max="3" :step="0.1" :precision="1" @update:model-value="v=>style.boxplotOutlierIqrMultiplier=v"/></el-form-item>
      <el-form-item label="标记形状"><el-select :model-value="style.boxplotOutlierSymbol||'circle'" @change="v=>style.boxplotOutlierSymbol=v"><el-option value="circle" label="圆形"/><el-option value="diamond" label="菱形"/><el-option value="triangle" label="三角形"/><el-option value="rect" label="方形"/></el-select></el-form-item>
      <el-form-item label="标记大小"><el-input-number :model-value="style.boxplotOutlierSize??8" :min="4" :max="20" :precision="0" @update:model-value="v=>style.boxplotOutlierSize=v"/></el-form-item>
      <el-form-item label="标记颜色"><el-color-picker :model-value="style.boxplotOutlierColor||'#E5484D'" @update:model-value="v=>style.boxplotOutlierColor=v||undefined"/></el-form-item>
    </template>
    <p class="hint">须线取 1.5×四分位距范围内的极值；范围外样本以散点标记。可调整 IQR 倍数，便于匹配业务异常口径。</p>
  </template>
  <template v-if="definition.type==='histogram'">
    <el-form-item label="分箱数量"><el-input-number :model-value="style.histogramBins ?? 0" :min="0" :max="50" :precision="0" @update:model-value="v=>style.histogramBins=v"/></el-form-item>
    <p class="hint">设置为 0 时按样本数平方根自动计算，最多 20 箱；手动设置范围为 1～50。</p>
  </template>
  <template v-if="definition.type==='waterfall'">
    <el-form-item label="显示累计总计"><el-switch :model-value="!!style.waterfallShowTotal" @update:model-value="v=>style.waterfallShowTotal=!!v"/></el-form-item>
    <p class="hint">累计值按查询结果顺序计算；建议在数据页明确设置排序。</p>
  </template>
</template>
<script setup lang="ts">
import {computed} from 'vue'
import {mapPieceColor} from '@/utils/biMapPreview'
import type {ChartDefinition} from '@/api/bi/chart'
const props=defineProps<{definition:ChartDefinition}>()
const style=computed(()=>props.definition.style!)
function pieceLabel(i:number){const a=style.value.mapBreaks || [];return i===0?'第1段：< '+a[0]:i===a.length?'第'+(i+1)+'段：≥ '+a[i-1]:'第'+(i+1)+'段：'+a[i-1]+' ≤ 值 < '+a[i]}
function setPiece(i:number,value:string|null){const a=Array.from({length:(style.value.mapBreaks?.length || 0)+1},(_,n)=>style.value.mapPieceColors?.[n] || null);a[i]=value || null;style.value.mapPieceColors=a}
function setMapMode(value:'continuous'|'piecewise'){style.value.mapColorMode=value;if(value==='piecewise' && !style.value.mapBreaks?.length)style.value.mapBreaks=[1000,10000]}
function setBreak(i:number,value:number|undefined){if(value!=null && style.value.mapBreaks)style.value.mapBreaks[i]=value;style.value.mapPieceColors=undefined}
function removeBreak(i:number){style.value.mapBreaks?.splice(i,1);style.value.mapPieceColors=undefined}
function addBreak(){const a=style.value.mapBreaks || [];style.value.mapBreaks=[...a,(a.at(-1) || 0)+1000];style.value.mapPieceColors=undefined}
</script>
<style scoped>.hint{font-size:12px;color:var(--bi-muted,#667085);line-height:1.7;margin:8px 0 18px}</style>
