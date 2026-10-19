<script setup>
defineProps({ items: { type: Array, required: true }, label: { type: String, default: '业务汇总' } });
</script>

<template>
  <section class="ui-summary-cards" :aria-label="label">
    <div v-for="item in items" :key="item.label" class="ui-summary-card">
      <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
      <small v-if="item.description">{{ item.description }}</small>
    </div>
  </section>
</template>
