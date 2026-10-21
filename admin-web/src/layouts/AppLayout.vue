<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { authStore, clearSession } from '../stores/auth.js';
import { activeRoute, navigate, navigationTrail, routerStore, visibleNavigation } from '../router/routes.js';
import { notificationApi, systemApi } from '../api/modules.js';
import FormDialog from '../components/FormDialog.vue';
import NavigationTree from '../components/NavigationTree.vue';
import IcpFooter from '../components/IcpFooter.vue';
import { apiErrorDialog, showErrorDialog } from '../stores/apiErrorDialog.js';

const menuKey = computed(() => activeRoute.value?.key === 'approvals-queue' ? 'approvals-todo' : activeRoute.value?.key);
const trail = computed(() => navigationTrail(menuKey.value));
const accountMenu = ref(null);
const notificationMenu = ref(null);
const notificationRows = ref([]);
const unreadNotifications = ref(0);
let notificationTimer;
async function refreshNotifications() {
  if (!authStore.token) return;
  try {
    const [page, count] = await Promise.all([notificationApi.list(), notificationApi.unreadCount()]);
    notificationRows.value = page.records || [];
    unreadNotifications.value = Number(count.unreadCount || 0);
  } catch { /* API client displays the request error. */ }
}
async function openNotification(item) {
  try {
    if (!item.readAt) await notificationApi.read(item.id);
    if (item.businessType === 'APPROVAL') {
      const target = item.type === 'APPROVAL_RETURN' ? 'approvals-mine' : 'approvals-todo';
      sessionStorage.setItem('admin.approvalNotificationId', String(item.businessId));
      if (routerStore.current === target) window.dispatchEvent(new Event('approval-notification-open'));
      else navigate(target);
      notificationMenu.value.open = false;
    }
    await refreshNotifications();
  } catch { /* API client displays the request error. */ }
}
const syncing = ref(false);
const feedback = ref('');
const passwordOpen = ref(false);
const passwordBusy = ref(false);
const passwordError = ref('');
watch(() => authStore.error, (message) => {
  if (message && !apiErrorDialog.open) showErrorDialog('加载失败', message);
});
watch(passwordError, (message) => {
  if (message && !apiErrorDialog.open) showErrorDialog('修改密码失败', message);
});
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });
function closeAccountMenuOnOutside(event) {
  if (accountMenu.value?.open && !accountMenu.value.contains(event.target)) accountMenu.value.open = false;
}
function closeAccountMenuOnEscape(event) {
  if (event.key === 'Escape' && accountMenu.value?.open) accountMenu.value.open = false;
}
onMounted(() => {
  document.addEventListener('pointerdown', closeAccountMenuOnOutside);
  document.addEventListener('keydown', closeAccountMenuOnEscape);
  refreshNotifications();
  notificationTimer = window.setInterval(refreshNotifications, 60000);
});
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', closeAccountMenuOnOutside);
  document.removeEventListener('keydown', closeAccountMenuOnEscape);
  window.clearInterval(notificationTimer);
});
watch([activeRoute, () => routerStore.current], ([route, current]) => {
  if (route && route.key !== current) navigate(route.key);
}, { immediate: true });
async function refreshPermissions() {
  if (syncing.value) return;
  syncing.value = true;
  feedback.value = '';
  await authStore.loadMe();
  if (!authStore.error) feedback.value = '当前账号权限已刷新';
  syncing.value = false;
  if (accountMenu.value) accountMenu.value.open = false;
}
function openPassword() {
  feedback.value = '';
  passwordError.value = '';
  Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' });
  passwordOpen.value = true;
  if (accountMenu.value) accountMenu.value.open = false;
}
async function changePassword() {
  passwordError.value = '';
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致';
    return;
  }
  if (passwordBusy.value) return;
  passwordBusy.value = true;
  try {
    await systemApi.changePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword });
    passwordOpen.value = false;
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' });
    sessionStorage.setItem('admin.loginNotice', '密码已修改，请使用新密码重新登录');
    clearSession();
  } catch (cause) {
    passwordError.value = cause.message || '修改密码失败，请重试';
  } finally {
    passwordBusy.value = false;
  }
}
</script>

<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <strong><span class="brand-mark" aria-hidden="true"><svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 11h16v11H3zM19 14h5l5 5v3H19zM7 11l2-4h9l2 4M7 22a2.5 2.5 0 1 0 5 0M22 22a2.5 2.5 0 1 0 5 0M2 6h7M1 9h5"/></svg></span>车队经营管理系统</strong>
        <span>管理端 · {{ authStore.user?.role?.name || authStore.user?.roleName || '角色未配置' }}</span>
      </div>
      <NavigationTree :groups="visibleNavigation" :active-key="menuKey" :trail="trail" @navigate="navigate" />
      <div class="sidebar-footer">专业的车队管理<br />从这里开始</div>
    </aside>
    <section class="workspace">
      <header class="topbar">
        <div class="topbar-location">车队经营管理系统 <template v-for="part in trail" :key="part"><span>/</span> {{ part }}</template></div>
        <details ref="notificationMenu" class="user-menu notification-menu" @toggle="notificationMenu?.open && refreshNotifications()">
          <summary><svg class="notification-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z"/><path d="M10 21h4"/></svg>消息<span v-if="unreadNotifications" class="notification-count">{{ unreadNotifications }}</span></summary>
          <div class="user-dropdown notification-dropdown">
            <strong>站内消息</strong>
            <div v-if="!notificationRows.length" class="notification-empty">暂无消息</div>
            <button v-for="item in notificationRows" :key="item.id" type="button" class="notification-item" @click="openNotification(item)">
              <span>{{ item.title }}<b v-if="!item.readAt">未读</b></span>
              <small>{{ item.summary }}</small>
            </button>
          </div>
        </details>
        <details ref="accountMenu" class="user-menu">
          <summary><span class="user-avatar">{{ (authStore.user?.name || authStore.user?.phone || '用').slice(0, 1) }}</span>{{ authStore.user?.name || authStore.user?.phone || '用户' }}<span class="user-role">{{ authStore.user?.role?.name || authStore.user?.roleName || '角色未配置' }}</span><span aria-hidden="true">⌄</span></summary>
          <div class="user-dropdown"><button type="button" :disabled="syncing" @click="refreshPermissions">{{ syncing ? '正在刷新…' : '刷新我的权限' }}</button><button type="button" @click="openPassword">修改密码</button><button type="button" @click="authStore.logout">退出登录</button></div>
        </details>
      </header>
      <p v-if="feedback" class="ui-success-message" role="status">{{ feedback }}</p>
      <slot />
      <IcpFooter />
    </section>
    <FormDialog :open="passwordOpen" title="修改密码" :busy="passwordBusy" @close="passwordOpen = false">
      <form @submit.prevent="changePassword">
        <p class="muted">修改后当前登录会失效，请使用新密码重新登录。</p>
        <label class="ui-form-field">当前密码<input v-model="passwordForm.oldPassword" type="password" autocomplete="current-password" required /></label>
        <label class="ui-form-field">新密码<input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" required minlength="6" /></label>
        <label class="ui-form-field">确认新密码<input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" required minlength="6" /></label>
        <div class="ui-dialog-actions"><button type="button" :disabled="passwordBusy" @click="passwordOpen = false">取消</button><button class="primary" type="submit" :disabled="passwordBusy">{{ passwordBusy ? '正在修改…' : '确认修改' }}</button></div>
      </form>
    </FormDialog>
  </main>
</template>

<style scoped>
.notification-menu { margin-left:auto; }
.notification-icon { width:18px; height:18px; flex:none; }
.notification-count { margin-left:6px; border-radius:12px; padding:1px 6px; background:#c43f31; color:#fff; font-size:12px; }
.notification-dropdown { width:min(360px, 90vw); max-height:420px; overflow-y:auto; padding:12px; }
.notification-dropdown strong { display:block; margin:2px 8px 8px; }
.notification-empty { margin:8px; color:#71847b; }
.notification-dropdown .notification-item { display:block; width:100%; padding:9px; text-align:left; border:0; border-bottom:1px solid #e8eeea; background:#fff; cursor:pointer; }
.notification-item span,.notification-item small { display:block; }
.notification-item b { margin-left:6px; color:#c43f31; font-size:12px; }
.notification-item small { margin-top:3px; color:#657a70; line-height:1.4; }
</style>
