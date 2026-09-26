<script setup>
defineProps({ title: { type: String, default: '暂无数据' }, description: { type: String, default: '' } });
</script>

<template><div class="ui-empty" role="status"><strong>{{ title }}</strong><p v-if="description">{{ description }}</p><slot /></div></template>
