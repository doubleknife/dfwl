<script setup>
defineProps({ title: { type: String, default: '' } });
</script>

<template><section class="ui-section"><h2 v-if="title">{{ title }}</h2><slot /></section></template>
