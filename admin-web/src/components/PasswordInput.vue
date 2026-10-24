<script setup>
import { ref, watch } from 'vue';
defineOptions({ inheritAttrs: false });
const props = defineProps({ modelValue: { type: String, default: '' } });
const emit = defineEmits(['update:modelValue']);
const visible = ref(false);
watch(() => props.modelValue, value => { if (!value) visible.value = false; });
</script>

<template>
  <span class="password-input-wrap">
    <input v-bind="$attrs" :value="modelValue" :type="visible ? 'text' : 'password'" @input="emit('update:modelValue', $event.target.value)" />
    <button type="button" class="password-eye-toggle" :disabled="$attrs.disabled" :aria-pressed="visible" :aria-label="visible ? '隐藏密码' : '显示密码'" @click="visible = !visible">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/><path v-if="visible" d="m3 3 18 18"/></svg>
    </button>
  </span>
</template>

<style scoped>
.password-input-wrap { position: relative; display: block; width: 100%; min-width: 0; }
.password-input-wrap input { width: 100%; box-sizing: border-box; padding-right: 46px; }
.password-eye-toggle { position: absolute; right: 4px; top: 50%; transform: translateY(-50%); display: grid; place-items: center; width: 38px; height: 38px; padding: 9px; color: #9aa7b3; background: transparent; border: 0; }
.password-eye-toggle svg { width: 20px; height: 20px; }
</style>
