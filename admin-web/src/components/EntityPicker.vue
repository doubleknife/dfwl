<script setup>
import { computed, onMounted, ref, useId, watch } from 'vue';
import PaginationBar from './PaginationBar.vue';
import { apiErrorDialog, showErrorDialog } from '../stores/apiErrorDialog.js';

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  loadPage: { type: Function, required: true },
  labelOf: { type: Function, required: true },
  optionValue: { type: Function, default: (item) => item.id },
  searchTextOf: { type: Function, default: null },
  itemDisabled: { type: Function, default: () => false },
  placeholder: { type: String, default: '请选择' },
  selectedLabel: { type: String, default: '' },
  required: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  remoteSearch: { type: Boolean, default: false }
});
const emit = defineEmits(['update:modelValue']);
const listboxId = useId();
const pageNo = ref(1);
const pageSize = 100;
const total = ref(0);
const items = ref([]);
const search = ref('');
const open = ref(false);
const active = ref(-1);
const loading = ref(false);
const error = ref('');
const remembered = ref({ id: '', label: '' });
let requestSeq = 0;
let loadedQuery = null;

watch(error, (message) => {
  if (message && !apiErrorDialog.open) showErrorDialog('选项加载失败', message);
});
const visible = computed(() => props.remoteSearch ? items.value : items.value.filter((item) =>
  String(props.searchTextOf ? props.searchTextOf(item) : props.labelOf(item)).toLowerCase().includes(search.value.trim().toLowerCase())));
const selected = computed(() => items.value.find((item) => String(props.optionValue(item)) === String(props.modelValue)));
const selectedText = computed(() => {
  if (props.modelValue === '' || props.modelValue == null) return '';
  if (selected.value) return props.labelOf(selected.value);
  if (remembered.value.id === String(props.modelValue)) return remembered.value.label;
  return props.selectedLabel || '已选对象';
});

async function load(page = 1) {
  const seq = ++requestSeq;
  const query = props.remoteSearch ? search.value.trim() : undefined;
  loading.value = true; error.value = '';
  try {
    const result = await props.loadPage(page, query);
    if (seq !== requestSeq) return;
    items.value = result.records || result || [];
    total.value = Number(result.total ?? items.value.length);
    pageNo.value = page;
    active.value = -1;
    loadedQuery = query;
  } catch (cause) { if (seq === requestSeq) error.value = cause.message || '选项加载失败'; }
  finally { if (seq === requestSeq) loading.value = false; }
}
function openOptions() {
  if (props.disabled || open.value) return;
  const hadSearch = search.value !== '';
  search.value = '';
  active.value = -1;
  open.value = true;
  if (props.remoteSearch && !hadSearch && loadedQuery !== '' && !loading.value) load(1);
}
function onInput(event) {
  search.value = event.target.value;
  open.value = true;
  active.value = -1;
}
function choose(item) {
  if (item && props.itemDisabled(item)) return;
  remembered.value = item ? { id: String(props.optionValue(item)), label: props.labelOf(item) } : { id: '', label: '' };
  emit('update:modelValue', item ? String(props.optionValue(item)) : '');
  open.value = false;
  search.value = '';
}
function onKey(event) {
  if (event.key === 'Escape') { event.stopPropagation(); open.value = false; return; }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    openOptions();
    const direction = event.key === 'ArrowDown' ? 1 : -1;
    let next = active.value;
    for (let i = 0; i < visible.value.length; i++) {
      next = Math.max(0, Math.min(visible.value.length - 1, next + direction));
      if (!props.itemDisabled(visible.value[next])) { active.value = next; break; }
      if (next === 0 || next === visible.value.length - 1) break;
    }
  }
  if (event.key === 'Enter' && open.value) {
    event.preventDefault();
    const item = visible.value[active.value];
    if (item && !props.itemDisabled(item)) choose(item);
  }
}
function onFocusOut(event) {
  if (event.currentTarget.contains(event.relatedTarget)) return;
  const picker = event.currentTarget;
  setTimeout(() => { if (!picker.contains(document.activeElement)) open.value = false; }, 0);
}
watch(() => props.loadPage, () => load(1));
watch(() => props.modelValue, (value) => { if (value === '' || value == null) remembered.value = { id: '', label: '' }; });
watch(search, (_, __, onCleanup) => {
  if (!props.remoteSearch || !open.value) return;
  ++requestSeq;
  items.value = [];
  total.value = 0;
  loading.value = true;
  const timer = setTimeout(() => { if (open.value) load(1); else loading.value = false; }, 250);
  onCleanup(() => clearTimeout(timer));
});
onMounted(() => load(1));
</script>

<template>
  <div class="ui-entity-picker" :class="{ 'is-open': open, 'is-disabled': disabled }" @focusout="onFocusOut">
    <input :value="open ? search : selectedText" type="search" role="combobox" :aria-label="placeholder"
      :aria-expanded="open" :aria-controls="listboxId" aria-autocomplete="list"
      :disabled="disabled" :required="required && !modelValue"
      :placeholder="open ? `搜索${placeholder.replace('请选择', '').replace('全部', '')}` : placeholder"
      @focus="openOptions" @click="openOptions" @input="onInput" @keydown="onKey" />
    <div v-if="open" :id="listboxId" class="ui-entity-options" role="listbox">
      <p v-if="error" class="muted"><button type="button" @click="load(pageNo)">重新加载选项</button></p>
      <template v-else>
        <button v-if="!required" type="button" role="option" :aria-selected="!modelValue"
          @mousedown.prevent @click="choose(null)">{{ placeholder }}</button>
        <p v-if="loading && !visible.length" class="muted">正在加载…</p>
        <p v-else-if="!visible.length" class="muted">没有匹配的选项</p>
        <button v-for="(item, index) in visible" :key="optionValue(item)" type="button" role="option"
          :aria-selected="String(optionValue(item)) === String(modelValue)" :disabled="itemDisabled(item)"
          :class="{ active: index === active }" @mousedown.prevent @click="choose(item)">
          {{ labelOf(item) }}{{ itemDisabled(item) ? '（停用）' : '' }}
        </button>
        <small class="muted">{{ remoteSearch ? '搜索全部记录' : '搜索当前页' }} · 共 {{ total }} 条</small>
        <PaginationBar :show-size="false" v-if="total > pageSize" :page-no="pageNo" :page-size="pageSize" :total="total" @change="load" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.ui-entity-picker { position: relative; min-width: 0; }
.ui-entity-picker::after { content: ''; position: absolute; top: 17px; right: 16px; width: 7px; height: 7px; border-right: 2px solid #48675d; border-bottom: 2px solid #48675d; transform: rotate(45deg); pointer-events: none; }
.ui-entity-picker.is-open::after { transform: translateY(4px) rotate(225deg); }
.ui-entity-picker.is-disabled::after { opacity: .45; }
.ui-entity-picker > input { width: 100%; padding-right: 40px; cursor: pointer; }
.ui-entity-picker > input:focus { cursor: text; }
.ui-entity-options { position: absolute; z-index: 30; top: calc(100% + 4px); left: 0; right: 0; max-height: 260px; overflow-y: auto; padding: 5px; background: #fff; border: 1px solid #cddbd1; border-radius: 8px; box-shadow: 0 14px 32px rgb(25 61 45 / 16%); }
.ui-entity-options > button { display: block; width: 100%; padding: 9px 11px; border: 0; background: none; text-align: left; }
.ui-entity-options > button:hover, .ui-entity-options > button.active { background: #e9f3ec; color: #155c41; }
.ui-entity-options > button:disabled { color: #82928a; cursor: not-allowed; }
.ui-entity-options > p, .ui-entity-options > small { display: block; margin: 6px 8px; }
.ui-entity-options :deep(.ui-pagination) { justify-content: flex-start; margin: 6px 4px 2px; font-size: 12px; }
.ui-entity-options :deep(.ui-pagination button) { min-height: 28px; }
</style>
