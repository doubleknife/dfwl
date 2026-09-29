import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = (path) => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8');
const calls = [];
globalThis.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
const { masterApi, routeApi, routeTemplateApi, salaryApi, systemApi, attachmentApi } = await import('../src/api/modules.js');
globalThis.fetch = async (url, options = {}) => {
  calls.push({ url: String(url), options });
  return { ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => ({ code: '0', data: { records: [] } }) };
};

const tests = [
  ['active navigation contains only V3-A pages', () => {
    const router = source('router/routes.js');
    for (const key of ['master', 'routes', 'salaries', 'system']) assert.match(router, new RegExp(`key: '${key}'`));
    for (const key of ['dashboard', 'imports', 'expenses', 'approvals', 'approvalFlows', 'tires', 'reports', 'settlements']) {
      assert.doesNotMatch(router, new RegExp(`key: '${key}'`));
    }
  }],
  ['task API has one final resource and no legacy route writes', async () => {
    calls.length = 0;
    await routeApi.list(2, 20, { status: 'IN_TRANSIT' });
    await routeApi.transfer(9, { driverId: 3 });
    await routeApi.adjustWeight(9, { grossWeight: 30, tareWeight: 5 });
    assert.match(calls[0].url, /\/transport-tasks\?pageNo=2&pageSize=20&status=IN_TRANSIT$/);
    assert.match(calls[1].url, /\/transport-tasks\/9\/transfer$/);
    assert.match(calls[2].url, /\/transport-tasks\/9\/weight-adjustments$/);
    assert.doesNotMatch(calls.map((call) => call.url).join(' '), /\/routes(?:\/|\?)/);
  }],
  ['template API separates defaults from task execution', async () => {
    calls.length = 0;
    await routeTemplateApi.create({ name: '线路模板' });
    await routeTemplateApi.setEnabled(7, false, 1);
    assert.match(calls[0].url, /\/route-templates$/);
    assert.match(calls[1].url, /\/route-templates\/7\/disable$/);
    const taskView = source('views/TransportTasksV3View.vue');
    assert.match(taskView, /defaultUnitPrice/);
    assert.match(taskView, /actualUnitPrice|amountHistory/);
  }],
  ['participant salary is read from the final task participant', async () => {
    calls.length = 0;
    await salaryApi.list(1, 3, '2026-09', 20);
    await salaryApi.history(11);
    assert.match(calls[0].url, /\/driver-salaries\?pageNo=1&pageSize=20&driverId=3&month=2026-09$/);
    assert.match(calls[1].url, /\/driver-salaries\/11\/history$/);
    const view = source('views/ParticipantSalariesV3View.vue');
    assert.match(view, /participantId/);
    assert.doesNotMatch(view, /salaryApi\.create|salaryApi\.update/);
  }],
  ['master data and binding use final live APIs', async () => {
    calls.length = 0;
    await masterApi.products();
    await masterApi.bindDriverVehicle(3, { vehicleId: 4 });
    assert.match(calls[0].url, /\/products\?/);
    assert.match(calls[1].url, /\/drivers\/3\/bind-vehicle$/);
    assert.match(source('components/BindingManagement.vue'), /确认解绑/);
  }],
  ['system management and task evidence remain available', async () => {
    calls.length = 0;
    await systemApi.users();
    await attachmentApi.list('TASK', 9, 'WEIGHT_ADJUST');
    assert.match(calls[0].url, /\/users\?/);
    assert.match(calls[1].url, /\/attachments\?/);
    assert.match(source('views/TransportTasksV3View.vue'), /原始卸货凭证/);
  }]
];

for (const [name, run] of tests) { await run(); console.log(`ok - ${name}`); }
console.log(`${tests.length} active V3-A admin tests passed`);
await import('./ui1Tests.mjs');
await import('./routeWeightEvidenceTests.mjs');
