import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = (file) => readFileSync(new URL(`../src/${file}`, import.meta.url), 'utf8');
const binding = source('components/BindingManagement.vue');
const salaries = source('views/SalaryLedgerV3View.vue');
const system = source('views/SystemUi1View.vue');
const audit = source('components/AuditLogsUi2.vue');
const routes = source('router/routes.js');

const tests = [
  ['binding uses business choices, server paging and per-object unbind confirmation', () => {
    assert.match(binding, /司机车辆绑定/);
    assert.match(binding, /车辆车挂绑定/);
    assert.match(binding, /<EntityPicker[^>]+:load-page="masterApi\.bindingVehicles"/);
    assert.match(binding, /<EntityPicker[^>]+:load-page="masterApi\.bindingTrailers"/);
    assert.match(binding, /<ConfirmDialog[^>]+确认解绑/);
    assert.match(binding, /更换绑定/);
    assert.match(binding, /后续绑定失败，该对象会保持未绑定/);
    assert.match(binding, /driverHistory\(row\.id\)/);
    assert.match(binding, /trailerHistory\(row\.id\)/);
    assert.doesNotMatch(binding, /aria-label="(?:司机|车辆|车挂) ID"/);
  }],
  ['participant salary view reads the unique salary fact and history', () => {
    assert.match(salaries, /:load-page="loadDriverOptions"/);
    assert.match(salaries, /<DetailDrawer[^>]+:title=/);
    assert.match(salaries, /salaryApi\.history\(item\.id\)/);
    assert.match(salaries, /historyByParticipant\[item\.id\]/);
    assert.doesNotMatch(salaries, /salaryApi\.create|司机编号/);
  }],
  ['password reset is scoped to a selected user and fixed roles are labeled', () => {
    assert.match(system, /function openReset\(row\).*resetTarget\.value = row/);
    assert.match(system, /type="password"/);
    assert.match(system, /确认新密码/);
    assert.match(system, /row\.systemFixed/);
    assert.doesNotMatch(system, /resetForm\.userId/);
  }],
  ['role permission changes show grouped summary and directory stays read only', () => {
    assert.match(system, /addedCodes\.length/);
    assert.match(system, /removedCodes\.length/);
    assert.match(system, /<RolePermissionTree :model="pagePermissionTree"/);
    assert.match(system, /toggleTreeCodes/);
    assert.match(system, /权限目录为空，请刷新后重试/);
    assert.match(system, /displayValue\('permissionType'/);
    assert.match(system, /权限目录/);
    assert.doesNotMatch(system, /createPermission|updatePermission|deletePermission/);
  }],
  ['system settings use real operations and show successful results only after API completion', () => {
    assert.match(system, /await systemApi\.saveRolePermissions\(permissionRole\.value\.id, selectedCodes\.value\)/);
    assert.match(system, /await systemApi\.resetPassword\(resetTarget\.value\.id/);
    assert.match(system, /await systemApi\.setStatus\(userToggle\.value\.id/);
    assert.match(system, /await authStore\.loadMe\(\)/);
    assert.match(system, /if \(!error\.value && !authStore\.error\) success\.value = '角色权限已保存/);
    assert.match(system, /role="status">\{\{ success \}\}/);
    assert.match(system, /title="用户详情"/);
  }],
  ['audit table uses business labels while raw action stays in read only detail', () => {
    assert.match(audit, /REPLACE_PERMISSIONS: '修改角色权限'/);
    assert.match(audit, /APPROVE: '同意当前审批步骤'/);
    assert.match(audit, /function moduleName\(value\).*'其他业务模块'/);
    assert.match(audit, /原始动作代码/);
    assert.match(audit, /原始业务类型/);
    assert.doesNotMatch(audit, /@click="(?:save|delete|update)/);
  }],
  ['V3 task and salary views replace legacy route and salary write pages', () => {
    assert.doesNotMatch(routes, /ExpensesUi1View\.vue/);
    assert.match(routes, /SalaryLedgerV3View\.vue/);
    assert.match(routes, /MonthlyOperationsV3View\.vue/);
    assert.match(routes, /SystemUi1View\.vue/);
    assert.match(routes, /TransportTasksV3View\.vue/);
  }]
];

for (const [name, run] of tests) { run(); console.log(`ok - ${name}`); }
console.log(`${tests.length} UI1 tests passed`);
