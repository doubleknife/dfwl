<script setup>
import { watch } from 'vue';
import { apiErrorDialog, showErrorDialog } from '../stores/apiErrorDialog.js';

const props = defineProps({ message: { type: String, required: true }, retryable: { type: Boolean, default: true } });
defineEmits(['retry']);
watch(() => props.message, (message) => {
  if (message && !apiErrorDialog.open) showErrorDialog('操作失败', message);
}, { immediate: true });
</script>

<template><div v-if="retryable" class="ui-error-retry"><button type="button" @click="$emit('retry')">重试</button></div></template>
