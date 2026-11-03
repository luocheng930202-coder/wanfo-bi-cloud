<template>
  <el-collapse class="mb8"><el-collapse-item title="看板主题与样式" name="appearance">
    <div class="preset-row">
      <button v-for="pack in appearancePacks" :key="pack.id" type="button" class="preset-chip" :class="{active:isActivePack(pack)}" :style="{ '--swatch': pack.swatch }" @click="applyPack(pack)">
        <span class="swatch" aria-hidden="true"/>{{ pack.label }}
      </button>
    </div>
    <div class="appearance-fields">
      <el-form-item label="视觉风格"><el-select :model-value="value.visualPreset || 'standard'" @change="changePreset"><el-option label="基础" value="standard"/><el-option label="科技蓝 · 驾驶舱" value="tech-blue-v1"/><el-option label="全息冰蓝 · 光效" value="hologram-ice-v1"/></el-select></el-form-item>
      <el-form-item v-if="value.visualPreset==='hologram-ice-v1'" class="glow-control" label="光效强度"><el-slider :model-value="value.glowIntensity ?? 55" :min="0" :max="100" :step="5" show-input aria-label="光效强度" @update:model-value="v=>update('glowIntensity',v)"/><small>0 关闭光晕；同时作用于标题、面板、指标和三维泛光。静态场景不持续刷新。</small></el-form-item>
      <el-form-item label="主题"><el-select :model-value="value.theme" @change="changeTheme"><el-option label="浅色" value="light"/><el-option label="深色" value="dark"/></el-select></el-form-item>
      <el-form-item v-for="field in colors" :key="field.key" :label="field.label"><el-color-picker :model-value="value[field.key]" color-format="hex" @update:model-value="(color:string|null) => color && update(field.key,color)"/></el-form-item>
      <el-form-item label="图表配色"><el-select :model-value="value.palette" @change="(v:unknown) => update('palette',v)"><el-option label="商务" value="business"/><el-option label="海洋" value="ocean"/><el-option label="暖色" value="warm"/></el-select></el-form-item>
      <el-form-item label="标题字号"><el-input-number :model-value="value.titleSize" :min="12" :max="24" :precision="0" @change="(v:unknown) => update('titleSize',v)"/></el-form-item>
      <el-form-item label="卡片圆角"><el-input-number :model-value="value.radius" :min="0" :max="24" :precision="0" @change="(v:unknown) => update('radius',v)"/></el-form-item>
      <el-form-item label="数值最大小数位"><el-input-number :model-value="value.decimals" :min="0" :max="6" :precision="0" @change="(v:unknown) => update('decimals',v)"/></el-form-item>
      <el-form-item label="数字千分位"><el-switch :model-value="value.grouping" @change="(v:unknown) => update('grouping',v)"/></el-form-item>
    </div>
    <div class="bg-image-block">
      <div class="bg-image-head"><strong>画布背景图</strong><span>覆盖在背景色之上，支持 512KB 内 PNG/JPEG/WebP/GIF/SVG</span></div>
      <div class="bg-image-actions">
        <label class="upload-button"><el-icon><Upload /></el-icon>上传背景图<input type="file" :accept="imageAccept" :disabled="busy" @change="uploadBackground"/></label>
        <el-button :disabled="busy || !value.backgroundImage" @click="clearBackground">清除背景图</el-button>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="true" @close="error=''" class="bg-alert"/>
      <img v-if="value.backgroundImage" :src="value.backgroundImage" alt="画布背景图预览" class="bg-preview"/>
    </div>
    <span class="format-note">快捷主题套装可一键套用；数字格式仅影响显示，不改变原始数据、聚合与权限。背景图随看板草稿保存。</span>
  </el-collapse-item></el-collapse>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import type { DashboardAppearance } from '@/api/bi/dashboard'
import { defaultAppearance,hologramAppearance } from '@/utils/biPresentation'
import { IMAGE_ACCEPT, readImageFileAsDataUrl } from '@/utils/biImageDataUrl'
type Pack = {id:string;label:string;swatch:string;appearance:DashboardAppearance}
const props=defineProps<{modelValue?:DashboardAppearance}>()
const emit=defineEmits<{(event:'update:modelValue',value:DashboardAppearance):void}>()
const value=computed(()=>props.modelValue || defaultAppearance())
const colors=[{key:'background',label:'画布背景'},{key:'cardBackground',label:'卡片背景'},{key:'textColor',label:'文字颜色'},{key:'borderColor',label:'边框颜色'}] as const
const imageAccept=IMAGE_ACCEPT
const busy=ref(false),error=ref('')
const appearancePacks:Pack[]=[
  {id:'hologram-ice',label:'全息冰蓝',swatch:'#42E9FF',appearance:hologramAppearance()},
  {id:'light-business',label:'浅色商务',swatch:'#409EFF',appearance:{...defaultAppearance('light'),palette:'business',titleSize:14,radius:6,visualPreset:'standard'}},
  {id:'light-ocean',label:'浅色海洋',swatch:'#38BDF8',appearance:{theme:'light',background:'#F1F5F9',cardBackground:'#FFFFFF',textColor:'#20324D',borderColor:'#D7E2F0',palette:'ocean',titleSize:16,radius:10,decimals:2,grouping:true,visualPreset:'standard'}},
  {id:'dark-ops',label:'深色运维',swatch:'#111827',appearance:{...defaultAppearance('dark'),palette:'business',titleSize:14,radius:4,visualPreset:'standard'}},
  {id:'dark-ocean',label:'深色驾驶舱',swatch:'#0B1426',appearance:{theme:'dark',background:'#0B1426',cardBackground:'#14243B',textColor:'#E7F0FF',borderColor:'#284361',palette:'ocean',titleSize:18,radius:8,decimals:2,grouping:true,visualPreset:'standard'}},
  {id:'tech-blue',label:'科技蓝',swatch:'#26C5EE',appearance:{theme:'dark',background:'#06121F',cardBackground:'#0A2032',textColor:'#DDF5FF',borderColor:'#20546B',palette:'ocean',titleSize:18,radius:4,decimals:2,grouping:true,visualPreset:'tech-blue-v1'}},
]
function update(key:keyof DashboardAppearance,v:unknown){if(v!==undefined && v!==null)emit('update:modelValue',{...value.value,[key]:v})}
function changePreset(visualPreset:NonNullable<DashboardAppearance['visualPreset']>){
  const colors=visualPreset==='hologram-ice-v1'?hologramAppearance():visualPreset==='tech-blue-v1'?{theme:'dark' as const,background:'#06121F',cardBackground:'#0A2032',textColor:'#DDF5FF',borderColor:'#20546B',palette:'ocean' as const,titleSize:18,radius:4}:defaultAppearance(value.value.theme)
  emit('update:modelValue',{...value.value,...colors,visualPreset,decimals:value.value.decimals,grouping:value.value.grouping,backgroundImage:value.value.backgroundImage})
}
function changeTheme(theme:'light'|'dark'){
  const d=defaultAppearance(theme)
  emit('update:modelValue',{...value.value,theme,background:d.background,cardBackground:d.cardBackground,textColor:d.textColor,borderColor:d.borderColor,backgroundImage:value.value.backgroundImage})
}
function applyPack(pack:Pack){
  emit('update:modelValue',{...pack.appearance,decimals:value.value.decimals,grouping:value.value.grouping,backgroundImage:value.value.backgroundImage})
}
function isActivePack(pack:Pack){
  const a=value.value
  return a.theme===pack.appearance.theme && a.background===pack.appearance.background && a.cardBackground===pack.appearance.cardBackground
    && a.palette===pack.appearance.palette && (a.visualPreset||'standard')===(pack.appearance.visualPreset||'standard')
}
function clearBackground(){emit('update:modelValue',{...value.value,backgroundImage:undefined});error.value=''}
async function uploadBackground(event:Event){
  const input=event.target as HTMLInputElement,file=input.files?.[0];input.value=''
  if(!file || busy.value)return
  busy.value=true;error.value=''
  try{
    const image=await readImageFileAsDataUrl(file)
    const next={...value.value,backgroundImage:image}
    if(JSON.stringify(next).length>1048576)throw Error('看板外观超过体积限制，请压缩背景图后再试')
    emit('update:modelValue',next)
  }catch(e){error.value=e instanceof Error?e.message:'背景图上传失败'}
  finally{busy.value=false}
}
</script>
<style scoped>
.preset-row{display:flex;flex-wrap:wrap;gap:8px;margin:0 0 14px}
.preset-chip{display:inline-flex;align-items:center;gap:7px;border:1px solid #e4e7ed;background:#fff;border-radius:999px;padding:5px 10px 5px 6px;font:inherit;font-size:12px;color:#344563;cursor:pointer}
.preset-chip.active{border-color:#3563e9;background:#edf2ff;color:#3563e9}
.swatch{width:14px;height:14px;border-radius:50%;background:var(--swatch);box-shadow:inset 0 0 0 1px #00000018}
.appearance-fields{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:0 16px}
.bg-image-block{margin:8px 0 12px;padding:12px;border:1px solid #eef1f6;border-radius:8px;background:#fafbfd}
.bg-image-head{display:flex;flex-direction:column;gap:2px;margin-bottom:10px}
.bg-image-head strong{font-size:13px;color:#344563}
.bg-image-head span{font-size:11px;color:#98a2b3;line-height:1.5}
.bg-image-actions{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
.upload-button{position:relative;display:inline-flex;align-items:center;gap:6px;border:1px dashed #bdccef;border-radius:6px;padding:8px 12px;color:#3563e9;font-size:12px;cursor:pointer;background:#fff}
.upload-button input{position:absolute;inset:0;opacity:0;cursor:pointer}
.bg-preview{display:block;width:100%;max-height:140px;object-fit:contain;margin-top:10px;border-radius:6px;background:#eef2f7}
.bg-alert{margin-top:8px}
.format-note{color:#606266;font-size:12px}
</style>

<style scoped>.glow-control{grid-column:1/-1}.glow-control :deep(.el-form-item__content){display:block}.glow-control small{display:block;margin-top:6px;color:#667085;font-size:12px;line-height:1.5}</style>
