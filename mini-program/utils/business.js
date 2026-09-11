const SENSITIVE_ROUTE_FIELDS = [
  'taxUnitPrice',
  'carryingFee',
  'incomeAmount',
  'routeExpense',
  'dailyExpense',
  'profitAmount',
  'totalProfit',
  'expenseAmount',
  'amount'
];

function toNumber(value) {
  if (value === '' || value === null || value === undefined) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function netWeight(grossWeight, tareWeight) {
  const gross = toNumber(grossWeight);
  const tare = toNumber(tareWeight);
  if (gross === null || tare === null) return '';
  return Math.max(gross - tare, 0).toFixed(3);
}

function validateWeights(grossWeight, tareWeight) {
  const gross = toNumber(grossWeight);
  const tare = toNumber(tareWeight);
  if (gross === null || tare === null || gross <= 0 || tare <= 0) {
    return { ok: false, message: '毛重和皮重必须为正数' };
  }
  if (gross <= tare) {
    return { ok: false, message: '毛重必须大于皮重' };
  }
  return { ok: true, message: '' };
}

function loadStandardMet(grossWeight, tareWeight, threshold) {
  const net = toNumber(netWeight(grossWeight, tareWeight));
  const target = toNumber(threshold);
  if (net === null || target === null || target <= 0) return null;
  return net >= target;
}

function sanitizeRoute(route) {
  const safe = { ...(route || {}) };
  SENSITIVE_ROUTE_FIELDS.forEach((field) => {
    delete safe[field];
  });
  return safe;
}

function containsSensitiveField(route) {
  return SENSITIVE_ROUTE_FIELDS.some((field) => Object.prototype.hasOwnProperty.call(route || {}, field));
}

module.exports = {
  SENSITIVE_ROUTE_FIELDS,
  netWeight,
  validateWeights,
  loadStandardMet,
  sanitizeRoute,
  containsSensitiveField
};
