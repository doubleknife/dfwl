const ENERGY_TYPES = ['ELECTRIC', 'GAS', 'TEMP_ELECTRIC'];

function availableTemplates(templates) {
  return (Array.isArray(templates) ? templates : []).filter((item) =>
    item.category === 'EXPENSE' || item.category === 'OTHER'
  );
}

function launchReason(template) {
  if (!template) return '请选择审批模板';
  if (template.category !== 'EXPENSE') return '请在管理端发起';
  if (!(template.allowedInputModes || []).includes('SINGLE')) return '请在管理端发起';
  return '';
}

function allowedAttributions(template) {
  return (template?.allowedAttributionTypes || []).filter((item) => item === 'ROUTE' || item === 'DAILY');
}

function newItem(itemNo, user = {}) {
  return {
    itemNo,
    vehicleId: user.driverType === 'INTERNAL' ? user.currentVehicleId || '' : '',
    vehicleName: user.driverType === 'INTERNAL' ? user.currentPlateNo || '' : '',
    driverId: user.driverType === 'INTERNAL' ? user.driverId || '' : '',
    driverName: user.driverType === 'INTERNAL' ? user.driverName || '' : '',
    amount: '', businessDate: '', remark: '',
    energyType: '', stationId: '', orderNo: '', startTime: '', quantity: ''
  };
}

function normalizeAmount(value) {
  const text = String(value ?? '').trim();
  if (!/^(?:(?:0|[1-9]\d*)(?:\.\d{1,2})?|\.\d{1,2})$/.test(text) || !/[1-9]/.test(text)) {
    throw new Error('金额必须是正数，最多两位小数');
  }
  const canonical = text.startsWith('.') ? `0${text}` : text;
  const [whole, fraction = ''] = canonical.split('.');
  if (whole.length > 16 || `${whole}${fraction}`.replace(/^0+/, '').length > 18) {
    throw new Error('金额超出允许范围');
  }
  return canonical;
}

function totalAmount(items) {
  const cents = (items || []).reduce((sum, item) => {
    const normalized = normalizeAmount(item.amount);
    const [whole, fraction = ''] = normalized.split('.');
    return sum + BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
  }, 0n);
  return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`;
}

function normalizeDate(value) {
  const text = String(value ?? '').trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) throw new Error('日期必须为 YYYY-MM-DD');
  const date = new Date(`${text}T00:00:00Z`);
  if (Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== text) {
    throw new Error('日期不是有效日历日期');
  }
  return text;
}

function positiveId(value, label) {
  if (!/^[1-9]\d*$/.test(String(value ?? ''))) throw new Error(`${label}不合法`);
  return Number(value);
}

function buildExpensePayload(template, attributionType, routeId, items, attachmentIds) {
  const reason = launchReason(template);
  if (reason) throw new Error(reason);
  if (!allowedAttributions(template).includes(attributionType)) throw new Error('请选择模板允许的费用归属');
  if (attributionType === 'ROUTE' && !routeId) throw new Error('线路费用必须选择线路');
  if (template.evidenceRequirement === 'REQUIRED' && !attachmentIds.length) {
    throw new Error('该模板必须上传图片依据');
  }
  if (!Array.isArray(items) || !items.length) throw new Error('请至少录入一条费用明细');
  const seen = new Set();
  const normalized = items.map((item) => {
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(item.itemNo) || seen.has(item.itemNo)) {
      throw new Error('费用明细编号无效或重复');
    }
    seen.add(item.itemNo);
    const row = {
      itemNo: item.itemNo,
      vehicleId: positiveId(item.vehicleId, `${item.itemNo}：车辆`),
      amount: normalizeAmount(item.amount),
      businessDate: normalizeDate(item.businessDate)
    };
    if (item.driverId) row.driverId = positiveId(item.driverId, `${item.itemNo}：司机`);
    if (item.remark && item.remark.trim()) row.remark = item.remark.trim();
    if (ENERGY_TYPES.includes(template.expenseType)) {
      if (!ENERGY_TYPES.includes(item.energyType) || !item.orderNo?.trim() || !item.startTime || !item.quantity) {
        throw new Error(`${item.itemNo}：请填写完整能源明细`);
      }
      if (!/^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/.test(item.startTime)) {
        throw new Error(`${item.itemNo}：能源开始时间不合法`);
      }
      const quantity = String(item.quantity).trim();
      if (!/^(?:0|[1-9]\d*)(?:\.\d{1,3})?$/.test(quantity) || !/[1-9]/.test(quantity)) {
        throw new Error(`${item.itemNo}：能源数量不合法`);
      }
      row.energyDetail = { energyType: item.energyType, orderNo: item.orderNo.trim(),
        startTime: item.startTime, quantity };
      if (item.stationId) row.energyDetail.stationId = positiveId(item.stationId, `${item.itemNo}：站点`);
    }
    return row;
  });
  return {
    templateId: positiveId(template.templateId, '模板'),
    attributionType,
    routeId: attributionType === 'ROUTE' ? positiveId(routeId, '线路') : null,
    items: normalized,
    attachmentIds: [...attachmentIds]
  };
}

module.exports = { availableTemplates, launchReason, allowedAttributions, newItem,
  normalizeAmount, normalizeDate, totalAmount, buildExpensePayload };
