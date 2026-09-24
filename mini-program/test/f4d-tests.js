const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { availableTemplates, launchReason, allowedAttributions, newItem,
  normalizeAmount, buildExpensePayload } = require('../utils/approvalApplication');
const { visibleHomeCards } = require('../utils/permissions');

const root = path.resolve(__dirname, '..');
const read = (name) => fs.readFileSync(path.join(root, name), 'utf8');
const expense = (changes = {}) => ({ templateId: 11, category: 'EXPENSE', expenseType: 'OTHER',
  evidenceRequirement: 'NONE', allowedInputModes: ['SINGLE'],
  allowedAttributionTypes: ['ROUTE', 'DAILY'], ...changes });
const item = (changes = {}) => ({ itemNo: 'm1', vehicleId: 2, driverId: 3, amount: '0.01',
  businessDate: '2026-09-24', remark: '', ...changes });

function loadPage(relativePath) {
  const full = path.join(root, relativePath);
  const prior = global.Page;
  let definition;
  global.Page = (page) => { definition = page; };
  try {
    delete require.cache[require.resolve(full)];
    require(full);
  } finally { global.Page = prior; }
  const page = { ...definition, data: structuredClone(definition.data) };
  page.setData = (updates) => {
    for (const [key, value] of Object.entries(updates)) {
      const parts = key.replace(/\[(\d+)\]/g, '.$1').split('.');
      let target = page.data;
      for (const part of parts.slice(0, -1)) target = target[part];
      target[parts.at(-1)] = value;
    }
  };
  return page;
}

const cases = [
  ['available templates come only from server result and exclude tire', () => {
    assert.deepEqual(availableTemplates([expense(), { category: 'TIRE' }, { category: 'OTHER' }]).map((t) => t.category), ['EXPENSE', 'OTHER']);
  }],
  ['empty available list stays empty', () => assert.deepEqual(availableTemplates(null), [])],
  ['single expense template is actionable', () => assert.equal(launchReason(expense()), '')],
  ['excel only template directs user to admin', () => assert.equal(launchReason(expense({ allowedInputModes: ['EXCEL'] })), '请在管理端发起')],
  ['other category does not invent a mobile form', () => assert.equal(launchReason({ category: 'OTHER' }), '请在管理端发起')],
  ['route only attribution is preserved', () => assert.deepEqual(allowedAttributions(expense({ allowedAttributionTypes: ['ROUTE'] })), ['ROUTE'])],
  ['daily only attribution is preserved', () => assert.deepEqual(allowedAttributions(expense({ allowedAttributionTypes: ['DAILY'] })), ['DAILY'])],
  ['dual attribution is preserved without pending attribution', () => assert.deepEqual(allowedAttributions(expense({ allowedAttributionTypes: ['ROUTE', 'DAILY', 'PENDING_ATTRIBUTION'] })), ['ROUTE', 'DAILY'])],
  ['route submission requires route selection', () => assert.throws(() => buildExpensePayload(expense(), 'ROUTE', '', [item()], []), /线路/)],
  ['route submission sends route id', () => assert.equal(buildExpensePayload(expense(), 'ROUTE', 5, [item()], []).routeId, 5)],
  ['daily submission clears route id', () => assert.equal(buildExpensePayload(expense(), 'DAILY', 5, [item()], []).routeId, null)],
  ['template attribution cannot be bypassed', () => assert.throws(() => buildExpensePayload(expense({ allowedAttributionTypes: ['DAILY'] }), 'ROUTE', 5, [item()], []), /归属/)],
  ['items carry exact backend fields', () => assert.deepEqual(buildExpensePayload(expense(), 'DAILY', null, [item()], []).items[0],
    { itemNo: 'm1', vehicleId: 2, driverId: 3, amount: '0.01', businessDate: '2026-09-24' })],
  ['duplicate itemNo is rejected', () => assert.throws(() => buildExpensePayload(expense(), 'DAILY', null, [item(), item()], []), /重复/)],
  ['invalid itemNo is rejected', () => assert.throws(() => buildExpensePayload(expense(), 'DAILY', null, [item({ itemNo: 'a b' })], []), /编号/)],
  ['internal driver is prefilled from me', () => assert.deepEqual([newItem('x', { driverType: 'INTERNAL', driverId: 3, currentVehicleId: 2 }).driverId,
    newItem('x', { driverType: 'INTERNAL', driverId: 3, currentVehicleId: 2 }).vehicleId], [3, 2])],
  ['non-driver item does not guess vehicle or driver', () => assert.deepEqual([newItem('x').vehicleId, newItem('x').driverId], ['', ''])],
  ['point seventy eight remains exact decimal string', () => assert.equal(normalizeAmount('.78'), '0.78')],
  ['one cent remains exact decimal string', () => assert.equal(buildExpensePayload(expense(), 'DAILY', null, [item()], []).items[0].amount, '0.01')],
  ['zero, exponent and third decimal are rejected', () => {
    for (const amount of ['0', '1e3', '1.234', '-1']) assert.throws(() => normalizeAmount(amount));
  }],
  ['none evidence can be absent', () => assert.deepEqual(buildExpensePayload(expense(), 'DAILY', null, [item()], []).attachmentIds, [])],
  ['optional evidence can be absent', () => assert.deepEqual(buildExpensePayload(expense({ evidenceRequirement: 'OPTIONAL' }), 'DAILY', null, [item()], []).attachmentIds, [])],
  ['required evidence cannot be absent', () => assert.throws(() => buildExpensePayload(expense({ evidenceRequirement: 'REQUIRED' }), 'DAILY', null, [item()], []), /图片依据/)],
  ['uploaded attachment IDs are submitted', () => assert.deepEqual(buildExpensePayload(expense({ evidenceRequirement: 'REQUIRED' }), 'DAILY', null, [item()], [21, 22]).attachmentIds, [21, 22])],
  ['failed evidence upload keeps form editable and shows backend error', async () => {
    const requestModule = require('../utils/request');
    const originalUpload = requestModule.uploadFile;
    requestModule.uploadFile = async () => { throw new Error('附件无权限 (requestId: f4d-test)'); };
    try {
      const page = loadPage('pages/expense-apply/index.js');
      page.data.user = { id: 4 };
      page.data.imagePath = 'test-image.jpg';
      await page.uploadEvidence();
      assert.match(page.data.error, /requestId: f4d-test/);
      assert.equal(page.data.uploading, false);
      assert.deepEqual(page.data.evidence, []);
    } finally { requestModule.uploadFile = originalUpload; }
  }],
  ['energy template requires energy detail', () => assert.throws(() => buildExpensePayload(expense({ expenseType: 'ELECTRIC' }), 'DAILY', null, [item()], []), /能源明细/)],
  ['energy detail preserves decimal quantity as text', () => {
    const result = buildExpensePayload(expense({ expenseType: 'GAS' }), 'DAILY', null,
      [item({ energyType: 'GAS', orderNo: 'G-1', startTime: '2026-09-24T10:20', quantity: '0.001' })], []);
    assert.equal(result.items[0].energyDetail.quantity, '0.001');
  }],
  ['item numbers survive deletion and insertion', () => {
    const page = loadPage('pages/expense-apply/index.js');
    page.data.user = { driverType: 'INTERNAL', driverId: 3, currentVehicleId: 2 };
    page.addItem(); page.addItem();
    const kept = page.data.items[1].itemNo;
    page.removeItem({ currentTarget: { dataset: { index: 0 } } });
    page.addItem();
    assert.equal(page.data.items[0].itemNo, kept);
    assert.notEqual(page.data.items[1].itemNo, kept);
  }],
  ['driver approval create permission exposes only one approval entry', () => {
    const cards = visibleHomeCards({ permissions: ['approval:create'] }, { driverType: 'INTERNAL' });
    assert.deepEqual(cards.map((card) => card.key), ['profile', 'approvals']);
  }],
  ['outsourced driver cannot see approval, tire or salary even with permissions', () => {
    const cards = visibleHomeCards({ permissions: ['approval:create', 'tire:request', 'salary:mine'] }, { driverType: 'OUTSOURCED' });
    assert.deepEqual(cards.map((card) => card.key), ['profile']);
  }],
  ['outsourced driver cannot open tire request form through a direct page link', async () => {
    const requestModule = require('../utils/request');
    const originalRequest = requestModule.request;
    requestModule.request = async () => ({ driverId: 3, driverType: 'OUTSOURCED',
      currentVehicleId: 2, permissions: ['tire:request'] });
    try {
      const page = loadPage('pages/tire-request/index.js');
      await page.load();
      assert.equal(page.data.canRequest, false);
      assert.match(page.data.error, /无换胎申请权限/);
      assert.match(read('pages/tire-request/index.wxml'), /wx:if="\{\{canRequest\}\}"/);
    } finally { requestModule.request = originalRequest; }
  }],
  ['internal driver with tire request permission can open tire request form', async () => {
    const requestModule = require('../utils/request');
    const originalRequest = requestModule.request;
    requestModule.request = async () => ({ driverId: 2, driverType: 'INTERNAL',
      currentVehicleId: 2, permissions: ['tire:request'] });
    try {
      const page = loadPage('pages/tire-request/index.js');
      await page.load();
      assert.equal(page.data.canRequest, true);
      assert.equal(page.data.vehicleId, 2);
    } finally { requestModule.request = originalRequest; }
  }],
  ['internal driver tire and salary stay available with permissions', () => {
    const keys = visibleHomeCards({ permissions: ['tire:request', 'salary:mine'] }, { driverType: 'INTERNAL' }).map((card) => card.key);
    assert.equal(keys.includes('tire') && keys.includes('salary'), true);
  }],
  ['tire request page resolves confirmed number without inventory list', () => {
    const source = read('pages/tire-request/index.js');
    assert.match(source, /\/tires\/request-target\?tireNumber=/);
    assert.doesNotMatch(source, /\/tires\?pageNo=/);
    assert.match(source, /\/ocr\/\$\{this\.data\.ocrRecordId\}\/confirm/);
  }],
  ['expense page uses available template and no excel or old snapshot', () => {
    const source = read('pages/expense-apply/index.js');
    assert.match(source, /\/approval-templates\/available/);
    assert.doesNotMatch(source, /businessSnapshot|parseFloat|toFixed|\.xlsx|\/imports/);
  }],
  ['approval center exposes mine and authorized todo', () => {
    const source = read('pages/approvals/index.js');
    assert.match(source, /mine:\s*'MINE'/);
    assert.match(source, /pending:\s*'TODO'/);
    assert.match(read('pages/approvals/index.wxml'), /wx:if="\{\{canTodo\}\}"/);
  }],
  ['submission evidence and action attachments remain separate', () => {
    const source = read('pages/approval-detail/index.wxml');
    assert.match(source, /item\.attachments/);
    assert.match(source, /action\.attachments/);
    assert.match(source, /V\{\{item\.versionNo\}\}/);
  }],
  ['approval actions are guarded by permission and pending task', () => {
    const source = read('pages/approval-detail/index.js');
    assert.match(source, /myPendingTask/);
    assert.match(source, /approval:process/);
    assert.match(source, /approval:return/);
    assert.match(read('pages/approval-detail/index.wxml'), /wx:if="\{\{canApprove\}\}"/);
  }],
  ['page requests use shared API helper only', () => {
    for (const file of ['pages/expense-apply/index.js', 'pages/approvals/index.js',
      'pages/approval-detail/index.js', 'pages/tire-request/index.js']) assert.doesNotMatch(read(file), /wx\.request\s*\(/);
  }],
  ['shared request helper retains request ID and handles 401 and 403', () => {
    const source = read('utils/request.js');
    assert.match(source, /requestId/); assert.match(source, /statusCode === 401/); assert.match(source, /statusCode === 403/);
  }],
  ['no second expense home entry exists', () => {
    assert.doesNotMatch(read('utils/permissions.js'), /key: 'expenseApply'/);
    assert.match(read('pages/approvals/index.js'), /pages\/expense-apply\/index/);
  }]
];

module.exports = cases;
