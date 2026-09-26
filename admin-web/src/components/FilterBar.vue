<script setup>
defineProps({ label: { type: String, default: '筛选条件' } });
</script>

<template><section class="ui-filter-bar" :aria-label="label"><slot /></section></template>
