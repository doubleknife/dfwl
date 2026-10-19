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
  ['approval and expense workflows require internal driver and permission', () => {
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
    assert.match(tire, /\/tires\/request-target/);
    assert.match(tire, /\/tire-requests/);
    assert.match(tire, /driverType !== 'INTERNAL'/);
  }]
];

tests.push(...require('./f4e-tests'));
tests.push(...require('./ui3-tests'));
tests.push(...require('./auth-session-tests'));
tests.push(...require('./change-password-tests'));

(async () => {
  for (const [name, run] of tests) {
    await run();
    console.log(`ok - ${name}`);
  }
  console.log(`${tests.length} tests passed`);
})().catch((error) => { console.error(error); process.exitCode = 1; });
