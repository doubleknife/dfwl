function requiredEnergyType(expenseType) {
  if (['ELECTRIC', 'TEMP_ELECTRIC'].includes(expenseType)) return 'ELECTRIC';
  if (['GAS', 'TEMP_GAS'].includes(expenseType)) return 'GAS';
  return '';
}

function eligibleVehicles(expenseType, vehicles) {
  const required = requiredEnergyType(expenseType);
  return (vehicles || []).filter((vehicle) => !required || vehicle.energyType === required);
}

function eligibleTasks(expenseType, tasks, vehicles) {
  const required = requiredEnergyType(expenseType);
  if (!required) return tasks || [];
  const vehicleIds = new Set(eligibleVehicles(expenseType, vehicles).map((vehicle) => Number(vehicle.id)));
  return (tasks || []).filter((task) => vehicleIds.has(Number(task.vehicleId)));
}

function vehicleEnergyError(expenseType, vehicle) {
  if (!vehicle) return '';
  if (requiredEnergyType(expenseType) === 'ELECTRIC' && vehicle.energyType !== 'ELECTRIC')
    return '电费、临时电费只能选择电车';
  if (requiredEnergyType(expenseType) === 'GAS' && vehicle.energyType !== 'GAS')
    return '气费、临时气费只能选择气车';
  return '';
}

function energyHint(expenseType) {
  if (['ELECTRIC', 'TEMP_ELECTRIC'].includes(expenseType)) return '当前费用大类仅适用于电车';
  if (['GAS', 'TEMP_GAS'].includes(expenseType)) return '当前费用大类仅适用于气车';
  return '';
}

module.exports = { requiredEnergyType, eligibleVehicles, eligibleTasks, vehicleEnergyError, energyHint };
