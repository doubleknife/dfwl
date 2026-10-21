const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { validateWeights } = require('../utils/business');
const { currentTask } = require('../utils/homePresentation');

async function pageScenario(relativePath, request, run, overrides = {}) {
  const requestModule = require('../utils/request');
  const oldRequest = requestModule.request;
  const oldUpload = requestModule.uploadFile;
  const oldPage = global.Page;
  const oldWx = global.wx;
  const pagePath = require.resolve(`../pages/${relativePath}/index.js`);
  let definition;
  requestModule.request = request;
  if (overrides.uploadFile) requestModule.uploadFile = overrides.uploadFile;
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
    requestModule.uploadFile = oldUpload;
    global.Page = oldPage;
    global.wx = oldWx;
    delete require.cache[pagePath];
  }
}

function route(id, status = 'PUBLISHED') {
  return { id, status, taskNo: `T${id}`, loadingPlace: '昆明', unloadingPlace: '曲靖',
    businessDate: '2026-09-27', currentParticipant: true, version: 0, vehiclePlate: '云A12345' };
}

module.exports = [
  ['approval consent returns to the refreshed list and failure keeps the detail', async () => {
    let failApproval = false;
    await pageScenario('approvals', async ({ url, method }) => {
      if (method === 'POST' && url.endsWith('/approve')) {
        if (failApproval) throw new Error('审批处理失败');
        return {};
      }
      if (url.startsWith('/approvals?')) return { records: [{ id: 8, businessType: 'EXPENSE', status: 'PENDING' }], total: 1 };
      throw new Error(`Unexpected request: ${url}`);
    }, async (page) => {
      const detail = { approval: { id: 7, version: 0 }, canProcess: true };
      page.setData({ detail, comment: '同意' });
      failApproval = true;
      await page.act({ currentTarget: { dataset: { kind: 'APPROVE' } } });
      assert.equal(page.data.detail, detail);
      assert.equal(page.data.error, '审批处理失败');
      failApproval = false;
      await page.act({ currentTarget: { dataset: { kind: 'APPROVE' } } });
      assert.equal(page.data.detail, null);
      assert.deepEqual(page.data.rows.map((row) => row.id), [8]);
      assert.equal(page.data.pageNo, 1);
    }, { showModal({ success }) { success({ confirm: true }); } });
  }],
  ['approval history and notification timestamps omit ISO separators and milliseconds', async () => {
    await pageScenario('approvals', async () => ({
      approval: { id: 1, status: 'RETURNED_TO_APPLICANT', business_type: 'TIRE_REPLACEMENT', applicant_user_id: 1 },
      actions: [
        { id: 2, action_type: 'APPROVE', operated_at: '2026-10-21T02:20:00.000' },
        { id: 3, action_type: 'RETURN_APPLICANT', operated_at: '2026-10-21T02:22:25.544' }
      ],
      tasks: [], submissions: [], expenseItems: [], attachments: [], returnableSteps: []
    }), async (page) => {
      page.data.user = { id: 1, permissions: [] };
      await page.open({ currentTarget: { dataset: { id: 1 } } });
      assert.equal(page.data.detail.actions[0].operatedLabel, '2026-10-21 02:22:25');
      assert.equal(page.data.detail.returnAction.operatedLabel, '2026-10-21 02:22:25');
      assert.deepEqual(page.data.detail.actions.map((action) => action.id), [3, 2]);
    });
    await pageScenario('notifications', async ({ url }) => url.includes('unread-count')
      ? { unreadCount: 1 } : { total: 2, records: [{ id: 1, createdAt: '2026-10-21T03:46:59.659' }] },
    async (page) => {
      await page.load();
      assert.equal(page.data.records[0].createdLabel, '2026-10-21 03:46:59');
      await page.loadMore();
      assert.equal(page.data.records[1].createdLabel, '2026-10-21 03:46:59');
    });
  }],
  ['home and profile show the nested role returned by me', async () => {
    const previousGetApp = global.getApp;
    global.getApp = () => ({ globalData: {} });
    const user = { id: 1, phone: '13900009917', role: { id: 1, code: 'ADMIN', name: '管理员' }, permissions: [] };
    try {
      await pageScenario('home', async ({ url }) => url === '/me' ? user : { unreadCount: 0 }, async (page) => {
        await page.load();
        assert.equal(page.data.roleLabel, '管理员');
      }, { setStorageSync() {} });
      await pageScenario('profile', async () => user, async (page) => {
        await page.load();
        assert.equal(page.data.roleName, '管理员');
        assert.equal(page.data.driverTypeLabel, '管理员');
      });
    } finally { global.getApp = previousGetApp; }
  }],
  ['home shows approval work for manager and transport only for driver', async () => {
    const previousGetApp = global.getApp;
    const urls = [];
    const manager = { id: 1, name: '管理员', permissions: ['PAGE_MINI_APPROVALS', 'BUTTON_MINI_APPROVALS_APPROVE', 'PAGE_MINI_ROUTES'] };
    global.getApp = () => ({ globalData: {} });
    try {
      await pageScenario('home', async ({ url }) => {
        urls.push(url);
        if (url === '/me') return manager;
        if (url.startsWith('/approvals?')) return { total: 4, records: [{ id: 2, templateName: '费用申请', approvalNo: 'A2', currentStepName: '财务审核' }] };
        return { unreadCount: 0 };
      }, async (page) => {
        await page.load();
        assert.equal(page.data.driver, null);
        assert.equal(page.data.todoCount, 4);
        assert.equal(page.data.approvalTasks[0].templateName, '费用申请');
        assert.equal(page.data.currentTask, null);
        assert.equal(page.data.canApprove, true);
        page.openApprovals();
      }, { setStorageSync() {}, navigateTo({ url }) { urls.push(url); } });
      assert.ok(urls.includes('/approvals?scope=TODO&pageNo=1&pageSize=3'));
      assert.ok(urls.includes('/pages/approvals/index'));
      assert.equal(urls.some((url) => url.startsWith('/driver/transport-tasks')), false);

      urls.length = 0;
      await pageScenario('home', async ({ url }) => {
        urls.push(url);
        if (url === '/me') return { id: 2, driverId: 8, driverName: '司机', driverType: 'INTERNAL', permissions: ['PAGE_MINI_ROUTES'] };
        if (url.startsWith('/driver/transport-tasks')) return { records: [route(9)] };
        return { unreadCount: 0 };
      }, async (page) => {
        await page.load();
        assert.equal(page.data.currentTask.id, 9);
        assert.equal(page.data.canApprove, false);
        assert.equal(page.data.todoCount, 0);
      }, { setStorageSync() {} });
      assert.equal(urls.some((url) => url.startsWith('/approvals?')), false);
    } finally { global.getApp = previousGetApp; }
  }],
  ['home does not show a false zero when approval tasks fail', async () => {
    const previousGetApp = global.getApp;
    global.getApp = () => ({ globalData: {} });
    try {
      await pageScenario('home', async ({ url }) => {
        if (url === '/me') return { id: 1, permissions: ['PAGE_MINI_APPROVALS', 'BUTTON_MINI_APPROVALS_RETURN'] };
        if (url.startsWith('/approvals?')) throw new Error('审批服务暂不可用');
        return { unreadCount: 0 };
      }, async (page) => {
        await page.load();
        assert.equal(page.data.todoError, '审批服务暂不可用');
        assert.equal(page.data.error, '');
        assert.equal(page.data.approvalTasks.length, 0);
      }, { setStorageSync() {} });
    } finally { global.getApp = previousGetApp; }
  }],
  ['home does not query approvals without processing permission', async () => {
    const previousGetApp = global.getApp;
    const urls = [];
    global.getApp = () => ({ globalData: {} });
    try {
      await pageScenario('home', async ({ url }) => {
        urls.push(url);
        if (url === '/me') return { id: 3, permissions: ['PAGE_MINI_EXPENSE_APPLY', 'BUTTON_MINI_EXPENSE_APPLY_SUBMIT'] };
        return { unreadCount: 0 };
      }, async (page) => {
        await page.load();
        assert.equal(page.data.canApprove, false);
        assert.equal(page.data.approvalTasks.length, 0);
        page.openApprovals();
      }, { setStorageSync() {}, navigateTo({ url }) { urls.push(url); } });
      assert.equal(urls.some((url) => url.startsWith('/approvals?') || url === '/pages/approvals/index'), false);
    } finally { global.getApp = previousGetApp; }
  }],
  ['finance can open expense application and select the driver for a task expense', async () => {
    const { visibleHomeActions } = require('../utils/permissions');
    const finance = { id: 8, roleCode: 'FINANCE', permissions: ['PAGE_MINI_EXPENSE_APPLY', 'BUTTON_MINI_EXPENSE_APPLY_SUBMIT'] };
    assert.equal(visibleHomeActions(finance, null).some((item) => item.key === 'expense'), true);
    assert.equal(visibleHomeActions({ ...finance, permissions: ['PAGE_MINI_EXPENSE_APPLY'] }, null)
      .some((item) => item.key === 'expense'), false);
    const urls = [];
    await pageScenario('expense-apply', async ({ url }) => {
      urls.push(url);
      if (url === '/me') return finance;
      if (url === '/approval-templates/available') return [{ id: 5, business_type: 'EXPENSE', name: '维修费申请' }];
      if (url === '/expense-applications/options') return {
        drivers: [{ id: 10, name: '张三' }], vehicles: [{ id: 20, plateNo: '云A100' }]
      };
      if (url === '/expense-applications/task-candidates?driverId=10') return [{ taskId: 30,
        taskNo: 'T30', businessDate: '2026-10-21', templateName: '昆明至曲靖',
        loadingPlace: '昆明', unloadingPlace: '曲靖', plateNo: '云A100', vehicleId: 20 }];
      throw new Error(`Unexpected URL: ${url}`);
    }, async (page) => {
      await page.load();
      assert.equal(page.data.denied, false);
      page.onDriver({ detail: { index: 0, value: 10 } });
      assert.equal(page.data.driverId, 10);
      page.onScope({ currentTarget: { dataset: { scope: 'TASK' } } });
      await new Promise((resolve) => setImmediate(resolve));
      assert.equal(page.data.tasks[0].taskNo, 'T30');
      assert.equal(page.data.tasks[0].taskLabel, '2026-10-21 · 昆明至曲靖 · 昆明 → 曲靖 · 云A100');
      assert.equal(page.data.visibleTasks[0].taskLabel, page.data.tasks[0].taskLabel);
      page.onTemplate({ detail: { index: 0, value: 5 } });
      page.onTask({ detail: { index: 0, value: 30 } });
      assert.equal(page.data.tasks[page.data.taskIndex].taskId, 30);
      page.setData({ amount: '12.50' });
      page.review();
      assert.equal(page.data.step, 2);
    });
    assert.equal(urls.includes('/expense-applications/task-candidates?driverId=10'), true);
    const expenseView = fs.readFileSync(path.join(__dirname, '../pages/expense-apply/index.wxml'), 'utf8');
    assert.match(expenseView, /label-key="taskLabel" value-key="taskId"/);
    assert.doesNotMatch(expenseView, /tasks\[taskIndex\]\.taskNo/);
  }],
  ['expense form keeps its task draft when image selection returns to the page', async () => {
    const urls = [];
    await pageScenario('expense-apply', async ({ url }) => {
      urls.push(url);
      if (url === '/me') return { id: 8, roleCode: 'FINANCE',
        permissions: ['PAGE_MINI_EXPENSE_APPLY', 'BUTTON_MINI_EXPENSE_APPLY_SUBMIT'] };
      if (url === '/approval-templates/available') return [
        { id: 5, business_type: 'EXPENSE', expense_type: 'REPAIR', name: '维修费申请' }];
      if (url === '/expense-applications/options') return {
        drivers: [{ id: 10, name: '张三' }], vehicles: [{ id: 20, plateNo: '云A100' }]
      };
      if (url === '/expense-applications/task-candidates?driverId=10') return [{ taskId: 30,
        businessDate: '2026-10-21', templateName: '昆明至曲靖', vehicleId: 20 }];
      throw new Error(`Unexpected URL: ${url}`);
    }, async (page) => {
      await page.onShow();
      page.onDriver({ detail: { index: 0 } });
      page.onTemplate({ detail: { index: 0 } });
      page.onScope({ currentTarget: { dataset: { scope: 'TASK' } } });
      await new Promise((resolve) => setImmediate(resolve));
      page.onTask({ detail: { index: 0 } });
      page.setData({ amount: '100', description: '维修', images: [{ id: 99, path: 'photo.png' }], uploading: true });
      const requestCount = urls.length;
      await page.onShow();
      page.setData({ uploading: false });
      assert.equal(urls.length, requestCount);
      assert.equal(page.data.scope, 'TASK');
      assert.equal(page.data.templateIndex, 0);
      assert.equal(page.data.driverId, 10);
      assert.equal(page.data.taskIndex, 0);
      assert.equal(page.data.vehicleIndex, 0);
      assert.equal(page.data.amount, '100');
      assert.equal(page.data.description, '维修');
      assert.deepEqual(page.data.images, [{ id: 99, path: 'photo.png' }]);
    });
  }],
  ['expense category switch clears stale daily plate or task and only offers matching energy', async () => {
    await pageScenario('expense-apply', async () => { throw new Error('Unexpected request'); }, async (page) => {
      page.setData({
        templates: [
          { expense_type: 'REPAIR' }, { expense_type: 'ELECTRIC' },
          { expense_type: 'TEMP_ELECTRIC' }, { expense_type: 'GAS' }, { expense_type: 'TEMP_GAS' }
        ], templateIndex: 0,
        vehicles: [
          { id: 10, plateNo: '电车', energyType: 'ELECTRIC' },
          { id: 20, plateNo: '气车', energyType: 'GAS' }
        ], vehicleIndex: 0, visibleVehicles: [],
        tasks: [{ taskId: 1, vehicleId: 10 }, { taskId: 2, vehicleId: 20 }],
        taskIndex: 0, scope: 'DAILY'
      });
      page.onTemplate({ detail: { index: 3 } });
      assert.equal(page.data.vehicleIndex, -1);
      assert.deepEqual(page.data.visibleVehicles.map((row) => row.id), [20]);
      assert.equal(page.data.error, '');
      page.onVehicle({ detail: { index: 0, value: 20 } });
      assert.equal(page.data.vehicleIndex, 1);
      page.onTemplate({ detail: { index: 4 } });
      assert.equal(page.data.vehicleIndex, -1);

      page.setData({ scope: 'TASK', templateIndex: 0, taskIndex: 0, vehicleIndex: 0 });
      page.onTemplate({ detail: { index: 2 } });
      assert.equal(page.data.taskIndex, -1);
      assert.equal(page.data.vehicleIndex, -1);
      assert.deepEqual(page.data.visibleTasks.map((row) => row.taskId), [1]);
      page.onTask({ detail: { index: 0, value: 1 } });
      assert.equal(page.data.taskIndex, 0);
      assert.equal(page.data.vehicleIndex, 0);
      page.onTemplate({ detail: { index: 3 } });
      assert.deepEqual(page.data.visibleTasks.map((row) => row.taskId), [2]);
      assert.equal(page.data.taskIndex, -1);
      assert.equal(page.data.vehicleIndex, -1);
    });
  }],
  ['expense and approval submission wait for every selected image upload', async () => {
    let posts = 0;
    await pageScenario('expense-apply', async () => { posts += 1; return {}; }, async (page) => {
      page.setData({ uploading: true, busy: false });
      await page.submit();
      assert.equal(posts, 0);
      assert.equal(page.data.busy, false);
    });
    await pageScenario('approvals', async () => { posts += 1; return {}; }, async (page) => {
      page.setData({ uploading: true, busy: false, detail: { canProcess: true, approval: { id: 1, version: 0 } } });
      await page.act({ currentTarget: { dataset: { kind: 'APPROVE' } } });
      assert.equal(posts, 0);
      assert.equal(page.data.busy, false);
    });
  }],
  ['BIZ1B home ignores historical participant and presents current transfer', () => {
    assert.equal(currentTask([{ ...route(1, 'IN_TRANSIT'), currentParticipant: false }], 1), null);
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
      assert.deepEqual(urls, [1, 2, 3].map((n) => `/driver/transport-tasks?pageNo=${n}&pageSize=20&status=PUBLISHED`));
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
      assert.equal(urls[1], '/driver/transport-tasks?pageNo=1&pageSize=20&status=IN_TRANSIT');
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
      if (url === '/me') return { driverId: 1, currentVehicleId: 7, currentPlateNo: '云A12345', permissions: ['PAGE_MINI_ROUTES', 'BUTTON_MINI_ROUTES_DEPART'] };
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
  ['route detail explains missing vehicle binding and departure validation failure', async () => {
    await pageScenario('route-detail', ({ url }) => url === '/me'
      ? { driverId: 1, driverType: 'INTERNAL', currentVehicleId: null,
          permissions: ['PAGE_MINI_ROUTES', 'BUTTON_MINI_ROUTES_DEPART'] }
      : route(4), async (page) => {
      page.data.id = 4;
      await page.load();
      assert.equal(page.data.vehicleLabel, '暂未绑定');
      assert.match(page.data.error, /未绑定公司车辆.*联系调度/);
    });
    let modal;
    await pageScenario('route-detail', async ({ url }) => {
      if (url === '/me') return { driverId: 1, driverType: 'INTERNAL', currentVehicleId: 7,
        currentPlateNo: '云A12345', permissions: ['PAGE_MINI_ROUTES', 'BUTTON_MINI_ROUTES_DEPART'] };
      if (url.endsWith('/depart')) {
        const error = new Error('司机车辆校验失败');
        error.code = 'ROUTE_003';
        error.statusCode = 409;
        throw error;
      }
      return route(4);
    }, async (page) => {
      page.data.id = 4;
      await page.load();
      page.depart();
      await modal.success({ confirm: true });
      assert.match(page.data.error, /核对车辆绑定和证照/);
      assert.doesNotMatch(page.data.error, /结果未确认/);
      assert.equal(page._departKey, null);
    }, { showModal(options) { modal = options; } });
  }],
  ['UI3 detail does not depart after cancel or on wrong status or missing permission', async () => {
    let modal;
    let posts = 0;
    await pageScenario('route-detail', async ({ url }) => {
      if (url === '/me') return { driverId: 1, permissions: ['PAGE_MINI_ROUTES', 'BUTTON_MINI_ROUTES_DEPART'] };
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
  ['BIZ1D completed route opens original ticket as read only without an unload action', async () => {
    const opened = [];
    await pageScenario('route-detail', ({ url }) => {
      if (url === '/me') return { driverId: 1, permissions: ['PAGE_MINI_ROUTES'] };
      return route(4, 'COMPLETED');
    }, async (page) => {
      page.data.id = 4;
      await page.load();
      page.viewTicket();
      page.weight();
      assert.deepEqual(opened, ['/pages/weight/index?id=4']);
      assert.equal(page.data.canUnload, false);
    }, { navigateTo(options) { opened.push(options.url); } });
  }],
  ['BIZ1B only current transfer driver may depart while waiting for handoff', async () => {
    await pageScenario('route-detail', ({ url }) => {
      if (url === '/me') return { driverId: 1, permissions: ['PAGE_MINI_ROUTES', 'BUTTON_MINI_ROUTES_DEPART'] };
      return { ...route(8, 'TRANSFER_PENDING'), currentParticipant: false, currentDriverName: '接手的外协司机' };
    }, async (page) => {
      page.data.id = 8;
      await page.load();
      assert.equal(page.data.canDepart, false);
      assert.equal(page.data.route.nextAction, '等待接手司机发车');
      assert.equal(page.data.driverLabel, '接手的外协司机');
    });
    await pageScenario('route-detail', ({ url }) => {
      if (url === '/me') return { driverId: 2, permissions: ['PAGE_MINI_ROUTES', 'BUTTON_MINI_ROUTES_DEPART'] };
      return { ...route(8, 'TRANSFER_PENDING'), currentParticipant: true, vehiclePlate: '外协B002' };
    }, async (page) => {
      page.data.id = 8;
      await page.load();
      assert.equal(page.data.canDepart, true);
      assert.equal(page.data.route.nextAction, '确认发车');
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
  ['BIZ1D weight page requires a ticket before opening confirmation', async () => {
    let modalCalls = 0;
    await pageScenario('weight', ({ url }) => url === '/me'
      ? { driverId: 1, permissions: ['PAGE_MINI_ROUTES', 'BUTTON_MINI_ROUTES_UNLOAD'] } : route(9, 'IN_TRANSIT'), async (page) => {
      page.data.id = 9;
      await page.load();
      page.onGross({ detail: { value: '30' } });
      page.onTare({ detail: { value: '10' } });
      page.submit();
      assert.match(page.data.mediaError, /至少一张磅单/);
      assert.equal(modalCalls, 0);
    }, { showModal() { modalCalls += 1; } });
  }],
  ['BIZ1D partial upload keeps successful image and allows another selection', async () => {
    let selected;
    let uploadCount = 0;
    await pageScenario('weight', ({ url }) => url === '/me'
      ? { driverId: 1, permissions: ['PAGE_MINI_ROUTES', 'BUTTON_MINI_ROUTES_UNLOAD'] } : route(10, 'IN_TRANSIT'), async (page) => {
      page.data.id = 10;
      await page.load();
      page.chooseTickets();
      await selected;
      assert.equal(page.data.tickets.length, 1);
      assert.match(page.data.mediaError, /上传失败/);
      assert.equal(page.data.uploading, false);
      assert.equal(page.data.tickets[0].id, 51);
      page.removeTicket({ currentTarget: { dataset: { index: 0 } } });
      assert.equal(page.data.tickets.length, 0);
    }, {
      chooseMedia(options) { selected = options.success({ tempFiles: [{ tempFilePath: '/tmp/a.jpg' }, { tempFilePath: '/tmp/b.jpg' }] }); },
      async uploadFile() { uploadCount += 1; if (uploadCount === 2) throw new Error('offline'); return { id: 50 + uploadCount }; }
    });
  }],
  ['UI3 weight page keeps confirm lock until submit completes and sends once', async () => {
    let modal;
    let finishPost;
    let posts = 0;
    let backs = 0;
    await pageScenario('weight', ({ url }) => {
      if (url === '/me') return Promise.resolve({ driverId: 1, permissions: ['PAGE_MINI_ROUTES', 'BUTTON_MINI_ROUTES_UNLOAD'] });
      if (url.endsWith('/unload')) { posts += 1; return new Promise((resolve) => { finishPost = resolve; }); }
      return Promise.resolve(route(5, 'IN_TRANSIT'));
    }, async (page) => {
      page.data.id = 5;
      await page.load();
      page.onGross({ detail: { value: '30.000' } });
      page.onTare({ detail: { value: '10.000' } });
      page.setData({ tickets: [{ id: 12, path: '/tmp/ticket.jpg' }] });
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
      assert.equal(backs, 0);
    }, { showModal(options) { modal = options; }, navigateBack() { backs += 1; } });
  }],
  ['UI3 canceled unload never posts and weak-network failure keeps input locked for refresh', async () => {
    let modal;
    let fail = false;
    let posts = 0;
    await pageScenario('weight', async ({ url }) => {
      if (url === '/me') return { driverId: 1, permissions: ['PAGE_MINI_ROUTES', 'BUTTON_MINI_ROUTES_UNLOAD'] };
      if (url.endsWith('/unload')) { posts += 1; throw new Error('网络不可用'); }
      return route(7, 'IN_TRANSIT');
    }, async (page) => {
      page.data.id = 7;
      await page.load();
      page.onGross({ detail: { value: '25' } });
      page.onTare({ detail: { value: '10' } });
      page.setData({ tickets: [{ id: 13, path: '/tmp/ticket.jpg' }] });
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
