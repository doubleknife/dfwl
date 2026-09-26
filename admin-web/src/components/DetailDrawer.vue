<script setup>
defineProps({ open: { type: Boolean, default: false }, title: { type: String, required: true } });
defineEmits(['close']);
</script>

<template>
  <div v-if="open" class="ui-overlay" @click.self="$emit('close')">
    <aside class="ui-drawer" role="dialog" aria-modal="true" :aria-label="title">
      <header class="ui-dialog-head"><div><h2>{{ title }}</h2><p v-if="$slots.subtitle" class="ui-drawer-subtitle"><slot name="subtitle" /></p></div><button type="button" aria-label="关闭" @click="$emit('close')">×</button></header>
      <div class="ui-drawer-body"><slot /></div>
      <footer v-if="$slots.footer" class="ui-drawer-footer"><slot name="footer" /></footer>
    </aside>
  </div>
</template>
