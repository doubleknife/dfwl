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
  authStore.token = data.token;
  authStore.user = data.user;
  localStorage.setItem('admin.token', data.token);
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
    const data = await apiClient.post('/auth/login', form);
    setSession(data);
  } catch (error) {
    authStore.error = error.message;
    throw error;
  } finally {
    authStore.loading = false;
  }
};

authStore.logout = () => {
  clearSession();
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
