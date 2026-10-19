<script setup>
import { reactive, watchEffect } from 'vue';
import NavigationIcon from './NavigationIcon.vue';

const props = defineProps({
  groups: { type: Array, required: true },
  activeKey: { type: String, default: '' },
  trail: { type: Array, default: () => [] }
});
const emit = defineEmits(['navigate']);
const storageKey = 'admin.navigation.expanded.v3';
let restored = {};
try {
  const saved = JSON.parse(sessionStorage.getItem(storageKey) || '{}');
  if (saved && typeof saved === 'object' && !Array.isArray(saved)) restored = saved;
} catch { /* Session storage is optional. */ }
const expanded = reactive(restored);
const branchKey = (group, item) => `${group.key}:${item.label}`;
const activeIn = (item) => item.key === props.activeKey || item.children?.some(activeIn);
function toggle(key) {
  expanded[key] = !expanded[key];
  try { sessionStorage.setItem(storageKey, JSON.stringify(expanded)); } catch { /* Navigation remains usable. */ }
}
watchEffect(() => {
  for (const group of props.groups) {
    if (props.trail[0] !== group.label) continue;
    expanded[group.key] = true;
    for (const item of group.children) {
      if (item.children?.length && props.trail[1] === item.label) expanded[branchKey(group, item)] = true;
    }
  }
});
</script>

<template>
  <nav class="nav-tree" aria-label="主导航">
    <div v-for="group in groups" :key="group.key" class="nav-domain">
      <button type="button" class="nav-domain-toggle" :class="{ 'is-current-path': trail[0] === group.label }"
        :aria-expanded="!!expanded[group.key]" :aria-controls="`nav-domain-${group.key}`" @click="toggle(group.key)">
        <NavigationIcon :name="group.key" /><span class="nav-domain-label">{{ group.label }}</span><span class="nav-chevron" aria-hidden="true">{{ expanded[group.key] ? '⌄' : '›' }}</span>
      </button>
      <div v-show="expanded[group.key]" :id="`nav-domain-${group.key}`" class="nav-domain-children">
        <template v-for="(item, index) in group.children" :key="item.key || item.label">
          <div v-if="item.children?.length" class="nav-subgroup">
            <button type="button" class="nav-subgroup-row" :class="{ 'is-current-path': activeIn(item) }"
              :aria-expanded="!!expanded[branchKey(group, item)]" :aria-controls="`nav-subgroup-${group.key}-${index}`"
              @click="toggle(branchKey(group, item))">
              <span>{{ item.label }}</span><span class="nav-chevron" aria-hidden="true">{{ expanded[branchKey(group, item)] ? '⌄' : '›' }}</span>
            </button>
            <div v-show="expanded[branchKey(group, item)]" :id="`nav-subgroup-${group.key}-${index}`" class="nav-subgroup-children">
              <button v-for="leaf in item.children" :key="leaf.key" type="button" class="nav-leaf nav-leaf-third"
                :class="{ active: activeKey === leaf.key }" :aria-current="activeKey === leaf.key ? 'page' : undefined"
                @click="emit('navigate', leaf.key)">{{ leaf.label }}</button>
            </div>
          </div>
          <button v-else type="button" class="nav-leaf nav-leaf-second"
            :class="{ active: activeKey === item.key }" :aria-current="activeKey === item.key ? 'page' : undefined"
            @click="emit('navigate', item.key)">{{ item.label }}</button>
        </template>
      </div>
    </div>
  </nav>
</template>
