import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = (path) => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8');
const calls = [];
globalThis.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
const sessionValues = new Map();
globalThis.sessionStorage = { getItem: (key) => sessionValues.get(key) || null,
  setItem: (key, value) => sessionValues.set(key, value), removeItem: (key) => sessionValues.delete(key) };
const { masterApi, routeApi, routeTemplateApi, dispatchApi, taskImportApi,
  salaryApi, attendanceApi, dispatchLedgerApi, systemApi, attachmentApi, v3ApprovalApi,
  v3ExpenseLedgerApi, v3FinanceApi, v3SettlementApi } = await import('../src/api/modules.js');
const { appendDispatchRows, updateSelectedAmounts, dispatchBody } = await import('../src/views/dispatchDraft.js');
const { appendPreviewRows, dailyItems, emptyExpenseRow } = await import('../src/views/expenseApplicationDraft.js');
const { hasExpenseDraft } = await import('../src/views/expenseScopeDraft.js');
const { navigation, filterNavigation, findNavigationTrail } = await import('../src/router/navigationModel.js');
const { buildPagePermissionTree, hasRouteAccess, overviewCardAccess,
  selectPermissionCodes, todoOverviewPermissions } = await import('../src/router/pagePermissionModel.js');
const { displayedPercent, submittedFraction, editingStatus, invalidStoredPercent, invalidFixedStandard, thresholdTons } = await import('../src/components/masterCatalogForm.js');
const { birthDateFromIdCard, fullAge, idCardValidationMessage } = await import('../src/utils/driverIdentity.js');
const { calendarDays, dayFact, isWeekend, rowTotalMatches } = await import('../src/components/vehicleMonthlyGrid.js');
const { missingApprovalPermissions, normalizeFlowOptions } = await import('../src/views/approvalFlowOptions.js');
const { apiErrorContent, apiErrorDialog, closeApiError } = await import('../src/stores/apiErrorDialog.js');
const { apiClient } = await import('../src/api/client.js');
const { authStore } = await import('../src/stores/auth.js');
globalThis.fetch = async (url, options = {}) => {
  calls.push({ url: String(url), options });
  return { ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => ({ code: '0', data: { records: [] } }) };
};

const tests = [
  ['admin logout revokes the server token and clears local state even on failure', async () => {
    const priorFetch = globalThis.fetch;
    authStore.token = 'test-only-token';
    authStore.user = { id: 1 };
    globalThis.fetch = async (url, options) => {
      assert.equal(url, '/auth/logout');
      assert.equal(options.method, 'POST');
      assert.equal(options.headers.Authorization, 'Bearer test-only-token');
      return { ok: true, status: 200, headers: { get: () => 'application/json' },
        json: async () => ({ code: '0', data: null }) };
    };
    try {
      await authStore.logout();
      assert.equal(authStore.token, '');
      assert.equal(authStore.user, null);
      assert.equal(sessionValues.has('admin.loginNotice'), false);
      authStore.token = 'test-only-token';
      authStore.user = { id: 1 };
      globalThis.fetch = async () => { throw new TypeError('Failed to fetch'); };
      await authStore.logout();
      assert.equal(authStore.token, '');
      assert.equal(authStore.user, null);
      assert.equal(sessionValues.get('admin.loginNotice'), '本机已退出，服务端会话撤销未确认');
      assert.equal(apiErrorDialog.open, true);
    } finally {
      globalThis.fetch = priorFetch;
      sessionValues.clear();
      closeApiError();
    }
  }],
  ['API failures use one compact business dialog without request identifiers', async () => {
    const app = source('App.vue');
    assert.match(app, /apiErrorDialog\.title/);
    assert.match(app, /apiErrorDialog\.message/);
    assert.match(app, /class="ui-api-error-actions"/);
    assert.match(app, />确定<\/button>/);
    assert.match(app, /z-index:2147483647/);
    assert.doesNotMatch(app, /请求编号|技术详情|复制编号|我知道了|apiErrorDialog\.requestId/);
    assert.doesNotMatch(source('views/approvalDetailModel.js'), /requestId: \$\{error\.requestId\}/);
    assert.doesNotMatch(source('views/LoginView.vue'), /v-if="authStore\.error"/);
    assert.deepEqual(apiErrorContent({ status: 403, message: '无权处理该审批', requestId: 'secret' },
      { path: '/approvals/1/approve', method: 'POST' }), { title: '权限不足', message: '无权处理该审批' });
    assert.deepEqual(apiErrorContent({ status: 409, message: 'requestId: abc-123' },
      { path: '/approvals', method: 'POST' }), { title: '提交失败', message: '当前数据已发生变化，请刷新后重试' });
    assert.equal(apiErrorContent(new TypeError('Failed to fetch'), { path: '/auth/login', method: 'POST' }).title, '登录失败');
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => ({ ok: false, status: 403, headers: { get: () => 'application/json' },
      json: async () => ({ code: 'FORBIDDEN', message: '当前账号无权查看', requestId: 'hidden' }) });
    try {
      await assert.rejects(masterApi.products());
      assert.equal(apiErrorDialog.open, true);
      assert.equal(apiErrorDialog.title, '权限不足');
      assert.equal(apiErrorDialog.message, '当前账号无权查看');
      assert.equal('requestId' in apiErrorDialog, false);
    } finally {
      closeApiError();
      globalThis.fetch = originalFetch;
    }
    globalThis.fetch = async () => ({ ok: false, status: 400, headers: { get: () => 'application/json' },
      json: async () => ({ code: 'BAD_REQUEST', message: '原密码错误', requestId: 'hidden' }) });
    try {
      await assert.rejects(apiClient.put('/auth/password', { oldPassword: 'x', newPassword: '123456' }));
      assert.equal(apiErrorDialog.title, '修改密码失败');
      assert.equal(apiErrorDialog.message, '原密码错误');
      closeApiError();
      await assert.rejects(apiClient.download('/attachments/1/download'));
      assert.equal(apiErrorDialog.title, '下载失败');
      assert.equal(apiErrorDialog.message, '原密码错误');
    } finally {
      closeApiError();
      globalThis.fetch = originalFetch;
    }
  }],
  ['driver and vehicle certificates have distinct required fields and derived age', () => {
    assert.equal(birthDateFromIdCard('11010519491231002X'), '1949-12-31');
    assert.equal(birthDateFromIdCard('11010519490230002X'), null);
    assert.equal(birthDateFromIdCard('110105194912310021'), null);
    assert.equal(birthDateFromIdCard('429006199307231272'), '1993-07-23');
    assert.equal(fullAge(birthDateFromIdCard('429006199307231272'), new Date(2026, 9, 14)), 33);
    assert.equal(idCardValidationMessage('429006199307231271'), '身份证号末位校验码不正确，请核对后重新输入');
    assert.equal(idCardValidationMessage('11010519490230002X'), '身份证号中的出生日期无效');
    assert.equal(idCardValidationMessage('11010519491231002X'), '');
    assert.equal(fullAge('2000-10-15', new Date(2026, 9, 14)), 25);
    assert.equal(fullAge('2000-10-14', new Date(2026, 9, 14)), 26);
    const driver = source('components/DriverManagementSample.vue');
    const vehicle = source('components/MasterCatalogUi2.vue');
    assert.doesNotMatch(driver, /出生日期<input|年龄<input|formBirthDate|formAge/);
    assert.match(driver, /key: 'idCardNo', label: '身份证号'/);
    assert.match(driver, /key: 'drivingLicenseNo', label: '驾驶证号'/);
    assert.match(driver, /key: 'birthDate', label: '出生日期'/);
    assert.match(driver, /key: 'age', label: '年龄'/);
    assert.match(driver, /driverBirthDate = \(driver\) => driver\?\.idCardNo \? birthDateFromIdCard\(driver\.idCardNo\)/);
    assert.match(driver, /fullAge\(driverBirthDate\(row\)\)/);
    assert.match(driver, /class="ui-field-error" role="alert"/);
    assert.doesNotMatch(driver, /<ErrorState v-if="error"/);
    assert.match(driver, /vehicleName = \(driver\) => driver\?\.driverType === 'OUTSOURCED' \? '不适用'/);
    assert.doesNotMatch(driver, /车辆驾驶证号|form\.vehicleLicenseNo|v-model="form\.birthDate"/);
    assert.match(vehicle, /行驶证号<input v-model\.trim="form\.vehicleLicenseNo" required/);
    assert.match(vehicle, /key: 'vehicleLicenseNo', label: '行驶证号'/);
    assert.match(vehicle, /key: 'loadStandardType', label: '达标方式'/);
    assert.match(vehicle, /trailers: \[\{ key: 'trailerNo'.*key: 'currentVehiclePlateNo'/);
    assert.doesNotMatch(vehicle, /vehicleSchemaUnavailable/);
    assert.match(vehicle, /String\(saved\?\.vehicleLicenseNo \|\| ''\)\.trim\(\) !== form\.vehicleLicenseNo\.trim\(\)/);
  }],
  ['vehicle percent and record statuses round trip without changing values on open or cancel', () => {
    assert.equal(displayedPercent(0.8), 80);
    assert.equal(submittedFraction(80), 0.8);
    assert.equal(displayedPercent(submittedFraction(80)), 80);
    assert.equal(displayedPercent(80), 80);
    assert.equal(invalidStoredPercent(80), true);
    assert.equal(invalidStoredPercent(0.8), false);
    assert.equal(thresholdTons({ maxLoad: '30.000', loadStandardType: 'PERCENT', loadStandardPercent: '0.8000' }), '24.000');
    assert.equal(thresholdTons({ maxLoad: '17.333', loadStandardType: 'PERCENT', loadStandardPercent: '0.8750' }), '15.166');
    assert.equal(thresholdTons({ maxLoad: '30.000', loadStandardType: 'FIXED', loadStandardMinLoad: '21.5' }), '21.500');
    assert.equal(invalidFixedStandard('32.000', '50.000'), true);
    assert.equal(invalidFixedStandard('32.000', '-1.000'), true);
    assert.equal(invalidFixedStandard('32.000', '0.000'), false);
    assert.equal(invalidFixedStandard('32.000', '32.000'), false);
    assert.equal(thresholdTons({ maxLoad: '32.000', loadStandardType: 'FIXED', loadStandardMinLoad: '50.000' }), null);
    assert.equal(thresholdTons({ maxLoad: '30.000', loadStandardType: 'PERCENT', loadStandardPercent: '80.0000' }), null);
    for (const type of ['vehicles', 'trailers']) {
      assert.equal(editingStatus({ status: 1 }, type), 1);
      assert.equal(editingStatus({ status: 0 }, type), 0);
    }
    assert.equal(editingStatus({ enabled: false }, 'products'), 0);
    const catalog = source('components/MasterCatalogUi2.vue');
    assert.match(catalog, /status: editingStatus\(row, props\.type\)/);
    assert.match(catalog, /submittedFraction\(form\.loadStandardPercent\)/);
    assert.match(catalog, /当前输入的达标吨数/);
    assert.match(catalog, /key: 'standardPercent', label: '达标比例'/);
    assert.match(catalog, /key: 'standardTons', label: '达标吨数'/);
    assert.match(catalog, /:max="form\.maxLoad \|\| undefined"/);
    assert.match(catalog, /@click="editor = false"/);
  }],
  ['driver editing and binding retain distinct page and button controls', () => {
    const driver = source('components/DriverManagementSample.vue');
    const binding = source('components/BindingManagement.vue');
    assert.match(driver, /can\('BUTTON_DRIVERS_EDIT'\)/);
    assert.match(binding, /can\('BUTTON_BINDING_BIND_DRIVER'\)/);
    assert.match(binding, /masterApi\.bindingDrivers\(page, pageSize\)/);
    assert.match(binding, /masterApi\.bindingVehicles\(page, pageSize\)/);
    assert.match(binding, /masterApi\.bindingTrailers\(1\)/);
    assert.doesNotMatch(binding, /masterApi\.drivers\(page, pageSize\)/);
  }],
  ['eight domains and 36 menu pages use a unique page function', () => {
    const expected = ['工作台','运输管理','车辆与人员','费用管理','审批中心','工资与考勤','经营与结算','系统设置'];
    assert.deepEqual(navigation.map((entry) => entry.label), expected);
    const router = source('router/routes.js');
    const pageRoutes = [...router.matchAll(/\{ key: '([^']+)', label: '[^']+', permission: '([^']+)'/g)]
      .map((match) => ({ key: match[1], permission: match[2] }));
    assert.equal(pageRoutes.length, 36);
    assert.equal(new Set(pageRoutes.map((route) => route.key)).size, 36);
    for (const route of pageRoutes) assert.equal(route.permission,
      `PAGE_${route.key.replaceAll('-', '_').toUpperCase()}`);
    assert.match(router, /key: 'my-todos', label: '待办概览', permission: todoOverviewPermissions/);
    assert.match(router, /key: 'expense-unassigned'.*permission: 'PAGE_EXPENSE_UNASSIGNED'/);
    assert.match(router, /key: 'approvals-todo'.*permission: 'PAGE_APPROVALS_TODO'/);
  }],
  ['server registry owns all menu pages and button operations', () => {
    const registry = readFileSync(new URL('../../backend/src/main/java/com/dfwl/fleet/security/FunctionRegistry.java', import.meta.url), 'utf8');
    const pages = [...registry.matchAll(/page\("([^"]+)", "([^"]*)", "([^"]+)", "([^"]+)", "([^"]+)"\)/g)]
      .map((match) => ({ permissionCode: `PAGE_${match[3].replaceAll('-', '_').toUpperCase()}`,
        permissionName: match[4], permissionType: 'PAGE', domain: match[1], group: match[2], pageKey: match[3], status: 1 }));
    const buttons = [...registry.matchAll(/button\("([^"]+)", "([^"]+)", "([^"]+)"\)/g)]
      .map((match) => ({ permissionCode: `BUTTON_${match[1].replaceAll('-', '_').toUpperCase()}_${match[2].replaceAll('-', '_').toUpperCase()}`,
        permissionName: match[3], permissionType: 'BUTTON', parentCode: `PAGE_${match[1].replaceAll('-', '_').toUpperCase()}`, status: 1 }));
    const router = source('router/routes.js');
    const routes = [...router.matchAll(/\{ key: '([^']+)', label: '([^']+)', permission: '([^']+)'/g)]
      .map((match) => ({ key: match[1], label: match[2], permission: match[3] }));
    routes.push({ key: 'my-todos', label: '待办概览', permission: todoOverviewPermissions });
    const tree = buildPagePermissionTree(navigation, routes, [...pages, ...buttons]);
    const owned = tree.domains.flatMap(function walk(node) {
      return node.type === 'page' ? node.codes : (node.children || []).flatMap(walk);
    });
    assert.equal(pages.length, 41);
    assert.equal(new Set(owned).size, pages.length + buttons.length);
    assert.deepEqual(tree.common, []);
    assert.equal(tree.domains[0].children.find((item) => item.key === 'my-todos').type, 'derived');
    assert.deepEqual(tree.domains[2].children.find((item) => item.key === 'binding').codes.sort(),
      ['PAGE_BINDING', 'BUTTON_BINDING_BIND_DRIVER', 'BUTTON_BINDING_UNBIND_DRIVER',
        'BUTTON_BINDING_BIND_TRAILER', 'BUTTON_BINDING_UNBIND_TRAILER'].sort());
    assert.match(source('components/RolePermissionPage.vue'), /props\.node\.operations/);
    assert.match(source('views/SystemUi1View.vue'), /<RolePermissionTree/);
  }],
  ['page and button grants preserve parent dependency and derived todo access', () => {
    const catalog = [
      { permissionCode: 'PAGE_PRODUCTS', permissionType: 'PAGE' },
      { permissionCode: 'BUTTON_PRODUCTS_MANAGE', permissionType: 'BUTTON', parentCode: 'PAGE_PRODUCTS' },
      { permissionCode: 'PAGE_APPROVALS_TODO', permissionType: 'PAGE' },
      { permissionCode: 'BUTTON_APPROVALS_TODO_APPROVE', permissionType: 'BUTTON', parentCode: 'PAGE_APPROVALS_TODO' },
      { permissionCode: 'PAGE_EXPENSE_UNASSIGNED', permissionType: 'PAGE' },
      { permissionCode: 'BUTTON_EXPENSE_UNASSIGNED_ATTRIBUTE', permissionType: 'BUTTON', parentCode: 'PAGE_EXPENSE_UNASSIGNED' }
    ];
    const routes = [{ key: 'products', permission: 'PAGE_PRODUCTS' },
      { key: 'my-todos', permission: todoOverviewPermissions }];
    assert.deepEqual(selectPermissionCodes([], ['BUTTON_PRODUCTS_MANAGE'], true, routes, 'products', catalog).sort(),
      ['BUTTON_PRODUCTS_MANAGE', 'PAGE_PRODUCTS']);
    assert.deepEqual(selectPermissionCodes(['PAGE_PRODUCTS','BUTTON_PRODUCTS_MANAGE'], ['PAGE_PRODUCTS'], false, routes, 'products', catalog), []);
    assert.equal(hasRouteAccess(routes[0], ['PAGE_PRODUCTS']), true);
    assert.equal(hasRouteAccess(routes[0], ['BUTTON_PRODUCTS_MANAGE']), false);
    assert.deepEqual(overviewCardAccess(['PAGE_APPROVALS_TODO','BUTTON_APPROVALS_TODO_APPROVE']),
      { approvals: true, attribution: false });
    assert.deepEqual(overviewCardAccess(['PAGE_EXPENSE_UNASSIGNED','BUTTON_EXPENSE_UNASSIGNED_ATTRIBUTE']),
      { approvals: false, attribution: true });
    assert.equal(hasRouteAccess(routes[1], ['BUTTON_APPROVALS_TODO_APPROVE']), false);
    assert.equal(hasRouteAccess(routes[1], ['PAGE_APPROVALS_TODO','BUTTON_APPROVALS_TODO_APPROVE']), true);
  }],
  ['workbench cards and sensitive binding data use official scoped APIs', async () => {
    const overview = source('views/MyTodosV3View.vue');
    assert.match(overview, /v3ApprovalApi\.list\('TODO', 1\)/);
    assert.match(overview, /v3ExpenseLedgerApi\.pendingAttributionSummary\(\)/);
    assert.match(overview, /@click="navigate\('approvals-todo'\)"/);
    assert.match(overview, /@click="navigate\('expense-unassigned'\)"/);
    assert.doesNotMatch(overview, /DataTable|PaginationBar|approvalRows/);
    const api = source('api/modules.js');
    assert.match(api, /bindingDrivers:.*\/drivers\/binding-candidates/);
    assert.match(api, /bindingVehicles:.*\/bindings\/vehicle-options/);
    assert.match(api, /bindingTrailers:.*\/bindings\/trailer-options/);
    calls.length = 0;
    await v3ExpenseLedgerApi.pendingAttributionSummary();
    assert.match(calls[0].url, /\/expense-ledger\/pending-attribution\/summary/);
  }],
  ['UI-B1 shared shell, account actions and common states stay connected', () => {
    const layout = source('layouts/AppLayout.vue');
    const tree = source('components/NavigationTree.vue');
    assert.match(layout, /<NavigationTree :groups="visibleNavigation" :active-key="menuKey" :trail="trail" @navigate="navigate"/);
    assert.match(tree, /:aria-expanded="!!expanded\[group\.key\]"/);
    assert.match(tree, /:aria-expanded="!!expanded\[branchKey\(group, item\)\]"/);
    assert.match(tree, /:aria-current="activeKey === leaf\.key \? 'page'/);
    assert.match(tree, /sessionStorage\.setItem\(storageKey/);
    assert.doesNotMatch(tree, /@click="navigate\(item\.key\)"/);
    assert.match(layout, /systemApi\.changePassword\(\{ oldPassword:/);
    assert.match(layout, /await authStore\.loadMe\(\)/);
    assert.match(layout, /@click="authStore\.logout"/);
    assert.match(source('stores/auth.js'), /await apiClient\.post\('\/auth\/logout'\)/);
    assert.match(source('components/DataTable.vue'), /<LoadingState v-if="loading"/);
    assert.match(source('components/DataTable.vue'), /<EmptyState v-else-if="!rows\.length"/);
    assert.match(source('components/PaginationBar.vue'), /v-if="total > 0"/);
    assert.match(source('components/ConfirmDialog.vue'), /!busy && \$emit\('cancel'\)/);
    assert.match(source('views/DashboardV3View.vue'), /<SummaryCards label="本月经营"/);
    assert.match(source('views/DashboardV3View.vue'), /<FormSection title="经营口径"/);
  }],
  ['expense source selection preserves unsubmitted DAILY and TASK drafts', () => {
    assert.equal(hasExpenseDraft('DAILY', [], {}, ''), false);
    assert.equal(hasExpenseDraft('DAILY', [emptyExpenseRow()], {}, ''), true);
    assert.equal(hasExpenseDraft('TASK', [], { amount: '' }, ''), false);
    assert.equal(hasExpenseDraft('TASK', [], { amount: '12.00' }, ''), true);
    assert.equal(hasExpenseDraft('TASK', [], { amount: '' }, '2'), true);
    const view = source('views/ExpenseApplicationV3View.vue');
    assert.match(view, /aria-label="申请类型" @change="changeScope"/);
    assert.match(view, /if \(hasExpenseDraft\(.+\)\) pendingScope\.value = next/);
    assert.match(view, /保留草稿并切换/);
    assert.doesNotMatch(view, /scope='DAILY'|scope='TASK'/);
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
    const settlement = source('views/SettlementV3View.vue');
    assert.match(settlement, /与前版差异/);
    assert.match(settlement, /<ConfirmDialog[^>]+@confirm="generate"/);
    assert.match(settlement, /@click="generateConfirmOpen = true"/);
    assert.match(settlement, /type="month" @change="loadVersions"/);
    assert.doesNotMatch(settlement, />查看版本<\/button>/);
    assert.doesNotMatch(settlement, /window\.confirm/);
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
  ['dispatch workspace loads candidates with dispatch permission without full driver records', async () => {
    calls.length = 0;
    await dispatchApi.driverOptions(2, 100);
    assert.match(calls[0].url, /\/dispatch-batches\/driver-options\?pageNo=2&pageSize=100$/);
    const view = source('views/DispatchWorkbenchV3View.vue');
    assert.match(view, /dispatchApi\.driverOptions\(1, 100\)/);
    assert.match(view, /dispatchApi\.driverOptions\(page, 100\)/);
    assert.doesNotMatch(view, /masterApi\.drivers\(/);
    assert.match(view, /dispatchApi\.vehicleOptions\(1, 100\)/);
    assert.doesNotMatch(view, /masterApi\.vehicles/);
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
    assert.match(source('views/TaskImportsV3View.vue'), /任务导入批次详情[\s\S]*批次状态/);
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
  ['approval steps require a role, optionally name one user, and applicants can withdraw', async () => {
    calls.length = 0;
    await v3ApprovalApi.withdraw(3);
    assert.match(calls[0].url, /\/approvals\/3\/withdraw$/);
    const view = source('views/ApprovalCenterV3View.vue');
    assert.match(view, /审批角色<select v-model="node\.approverRoleId" required/);
    assert.match(view, /指定审批人（选填）/);
    assert.match(view, /person\.roleId/);
    assert.match(view, /v-for="role in approverRoles"/);
    assert.doesNotMatch(view, /缺少.*权限）/);
    assert.match(view, /onBusinessTypeChange/);
    assert.match(view, /detail\.canProcess/);
    assert.match(view, /detail\.canReturn/);
    assert.match(view, /canWithdraw/);
    assert.doesNotMatch(view, /Number\(currentTask\.value\?\.approver_user_id\)/);
  }],
  ['approval flow options remain renderable when the server omits approver roles', () => {
    assert.deepEqual(normalizeFlowOptions(), { roles: [], approverRoles: [], approvers: [], expenseTypes: [], hasApproverRoles: false });
    assert.deepEqual(normalizeFlowOptions({ roles: [{ id: 1 }], approvers: [{ id: 2 }] }),
      { roles: [{ id: 1 }], approverRoles: [], approvers: [{ id: 2 }], expenseTypes: [], hasApproverRoles: false });
    assert.deepEqual(normalizeFlowOptions({ roles: [], approverRoles: [{ id: 3 }], approvers: [] }),
      { roles: [], approverRoles: [{ id: 3 }], approvers: [], expenseTypes: [], hasApproverRoles: true });
    assert.match(source('views/ApprovalCenterV3View.vue'), /options\.value = normalizeFlowOptions\(loadedOptions\)/);
  }],
  ['approval role choices depend only on unified approval permission', () => {
    assert.deepEqual(missingApprovalPermissions({ canProcessApproval: 1 }), []);
    assert.deepEqual(missingApprovalPermissions({ canProcessApproval: 0 }), ['处理审批']);
    assert.deepEqual(missingApprovalPermissions({ canProcessApproval: 1, canReturnApproval: 0 }), []);
    assert.doesNotMatch(source('views/ApprovalCenterV3View.vue'), /“处理换胎申请”权限/);
    assert.doesNotMatch(source('views/ApprovalCenterV3View.vue'), /暂不可选：/);
    assert.match(source('views/ApprovalCenterV3View.vue'), /approverRoles = computed\(\(\) => options\.value\.approverRoles\.filter/);
    assert.match(source('views/ApprovalCenterV3View.vue'), /v-for="role in approverRoles"/);
  }],
  ['approval template creation uses official categories and a two-row step form', () => {
    const view = source('views/ApprovalCenterV3View.vue');
    assert.match(view, /业务类型<select v-model="form\.businessType"/);
    assert.match(view, /v-if="form\.businessType === 'EXPENSE'"[^>]*>费用大类<select/);
    assert.match(view, /v-for="type in options\.expenseTypes"/);
    assert.match(view, /form\.expenseType = ''/);
    assert.match(view, /class="approval-step-row approval-step-primary"/);
    assert.match(view, /class="approval-step-row approval-step-secondary"/);
    assert.match(view, /暂无可用费用大类/);
    assert.match(view, /换胎节点按冻结流程中的审批角色/);
    assert.match(view, /暂无可处理换胎申请的角色/);
    assert.doesNotMatch(view, /业务名称<input|v-model="form\.name"/);
    assert.doesNotMatch(view, /form\.stableCode|stableCode: flowDetail/);
    assert.doesNotMatch(view, /expenseTypes:\s*\[\s*\{\s*categoryCode/);
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
  ['vehicle monthly ledgers share one sticky grid and keep actual daily departure facts', async () => {
    assert.equal(calendarDays('2025-02').length, 28);
    assert.equal(calendarDays('2024-02').length, 29);
    assert.equal(calendarDays('2026-04').length, 30);
    assert.equal(calendarDays('2026-10').length, 31);
    assert.equal(isWeekend('2026-10', 17), true);
    assert.equal(isWeekend('2026-10', 19), false);
    const firstDriver = { rowKey: 'I:1:10:', plate: '测试车', driverName: '司机甲', days: [{ day: 1, count: 2, tasks: [{ taskNo: 'A' }, { taskNo: 'B' }] }], monthlyTotal: 2 };
    const secondDriver = { rowKey: 'I:1:11:', plate: '测试车', driverName: '司机乙', days: [{ day: 1, count: 1, tasks: [{ taskNo: 'C' }] }], monthlyTotal: 1 };
    assert.equal(dayFact(firstDriver, 1).tasks.length, 2);
    assert.equal(rowTotalMatches(firstDriver), true);
    assert.equal(rowTotalMatches({ ...firstDriver, monthlyTotal: 1 }), false);
    assert.notEqual(firstDriver.rowKey, secondDriver.rowKey);
    const grid = source('components/VehicleMonthlyGrid.vue');
    const page = source('views/MonthlyOperationsV3View.vue');
    assert.match(grid, /v-for="row in rows" :key="row\.rowKey"/);
    assert.match(grid, /vehicle-month-plate.*vehicle-month-driver.*vehicle-month-trailer/s);
    assert.match(grid, /vehicle-month-total\{right:0/);
    assert.match(grid, /overflow:auto/);
    assert.match(grid, /fact\.tasks\?\.length/);
    assert.match(grid, /v-if="!rows\.length"/);
    assert.match(page, /<VehicleMonthlyGrid v-if="!attendance/);
    assert.match(page, /dispatchLedgerApi\.export\(props\.scope, \{ month: activeMonth\.value \}\)/);
    calls.length = 0;
    await dispatchLedgerApi.export('INTERNAL', { month: '2026-10' });
    assert.match(calls[0].url, /\/dispatch-ledger\/export\?month=2026-10&scope=INTERNAL$/);
  }],
  ['master data and binding use final live APIs', async () => {
    calls.length = 0;
    await masterApi.products();
    await masterApi.bindDriverVehicle(3, { vehicleId: 4 });
    assert.match(calls[0].url, /\/products\?/);
    assert.match(calls[1].url, /\/drivers\/3\/bind-vehicle$/);
    assert.match(source('components/BindingManagement.vue'), /确认解绑/);
    assert.match(source('views/MasterDataUi2View.vue'), /masterApi\.drivers\(nextPage, 20, driverFilters\)/);
    assert.match(source('components/DriverManagementSample.vue'), /<PaginationBar/);
    assert.doesNotMatch(source('components/DriverManagementSample.vue'), /车辆 #\$\{id\}/);
  }],
  ['system management and task evidence remain available', async () => {
    calls.length = 0;
    await systemApi.users();
    await attachmentApi.list('TASK', 9, 'WEIGHT_ADJUST');
    assert.match(calls[0].url, /\/users\?/);
    assert.match(calls[1].url, /\/attachments\?/);
    assert.match(source('views/TransportTasksV3View.vue'), /原始卸货凭证/);
    calls.length = 0;
    await systemApi.changePassword({ oldPassword: 'before', newPassword: 'after' });
    assert.match(calls[0].url, /\/auth\/password$/);
    const layout = source('layouts/AppLayout.vue');
    assert.match(layout, /刷新我的权限/);
    assert.match(layout, /修改密码/);
    assert.match(layout, /authStore\.user\?\.role\?\.name/);
    assert.match(layout, /class="user-role"/);
    assert.match(layout, /authStore\.loadMe\(\)/);
    assert.match(source('App.vue'), /暂无可访问的业务页面/);
    assert.doesNotMatch(source('views/ApprovalCenterV3View.vue'), /发起普通审批/);
    assert.match(source('views/ApprovalCenterV3View.vue'), /<option value="EXPENSE">费用申请<\/option>/);
    assert.match(source('views/ExpenseApplicationV3View.vue'), /navigate\('approvals-mine'\)/);
  }]
];

for (const [name, run] of tests) { await run(); console.log(`ok - ${name}`); }
console.log(`${tests.length} active V3-A/B/C admin tests passed`);
await import('./ui1Tests.mjs');
await import('./routeWeightEvidenceTests.mjs');
await import('./chargingWorkbookTests.mjs');
