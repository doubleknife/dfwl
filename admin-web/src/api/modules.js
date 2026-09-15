import { apiClient } from './client.js';

export const dashboardApi = {
  summary: () => apiClient.get('/reports/dashboard'),
  profit: () => apiClient.get('/reports/profit'),
  expenses: () => apiClient.get('/expenses?pageNo=1&pageSize=20'),
  approvals: () => apiClient.get('/approvals?pageNo=1&pageSize=20'),
  routes: () => apiClient.get('/routes?pageNo=1&pageSize=20')
};

export const masterApi = {
  drivers: () => apiClient.get('/drivers?pageNo=1&pageSize=100'),
  vehicles: () => apiClient.get('/vehicles?pageNo=1&pageSize=100'),
  trailers: () => apiClient.get('/trailers?pageNo=1&pageSize=100'),
  customers: () => apiClient.get('/customers?pageNo=1&pageSize=100'),
  products: () => apiClient.get('/products?pageNo=1&pageSize=100'),
  createDriver: (body) => apiClient.post('/drivers', body),
  createVehicle: (body) => apiClient.post('/vehicles', body),
  createTrailer: (body) => apiClient.post('/trailers', body),
  createCustomer: (body) => apiClient.post('/customers', body),
  updateCustomer: (id, body) => apiClient.put(`/customers/${id}`, body),
  setCustomerStatus: (id, status) => apiClient.put(`/customers/${id}/status`, { status }),
  createProduct: (body) => apiClient.post('/products', body),
  updateProduct: (id, body) => apiClient.put(`/products/${id}`, body),
  setProductStatus: (id, status) => apiClient.put(`/products/${id}/status`, { status }),
  bindDriverVehicle: (driverId, body) => apiClient.post(`/drivers/${driverId}/bind-vehicle`, body),
  unbindDriverVehicle: (driverId, body = {}) => apiClient.post(`/drivers/${driverId}/unbind-vehicle`, body),
  bindTrailerVehicle: (trailerId, body) => apiClient.post(`/trailers/${trailerId}/bind-vehicle`, body),
  unbindTrailerVehicle: (trailerId, body = {}) => apiClient.post(`/trailers/${trailerId}/unbind-vehicle`, body),
  vehicleHistory: (vehicleId) => apiClient.get(`/vehicles/${vehicleId}/binding-history`)
};

export const routeApi = {
  list: () => apiClient.get('/routes?pageNo=1&pageSize=100'),
  detail: (id) => apiClient.get(`/routes/${id}`),
  create: (body) => apiClient.post('/routes', body),
  update: (id, body) => apiClient.put(`/routes/${id}`, body),
  delete: (id) => apiClient.delete(`/routes/${id}`),
  action: (id, action, body = {}) => apiClient.post(`/routes/${id}/${action}`, body),
  unload: (id, body) => apiClient.post(`/routes/${id}/unload`, body),
  adjustWeight: (id, body) => apiClient.post(`/routes/${id}/weight-adjustments`, body),
  weightVersions: (id) => apiClient.get(`/routes/${id}/weight-versions`)
};

export const importApi = {
  uploadFile: (formData) => apiClient.upload('/attachments', formData),
  preview: (body) => apiClient.post('/imports/preview', body),
  commit: (id) => apiClient.post(`/imports/${id}/commit`, {}),
  history: () => apiClient.get('/imports?pageNo=1&pageSize=100'),
  failures: (id) => apiClient.download(`/imports/${id}/failures/export`)
};

export const expenseApi = {
  list: () => apiClient.get('/expenses?pageNo=1&pageSize=100'),
  create: (body) => apiClient.post('/expenses', body),
  update: (id, body) => apiClient.put(`/expenses/${id}`, body),
  delete: (id) => apiClient.delete(`/expenses/${id}`),
  reverse: (id, body) => apiClient.post(`/expenses/${id}/reversal`, body),
  attribution: (id, body) => apiClient.put(`/expenses/${id}/attribution`, body),
  attributionHistory: (id) => apiClient.get(`/expenses/${id}/attribution-history`),
  approval: (id) => apiClient.get(`/expenses/${id}/approval`)
};

export const approvalApi = {
  list: () => apiClient.get('/approvals?pageNo=1&pageSize=100'),
  detail: (id) => apiClient.get(`/approvals/${id}`),
  create: (body) => apiClient.post('/approvals', body),
  approve: (id, body) => apiClient.post(`/approvals/${id}/approve`, body),
  returnApplicant: (id, body) => apiClient.post(`/approvals/${id}/return-applicant`, body),
  returnNode: (id, body) => apiClient.post(`/approvals/${id}/return-node`, body),
  resubmit: (id, body) => apiClient.post(`/approvals/${id}/resubmit`, body),
  flowList: (params = {}) => {
    const query = new URLSearchParams({ pageNo: '1', pageSize: '100' });
    if (params.approvalType) query.set('approvalType', params.approvalType);
    if (params.status) query.set('status', params.status);
    return apiClient.get(`/approval-flows?${query}`);
  },
  flowDetail: (id) => apiClient.get(`/approval-flows/${id}`),
  flowVersions: (approvalType) => apiClient.get(`/approval-flows/types/${approvalType}/versions`),
  activeFlow: (approvalType) => apiClient.get(`/approval-flows/types/${approvalType}/active`),
  maintenance: (id) => apiClient.post(`/approval-flows/${id}/maintenance`, {}),
  updateFlow: (id, body) => apiClient.put(`/approval-flows/${id}`, body),
  publishFlow: (id) => apiClient.post(`/approval-flows/${id}/publish`, {})
};

export const tireApi = {
  list: () => apiClient.get('/tires?pageNo=1&pageSize=100'),
  request: (body) => apiClient.post('/tire-requests', body),
  requestDetail: (id) => apiClient.get(`/tire-requests/${id}`),
  ocr: (attachmentId) => apiClient.post('/ocr/tire-number', { attachmentId }),
  confirmOcr: (id, body) => apiClient.post(`/ocr/${id}/confirm`, body)
};

export const salaryApi = {
  list: () => apiClient.get('/driver-salaries?pageNo=1&pageSize=100'),
  create: (body) => apiClient.post('/driver-salaries', body),
  history: (routeId) => apiClient.get(`/driver-salaries/routes/${routeId}/history`)
};

export const reportApi = {
  profit: () => apiClient.get('/reports/profit'),
  vehicle: () => apiClient.get('/reports/vehicle-expense'),
  driver: () => apiClient.get('/reports/driver-expense'),
  attendance: () => apiClient.get('/reports/attendance'),
  energy: () => apiClient.get('/reports/energy')
};

export const settlementApi = {
  versions: (month) => apiClient.get(`/settlements/${month}/versions`),
  generate: (month) => apiClient.post(`/settlements/${month}/versions`, {}),
  details: (id) => apiClient.get(`/settlements/versions/${id}/details`),
  diff: (id) => apiClient.get(`/settlements/versions/${id}/diff`)
};

export const systemApi = {
  me: () => apiClient.get('/me'),
  users: () => apiClient.get('/users?pageNo=1&pageSize=100'),
  createUser: (body) => apiClient.post('/users', body),
  updateUser: (id, body) => apiClient.put(`/users/${id}`, body),
  resetPassword: (id, body) => apiClient.post(`/users/${id}/reset-password`, body),
  setStatus: (id, body) => apiClient.put(`/users/${id}/status`, body),
  roles: () => apiClient.get('/roles?pageNo=1&pageSize=100'),
  role: (id) => apiClient.get(`/roles/${id}`),
  createRole: (body) => apiClient.post('/roles', body),
  updateRole: (id, body) => apiClient.put(`/roles/${id}`, body),
  setRoleStatus: (id, status) => apiClient.put(`/roles/${id}/status?status=${status}`, {}),
  saveRolePermissions: (id, permissionCodes) => apiClient.put(`/roles/${id}/permissions`, { permissionCodes }),
  permissions: (permissionType = '') => apiClient.get(`/permissions${permissionType ? `?permissionType=${permissionType}` : ''}`),
  auditLogs: (params = {}) => {
    const query = new URLSearchParams({ pageNo: '1', pageSize: '100' });
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') query.set(key, value);
    });
    return apiClient.get(`/audit-logs?${query}`);
  }
};
