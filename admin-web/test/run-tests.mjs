import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { hasAnyPermission, hasPermission } from '../src/utils/permissions.js';
import { unwrapApiPayload } from '../src/api/response.js';
import {
  attributionTypes, canConfigureNext, canMaintain, canPublish, emptyForm,
  evidenceRequirements, expenseTypes, formFromConfiguration, formPayload, inputModes,
  templateCategories, templateSummary
} from '../src/views/approvalTemplateModel.js';

const requests = [];
globalThis.localStorage = {
  data: new Map(),
  getItem(key) {
    return this.data.get(key) || null;
  },
  setItem(key, value) {
    this.data.set(key, value);
  },
  removeItem(key) {
    this.data.delete(key);
  }
};
globalThis.fetch = async (url, options = {}) => {
  requests.push({ url: String(url), options });
  const data = String(url).endsWith('/auth/login')
    ? {
        tokenType: 'Bearer',
        accessToken: 'real-access-token',
        user: { id: 1, role: { code: 'ADMIN' }, permissions: ['user:manage'] }
      }
    : { ok: true, records: [] };
  return {
    ok: true,
    status: 200,
    headers: { get: () => 'application/json' },
    async json() {
      return { code: '0', message: 'OK', data };
    }
  };
};

const { masterApi, approvalTemplateApi, systemApi, tireApi } = await import('../src/api/modules.js');
const { authStore, clearSession } = await import('../src/stores/auth.js');

function configuredForm(category = 'EXPENSE') {
  const form = emptyForm(category);
  form.templateCode = category === 'TIRE' ? 'TIRE_REQUEST' : `${category}_TEST`;
  form.flowName = `${category} test flow`;
  form.initiatorRoleIds = [1, 2];
  form.nodes = [{ nodeOrder: 1, nodeName: '管理员审批', positionId: '', approverUserId: 3, allowReturn: true }];
  form.expenseType = 'WATER';
  form.inputModes = ['SINGLE', 'EXCEL'];
  form.attributionTypes = ['ROUTE', 'DAILY'];
  return form;
}

const tests = [
  ['permission helpers allow matching single permission', () => {
    assert.equal(hasPermission(['route:list'], 'route:list'), true);
    assert.equal(hasPermission(['route:list'], 'route:delete'), false);
  }],
  ['permission helpers allow any configured permission', () => {
    assert.equal(hasAnyPermission(['salary:view'], ['salary:manage', 'salary:view']), true);
    assert.equal(hasAnyPermission(['salary:view'], ['route:list', 'expense:list']), false);
  }],
  ['unwrapApiPayload returns data on backend success code', () => {
    assert.deepEqual(unwrapApiPayload({ code: '0', data: { ok: true } }), { ok: true });
  }],
  ['unwrapApiPayload throws backend message and code', () => {
    assert.throws(() => unwrapApiPayload({ code: 'AUTH_003', message: '无权限' }), (error) => {
      assert.equal(error.message, '无权限');
      assert.equal(error.code, 'AUTH_003');
      return true;
    });
  }],
  ['login stores accessToken and sends it as Bearer until logout', async () => {
    clearSession();
    requests.length = 0;
    await authStore.login({ phone: '13900000001', password: 'secret' });
    assert.equal(authStore.token, 'real-access-token');
    assert.equal(localStorage.getItem('admin.token'), 'real-access-token');
    assert.equal(JSON.parse(localStorage.getItem('admin.user')).role.code, 'ADMIN');

    await masterApi.customers();
    assert.equal(requests[1].options.headers.Authorization, 'Bearer real-access-token');

    clearSession();
    assert.equal(authStore.token, '');
    assert.equal(localStorage.getItem('admin.token'), null);
    assert.equal(localStorage.getItem('admin.user'), null);
  }],
  ['master API covers customer and product endpoints', async () => {
    requests.length = 0;
    await masterApi.customers();
    await masterApi.createCustomer({ customerName: 'A', status: 1 });
    await masterApi.updateProduct(7, { productName: 'P', status: 1 });
    assert.match(requests[0].url, /\/customers\?pageNo=1&pageSize=100$/);
    assert.match(requests[1].url, /\/customers$/);
    assert.equal(requests[1].options.method, 'POST');
    assert.match(requests[2].url, /\/products\/7$/);
    assert.equal(requests[2].options.method, 'PUT');
  }],
  ['approval template API keeps templateId separate from flowId', async () => {
    requests.length = 0;
    await approvalTemplateApi.list();
    await approvalTemplateApi.detail(8);
    await approvalTemplateApi.versions(8);
    await approvalTemplateApi.active(8);
    await approvalTemplateApi.version(8, 12);
    assert.match(requests[0].url, /\/approval-templates$/);
    assert.match(requests[1].url, /\/approval-templates\/8$/);
    assert.match(requests[2].url, /\/approval-templates\/8\/versions$/);
    assert.match(requests[3].url, /\/approval-templates\/8\/active$/);
    assert.match(requests[4].url, /\/approval-templates\/8\/versions\/12$/);
    assert.equal(requests.every((request) => !request.url.includes('/types/')), true);
  }],
  ['approval template mutations use formal create, maintenance, update and publish paths', async () => {
    requests.length = 0;
    const body = formPayload(configuredForm(), { creating: true });
    await approvalTemplateApi.create(body);
    await approvalTemplateApi.maintenance(12);
    await approvalTemplateApi.createNextVersion(12, formPayload(configuredForm()));
    await approvalTemplateApi.publish(13);
    assert.match(requests[0].url, /\/approval-templates$/);
    assert.equal(requests[0].options.method, 'POST');
    assert.equal(JSON.parse(requests[0].options.body).category, 'EXPENSE');
    assert.match(requests[1].url, /\/approval-flows\/12\/maintenance$/);
    assert.equal(requests[1].options.method, 'POST');
    assert.match(requests[2].url, /\/approval-flows\/12$/);
    assert.equal(requests[2].options.method, 'PUT');
    assert.match(requests[3].url, /\/approval-flows\/13\/publish$/);
    assert.equal(requests[3].options.method, 'POST');
  }],
  ['publish errors preserve backend message and requestId for the page', async () => {
    const previousFetch = globalThis.fetch;
    globalThis.fetch = async () => ({
      ok: false,
      status: 409,
      headers: { get: () => 'application/json' },
      async json() {
        return { code: 'APPROVAL_002', message: '版本不能发布', requestId: 'f4a-request' };
      }
    });
    try {
      await assert.rejects(approvalTemplateApi.publish(12), (error) => {
        assert.equal(error.message, '版本不能发布');
        assert.equal(error.requestId, 'f4a-request');
        return true;
      });
    } finally {
      globalThis.fetch = previousFetch;
    }
  }],
  ['template options match formal OpenAPI enums', () => {
    assert.deepEqual(templateCategories, ['TIRE', 'EXPENSE', 'OTHER']);
    assert.deepEqual(evidenceRequirements, ['NONE', 'OPTIONAL', 'REQUIRED']);
    assert.deepEqual(inputModes, ['SINGLE', 'EXCEL']);
    assert.deepEqual(attributionTypes, ['ROUTE', 'DAILY']);
    assert.equal(expenseTypes.includes('WATER'), true);
    assert.equal(expenseTypes.includes('DRIVER_SALARY'), true);
  }],
  ['EXPENSE V1 submits both modes and attribution choices with multiple roles', () => {
    const body = formPayload(configuredForm(), { creating: true });
    assert.equal(body.templateCode, 'EXPENSE_TEST');
    assert.deepEqual(body.initiatorRoleIds, [1, 2]);
    assert.deepEqual(body.nodes, [{ nodeOrder: 1, nodeName: '管理员审批', positionId: null,
      approverUserId: 3, allowReturn: true }]);
    assert.deepEqual(body.expenseConfig, { expenseType: 'WATER', inputModes: ['SINGLE', 'EXCEL'],
      attributionTypes: ['ROUTE', 'DAILY'] });
  }],
  ['OTHER and TIRE never submit EXPENSE configuration', () => {
    for (const category of ['OTHER', 'TIRE']) {
      const body = formPayload(configuredForm(category), { creating: true });
      assert.equal(body.category, category);
      assert.equal('expenseConfig' in body, false);
    }
  }],
  ['invalid EXPENSE combinations and missing role or node are rejected before request', () => {
    for (const field of ['inputModes', 'attributionTypes']) {
      const form = configuredForm();
      form[field] = [];
      assert.throws(() => formPayload(form, { creating: true }), /请选择有效/);
    }
    const noRole = configuredForm();
    noRole.initiatorRoleIds = [];
    assert.throws(() => formPayload(noRole, { creating: true }), /允许发起角色/);
    const noNode = configuredForm();
    noNode.nodes = [];
    assert.throws(() => formPayload(noNode, { creating: true }), /审批节点/);
  }],
  ['version models keep V1 and V2 settings independent', () => {
    const v1 = { flow: { id: 11, versionNo: 1, status: 'MAINTENANCE', flowName: 'V1' },
      evidenceRequirement: 'REQUIRED', initiatorRoleIds: [1],
      nodes: [{ nodeOrder: 1, nodeName: '旧节点', approverUserId: 3, allowReturn: true }],
      expenseConfig: { expenseType: 'WATER', inputModes: ['SINGLE'], attributionTypes: ['ROUTE'] } };
    const v2 = { ...v1, flow: { id: 12, versionNo: 2, status: 'ACTIVE', flowName: 'V2' },
      evidenceRequirement: 'OPTIONAL', initiatorRoleIds: [2],
      nodes: [{ nodeOrder: 1, nodeName: '新节点', approverUserId: 4, allowReturn: false }],
      expenseConfig: { expenseType: 'WATER', inputModes: ['EXCEL'], attributionTypes: ['DAILY'] } };
    const oldForm = formFromConfiguration(v1, 'EXPENSE');
    const newForm = formFromConfiguration(v2, 'EXPENSE');
    assert.equal(oldForm.nodes[0].nodeName, '旧节点');
    assert.deepEqual(newForm.attributionTypes, ['DAILY']);
    oldForm.nodes[0].nodeName = '本地草稿';
    assert.equal(v1.nodes[0].nodeName, '旧节点');
    assert.deepEqual(v1.initiatorRoleIds, [1]);
    assert.equal(canMaintain(v2.flow, [v1.flow, v2.flow]), true);
    assert.equal(canConfigureNext(v1.flow, [v1.flow, v2.flow]), false);
    assert.equal(canPublish(v1.flow), true);
    assert.equal(canPublish(v2.flow), false);
  }],
  ['stable template list distinguishes two active EXPENSE flows', () => {
    const one = templateSummary({ id: 7, templateCode: 'EXPENSE_WATER', category: 'EXPENSE', activeFlowId: 70 },
      [{ id: 70, versionNo: 2, status: 'ACTIVE', flowName: '水费' }]);
    const two = templateSummary({ id: 8, templateCode: 'EXPENSE_TOLL', category: 'EXPENSE', activeFlowId: 80 },
      [{ id: 80, versionNo: 1, status: 'ACTIVE', flowName: '过路费' }]);
    assert.equal(one.activeVersion, 'V2');
    assert.equal(two.activeVersion, 'V1');
    assert.notEqual(one.id, two.id);
    assert.notEqual(one.activeFlowId, two.activeFlowId);
  }],
  ['approval template route uses actual manage permission', () => {
    const routeSource = readFileSync(new URL('../src/router/routes.js', import.meta.url), 'utf8');
    assert.match(routeSource, /key: 'approvalFlows', label: '审批模板', permission: 'approval:flow:manage'/);
    assert.equal(hasAnyPermission([], 'approval:flow:manage'), false);
    assert.equal(hasAnyPermission(['approval:flow:manage'], 'approval:flow:manage'), true);
  }],
  ['role options come from system API, not a hardcoded role list', async () => {
    requests.length = 0;
    await systemApi.roles();
    await systemApi.users();
    assert.match(requests[0].url, /\/roles\?pageNo=1&pageSize=100$/);
    assert.match(requests[1].url, /\/users\?pageNo=1&pageSize=100$/);
    const formSource = readFileSync(new URL('../src/components/ApprovalTemplateForm.vue', import.meta.url), 'utf8');
    assert.match(formSource, /v-for="role in roles"/);
    assert.doesNotMatch(formSource, /const roles\s*=\s*\[/);
  }],
  ['system API covers users, roles, permissions and audit logs', async () => {
    requests.length = 0;
    await systemApi.users();
    await systemApi.createUser({ phone: '13800000000', password: 'p', roleId: 1, status: 1 });
    await systemApi.saveRolePermissions(2, ['user:manage']);
    await systemApi.permissions('API');
    await systemApi.auditLogs({ module: 'ROUTE', operatorId: 1 });
    assert.match(requests[0].url, /\/users\?pageNo=1&pageSize=100$/);
    assert.equal(requests[1].options.method, 'POST');
    assert.match(requests[2].url, /\/roles\/2\/permissions$/);
    assert.match(requests[3].url, /\/permissions\?permissionType=API$/);
    assert.match(requests[4].url, /\/audit-logs\?pageNo=1&pageSize=100&module=ROUTE&operatorId=1$/);
  }],
  ['password reset uses backend newPassword contract', async () => {
    requests.length = 0;
    await systemApi.resetPassword(3, { newPassword: 'fresh' });
    assert.match(requests[0].url, /\/users\/3\/reset-password$/);
    assert.equal(JSON.parse(requests[0].options.body).newPassword, 'fresh');
  }],
  ['tire OCR client uses current JSON attachmentId contract', async () => {
    requests.length = 0;
    await tireApi.ocr(9);
    assert.match(requests[0].url, /\/ocr\/tire-number$/);
    assert.equal(requests[0].options.method, 'POST');
    assert.equal(JSON.parse(requests[0].options.body).attachmentId, 9);
    assert.doesNotMatch(String(requests[0].options.body), /FormData|recognizedText|rawResultJson|ocrProvider/);
  }]
];

for (const [name, run] of tests) {
  await run();
  console.log(`ok - ${name}`);
}

console.log(`${tests.length} tests passed`);
await import('./approvalApplicationTests.mjs');
