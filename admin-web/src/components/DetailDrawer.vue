<script setup>
import { ref, watch } from 'vue';

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  resizable: { type: Boolean, default: false }
});
defineEmits(['close']);
const drawerWidth = ref(null);
const dragging = ref(false);

watch(() => [props.open, props.resizable], ([open, resizable]) => {
  if (!open || !resizable) { drawerWidth.value = null; dragging.value = false; }
});

function resizeTo(clientX) {
  const maximum = Math.max(0, window.innerWidth - 24);
  const minimum = Math.min(460, maximum);
  drawerWidth.value = Math.max(minimum, Math.min(maximum, window.innerWidth - clientX));
}
function startResize(event) {
  if (event.button !== 0) return;
  dragging.value = true;
  event.currentTarget.setPointerCapture(event.pointerId);
  resizeTo(event.clientX);
  event.preventDefault();
}
function moveResize(event) {
  if (dragging.value) resizeTo(event.clientX);
}
function stopResize(event) {
  if (!dragging.value) return;
  dragging.value = false;
  if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
}
function keyResize(event) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
  const current = drawerWidth.value ?? event.currentTarget.closest('.ui-drawer').getBoundingClientRect().width;
  resizeTo(window.innerWidth - current + (event.key === 'ArrowLeft' ? -32 : 32));
  event.preventDefault();
}
</script>

<template>
  <div v-if="open" class="ui-overlay">
    <aside class="ui-drawer" role="dialog" aria-modal="true" :aria-label="title" :style="resizable && drawerWidth !== null ? { width: `${drawerWidth}px` } : null">
      <div v-if="resizable" class="ui-drawer-resize-handle" role="separator" aria-label="调整详情宽度" aria-orientation="vertical" tabindex="0"
        @pointerdown="startResize" @pointermove="moveResize" @pointerup="stopResize" @pointercancel="stopResize" @keydown="keyResize" />
      <header class="ui-dialog-head"><div><h2>{{ title }}</h2><p v-if="$slots.subtitle" class="ui-drawer-subtitle"><slot name="subtitle" /></p></div><button type="button" aria-label="关闭" @click="$emit('close')">×</button></header>
      <div class="ui-drawer-body"><slot /></div>
      <footer v-if="$slots.footer" class="ui-drawer-footer"><slot name="footer" /></footer>
    </aside>
  </div>
</template>

<style scoped>
.ui-drawer-resize-handle { position: absolute; z-index: 5; inset: 20% auto 20% 0; width: 12px; cursor: ew-resize; touch-action: none; }
.ui-drawer-resize-handle::after { content: ''; position: absolute; top: 35%; bottom: 35%; left: 4px; width: 3px; border-radius: 3px; background: #abc7bb; }
.ui-drawer-resize-handle:hover::after, .ui-drawer-resize-handle:focus-visible::after { background: #087563; }
</style>
