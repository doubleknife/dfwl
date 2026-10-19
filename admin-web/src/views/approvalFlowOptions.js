export function normalizeFlowOptions(response) {
  const value = response && typeof response === 'object' ? response : {};
  return {
    roles: Array.isArray(value.roles) ? value.roles : [],
    approverRoles: Array.isArray(value.approverRoles) ? value.approverRoles : [],
    approvers: Array.isArray(value.approvers) ? value.approvers : [],
    expenseTypes: Array.isArray(value.expenseTypes) ? value.expenseTypes : [],
    hasApproverRoles: Array.isArray(value.approverRoles)
  };
}

export function missingApprovalPermissions(role) {
  const missing = [];
  // The server returns eligible roles with their page and button action flags.
  const granted = (value) => value === true || value === 1 || value === '1';
  if (role.canProcessApproval !== undefined && !granted(role.canProcessApproval)) missing.push('处理审批');
  return missing;
}
