<script setup>
defineProps({ title: { type: String, required: true }, description: { type: String, default: '' } });
</script>

<template>
  <section class="ui-form-section">
    <header><h2>{{ title }}</h2><p v-if="description" class="muted">{{ description }}</p></header>
    <slot />
  </section>
</template>
