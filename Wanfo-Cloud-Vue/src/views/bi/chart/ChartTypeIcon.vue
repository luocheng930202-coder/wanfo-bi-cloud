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
    <g v-else-if="type === 'heatmap'" stroke="#fff" stroke-width="1.2">
      <rect x="5" y="4" width="11" height="7" rx="1" fill="currentColor" opacity=".25"/><rect x="17" y="4" width="11" height="7" rx="1" fill="currentColor" opacity=".65"/><rect x="29" y="4" width="13" height="7" rx="1" fill="currentColor"/>
      <rect x="5" y="12" width="11" height="7" rx="1" fill="currentColor" opacity=".55"/><rect x="17" y="12" width="11" height="7" rx="1" fill="currentColor" opacity=".35"/><rect x="29" y="12" width="13" height="7" rx="1" fill="currentColor" opacity=".8"/>
      <rect x="5" y="20" width="11" height="7" rx="1" fill="currentColor" opacity=".85"/><rect x="17" y="20" width="11" height="7" rx="1" fill="currentColor" opacity=".2"/><rect x="29" y="20" width="13" height="7" rx="1" fill="currentColor" opacity=".55"/>
    </g>
    <g v-else-if="type === 'treemap'" fill="currentColor">
      <rect x="5" y="4" width="38" height="24" rx="2" opacity=".18"/><rect x="7" y="6" width="18" height="11" rx="1" opacity=".75"/><rect x="27" y="6" width="14" height="7" rx="1" opacity=".4"/><rect x="27" y="15" width="14" height="11" rx="1"/><rect x="7" y="19" width="18" height="7" rx="1" opacity=".52"/>
    </g>
    <g v-else-if="type === 'sunburst'" fill="currentColor">
      <path d="M23 16 L23 4 A12 12 0 0 1 34.4 12 Z"/><path d="M23 16 L34.4 12 A12 12 0 0 1 30 26 Z" opacity=".55"/><path d="M23 16 L30 26 A12 12 0 0 1 12.3 22 Z" opacity=".8"/><path d="M23 16 L12.3 22 A12 12 0 0 1 23 4 Z" opacity=".35"/>
      <circle cx="23" cy="16" r="5" fill="#fff"/><path d="M23 16 L23 9 A7 7 0 0 1 29.3 13 Z" opacity=".5"/><path d="M23 16 L29.3 13 A7 7 0 0 1 26.5 22 Z"/><path d="M23 16 L26.5 22 A7 7 0 0 1 16.5 18 Z" opacity=".55"/>
    </g>
    <g v-else-if="type === 'sankey'" fill="none" stroke="currentColor" stroke-linecap="round">
      <rect x="5" y="6" width="4" height="20" rx="1" fill="currentColor"/><rect x="39" y="5" width="4" height="8" rx="1" fill="currentColor" opacity=".8"/><rect x="39" y="17" width="4" height="11" rx="1" fill="currentColor" opacity=".5"/>
      <path d="M9 10 C20 10 27 8 39 8" stroke-width="5" opacity=".42"/><path d="M9 18 C21 18 28 22 39 22" stroke-width="7" opacity=".24"/><path d="M9 23 C21 23 28 12 39 11" stroke-width="2.4" opacity=".75"/>
    </g>
    <g v-else-if="type === 'boxplot'" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
      <line x1="24" y1="4" x2="24" y2="9"/><line x1="24" y1="23" x2="24" y2="28"/><line x1="17" y1="4" x2="31" y2="4"/><line x1="17" y1="28" x2="31" y2="28"/><rect x="14" y="9" width="20" height="14" rx="1.5" fill="currentColor" fill-opacity=".18"/><line x1="14" y1="16" x2="34" y2="16" stroke-width="2.8"/>
    </g>
    <g v-else-if="type === 'histogram'" fill="currentColor">
      <rect x="6" y="20" width="7" height="8" rx="1" opacity=".4"/><rect x="14" y="13" width="7" height="15" rx="1" opacity=".65"/><rect x="22" y="6" width="7" height="22" rx="1"/><rect x="30" y="11" width="7" height="17" rx="1" opacity=".78"/><rect x="38" y="18" width="5" height="10" rx="1" opacity=".48"/><path d="M5 29.5 H44" stroke="currentColor" stroke-width="1" opacity=".45"/>
    </g>
    <g v-else-if="type === 'waterfall'" fill="currentColor">
      <rect x="5" y="22" width="7" height="6" rx="1" opacity=".6"/><rect x="14" y="15" width="7" height="7" rx="1" opacity=".85"/><rect x="23" y="15" width="7" height="10" rx="1" opacity=".45"/><rect x="32" y="8" width="7" height="7" rx="1"/><rect x="40" y="8" width="5" height="20" rx="1" opacity=".72"/>
      <path d="M12 22 H14 M21 15 H23 M30 15 H32 M39 8 H40" stroke="currentColor" stroke-width="1.4" opacity=".6"/>
    </g>
    <g v-else-if="type === 'geo-map'" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
      <path d="M6 25 H43 M9 27 V5" opacity=".38"/><path d="M13 21 C18 18 20 13 25 16 S33 22 39 9" stroke-width="2"/><circle cx="13" cy="21" r="2.2" fill="currentColor"/><circle cx="25" cy="16" r="2.2" fill="currentColor"/><circle cx="39" cy="9" r="2.2" fill="currentColor"/>
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
