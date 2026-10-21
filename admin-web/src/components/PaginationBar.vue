<script setup>
const props = defineProps({ pageNo: { type: Number, default: 1 }, pageSize: { type: Number, default: 20 }, total: { type: Number, default: 0 }, showSize: { type: Boolean, default: true } });
const emit = defineEmits(['change', 'change-size']);
function resetScroll(event) {
  const nav = event?.currentTarget?.closest('.ui-pagination');
  let sibling = nav?.previousElementSibling;
  let table = null;
  while (sibling && !table) {
    table = sibling.matches?.('.ui-paged-table, .ui-role-scroll, .v3-month-scroll')
      ? sibling : sibling.querySelector?.('.ui-paged-table, .ui-role-scroll, .v3-month-scroll');
    sibling = sibling.previousElementSibling;
  }
  if (table) { table.scrollTop = 0; table.scrollLeft = 0; }
}
function move(page, event) {
  if (page >= 1 && page <= Math.max(1, Math.ceil(props.total / props.pageSize))) {
    resetScroll(event);
    emit('change', page);
  }
}
function changeSize(event) {
  const size = Number(event.target.value);
  if (![10, 20, 50, 100].includes(size) || size === props.pageSize) return;
  resetScroll(event);
  emit('change-size', size);
}
</script>

<template>
  <nav class="ui-pagination" aria-label="分页">
    <span>共 {{ total }} 条 · 第 {{ pageNo }} / {{ Math.max(1, Math.ceil(total / pageSize)) }} 页</span>
    <label v-if="showSize" class="ui-page-size-label">每页 <select aria-label="每页条数" :value="pageSize" @change="changeSize"><option v-for="size in [10, 20, 50, 100]" :key="size" :value="size">{{ size }} 条</option></select></label>
    <button type="button" :disabled="pageNo <= 1" @click="move(pageNo - 1, $event)">上一页</button>
    <button type="button" :disabled="pageNo * pageSize >= total" @click="move(pageNo + 1, $event)">下一页</button>
  </nav>
</template>
