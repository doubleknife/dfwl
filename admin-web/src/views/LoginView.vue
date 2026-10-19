<script setup>
import { reactive, ref } from 'vue';
import { authStore } from '../stores/auth.js';

const form = reactive({ phone: '', password: '' });
const notice = ref(sessionStorage.getItem('admin.loginNotice') || '');
const icpNumber = import.meta.env.VITE_ICP_NUMBER?.trim() || '';
sessionStorage.removeItem('admin.loginNotice');

async function submit() {
  try {
    await authStore.login(form);
  } catch {
    // API client presents the unified error dialog.
  }
}
</script>

<template>
  <main class="login-shell">
    <form class="login-panel" @submit.prevent="submit">
      <h1>车队经营管理系统</h1>
      <p>登录后按账号权限加载菜单和按钮。</p>
      <p v-if="notice" class="ui-success-message" role="status">{{ notice }}</p>
      <label>手机号<input v-model.trim="form.phone" autocomplete="username" /></label>
      <label>密码<input v-model="form.password" type="password" autocomplete="current-password" /></label>
      <button class="primary" :disabled="authStore.loading">登录</button>
    </form>
    <small v-if="icpNumber" class="login-icp">{{ icpNumber }}</small>
  </main>
</template>
