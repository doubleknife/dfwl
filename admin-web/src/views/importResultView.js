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
  if (!value) return empty;
  const match = String(value).match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})(?::(\d{2}))?(?:\.\d+)?$/);
  return match ? `${match[1]} ${match[2]}:${match[3] || '00'}` : String(value);
}
