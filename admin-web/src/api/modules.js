import { apiClient } from './client.js';

export const dashboardApi = {
  summary: () => apiClient.get('/reports/dashboard'),
  profit: () => apiClient.get('/reports/profit'),
  expenses: () => apiClient.get('/expenses?pageNo=1&pageSize=20'),
  approvals: () => apiClient.get('/approvals?pageNo=1&pageSize=20'),
  routes: () => apiClient.get('/transport-tasks?pageNo=1&pageSize=20')
};

export const masterApi = {
  drivers: (pageNo = 1, pageSize = 100) => apiClient.get(`/drivers?pageNo=${pageNo}&pageSize=${pageSize}`),
  driver: (id) => apiClient.get(`/drivers/${id}`),
  vehicles: (pageNo = 1, pageSize = 100) => apiClient.get(`/vehicles?pageNo=${pageNo}&pageSize=${pageSize}`),
  vehicle: (id) => apiClient.get(`/vehicles/${id}`),
  trailers: (pageNo = 1, pageSize = 100) => apiClient.get(`/trailers?pageNo=${pageNo}&pageSize=${pageSize}`),
  products: (pageNo = 1, pageSize = 100) => apiClient.get(`/products?pageNo=${pageNo}&pageSize=${pageSize}`),
  createDriver: (body) => apiClient.post('/drivers', body),
  createVehicle: (body) => apiClient.post('/vehicles', body),
  updateVehicle: (id, body) => apiClient.put(`/vehicles/${id}`, body),
  createTrailer: (body) => apiClient.post('/trailers', body),
  updateTrailer: (id, body) => apiClient.put(`/trailers/${id}`, body),
  createProduct: (body) => apiClient.post('/products', body),
  updateProduct: (id, body) => apiClient.put(`/products/${id}`, body),
  setProductStatus: (id, status) => apiClient.put(`/products/${id}/status`, { status }),
  bindDriverVehicle: (driverId, body) => apiClient.post(`/drivers/${driverId}/bind-vehicle`, body),
  unbindDriverVehicle: (driverId, body = {}) => apiClient.post(`/drivers/${driverId}/unbind-vehicle`, body),
  bindTrailerVehicle: (trailerId, body) => apiClient.post(`/trailers/${trailerId}/bind-vehicle`, body),
  unbindTrailerVehicle: (trailerId, body = {}) => apiClient.post(`/trailers/${trailerId}/unbind-vehicle`, body),
  vehicleHistory: (vehicleId) => apiClient.get(`/vehicles/${vehicleId}/binding-history`),
  driverHistory: (driverId) => apiClient.get(`/drivers/${driverId}/binding-history`),
  trailerHistory: (vehicleId) => apiClient.get(`/vehicles/${vehicleId}/trailer-binding-history`)
};

export const routeApi = {
  list: (pageNo = 1, pageSize = 100, filters = {}) => {
    const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) });
    for (const [key, value] of Object.entries(filters)) if (value !== '' && value != null) params.set(key, String(value));
    return apiClient.get(`/transport-tasks?${params}`);
  },
  detail: (id) => apiClient.get(`/transport-tasks/${id}`),
  participants: (id) => apiClient.get(`/transport-tasks/${id}/participants`),
  transfer: (id, body) => apiClient.post(`/transport-tasks/${id}/transfer`, body),
  depart: (id, body) => apiClient.post(`/transport-tasks/${id}/depart`, body),
  cancel: (id, body) => apiClient.post(`/transport-tasks/${id}/cancel`, body),
  adjustWeight: (id, body) => apiClient.post(`/transport-tasks/${id}/weight-adjustments`, body),
  weightVersions: (id) => apiClient.get(`/transport-tasks/${id}/weight-versions`),
  amountHistory: (id) => apiClient.get(`/transport-tasks/${id}/amount-history`),
  adjustAmount: (id, body) => apiClient.post(`/transport-tasks/${id}/amount-adjustments`, body),
  confirmSalaries: (id, body) => apiClient.post(`/transport-tasks/${id}/participant-salaries/confirm`, body)
};

export const routeTemplateApi = {
  list: () => apiClient.get('/route-templates'),
  detail: (id) => apiClient.get(`/route-templates/${id}`),
  create: (body) => apiClient.post('/route-templates', body),
  update: (id, body) => apiClient.patch(`/route-templates/${id}`, body),
  setEnabled: (id, enabled, expectedVersion) => apiClient.post(`/route-templates/${id}/${enabled ? 'enable' : 'disable'}`, { expectedVersion })
};

export const expenseApi = {
  list: (pageNo = 1, pageSize = 100, filters = {}) => {
    const query = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) });
    for (const [key, value] of Object.entries(filters)) if (value != null && value !== '') query.set(key, String(value));
    return apiClient.get(`/expenses?${query}`);
  },
  eligibleRouteVehicles: (routeId) => apiClient.get(`/expenses/eligible-route-vehicles?routeId=${routeId}`),
  create: (body) => apiClient.post('/expenses', body),
  update: (id, body) => apiClient.put(`/expenses/${id}`, body),
  delete: (id) => apiClient.delete(`/expenses/${id}`),
  reverse: (id, body) => apiClient.post(`/expenses/${id}/reversal`, body),
  reversalRecord: (id) => apiClient.get(`/expenses/${id}/reversal-record`),
  attribution: (id, body) => apiClient.put(`/expenses/${id}/attribution`, body),
  attributionHistory: (id) => apiClient.get(`/expenses/${id}/attribution-history`),
  approval: (id) => apiClient.get(`/expenses/${id}/approval`)
};

export const approvalApi = {
  list: (scope = 'ALL', pageNo = 1, pageSize = 20, filters = {}) => {
    const query = new URLSearchParams({ scope, pageNo: String(pageNo), pageSize: String(pageSize) });
    if (filters.approvalType) query.set('approvalType', filters.approvalType);
    if (filters.status) query.set('status', filters.status);
    return apiClient.get(`/approvals?${query}`);
  },
  detail: (id) => apiClient.get(`/approvals/${id}`),
  create: (body) => apiClient.post('/approvals', body),
  approve: (id, body) => apiClient.post(`/approvals/${id}/approve`, body),
  returnApplicant: (id, body) => apiClient.post(`/approvals/${id}/return-applicant`, body),
  returnNode: (id, body) => apiClient.post(`/approvals/${id}/return-node`, body),
  resubmit: (id, body) => apiClient.post(`/approvals/${id}/resubmit`, body)
};

export const approvalTemplateApi = {
  available: () => apiClient.get('/approval-templates/available'),
  list: () => apiClient.get('/approval-templates'),
  detail: (templateId) => apiClient.get(`/approval-templates/${templateId}`),
  versions: (templateId) => apiClient.get(`/approval-templates/${templateId}/versions`),
  active: (templateId) => apiClient.get(`/approval-templates/${templateId}/active`),
  version: (templateId, flowId) => apiClient.get(`/approval-templates/${templateId}/versions/${flowId}`),
  create: (body) => apiClient.post('/approval-templates', body),
  maintenance: (flowId) => apiClient.post(`/approval-flows/${flowId}/maintenance`, {}),
  createNextVersion: (flowId, body) => apiClient.put(`/approval-flows/${flowId}`, body),
  publish: (flowId) => apiClient.post(`/approval-flows/${flowId}/publish`, {})
};

export const attachmentApi = {
  list: (ownerType, ownerId, purpose) => apiClient.get(`/attachments?${new URLSearchParams({ ownerType, ownerId: String(ownerId), purpose })}`),
  upload: (formData) => apiClient.upload('/attachments', formData),
  download: (id) => apiClient.download(`/attachments/${id}`)
};

export const tireApi = {
  list: (pageNo = 1, pageSize = 20, filters = {}) => {
    const query = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) });
    if (filters.tireNo) query.set('tireNo', filters.tireNo);
    if (filters.status) query.set('status', filters.status);
    if (filters.vehicleId) query.set('vehicleId', String(filters.vehicleId));
    return apiClient.get(`/tires?${query}`);
  },
  request: (body) => apiClient.post('/tire-requests', body),
  requestDetail: (id) => apiClient.get(`/tire-requests/${id}`),
  ocr: (attachmentId) => apiClient.post('/ocr/tire-number', { attachmentId }),
  confirmOcr: (id, body) => apiClient.post(`/ocr/${id}/confirm`, body)
};

export const salaryApi = {
  list: (pageNo = 1, driverId = '', month = '', pageSize = 100) => {
    const query = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) });
    if (driverId) query.set('driverId', String(driverId));
    if (month) query.set('month', month);
    return apiClient.get(`/driver-salaries?${query}`);
  },
  history: (participantId) => apiClient.get(`/driver-salaries/${participantId}/history`)
};

export const reportApi = {
  profit: () => apiClient.get('/reports/profit'),
  completedRoutes: (pageNo = 1, pageSize = 20, status = '') => apiClient.get(`/reports/completed-routes?pageNo=${pageNo}&pageSize=${pageSize}${status ? `&status=${status}` : ''}`),
  routeArchive: (id, expensePageNo = 1, expensePageSize = 20) => apiClient.get(`/reports/completed-routes/${id}?expensePageNo=${expensePageNo}&expensePageSize=${expensePageSize}`),
  vehicle: () => apiClient.get('/reports/vehicle-expense'),
  driver: () => apiClient.get('/reports/driver-expense'),
  attendance: () => apiClient.get('/reports/attendance'),
  energy: () => apiClient.get('/reports/energy')
};

export const settlementApi = {
  versions: (month) => apiClient.get(`/settlements/${month}/versions`),
  generate: (month) => apiClient.post(`/settlements/${month}/versions`, {}),
  details: (id) => apiClient.get(`/settlements/versions/${id}/details`),
  diff: (id) => apiClient.get(`/settlements/versions/${id}/diff`),
  detailsPage: (id, pageNo = 1, pageSize = 20) => apiClient.get(`/settlements/versions/${id}/details-page?pageNo=${pageNo}&pageSize=${pageSize}`),
  diffPage: (id, pageNo = 1, pageSize = 20) => apiClient.get(`/settlements/versions/${id}/diff-page?pageNo=${pageNo}&pageSize=${pageSize}`)
};

export const systemApi = {
  me: () => apiClient.get('/me'),
  users: (pageNo = 1, pageSize = 100) => apiClient.get(`/users?pageNo=${pageNo}&pageSize=${pageSize}`),
  createUser: (body) => apiClient.post('/users', body),
  updateUser: (id, body) => apiClient.put(`/users/${id}`, body),
  resetPassword: (id, body) => apiClient.post(`/users/${id}/reset-password`, body),
  setStatus: (id, body) => apiClient.put(`/users/${id}/status`, body),
  roles: (pageNo = 1, pageSize = 100) => apiClient.get(`/roles?pageNo=${pageNo}&pageSize=${pageSize}`),
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
