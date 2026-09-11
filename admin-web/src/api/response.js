export function unwrapApiPayload(payload) {
  if (!payload || payload.code !== '0') {
    const error = new Error(payload?.message || '请求失败');
    error.code = payload?.code || 'HTTP_ERROR';
    throw error;
  }
  return payload.data;
}
