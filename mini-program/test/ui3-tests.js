const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { validateWeights } = require('../utils/business');
const { currentTask } = require('../utils/homePresentation');

async function pageScenario(relativePath, request, run, overrides = {}) {
  const requestModule = require('../utils/request');
  const oldRequest = requestModule.request;
  const oldPage = global.Page;
  const oldWx = global.wx;
  const pagePath = require.resolve(`../pages/${relativePath}/index.js`);
  let definition;
  requestModule.request = request;
  global.Page = (value) => { definition = value; };
  global.wx = { stopPullDownRefresh() {}, showToast() {}, navigateTo() {}, navigateBack() {}, ...overrides };
  try {
    delete require.cache[pagePath];
    require(pagePath);
    const page = { ...definition, data: structuredClone(definition.data) };
    page.setData = (updates) => Object.assign(page.data, updates);
    await run(page);
  } finally {
    requestModule.request = oldRequest;
    global.Page = oldPage;
    global.wx = oldWx;
    delete require.cache[pagePath];
  }
}

function route(id, status = 'PUBLISHED') {
  return { id, status, routeNo: `R${id}`, loadingPlace: '昆明', unloadingPlace: '曲靖',
    businessDate: '2026-09-27', loadStandardThreshold: '20.000', assignedDriverId: 1 };
}

module.exports = [
  ['BIZ1B home ignores historical participant and presents current transfer', () => {
    assert.equal(currentTask([{ ...route(1, 'IN_TRANSIT'), assignedDriverId: 2 }], 1), null);
    assert.equal(currentTask([route(2, 'TRANSFER_PENDING')], 1).nextAction, '确认发车');
  }],
  ['UI3 route list uses exact server filter and real total beyond first 50', async () => {
    const urls = [];
    await pageScenario('routes', async ({ url }) => {
      urls.push(url);
      const pageNo = Number(new URL(`http://local${url}`).searchParams.get('pageNo'));
      const count = pageNo === 3 ? 15 : 20;
      return { total: 55, records: Array.from({ length: count }, (_, index) => route((pageNo - 1) * 20 + index + 1)) };
    }, async (page) => {
      await page.refresh();
      assert.equal(page.data.records.length, 20);
      assert.equal(page.data.hasMore, true);
      await page.loadMore();
      await page.loadMore();
      assert.equal(page.data.records.length, 55);
      assert.equal(page.data.total, 55);
      assert.equal(page.data.hasMore, false);
      assert.equal(page.data.records[54].statusLabel, '等待发车');
      assert.deepEqual(urls, [1, 2, 3].map((n) => `/routes?pageNo=${n}&pageSize=20&status=PUBLISHED`));
    });
  }],
  ['UI3 tabs change server status and discard stale prior response', async () => {
    let release;
    const urls = [];
    await pageScenario('routes', ({ url }) => {
      urls.push(url);
      if (url.includes('status=PUBLISHED')) return new Promise((resolve) => { release = resolve; });
      return Promise.resolve({ total: 1, records: [route(3, 'IN_TRANSIT')] });
    }, async (page) => {
      const pending = page.refresh();
      page.onTab({ currentTarget: { dataset: { status: 'IN_TRANSIT' } } });
      await Promise.resolve();
      release({ total: 1, records: [route(1)] });
      await pending;
      await Promise.resolve();
      assert.equal(page.data.records[0].id, 3);
      assert.equal(page.data.activeStatus, 'IN_TRANSIT');
      assert.equal(urls[1], '/routes?pageNo=1&pageSize=20&status=IN_TRANSIT');
    });
  }],
  ['UI3 list distinguishes empty, network error and retry', async () => {
    let fail = false;
    await pageScenario('routes', async () => {
      if (fail) throw new Error('网络不可用');
      return { total: 0, records: [] };
    }, async (page) => {
      await page.refresh();
      assert.equal(page.data.initialized, true);
      assert.equal(page.data.error, '');
      assert.equal(page.data.records.length, 0);
      fail = true;
      await page.refresh();
      assert.match(page.data.error, /网络不可用/);
      fail = false;
      await page.refresh();
      assert.equal(page.data.error, '');
    });
  }],
  ['UI3 list reports permission denial distinctly', async () => {
    await pageScenario('routes', async () => { const error = new Error('无权限'); error.statusCode = 403; throw error; }, async (page) => {
      await page.refresh();
      assert.match(page.data.error, /没有查看线路的权限/);
    });
  }],
  ['UI3 detail confirms vehicle and prevents duplicate departure', async () => {
    let modal;
    let posts = 0;
    await pageScenario('route-detail', async ({ url, method }) => {
      if (url === '/me') return { driverId: 1, currentVehicleId: 7, currentPlateNo: '云A12345', permissions: ['route:list', 'route:depart'] };
      if (url.endsWith('/depart')) { posts += 1; return route(4, 'IN_TRANSIT'); }
      return route(4);
    }, async (page) => {
      page.data.id = 4;
      await page.load();
      page.depart();
      page.depart();
      assert.match(modal.content, /云A12345/);
      assert.match(modal.content, /昆明发往曲靖/);
      assert.equal(posts, 0);
      await modal.success({ confirm: true });
      assert.equal(posts, 1);
      assert.equal(page.data.busy, false);
      assert.equal(page.data.confirming, false);
    }, { showModal(options) { modal = options; } });
  }],
  ['UI3 detail does not depart after cancel or on wrong status or missing permission', async () => {
    let modal;
    let posts = 0;
    await pageScenario('route-detail', async ({ url }) => {
      if (url === '/me') return { driverId: 1, permissions: ['route:list', 'route:depart'] };
      if (url.endsWith('/depart')) posts += 1;
      return route(1);
    }, async (page) => {
      page.data.id = 1;
      await page.load();
      page.depart();
      await modal.success({ confirm: false });
      assert.equal(page.data.confirming, false);
      page.data.route.status = 'COMPLETED';
      page.depart();
      page.data.route.status = 'PUBLISHED';
      page.data.canDepart = false;
      page.depart();
      assert.equal(posts, 0);
    }, { showModal(options) { modal = options; } });
  }],
  ['BIZ1B only current transfer driver may depart while waiting for handoff', async () => {
    await pageScenario('route-detail', ({ url }) => {
      if (url === '/me') return { driverId: 1, permissions: ['route:list', 'route:depart'] };
      return { ...route(8, 'TRANSFER_PENDING'), assignedDriverId: 2 };
    }, async (page) => {
      page.data.id = 8;
      await page.load();
      assert.equal(page.data.canDepart, false);
    });
    await pageScenario('route-detail', ({ url }) => {
      if (url === '/me') return { driverId: 2, permissions: ['route:list', 'route:depart'] };
      return { ...route(8, 'TRANSFER_PENDING'), assignedDriverId: 2, currentOutsourcedPlateNo: '外协B002' };
    }, async (page) => {
      page.data.id = 8;
      await page.load();
      assert.equal(page.data.canDepart, true);
      assert.equal(page.data.vehicleLabel, '外协B002');
    });
  }],
  ['UI3 weights enforce positive order and three decimal places', () => {
    assert.equal(validateWeights('30.1234', '10').ok, false);
    assert.equal(validateWeights('30', '10.1234').ok, false);
    assert.equal(validateWeights('-1', '1').ok, false);
    assert.equal(validateWeights('10', '10').ok, false);
    assert.equal(validateWeights('30.123', '10.100').ok, true);
  }],
  ['UI3 weight page keeps confirm lock until submit completes and sends once', async () => {
    let modal;
    let finishPost;
    let posts = 0;
    let backs = 0;
    await pageScenario('weight', ({ url }) => {
      if (url === '/me') return Promise.resolve({ permissions: ['route:unload'] });
      if (url.endsWith('/unload')) { posts += 1; return new Promise((resolve) => { finishPost = resolve; }); }
      return Promise.resolve(route(5, 'IN_TRANSIT'));
    }, async (page) => {
      page.data.id = 5;
      await page.load();
      page.onGross({ detail: { value: '30.000' } });
      page.onTare({ detail: { value: '10.000' } });
      assert.equal(page.data.netWeight, '20.000');
      page.submit();
      page.submit();
      assert.match(modal.content, /实际载重 20.000 吨/);
      const pending = modal.success({ confirm: true });
      page.submit();
      assert.equal(page.data.busy, true);
      assert.equal(posts, 1);
      finishPost(route(5, 'COMPLETED'));
      await pending;
      assert.equal(page.data.busy, false);
      assert.equal(page.data.route.status, 'COMPLETED');
      assert.equal(backs, 1);
    }, { showModal(options) { modal = options; }, navigateBack() { backs += 1; } });
  }],
  ['UI3 canceled unload never posts and weak-network failure keeps input locked for refresh', async () => {
    let modal;
    let fail = false;
    let posts = 0;
    await pageScenario('weight', async ({ url }) => {
      if (url === '/me') return { permissions: ['route:unload'] };
      if (url.endsWith('/unload')) { posts += 1; throw new Error('网络不可用'); }
      return route(7, 'IN_TRANSIT');
    }, async (page) => {
      page.data.id = 7;
      await page.load();
      page.onGross({ detail: { value: '25' } });
      page.onTare({ detail: { value: '10' } });
      page.submit();
      await modal.success({ confirm: false });
      assert.equal(posts, 0);
      page.submit();
      await modal.success({ confirm: true });
      assert.equal(posts, 1);
      assert.equal(page.data.resultUnknown, true);
      assert.equal(page.data.grossWeight, '25');
      page.submit();
      assert.equal(posts, 1);
      await page.load();
      assert.equal(page.data.resultUnknown, false);
      assert.equal(page.data.grossWeight, '25');
    }, { showModal(options) { modal = options; } });
  }],
  ['UI3 driver pages retain scope-safe fields, large actions and Chinese statuses', () => {
    const base = path.join(__dirname, '../pages');
    const list = fs.readFileSync(path.join(base, 'routes/index.wxml'), 'utf8');
    const detail = fs.readFileSync(path.join(base, 'route-detail/index.wxml'), 'utf8');
    const weight = fs.readFileSync(path.join(base, 'weight/index.wxml'), 'utf8');
    assert.doesNotMatch(list + detail + weight, /taxUnitPrice|incomeAmount|profitAmount|carryingFee/);
    assert.match(list, /待发车|运输中|已完成/);
    assert.match(weight, /毛重（吨）|皮重（吨）|实际载重/);
    assert.match(fs.readFileSync(path.join(base, 'weight/index.wxss'), 'utf8'), /safe-area-inset-bottom/);
    assert.match(fs.readFileSync(path.join(base, 'routes/index.wxss'), 'utf8'), /font-size: 35rpx/);
  }]
];
