<script setup>
defineProps({
  value: { type: [String, Number, Boolean], default: '' },
  tone: { type: String, default: 'neutral' }
});
</script>

<template>
  <span class="badge" :class="`badge-${tone}`">{{ value }}</span>
</template>
