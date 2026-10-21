const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { visibleHomeActions } = require('../utils/permissions');
const { currentTask, routePresentation } = require('../utils/homePresentation');

async function homeData(user) {
  const requestModule = require('../utils/request');
  const originalRequest = requestModule.request;
  const originalPage = global.Page;
  const originalGetApp = global.getApp;
  const originalWx = global.wx;
  const pagePath = require.resolve('../pages/home/index.js');
  let definition;
  requestModule.request = async ({ url }) => url === '/me' ? user : url.startsWith('/driver/transport-tasks?') ? { records: [] } : [];
  global.Page = (page) => { definition = page; };
  global.getApp = () => ({ globalData: {} });
  global.wx = { setStorageSync() {} };
  try {
    delete require.cache[pagePath];
    require(pagePath);
    const page = { ...definition, data: structuredClone(definition.data) };
    page.setData = (updates) => Object.assign(page.data, updates);
    await page.load();
    return page.data;
  } finally {
    requestModule.request = originalRequest;
    global.Page = originalPage;
    global.getApp = originalGetApp;
    global.wx = originalWx;
    delete require.cache[pagePath];
  }
}

module.exports = [
  ['home shows actual current binding without guessing vehicle status', async () => {
    const data = await homeData({ driverId: 2, driverName: '司机甲', driverType: 'INTERNAL',
      currentVehicleId: 5, currentPlateNo: '云A12345', permissions: ['PAGE_MINI_ROUTES'] });
    assert.equal(data.vehicle.plateNo, '云A12345');
    assert.equal(data.displayName, '司机甲');
    const markup = fs.readFileSync(path.join(__dirname, '../pages/home/index.wxml'), 'utf8');
    assert.doesNotMatch(markup, /driver\.status|状态未知|启用/);
  }],
  ['home distinguishes unbound driver and non-driver', async () => {
    assert.equal((await homeData({ driverId: 2, driverType: 'INTERNAL',
      currentVehicleId: null, permissions: [] })).vehicle, null);
    assert.equal((await homeData({ driverId: null, permissions: [] })).driver, null);
  }],
  ['home exposes driver expense and approval actions according to permission and driver type', () => {
    const user = { permissions: ['PAGE_MINI_TIRE_REQUEST', 'PAGE_MINI_EXPENSE_APPLY', 'PAGE_MINI_ROUTES', 'BUTTON_MINI_TIRE_REQUEST_SUBMIT', 'PAGE_MINI_SALARY', 'BUTTON_MINI_EXPENSE_APPLY_SUBMIT'] };
    assert.deepEqual(visibleHomeActions(user, { driverType: 'INTERNAL' }, [{}]).map((card) => card.key),
      ['routes', 'expense', 'approval', 'salary']);
    assert.deepEqual(visibleHomeActions(user, { driverType: 'OUTSOURCED' }, [{}]).map((card) => card.key),
      ['routes']);
  }],
  ['tire request requires internal driver, current company vehicle and permissions', () => {
    const user = { permissions: ['PAGE_MINI_EXPENSE_APPLY', 'PAGE_MINI_TIRE_REQUEST', 'PAGE_MINI_ROUTES', 'BUTTON_MINI_EXPENSE_APPLY_SUBMIT', 'PAGE_MINI_APPROVALS', 'PAGE_MINI_SALARY', 'BUTTON_MINI_TIRE_REQUEST_SUBMIT'] };
    assert.deepEqual(visibleHomeActions(user, { driverType: 'INTERNAL' }, [{}]).map((item) => item.key),
      ['routes', 'expense', 'approval', 'salary']);
    assert.deepEqual(visibleHomeActions(user, { driverType: 'INTERNAL', currentVehicleId: 7 }).map((item) => item.key),
      ['routes', 'expense', 'approval', 'salary', 'tire']);
    assert.equal(visibleHomeActions({ permissions: ['PAGE_MINI_TIRE_REQUEST', 'BUTTON_MINI_TIRE_REQUEST_SUBMIT'] },
      { driverType: 'INTERNAL', currentVehicleId: 7 }).some((item) => item.key === 'tire'), true);
    assert.equal(visibleHomeActions(user, { driverType: 'INTERNAL' }, []).some((item) => item.key === 'launchApproval'), false);
    assert.deepEqual(visibleHomeActions(user, { driverType: 'OUTSOURCED' }, [{}]).map((item) => item.key), ['routes']);
  }],
  ['UI0.1 notification visuals distinguish zero and count without claiming a live backend', () => {
    const summary = fs.readFileSync(path.join(__dirname, '../components/user-summary/index.wxml'), 'utf8');
    const home = fs.readFileSync(path.join(__dirname, '../pages/home/index.wxml'), 'utf8');
    const grid = fs.readFileSync(path.join(__dirname, '../components/function-grid/index.wxml'), 'utf8');
    assert.match(summary, /wx:if="\{\{unreadCount > 0\}\}"/);
    assert.match(summary, /unreadCount > 99 \? '99\+' : unreadCount/);
    assert.match(home, /unread-count="\{\{unreadCount\}\}"/);
    assert.doesNotMatch(home, /mp-vehicle-line/);
    assert.match(grid, /wx:if="\{\{item.badge > 0\}\}"/);
  }],
  ['current task prioritizes in-transit and presents Chinese next action', () => {
    const task = currentTask([{ id: 1, status: 'PUBLISHED' }, { id: 2, status: 'IN_TRANSIT' }]);
    assert.equal(task.id, 2);
    assert.equal(task.nextAction, '录入重量并卸货');
    assert.equal(routePresentation({ status: 'COMPLETED' }).statusLabel, '已完成');
  }],
  ['route detail refreshes on show and prevents duplicate depart', () => {
    const source = fs.readFileSync(path.join(__dirname, '../pages/route-detail/index.js'), 'utf8');
    assert.match(source, /onShow\(\).*this\.load\(\)/);
    assert.match(source, /this\.data\.busy \|\| this\.data\.confirming/);
    assert.match(source, /hasPermission\(user, 'BUTTON_MINI_ROUTES_DEPART'\)/);
    assert.match(source, /route\.loadingPlace/);
    assert.match(source, /route\.unloadingPlace/);
  }]
];
