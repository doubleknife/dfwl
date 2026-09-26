<script setup>
import { computed } from 'vue';
import { authStore } from '../stores/auth.js';
import { activeRoute, navigate, routerStore, visibleRoutes } from '../router/routes.js';

const title = computed(() => activeRoute.value?.label || '');
const groups = [
  { label: '总览', keys: ['dashboard'] },
  { label: '业务运营', keys: ['master', 'routes', 'imports', 'expenses', 'approvals', 'approvalFlows', 'tires', 'salaries'] },
  { label: '经营分析', keys: ['reports', 'settlements'] },
  { label: '设置', keys: ['system'] }
];
const groupedRoutes = computed(() => groups.map((group) => ({ ...group, routes: visibleRoutes.value.filter((route) => group.keys.includes(route.key)) })).filter((group) => group.routes.length));
</script>

<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <strong>车队经营管理</strong>
        <span>{{ authStore.user?.roleName || authStore.user?.roleCode || '管理端' }}</span>
      </div>
      <nav aria-label="主导航">
        <div v-for="group in groupedRoutes" :key="group.label">
          <div class="sidebar-group">{{ group.label }}</div>
          <button v-for="route in group.routes" :key="route.key" type="button" :class="{ active: routerStore.current === route.key }" @click="navigate(route.key)">{{ route.label }}</button>
        </div>
      </nav>
    </aside>
    <section class="workspace">
      <header class="topbar">
        <div class="topbar-location">车队经营管理 <span>/</span> {{ title }}</div>
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
