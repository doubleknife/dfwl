<script setup>
defineProps({ open: { type: Boolean, default: false }, title: { type: String, default: '确认操作' }, message: { type: String, required: true }, danger: { type: Boolean, default: false }, busy: { type: Boolean, default: false } });
defineEmits(['confirm', 'cancel']);
</script>

<template>
  <div v-if="open" class="ui-overlay">
    <section class="ui-dialog ui-dialog--small" role="alertdialog" aria-modal="true" :aria-label="title">
      <h2>{{ title }}</h2><p>{{ message }}</p>
      <div class="ui-dialog-actions"><button type="button" :disabled="busy" @click="$emit('cancel')">取消</button><button type="button" :class="danger ? 'ui-danger-button' : 'primary'" :disabled="busy" @click="$emit('confirm')">确认</button></div>
    </section>
  </div>
</template>
