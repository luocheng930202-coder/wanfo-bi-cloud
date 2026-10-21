<template>
  <section v-if="definition.type==='table'" aria-label="明细表样式">
    <el-divider content-position="left">表格外观</el-divider>
    <el-form-item label="斑马纹"><el-switch :model-value="!!style.tableStripe" @update:model-value="v=>style.tableStripe=!!v"/></el-form-item>
    <el-form-item label="显示边框"><el-switch :model-value="style.tableBorder!==false" @update:model-value="v=>style.tableBorder=!!v"/></el-form-item>
    <el-form-item label="最小行高"><el-input-number :model-value="style.tableRowHeight || 32" :min="28" :max="80" :precision="0" @update:model-value="v=>style.tableRowHeight=v"/></el-form-item>
    <el-form-item label="空值显示"><el-input :model-value="style.tableNullText" maxlength="20" placeholder="默认沿用指标格式" @update:model-value="v=>style.tableNullText=v"/><el-button link @click="style.tableNullText=undefined">恢复默认</el-button></el-form-item>
    <el-form-item label="表头背景"><el-color-picker :model-value="style.tableHeaderBackground || '#F5F7FA'" @update:model-value="v=>style.tableHeaderBackground=v || undefined"/></el-form-item>
    <el-form-item label="表头文字"><el-color-picker :model-value="style.tableHeaderColor || '#475467'" @update:model-value="v=>style.tableHeaderColor=v || undefined"/></el-form-item>
    <el-divider content-position="left">逐列设置</el-divider>
    <el-form-item label="配置列"><el-select v-model="selected" aria-label="配置列"><el-option v-for="o in outputs" :key="o.key" :value="o.key" :label="labels[o.key] || o.key"/></el-select></el-form-item>
    <template v-if="selected">
      <el-form-item label="列宽（像素）"><el-input-number :model-value="entry?.width" :min="entry?.width==null?undefined:60" :max="800" :precision="0" placeholder="自动" @update:model-value="v=>patch({width:v})"/><el-button link @click="patch({width:undefined})">自动列宽</el-button></el-form-item>
      <el-form-item label="列对齐"><el-select :model-value="entry?.align || (current?.metric?'right':'left')" @change="v=>patch({align:v})"><el-option value="left" label="左对齐"/><el-option value="center" label="居中"/><el-option value="right" label="右对齐"/></el-select></el-form-item>
      <el-form-item label="固定列"><el-select :model-value="entry?.fixed || 'none'" @change="v=>patch({fixed:v})"><el-option value="none" label="不固定"/><el-option value="left" label="左侧"/><el-option value="right" label="右侧"/></el-select></el-form-item>
      <el-form-item label="自动换行"><el-switch :model-value="!!entry?.wrap" @update:model-value="v=>patch({wrap:!!v})"/></el-form-item>
      <template v-if="current?.metric">
        <h4>条件背景色</h4><p class="hint">按原始指标数值比较，第一条命中生效。单元格背景优先于斑马纹，不影响文字阈值颜色。</p>
        <div v-for="(rule,i) in entry?.backgroundRules || []" :key="i" class="rule">
          <el-select v-model="rule.op" aria-label="背景比较"><el-option v-for="(label,op) in comparisons" :key="op" :value="op" :label="label"/></el-select>
          <el-input v-model="rule.value" maxlength="100" aria-label="背景阈值" placeholder="原始数值"/><el-color-picker v-model="rule.color" aria-label="背景颜色"/><el-button link @click="removeRule(i)">删除</el-button>
        </div>
        <el-button :disabled="(entry?.backgroundRules?.length || 0)>=8" @click="patch({backgroundRules:[...(entry?.backgroundRules || []),{op:'GTE',value:'0',color:'#DCFCE7'}]})">添加背景规则</el-button>
      </template>
      <el-button link @click="reset">恢复当前列默认</el-button>
    </template>
    <p class="hint">列设置绑定输出字段与聚合；移除或替换字段后自动清理。换行内容可撑高行高，固定列在横向滚动时生效；空值文本只替换 null，不替换零或空字符串。</p>
  </section>
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {ChartDefinition,TableColumnStyle} from '@/api/bi/chart'
import {tableOutputs,retainTableColumns} from '@/utils/biTablePresentation'
const props=defineProps<{definition:ChartDefinition;labels:Record<string,string>}>()
const style=computed(()=>props.definition.style!)
const outputs=computed(()=>tableOutputs(props.definition)),selected=ref('')
watch(outputs,(now,old)=>{retainTableColumns(style.value,now,old || []);if(!now.some(o=>o.key===selected.value))selected.value=now[0]?.key || ''},{immediate:true})
const current=computed(()=>outputs.value.find(o=>o.key===selected.value))
const entry=computed(()=>style.value.tableColumns?.find(c=>c.key===selected.value))
function patch(p:Partial<TableColumnStyle>){if(!selected.value)return;const values=style.value.tableColumns || [];style.value.tableColumns=[...values.filter(c=>c.key!==selected.value),{...entry.value,key:selected.value,...p}]}
function reset(){style.value.tableColumns=style.value.tableColumns?.filter(c=>c.key!==selected.value)}
function removeRule(i:number){patch({backgroundRules:entry.value?.backgroundRules?.filter((_,n)=>n!==i)})}
const comparisons={GT:'大于',GTE:'大于等于',LT:'小于',LTE:'小于等于',EQ:'等于',NE:'不等于'}
</script>
<style scoped>.hint{font-size:12px;color:var(--bi-muted,#667085);line-height:1.7}.rule{display:flex;gap:6px;align-items:center;margin-bottom:8px}.rule .el-select{width:110px;flex-shrink:0}.rule .el-input{min-width:80px}</style>
