import assert from 'node:assert/strict';
import { hasAnyPermission, hasPermission } from '../src/utils/permissions.js';
import { unwrapApiPayload } from '../src/api/response.js';

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
  return {
    ok: true,
    status: 200,
    headers: { get: () => 'application/json' },
    async json() {
      return { code: '0', message: 'OK', data: { ok: true, records: [] } };
    }
  };
};

const { masterApi, approvalApi, systemApi, tireApi } = await import('../src/api/modules.js');

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
  ['approval flow API covers list, versions, active and detail', async () => {
    requests.length = 0;
    await approvalApi.flowList({ approvalType: 'EXPENSE', status: 'ACTIVE' });
    await approvalApi.flowVersions('EXPENSE');
    await approvalApi.activeFlow('EXPENSE');
    await approvalApi.flowDetail(12);
    assert.match(requests[0].url, /\/approval-flows\?pageNo=1&pageSize=100&approvalType=EXPENSE&status=ACTIVE$/);
    assert.match(requests[1].url, /\/approval-flows\/types\/EXPENSE\/versions$/);
    assert.match(requests[2].url, /\/approval-flows\/types\/EXPENSE\/active$/);
    assert.match(requests[3].url, /\/approval-flows\/12$/);
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
