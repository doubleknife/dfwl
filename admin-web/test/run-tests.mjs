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
const { appendPreviewRows, dailyItems, emptyExpenseRow, expenseRowError, vehicleEnergyError } = await import('../src/views/expenseApplicationDraft.js');
const { canTransferTask, canCancelTask, transferBody, cancelBody } = await import('../src/views/taskOperations.js');
const { hasExpenseDraft } = await import('../src/views/expenseScopeDraft.js');
const { countImportFailures, filterImportRows, formatImportDateTime, summarizeCompletedRows } = await import('../src/views/importResultView.js');
const { formatDateTime } = await import('../src/utils/dateTime.js');
const { displayValue } = await import('../src/utils/displayLabels.js');
const { groupTireMaintenanceHistory } = await import('../src/views/tireMaintenanceHistory.js');
const { navigation, filterNavigation, findNavigationTrail } = await import('../src/router/navigationModel.js');
const { buildPagePermissionTree, hasRouteAccess, overviewCardAccess,
  pagePermissionName, selectPermissionCodes, todoOverviewPermissions } = await import('../src/router/pagePermissionModel.js');
const { displayedPercent, submittedFraction, editingStatus, invalidFixedStandard, thresholdTons } = await import('../src/components/masterCatalogForm.js');
const { birthDateFromIdCard, fullAge, idCardValidationMessage } = await import('../src/utils/driverIdentity.js');
const { calendarDays, dayFact, isWeekend, rowTotalMatches } = await import('../src/components/vehicleMonthlyGrid.js');
const { missingApprovalPermissions, normalizeFlowOptions } = await import('../src/views/approvalFlowOptions.js');
const { apiErrorContent, apiErrorDialog, closeApiError } = await import('../src/stores/apiErrorDialog.js');
const { apiClient } = await import('../src/api/client.js');
const { statusTone } = await import('../src/utils/statusTone.js');
const { authStore } = await import('../src/stores/auth.js');
globalThis.fetch = async (url, options = {}) => {
  calls.push({ url: String(url), options });
  return { ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => ({ code: '0', data: { records: [] } }) };
};

const tests = [
  ['status labels use consistent semantic colors across lists, details and import records', () => {
    for (const value of [0, false, '停用', '保险不完整', '校验失败', '发布失败', '已冲销']) {
      assert.equal(statusTone(value), 'danger');
    }
    for (const value of [1, true, '启用', '保险完整', '校验通过', '已完成']) {
      assert.equal(statusTone(value), 'success');
    }
    assert.equal(statusTone('待处理'), 'warning');
    assert.equal(statusTone('停用', 'neutral'), 'danger');
    assert.match(source('components/DataTable.vue'), /<StatusTag v-if="\/\(\^\|_\)status\$/);
    assert.match(source('components/MasterCatalogUi2.vue'), /<StatusTag :value="detail\.status"/);
    assert.match(source('views/TaskImportsV3View.vue'), /<StatusTag :value="stateName\[row\.status\]/);
    assert.match(source('views/TireImportsV3View.vue'), /<StatusTag :value="stateName\[value\]/);
    assert.doesNotMatch(source('components/MasterCatalogUi2.vue'), /'neutral'/);
  }],
  ['page-level failures use the shared dialog while retry and row errors remain available', () => {
    const state = source('components/ErrorState.vue');
    const expense = source('views/ExpenseApplicationV3View.vue');
    assert.match(state, /showErrorDialog\('操作失败', message\)/);
    assert.match(state, /apiErrorDialog\.open/);
    assert.match(state, /@click="\$emit\('retry'\)"/);
    assert.doesNotMatch(state, /\{\{ message \}\}/);
    assert.doesNotMatch(expense, /<p v-if="error" class="error"/);
    assert.match(expense, /v-if="dailyRowError\(row\)" class="error"/);
    assert.match(expense, /const preview = await v3ApprovalApi\.dailyPreview\(file\)/);
    assert.match(expense, /dailyRows\.value = appendPreviewRows\(dailyRows\.value, preview\.rows, options\.value\)/);
    assert.match(expense, /imported\.errors\?\.includes\('车挂号不存在'\)/);
    assert.doesNotMatch(source('layouts/AppLayout.vue'), /<p v-if="authStore\.error" class="error"/);
    assert.doesNotMatch(source('views/ApprovalCenterV3View.vue'), /<p v-if="error" class="error"/);
  }],
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
    assert.equal(displayedPercent(0.85), 85);
    assert.equal(thresholdTons({ maxLoad: '30.000', loadStandardType: 'PERCENT', loadStandardPercent: '0.8000' }), '24.000');
    assert.equal(thresholdTons({ maxLoad: '17.333', loadStandardType: 'PERCENT', loadStandardPercent: '0.8750' }), '15.166');
    assert.equal(thresholdTons({ maxLoad: '30.000', loadStandardType: 'FIXED', loadStandardMinLoad: '21.5' }), '21.500');
    assert.equal(invalidFixedStandard('32.000', '50.000'), true);
    assert.equal(invalidFixedStandard('32.000', '-1.000'), true);
    assert.equal(invalidFixedStandard('32.000', '0.000'), true);
    assert.equal(invalidFixedStandard('32.000', '32.000'), false);
    assert.equal(thresholdTons({ maxLoad: '32.000', loadStandardType: 'FIXED', loadStandardMinLoad: '50.000' }), null);
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
    assert.match(binding, /masterApi\.bindingDrivers\(page, pageSize\.value\)/);
    assert.match(binding, /masterApi\.bindingVehicles\(page, pageSize\.value\)/);
    assert.match(binding, /masterApi\.bindingTrailers\(1\)/);
    assert.match(binding, /:load-page="masterApi\.unboundDriverVehicles"/);
    assert.match(binding, /:load-page="masterApi\.unboundVehicleTrailers"/);
    assert.doesNotMatch(binding, /masterApi\.drivers\(page, pageSize\)/);
  }],
  ['eight domains and 36 menu pages use a unique page function', () => {
    const expected = ['工作台','运输管理','车辆与人员','费用管理','审批中心','工资与考勤','经营与结算','系统设置'];
    assert.deepEqual(navigation.map((entry) => entry.label), expected);
    const finance = navigation.find((entry) => entry.key === 'finance');
    assert.equal(finance.children[0].label, '收入与利润');
    assert.deepEqual(finance.children[0].children, ['finance-company', 'finance-templates', 'finance-tasks']);
    const router = source('router/routes.js');
    assert.match(router, /key: 'finance-company', label: '公司收支利润', permission: 'PAGE_FINANCE_COMPANY'/);
    assert.match(router, /key: 'finance-templates', label: '线路收支利润', permission: 'PAGE_FINANCE_TEMPLATES'/);
    assert.match(router, /key: 'finance-tasks', label: '单趟收支利润', permission: 'PAGE_FINANCE_TASKS'/);
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
    const financePermissions = pages.filter((item) => ['finance-company', 'finance-templates', 'finance-tasks'].includes(item.pageKey));
    assert.deepEqual(financePermissions.map((item) => [item.group, item.permissionName]), [
      ['收入与利润', '公司收支利润'], ['收入与利润', '线路收支利润'], ['收入与利润', '单趟收支利润']
    ]);
    const financeTree = tree.domains.find((item) => item.key === 'finance');
    assert.deepEqual(financeTree.children[0].children.map((item) => item.label),
      ['公司收支利润', '线路收支利润', '单趟收支利润']);
    const staleCatalog = pages.map((item) => item.pageKey === 'finance-company'
      ? { ...item, permissionName: '公司经营' } : item);
    const staleTree = buildPagePermissionTree(navigation, routes, [...staleCatalog, ...buttons]);
    assert.equal(staleTree.domains.find((item) => item.key === 'finance').children[0].children[0].label, '公司收支利润');
    assert.equal(pagePermissionName(staleCatalog.find((item) => item.pageKey === 'finance-company'), routes), '公司收支利润');
    const owned = tree.domains.flatMap(function walk(node) {
      return node.type === 'page' ? node.codes : (node.children || []).flatMap(walk);
    });
    assert.equal(pages.length, 41);
    assert.equal(new Set(owned).size, pages.length + buttons.length);
    assert.deepEqual(tree.common, []);
    assert.deepEqual(tree.domains.map((domain) => domain.label), [...navigation.map((domain) => domain.label), '小程序']);
    const miniPages = pages.filter((page) => page.pageKey.startsWith('mini-'));
    const miniDomain = tree.domains.at(-1);
    assert.deepEqual(miniDomain.children.map((page) => page.key), miniPages.map((page) => page.pageKey));
    assert.deepEqual(miniDomain.codes.sort(), [...miniPages, ...buttons.filter((button) =>
      miniPages.some((page) => page.permissionCode === button.parentCode))]
      .map((item) => item.permissionCode).sort());
    assert.equal(tree.domains.slice(0, -1).flatMap((domain) => domain.codes)
      .some((code) => code.startsWith('PAGE_MINI_') || code.startsWith('BUTTON_MINI_')), false);
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
    await masterApi.unboundDriverVehicles(2, 20);
    await masterApi.unboundVehicleTrailers(3, 20);
    assert.match(calls[0].url, /\/bindings\/vehicle-options\?pageNo=2&pageSize=20&onlyUnboundDriver=true$/);
    assert.match(calls[1].url, /\/bindings\/trailer-options\?pageNo=3&pageSize=20&onlyUnboundVehicle=true$/);
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
    assert.match(source('components/PaginationBar.vue'), /\[10, 20, 50, 100\]/);
    assert.match(source('components/PaginationBar.vue'), /emit\('change-size', size\)/);
    for (const path of ['components/FormDialog.vue', 'components/ConfirmDialog.vue', 'components/DetailDrawer.vue', 'App.vue']) {
      assert.doesNotMatch(source(path), /@click\.self|@keydown\.esc/, `${path} must stay open on backdrop click and Escape`);
    }
    assert.match(source('components/ConfirmDialog.vue'), /@click="\$emit\('cancel'\)"/);
    assert.match(source('components/FormDialog.vue'), /aria-label="关闭" @click="\$emit\('close'\)"/);
    assert.match(source('components/DetailDrawer.vue'), /aria-label="关闭" @click="\$emit\('close'\)"/);
    assert.match(source('App.vue'), /@click="closeApiError">确定/);
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
    assert.doesNotMatch(source('api/modules.js'), /export const (approvalApi|approvalTemplateApi)\s*=/);
    assert.match(source('api/modules.js'), /export const v3ApprovalApi\s*=/);
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
      { rowNo: 2, businessDate: '2026-09-30', driverPhone: '13900000100', plateNo: '云A100', trailerNo: '贵A123挂', amount: '8.00', description: '导入一' },
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
    assert.equal(items[0].trailerNo, '贵A123挂');
    assert.equal(items[1].trailerNo, null);
    assert.equal(expenseRowError(rows[0], { number: '贵A123挂', exists: false }), '车挂号不存在');
    assert.equal(expenseRowError(rows[0], { number: '贵A123挂', exists: true }), '');
    const electric = { id: 20, plateNo: '云A100', energyType: 'ELECTRIC' };
    const gas = { id: 21, plateNo: '云A101', energyType: 'GAS' };
    assert.equal(vehicleEnergyError('TEMP_ELECTRIC', gas), '电费、临时电费只能选择电车');
    assert.equal(vehicleEnergyError('TEMP_GAS', electric), '气费、临时气费只能选择气车');
    assert.equal(vehicleEnergyError('GAS', gas), '');
    assert.equal(expenseRowError(rows[0], { number: '贵A123挂', exists: true }, 'GAS', [electric, gas]),
      '气费、临时气费只能选择气车');
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
  ['task operations retain permissions, status limits and complete transfer facts', async () => {
    const view = source('views/TransportTasksV3View.vue');
    assert.match(view, /can\('BUTTON_ROUTES_TRANSFER'\)/);
    assert.match(view, /can\('BUTTON_ROUTES_CANCEL'\)/);
    assert.match(view, /routeApi\.transfer\(taskId, body\)/);
    assert.match(view, /routeApi\.cancel\(taskId, body\)/);
    assert.equal(canTransferTask({ status: 'IN_TRANSIT' }), true);
    assert.equal(canTransferTask({ status: 'PUBLISHED' }), false);
    assert.equal(canCancelTask({ status: 'PUBLISHED' }), true);
    assert.equal(canCancelTask({ status: 'UNPUBLISHED' }), false);
    assert.equal(canCancelTask({ status: 'TRANSFER_PENDING' }), false);
    const task = { status: 'IN_TRANSIT', version: 4 };
    const people = [{ id: 12, sequenceNo: 1, driverId: 3, current: true }];
    const form = { newSalary: '500.00', reason: '司机交接', salaries: { 12: '300.00' }, outsourcedPlateNo: '云A12345' };
    assert.deepEqual(transferBody(task, people, form, { id: 4, driverType: 'OUTSOURCED' }), {
      newDriverId: 4, outsourcedPlateNo: '云A12345', allParticipantSalaries: [{ participantId: 12, amount: '300.00' }],
      newSalary: '500.00', reason: '司机交接', expectedVersion: 4, expectedCurrentParticipantId: 12
    });
    assert.throws(() => transferBody(task, people, { ...form, salaries: {} }, { id: 4, driverType: 'OUTSOURCED' }), /核定/);
    assert.throws(() => transferBody(task, people, form, { id: 3, driverType: 'OUTSOURCED' }), /另一位/);
    assert.deepEqual(cancelBody({ status: 'PUBLISHED', version: 2 }, '未发车取消', 'key-1'),
      { reason: '未发车取消', expectedVersion: 2, requestKey: 'key-1' });
    assert.throws(() => cancelBody(task, '原因', 'key-2'), /未发车/);
    calls.length = 0;
    await routeApi.cancel(9, { requestKey: 'key-1' });
    assert.match(calls[0].url, /\/transport-tasks\/9\/cancel$/);
  }],
  ['task finance tab and columns follow finance permission with actions at the right edge', () => {
    const view = source('views/TransportTasksV3View.vue');
    assert.match(view, /can\('PAGE_FINANCE_TASKS'\) \? \[\{key:'expenses',label:'费用与经营'\}\] : \[\]/);
    assert.match(view, /detailTab === 'expenses' && can\('PAGE_FINANCE_TASKS'\)/);
    assert.match(view, /\] : \[\]\), \{ key: 'actions', label: '操作' \}/);
    for (const key of ['income_amount', 'total_expense_amount', 'profit_amount']) {
      assert.match(view, new RegExp(`data-column="${key}"`));
    }
    assert.match(view, /\[data-column="actions"\]\) \{ position: sticky; right: 0/);
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
    assert.match(taskView, /保存后自动生成（L＋7 位数字）/);
    assert.match(taskView, /editing\?\.id \? editing\.stableCode/);
    assert.doesNotMatch(taskView, /v-model\.trim="form\.stableCode"/);
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
    assert.match(source('views/MasterDataUi2View.vue'), /masterApi\.drivers\(nextPage, driverPageSize\.value, driverFilters\)/);
    assert.match(source('components/DriverManagementSample.vue'), /<PaginationBar/);
    assert.doesNotMatch(source('components/DriverManagementSample.vue'), /车辆 #\$\{id\}/);
    const driverPage = source('components/DriverManagementSample.vue');
    assert.match(driverPage, /v-if="can\('BUTTON_DRIVERS_IMPORT'\)"[^>]*>批量导入<\/button>/);
    assert.match(driverPage, /<FormDialog :open="importOpen && can\('BUTTON_DRIVERS_IMPORT'\)" title="批量导入"[^>]* wide fixed-height/);
    assert.match(driverPage, /<ImportFilePicker :file="importFile"[^>]*@clear="clearImportFile" @select="selectImportFile"/);
    assert.match(driverPage, /if \(importFile\.value\) await previewImport\(importFile\.value\)/);
    assert.doesNotMatch(driverPage, /上传并预览/);
  }],
  ['task, tire and charging imports preview on selection and clear file state', async () => {
    const picker = source('components/ImportFilePicker.vue');
    assert.match(picker, /aria-label="清除所选文件和预览"/);
    assert.match(picker, /emit\('clear'\)/);
    for (const path of ['views/TaskImportsV3View.vue', 'views/TireImportsV3View.vue', 'views/ChargingImportsV3View.vue']) {
      const page = source(path);
      assert.match(page, /<ImportFilePicker/);
      assert.match(page, /@clear="clear(?:Import)?File" @select="choose(?:Import|File)?"/);
      assert.doesNotMatch(page, />上传并预览<|>解析并预览</);
      assert.doesNotMatch(page, /<FormDialog[^>]*title="批量导入"/);
    }
    assert.match(source('views/TaskImportsV3View.vue'), /previewFile\(\);/);
    assert.match(source('views/TireImportsV3View.vue'), /upload\(\);/);
    assert.match(source('views/ChargingImportsV3View.vue'), /await readChargingWorkbook\(file\); buildPreview\(\)/);
  }],
  ['import file selection stays the same size and API errors have only one popup', () => {
    const driver = source('components/DriverManagementSample.vue');
    const picker = source('components/ImportFilePicker.vue');
    assert.doesNotMatch(driver, /选择系统模板/);
    assert.match(driver, /<ImportFilePicker :file="importFile"/);
    assert.match(source('style.css'), /\.ui-dialog--fixed-height \{ height: min\(900px, calc\(100dvh - 36px\)\)/);
    assert.match(driver, /\.ui-initial-import \{ display: flex; flex: 1; flex-direction: column; min-height: 0; overflow: hidden/);
    assert.match(picker, /file\?\.name \|\| '未选择文件'/);
    assert.match(picker, /<div class="ui-import-file-field" role="status">/);
    assert.match(picker, /<button v-if="file" class="ui-import-file-clear"/);
    assert.match(picker, /<label v-else class="ui-import-file-choice">/);
    assert.match(picker, /\.ui-import-file-controls \{[^}]*flex-wrap:nowrap/);
    assert.match(picker, /aria-label="清除所选文件和预览"/);
    assert.match(picker, /background:#f1f4f2/);
    assert.match(driver, /ui-initial-import-table ul.*color: #b14436/);
    assert.match(driver, /class="ui-import-validation-hint">请修正 Excel/);
    assert.match(source('views/TaskImportsV3View.vue'), /ui-import-validation-hint/);
    assert.match(source('views/TireImportsV3View.vue'), /ui-import-validation-hint/);
    assert.match(source('views/ChargingImportsV3View.vue'), /ui-import-validation-hint/);
    assert.match(source('views/ChargingImportsV3View.vue'), /<ImportFilePicker v-if="editorMode === 'sample'"/);
    assert.match(picker, /emit\('select', props\.multiple \? files : files\[0\]\)/);
    for (const path of ['views/TaskImportsV3View.vue', 'views/TireImportsV3View.vue', 'views/ChargingImportsV3View.vue']) {
      assert.doesNotMatch(source(path), /<ErrorState v-if="error"/);
      assert.doesNotMatch(source(path), /class="error"/);
    }
    assert.match(source('views/ChargingImportsV3View.vue'), /showErrorDialog\('校验失败'/);
    assert.match(source('stores/apiErrorDialog.js'), /export function showErrorDialog/);
  }],
  ['import result filters only change visible rows and expense keeps its original import path', () => {
    const rows = [{ status: 'VALID' }, { status: 'INVALID' }, { status: 'PUBLISHED' }, { status: 'FAILED' }];
    assert.equal(countImportFailures(rows), 2);
    assert.deepEqual(filterImportRows(rows, 'ALL'), rows);
    assert.deepEqual(filterImportRows(rows, 'PASS'), [rows[0], rows[2]]);
    assert.deepEqual(filterImportRows(rows, 'FAIL'), [rows[1], rows[3]]);
    for (const path of ['components/DriverManagementSample.vue', 'views/TaskImportsV3View.vue',
      'views/TireImportsV3View.vue', 'views/ChargingImportsV3View.vue']) {
      assert.match(source(path), /<ImportResultFilter/);
      assert.match(source(path), /<SummaryCards/);
    }
    const expense = source('views/ExpenseApplicationV3View.vue');
    assert.match(expense, /<input type="file" accept="\.xlsx" :disabled="busy" @change="chooseFile"/);
    assert.match(expense, /dailyRows\.value = appendPreviewRows\(dailyRows\.value, preview\.rows, options\.value\)/);
    assert.match(expense, /finally \{ busy\.value = false; event\.target\.value = ''; \}/);
    assert.doesNotMatch(expense, /ImportFilePicker|ImportResultFilter|clearDailyFile|latestImportedKeys/);
    assert.doesNotMatch(source('components/RouteWeightImageUpload.vue'), /ImportFilePicker|pendingFile/);
  }],
  ['formal imports separate preview from completed counts and preserve failure reasons', () => {
    assert.deepEqual(summarizeCompletedRows([
      { rowNo: 2, status: 'IMPORTED' }, { rowNo: 3, status: 'FAILED', message: '胎号重复' },
      { rowNo: 4, status: 'INVALID', message: '到货时间无效' }
    ], 'IMPORTED'), { total: 3, success: 1, failed: 2, pending: 0,
      failures: [{ rowNo: 3, reason: '胎号重复' }, { rowNo: 4, reason: '到货时间无效' }] });
    assert.equal(formatImportDateTime('2026-10-20T19:27:04.062'), '2026-10-20 19:27:04');
    assert.equal(formatImportDateTime('2026-10-20 19:27:04.062'), '2026-10-20 19:27:04');
    assert.equal(formatImportDateTime(null, '尚未确认'), '尚未确认');
    assert.equal(formatDateTime('2026-10-21T05:30:51.325'), '2026-10-21 05:30:51');
    assert.equal(displayValue('generated_at', '2026-10-21T05:30:51.325'), '2026-10-21 05:30:51');
    assert.equal(formatDateTime('2026-10-21'), '2026-10-21');
    assert.equal(formatDateTime(null), '—');
    for (const path of ['views/TireImportsV3View.vue', 'views/TaskImportsV3View.vue']) {
      const page = source(path);
      assert.match(page, /<ImportCompletionSummary v-if="completion"/);
      assert.match(page, /\.value = null;\s*(?:file|selectedFile)\.value = null/);
      assert.match(page, /formatImportDateTime\(/);
    }
    assert.match(source('components/DriverManagementSample.vue'), /importCompletion\.value = \{ total: importPreview\.value\.total, success: result\.imported/);
    assert.match(source('views/ChargingImportsV3View.vue'), /<ImportCompletionSummary v-if="completion"/);
    assert.doesNotMatch(source('views/ExpenseApplicationV3View.vue'), /ImportCompletionSummary|summarizeCompletedRows/);
  }],
  ['unconfirmed tire and task previews do not create import history', () => {
    const api = source('api/modules.js');
    assert.match(api, /apiClient\.upload\('\/task-imports\/confirm', form\)/);
    assert.match(api, /apiClient\.upload\('\/tire-imports\/confirm', form\)/);
    assert.doesNotMatch(api, /task-imports\/\$\{id\}\/confirm|tire-imports\/\$\{id\}\/confirm/);
    assert.match(source('views/TaskImportsV3View.vue'), /taskImportApi\.confirm\(selectedFile\.value, requestKey\.value\)/);
    assert.match(source('views/TireImportsV3View.vue'), /tireApi\.confirm\(file\.value, requestKey\.value\)/);
  }],
  ['tire import detail keeps long results inside its drawer with hover text', () => {
    const page = source('views/TireImportsV3View.vue');
    assert.match(page, /class="tire-import-detail-drawer" title="轮胎入库批次详情"/);
    assert.match(page, /<div class="tire-import-detail-table">/);
    assert.match(page, /<template #message="\{value\}"><span class="tire-import-ellipsis" :title="value \|\| '—'">/);
    assert.match(page, /\.tire-import-detail-table :deep\(\.ui-data-table\) \{ width: 100%; min-width: 0; table-layout: fixed; \}/);
    assert.match(page, /\.tire-import-ellipsis \{ display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; \}/);
  }],
  ['tire inventory exposes manual maintenance next to each editable tire and at the top of its detail', () => {
    const page = source('views/TiresUi2View.vue');
    assert.match(page, /@click="openForEdit\(row\)">人工维护<\/button>/);
    assert.match(page, /class="tire-maintenance-actions"/);
    assert.match(page, /can\('BUTTON_TIRES_MANAGE'\) && row\.status === 'AVAILABLE'/);
    assert.match(page, /can\('BUTTON_TIRES_MANAGE'\) && selected\.status === 'AVAILABLE'/);
    assert.match(page, /已申领轮胎不能修改入库资料/);
    assert.match(page, /@click="beginEdit">人工维护<\/button>/);
    assert.match(page, /tireApi\.edit\(selected\.value\.id/);
    assert.match(page, /<table class="tire-history-table" aria-label="轮胎资料修改记录">/);
    assert.match(page, /<th scope="col">修改项目<\/th><th scope="col">修改内容<\/th><th scope="col">原因与操作<\/th>/);
    assert.match(page, /<tr v-for="\(change, changeIndex\) in group\.changes"/);
    assert.match(page, /<th scope="row">\{\{ fieldName\[change\.fieldName\]/);
    assert.match(page, /<td v-if="changeIndex === 0" :rowspan="group\.changes\.length">/);
    assert.match(page, /colspan="3" class="tire-history-empty"/);
    assert.match(page, /historyValue\(change\.fieldName, change\.beforeValue\)/);
    assert.match(page, /historyValue\(change\.fieldName, change\.afterValue\)/);
    assert.match(page, /<th scope="col">原因与操作<\/th>/);
    assert.match(page, /group\.reason \|\| '未填写'/);
    assert.match(page, /group\.operatorPhone \|\| '未知账号'/);
    assert.match(page, /historyTime\(group\.operatedAt\)/);
    assert.match(page, /<template v-for="\(group, index\) in maintenanceHistory"/);
    assert.match(page, /\.tire-history-table \{ width: 100%; table-layout: fixed/);
    assert.match(page, /white-space: normal; overflow-wrap: anywhere/);
  }],
  ['tire history groups fields from one edit while keeping separate edits apart', () => {
    const first = ['barcode', 'tire_no', 'arrived_at', 'material_description'].map((fieldName, index) => ({
      fieldName, beforeValue: 'old', afterValue: 'new', reason: '校正', operatorPhone: '13900007772',
      operatedAt: `2026-10-20T20:04:19.${String(index + 1).padStart(3, '0')}`
    }));
    const older = { fieldName: 'material_description', beforeValue: 'before', afterValue: 'after',
      reason: '修改', operatorPhone: '13900007772', operatedAt: '2026-10-20T20:01:53.001' };
    const groups = groupTireMaintenanceHistory([...first, older]);
    assert.equal(groups.length, 2);
    assert.deepEqual(groups.map((group) => group.changes.length), [4, 1]);
    assert.deepEqual(groups[0].changes.map((change) => change.fieldName), first.map((change) => change.fieldName));
    assert.equal(groupTireMaintenanceHistory([first[0], { ...first[1], reason: '另一原因' }]).length, 2);
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
  }],
  ['five button roles keep navigation, detail, danger and permission behavior', () => {
    const css = source('design/ui-final.css');
    for (const role of ['primary', 'ui-return-button', 'ui-detail-button', 'ui-danger-button', 'ui-danger-link'])
      assert.ok(css.includes(role), `missing ${role} styling`);
    assert.match(css, /button\.primary\s*\{[^}]*background:/);
    assert.match(css, /\.ui-return-button\s*\{[^}]*border:\s*0/);
    assert.match(css, /button\.ui-detail-button::after\s*\{[^}]*›/);
    assert.match(source('components/ConfirmDialog.vue'), /danger \? 'ui-danger-button' : 'primary'/);
    const approval = source('views/ApprovalCenterV3View.vue');
    assert.match(approval, /class="ui-return-button" @click="backToList"/);
    assert.match(approval, /class="ui-detail-button" @click="openDetail\(row\)"/);
    assert.match(approval, /v-if="detail\.canProcess"[^>]*@click="process\('APPROVE'\)"/);
    assert.match(source('views/TransportTasksV3View.vue'), /v-if="can\('BUTTON_TEMPLATES_MANAGE'\)"[^>]*setTemplateEnabled\(row\)/);
  }],
  ['paged admin tables keep server paging, scroll and empty states', async () => {
    const pagination = source('components/PaginationBar.vue');
    const table = source('components/DataTable.vue');
    const css = source('design/ui-final.css');
    assert.match(pagination, /\[10, 20, 50, 100\]/);
    assert.match(pagination, /resetScroll\(event\)/);
    assert.match(table, /window\.innerHeight - element\.getBoundingClientRect\(\)\.top/);
    assert.match(css, /\.ui-paged-table \.ui-data-table thead th \{ position: sticky/);
    assert.match(table, /<LoadingState v-if="loading"/);
    assert.match(table, /<EmptyState v-else-if="!rows\.length"/);
    for (const page of ['ApprovalCenterV3View', 'FinanceReportsV3View', 'SalaryLedgerV3View',
      'SettlementV3View', 'ExpenseLedgerV3View', 'TransportTasksV3View']) {
      assert.match(source(`views/${page}.vue`), /@change-size="\(size\) => \{ \w+ = size; \w+\(1\); \}"/);
    }
    calls.length = 0;
    await v3ApprovalApi.list('MINE', 1, 50);
    await v3FinanceApi.tasks({ month: '2026-10' }, 1, 100);
    await v3ExpenseLedgerApi.list({}, 1, 10);
    await v3SettlementApi.details(7, 1, 50);
    assert.match(calls[0].url, /pageNo=1&pageSize=50/);
    assert.match(calls[1].url, /pageNo=1&pageSize=100/);
    assert.match(calls[2].url, /pageNo=1&pageSize=10/);
    assert.match(calls[3].url, /pageNo=1&pageSize=50/);
  }],
  ['registration link is shared by login and the authenticated layout', () => {
    const footer = source('components/IcpFooter.vue');
    const login = source('views/LoginView.vue');
    const layout = source('layouts/AppLayout.vue');
    const css = source('design/ui-final.css');
    assert.match(footer, /href="https:\/\/beian\.miit\.gov\.cn\/" target="_blank" rel="noopener noreferrer">鄂ICP备16012832号/);
    assert.match(login, /<IcpFooter \/>/);
    assert.match(layout, /<slot \/>\s*<IcpFooter \/>/);
    assert.doesNotMatch(login + layout, /VITE_ICP_NUMBER|login-icp/);
    assert.match(css, /\.ui-icp-footer \{[^}]*text-align:center/);
    assert.doesNotMatch(css, /\.ui-icp-footer \{[^}]*position:\s*(fixed|absolute)/);
  }],
  ['expense application and approval actions share image attachments without changing daily rows', () => {
    const expense = source('views/ExpenseApplicationV3View.vue');
    const approval = source('views/ApprovalCenterV3View.vue');
    const upload = source('components/ApprovalImageUpload.vue');
    assert.match(expense, /dailyRows\.value = appendPreviewRows/);
    assert.match(expense, /scope\.value === 'DAILY' \? dailyItems\(dailyRows\.value\) : \[\{/);
    assert.match(expense, /attachmentIds: applicationImages\.value\.map/);
    assert.match(approval, /attachmentIds: actionImages\.value\.map/);
    assert.match(approval, /<ApprovalHistoryDetail :detail="detail" section="summary" \/>/);
    assert.match(source('components/ApprovalHistoryDetail.vue'), /submissionEvidenceFor\(version\.id\)/);
    assert.match(upload, /APPROVAL_UPLOAD/);
    assert.match(upload, /emit\('uploading', true\)/);
    assert.match(upload, /<ApprovalEvidence/);
  }],
  ['settlement details use server ordering and approval history detail is reusable', () => {
    const settlement = source('views/SettlementV3View.vue');
    const approval = source('views/ApprovalCenterV3View.vue');
    const historyDetail = source('components/ApprovalHistoryDetail.vue');
    assert.match(settlement, /v3SettlementApi\.details\(chosen\.value\.id, page, pageSize\.value\)/);
    assert.match(settlement, /v3ApprovalApi\.detail\(row\.approvalId\)/);
    assert.match(settlement, /<DetailDrawer :open="approvalOpen" title="审批详情"/);
    assert.match(settlement, /<ApprovalHistoryDetail :detail="approvalDetail" section="summary"/);
    assert.match(approval, /<ApprovalHistoryDetail :detail="detail" section="summary"/);
    assert.match(historyDetail, /处理时间线/);
    assert.match(historyDetail, /发起人当前角色/);
    assert.match(historyDetail, /detail\.approval\.applicant_name/);
    assert.match(historyDetail, /formatTime\(detail\.approval\.created_at\)/);
  }]
];

for (const [name, run] of tests) { await run(); console.log(`ok - ${name}`); }
console.log(`${tests.length} active V3-A/B/C admin tests passed`);
await import('./ui1Tests.mjs');
await import('./routeWeightEvidenceTests.mjs');
await import('./chargingWorkbookTests.mjs');
