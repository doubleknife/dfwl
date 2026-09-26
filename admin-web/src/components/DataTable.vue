<script setup>
import { displayValue } from '../utils/displayLabels.js';
defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  emptyText: { type: String, default: '暂无数据' },
  loading: { type: Boolean, default: false }
});
</script>

<template>
  <div class="table-panel ui-table-wrap">
    <table class="ui-data-table">
      <thead><tr><th v-for="column in columns" :key="column.key" :class="{ 'ui-number': column.numeric }" scope="col">{{ column.label }}</th></tr></thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="row[rowKey] ?? index">
          <td v-for="column in columns" :key="column.key" :class="{ 'ui-number': column.numeric }">
            <slot :name="column.key" :row="row" :value="row[column.key]">{{ displayValue(column.key, row[column.key], row) }}</slot>
          </td>
      </tr></tbody>
    </table>
    <div v-if="loading" class="ui-loading" role="status">正在加载…</div>
    <div v-else-if="!rows.length" class="ui-empty" role="status">{{ emptyText }}</div>
  </div>
</template>
