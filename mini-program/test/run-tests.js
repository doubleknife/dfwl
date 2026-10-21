const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const { visibleHomeCards } = require('../utils/permissions');
const { containsSensitiveField, netWeight, sanitizeRoute, validateWeights } = require('../utils/business');

function readJson(relativePath) {
  return JSON.parse(fs.readFileSync(path.join(root, relativePath), 'utf8'));
}

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    return entry.isDirectory() ? walk(full) : [full];
  });
}

const tests = [
  ['approval and current transport statuses use semantic color tags', () => {
    const approvalMarkup = fs.readFileSync(path.join(root, 'pages/approvals/index.wxml'), 'utf8');
    const approvalPage = fs.readFileSync(path.join(root, 'pages/approvals/index.js'), 'utf8');
    const currentTask = fs.readFileSync(path.join(root, 'components/current-task-card/index.wxml'), 'utf8');
    assert.match(approvalMarkup, /<status-tag label="\{\{item\.statusLabel\}\}" tone="\{\{item\.statusTone\}\}"/);
    assert.match(approvalMarkup, /<status-tag label="\{\{detail\.statusLabel\}\}" tone="\{\{detail\.statusTone\}\}"/);
    assert.match(approvalPage, /status === 'APPROVED' \? 'success'/);
    assert.match(currentTask, /<status-tag label="\{\{task\.statusLabel\}\}" tone="\{\{task\.tone\}\}"/);
    assert.equal(require('../utils/homePresentation').routePresentation({ status: 'CANCELLED' }).tone, 'danger');
  }],
  ['login and me endpoints are wired', () => {
    const login = fs.readFileSync(path.join(root, 'pages/login/index.js'), 'utf8');
    const profile = fs.readFileSync(path.join(root, 'pages/profile/index.js'), 'utf8');
    const changePassword = fs.readFileSync(path.join(root, 'pages/change-password/index.js'), 'utf8');
    const home = fs.readFileSync(path.join(root, 'pages/home/index.js'), 'utf8');
    assert.match(login, /\/auth\/login/);
    assert.match(login, /accessToken/);
    assert.match(profile, /\/me/);
    assert.match(changePassword, /\/auth\/password/);
    assert.match(profile, /\/pages\/change-password\/index/);
    assert.match(home, /\/me/);
    assert.doesNotMatch(home + profile, /\/drivers\?pageNo=1&pageSize=1/);
  }],
  ['driver menu hides salary and tire for outsourced driver', () => {
    const user = { permissions: ['PAGE_MINI_TIRE_REQUEST', 'PAGE_MINI_EXPENSE_APPLY', 'PAGE_MINI_ROUTES', 'PAGE_MINI_SALARY', 'BUTTON_MINI_TIRE_REQUEST_SUBMIT', 'BUTTON_MINI_EXPENSE_APPLY_SUBMIT'] };
    const cards = visibleHomeCards(user, { driverType: 'OUTSOURCED' }).map((item) => item.key);
    assert.deepEqual(cards, ['routes', 'profile']);
  }],
  ['internal driver menu includes the currently available salary capability', () => {
    const user = { permissions: ['PAGE_MINI_TIRE_REQUEST', 'PAGE_MINI_ROUTES', 'PAGE_MINI_SALARY', 'BUTTON_MINI_TIRE_REQUEST_SUBMIT'] };
    const cards = visibleHomeCards(user, { driverType: 'INTERNAL' }).map((item) => item.key);
    assert.equal(cards.includes('salary'), true);
    assert.equal(cards.includes('tire'), false);
  }],
  ['driver expense actions require permission and exclude outsourced drivers', () => {
    const user = { permissions: ['PAGE_MINI_EXPENSE_APPLY', 'BUTTON_MINI_EXPENSE_APPLY_SUBMIT', 'PAGE_MINI_APPROVALS', 'BUTTON_MINI_APPROVALS_APPROVE'] };
    const { visibleHomeActions } = require('../utils/permissions');
    assert.deepEqual(visibleHomeActions(user, { driverType: 'INTERNAL' }).map((item) => item.key), ['expense', 'approval']);
    assert.deepEqual(visibleHomeActions(user, { driverType: 'OUTSOURCED' }).map((item) => item.key), []);
  }],
  ['manager sees approval tasks without driver routes and password minimum stays six', () => {
    const { visibleHomeActions } = require('../utils/permissions');
    const manager = { roleCode: 'ADMIN', permissions: ['PAGE_MINI_APPROVALS', 'PAGE_MINI_ROUTES', 'BUTTON_MINI_APPROVALS_APPROVE'] };
    assert.deepEqual(visibleHomeActions(manager, null).map((item) => item.key), ['approval']);
    assert.deepEqual(visibleHomeCards(manager, null).map((item) => item.key), ['profile']);
    const approvalPage = fs.readFileSync(path.join(root, 'pages/approvals/index.js'), 'utf8');
    const passwordPage = fs.readFileSync(path.join(root, 'pages/change-password/index.js'), 'utf8');
    assert.match(approvalPage, /canProcess/);
    assert.match(passwordPage, /length < 6/);
  }],
  ['orphan mini button is not visible without its parent page grant', () => {
    const { visibleHomeActions, hasPermission } = require('../utils/permissions');
    const orphan = { permissions: ['BUTTON_MINI_APPROVALS_APPROVE'] };
    assert.equal(hasPermission(orphan, 'BUTTON_MINI_APPROVALS_APPROVE'), false);
    assert.deepEqual(visibleHomeActions(orphan, null), []);
  }],
  ['weight validation blocks gross less than or equal tare', () => {
    assert.equal(validateWeights('10', '10').ok, false);
    assert.equal(validateWeights('9', '10').ok, false);
    assert.equal(validateWeights('12.5', '10').ok, true);
    assert.equal(netWeight('12.5', '10'), '2.500');
  }],
  ['route sanitizer removes sensitive business fields', () => {
    const route = sanitizeRoute({ routeNo: 'R1', taxUnitPrice: '1.00', profitAmount: '9.00', amount: '3.00' });
    assert.equal(containsSensitiveField(route), false);
    assert.equal(route.routeNo, 'R1');
  }],
  ['app pages configuration points to existing page files', () => {
    const app = readJson('app.json');
    for (const page of app.pages) {
      assert.equal(fs.existsSync(path.join(root, `${page}.js`)), true, `${page}.js`);
      assert.equal(fs.existsSync(path.join(root, `${page}.wxml`)), true, `${page}.wxml`);
      assert.equal(fs.existsSync(path.join(root, `${page}.json`)), true, `${page}.json`);
    }
  }],
  ['no mock or todo remains in mini program source', () => {
    const files = walk(root).filter((file) => /\.(js|wxml|json|wxss)$/.test(file)
      && !file.includes(`${path.sep}test${path.sep}`) && !file.endsWith('project.private.config.json'));
    const offenders = files.filter((file) => /mock|\/\/\s*TODO|\/\*\s*TODO|占位|尚未提供/i.test(fs.readFileSync(file, 'utf8')));
    assert.deepEqual(offenders.map((file) => path.relative(root, file)), []);
  }],
  ['driver-facing route pages do not render sensitive money fields', () => {
    const routeList = fs.readFileSync(path.join(root, 'pages/routes/index.wxml'), 'utf8');
    const routeDetail = fs.readFileSync(path.join(root, 'pages/route-detail/index.wxml'), 'utf8');
    const sensitive = /taxUnitPrice|carryingFee|incomeAmount|profitAmount|routeExpense|dailyExpense|费用|利润|单价|收入/;
    assert.equal(sensitive.test(routeList), false);
    assert.equal(sensitive.test(routeDetail), false);
  }],
  ['V3-F tire request is the sole driver entry and uses photo, OCR and approval', () => {
    const pages = readJson('app.json').pages;
    assert.equal(pages.includes('pages/expense-apply/index'), true);
    assert.equal(pages.includes('pages/approvals/index'), true);
    assert.equal(pages.filter((page) => page === 'pages/tire-request/index').length, 1);
    assert.equal(pages.includes('pages/approval-detail/index'), false);
    const tire = fs.readFileSync(path.join(root, 'pages/tire-request/index.js'), 'utf8');
    assert.match(tire, /chooseMedia/);
    assert.match(tire, /purpose: 'TIRE_OCR'/);
    assert.match(tire, /\/tire-requests\/ocr/);
    assert.match(tire, /\/tire-requests\/validate/);
    assert.match(tire, /\/tire-requests/);
    assert.match(tire, /driverType !== 'INTERNAL'/);
  }]
];

tests.push(...require('./f4e-tests'));
tests.push(...require('./ui3-tests'));
tests.push(...require('./auth-session-tests'));
tests.push(...require('./change-password-tests'));
tests.push(...require('./tire-request-tests'));
tests.push(...require('./search-select-tests'));

tests.push(['transport detail shows readable departure and unload times', () => {
  const { formatDateTime } = require('../utils/dateTime');
  assert.equal(formatDateTime('2026-10-21T05:30:51.325'), '2026-10-21 05:30:51');
  const page = fs.readFileSync(path.join(root, 'pages/route-detail/index.js'), 'utf8');
  const markup = fs.readFileSync(path.join(root, 'pages/route-detail/index.wxml'), 'utf8');
  assert.match(page, /departureLabel: formatDateTime\(safe\.departureTime\)/);
  assert.match(page, /unloadLabel: formatDateTime\(safe\.unloadTime\)/);
  assert.match(markup, /\{\{route\.departureLabel\}\}/);
  assert.match(markup, /\{\{route\.unloadLabel\}\}/);
}]);

tests.push(['button roles keep touch targets and existing approval actions', () => {
  const appStyle = fs.readFileSync(path.join(root, 'app.wxss'), 'utf8');
  const tokens = fs.readFileSync(path.join(root, 'design/tokens.wxss'), 'utf8');
  for (const role of ['button.primary', 'button.secondary', 'button.mp-return-button', 'button.mp-detail-button', 'button.mp-danger'])
    assert.ok(appStyle.includes(role), `missing ${role} styling`);
  assert.match(tokens, /--mp-button-height:\s*88rpx/);
  assert.match(appStyle, /button\[disabled\]\s*\{[^}]*opacity:/);
  const approval = fs.readFileSync(path.join(root, 'pages/approvals/index.wxml'), 'utf8');
  assert.match(approval, /class="page-return-button mp-return-button" bindtap="back"/);
  assert.match(approval, /class="card-action mp-detail-link">查看详情 ›/);
  assert.match(approval, /class="approval-card" data-id="\{\{item\.id\}\}" bindtap="open"/);
  assert.match(approval, /wx:if="\{\{detail\.canApprove\}\}"[^>]*data-kind="APPROVE" bindtap="act"/);
  assert.match(fs.readFileSync(path.join(root, 'pages/tire-request/index.wxml'), 'utf8'), /class="mp-danger"[^>]*bindtap="removeItem"/);
  assert.match(fs.readFileSync(path.join(root, 'pages/weight/index.wxml'), 'utf8'), /class="mp-danger"[^>]*bindtap="removeTicket"/);
}]);

tests.push(['expense energy choices filter daily plates and participated task vehicles', () => {
  const { vehicleEnergyError, energyHint, eligibleVehicles, eligibleTasks } = require('../utils/expenseEnergy');
  const vehicles = [{ id: 1, energyType: 'ELECTRIC' }, { id: 2, energyType: 'GAS' }];
  const tasks = [{ taskId: 10, vehicleId: 1 }, { taskId: 11, vehicleId: 2 }];
  for (const type of ['ELECTRIC', 'TEMP_ELECTRIC']) {
    assert.deepEqual(eligibleVehicles(type, vehicles).map((row) => row.id), [1]);
    assert.deepEqual(eligibleTasks(type, tasks, vehicles).map((row) => row.taskId), [10]);
  }
  for (const type of ['GAS', 'TEMP_GAS']) {
    assert.deepEqual(eligibleVehicles(type, vehicles).map((row) => row.id), [2]);
    assert.deepEqual(eligibleTasks(type, tasks, vehicles).map((row) => row.taskId), [11]);
  }
  assert.equal(eligibleVehicles('REPAIR', vehicles).length, 2);
  assert.equal(vehicleEnergyError('ELECTRIC', { energyType: 'GAS' }), '电费、临时电费只能选择电车');
  assert.equal(vehicleEnergyError('TEMP_GAS', { energyType: 'ELECTRIC' }), '气费、临时气费只能选择气车');
  assert.equal(vehicleEnergyError('GAS', { energyType: 'GAS' }), '');
  assert.match(energyHint('TEMP_ELECTRIC'), /电车/);
}]);

tests.push(['mini expense remains one item and approval images keep only successful uploads', async () => {
  const requestModule = require('../utils/request');
  const originalUpload = requestModule.uploadFile;
  const originalWx = global.wx;
  const helperPath = require.resolve('../utils/approvalImages');
  const selected = [];
  let uploads = 0;
  let retained = [];
  try {
    global.wx = { chooseMedia(options) {
      selected.push(options);
      options.success({ tempFiles: [{ tempFilePath: '/tmp/one.jpg', size: 10 },
        { tempFilePath: '/tmp/two.jpg', size: 10 }] });
    } };
    requestModule.uploadFile = async (options) => {
      uploads += 1;
      assert.equal(options.formData.ownerType, 'APPROVAL_UPLOAD');
      assert.equal(options.formData.ownerId, '501');
      if (uploads === 2) throw new Error('第二张上传失败');
      return { id: 70 };
    };
    delete require.cache[helperPath];
    const { chooseApprovalImages, singleExpenseItem } = require('../utils/approvalImages');
    assert.deepEqual(singleExpenseItem({ amount: 1 }), [{ amount: 1 }]);
    assert.throws(() => singleExpenseItem([{ amount: 1 }, { amount: 2 }]), /只能提交一笔/);
    await assert.rejects(chooseApprovalImages([], 501, 'APPROVAL_APPLICATION', (images) => { retained = images; }),
      /第二张上传失败/);
    assert.deepEqual(retained.map((image) => image.id), [70]);
    assert.deepEqual(selected[0].sourceType, ['camera', 'album']);
    const expense = fs.readFileSync(path.join(root, 'pages/expense-apply/index.js'), 'utf8');
    const approval = fs.readFileSync(path.join(root, 'pages/approvals/index.js'), 'utf8');
    assert.match(expense, /const items = singleExpenseItem\(item\)/);
    assert.match(expense, /items, attachmentIds: d\.images\.map/);
    assert.match(approval, /attachmentIds: this\.data\.actionImages\.map/);
  } finally {
    requestModule.uploadFile = originalUpload;
    global.wx = originalWx;
    delete require.cache[helperPath];
  }
}]);

(async () => {
  for (const [name, run] of tests) {
    await run();
    console.log(`ok - ${name}`);
  }
  console.log(`${tests.length} tests passed`);
})().catch((error) => { console.error(error); process.exitCode = 1; });
