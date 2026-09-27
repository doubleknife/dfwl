<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import PaginationBar from './PaginationBar.vue';

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  loadPage: { type: Function, required: true },
  labelOf: { type: Function, required: true },
  placeholder: { type: String, default: '请选择' },
  selectedLabel: { type: String, default: '' },
  required: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
});
const emit = defineEmits(['update:modelValue']);
const pageNo = ref(1);
const pageSize = ref(100);
const total = ref(0);
const items = ref([]);
const search = ref('');
const loading = ref(false);
const error = ref('');
const remembered = ref('');
const visible = computed(() => items.value.filter((item) => props.labelOf(item).toLowerCase().includes(search.value.trim().toLowerCase())));
const currentLabel = computed(() => items.value.find((item) => String(item.id) === String(props.modelValue)) || null);

async function load(page = 1) {
  loading.value = true; error.value = '';
  try {
    const result = await props.loadPage(page);
    items.value = result.records || result || [];
    total.value = Number(result.total ?? items.value.length);
    pageNo.value = page;
  } catch (cause) { error.value = cause.message || '选项加载失败'; }
  finally { loading.value = false; }
}
function choose(event) {
  const value = event.target.value;
  const item = items.value.find((entry) => String(entry.id) === value);
  remembered.value = item ? props.labelOf(item) : '';
  emit('update:modelValue', value);
}
watch(() => props.loadPage, () => load(1));
onMounted(() => load(1));
</script>

<template>
  <div class="ui-entity-picker">
    <input v-model="search" type="search" :disabled="disabled" :placeholder="`搜索当前页${placeholder.replace('请选择', '')}`" :aria-label="`搜索${placeholder}`" />
    <select :value="String(modelValue ?? '')" :required="required" :disabled="disabled || loading" :aria-label="placeholder" @change="choose">
      <option value="">{{ loading ? '正在加载…' : placeholder }}</option>
      <option v-if="modelValue && !currentLabel" :value="String(modelValue)">{{ remembered || selectedLabel || '已选对象（不在当前页）' }}</option>
      <option v-for="item in visible" :key="item.id" :value="String(item.id)">{{ labelOf(item) }}</option>
    </select>
    <small v-if="error" class="error">{{ error }} <button type="button" @click="load(pageNo)">重试</button></small>
    <small v-else class="muted">在当前页搜索；可翻页查找，共 {{ total }} 条。</small>
    <PaginationBar v-if="total > pageSize" :page-no="pageNo" :page-size="pageSize" :total="total" @change="load" />
  </div>
</template>
