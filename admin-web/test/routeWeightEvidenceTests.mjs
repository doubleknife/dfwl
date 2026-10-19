import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { adjustedNetWeight, canAdjustWeight, maskedPhone, operatorIdentity, weightAdjustmentBody, weightAdjustUploadBody, weightEntries, weightText } from '../src/views/routeWeightEvidence.js';

const records = weightEntries([
  { versionNo: 3, sourceType: 'ADJUSTMENT' },
  { versionNo: 1, sourceType: 'UNLOAD' },
  { versionNo: 2, sourceType: 'ADJUSTMENT' }
]);
assert.deepEqual(records.map((item) => item.businessLabel), ['原始卸货', '第1次重量调整', '第2次重量调整']);
assert.equal(weightText('30.000'), '30 吨');
assert.equal(weightText('30.125'), '30.125 吨');
assert.equal(weightText(null), '—');
assert.equal(adjustedNetWeight('30.125', '5.005'), '25.12');
assert.equal(adjustedNetWeight('30', '5'), '25');
assert.equal(adjustedNetWeight('30', '30'), '');
assert.equal(adjustedNetWeight('30', '0'), '');
assert.equal(adjustedNetWeight('30.1234', '5'), '');
assert.equal(canAdjustWeight({ status: 'COMPLETED' }, true), true);
assert.equal(canAdjustWeight({ status: 'IN_TRANSIT' }, true), false);
assert.equal(canAdjustWeight({ status: 'COMPLETED' }, false), false);
const uploadBody = weightAdjustUploadBody(7, new Blob(['image'], { type: 'image/png' }));
assert.equal(uploadBody.get('ownerType'), 'TASK');
assert.equal(uploadBody.get('ownerId'), '7');
assert.equal(uploadBody.get('purpose'), 'WEIGHT_ADJUST');
assert.deepEqual(weightAdjustmentBody({ grossWeight: '31', tareWeight: '5', reason: '  复核磅单  ' }, [{ id: 9 }, { id: 10 }], 4), {
  grossWeight: '31', tareWeight: '5', reason: '复核磅单', evidenceAttachmentIds: [9, 10], expectedVersion: 4
});
assert.equal(maskedPhone('13912345678'), '139****5678');
assert.deepEqual(operatorIdentity(8, [{ id: 8, phone: '13912345678', roleName: '司机' }], [{ userId: 8, name: '张司机', phone: '13912345678' }]), { name: '张司机', phone: '139****5678' });
assert.deepEqual(operatorIdentity(1, [{ id: 1, phone: '13900000001', roleName: '管理员' }]), { name: '管理员', phone: '139****0001' });
assert.deepEqual(operatorIdentity(999), { name: '操作人姓名未记录', phone: '' });
const routeView = readFileSync(new URL('../src/views/TransportTasksV3View.vue', import.meta.url), 'utf8');
assert.match(routeView, /<button v-if="selected\.status === 'COMPLETED' && can\('BUTTON_ROUTES_WEIGHT_ADJUST'\)"[^>]*>重量纠错<\/button>/);
assert.match(routeView, /<FormDialog :open="adjusting"[^>]*>[\s\S]*?<RouteWeightImageUpload/);
assert.doesNotMatch(routeView, /证据附件 ID|逗号分隔|operation = 'adjust'/);
assert.match(routeView, /await openTask\(selected\.value\)/);
console.log('route weight evidence presentation tests passed');
