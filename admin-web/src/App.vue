<script setup>
import { computed, onMounted } from 'vue';
import AppLayout from './layouts/AppLayout.vue';
import LoginView from './views/LoginView.vue';
import { authStore } from './stores/auth.js';
import { activeRoute, routerStore } from './router/routes.js';

const currentComponent = computed(() => activeRoute.value?.component);

onMounted(async () => {
  if (authStore.token) {
    await authStore.loadMe();
  }
});
</script>

<template>
  <LoginView v-if="!authStore.token" />
  <AppLayout v-else>
    <component :is="currentComponent" :key="routerStore.current" />
  </AppLayout>
</template>
