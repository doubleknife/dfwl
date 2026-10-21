import { formatDateTime } from '../utils/dateTime.js';

const failedStatuses = new Set(['INVALID', 'FAILED']);

export function filterImportRows(rows, filter) {
  if (filter === 'ALL') return rows;
  return rows.filter((row) => filter === 'FAIL' ? failedStatuses.has(row.status) : !failedStatuses.has(row.status));
}

export function countImportFailures(rows) {
  return rows.filter((row) => failedStatuses.has(row.status)).length;
}

export function summarizeCompletedRows(rows, successStatus) {
  const failures = rows.filter((row) => failedStatuses.has(row.status)).map((row) => ({
    rowNo: row.rowNo,
    reason: row.message || '导入失败，请查看该行结果'
  }));
  const success = rows.filter((row) => row.status === successStatus).length;
  return { total: rows.length, success, failed: failures.length,
    pending: rows.length - success - failures.length, failures };
}

export function formatImportDateTime(value, empty = '—') {
  return formatDateTime(value, empty);
}
