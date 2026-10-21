<script setup>
defineProps({ open: { type: Boolean, default: false }, title: { type: String, required: true }, busy: { type: Boolean, default: false }, wide: { type: Boolean, default: false }, fixedHeight: { type: Boolean, default: false } });
defineEmits(['close']);
</script>

<template>
  <div v-if="open" class="ui-overlay">
    <section class="ui-dialog" :class="{ 'ui-dialog--wide': wide, 'ui-dialog--fixed-height': fixedHeight }" role="dialog" aria-modal="true" :aria-label="title">
      <header class="ui-dialog-head"><h2>{{ title }}</h2><button type="button" :disabled="busy" aria-label="关闭" @click="$emit('close')">×</button></header>
      <slot />
    </section>
  </div>
</template>
