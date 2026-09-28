import { displayValue, expenseTypeLabels } from '../utils/displayLabels.js';

export function money(value) {
  return `¥ ${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function settlementBusinessSummary(summary) {
  if (!summary) return '';
  const companyCategories = { ROUTE: '线路费用', DAILY: '日常费用', UNASSIGNED: '未归属公司费用' };
  return summary.split(' · ').map(part =>
    companyCategories[part] || expenseTypeLabels[part] || part).join(' · ');
}

export function settlementFactSummary(factType, snapshotJson) {
  try {
    const data = JSON.parse(snapshotJson || '{}');
    if (!data || typeof data !== 'object' || Array.isArray(data)) return '无业务摘要';
    if (factType === 'ROUTE') {
      return [data.routeNo, data.businessDate].filter(Boolean).join(' · ') || '无业务摘要';
    }
    if (factType === 'DAILY_EXPENSE') {
      return [data.expenseType && displayValue('expenseType', data.expenseType), data.businessDate]
        .filter(Boolean).join(' · ') || '无业务摘要';
    }
    return '详情见原始快照';
  } catch {
    return '快照可在高级信息中查看';
  }
}

export function firstAllowedReport(definitions, permitted) {
  return definitions.find(item => permitted(item.permission)) || null;
}

export function pageRows(rows, page, size = 20) {
  return rows.slice((page - 1) * size, page * size);
}

export function dashboardMetrics(summary) {
  const month = summary.month || {};
  return [
    { label: '本月收入', value: money(month.incomeAmount) },
    { label: '本月支出', value: money(Number(month.routeExpense || 0) + Number(month.dailyExpense || 0) + Number(month.driverSalary || 0)) },
    { label: '本月利润', value: money(month.companyTotalProfit) },
    { label: '本月趟次', value: month.routeCount ?? 0 }
  ];
}
