<script setup>
import { ref } from 'vue';

const props = defineProps({
  file: { type: Object, default: null },
  label: { type: String, default: '选择 Excel 文件' },
  accept: { type: String, default: '.xlsx' },
  multiple: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  clearOnSelect: { type: Boolean, default: true }
});
const emit = defineEmits(['select', 'clear']);
const input = ref(null);
function prepare(event) {
  event.target.value = '';
}
function select(event) {
  const files = Array.from(event.target.files || []);
  if (files.length) {
    if (props.clearOnSelect) emit('clear');
    emit('select', props.multiple ? files : files[0]);
  }
}
function clear() {
  if (input.value) input.value.value = '';
  emit('clear');
}
</script>

<template>
  <div class="ui-import-file-picker">
    <span v-if="label" class="ui-import-file-label">{{ label }}</span>
    <div class="ui-import-file-controls">
      <div class="ui-import-file-field" role="status"><span :title="file?.name || ''">{{ file?.name || '未选择文件' }}</span></div>
      <button v-if="file" class="ui-import-file-clear" type="button" aria-label="清除所选文件和预览" title="清除文件" :disabled="disabled || loading" @click="clear">×</button>
      <label v-else class="ui-import-file-choice"><input ref="input" type="file" :accept="accept" :multiple="multiple" :disabled="disabled || loading" @click="prepare" @change="select" /><span>选择文件</span></label>
    </div>
    <span v-if="loading" class="ui-import-file-loading">正在校验…</span>
  </div>
</template>

<style scoped>
.ui-import-file-picker { display:grid; justify-items:start; gap:7px; margin:10px 0; }
.ui-import-file-label { font-weight:600; }
.ui-import-file-controls { display:flex; align-items:center; flex-wrap:nowrap; gap:10px; width:min(100%, 430px); }
.ui-import-file-field { display:flex; align-items:center; flex:1; min-width:0; min-height:40px; padding:0 12px; border:1px solid #cdded5; border-radius:7px; background:#f1f4f2; color:#31594c; }
.ui-import-file-field > span { min-width:0; flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.ui-import-file-choice { position:relative; display:inline-flex; flex:none; width:94px; cursor:pointer; }
.ui-import-file-choice input { position:absolute; width:1px; height:1px; opacity:0; }
.ui-import-file-choice > span { display:inline-flex; align-items:center; justify-content:center; width:100%; min-height:40px; padding:0 8px; border:1px solid #cdded5; border-radius:7px; background:#fff; color:#23483e; }
.ui-import-file-choice input:focus-visible + span { outline:2px solid #1c806d; outline-offset:2px; }
.ui-import-file-choice input:disabled + span { opacity:.55; cursor:not-allowed; }
.ui-import-file-clear { flex:none; width:94px; min-height:40px; border:1px solid #cdded5; border-radius:7px; background:#fff; color:#44685d; font-size:22px; line-height:1; }
.ui-import-file-loading { align-self:center; color:#5b746c; }
</style>
