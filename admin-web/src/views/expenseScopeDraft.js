export function hasExpenseDraft(scope, dailyRows, taskItem, templateId) {
  if (scope === 'DAILY') return dailyRows.length > 0 || Boolean(templateId);
  return Boolean(templateId) || ['driverId', 'vehicleId', 'targetTaskId', 'amount', 'description',
    'receiptNo', 'invoiceNo', 'remark', 'stationName'].some((key) => Boolean(taskItem[key]));
}
