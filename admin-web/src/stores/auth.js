import { reactive } from 'vue';
import { apiClient } from '../api/client.js';
import { hasAnyPermission, hasPermission } from '../utils/permissions.js';

const savedUser = localStorage.getItem('admin.user');

export const authStore = reactive({
  token: localStorage.getItem('admin.token') || '',
  user: savedUser ? JSON.parse(savedUser) : null,
  loading: false,
  error: ''
});

export function permissions() {
  return authStore.user?.permissions || [];
}

export function can(code) {
  return hasPermission(permissions(), code);
}

export function canAny(codeOrCodes) {
  return hasAnyPermission(permissions(), codeOrCodes);
}

export function setSession(data) {
  authStore.token = data.accessToken;
  authStore.user = data.user;
  localStorage.setItem('admin.token', data.accessToken);
  localStorage.setItem('admin.user', JSON.stringify(data.user));
}

export function clearSession() {
  authStore.token = '';
  authStore.user = null;
  localStorage.removeItem('admin.token');
  localStorage.removeItem('admin.user');
}

authStore.login = async (form) => {
  authStore.loading = true;
  authStore.error = '';
  try {
    const data = await apiClient.post('/auth/login', { ...form, terminal: 'WEB' });
    setSession(data);
  } catch (error) {
    authStore.error = error.message;
    throw error;
  } finally {
    authStore.loading = false;
  }
};

authStore.logout = async () => {
  let serverRevoked = !authStore.token;
  try {
    if (authStore.token) {
      await apiClient.post('/auth/logout');
      serverRevoked = true;
    }
  } catch {
    // The API client shows the business error. The local credential must still be cleared.
  } finally {
    clearSession();
    if (!serverRevoked) sessionStorage.setItem('admin.loginNotice', '本机已退出，服务端会话撤销未确认');
  }
};

authStore.loadMe = async () => {
  authStore.error = '';
  try {
    authStore.user = await apiClient.get('/me');
    localStorage.setItem('admin.user', JSON.stringify(authStore.user));
  } catch (error) {
    authStore.error = error.message;
  }
};
