import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = (file) => readFileSync(new URL(`../src/${file}`, import.meta.url), 'utf8');
const binding = source('components/BindingManagement.vue');
const expenses = source('views/ExpensesUi1View.vue');
const salaries = source('views/SalariesUi1View.vue');
const system = source('views/SystemUi1View.vue');
const routes = source('router/routes.js');

const tests = [
  ['binding uses business choices, server paging and per-object unbind confirmation', () => {
    assert.match(binding, /司机车辆绑定/);
    assert.match(binding, /车辆车挂绑定/);
    assert.match(binding, /<EntityPicker[^>]+:load-page="masterApi\.vehicles"/);
    assert.match(binding, /<EntityPicker[^>]+:load-page="masterApi\.trailers"/);
    assert.match(binding, /<ConfirmDialog[^>]+确认解绑/);
    assert.match(binding, /更换绑定/);
    assert.match(binding, /后续绑定失败，该对象会保持未绑定/);
    assert.match(binding, /driverHistory\(row\.id\)/);
    assert.match(binding, /trailerHistory\(row\.id\)/);
    assert.doesNotMatch(binding, /aria-label="(?:司机|车辆|车挂) ID"/);
  }],
  ['expense entry is separate from list and reversal requires danger confirmation', () => {
    assert.match(expenses, /<FormDialog[^>]+title="人工新增费用"/);
    assert.match(expenses, /form\.attributionType === 'ROUTE'/);
    assert.match(expenses, /<ConfirmDialog[^>]+title="最终确认冲销"/);
    assert.match(expenses, /reversalOfId/);
    assert.doesNotMatch(expenses, /approvalApi\.create/);
  }],
  ['salary entry uses selected driver and route, with history in detail drawer', () => {
    assert.match(salaries, /:load-page="routeApi\.list"/);
    assert.match(salaries, /:load-page="masterApi\.drivers"/);
    assert.match(salaries, /<DetailDrawer[^>]+title="工资详情与历史"/);
    assert.match(salaries, /navigate\('imports'\)/);
    assert.doesNotMatch(salaries, /aria-label="(?:司机|线路) ID"/);
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
    assert.match(system, /toggleModule\(items/);
    assert.match(system, /displayValue\('permissionType'/);
    assert.match(system, /权限目录/);
    assert.doesNotMatch(system, /createPermission|updatePermission|deletePermission/);
  }],
  ['UI1 routes use new views without altering UI0 sample routes', () => {
    assert.match(routes, /ExpensesUi1View\.vue/);
    assert.match(routes, /SalariesUi1View\.vue/);
    assert.match(routes, /SystemUi1View\.vue/);
    assert.match(routes, /RoutesSampleView\.vue/);
  }]
];

for (const [name, run] of tests) { run(); console.log(`ok - ${name}`); }
console.log(`${tests.length} UI1 tests passed`);
