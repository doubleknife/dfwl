<script setup>
import { computed } from 'vue';
import { displayValue } from '../utils/displayLabels.js';
const props = defineProps({ value: { type: [String, Number], default: '' }, tone: { type: String, default: '' } });
const derivedTone = computed(() => {
  if (props.tone) return props.tone;
  if (['COMPLETED', 'APPROVED', 'ACTIVE', 'AVAILABLE'].includes(props.value)) return 'success';
  if (['PENDING', 'UNPUBLISHED', 'APPROVAL_RESERVED', 'IN_TRANSIT'].includes(props.value)) return 'warning';
  if (['VOIDED', 'CANCELLED', 'REJECTED', 'CLAIMED'].includes(props.value)) return 'danger';
  return 'info';
});
</script>

<template><span class="ui-status" :class="`ui-status--${derivedTone}`">{{ displayValue('status', value) }}</span></template>
