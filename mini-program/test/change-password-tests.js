const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const pagePath = require.resolve('../pages/change-password/index.js');
const profilePath = require.resolve('../pages/profile/index.js');

async function withPage(which, respond, run) {
  const requestModule = require('../utils/request');
  const previousRequest = requestModule.request;
  const previousPage = global.Page;
  const previousWx = global.wx;
  const previousGetApp = global.getApp;
  const calls = { requests: [], clear: 0, redirect: [], navigate: [], modal: [] };
  const app = { globalData: { token: 'session-token' }, clearSession() { calls.clear += 1; this.globalData.token = ''; } };
  let definition;
  requestModule.request = async (options) => { calls.requests.push(options); return respond(options); };
  global.Page = (value) => { definition = value; };
  global.getApp = () => app;
  global.wx = {
    getStorageSync: () => '',
    redirectTo: ({ url }) => { calls.redirect.push(url); },
    navigateTo: ({ url }) => { calls.navigate.push(url); },
    showModal: (options) => { calls.modal.push(options); if (options.complete) options.complete(); }
  };
  try {
    const target = which === 'profile' ? profilePath : pagePath;
    delete require.cache[target];
    require(target);
    const page = { ...definition, data: { ...definition.data } };
    page.setData = (changes) => Object.assign(page.data, changes);
    await run(page, calls, app);
  } finally {
    requestModule.request = previousRequest;
    global.Page = previousPage;
    global.wx = previousWx;
    global.getApp = previousGetApp;
    delete require.cache[which === 'profile' ? profilePath : pagePath];
  }
}

function enter(page, oldPassword, newPassword, confirmPassword) {
  page.onOld({ detail: { value: oldPassword } });
  page.onNew({ detail: { value: newPassword } });
  page.onConfirm({ detail: { value: confirmPassword } });
}

module.exports = [
  ['profile retains facts and logout but only links to the password page', async () => {
    const wxml = fs.readFileSync(path.join(__dirname, '../pages/profile/index.wxml'), 'utf8');
    assert.match(wxml, /当前车辆/);
    assert.match(wxml, /角色/);
    assert.match(wxml, /退出登录/);
    assert.match(wxml, /bindtap="openChangePassword"/);
    assert.doesNotMatch(wxml, /oldPassword|newPassword|securityOpen/);
    await withPage('profile', async () => {}, async (page, calls) => {
      page.openChangePassword();
      assert.deepEqual(calls.navigate, ['/pages/change-password/index']);
    });
  }],
  ['password page fields are ordered and masked by default with visibility toggles', async () => {
    const wxml = fs.readFileSync(path.join(__dirname, '../pages/change-password/index.wxml'), 'utf8');
    assert.ok(wxml.indexOf('原密码') < wxml.indexOf('新密码'));
    assert.ok(wxml.indexOf('新密码') < wxml.indexOf('确认新密码'));
    for (const name of ['Old', 'New', 'Confirm']) {
      assert.match(wxml, new RegExp(`password="{{!show${name}Password}}"`));
    }
    await withPage('password', async () => {}, async (page) => {
      assert.equal(page.data.showOldPassword, false);
      assert.equal(page.data.showNewPassword, false);
      assert.equal(page.data.showConfirmPassword, false);
      page.toggleOldVisibility(); page.toggleNewVisibility(); page.toggleConfirmVisibility();
      assert.equal(page.data.showOldPassword, true);
      assert.equal(page.data.showNewPassword, true);
      assert.equal(page.data.showConfirmPassword, true);
    });
  }],
  ['password validation rejects missing, short and mismatched inputs before API call', async () => {
    await withPage('password', async () => {}, async (page, calls) => {
      await page.changePassword();
      assert.equal(page.data.oldError, '请输入原密码');
      assert.equal(page.data.newError, '请输入新密码');
      assert.equal(page.data.confirmError, '请确认新密码');
      enter(page, 'current', '12345', '12345');
      await page.changePassword();
      assert.equal(page.data.newError, '新密码至少 6 位');
      enter(page, 'current', '123456', '654321');
      await page.changePassword();
      assert.equal(page.data.confirmError, '两次输入的新密码不一致');
      assert.deepEqual(calls.requests, []);
    });
  }],
  ['wrong old password is exact and other API failures keep the current session', async () => {
    for (const failure of [Object.assign(new Error('其它业务错误'), { code: 'AUTH_005' }), new Error('网络不可用')]) {
      await withPage('password', async () => { throw failure; }, async (page, calls, app) => {
        enter(page, 'wrong-old', '123456', '123456');
        await page.changePassword();
        assert.equal(page.data.apiError, failure.code === 'AUTH_005' ? '原密码错误' : '网络不可用');
        assert.equal(page.data.loading, false);
        assert.equal(app.globalData.token, 'session-token');
        assert.equal(calls.clear, 0);
        assert.deepEqual(calls.redirect, []);
        assert.equal(calls.requests.length, 1);
      });
    }
  }],
  ['successful password change uses the formal API, clears local session and requires login', async () => {
    await withPage('password', async () => {}, async (page, calls) => {
      enter(page, 'current', '123456', '123456');
      await page.changePassword();
      assert.deepEqual(calls.requests, [{ url: '/auth/password', method: 'PUT', data: { oldPassword: 'current', newPassword: '123456' } }]);
      assert.equal(calls.clear, 1);
      assert.equal(calls.modal[0].title, '修改成功');
      assert.equal(calls.modal[0].showCancel, false);
      assert.deepEqual(calls.redirect, ['/pages/login/index']);
      assert.equal(page.data.oldPassword, '');
      assert.equal(page.data.newPassword, '');
      assert.equal(page.data.confirmPassword, '');
    });
  }],
  ['password page rejects missing session and clears sensitive fields when leaving', async () => {
    await withPage('password', async () => {}, async (page, calls, app) => {
      app.globalData.token = '';
      page.onLoad();
      assert.deepEqual(calls.redirect, ['/pages/login/index']);
      enter(page, 'old', '123456', '123456');
      page.onUnload();
      assert.equal(page.data.oldPassword, '');
      assert.equal(page.data.newPassword, '');
      assert.equal(page.data.confirmPassword, '');
    });
  }],
  ['API error messages shown to users omit request identifiers', async () => {
    const { request } = require('../utils/request');
    const previousWx = global.wx;
    const previousGetApp = global.getApp;
    const toast = [];
    global.getApp = () => ({ globalData: { baseUrl: 'http://localhost:8080/api/v1', token: 'session-token' } });
    global.wx = {
      getStorageSync: () => '',
      showToast: ({ title }) => toast.push(title),
      request: ({ success }) => success({ statusCode: 409, data: { code: 'AUTH_005', message: '原密码错误', requestId: 'trace-only' } })
    };
    try {
      await assert.rejects(request({ url: '/auth/password', method: 'PUT' }), (error) => {
        assert.equal(error.message, '原密码错误');
        assert.equal(error.code, 'AUTH_005');
        return true;
      });
      assert.deepEqual(toast, ['原密码错误']);
    } finally {
      global.wx = previousWx;
      global.getApp = previousGetApp;
    }
  }]
];
