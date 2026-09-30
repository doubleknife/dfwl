export function appendDispatchRows(existing, template, drivers, firstKey = 1) {
  return [...existing, ...drivers.map((driver, index) => ({
    localKey: firstKey + index,
    templateStableCode: template.stableCode,
    templateName: template.name,
    customerName: template.customerName,
    loadingPlace: template.loadingPlace,
    unloadingPlace: template.unloadingPlace,
    driverId: driver.id,
    driverName: driver.name,
    driverType: driver.driverType,
    vehiclePlate: driver.currentVehiclePlateNo || '',
    outsourcedPlateNo: '',
    actualUnitPrice: String(template.defaultUnitPrice),
    actualInformationFee: String(template.defaultInformationFee),
    initialSalary: String(template.defaultDriverSalary),
    remark: '', errors: []
  }))];
}

export function updateSelectedAmounts(rows, selectedKeys, field, amount) {
  if (!['actualUnitPrice', 'actualInformationFee', 'initialSalary'].includes(field)
      || !/^\d+(\.\d{1,2})?$/.test(String(amount))) throw new Error('金额格式不正确');
  return rows.map((row) => selectedKeys.includes(row.localKey) ? { ...row, [field]: String(amount) } : row);
}

export function dispatchBody(businessDate, rows) {
  return { businessDate, rows: rows.map((row) => ({
    templateStableCode: row.templateStableCode,
    driverId: row.driverId,
    outsourcedPlateNo: row.driverType === 'OUTSOURCED' ? row.outsourcedPlateNo.trim() : null,
    actualUnitPrice: row.actualUnitPrice,
    actualInformationFee: row.actualInformationFee,
    initialSalary: row.initialSalary,
    remark: row.remark.trim() || null
  })) };
}
