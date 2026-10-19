<script setup>
import { displayValue } from '../utils/displayLabels.js';
import LoadingState from './LoadingState.vue';
import EmptyState from './EmptyState.vue';
defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  emptyText: { type: String, default: '暂无数据' },
  loading: { type: Boolean, default: false }
});
</script>

<template>
  <div class="table-panel ui-table-wrap" :aria-busy="loading">
    <table v-if="!loading" class="ui-data-table">
      <thead><tr><th v-for="column in columns" :key="column.key" :data-column="column.key" :class="{ 'ui-number': column.numeric || /amount|price|salary|profit|count|total/i.test(column.key), 'ui-action-column': column.key === 'actions' }" scope="col">{{ column.label }}</th></tr></thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="row[rowKey] ?? index">
          <td v-for="column in columns" :key="column.key" :data-column="column.key" :class="{ 'ui-number': column.numeric || /amount|price|salary|profit|count|total/i.test(column.key), 'ui-action-column': column.key === 'actions' }">
            <slot :name="column.key" :row="row" :value="row[column.key]">{{ displayValue(column.key, row[column.key], row) }}</slot>
          </td>
      </tr></tbody>
    </table>
    <LoadingState v-if="loading" />
    <EmptyState v-else-if="!rows.length" :title="emptyText" />
  </div>
</template>
