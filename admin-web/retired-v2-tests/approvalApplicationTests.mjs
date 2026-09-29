import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { readSheet } from 'read-excel-file/browser';
import {
  allowedAttributions, expensePayload, loadAllPages, mapExcelRows, newItem, normalizeAmount,
  normalizeDate, visibleTemplates
} from '../src/views/approvalApplicationModel.js';

const expense = {
  templateId: 11, category: 'EXPENSE', expenseType: 'WATER',
  allowedInputModes: ['SINGLE', 'EXCEL'], allowedAttributionTypes: ['ROUTE', 'DAILY'],
  evidenceRequirement: 'REQUIRED'
};
const vehicles = [{ id: 4, plateNo: '云A12345' }];
const drivers = [{ id: 7, name: '张师傅' }];
let counter = 0;
const next = () => `I${++counter}`;
const sample = (values = {}) => newItem('I1', {
  vehicleId: 4, driverId: 7, amount: '0.01', businessDate: '2026-09-24', ...values
});
const cases = [
  ['available only offers EXPENSE and OTHER', () => {
    assert.deepEqual(visibleTemplates([{ category: 'TIRE' }, expense, { category: 'OTHER' }]).map((x) => x.category), ['EXPENSE', 'OTHER']);
    assert.deepEqual(visibleTemplates([]), []);
  }],
  ['attribution is restricted to template settings', () => {
    assert.deepEqual(allowedAttributions({ allowedAttributionTypes: ['DAILY'] }), ['DAILY']);
    assert.deepEqual(allowedAttributions({ allowedAttributionTypes: ['ROUTE'] }), ['ROUTE']);
    assert.deepEqual(allowedAttributions(expense), ['ROUTE', 'DAILY']);
  }],
  ['ROUTE requires route, DAILY sends null', () => {
    assert.throws(() => expensePayload(expense, 'ROUTE', '', [sample()], [9]), /选择线路/);
    assert.equal(expensePayload(expense, 'ROUTE', '3', [sample()], [9]).routeId, 3);
    assert.equal(expensePayload(expense, 'DAILY', '3', [sample()], [9]).routeId, null);
  }],
  ['precise money text is preserved and invalid values rejected', () => {
    assert.equal(normalizeAmount('123456789012345.78'), '123456789012345.78');
    assert.equal(normalizeAmount('0.01'), '0.01');
    assert.throws(() => normalizeAmount('0.001'), /最多两位/);
    assert.throws(() => normalizeAmount('12345678901234567.89'), /DECIMAL/);
    assert.throws(() => normalizeAmount(123456789012345.78), /安全精度/);
    const body = expensePayload(expense, 'DAILY', '', [sample({ amount: '123456789012345.78' })], [9]);
    assert.equal(body.items[0].amount, '123456789012345.78');
    assert.equal(body.items[0].driverId, 7);
    assert.equal(body.templateId, 11);
    assert.equal('expenseType' in body, false);
  }],
  ['dates are real calendar dates', () => {
    assert.equal(normalizeDate('2026-09-24'), '2026-09-24');
    assert.throws(() => normalizeDate('2026-02-30'), /有效日历/);
  }],
  ['stable itemNo survives delete, edit, and additional import', () => {
    counter = 0;
    const first = newItem(next());
    const second = newItem(next());
    second.amount = '0.01';
    const remaining = [first, second].filter((item) => item.itemNo !== first.itemNo);
    const nextImported = newItem(next());
    assert.deepEqual([remaining[0].itemNo, nextImported.itemNo], ['I2', 'I3']);
  }],
  ['Excel unified columns map formal IDs', () => {
    counter = 0;
    const rows = mapExcelRows([['车辆', '司机', '金额', '日期'], ['云A12345', '张师傅', '0.01', '2026-09-24']], vehicles, drivers, next);
    assert.deepEqual([rows[0].vehicleId, rows[0].driverId, rows[0].amount, rows[0].businessDate], [4, 7, '0.01', '2026-09-24']);
    assert.equal(rows[0].itemNo, 'I1');
  }],
  ['Excel errors identify row and unmatched or ambiguous master data', () => {
    assert.throws(() => mapExcelRows([['车辆', '司机', '金额', '日期'], ['未知', '张师傅', 1, '2026-09-24']], vehicles, drivers, next), /第 2 行.*未匹配/);
    assert.throws(() => mapExcelRows([['车辆', '司机', '金额', '日期'], ['云A12345', '张师傅', 1, '2026-09-24']], vehicles, [...drivers, { id: 8, name: '张师傅' }], next), /有多个匹配/);
    assert.throws(() => mapExcelRows([['车辆', '司机', '金额', '日期'], ['云A12345', '', 1, '2026-02-30']], vehicles, drivers, next), /日期/);
  }],
  ['master data selection loads every authorized page', async () => {
    const pages = [];
    const records = await loadAllPages(async (pageNo) => {
      pages.push(pageNo);
      return { pageNo, pageSize: 2, total: 3, records: pageNo === 1
        ? [...vehicles, { id: 5, plateNo: '云A54321' }]
        : [{ id: 6, plateNo: '云A98765' }] };
    });
    assert.deepEqual(pages, [1, 2]);
    assert.equal(records.length, 3);
    assert.equal(records[2].plateNo, '云A98765');
  }],
  ['REQUIRED evidence and duplicate item numbers block submit', () => {
    assert.throws(() => expensePayload(expense, 'DAILY', '', [sample()], []), /图片依据/);
    assert.throws(() => expensePayload(expense, 'DAILY', '', [sample(), sample()], [9]), /重复/);
  }],
  ['energy details are required without invented defaults', () => {
    const energy = { ...expense, expenseType: 'ELECTRIC' };
    assert.throws(() => expensePayload(energy, 'ROUTE', 3, [sample()], [9]), /能源明细/);
    const item = sample({ energyType: 'ELECTRIC', orderNo: 'O-1', startTime: '2026-09-24T12:30', quantity: '1.234' });
    assert.equal(expensePayload(energy, 'ROUTE', 3, [item], [9]).items[0].energyDetail.quantity, '1.234');
  }],
  ['local Excel fixture parses in browser without network', async () => {
    const bytes = readFileSync(new URL('./fixtures/expense-items.xlsx', import.meta.url));
    const file = new File([bytes], 'expense-items.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const rows = await readSheet(file);
    assert.deepEqual(rows[0], ['车辆', '司机', '金额', '日期']);
    assert.equal(rows[1][2], '0.01');
  }],
  ['view uses API client for uploads and keeps Excel local', () => {
    const source = readFileSync(new URL('../src/components/ApprovalCreateForm.vue', import.meta.url), 'utf8');
    assert.match(source, /readSheet\(file\)/);
    assert.match(source, /attachmentApi\.upload\(body\)/);
    assert.doesNotMatch(source, /fetch\(|importApi|FileReader|upload\(file\)/);
    assert.match(source, /canManual/);
    assert.match(source, /canExcel/);
  }],
  ['approval center is single application entry', () => {
    const source = readFileSync(new URL('../src/views/ApprovalsView.vue', import.meta.url), 'utf8');
    const expenses = readFileSync(new URL('../src/views/ExpensesView.vue', import.meta.url), 'utf8');
    assert.match(source, /ApprovalCreateForm/);
    assert.doesNotMatch(expenses, /approvalApi\.create|approvalTemplateApi\.available/);
  }]
];

for (const [name, run] of cases) {
  await run();
  console.log(`ok - ${name}`);
}
console.log(`${cases.length} approval application tests passed`);
