<script setup>
defineProps({ modelValue: { type: String, required: true } });
const emit = defineEmits(['update:modelValue']);
</script>

<template>
  <label class="ui-import-result-filter">查看结果
    <select :value="modelValue" @change="emit('update:modelValue', $event.target.value)">
      <option value="ALL">全部</option>
      <option value="PASS">通过</option>
      <option value="FAIL">失败</option>
    </select>
  </label>
</template>

<style scoped>
.ui-import-result-filter { display:grid; gap:5px; min-width:126px; color:var(--ui-text-secondary); font-size:13px; font-weight:600; }
.ui-import-result-filter select { min-height:40px; padding:0 12px; border:1px solid var(--ui-border); border-radius:7px; background:#fff; color:var(--ui-text); }
</style>
