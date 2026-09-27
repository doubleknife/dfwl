const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { totalAmount } = require('../utils/approvalApplication');
const { presentDetail, snapshotFacts } = require('../utils/approvalPresentation');

async function pageScenario(name, request, run, wxOverrides = {}) {
  const module = require('../utils/request');
  const previousRequest = module.request;
  const previousPage = global.Page;
  const previousWx = global.wx;
  const previousApp = global.getApp;
  const pagePath = require.resolve(`../pages/${name}/index.js`);
  let definition;
  module.request = request;
  global.Page = (value) => { definition = value; };
  global.wx = { showToast() {}, navigateTo() {}, showModal() {}, ...wxOverrides };
  global.getApp = () => ({ globalData: { user: { id: 1 } } });
  try {
    delete require.cache[pagePath];
    require(pagePath);
    const page = { ...definition, data: structuredClone(definition.data) };
    page.setData = (changes) => Object.assign(page.data, changes);
    await run(page);
  } finally {
    module.request = previousRequest;
    global.Page = previousPage;
    global.wx = previousWx;
    global.getApp = previousApp;
    delete require.cache[pagePath];
  }
}

module.exports = [
  ['UI4 approval versions keep distinct facts and action evidence', () => {
    const detail = presentDetail({ approval: { businessType: 'TIRE_REQUEST', status: 'PENDING' },
      displayNames: { vehicles: { 7: '云A001' }, drivers: { 9: '张师傅' } },
      submissions: [
        { id: 1, versionNo: 1, businessSnapshotJson: '{"driverId":9,"vehicleId":7,"items":[{"confirmedTireNo":"H111"}]}', attachments: [{ id: 10 }] },
        { id: 2, versionNo: 2, businessSnapshotJson: '{"items":[{"confirmedTireNo":"H222"}]}', attachments: [{ id: 11 }] }
      ], tasks: [{ id: 3, status: 'APPROVED', actions: [{ id: 4, actionType: 'RETURN_APPLICANT', attachments: [{ id: 12 }] }] }] });
    assert.equal(detail.submissions[0].facts.find((fact) => fact.label === '确认胎号').value, 'H111');
    assert.equal(detail.submissions[0].facts.find((fact) => fact.label.includes('车辆')).value, '云A001');
    assert.equal(detail.submissions[1].facts.find((fact) => fact.label === '确认胎号').value, 'H222');
    assert.equal(detail.submissions[0].attachments[0].id, 10);
    assert.equal(detail.tasks[0].actions[0].attachments[0].id, 12);
    assert.equal(detail.tasks[0].actions[0].actionLabel, '打回申请人');
    const wxml = fs.readFileSync(path.join(__dirname, '../pages/approval-detail/index.wxml'), 'utf8');
    assert.doesNotMatch(wxml, /\{\{item\.businessSnapshotJson\}\}|\{\{item\.approverUserId\}\}|预览原始图片 #|打回节点序号/);
  }],
  ['UI4 expense total uses exact decimal arithmetic', () => {
    assert.equal(totalAmount([{ amount: '0.10' }, { amount: '0.20' }, { amount: '999999999999.99' }]), '1000000000000.29');
    assert.throws(() => totalAmount([{ amount: '0.001' }]));
    assert.equal(snapshotFacts('EXPENSE', '{"expenseType":"REPAIR","attributionType":"ROUTE","items":[{"amount":"12.30","businessDate":"2026-09-27"}]}')
      .some((fact) => fact.value === '¥ 12.30'), true);
  }],
  ['UI4 salary keeps true server summary while loading later detail pages', async () => {
    await pageScenario('salary', async ({ url }) => {
      if (url.includes('monthly-summary')) return { totalAmount: '300.30', entryCount: 25 };
      if (url.includes('pageNo=2')) return { total: 25, records: Array.from({ length: 5 }, (_, i) => ({ id: i + 21 })) };
      return { total: 25, records: Array.from({ length: 20 }, (_, i) => ({ id: i + 1 })) };
    }, async (page) => {
      await page.load();
      assert.equal(page.data.totalAmount, '300.30');
      assert.equal(page.data.records.length, 20);
      assert.equal(page.data.hasMore, true);
      await page.loadMore();
      assert.equal(page.data.records.length, 25);
      assert.equal(page.data.totalAmount, '300.30');
    });
  }],
  ['UI4 salary error clears the previous month total', async () => {
    let fail = false;
    await pageScenario('salary', async ({ url }) => {
      if (fail) throw new Error('网络中断');
      return url.includes('monthly-summary') ? { totalAmount: '880.00', entryCount: 1 }
        : { total: 1, records: [{ id: 1 }] };
    }, async (page) => {
      await page.load();
      assert.equal(page.data.totalAmount, '880.00');
      fail = true;
      page.data.businessMonth = '2026-08';
      await page.load();
      assert.equal(page.data.totalAmount, '0.00');
      assert.equal(page.data.entryCount, 0);
      assert.match(page.data.error, /网络中断/);
    });
  }],
  ['UI4 expense steps validate route and show an exact confirmation summary', async () => {
    await pageScenario('expense-apply', async () => ({}), async (page) => {
      page.data.template = { templateId: 2, flowName: '维修费申请', category: 'EXPENSE',
        allowedInputModes: ['SINGLE'], allowedAttributionTypes: ['ROUTE'], evidenceRequirement: 'OPTIONAL' };
      page.data.canCompose = true;
      page.data.attributionType = 'ROUTE';
      page.nextStep();
      assert.equal(page.data.step, 2);
      page.nextStep();
      assert.equal(page.data.step, 2);
      assert.match(page.data.error, /线路/);
      page.data.routeId = 9;
      page.data.selectedRouteNo = 'R9';
      page.data.items = [{ itemNo: 'm1', vehicleId: 3, driverId: 4, amount: '0.10', businessDate: '2026-09-27' },
        { itemNo: 'm2', vehicleId: 3, driverId: 4, amount: '0.20', businessDate: '2026-09-27' }];
      page.nextStep(); page.nextStep(); page.nextStep();
      assert.equal(page.data.step, 5);
      assert.equal(page.data.summary.amount, '0.30');
      assert.equal(page.data.summary.routeNo, 'R9');
      assert.equal(page.data.summary.itemCount, 2);
    });
  }],
  ['UI4 approval list restores tab and separates error from empty', async () => {
    let fail = false;
    await pageScenario('approvals', async ({ url }) => {
      if (url === '/me') return { id: 7, permissions: ['approval:create', 'approval:process'], roleCode: 'ADMIN' };
      if (url === '/approval-templates/available') return [{ category: 'EXPENSE', allowedInputModes: ['SINGLE'] }];
      if (fail) throw new Error('网络故障');
      return { records: [], total: 0 };
    }, async (page) => {
      await page.load();
      assert.equal(page.data.tab, 'mine');
      page.switchTab({ currentTarget: { dataset: { tab: 'pending' } } });
      await Promise.resolve();
      await page.load();
      assert.equal(page.data.tab, 'pending');
      fail = true;
      await page.loadApprovals();
      assert.match(page.data.error, /网络故障/);
      assert.equal(page.data.approvals.length, 0);
    });
  }],
  ['UI4 tire submission blocks a duplicate tap and retains OCR evidence on failure', async () => {
    let release;
    let posts = 0;
    await pageScenario('tire-request', ({ url }) => {
      if (url === '/tire-requests') { posts += 1; return new Promise((resolve, reject) => { release = reject; }); }
      return Promise.resolve({});
    }, async (page) => {
      Object.assign(page.data, { tireId: 3, confirmedTireNo: 'H333', ocrRecordId: 4,
        driver: { id: 1 }, vehicleId: 2, imagePath: 'photo.jpg', step: 5 });
      const pending = page.submit();
      await page.submit();
      assert.equal(posts, 1);
      release(new Error('网络中断'));
      await pending;
      assert.equal(page.data.step, 5);
      assert.equal(page.data.ocrRecordId, 4);
      assert.match(page.data.error, /网络中断/);
    });
  }],
  ['UI4 tire confirmation uses exact request target and preserves state on occupied stock', async () => {
    let occupied = false;
    const urls = [];
    await pageScenario('tire-request', async ({ url }) => {
      urls.push(url);
      if (url.endsWith('/confirm')) return { id: 5, confirmedText: 'H621769799' };
      if (occupied) throw new Error('该轮胎已被其他申请预留');
      return { tireId: 12, tireNumber: 'H621769799' };
    }, async (page) => {
      page.data.ocrRecordId = 5;
      page.data.confirmedTireNo = 'H621769799';
      page.data.step = 3;
      await page.confirmOcr();
      assert.equal(page.data.step, 5);
      assert.equal(page.data.tireId, 12);
      assert.equal(urls[1], '/tires/request-target?tireNumber=H621769799');
      occupied = true;
      await page.confirmOcr();
      assert.equal(page.data.step, 4);
      assert.equal(page.data.tireId, '');
      assert.match(page.data.matchStatus, /预留/);
    });
  }],
  ['UI4 notifications mark only clicked item and navigate through guarded business page', async () => {
    let target = '';
    const calls = [];
    await pageScenario('notifications', async ({ url }) => {
      calls.push(url);
      if (url.endsWith('/read')) return { unreadCount: 0 };
      return {};
    }, async (page) => {
      page.data.records = [{ id: 8, businessType: 'APPROVAL', businessId: 22, readAt: null }];
      await page.open({ currentTarget: { dataset: { id: 8 } } });
      assert.equal(target, '/pages/approval-detail/index?id=22');
      assert.equal(calls[0], '/notifications/8/read');
    }, { navigateTo({ url }) { target = url; } });
  }],
  ['UI4 profile keeps password editor secondary and confirms logout', async () => {
    let cleared = false;
    let redirected = false;
    await pageScenario('profile', async () => ({ phone: '13900000990', driverType: 'INTERNAL', currentPlateNo: '云A001' }), async (page) => {
      await page.load();
      assert.equal(page.data.driverTypeLabel, '自有司机');
      assert.equal(page.data.securityOpen, false);
      page.toggleSecurity();
      assert.equal(page.data.securityOpen, true);
      page.logout();
      assert.equal(redirected, false);
    }, { showModal({ success }) { success({ confirm: false }); }, redirectTo() { redirected = true; } });
    assert.equal(cleared, false);
  }]
];
