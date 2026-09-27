const STATUS = {
  PENDING: '待审批', APPROVED: '已通过', RETURNED_TO_APPLICANT: '已退回修改',
  RETURNED: '已退回', COMPLETED: '已通过', CANCELLED: '已取消',
  APPROVE: '通过', RETURN_APPLICANT: '打回申请人', RETURN_NODE: '打回上一节点'
};
const EXPENSE_TYPE = {
  ELECTRIC: '充电费', GAS: '燃气费', TEMP_ELECTRIC: '临时充电费',
  REPAIR: '维修费', TOLL: '过路费', PARKING: '停车费'
};

function statusLabel(value) { return STATUS[value] || '状态待确认'; }
function typeLabel(value) {
  if (value === 'TIRE_REQUEST') return '换胎申请';
  if (value === 'EXPENSE') return '费用申请';
  return value === 'OTHER' ? '其他审批' : '业务审批';
}

function parseSnapshot(json) {
  try { return typeof json === 'string' ? JSON.parse(json) : (json || {}); }
  catch (_) { return {}; }
}

function snapshotFacts(businessType, json, names = {}) {
  const data = parseSnapshot(json);
  const facts = [];
  if (businessType === 'TIRE_REQUEST') {
    if (data.driverId) facts.push({ label: '申请司机（当前登记）', value: names.drivers?.[data.driverId] || '已登记' });
    if (data.vehicleId) facts.push({ label: '申请车辆（当前登记）', value: names.vehicles?.[data.vehicleId] || '已登记' });
    (data.items || []).forEach((item) => {
      if (item.confirmedTireNo) facts.push({ label: '确认胎号', value: item.confirmedTireNo });
      if (item.ocrRecordId) facts.push({ label: '识别依据', value: '已关联 OCR 原图' });
    });
  } else if (businessType === 'EXPENSE' || data.expenseType) {
    facts.push({ label: '费用类型', value: EXPENSE_TYPE[data.expenseType] || '费用申请' });
    facts.push({ label: '费用归属', value: data.attributionType === 'ROUTE' ? '线路费用' : '日常费用' });
    if (data.routeId) facts.push({ label: '关联线路（当前登记）', value: names.routes?.[data.routeId] || '已关联线路' });
    (data.items || []).forEach((item, index) => {
      if (item.businessDate) facts.push({ label: `明细 ${index + 1} · 日期`, value: item.businessDate });
      if (item.vehicleId) facts.push({ label: `明细 ${index + 1} · 车辆（当前登记）`, value: names.vehicles?.[item.vehicleId] || '已登记' });
      if (item.driverId) facts.push({ label: `明细 ${index + 1} · 司机（当前登记）`, value: names.drivers?.[item.driverId] || '已登记' });
      if (item.amount) facts.push({ label: `明细 ${index + 1} · 金额`, value: `¥ ${item.amount}` });
      if (item.remark) facts.push({ label: `明细 ${index + 1} · 说明`, value: item.remark });
    });
  } else {
    if (data.title) facts.push({ label: '申请事项', value: String(data.title) });
    if (data.reason) facts.push({ label: '申请说明', value: String(data.reason) });
    if (data.description) facts.push({ label: '说明', value: String(data.description) });
  }
  return facts.length ? facts : [{ label: '申请依据', value: '请查看附件和审批记录' }];
}

function presentDetail(detail) {
  const approval = detail.approval || {};
  return {
    ...detail,
    approval: { ...approval, statusLabel: statusLabel(approval.status), typeLabel: typeLabel(approval.businessType) },
    submissions: (detail.submissions || []).map((item) => ({ ...item,
      facts: snapshotFacts(approval.businessType, item.businessSnapshotJson, detail.displayNames || {}) })),
    tasks: (detail.tasks || []).map((task) => ({ ...task, statusLabel: statusLabel(task.status),
      actions: (task.actions || []).map((action) => ({ ...action, actionLabel: statusLabel(action.actionType) })) }))
  };
}

module.exports = { statusLabel, typeLabel, parseSnapshot, snapshotFacts, presentDetail };
