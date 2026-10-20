<script setup>
import { computed, ref, watch } from 'vue';
import { apiErrorDialog, showErrorDialog } from '../stores/apiErrorDialog.js';

const props = defineProps({ modelValue: { type: [Number, String], default: '' }, loadPage: { type: Function, required: true }, selectedLabel: { type: String, default: '' } });
const emit = defineEmits(['update:modelValue']);
const query = ref(''); const open = ref(false); const loading = ref(false); const error = ref(''); const roles = ref([]); const active = ref(0);
watch(error, (message) => {
  if (message && !apiErrorDialog.open) showErrorDialog('角色加载失败', message);
});
const filtered = computed(() => roles.value.filter((role) => role.status === 1 && role.roleCode !== 'DRIVER' && role.roleName.toLowerCase().includes(query.value.trim().toLowerCase())));
const selected = computed(() => roles.value.find((role) => String(role.id) === String(props.modelValue))?.roleName || props.selectedLabel);
async function load() {
  if (roles.value.length) return;
  loading.value = true; error.value = '';
  try {
    const collected = [];
    for (let page = 1; page <= 20; page++) {
      const result = await props.loadPage(page, 100);
      collected.push(...(result.records || []));
      if (collected.length >= Number(result.total || 0)) break;
    }
    roles.value = collected;
  } catch (cause) { error.value = cause.message || '角色加载失败'; }
  finally { loading.value = false; }
}
function choose(role) { emit('update:modelValue', String(role.id)); query.value = ''; open.value = false; }
function onKey(event) {
  if (event.key === 'Escape') { open.value = false; return; }
  if (event.key === 'ArrowDown') { event.preventDefault(); open.value = true; active.value = Math.min(active.value + 1, filtered.value.length - 1); }
  if (event.key === 'ArrowUp') { event.preventDefault(); active.value = Math.max(active.value - 1, 0); }
  if (event.key === 'Enter' && open.value && filtered.value[active.value]) { event.preventDefault(); choose(filtered.value[active.value]); }
}
watch(query, () => { active.value = 0; });
</script>

<template>
  <div class="ui-role-combobox" @focusout="(event) => { if (!event.currentTarget.contains(event.relatedTarget)) open = false; }">
    <input :value="open ? query : selected" type="search" role="combobox" aria-label="搜索并选择角色" :aria-expanded="open"
      aria-controls="ui-role-options" :placeholder="open ? (loading ? '正在加载角色…' : '搜索并选择角色') : ''"
      @focus="open = true; load()" @input="query = $event.target.value; open = true" @keydown="onKey" />
    <div v-if="open" id="ui-role-options" class="ui-role-options" role="listbox">
      <p v-if="error" class="muted"><button type="button" @click="load">重新加载角色</button></p>
      <p v-else-if="!filtered.length" class="muted">{{ loading ? '正在加载…' : '没有匹配的有效角色' }}</p>
      <button v-for="(role,index) in filtered" :key="role.id" type="button" role="option" :aria-selected="String(role.id) === String(modelValue)"
        :class="{ active: index === active }" @mousedown.prevent="choose(role)">{{ role.roleName }}</button>
    </div>
  </div>
</template>

<style scoped>
.ui-role-combobox { position:relative; min-width:0; }
.ui-role-combobox input { width:100%; }
.ui-role-options { position:absolute; z-index:30; top:calc(100% + 4px); left:0; right:0; max-height:240px; overflow-y:auto; padding:5px; background:#fff; border:1px solid #cddbd1; border-radius:8px; box-shadow:0 14px 32px rgb(25 61 45 / 16%); }
.ui-role-options button { display:block; width:100%; padding:9px 11px; border:0; background:none; text-align:left; }
.ui-role-options button:hover,.ui-role-options button.active { background:#e9f3ec; color:#155c41; }
</style>
