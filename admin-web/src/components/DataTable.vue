<script setup>
defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  emptyText: { type: String, default: '暂无数据' }
});
</script>

<template>
  <div class="table-panel">
    <div class="data-table" :style="{ '--cols': columns.length }">
      <div class="table-head" v-for="column in columns" :key="column.key">{{ column.label }}</div>
      <template v-for="(row, index) in rows" :key="row[rowKey] ?? index">
        <div class="table-cell" v-for="column in columns" :key="column.key">
          <slot :name="column.key" :row="row" :value="row[column.key]">
            {{ row[column.key] ?? '-' }}
          </slot>
        </div>
      </template>
    </div>
    <div v-if="!rows.length" class="empty">{{ emptyText }}</div>
  </div>
</template>
