import { authStore, clearSession } from '../stores/auth.js';
import { unwrapApiPayload } from './response.js';

export const API_BASE = import.meta.env?.VITE_API_BASE_URL || '';

export class ApiError extends Error {
  constructor(message, code, status) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

function buildHeaders(extra = {}, hasJsonBody = true) {
  return {
    ...(hasJsonBody ? { 'Content-Type': 'application/json' } : {}),
    ...(authStore.token ? { Authorization: `Bearer ${authStore.token}` } : {}),
    ...extra
  };
}

async function parseResponse(response) {
  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    if (!response.ok) {
      throw new ApiError('请求失败', `HTTP_${response.status}`, response.status);
    }
    return response.blob();
  }
  const payload = await response.json();
  if (!response.ok || payload.code !== '0') {
    if (response.status === 401) clearSession();
    throw new ApiError(payload.message || '请求失败', payload.code || `HTTP_${response.status}`, response.status);
  }
  return unwrapApiPayload(payload);
}

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: options.method || 'GET',
    headers: buildHeaders(options.headers, options.body !== undefined),
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });
  return parseResponse(response);
}

async function upload(path, formData) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: buildHeaders({}, false),
    body: formData
  });
  return parseResponse(response);
}

async function download(path) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: buildHeaders({}, false)
  });
  if (!response.ok) {
    throw new ApiError('下载失败', `HTTP_${response.status}`, response.status);
  }
  return response.blob();
}

export const apiClient = {
  get: (path) => request(path),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  delete: (path) => request(path, { method: 'DELETE' }),
  upload,
  download
};
