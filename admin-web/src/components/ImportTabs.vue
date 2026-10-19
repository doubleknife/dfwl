<script setup>
const props = defineProps({ modelValue: { type: String, required: true }, label: { type: String, required: true }, items: { type: Array, required: true } });
const emit = defineEmits(['update:modelValue']);
function onKeydown(event, index) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? props.items.length - 1
    : (index + (event.key === 'ArrowRight' ? 1 : -1) + props.items.length) % props.items.length;
  emit('update:modelValue', props.items[next].key);
  event.currentTarget.parentElement.querySelectorAll('[role="tab"]')[next]?.focus();
}
</script>

<template>
  <div class="import-tabs" role="tablist" :aria-label="label">
    <button v-for="(item, index) in items" :key="item.key" type="button" role="tab"
      :aria-selected="modelValue === item.key" :tabindex="modelValue === item.key ? 0 : -1"
      @click="emit('update:modelValue', item.key)" @keydown="onKeydown($event, index)">{{ item.label }}</button>
  </div>
</template>

<style scoped>
.import-tabs { display: flex; gap: 6px; align-items: end; margin: 18px 0; border-bottom: 1px solid var(--ui-border); }
.import-tabs button { position: relative; min-height: 40px; padding: 8px 18px; border: 1px solid transparent; border-bottom: 3px solid transparent; border-radius: 8px 8px 0 0; background: transparent; color: var(--ui-text-secondary); font-weight: 500; }
.import-tabs button:hover { background: #f0f8f4; color: var(--ui-primary); }
.import-tabs button[aria-selected="true"] { background: #e7f3ed; color: var(--ui-primary); border-color: #cce4d7; border-bottom-color: var(--ui-primary); font-weight: 700; }
.import-tabs button:focus-visible { outline: 2px solid var(--ui-primary); outline-offset: 2px; }
</style>
