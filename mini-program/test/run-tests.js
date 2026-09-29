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
    assert.match(login, /\/auth\/login/);
    assert.match(login, /accessToken/);
    assert.match(profile, /\/me/);
    assert.match(profile, /\/auth\/password/);
    assert.match(home, /\/me/);
    assert.doesNotMatch(home + profile, /\/drivers\?pageNo=1&pageSize=1/);
  }],
  ['driver menu hides salary and tire for outsourced driver', () => {
    const user = { permissions: ['task:view', 'salary:mine', 'tire:request', 'approval:create'] };
    const cards = visibleHomeCards(user, { driverType: 'OUTSOURCED' }).map((item) => item.key);
    assert.deepEqual(cards, ['routes', 'profile']);
  }],
  ['internal driver menu includes the currently available salary capability', () => {
    const user = { permissions: ['task:view', 'salary:mine', 'tire:request'] };
    const cards = visibleHomeCards(user, { driverType: 'INTERNAL' }).map((item) => item.key);
    assert.equal(cards.includes('salary'), true);
    assert.equal(cards.includes('tire'), false);
  }],
  ['retired approval workflows stay out of the active driver menu', () => {
    const user = { permissions: ['approval:create', 'approval:history:view', 'approval:process'] };
    const cards = visibleHomeCards(user, null).map((item) => item.key);
    assert.equal(cards.includes('expenseApply'), false);
    assert.equal(cards.includes('approvals'), false);
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
  ['retired pages are not listed in the active mini-program package', () => {
    const pages = readJson('app.json').pages;
    for (const retired of ['pages/tire-request/index', 'pages/expense-apply/index', 'pages/approvals/index', 'pages/approval-detail/index']) {
      assert.equal(pages.includes(retired), false);
    }
  }]
];

tests.push(...require('./f4e-tests'));
tests.push(...require('./ui3-tests'));
tests.push(...require('./auth-session-tests'));

(async () => {
  for (const [name, run] of tests) {
    await run();
    console.log(`ok - ${name}`);
  }
  console.log(`${tests.length} tests passed`);
})().catch((error) => { console.error(error); process.exitCode = 1; });
