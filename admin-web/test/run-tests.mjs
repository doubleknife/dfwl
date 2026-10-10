import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = (path) => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8');
const calls = [];
globalThis.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
const { masterApi, routeApi, routeTemplateApi, dispatchApi, taskImportApi,
  salaryApi, attendanceApi, dispatchLedgerApi, systemApi, attachmentApi, v3ApprovalApi,
  v3ExpenseLedgerApi, v3FinanceApi, v3SettlementApi } = await import('../src/api/modules.js');
const { appendDispatchRows, updateSelectedAmounts, dispatchBody } = await import('../src/views/dispatchDraft.js');
const { appendPreviewRows, dailyItems, emptyExpenseRow } = await import('../src/views/expenseApplicationDraft.js');
globalThis.fetch = async (url, options = {}) => {
  calls.push({ url: String(url), options });
  return { ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => ({ code: '0', data: { records: [] } }) };
};

const tests = [
  ['active navigation groups final V3 pages and removes retired flat entries', () => {
    const router = source('router/routes.js');
    for (const key of ['dashboard', 'drivers', 'vehicles', 'trailers', 'binding', 'products', 'templates', 'routes', 'dispatch', 'task-imports', 'salaries', 'attendance', 'dispatch-internal', 'dispatch-outsourced', 'expense-apply', 'expense-ledger', 'expense-attribution', 'approvals-todo', 'approvals-mine', 'finance-tasks', 'finance-templates', 'finance-company', 'settlements', 'users', 'roles', 'audit', 'tires', 'tire-imports'])
      assert.match(router, new RegExp(`key: '${key}'`));
    for (const key of ['imports', 'approvalFlows', 'reports']) {
      assert.doesNotMatch(router, new RegExp(`key: '${key}'`));
    }
    for (const label of ['工作台', '运输调度', '费用管理', '工资管理', '审批中心', '车辆与基础资料', '经营分析', '系统管理'])
      assert.match(router, new RegExp(label));
    assert.match(router, /visibleNavigation/);
    assert.match(router, /navigationTrail/);
  }],
  ['V3-E ledger and finance pages use formal APIs and business fields', async () => {
    calls.length = 0;
    await v3ExpenseLedgerApi.list({ month: '2026-10', applicationScope: 'DAILY', bound: false });
    await v3ExpenseLedgerApi.attribute(8, { targetTaskId: 9, expectedVersion: 0, reason: '归属' });
    await v3ExpenseLedgerApi.reverse(8, { reason: '冲销', requestKey: 'key' });
    await v3FinanceApi.task(9);
    await v3SettlementApi.diff(3);
    assert.match(calls[0].url, /\/expense-ledger\?/);
    assert.match(calls[1].url, /\/expense-ledger\/8\/attribution$/);
    assert.match(calls[2].url, /\/expense-ledger\/8\/reverse$/);
    assert.match(calls[3].url, /\/finance\/tasks\/9$/);
    assert.match(calls[4].url, /\/settlements\/versions\/3\/diff\?/);
    const ledger = source('views/ExpenseLedgerV3View.vue');
    assert.match(ledger, /申请属性/);
    assert.match(ledger, /当前经营归属/);
    assert.doesNotMatch(ledger, /JSON\.stringify|附件 ID/);
    assert.match(source('views/SettlementV3View.vue'), /与前版差异/);
    const tasks = source('views/TransportTasksV3View.vue');
    assert.match(tasks, /income_amount/);
    assert.match(tasks, /selectedFinance\.expenses/);
    assert.match(tasks, /归属费用净额/);
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
  ['V3-D approval and DAILY Excel use one active contract and stable return target', async () => {
    calls.length = 0;
    await v3ApprovalApi.available();
    await v3ApprovalApi.create({ templateId: 1, requestKey: 'k' });
    await v3ApprovalApi.returnStep(3, { targetFlowNodeId: 12, expectedVersion: 0 });
    await v3ApprovalApi.resubmit(3, { items: [] });
    assert.match(calls[0].url, /\/approval-templates\/available$/);
    assert.match(calls[1].url, /\/approvals$/);
    assert.match(calls[2].url, /\/approvals\/3\/return-step$/);
    assert.match(calls[3].url, /\/approvals\/3\/resubmit$/);
    assert.match(source('views/ApprovalCenterV3View.vue'), /targetFlowNodeId/);
    assert.doesNotMatch(source('views/ApprovalCenterV3View.vue'), /targetNodeOrder/);
    assert.match(source('views/ExpenseApplicationV3View.vue'), /dailyPreview/);
    assert.doesNotMatch(source('views/ExpenseApplicationV3View.vue'), /dailySubmit|mode==='EXCEL'/);
    assert.match(source('views/ExpenseApplicationV3View.vue'), /dailyItems\(dailyRows\.value\)/);
  }],
  ['DAILY Excel rows and manual rows share one editable final items array', () => {
    const options = { drivers: [{ id: 10, phone: '13900000100' }], vehicles: [{ id: 20, plateNo: '云A100' }] };
    const preview = [
      { rowNo: 2, businessDate: '2026-09-30', driverPhone: '13900000100', plateNo: '云A100', amount: '8.00', description: '导入一' },
      { rowNo: 3, businessDate: '2026-09-30', driverPhone: '13900000100', plateNo: '云A100', amount: '9.00', description: '导入二' }
    ];
    const rows = appendPreviewRows([], preview, options);
    assert.equal(rows.length, 2);
    rows[0].amount = '10.00'; // Edit an imported row.
    rows.splice(1, 1); // Delete another imported row.
    const manual = emptyExpenseRow();
    Object.assign(manual, { businessDate: '2026-09-30', driverId: 10, vehicleId: 20, amount: '3.00' });
    rows.push(manual);
    const items = dailyItems(rows);
    assert.deepEqual(items.map((row) => row.amount), ['10.00', '3.00']);
    assert.equal(items[0].driverId, 10);
    assert.equal(items[0].vehicleId, 20);
    assert(items.every((row) => row.targetTaskId === null));
    assert.throws(() => dailyItems([...rows, { ...manual, amount: '0.00' }]));
    assert.throws(() => dailyItems([...rows, { ...manual, amount: '-1' }]));
    assert.throws(() => dailyItems([]));
    assert.doesNotMatch(source('views/TaskImportsV3View.vue'), /dailyRows/);
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
await import('./chargingWorkbookTests.mjs');
