<script setup>
defineProps({ message: { type: String, required: true } });
defineEmits(['retry']);
</script>

<template><div class="ui-error" role="alert"><span>{{ message }}</span><button type="button" @click="$emit('retry')">重试</button></div></template>
