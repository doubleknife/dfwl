<script setup>
const props = defineProps({ pageNo: { type: Number, default: 1 }, pageSize: { type: Number, default: 20 }, total: { type: Number, default: 0 } });
const emit = defineEmits(['change']);
function move(page) { if (page >= 1 && page <= Math.max(1, Math.ceil(props.total / props.pageSize))) emit('change', page); }
</script>

<template>
  <nav v-if="total > 0" class="ui-pagination" aria-label="分页">
    <span>共 {{ total }} 条 · 第 {{ pageNo }} / {{ Math.max(1, Math.ceil(total / pageSize)) }} 页</span>
    <button type="button" :disabled="pageNo <= 1" @click="move(pageNo - 1)">上一页</button>
    <button type="button" :disabled="pageNo * pageSize >= total" @click="move(pageNo + 1)">下一页</button>
  </nav>
</template>
