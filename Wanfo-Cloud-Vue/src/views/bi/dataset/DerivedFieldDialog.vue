<template>
  <el-dialog :model-value="visible" :title="kind==='group'?'新建 / 编辑分组字段':'新建 / 编辑计算字段'" width="min(720px, calc(100vw - 32px))" :close-on-click-modal="false" @update:model-value="$emit('update:visible',$event)" @open="reset">
    <el-form label-position="top"><div class="field-names"><el-form-item label="字段标识" required><el-input v-model="form.originName" maxlength="64" placeholder="例如：meal_cost，使用字母数字下划线"/></el-form-item><el-form-item label="显示名称"><el-input v-model="form.showName" maxlength="200" placeholder="例如：人均餐费"/></el-form-item></div>
      <template v-if="kind==='calc'"><el-form-item label="角色"><el-radio-group v-model="form.dimFlag"><el-radio value="0">指标</el-radio><el-radio value="1">维度</el-radio></el-radio-group></el-form-item><el-form-item label="表达式" required><el-input v-model="form.formula" type="textarea" :rows="5" maxlength="4000" placeholder="例如 ROUND([amount] / NULLIF([qty], 0), 2)"/></el-form-item><p class="hint">点击字段插入引用。支持四则运算、ROUND、ABS、COALESCE、NULLIF、TRIM、CASE 等安全函数；可引用前序派生字段。</p><div class="reference-fields"><el-button v-for="field in fields" :key="field.name" size="small" @click="insert(field.name)">{{ field.label || field.name }}</el-button></div></template>
      <template v-else><el-form-item label="来源字段" required><el-select v-model="form.sourceField" filterable style="width:100%"><el-option v-for="field in fields" :key="field.name" :value="field.name" :label="`${field.label || field.name} · ${field.name}`"/></el-select></el-form-item><p class="hint">文本或数值等值分组，每行填写一个原始值。同一个值只能属于一个分组；未匹配值及空值归入“其他分组”。</p><div v-for="(group,index) in groupRows" :key="index" class="group-row"><el-input v-model="group.label" placeholder="分组名称，如：华东地区" maxlength="100"/><el-input v-model="group.text" type="textarea" :rows="2" placeholder="每行一个值，如：上海（换行）浙江"/><el-button link type="danger" aria-label="删除分组规则" @click="groupRows.splice(index,1)">删除</el-button></div><el-button :disabled="groupRows.length>=10" @click="groupRows.push({label:'',text:''})">＋ 添加分组</el-button><el-form-item label="其他分组名称" style="margin-top:16px"><el-input v-model="form.otherLabel" maxlength="100" placeholder="其他"/></el-form-item></template>
    </el-form><el-alert v-if="error" :title="error" type="error" :closable="false"/><template #footer><el-button @click="$emit('update:visible',false)">取消</el-button><el-button type="primary" @click="apply">加入字段配置</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import {reactive,ref} from 'vue'
import type {DatasetDerivedField} from '@/api/bi/dataset'
const props=defineProps<{visible:boolean;kind:'calc'|'group';fields:{name:string;label?:string}[];initial?:DatasetDerivedField;usedNames?:string[]}>()
const emit=defineEmits<{(e:'update:visible',value:boolean):void;(e:'apply',value:DatasetDerivedField):void}>()
const form=reactive({originName:'',showName:'',dimFlag:'0' as '0'|'1',formula:'',sourceField:'',otherLabel:'其他'}),groupRows=ref([{label:'',text:''}]),error=ref('')
function reset(){Object.assign(form,{originName:'',showName:'',dimFlag:props.kind==='group'?'1':'0',formula:'',sourceField:'',otherLabel:'其他'},props.initial || {});groupRows.value=props.initial?.groups?.map(g=>({label:g.label,text:g.values.join('\n')})) || [{label:'',text:''}];error.value=''}
function insert(name:string){form.formula+=/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)?`[${name}]`:`"${name.replaceAll('"','""')}"`}
function apply(){
  error.value='';if(!/^[A-Za-z_][A-Za-z0-9_]{0,63}$/.test(form.originName) || /^r\d*_/.test(form.originName)){error.value='字段标识须使用字母数字下划线，不能使用 r_、r2_ 等关联字段前缀';return}
  if((props.usedNames || props.fields.map(f=>f.name)).some(name=>name.toLowerCase()===form.originName.toLowerCase())){error.value='字段标识已存在';return}
  if(props.kind==='calc' && !form.formula.trim()){error.value='请填写计算表达式';return}
  const groups=groupRows.value.map(g=>({label:g.label.trim(),values:g.text.split(/\r?\n/).filter(v=>v.length>0)}))
  if(props.kind==='group' && (!form.sourceField || !groups.length || groups.some(g=>!g.label || !g.values.length || g.values.length>50))){error.value='请选择来源字段，并为每个分组填写名称和 1～50 个值';return}
  emit('apply',{kind:props.kind,...form,groups:props.kind==='group'?groups:undefined});emit('update:visible',false)
}
</script>
<style scoped>.field-names{display:grid;grid-template-columns:1fr 1fr;gap:16px}.hint{font-size:12px;line-height:1.8;color:var(--el-text-color-secondary)}.reference-fields{display:flex;flex-wrap:wrap;gap:8px;max-height:130px;overflow:auto}.reference-fields .el-button{margin-left:0}.group-row{display:grid;grid-template-columns:1fr 1.4fr 48px;gap:12px;align-items:start;margin-bottom:12px}@media(max-width:600px){.field-names,.group-row{grid-template-columns:1fr}}</style>
