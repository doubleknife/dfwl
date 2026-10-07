<script setup>
import { computed, reactive, watchEffect } from 'vue';
import { authStore } from '../stores/auth.js';
import { activeRoute, navigate, navigationTrail, routerStore, visibleNavigation } from '../router/routes.js';

const title = computed(() => activeRoute.value?.label || '');
const trail = computed(() => navigationTrail(activeRoute.value?.key));
const expanded = reactive({});
watchEffect(() => {
  for (const group of visibleNavigation.value) {
    if (trail.value[0] === group.label) expanded[group.key] = true;
  }
});
const activeIn = (item) => item.children?.some((leaf) => leaf.key === activeRoute.value?.key);
</script>

<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <strong>车队经营管理</strong>
        <span>{{ authStore.user?.roleName || authStore.user?.roleCode || '管理端' }}</span>
      </div>
      <nav aria-label="主导航">
        <div v-for="group in visibleNavigation" :key="group.key" class="nav-domain">
          <button type="button" class="nav-domain-toggle" :aria-expanded="!!expanded[group.key]" @click="expanded[group.key] = !expanded[group.key]">
            <span>{{ group.label }}</span><span aria-hidden="true">{{ expanded[group.key] ? '⌄' : '›' }}</span>
          </button>
          <div v-if="expanded[group.key]" class="nav-domain-children">
            <template v-for="item in group.children" :key="item.key || item.label">
              <div v-if="item.children" class="nav-subgroup">
                <div :class="{ 'nav-subgroup-active': activeIn(item) }">{{ item.label }}</div>
                <button v-for="leaf in item.children" :key="leaf.key" type="button" class="nav-leaf nav-leaf-third"
                  :class="{ active: activeRoute?.key === leaf.key }" :aria-current="activeRoute?.key === leaf.key ? 'page' : undefined"
                  @click="navigate(leaf.key)">{{ leaf.label }}</button>
              </div>
              <button v-else type="button" class="nav-leaf" :class="{ active: activeRoute?.key === item.key }"
                :aria-current="activeRoute?.key === item.key ? 'page' : undefined" @click="navigate(item.key)">{{ item.label }}</button>
            </template>
          </div>
        </div>
      </nav>
    </aside>
    <section class="workspace">
      <header class="topbar">
        <div class="topbar-location">车队经营管理 <template v-for="part in trail" :key="part"><span>/</span> {{ part }}</template></div>
        <details class="user-menu">
          <summary><span class="user-avatar">{{ (authStore.user?.name || authStore.user?.phone || '用').slice(0, 1) }}</span>{{ authStore.user?.name || authStore.user?.phone || '用户' }} <span aria-hidden="true">⌄</span></summary>
          <div class="user-dropdown"><button type="button" @click="authStore.loadMe">同步权限</button><button type="button" @click="authStore.logout">退出登录</button></div>
        </details>
      </header>
      <p v-if="authStore.error" class="error">{{ authStore.error }}</p>
      <slot />
    </section>
  </main>
</template>
