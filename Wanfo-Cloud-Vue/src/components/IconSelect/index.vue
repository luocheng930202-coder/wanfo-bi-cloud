<template>
  <div class="icon-body">
    <el-tabs v-model="activeTab" class="icon-tabs">
      <el-tab-pane :label="`项目 SVG（${svgIconNames.length}）`" name="svg">
        <el-input v-model="iconName" class="icon-search" clearable placeholder="搜索 SVG 图标" @clear="filterIcons" @input="filterIcons" />
        <div class="icon-list">
          <div class="list-container">
            <div v-for="item in iconList" :key="'svg-' + item" class="icon-item-wrapper" @click="selectedIcon(item)">
              <div :class="['icon-item', { active: activeIcon === item }]">
                <svg-icon :icon-class="item" class-name="icon" style="height: 22px;width: 22px;" />
                <span>{{ item }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane :label="`Element Plus（${elementPlusIconNames.length}）`" name="ep">
        <el-input v-model="epQuery" class="icon-search" clearable placeholder="搜索 Element Plus 图标" @clear="filterEp" @input="filterEp" />
        <div class="icon-list ep-grid">
          <div v-for="item in epList" :key="'ep-' + item" :class="['ep-item', { active: activeIcon === encodeElementPlusIcon(item) }]" @click="selectedEp(item)">
            <el-icon :size="22"><component :is="item" /></el-icon>
            <span>{{ item }}</span>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import {
  svgIconNames,
  elementPlusIconNames,
  filterSvgIcons,
  filterElementPlusIcons,
  encodeElementPlusIcon
} from '@/utils/iconRegistry'

defineProps<{ activeIcon?: string }>()

const activeTab = ref<'svg' | 'ep'>('svg')
const iconName = ref('')
const epQuery = ref('')
const iconList = ref([...svgIconNames])
const epList = ref([...elementPlusIconNames])
const emit = defineEmits<{ selected: [name: string] }>()

function filterIcons(): void {
  iconList.value = filterSvgIcons(iconName.value)
}

function filterEp(): void {
  epList.value = filterElementPlusIcons(epQuery.value)
}

function selectedIcon(name: string): void {
  emit('selected', name)
  document.body.click()
}

function selectedEp(name: string): void {
  emit('selected', encodeElementPlusIcon(name))
  document.body.click()
}

function reset(): void {
  iconName.value = ''
  epQuery.value = ''
  iconList.value = [...svgIconNames]
  epList.value = [...elementPlusIconNames]
  activeTab.value = 'svg'
}

defineExpose({ reset })
</script>

<style lang="scss" scoped>
.icon-body {
  width: 100%;
  padding: 4px 6px 10px;
  .icon-tabs :deep(.el-tabs__header) {
    margin-bottom: 8px;
  }
  .icon-search {
    margin-bottom: 8px;
  }
  .icon-list {
    height: 280px;
    overflow: auto;
    .list-container {
      display: flex;
      flex-wrap: wrap;
      .icon-item-wrapper {
        width: calc(100% / 3);
        min-height: 28px;
        line-height: 1.2;
        cursor: pointer;
        display: flex;
        padding: 2px 0;
        .icon-item {
          display: flex;
          align-items: center;
          gap: 4px;
          max-width: 100%;
          padding: 2px 5px;
          &:hover,
          &.active {
            background: #ececec;
            border-radius: 5px;
          }
          span {
            font-size: 12px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }
        }
      }
    }
    &.ep-grid {
      display: flex;
      flex-wrap: wrap;
      align-content: flex-start;
      gap: 4px;
      .ep-item {
        width: calc(20% - 4px);
        min-height: 72px;
        padding: 6px 4px;
        box-sizing: border-box;
        text-align: center;
        cursor: pointer;
        border-radius: 6px;
        font-size: 11px;
        line-height: 1.3;
        word-break: break-all;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 6px;
        &:hover,
        &.active {
          background: #e8f3ff;
          color: #3370ff;
        }
      }
    }
  }
}
</style>
