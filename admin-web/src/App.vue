<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import AppLayout from './layouts/AppLayout.vue';
import LoginView from './views/LoginView.vue';
import { authStore } from './stores/auth.js';
import { activeRoute, routeAccessDenied, routerStore } from './router/routes.js';
import { apiErrorDialog, closeApiError } from './stores/apiErrorDialog.js';

const currentComponent = computed(() => activeRoute.value?.component);
const errorConfirmButton = ref(null);

watch(() => apiErrorDialog.open, async (open) => {
  if (open) {
    await nextTick();
    errorConfirmButton.value?.focus();
  }
});

onMounted(async () => {
  if (authStore.token) {
    await authStore.loadMe();
  }
});
</script>

<template>
  <LoginView v-if="!authStore.token" />
  <AppLayout v-else>
    <component v-if="activeRoute" :is="currentComponent" v-bind="activeRoute?.props || {}" :key="routerStore.current" />
    <section v-else class="ui-page-container ui-section" role="status">
      <h1>{{ routeAccessDenied ? '无权访问此页面' : '暂无可访问的业务页面' }}</h1>
      <p>当前账号没有该页面权限。请联系管理员核对角色权限，再从右上角刷新我的权限。</p>
    </section>
  </AppLayout>
  <Teleport to="body">
    <div v-if="apiErrorDialog.open" class="ui-api-error-overlay">
      <section class="ui-api-error-dialog" role="alertdialog" aria-modal="true" aria-labelledby="ui-api-error-title" aria-describedby="ui-api-error-message">
        <div class="ui-api-error-heading">
          <span class="ui-api-error-icon" aria-hidden="true">!</span>
          <h2 id="ui-api-error-title">{{ apiErrorDialog.title }}</h2>
        </div>
        <p id="ui-api-error-message">{{ apiErrorDialog.message }}</p>
        <div class="ui-api-error-actions">
          <button ref="errorConfirmButton" class="primary" type="button" @click="closeApiError">确定</button>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style>
.ui-api-error-overlay { position:fixed; inset:0; z-index:2147483647; display:grid; place-items:center; padding:20px; background:rgb(12 31 24 / 48%); }
.ui-api-error-dialog { width:min(100%, 400px); padding:22px 24px 20px; background:#fff; border:1px solid #d5dfd9; border-radius:12px; box-shadow:0 24px 72px rgb(0 0 0 / 22%); }
.ui-api-error-heading { display:flex; align-items:center; gap:10px; margin-bottom:12px; }
.ui-api-error-icon { display:grid; place-items:center; flex:none; width:28px; height:28px; border-radius:50%; background:#fff0eb; color:#b63b25; font-size:18px; font-weight:700; }
.ui-api-error-dialog h2 { margin:0; color:#183e34; font-size:18px; line-height:1.4; }
.ui-api-error-dialog p { margin:0; color:#384a43; font-size:14px; line-height:1.6; overflow-wrap:anywhere; }
.ui-api-error-actions { display:flex; justify-content:flex-end; margin-top:20px; }
.ui-api-error-actions button { min-width:80px; }
</style>
