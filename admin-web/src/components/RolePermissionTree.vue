<script setup>
import { computed, ref } from 'vue';
import RolePermissionPage from './RolePermissionPage.vue';

const props = defineProps({ model: { type: Object, required: true }, selected: { type: Array, required: true },
  search: { type: String, default: '' } });
const emit = defineEmits(['toggle-codes', 'toggle-code']);
const expanded = ref(new Set());
const query = computed(() => props.search.trim().toLowerCase());
function state(node) {
  if (!node.codes.length) return 'none';
  const count = node.codes.filter((code) => props.selected.includes(code)).length;
  return count === 0 ? 'none' : count === node.codes.length ? 'all' : 'partial';
}
function matches(node) {
  if (!query.value) return true;
  if (node.label?.toLowerCase().includes(query.value)) return true;
  if ((node.codes || []).some((code) => `${code} ${props.model.permissions.get(code)?.permissionName || ''}`.toLowerCase().includes(query.value))) return true;
  return (node.children || []).some(matches);
}
function isOpen(node) { return !!query.value || expanded.value.has(node.key); }
function toggleOpen(node) {
  const next = new Set(expanded.value);
  if (next.has(node.key)) next.delete(node.key); else next.add(node.key);
  expanded.value = next;
}
const common = computed(() => props.model.common.filter((item) => !query.value
  || `${item.permissionName} ${item.permissionCode}`.toLowerCase().includes(query.value)));
function forwardPage(node, checked) { emit('toggle-codes', node.codes, checked); }
</script>

<template>
  <div class="ui-page-permission-tree">
    <section v-for="domain in model.domains.filter(matches)" :key="domain.key" class="ui-permission-domain">
      <header class="ui-permission-domain-head">
        <button type="button" :aria-expanded="isOpen(domain)" @click="toggleOpen(domain)">{{ isOpen(domain) ? '▾' : '▸' }} {{ domain.label }}</button>
        <label><input type="checkbox" :checked="state(domain) === 'all'" :indeterminate="state(domain) === 'partial'"
          :aria-label="`选择${domain.label}全部权限`" @change="emit('toggle-codes', domain.codes, state(domain) !== 'all')" />
          {{ domain.codes.filter((code) => selected.includes(code)).length }} / {{ domain.codes.length }} 项</label>
      </header>
      <div v-if="isOpen(domain)" class="ui-permission-domain-body">
        <template v-for="entry in domain.children.filter(matches)" :key="entry.key">
          <RolePermissionPage v-if="entry.type !== 'group'" :node="entry" :selected="selected" :permissions="model.permissions"
            :search="search" @toggle-page="forwardPage" @toggle-code="(...args) => emit('toggle-code', ...args)" />
          <section v-else class="ui-permission-subgroup">
            <header class="ui-permission-subgroup-head">
              <button type="button" :aria-expanded="isOpen(entry)" @click="toggleOpen(entry)">{{ isOpen(entry) ? '▾' : '▸' }} {{ entry.label }}</button>
              <label v-if="entry.codes.length"><input type="checkbox" :checked="state(entry) === 'all'" :indeterminate="state(entry) === 'partial'"
                :aria-label="`选择${entry.label}全部权限`" @change="emit('toggle-codes', entry.codes, state(entry) !== 'all')" />
                {{ entry.codes.filter((code) => selected.includes(code)).length }} / {{ entry.codes.length }} 项</label>
              <span v-else class="ui-permission-rule">随关联页面授权</span>
            </header>
            <div v-if="isOpen(entry)"><RolePermissionPage v-for="page in entry.children.filter(matches)" :key="page.key"
              :node="page" :selected="selected" :permissions="model.permissions" :search="search"
              @toggle-page="forwardPage" @toggle-code="(...args) => emit('toggle-code', ...args)" /></div>
          </section>
        </template>
      </div>
    </section>
    <section v-if="common.length" class="ui-permission-domain">
      <header class="ui-permission-domain-head">
        <button type="button" :aria-expanded="isOpen({key:'common'})" @click="toggleOpen({key:'common'})">{{ isOpen({key:'common'}) ? '▾' : '▸' }} 公共权限</button>
        <label><input type="checkbox" :checked="common.every((item) => selected.includes(item.permissionCode))"
          :indeterminate="common.some((item) => selected.includes(item.permissionCode)) && !common.every((item) => selected.includes(item.permissionCode))"
          aria-label="选择全部公共权限"
          @change="emit('toggle-codes', common.map((item) => item.permissionCode), !common.every((item) => selected.includes(item.permissionCode)))" />
          {{ common.filter((item) => selected.includes(item.permissionCode)).length }} / {{ common.length }} 项</label>
      </header>
      <div v-if="isOpen({key:'common'})" class="ui-permission-domain-body">
        <label v-for="item in common" :key="item.permissionCode" class="ui-permission-action" :title="item.permissionCode">
          <input type="checkbox" :checked="selected.includes(item.permissionCode)"
            @change="emit('toggle-code', item.permissionCode, !selected.includes(item.permissionCode), '')" />
          {{ item.permissionName }}
        </label>
      </div>
    </section>
  </div>
</template>
