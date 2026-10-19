export function displayedPercent(fraction) {
  if (fraction == null) return 90;
  const value = Number(fraction);
  return Number((value > 1 ? value : value * 100).toFixed(2));
}

export function invalidStoredPercent(fraction) {
  return fraction != null && (Number(fraction) <= 0 || Number(fraction) > 1);
}

export function submittedFraction(percent) {
  return Number((Number(percent) / 100).toFixed(4));
}

function scaled(value, places) {
  const text = String(value ?? '');
  if (!/^\d+(?:\.\d+)?$/.test(text)) return null;
  const [integer, decimals = ''] = text.split('.');
  if (/[1-9]/.test(decimals.slice(places))) return null;
  return BigInt(integer) * 10n ** BigInt(places) + BigInt((decimals + '0'.repeat(places)).slice(0, places));
}

export function invalidFixedStandard(maxLoad, fixedTons) {
  const limit = scaled(maxLoad, 3);
  const standard = scaled(fixedTons, 3);
  return limit == null || limit <= 0n || standard == null || standard > limit;
}

export function thresholdTons(vehicle) {
  if (vehicle.loadStandardType === 'FIXED') {
    if (invalidFixedStandard(vehicle.maxLoad, vehicle.loadStandardMinLoad)) return null;
    const fixed = scaled(vehicle.loadStandardMinLoad, 3);
    return fixed == null ? null : (Number(fixed) / 1000).toFixed(3);
  }
  if (vehicle.loadStandardType !== 'PERCENT' || invalidStoredPercent(vehicle.loadStandardPercent)) return null;
  const load = scaled(vehicle.maxLoad, 3);
  const ratio = scaled(vehicle.loadStandardPercent, 4);
  if (load == null || ratio == null) return null;
  const thresholdMillis = (load * ratio + 5000n) / 10000n;
  return (Number(thresholdMillis) / 1000).toFixed(3);
}

export function editingStatus(row, type) {
  if (!row) return 1;
  return type === 'products' ? (row.enabled ? 1 : 0) : Number(row.status);
}
