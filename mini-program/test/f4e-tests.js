const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { visibleHomeCards } = require('../utils/permissions');

async function homeData(user) {
  const requestModule = require('../utils/request');
  const originalRequest = requestModule.request;
  const originalPage = global.Page;
  const originalGetApp = global.getApp;
  const originalWx = global.wx;
  const pagePath = require.resolve('../pages/home/index.js');
  let definition;
  requestModule.request = async () => user;
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
      currentVehicleId: 5, currentPlateNo: '云A12345', permissions: ['route:list'] });
    assert.equal(data.bindingStatus, '已绑定');
    assert.equal(data.vehicle.plateNo, '云A12345');
    const markup = fs.readFileSync(path.join(__dirname, '../pages/home/index.wxml'), 'utf8');
    assert.doesNotMatch(markup, /driver\.status|状态未知|启用/);
  }],
  ['home distinguishes unbound driver and non-driver', async () => {
    assert.equal((await homeData({ driverId: 2, driverType: 'INTERNAL',
      currentVehicleId: null, permissions: [] })).bindingStatus, '暂无绑定');
    assert.equal((await homeData({ driverId: null, permissions: [] })).bindingStatus, '不适用');
  }],
  ['home keeps internal and outsourced menus within granted capabilities', () => {
    const user = { permissions: ['route:list', 'tire:request', 'salary:mine', 'approval:create'] };
    assert.deepEqual(visibleHomeCards(user, { driverType: 'INTERNAL' }).map((card) => card.key),
      ['routes', 'profile', 'salary', 'tire', 'approvals']);
    assert.deepEqual(visibleHomeCards(user, { driverType: 'OUTSOURCED' }).map((card) => card.key),
      ['routes', 'profile']);
  }]
];
