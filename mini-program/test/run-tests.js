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
    const home = fs.readFileSync(path.join(root, 'pages/home/index.js'), 'utf8');
    const tire = fs.readFileSync(path.join(root, 'pages/tire-request/index.js'), 'utf8');
    assert.match(login, /\/auth\/login/);
    assert.match(login, /accessToken/);
    assert.match(profile, /\/me/);
    assert.match(profile, /\/auth\/password/);
    assert.match(home, /\/me/);
    assert.match(tire, /\/me/);
    assert.doesNotMatch(home + profile + tire, /\/drivers\?pageNo=1&pageSize=1/);
  }],
  ['driver menu hides salary and tire for outsourced driver', () => {
    const user = { permissions: ['route:list', 'salary:mine', 'tire:request', 'approval:create'] };
    const cards = visibleHomeCards(user, { driverType: 'OUTSOURCED' }).map((item) => item.key);
    assert.deepEqual(cards, ['routes', 'profile']);
  }],
  ['internal driver menu includes salary and tire capabilities', () => {
    const user = { permissions: ['route:list', 'salary:mine', 'tire:request'] };
    const cards = visibleHomeCards(user, { driverType: 'INTERNAL' }).map((item) => item.key);
    assert.equal(cards.includes('salary'), true);
    assert.equal(cards.includes('tire'), true);
  }],
  ['configured role menu exposes expense approval and approval center', () => {
    const user = { permissions: ['approval:create', 'approval:history:view', 'approval:process'] };
    const cards = visibleHomeCards(user, null).map((item) => item.key);
    assert.equal(cards.includes('expenseApply'), true);
    assert.equal(cards.includes('approvals'), true);
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
  ['no mock placeholder or todo remains in mini program source', () => {
    const files = walk(root).filter((file) => /\.(js|wxml|json|wxss)$/.test(file) && !file.includes(`${path.sep}test${path.sep}`));
    const offenders = files.filter((file) => /mock|placeholder|TODO|占位|尚未提供/i.test(fs.readFileSync(file, 'utf8')));
    assert.deepEqual(offenders.map((file) => path.relative(root, file)), []);
  }],
  ['driver-facing route pages do not render sensitive money fields', () => {
    const routeList = fs.readFileSync(path.join(root, 'pages/routes/index.wxml'), 'utf8');
    const routeDetail = fs.readFileSync(path.join(root, 'pages/route-detail/index.wxml'), 'utf8');
    const sensitive = /taxUnitPrice|carryingFee|incomeAmount|profitAmount|routeExpense|dailyExpense|费用|利润|单价|收入/;
    assert.equal(sensitive.test(routeList), false);
    assert.equal(sensitive.test(routeDetail), false);
  }],
  ['approval and tire flows use real backend endpoints', () => {
    const tire = fs.readFileSync(path.join(root, 'pages/tire-request/index.js'), 'utf8');
    const expense = fs.readFileSync(path.join(root, 'pages/expense-apply/index.js'), 'utf8');
    const approvals = fs.readFileSync(path.join(root, 'pages/approval-detail/index.js'), 'utf8');
    assert.match(tire, /\/attachments/);
    assert.match(tire, /\/ocr\/tire-number/);
    assert.match(tire, /\/tire-requests/);
    assert.match(expense, /\/approvals/);
    assert.match(approvals, /\/approvals\/\$\{this\.data\.id\}\/approve/);
    assert.match(approvals, /return-applicant/);
    assert.match(approvals, /return-node/);
  }]
];

for (const [name, run] of tests) {
  run();
  console.log(`ok - ${name}`);
}

console.log(`${tests.length} tests passed`);
