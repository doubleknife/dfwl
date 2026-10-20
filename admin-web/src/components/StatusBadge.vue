<script setup>
import { displayValue } from '../utils/displayLabels.js';
import { statusTone } from '../utils/statusTone.js';
const props = defineProps({
  value: { type: [String, Number, Boolean], default: '' },
  tone: { type: String, default: '' }
});
</script>

<template>
  <span class="badge" :class="`badge-${statusTone(props.value, props.tone)}`">{{ value === true || value === 1 || value === '1' ? '启用' : value === false || value === 0 || value === '0' ? '停用' : displayValue('status', value) }}</span>
</template>
