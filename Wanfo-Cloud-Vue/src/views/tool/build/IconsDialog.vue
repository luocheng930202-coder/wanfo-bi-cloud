<template>
  <div class="icon-dialog">
    <el-dialog v-model="value" width="980px" :close-on-click-modal="false" :modal-append-to-body="false" @open="onOpen" @close="onClose">
      <template #header>
        选择图标
        <el-input v-model="key" size="small" :style="{ width: '260px', marginLeft: '12px' }" placeholder="请输入图标名称" prefix-icon="Search" clearable />
      </template>
      <el-tabs v-model="tab">
        <el-tab-pane :label="`Element Plus（${elementPlusIconNames.length}）`" name="ep">
          <ul class="icon-ul">
            <li v-for="icon in epFiltered" :key="icon" :class="active === icon ? 'active-item' : ''" @click="onSelect(icon)">
              <div>
                <el-icon :size="30"><component :is="icon" /></el-icon>
                <div>{{ icon }}</div>
              </div>
            </li>
          </ul>
        </el-tab-pane>
        <el-tab-pane :label="`项目 SVG（${svgIconNames.length}）`" name="svg">
          <ul class="icon-ul">
            <li v-for="icon in svgFiltered" :key="icon" :class="active === icon ? 'active-item' : ''" @click="onSelectSvg(icon)">
              <div>
                <svg-icon :icon-class="icon" style="width:30px;height:30px" />
                <div>{{ icon }}</div>
              </div>
            </li>
          </ul>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  elementPlusIconNames,
  svgIconNames,
  filterElementPlusIcons,
  filterSvgIcons
} from '@/utils/iconRegistry'

const key = ref('')
const tab = ref<'ep' | 'svg'>('ep')
const active = ref('')
const emit = defineEmits(['select'])
const value = defineModel<boolean>()

const epFiltered = computed(() => filterElementPlusIcons(key.value))
const svgFiltered = computed(() => filterSvgIcons(key.value))

function onOpen(): void { }
function onClose(): void { }
function onSelect(icon: string): void {
  active.value = icon
  emit('select', icon)
  if (value.value !== undefined) value.value = false
}
function onSelectSvg(icon: string): void {
  active.value = icon
  emit('select', icon)
  if (value.value !== undefined) value.value = false
}

watch(key, () => { active.value = '' })
</script>
<style lang="scss" scoped>
.icon-ul {
  margin: 0;
  padding: 0;
  font-size: 0;
  max-height: 60vh;
  overflow: auto;
  li {
    list-style-type: none;
    text-align: center;
    font-size: 14px;
    display: inline-flex;
    width: 16.66%;
    box-sizing: border-box;
    height: 108px;
    padding: 6px;
    cursor: pointer;
    overflow: hidden;
    align-items: center;
    justify-content: center;
    &:hover { background: #f2f2f2; }
    &.active-item { background: #e1f3fb; color: #7a6df0; }
  }
}
.icon-dialog :deep(.el-dialog) {
  border-radius: 8px;
  margin-top: 4vh !important;
  max-height: 92vh;
  .el-dialog__body { padding-top: 0; }
}
</style>
