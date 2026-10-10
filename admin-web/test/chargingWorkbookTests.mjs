import assert from 'node:assert/strict';
import * as XLSX from 'xlsx';
import { headersAt, mappingComplete, previewChargingOrders, readChargingWorkbook } from '../src/views/chargingWorkbook.js';

function file(rows, bookType) {
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.aoa_to_sheet(rows), '订单');
  const bytes = XLSX.write(workbook, { bookType, type: 'array' });
  return { arrayBuffer: async () => bytes };
}

const columns = ['订单号', '车牌号', '充电时间', '金额'];
const mapping = { orderNo: 0, plateNo: 1, chargedAt: 2, amount: 3 };
for (const bookType of ['xls', 'xlsx']) {
  const book = await readChargingWorkbook(file([
    ['充电公司订单'], columns,
    ['001234567890123456789', '云A001', '2026-08-15 10:00:00', '12.34'],
    [], ['ZERO-1', '云A001', '2026-08-15 11:00:00', 0],
    ['合计', '', '', '12.34']
  ], bookType));
  assert.equal(book.headerRowNo, 2);
  assert.equal(mappingComplete(mapping, book.headers), true);
  const preview = previewChargingOrders(book, mapping);
  assert.equal(preview.count, 2);
  assert.equal(preview.zeroCount, 1);
  assert.equal(preview.totalAmount, '12.34');
  assert.deepEqual(preview.errors, []);
  assert.equal(preview.orders[1].status, '零元不入账');
  assert.equal(preview.orders[1].rowNo, 5);
  assert.equal(preview.ignored.length, 1);
}

const duplicateHeaders = headersAt([['订单号', '金额', '金额', '车牌号', '充电时间']], 1);
assert.equal(duplicateHeaders[1].label, duplicateHeaders[2].label);
assert.notEqual(duplicateHeaders[1].index, duplicateHeaders[2].index);
const invalid = await readChargingWorkbook(file([columns,
  ['A', '云A001', '2026-08-15 10:00:00', ''],
  ['B', '云A001', '2026-08-15 10:00:00', -1],
  ['C', '云A001', '2026-08-15 10:00:00', 'abc']
], 'xlsx'));
assert.equal(previewChargingOrders(invalid, mapping).errors.length, 3);
const missingIdentity = await readChargingWorkbook(file([columns,
  ['A', '云A001', '2026-08-15 10:00:00', '1.00'],
  ['', '', '', '2.00']
], 'xlsx'));
assert.equal(previewChargingOrders(missingIdentity, mapping).errors[0].rowNo, 3);
const totals = await readChargingWorkbook(file([columns,
  ['A', '云A001', '2026-08-15 10:00:00', '1.00'],
  ['', '', '', '1.00']
], 'xlsx'));
assert.equal(previewChargingOrders(totals, mapping).ignored[0], 3);
console.log('ok - charging workbook xls/xlsx, zero, blank rows, positional headers and invalid amounts');
