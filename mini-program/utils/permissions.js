function permissionsOf(user) {
  return user && Array.isArray(user.permissions) ? user.permissions : [];
}

function roleCodeOf(user) {
  return (user && (user.roleCode || (user.role && user.role.code))) || '';
}

function hasPermission(user, code) {
  const granted = permissionsOf(user);
  if (!granted.includes(code)) return false;
  if (code.startsWith('BUTTON_MINI_')) {
    const parent = `PAGE_${code.slice('BUTTON_'.length, code.lastIndexOf('_'))}`;
    return granted.includes(parent);
  }
  return true;
}

function hasAnyPermission(user, codes) {
  return [].concat(codes || []).some((code) => hasPermission(user, code));
}

function isDriver(user) {
  return roleCodeOf(user).toUpperCase().includes('DRIVER');
}

function isOutsourcedDriver(driver) {
  return driver && driver.driverType === 'OUTSOURCED';
}

function isInternalDriver(driver) {
  return driver && driver.driverType === 'INTERNAL';
}

function visibleHomeCards(user, driver) {
  const cards = [
    { key: 'routes', title: '我的线路', url: '/pages/routes/index', visible: !!driver && hasPermission(user, 'PAGE_MINI_ROUTES') },
    { key: 'profile', title: '个人信息', url: '/pages/profile/index', visible: true }
  ];
  if (isInternalDriver(driver) && hasPermission(user, 'PAGE_MINI_SALARY')) {
    cards.push({ key: 'salary', title: '工资单', url: '/pages/salary/index', visible: true });
  }
  return cards.filter((card) => card.visible);
}

function visibleHomeActions(user, driver) {
  const actions = [];
  if (!driver && !isDriver(user) && hasPermission(user, 'PAGE_MINI_DISPATCH'))
    actions.push({ key: 'dispatch', title: '单人派单', icon: '派', url: '/pages/dispatch/index' });
  if (driver && hasPermission(user, 'PAGE_MINI_ROUTES')) actions.push({ key: 'routes', title: '我的线路', icon: '线', url: '/pages/routes/index' });
  if (!isOutsourcedDriver(driver) && hasPermission(user, 'BUTTON_MINI_EXPENSE_APPLY_SUBMIT')) actions.push({ key: 'expense', title: '发起费用申请', icon: '费', url: '/pages/expense-apply/index' });
  if (!isOutsourcedDriver(driver) && (hasPermission(user, 'PAGE_MINI_APPROVALS')
      || hasAnyPermission(user, ['BUTTON_MINI_EXPENSE_APPLY_SUBMIT', 'BUTTON_MINI_TIRE_REQUEST_SUBMIT', 'BUTTON_MINI_APPROVALS_APPROVE', 'BUTTON_MINI_APPROVALS_RETURN'])))
    actions.push({ key: 'approval', title: '我的审批', icon: '审', url: '/pages/approvals/index' });
  if (isInternalDriver(driver) && hasPermission(user, 'PAGE_MINI_SALARY')) actions.push({ key: 'salary', title: '工资单', icon: '薪', url: '/pages/salary/index' });
  if (isInternalDriver(driver) && driver.currentVehicleId && hasPermission(user, 'BUTTON_MINI_TIRE_REQUEST_SUBMIT'))
    actions.push({ key: 'tire', title: '换胎申请', icon: '胎', url: '/pages/tire-request/index' });
  return actions;
}

module.exports = {
  permissionsOf,
  roleCodeOf,
  hasPermission,
  hasAnyPermission,
  isDriver,
  isOutsourcedDriver,
  isInternalDriver,
  visibleHomeCards,
  visibleHomeActions
};
