<script setup>
import { reactive } from 'vue';
import { authStore } from '../stores/auth.js';

const form = reactive({ phone: '', password: '' });

async function submit() {
  await authStore.login(form);
}
</script>

<template>
  <main class="login-shell">
    <form class="login-panel" @submit.prevent="submit">
      <h1>车队经营管理</h1>
      <p>登录后按账号权限加载菜单和按钮。</p>
      <label>手机号<input v-model.trim="form.phone" autocomplete="username" /></label>
      <label>密码<input v-model="form.password" type="password" autocomplete="current-password" /></label>
      <button class="primary" :disabled="authStore.loading">登录</button>
      <p v-if="authStore.error" class="error">{{ authStore.error }}</p>
    </form>
  </main>
</template>
