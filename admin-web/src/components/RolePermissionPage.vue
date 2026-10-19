<script setup>
import { computed } from 'vue';

const props = defineProps({ node: { type: Object, required: true }, selected: { type: Array, required: true },
  permissions: { type: Map, required: true }, search: { type: String, default: '' } });
const emit = defineEmits(['toggle-page', 'toggle-code']);
const count = computed(() => props.node.codes.filter((code) => props.selected.includes(code)).length);
const state = computed(() => !props.node.codes.length || !count.value ? 'none' : count.value === props.node.codes.length ? 'all' : 'partial');
const sharedOwners = computed(() => [...new Set((props.node.shared || []).map((item) => item.ownerLabel))]);
const matchingPermissions = computed(() => {
  const query = props.search.toLowerCase();
  if (!query || props.node.label.toLowerCase().includes(query)) return props.node.operations || [];
  return (props.node.operations || []).filter((code) => `${props.permissions.get(code)?.permissionName || ''} ${code}`.toLowerCase().includes(query));
});
</script>

<template>
  <div class="ui-permission-page">
    <div class="ui-permission-page-head">
      <span v-if="node.type === 'derived'" class="ui-permission-derived">{{ node.label }} <small>按审批或费用归属权限自动显示</small></span>
      <label v-else-if="node.codes.length" class="ui-permission-page-toggle">
        <input type="checkbox" :checked="state === 'all'" :indeterminate="state === 'partial'"
          :aria-label="`选择${node.label}页面权限`" @change="emit('toggle-page', node, state !== 'all')" />
        <strong>{{ node.label }}</strong><small>{{ count }} / {{ node.codes.length }} 项</small>
      </label>
      <strong v-else class="ui-permission-derived">{{ node.label }} <small>共享访问</small></strong>
      <span v-if="node.accessMode === 'ANY'" class="ui-permission-rule">任一访问权限即可进入</span>
    </div>
    <div v-if="node.type === 'page' && matchingPermissions.length" class="ui-permission-page-actions">
      <label v-for="code in matchingPermissions" :key="code" class="ui-permission-action" :title="code">
        <input type="checkbox" :checked="selected.includes(code)"
          @change="emit('toggle-code', code, !selected.includes(code), node.key)" />
        <span>{{ permissions.get(code)?.permissionName || code }}</span>
      </label>
    </div>
    <p v-if="node.type === 'page' && sharedOwners.length" class="ui-permission-shared">
      此页还使用由「{{ sharedOwners.join('」「') }}」统一配置的权限。
    </p>
  </div>
</template>
