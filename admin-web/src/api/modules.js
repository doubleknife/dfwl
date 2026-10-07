import { apiClient } from './client.js';

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

export const dispatchApi = {
  preview: (batch) => apiClient.post('/dispatch-batches/preview', batch),
  confirm: (body) => apiClient.post('/dispatch-batches/confirm', body),
  detail: (id) => apiClient.get(`/dispatch-batches/${id}`)
};

export const taskImportApi = {
  template: () => apiClient.download('/task-imports/template'),
  preview: (file, requestKey) => {
    const form = new FormData();
    form.append('file', file);
    form.append('requestKey', requestKey);
    return apiClient.upload('/task-imports/preview', form);
  },
  confirm: (id) => apiClient.post(`/task-imports/${id}/confirm`, {}),
  history: (pageNo = 1, pageSize = 20) => apiClient.get(`/task-imports?pageNo=${pageNo}&pageSize=${pageSize}`),
  detail: (id) => apiClient.get(`/task-imports/${id}`)
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

export const v3ApprovalApi = {
  list: (scope, pageNo = 1) => apiClient.get(`/approvals?scope=${scope}&pageNo=${pageNo}&pageSize=20`),
  detail: (id) => apiClient.get(`/approvals/${id}`),
  available: () => apiClient.get('/approval-templates/available'),
  templates: () => apiClient.get('/approval-templates'),
  options: () => apiClient.get('/approval-templates/options'),
  flow: (id) => apiClient.get(`/approval-flows/${id}`),
  createTemplate: (body) => apiClient.post('/approval-templates', body),
  nextVersion: (id, body) => apiClient.post(`/approval-templates/${id}/versions`, body),
  publish: (id) => apiClient.post(`/approval-flows/${id}/publish`, {}),
  create: (body) => apiClient.post('/approvals', body),
  resubmit: (id, body) => apiClient.post(`/approvals/${id}/resubmit`, body),
  approve: (id, body) => apiClient.post(`/approvals/${id}/approve`, body),
  returnStep: (id, body) => apiClient.post(`/approvals/${id}/return-step`, body),
  returnApplicant: (id, body) => apiClient.post(`/approvals/${id}/return-applicant`, body),
  expenseOptions: () => apiClient.get('/expense-applications/options'),
  taskCandidates: (driverId) => apiClient.get(`/expense-applications/task-candidates?driverId=${driverId}`),
  dailyTemplate: () => apiClient.download('/expense-applications/daily-template'),
  dailyPreview: (file) => {
    const form = new FormData(); form.append('file', file);
    return apiClient.upload('/expense-applications/daily-preview', form);
  },
  dailySubmit: (file, templateId, requestKey) => {
    const form = new FormData(); form.append('file', file); form.append('templateId', templateId);
    form.append('requestKey', requestKey);
    return apiClient.upload('/expense-applications/daily-submit', form);
  }
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
    return apiClient.get(`/tires?${query}`);
  },
  detail: (id) => apiClient.get(`/tires/${id}`),
  edit: (id, body) => apiClient.patch(`/tires/${id}`, body),
  template: () => apiClient.download('/tire-imports/template'),
  preview: (file) => {
    const form = new FormData(); form.append('file', file);
    return apiClient.upload('/tire-imports/preview', form);
  },
  confirm: (id) => apiClient.post(`/tire-imports/${id}/confirm`, {}),
  imports: (pageNo = 1) => apiClient.get(`/tire-imports?pageNo=${pageNo}&pageSize=20`),
  importDetail: (id) => apiClient.get(`/tire-imports/${id}`)
};

export const salaryApi = {
  list: (filters = {}, pageNo = 1, pageSize = 20) => apiClient.get(`/driver-salaries?${query(filters, pageNo, pageSize)}`),
  options: (kind, search = '', month = '', pageNo = 1, pageSize = 20) => apiClient.get(`/driver-salaries/filter-options?${query({ kind, search, month }, pageNo, pageSize)}`),
  summary: (filters = {}) => apiClient.get(`/driver-salaries/summary?${query(filters)}`),
  drivers: (filters = {}, pageNo = 1, pageSize = 20) => apiClient.get(`/driver-salaries/drivers?${query(filters, pageNo, pageSize)}`),
  export: (filters = {}) => apiClient.download(`/driver-salaries/export?${query(filters)}`),
  history: (participantId) => apiClient.get(`/driver-salaries/${participantId}/history`)
};

function query(filters = {}, pageNo, pageSize) {
  const params = new URLSearchParams();
  if (pageNo != null) params.set('pageNo', String(pageNo));
  if (pageSize != null) params.set('pageSize', String(pageSize));
  for (const [key, value] of Object.entries(filters)) if (value !== '' && value != null) params.set(key, String(value));
  return params;
}

export const attendanceApi = {
  list: (filters, pageNo = 1, pageSize = 20) => apiClient.get(`/attendance?${query(filters, pageNo, pageSize)}`),
  drivers: (pageNo = 1, search = '') => apiClient.get(`/attendance/drivers?${query({ search }, pageNo, 20)}`),
  export: (filters) => apiClient.download(`/attendance/export?${query(filters)}`)
};

export const dispatchLedgerApi = {
  list: (scope, filters, pageNo = 1, pageSize = 20) => apiClient.get(`/dispatch-ledger/${scope === 'OUTSOURCED' ? 'outsourced' : 'internal'}?${query(filters, pageNo, pageSize)}`),
  export: (scope, filters) => apiClient.download(`/dispatch-ledger/export?${query({ ...filters, scope })}`)
};

export const v3ExpenseLedgerApi = {
  list: (filters, pageNo = 1) => apiClient.get(`/expense-ledger?${query(filters, pageNo, 20)}`),
  summary: (filters) => apiClient.get(`/expense-ledger/summary?${query(filters)}`),
  options: (kind, search = '', pageNo = 1, vehicleId = '') => apiClient.get(`/expense-ledger/filter-options?${query({ kind, search, vehicleId, pageNo })}`),
  detail: (id) => apiClient.get(`/expense-ledger/${id}`),
  attribute: (id, body) => apiClient.post(`/expense-ledger/${id}/attribution`, body),
  reverse: (id, body) => apiClient.post(`/expense-ledger/${id}/reverse`, body),
  export: (filters) => apiClient.download(`/expense-ledger/export?${query(filters)}`)
};

export const v3FinanceApi = {
  taskSummaries: (taskIds) => apiClient.get(`/finance/task-summaries?${new URLSearchParams(taskIds.map((id) => ['taskIds', String(id)]))}`),
  tasks: (filters, pageNo = 1) => apiClient.get(`/finance/tasks?${query(filters, pageNo, 20)}`),
  task: (id) => apiClient.get(`/finance/tasks/${id}`),
  templates: (filters, pageNo = 1) => apiClient.get(`/finance/templates?${query(filters, pageNo, 20)}`),
  company: (month) => apiClient.get(`/finance/company?month=${encodeURIComponent(month)}`),
  dashboard: (month) => apiClient.get(`/finance/dashboard?month=${encodeURIComponent(month)}`)
};

export const v3SettlementApi = {
  versions: (month) => apiClient.get(`/settlements/${encodeURIComponent(month)}/versions`),
  generate: (month) => apiClient.post(`/settlements/${encodeURIComponent(month)}/versions`, {}),
  version: (id) => apiClient.get(`/settlements/versions/${id}`),
  details: (id, pageNo = 1) => apiClient.get(`/settlements/versions/${id}/details?${query({}, pageNo, 20)}`),
  diff: (id, pageNo = 1) => apiClient.get(`/settlements/versions/${id}/diff?${query({}, pageNo, 20)}`)
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
