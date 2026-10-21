const amountPattern = /^\d+(\.\d{1,2})?$/;

export const canTransferTask = (task) => ['IN_TRANSIT', 'TRANSFER_PENDING'].includes(task?.status);
export const canCancelTask = (task) => task?.status === 'PUBLISHED';

export function transferBody(task, participants, form, driver) {
  const current = participants.find((person) => person.current);
  if (!canTransferTask(task) || !current) throw new Error('当前任务状态不允许转派');
  if (!driver || Number(driver.id) === Number(current.driverId)) throw new Error('请选择另一位司机');
  if (driver.driverType === 'INTERNAL' && !driver.currentVehicleId)
    throw new Error('接手司机未绑定可用公司车辆');
  if (driver.driverType === 'OUTSOURCED' && !String(form.outsourcedPlateNo || '').trim())
    throw new Error('请填写外协司机本次真实车牌');
  if (!amountPattern.test(String(form.newSalary || ''))) throw new Error('请填写接手司机工资，最多两位小数');
  if (!String(form.reason || '').trim()) throw new Error('请填写转派原因');
  const allParticipantSalaries = participants.map((person) => {
    const amount = String(form.salaries[person.id] ?? '');
    if (!amountPattern.test(amount)) throw new Error(`请核定第 ${person.sequenceNo} 位司机工资，最多两位小数`);
    return { participantId: person.id, amount };
  });
  return {
    newDriverId: Number(driver.id),
    outsourcedPlateNo: driver.driverType === 'OUTSOURCED' ? String(form.outsourcedPlateNo).trim() : null,
    allParticipantSalaries, newSalary: String(form.newSalary), reason: String(form.reason).trim(),
    expectedVersion: task.version, expectedCurrentParticipantId: current.id
  };
}

export function cancelBody(task, reason, requestKey) {
  if (!canCancelTask(task)) throw new Error('只有未发车且未进入转派链的任务可以取消');
  if (!String(reason || '').trim()) throw new Error('请填写取消原因');
  return { reason: String(reason).trim(), expectedVersion: task.version, requestKey };
}
