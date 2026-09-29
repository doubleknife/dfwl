export function weightEntries(versions = []) {
  let adjustmentNumber = 0;
  return [...versions].sort((a, b) => Number(a.versionNo) - Number(b.versionNo)).map((version) => ({
    ...version,
    businessLabel: version.sourceType === 'UNLOAD'
      ? '原始卸货'
      : version.sourceType === 'ADJUSTMENT'
        ? `第${++adjustmentNumber}次重量调整`
        : '重量记录'
  }));
}

export function weightText(value) {
  if (value == null || value === '') return '—';
  const text = String(value);
  return `${text.includes('.') ? text.replace(/0+$/, '').replace(/\.$/, '') : text} 吨`;
}

export function adjustedNetWeight(grossWeight, tareWeight) {
  const decimal = /^\d+(?:\.\d{1,3})?$/;
  const gross = String(grossWeight).trim();
  const tare = String(tareWeight).trim();
  if (!decimal.test(gross) || !decimal.test(tare)) return '';
  const scaled = (value) => {
    const [whole, fraction = ''] = value.split('.');
    return BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0'));
  };
  const grossUnits = scaled(gross);
  const tareUnits = scaled(tare);
  if (grossUnits <= 0n || tareUnits <= 0n || grossUnits <= tareUnits) return '';
  const difference = grossUnits - tareUnits;
  return `${difference / 1000n}.${String(difference % 1000n).padStart(3, '0')}`
    .replace(/0+$/, '').replace(/\.$/, '');
}

export function canAdjustWeight(route, permitted) {
  return Boolean(permitted && route?.status === 'COMPLETED');
}

export function weightAdjustUploadBody(taskId, file) {
  const body = new FormData();
  body.append('ownerType', 'TASK');
  body.append('ownerId', String(taskId));
  body.append('purpose', 'WEIGHT_ADJUST');
  body.append('file', file);
  return body;
}

export function weightAdjustmentBody(form, images, expectedVersion) {
  return {
    grossWeight: form.grossWeight,
    tareWeight: form.tareWeight,
    reason: form.reason.trim(),
    evidenceAttachmentIds: images.map((file) => file.id),
    expectedVersion
  };
}

export function maskedPhone(phone) {
  const text = String(phone || '');
  return /^\d{11}$/.test(text) ? `${text.slice(0, 3)}****${text.slice(-4)}` : '';
}

export function operatorIdentity(id, users = [], drivers = [], currentUser = null) {
  if (id == null) return { name: '操作人姓名未记录', phone: '' };
  const driver = drivers.find((item) => Number(item.userId) === Number(id));
  const user = users.find((item) => Number(item.id) === Number(id));
  const phone = maskedPhone(driver?.phone || user?.phone || (Number(currentUser?.id) === Number(id) ? currentUser.phone : ''));
  if (driver?.name) return { name: driver.name, phone };
  const currentRole = Number(currentUser?.id) === Number(id) ? currentUser?.roleName : null;
  return { name: user?.roleName || currentRole || '操作人姓名未记录', phone };
}
