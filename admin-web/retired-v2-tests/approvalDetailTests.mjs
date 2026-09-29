import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  actionName, approvalKind, detailError, displayTime, expenseExtensions, missingTireOcrEvidence,
  parseSnapshot, prepareApprovalDetail, snapshotSummary, statusName
} from '../src/views/approvalDetailModel.js';
import { approvalApi, attachmentApi } from '../src/api/modules.js';

const file = (id, purpose = 'APPROVAL_UPLOAD') => ({ id, purpose, originalFilename: `${id}.jpg`, contentType: 'image/jpeg' });
const submission = (versionNo, snapshot, attachments = []) => ({
  id: versionNo, versionNo, submittedBy: 2, submittedAt: `2026-09-2${versionNo}T10:00:00`,
  businessSnapshotJson: JSON.stringify(snapshot), attachments
});
const detail = (submissions, tasks = [], approvalType = 'EXPENSE') => prepareApprovalDetail({
  approval: { id: 4, approvalType, status: 'PENDING', businessId: 9 }, submissions, tasks
});
const expense = (amount, attributionType = 'ROUTE', itemNo = 1) => ({
  attributionType, routeId: attributionType === 'ROUTE' ? 7 : null,
  items: [{ itemNo, vehicleId: 8, driverId: 5, amount, businessDate: '2026-09-24' }]
});

const tests = [
  ['one Submission V1 is displayed as current', () => {
    const prepared = detail([submission(1, expense('12.34'), [file(10)])]);
    assert.equal(prepared.currentSubmission.versionNo, 1);
    assert.equal(prepared.submissions[0].attachments[0].id, 10);
  }],
  ['V1 and V2 are ordered by version, not API response order', () => {
    const prepared = detail([submission(2, expense('20.00')), submission(1, expense('10.00'))]);
    assert.deepEqual(prepared.submissions.map((value) => value.versionNo), [1, 2]);
    assert.equal(prepared.currentSubmission.versionNo, 2);
  }],
  ['V1 snapshot and evidence remain independent of V2', () => {
    const prepared = detail([submission(1, expense('10.00'), [file(10)]), submission(2, expense('20.00'), [file(20)])]);
    assert.equal(prepared.submissions[0].value.items[0].amount, '10.00');
    assert.equal(prepared.submissions[1].value.items[0].amount, '20.00');
    assert.deepEqual(prepared.submissions.map((value) => value.attachments[0].id), [10, 20]);
  }],
  ['Return Node with one Submission does not invent V2', () => {
    const prepared = detail([submission(1, expense('10.00'))], [{ submissionVersionNo: 1, actions: [{ actionType: 'RETURN_NODE' }] }]);
    assert.equal(prepared.submissions.length, 1);
    assert.equal(prepared.tasks[0].actions[0].actionType, 'RETURN_NODE');
  }],
  ['Return Applicant Action remains on V1 after V2 resubmit', () => {
    const prepared = detail([submission(1, expense('10.00')), submission(2, expense('11.00'))], [{
      submissionVersionNo: 1, actions: [{ actionType: 'RETURN_APPLICANT', comment: '补充后重提' }]
    }]);
    assert.equal(prepared.tasks[0].submissionVersionNo, 1);
    assert.equal(prepared.currentSubmission.versionNo, 2);
    assert.equal(prepared.tasks[0].actions[0].comment, '补充后重提');
  }],
  ['EXPENSE ROUTE summary identifies route', () => {
    assert.match(snapshotSummary({ approvalType: 'EXPENSE' }, detail([submission(1, expense('1.00'))]).currentSubmission), /线路 #7/);
  }],
  ['EXPENSE DAILY summary does not invent route', () => {
    assert.doesNotMatch(snapshotSummary({ approvalType: 'EXPENSE' }, detail([submission(1, expense('1.00', 'DAILY'))]).currentSubmission), /线路/);
  }],
  ['N expense items remain distinct', () => {
    const snapshot = expense('1.00');
    snapshot.items.push({ ...snapshot.items[0], itemNo: 2, amount: '2.00' });
    const prepared = detail([submission(1, snapshot)]);
    assert.deepEqual(prepared.currentSubmission.value.items.map((item) => item.itemNo), ['1', '2']);
  }],
  ['large unquoted JSON decimal remains exact text', () => {
    const parsed = parseSnapshot('{"items":[{"amount":123456789012345.78}]}');
    assert.equal(parsed.error, '');
    assert.equal(parsed.value.items[0].amount, '123456789012345.78');
  }],
  ['valid expense extension stays with its own item', () => {
    const extensions = expenseExtensions({ energyDetail: { stationId: 8, orderNo: 'A' } });
    assert.equal(extensions.length, 1);
    assert.match(extensions[0].text, /stationId/);
  }],
  ['TIRE Snapshot retains request, driver, vehicle and OCR item', () => {
    const prepared = detail([submission(1, { requestId: 11, driverId: 12, vehicleId: 13,
      items: [{ tireId: 14, confirmedTireNo: 'H621769799', ocrRecordId: 15, ocrAttachmentId: 16 }] }, [file(16)])], [], 'TIRE_REQUEST');
    assert.equal(approvalKind(prepared.approval), 'TIRE');
    assert.equal(prepared.currentSubmission.value.items[0].confirmedTireNo, 'H621769799');
    assert.equal(missingTireOcrEvidence(prepared.currentSubmission), false);
  }],
  ['missing OCR original is reported from Submission evidence rather than made into URL', () => {
    const prepared = detail([submission(1, { items: [{ ocrAttachmentId: 16 }] })], [], 'TIRE_REQUEST');
    assert.equal(missingTireOcrEvidence(prepared.currentSubmission), true);
  }],
  ['TIRE V1 and V2 retain separate OCR images', () => {
    const prepared = detail([
      submission(1, { items: [{ ocrAttachmentId: 16 }] }, [file(16)]),
      submission(2, { items: [{ ocrAttachmentId: 26 }] }, [file(26)])
    ], [], 'TIRE_REQUEST');
    assert.deepEqual(prepared.submissions.map((value) => value.attachments[0].id), [16, 26]);
  }],
  ['one Submission retains multiple evidence files', () => {
    const prepared = detail([submission(1, expense('1.00'), [file(10), file(11)])]);
    assert.deepEqual(prepared.currentSubmission.attachments.map((value) => value.id), [10, 11]);
  }],
  ['non-image evidence keeps filename and type for authorized download', () => {
    const prepared = detail([submission(1, expense('1.00'), [
      { id: 30, originalFilename: 'audit.pdf', contentType: 'application/pdf' }
    ])]);
    assert.equal(prepared.currentSubmission.attachments[0].originalFilename, 'audit.pdf');
    assert.equal(prepared.currentSubmission.attachments[0].contentType, 'application/pdf');
  }],
  ['OTHER remains generic without expense or tire assumptions', () => {
    const prepared = detail([submission(1, { customField: 'a' })], [], 'OTHER');
    assert.equal(approvalKind(prepared.approval), 'OTHER');
    assert.equal(prepared.currentSubmission.value.customField, 'a');
  }],
  ['Action evidence belongs to its own Action, not Submission', () => {
    const prepared = detail([submission(1, expense('1.00'), [file(10)])], [{
      id: 1, submissionVersionNo: 1, actions: [{ id: 2, actionType: 'APPROVE', attachments: [file(20, 'APPROVAL_ACTION')] }]
    }]);
    assert.equal(prepared.submissions[0].attachments[0].id, 10);
    assert.equal(prepared.tasks[0].actions[0].attachments[0].id, 20);
  }],
  ['two Actions keep their own evidence and remarks', () => {
    const prepared = detail([submission(1, expense('1.00'))], [{
      id: 1, actions: [
        { id: 2, comment: '补充依据', operatedAt: '2026-09-24T10:00:00', operatorId: 3, attachments: [file(20)] },
        { id: 3, comment: '确认', operatedAt: '2026-09-24T11:00:00', operatorId: 4, attachments: [file(21)] }
      ]
    }]);
    assert.deepEqual(prepared.tasks[0].actions.map((value) => value.attachments[0].id), [20, 21]);
    assert.equal(prepared.tasks[0].actions[0].comment, '补充依据');
    assert.equal(prepared.tasks[0].actions[1].operatorId, 4);
  }],
  ['Action time is formatted without changing chronology', () => {
    assert.equal(displayTime('2026-09-24T10:11:12'), '2026-09-24 10:11:12');
  }],
  ['Action labels include Approve and both returns', () => {
    assert.deepEqual(['APPROVE', 'RETURN_APPLICANT', 'RETURN_NODE'].map(actionName), ['通过', '打回申请人', '打回指定节点']);
    assert.equal(statusName('RETURNED_TO_APPLICANT'), '已打回申请人');
  }],
  ['403 and 404 errors retain message and requestId', () => {
    assert.match(detailError({ status: 403, message: '无权访问', requestId: 'r1' }), /无权.*r1/);
    assert.match(detailError({ status: 404, message: '文件不存在', requestId: 'r2' }, '附件'), /附件不存在.*r2/);
  }],
  ['401 explains re-login', () => {
    assert.match(detailError({ status: 401, message: 'Unauthorized' }), /登录已失效/);
  }],
  ['invalid Snapshot is explicit and does not drop evidence', () => {
    const prepared = detail([{ ...submission(1, {}, [file(10)]), businessSnapshotJson: '{broken' }]);
    assert.match(prepared.submissions[0].error, /无法解析/);
    assert.equal(prepared.submissions[0].attachments[0].id, 10);
  }],
  ['missing evidence arrays normalize to empty without borrowing another version', () => {
    const prepared = detail([submission(1, expense('1.00'), [file(10)]), { ...submission(2, expense('2.00')), attachments: null }]);
    assert.deepEqual(prepared.submissions[1].attachments, []);
    assert.equal(prepared.submissions[0].attachments.length, 1);
  }]
];

for (const [name, run] of tests) {
  await run();
  console.log(`ok - ${name}`);
}

const originalFetch = globalThis.fetch;
try {
  const requests = [];
  globalThis.fetch = async (url, options) => {
    requests.push({ url: String(url), options });
    return { ok: true, status: 200, headers: { get: () => 'application/octet-stream' }, blob: async () => new Blob(['image']) };
  };
  await attachmentApi.download(23);
  assert.match(requests[0].url, /\/attachments\/23$/);
  assert.doesNotMatch(requests[0].url, /storageKey|ocrAttachmentId/);
  for (const scope of ['MINE', 'TODO', 'DONE', 'ALL']) await approvalApi.list(scope);
  assert.deepEqual(requests.slice(1).map((value) => new URL(value.url, 'http://localhost').searchParams.get('scope')),
    ['MINE', 'TODO', 'DONE', 'ALL']);
  globalThis.fetch = async () => ({ ok: false, status: 403, headers: { get: () => 'application/json' },
    json: async () => ({ code: 'AUTH_003', message: '无权访问', requestId: 'evidence-r1' }) });
  await assert.rejects(attachmentApi.download(23), (error) => error.status === 403 && error.requestId === 'evidence-r1');
  globalThis.fetch = async () => ({ ok: false, status: 404, headers: { get: () => 'application/json' },
    json: async () => ({ code: 'NOT_FOUND', message: '附件不存在', requestId: 'evidence-r2' }) });
  await assert.rejects(attachmentApi.download(23), (error) => error.status === 404 && error.requestId === 'evidence-r2');
  console.log('ok - authorized attachment endpoint preserves 403/404');
} finally { globalThis.fetch = originalFetch; }

const view = readFileSync(new URL('../src/views/ApprovalsView.vue', import.meta.url), 'utf8');
const routes = readFileSync(new URL('../src/router/routes.js', import.meta.url), 'utf8');
const panel = readFileSync(new URL('../src/components/ApprovalDetailPanel.vue', import.meta.url), 'utf8');
const evidence = readFileSync(new URL('../src/components/ApprovalEvidence.vue', import.meta.url), 'utf8');
assert.match(view, /'MINE'.*'TODO'.*'DONE'.*'ALL'/s);
assert.match(routes, /key: 'approvals'.*'approval:process'.*'approval:return'/);
assert.match(view, /<ApprovalDetailPanel v-if="detail"/);
assert.match(view, /await Promise\.all\(\[load\(\), refreshDetail\(\)\]\)/);
assert.match(view, /if \(actionBusy\.value/);
assert.match(panel, /Submission V\{\{ submission\.versionNo \}\}/);
assert.match(panel, /<ApprovalEvidence :attachments="submission\.attachments"/);
assert.match(panel, /<ApprovalEvidence :attachments="action\.attachments"/);
assert.match(evidence, /attachmentApi\.download\(file\.id\)/);
assert.match(evidence, /URL\.createObjectURL\(blob\)/);
assert.match(evidence, /link\.download = file\.originalFilename/);
assert.match(view, /:disabled="actionBusy" @click="act\('APPROVE'\)"/);
assert.match(panel, /action\.operatorId/);
assert.doesNotMatch(evidence, /[`'"]\/attachments\/\$\{/);
console.log('ok - unified detail, version and Action sections use authorized evidence');
console.log(`${tests.length + 2} approval detail tests passed`);
