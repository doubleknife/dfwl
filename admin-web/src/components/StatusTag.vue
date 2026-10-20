<script setup>
import { computed } from 'vue';
import { displayValue } from '../utils/displayLabels.js';
import { statusTone } from '../utils/statusTone.js';
const props = defineProps({ value: { type: [String, Number, Boolean], default: '' }, tone: { type: String, default: '' } });
const derivedTone = computed(() => statusTone(props.value, props.tone));
const label = computed(() => props.value === true || props.value === 1 || props.value === '1' ? '启用'
  : props.value === false || props.value === 0 || props.value === '0' ? '停用' : displayValue('status', props.value));
</script>

<template><span class="ui-status" :class="`ui-status--${derivedTone}`">{{ label }}</span></template>
