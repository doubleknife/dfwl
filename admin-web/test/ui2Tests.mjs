import assert from 'node:assert/strict';
import { dashboardMetrics, firstAllowedReport, money, pageRows, settlementFactSummary } from '../src/views/ui2DataModel.js';

assert.deepEqual(dashboardMetrics({ month: {
  incomeAmount: 1000, routeExpense: 100, dailyExpense: 50, driverSalary: 200,
  companyTotalProfit: 650, routeCount: 3
} }), [
  { label: '本月收入', value: '¥ 1,000.00' },
  { label: '本月支出', value: '¥ 350.00' },
  { label: '本月利润', value: '¥ 650.00' },
  { label: '本月趟次', value: 3 }
]);
assert.equal(money(1234567.5), '¥ 1,234,567.50');
assert.equal(settlementFactSummary('DAILY_EXPENSE', JSON.stringify({
  status: 'ACTIVE', createdAt: '2026-09-27T05:27:48', expenseNo: 'E001',
  expenseType: 'REPAIR', businessDate: '2026-09-27'
})), '维修费 · 2026-09-27');
assert.equal(settlementFactSummary('ROUTE', JSON.stringify({
  routeNo: 'R001', businessDate: '2026-09-27', driverId: 1
})), 'R001 · 2026-09-27');
assert.equal(settlementFactSummary('DAILY_EXPENSE', 'broken'), '快照可在高级信息中查看');

const reports = [
  { key: 'profit', permission: 'report:profit' },
  { key: 'vehicle', permission: 'report:vehicle' }
];
assert.equal(firstAllowedReport(reports, permission => permission === 'report:vehicle').key, 'vehicle');
assert.equal(firstAllowedReport(reports, () => false), null);
const all = Array.from({ length: 47 }, (_, index) => index + 1);
assert.deepEqual(pageRows(all, 2), Array.from({ length: 20 }, (_, index) => index + 21));
assert.deepEqual(pageRows(all, 3), [41, 42, 43, 44, 45, 46, 47]);
console.log('4 UI2 data and permission tests passed');
