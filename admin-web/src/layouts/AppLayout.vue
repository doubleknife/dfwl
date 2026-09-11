<script setup>
import { computed } from 'vue';
import { authStore } from '../stores/auth.js';
import { activeRoute, navigate, routerStore, visibleRoutes } from '../router/routes.js';

const title = computed(() => activeRoute.value?.label || '');
</script>

<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <strong>车队经营管理</strong>
        <span>{{ authStore.user?.roleName || authStore.user?.roleCode || '管理端' }}</span>
      </div>
      <button
        v-for="route in visibleRoutes"
        :key="route.key"
        type="button"
        :class="{ active: routerStore.current === route.key }"
        @click="navigate(route.key)"
      >
        {{ route.label }}
      </button>
    </aside>
    <section class="workspace">
      <header class="topbar">
        <div>
          <h1>{{ title }}</h1>
          <p>{{ authStore.user?.name || authStore.user?.phone }}</p>
        </div>
        <div class="toolbar">
          <button type="button" @click="authStore.loadMe">同步权限</button>
          <button type="button" @click="authStore.logout">退出</button>
        </div>
      </header>
      <p v-if="authStore.error" class="error">{{ authStore.error }}</p>
      <slot />
    </section>
  </main>
</template>
