const assert = require('node:assert/strict');
const { request, uploadFile } = require('../utils/request');

async function sessionScenario(kind, stale) {
  const previousWx = global.wx;
  const previousGetApp = global.getApp;
  const calls = { clear: 0, redirect: 0 };
  const app = {
    globalData: { baseUrl: 'http://localhost:8080/api/v1', token: 'old-token' },
    clearSession() { calls.clear += 1; this.globalData.token = ''; }
  };
  let respond;
  global.getApp = () => app;
  global.wx = {
    getStorageSync: () => app.globalData.token,
    redirectTo: () => { calls.redirect += 1; },
    showToast: () => {},
    request: (options) => { respond = options.success; },
    uploadFile: (options) => { respond = options.success; return {}; }
  };
  try {
    const pending = kind === 'upload'
      ? uploadFile({ url: '/attachments', filePath: 'photo.jpg' })
      : request({ url: '/me' });
    if (stale) app.globalData.token = 'new-token';
    respond({ statusCode: 401, data: kind === 'upload' ? '{}' : {} });
    await assert.rejects(pending, (error) => error.statusCode === 401);
    assert.equal(calls.clear, stale ? 0 : 1);
    assert.equal(calls.redirect, stale ? 0 : 1);
    assert.equal(app.globalData.token, stale ? 'new-token' : '');
  } finally {
    global.wx = previousWx;
    global.getApp = previousGetApp;
  }
}

async function failedLoginScenario() {
  const previousWx = global.wx;
  const previousGetApp = global.getApp;
  const calls = { clear: 0, redirect: 0 };
  let respond;
  const app = {
    globalData: { baseUrl: 'http://localhost:8080/api/v1', token: '' },
    clearSession() { calls.clear += 1; }
  };
  global.getApp = () => app;
  global.wx = {
    getStorageSync: () => '',
    redirectTo: () => { calls.redirect += 1; },
    request: (options) => { respond = options.success; }
  };
  try {
    const pending = request({ url: '/auth/login', method: 'POST' });
    respond({ statusCode: 401, data: {} });
    await assert.rejects(pending, /手机号或密码不正确/);
    assert.deepEqual(calls, { clear: 0, redirect: 0 });
  } finally {
    global.wx = previousWx;
    global.getApp = previousGetApp;
  }
}

async function loginPageErrorScenario() {
  const requestModule = require('../utils/request');
  const previousRequest = requestModule.request;
  const previousPage = global.Page;
  const pagePath = require.resolve('../pages/login/index.js');
  let definition;
  requestModule.request = async () => { throw new Error('手机号或密码不正确'); };
  global.Page = (value) => { definition = value; };
  try {
    delete require.cache[pagePath];
    require(pagePath);
    const page = { ...definition, data: { ...definition.data, phone: '13900000990', password: 'incorrect' } };
    page.setData = (changes) => Object.assign(page.data, changes);
    await page.login();
    assert.equal(page.data.error, '手机号或密码不正确');
    assert.equal(page.data.loading, false);
  } finally {
    requestModule.request = previousRequest;
    global.Page = previousPage;
    delete require.cache[pagePath];
  }
}

module.exports = [
  ['stale 401 cannot clear a newly logged-in session', () => sessionScenario('request', true)],
  ['current session 401 still requires login', () => sessionScenario('request', false)],
  ['stale upload 401 cannot clear a newly logged-in session', () => sessionScenario('upload', true)],
  ['current session upload 401 still requires login', () => sessionScenario('upload', false)],
  ['login 401 stays on the login page and explains the failure', failedLoginScenario],
  ['login page displays authentication failure and resets loading', loginPageErrorScenario]
];
