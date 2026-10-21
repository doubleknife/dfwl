<script setup>
import { onMounted, onUpdated, onUnmounted, ref } from 'vue';
import { displayValue } from '../utils/displayLabels.js';
import LoadingState from './LoadingState.vue';
import EmptyState from './EmptyState.vue';
import StatusTag from './StatusTag.vue';
defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  emptyText: { type: String, default: '暂无数据' },
  loading: { type: Boolean, default: false }
});
const root = ref(null);
function fitViewport() {
  const element = root.value;
  if (!element?.classList.contains('ui-paged-table')) return;
  const available = window.innerHeight - element.getBoundingClientRect().top - 78;
  element.style.height = `${Math.max(220, available)}px`;
}
onMounted(() => { fitViewport(); window.addEventListener('resize', fitViewport); });
onUpdated(fitViewport);
onUnmounted(() => window.removeEventListener('resize', fitViewport));
</script>

<template>
  <div ref="root" class="table-panel ui-table-wrap" :aria-busy="loading">
    <table v-if="!loading" class="ui-data-table">
      <thead><tr><th v-for="column in columns" :key="column.key" :data-column="column.key" :class="{ 'ui-number': column.numeric || /amount|price|salary|profit|count|total/i.test(column.key), 'ui-action-column': column.key === 'actions' }" scope="col">{{ column.label }}</th></tr></thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="row[rowKey] ?? index">
          <td v-for="column in columns" :key="column.key" :data-column="column.key" :class="{ 'ui-number': column.numeric || /amount|price|salary|profit|count|total/i.test(column.key), 'ui-action-column': column.key === 'actions' }">
            <slot :name="column.key" :row="row" :value="row[column.key]">
              <StatusTag v-if="/(^|_)status$|Status$|^enabled$/.test(column.key)" :value="row[column.key]" />
              <template v-else>{{ displayValue(column.key, row[column.key], row) }}</template>
            </slot>
          </td>
      </tr></tbody>
    </table>
    <LoadingState v-if="loading" />
    <EmptyState v-else-if="!rows.length" :title="emptyText" />
  </div>
</template>
