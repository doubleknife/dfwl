export const emptyExpenseRow = () => ({
  localKey: crypto.randomUUID(), source: '手工', businessDate: new Date().toLocaleDateString('sv-SE'),
  driverId: '', vehicleId: '', trailerNo: '', amount: '', description: '', receiptNo: '',
  invoiceNo: '', remark: '', stationName: ''
});

export function appendPreviewRows(rows, previewRows, options) {
  const drivers = new Map(options.drivers.map((row) => [row.phone, row.id]));
  const vehicles = new Map(options.vehicles.map((row) => [row.plateNo, row.id]));
  return [...rows, ...previewRows.map((row) => ({
    localKey: crypto.randomUUID(), source: `Excel 第 ${row.rowNo} 行`,
    businessDate: row.businessDate, driverId: drivers.get(row.driverPhone) || '',
    vehicleId: vehicles.get(row.plateNo) || '', trailerNo: row.trailerNo || '', amount: row.amount,
    description: row.description || '', receiptNo: row.receiptNo || '',
    invoiceNo: row.invoiceNo || '', remark: row.remark || '', stationName: row.stationName || ''
  }))];
}

export function vehicleEnergyError(expenseType, vehicle) {
  if (!vehicle) return '';
  if (['ELECTRIC', 'TEMP_ELECTRIC'].includes(expenseType) && vehicle.energyType !== 'ELECTRIC')
    return '电费、临时电费只能选择电车';
  if (['GAS', 'TEMP_GAS'].includes(expenseType) && vehicle.energyType !== 'GAS')
    return '气费、临时气费只能选择气车';
  return '';
}

export function expenseRowError(row, trailerCheck = null, expenseType = '', vehicles = []) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(row.businessDate || ''))
    || !row.driverId || !row.vehicleId) return '请填写日期、司机和公司车辆';
  if (!/^\d+(\.\d{1,2})?$/.test(String(row.amount || '')) || Number(row.amount) <= 0)
    return '金额必须大于 0 且最多两位小数';
  const vehicle = vehicles.find((entry) => String(entry.id) === String(row.vehicleId));
  const energyError = vehicleEnergyError(expenseType, vehicle);
  if (energyError) return energyError;
  if (String(row.trailerNo || '').trim().length > 64) return '车挂号不能超过 64 字';
  if (String(row.trailerNo || '').trim() && trailerCheck?.number === String(row.trailerNo || '').trim()
    && trailerCheck.exists === false)
    return '车挂号不存在';
  return '';
}

export function dailyItems(rows) {
  if (!rows.length) throw new Error('请至少添加一条费用明细');
  if (rows.length > 500) throw new Error('一个申请最多 500 条费用明细');
  for (const [index, row] of rows.entries()) {
    const error = expenseRowError(row);
    if (error) throw new Error(`第 ${index + 1} 条：${error}`);
  }
  return rows.map((row) => ({
    businessDate: row.businessDate, driverId: Number(row.driverId),
    vehicleId: Number(row.vehicleId), targetTaskId: null, amount: String(row.amount),
    description: row.description || null, receiptNo: row.receiptNo || null,
    invoiceNo: row.invoiceNo || null, remark: row.remark || null,
    stationName: row.stationName || null, trailerNo: row.trailerNo?.trim() || null
  }));
}
