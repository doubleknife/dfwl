import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = (path) => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8');
const calls = [];
globalThis.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
const { masterApi, routeApi, routeTemplateApi, dispatchApi, taskImportApi,
  salaryApi, attendanceApi, dispatchLedgerApi, systemApi, attachmentApi } = await import('../src/api/modules.js');
const { appendDispatchRows, updateSelectedAmounts, dispatchBody } = await import('../src/views/dispatchDraft.js');
globalThis.fetch = async (url, options = {}) => {
  calls.push({ url: String(url), options });
  return { ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => ({ code: '0', data: { records: [] } }) };
};

const tests = [
  ['active navigation contains current V3-A/B/C pages', () => {
    const router = source('router/routes.js');
    for (const key of ['master', 'routes', 'dispatch', 'task-imports', 'salaries', 'attendance', 'dispatch-internal', 'dispatch-outsourced', 'system'])
      assert.match(router, new RegExp(`key: '${key}'`));
    for (const key of ['dashboard', 'imports', 'expenses', 'approvals', 'approvalFlows', 'tires', 'reports', 'settlements']) {
      assert.doesNotMatch(router, new RegExp(`key: '${key}'`));
    }
  }],
  ['dispatch draft preserves one task per row and only selected amount changes', () => {
    const template = { stableCode: 'TPL-01', name: '砂石运输', customerName: '中文客户',
      loadingPlace: '昆明', unloadingPlace: '曲靖', defaultUnitPrice: '100.00',
      defaultInformationFee: '10.00', defaultDriverSalary: '20.00' };
    const driver = { id: 11, name: '司机甲', driverType: 'INTERNAL', currentVehiclePlateNo: '云A001' };
    const two = appendDispatchRows([], template, [driver, driver], 1);
    assert.equal(two.length, 2);
    assert.notEqual(two[0].localKey, two[1].localKey);
    const changed = updateSelectedAmounts(two, [2], 'initialSalary', '0.00');
    assert.equal(changed[0].initialSalary, '20.00');
    assert.equal(changed[1].initialSalary, '0.00');
    assert.equal(template.defaultDriverSalary, '20.00');
    assert.equal(dispatchBody('2026-09-30', changed).rows.length, 2);
    assert.throws(() => updateSelectedAmounts(two, [1], 'initialSalary', '-1'));
  }],
  ['dispatch and fixed import use distinct official APIs', async () => {
    calls.length = 0;
    await dispatchApi.preview({ businessDate: '2026-09-30', rows: [] });
    await dispatchApi.confirm({ previewId: 'p', requestKey: 'k', batch: {} });
    await taskImportApi.history();
    assert.match(calls[0].url, /\/dispatch-batches\/preview$/);
    assert.match(calls[1].url, /\/dispatch-batches\/confirm$/);
    assert.match(calls[2].url, /\/task-imports\?pageNo=1&pageSize=20$/);
    assert.match(source('views/DispatchWorkbenchV3View.vue'), /确认整批发布/);
    assert.match(source('views/TaskImportsV3View.vue'), /逐行结果/);
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
  ['salary, attendance and dispatch month views use distinct official read contracts', async () => {
    calls.length = 0;
    await salaryApi.list({ month: '2026-09', driverId: 3, transferOnly: true }, 1, 20);
    await salaryApi.summary({ month: '2026-09' });
    await salaryApi.options('DRIVER', '张', '2026-09');
    await salaryApi.history(11);
    await attendanceApi.list({ month: '2026-09' });
    await dispatchLedgerApi.list('OUTSOURCED', { month: '2026-09' });
    assert.match(calls[0].url, /\/driver-salaries\?pageNo=1&pageSize=20&month=2026-09&driverId=3&transferOnly=true$/);
    assert.match(calls[1].url, /\/driver-salaries\/summary\?month=2026-09$/);
    assert.match(calls[2].url, /\/driver-salaries\/filter-options\?pageNo=1&pageSize=20&kind=DRIVER&search=/);
    assert.match(calls[3].url, /\/driver-salaries\/11\/history$/);
    assert.match(calls[4].url, /\/attendance\?pageNo=1&pageSize=20&month=2026-09$/);
    assert.match(calls[5].url, /\/dispatch-ledger\/outsourced\?pageNo=1&pageSize=20&month=2026-09$/);
    const view = source('views/SalaryLedgerV3View.vue');
    assert.match(view, /转派工资核对/);
    assert.match(view, /confirmSalaries/);
    assert.doesNotMatch(view, /salaryApi\.create|salaryApi\.update/);
    assert.match(source('views/MonthlyOperationsV3View.vue'), /月合计/);
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
console.log(`${tests.length} active V3-A/B/C admin tests passed`);
await import('./ui1Tests.mjs');
await import('./routeWeightEvidenceTests.mjs');
