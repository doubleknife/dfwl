const assert = require('node:assert/strict');
const { request, uploadFile } = require('../utils/request');

async function sessionScenario(kind, stale, url = '/me') {
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
      : request({ url });
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

async function profileLogoutScenario(shouldFail) {
  const requestModule = require('../utils/request');
  const originalRequest = requestModule.request;
  const originalPage = global.Page;
  const originalWx = global.wx;
  const originalGetApp = global.getApp;
  const pagePath = require.resolve('../pages/profile/index.js');
  let definition;
  const calls = { revoke: 0, clear: 0, redirect: 0, warning: 0 };
  requestModule.request = async (options) => {
    assert.deepEqual(options, { url: '/auth/logout', method: 'POST' });
    calls.revoke += 1;
    if (shouldFail) throw new Error('network');
  };
  global.Page = (value) => { definition = value; };
  global.getApp = () => ({ clearSession() { calls.clear += 1; } });
  global.wx = {
    showModal(options) {
      if (options.title === '退出登录') options.success({ confirm: true });
      else { calls.warning += 1; options.complete(); }
    },
    redirectTo() { calls.redirect += 1; }
  };
  try {
    delete require.cache[pagePath];
    require(pagePath);
    definition.logout();
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(calls, { revoke: 1, clear: 1, redirect: 1, warning: shouldFail ? 1 : 0 });
  } finally {
    requestModule.request = originalRequest;
    global.Page = originalPage;
    global.wx = originalWx;
    global.getApp = originalGetApp;
    delete require.cache[pagePath];
  }
}

module.exports = [
  ['stale 401 cannot clear a newly logged-in session', () => sessionScenario('request', true)],
  ['current session 401 still requires login', () => sessionScenario('request', false)],
  ['expired password session requires login', () => sessionScenario('request', false, '/auth/password')],
  ['stale upload 401 cannot clear a newly logged-in session', () => sessionScenario('upload', true)],
  ['current session upload 401 still requires login', () => sessionScenario('upload', false)],
  ['login 401 stays on the login page and explains the failure', failedLoginScenario],
  ['login page displays authentication failure and resets loading', loginPageErrorScenario],
  ['mini logout revokes the server session', () => profileLogoutScenario(false)],
  ['mini logout clears local credentials and reports failed revocation', () => profileLogoutScenario(true)]
];
