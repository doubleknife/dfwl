<script setup>
import { displayValue } from '../utils/displayLabels.js';
defineProps({
  value: { type: [String, Number, Boolean], default: '' },
  tone: { type: String, default: 'neutral' }
});
</script>

<template>
  <span class="badge" :class="`badge-${tone}`">{{ displayValue('status', value) }}</span>
</template>
