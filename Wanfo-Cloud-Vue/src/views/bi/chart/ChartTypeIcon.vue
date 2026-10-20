<template>
  <svg class="chart-type-icon" :class="`chart-type-icon--${type}`" viewBox="0 0 48 32" aria-hidden="true" focusable="false">
    <g v-if="type === 'bar'" fill="currentColor">
      <rect x="6" y="14" width="7" height="14" rx="1" opacity="0.45"/>
      <rect x="17" y="8" width="7" height="20" rx="1"/>
      <rect x="28" y="12" width="7" height="16" rx="1" opacity="0.75"/>
      <rect x="39" y="5" width="7" height="23" rx="1" opacity="0.55"/>
    </g>
    <g v-else-if="type === 'line'" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
      <polyline points="4,24 14,18 24,20 34,10 44,14"/>
      <circle cx="14" cy="18" r="2" fill="currentColor" stroke="none"/>
      <circle cx="24" cy="20" r="2" fill="currentColor" stroke="none"/>
      <circle cx="34" cy="10" r="2" fill="currentColor" stroke="none"/>
    </g>
    <g v-else-if="type === 'area'">
      <path d="M4 26 L14 20 L24 22 L34 12 L44 16 L44 28 L4 28 Z" fill="currentColor" opacity="0.28"/>
      <polyline points="4,24 14,18 24,20 34,10 44,14" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
    </g>
    <g v-else-if="type === 'hbar'" fill="currentColor">
      <rect x="8" y="5" width="28" height="5" rx="1"/>
      <rect x="8" y="13" width="36" height="5" rx="1" opacity="0.75"/>
      <rect x="8" y="21" width="20" height="5" rx="1" opacity="0.45"/>
    </g>
    <g v-else-if="type === 'stack'" fill="currentColor">
      <rect x="8" y="18" width="8" height="10" rx="1" opacity="0.35"/>
      <rect x="8" y="10" width="8" height="8" rx="1" opacity="0.65"/>
      <rect x="20" y="14" width="8" height="14" rx="1" opacity="0.35"/>
      <rect x="20" y="6" width="8" height="8" rx="1"/>
      <rect x="32" y="16" width="8" height="12" rx="1" opacity="0.35"/>
      <rect x="32" y="8" width="8" height="8" rx="1" opacity="0.8"/>
    </g>
    <g v-else-if="type === 'percent'" fill="currentColor">
      <rect x="6" y="10" width="36" height="12" rx="2" opacity="0.25"/>
      <rect x="6" y="10" width="14" height="12" rx="2"/>
      <rect x="20" y="10" width="11" height="12" opacity="0.65"/>
      <rect x="31" y="10" width="11" height="12" opacity="0.4"/>
    </g>
    <g v-else-if="type === 'pie'">
      <circle cx="24" cy="16" r="12" fill="currentColor" opacity="0.22"/>
      <path d="M24 4 A12 12 0 0 1 35 24 L24 16 Z" fill="currentColor"/>
      <path d="M24 16 L35 24 A12 12 0 0 1 13 22 Z" fill="currentColor" opacity="0.55"/>
      <path d="M24 16 L13 22 A12 12 0 0 1 24 4 Z" fill="currentColor" opacity="0.35"/>
    </g>
    <g v-else-if="type === 'ring'">
      <circle cx="24" cy="16" r="12" fill="none" stroke="currentColor" stroke-width="7" opacity="0.22"/>
      <circle cx="24" cy="16" r="12" fill="none" stroke="currentColor" stroke-width="7" stroke-dasharray="28 50" transform="rotate(-90 24 16)"/>
      <circle cx="24" cy="16" r="5" fill="#fff"/>
    </g>
    <g v-else-if="type === 'funnel'" fill="currentColor">
      <path d="M10 6 H38 L32 12 H16 Z" opacity="0.35"/>
      <path d="M14 12 H34 L28 18 H20 Z" opacity="0.6"/>
      <path d="M18 18 H30 L26 26 H22 Z"/>
    </g>
    <g v-else-if="type === 'radar'" fill="currentColor" stroke="currentColor">
      <polygon points="24,6 38,14 34,28 14,28 10,14" fill="none" stroke-width="1.2" opacity="0.35"/>
      <polygon points="24,10 33,15 30,24 18,24 15,15" opacity="0.45"/>
      <circle cx="24" cy="16" r="1.5"/>
    </g>
    <g v-else-if="type === 'scatter'" fill="currentColor">
      <circle cx="12" cy="22" r="2.8" opacity="0.45"/>
      <circle cx="20" cy="14" r="2.8"/>
      <circle cx="28" cy="20" r="2.8" opacity="0.7"/>
      <circle cx="34" cy="10" r="2.8" opacity="0.55"/>
      <circle cx="38" cy="24" r="2.8" opacity="0.35"/>
    </g>
    <g v-else-if="type === 'gauge'">
      <path d="M10 24 A14 14 0 0 1 38 24" fill="none" stroke="currentColor" stroke-width="3" opacity="0.25"/>
      <path d="M10 24 A14 14 0 0 1 32 12" fill="none" stroke="currentColor" stroke-width="3"/>
      <line x1="24" y1="24" x2="30" y2="14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      <circle cx="24" cy="24" r="2.5" fill="currentColor"/>
    </g>
    <g v-else-if="type === 'combo'" fill="currentColor" stroke="currentColor">
      <rect x="8" y="16" width="6" height="10" rx="1" opacity="0.5"/>
      <rect x="18" y="12" width="6" height="14" rx="1" opacity="0.7"/>
      <rect x="28" y="18" width="6" height="8" rx="1" opacity="0.45"/>
      <polyline points="11,14 21,10 31,12 37,8" fill="none" stroke-width="2" stroke-linecap="round"/>
    </g>
    <g v-else-if="type === 'table'" stroke="currentColor" fill="none" stroke-width="1.2">
      <rect x="8" y="6" width="32" height="20" rx="2"/>
      <line x1="8" y1="12" x2="40" y2="12"/>
      <line x1="8" y1="18" x2="40" y2="18"/>
      <line x1="20" y1="6" x2="20" y2="26"/>
      <line x1="30" y1="6" x2="30" y2="26"/>
    </g>
    <g v-else-if="type === 'pivot'" stroke="currentColor" fill="currentColor" stroke-width="1">
      <rect x="8" y="6" width="32" height="20" rx="2" fill="none" stroke-width="1.2"/>
      <rect x="8" y="6" width="10" height="8" opacity="0.35"/>
      <rect x="18" y="6" width="22" height="4" opacity="0.2"/>
      <rect x="18" y="10" width="22" height="4" opacity="0.15"/>
      <line x1="8" y1="14" x2="40" y2="14" stroke-width="1.2" fill="none"/>
      <line x1="18" y1="6" x2="18" y2="26" stroke-width="1.2" fill="none"/>
    </g>
    <g v-else-if="type === 'map'" fill="currentColor" stroke="currentColor" stroke-width="1">
      <path d="M14 8 L22 6 L30 9 L36 7 L38 14 L34 22 L26 26 L16 24 L10 18 Z" opacity="0.25"/>
      <path d="M14 8 L22 6 L30 9 L36 7 L38 14 L34 22 L26 26 L16 24 L10 18 Z" fill="none" stroke-width="1.4"/>
      <circle cx="26" cy="15" r="2.5"/>
    </g>
    <g v-else fill="currentColor">
      <rect x="10" y="8" width="28" height="16" rx="2" opacity="0.3"/>
    </g>
  </svg>
</template>
<script setup lang="ts">
import type { ChartType } from '@/utils/biChartTypes'
defineProps<{ type: ChartType | string }>()
</script>
<style scoped>
.chart-type-icon{display:block;width:100%;height:100%;color:#3370ff}
</style>
