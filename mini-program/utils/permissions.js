function permissionsOf(user) {
  return user && Array.isArray(user.permissions) ? user.permissions : [];
}

function roleCodeOf(user) {
  return (user && (user.roleCode || (user.role && user.role.code))) || '';
}

function hasPermission(user, code) {
  return permissionsOf(user).includes(code);
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
    { key: 'routes', title: '我的线路', url: '/pages/routes/index', visible: hasPermission(user, 'route:list') },
    { key: 'profile', title: '个人信息', url: '/pages/profile/index', visible: true }
  ];
  if (isInternalDriver(driver) && hasPermission(user, 'salary:mine')) {
    cards.push({ key: 'salary', title: '工资单', url: '/pages/salary/index', visible: true });
  }
  if (isInternalDriver(driver) && hasPermission(user, 'tire:request')) {
    cards.push({ key: 'tire', title: '换胎申请', url: '/pages/tire-request/index', visible: true });
  }
  if (!isOutsourcedDriver(driver) && hasAnyPermission(user, ['approval:create', 'approval:history:view', 'approval:process', 'approval:return'])) {
    cards.push({ key: 'approvals', title: '审批中心', url: '/pages/approvals/index', visible: true });
  }
  return cards.filter((card) => card.visible);
}

function visibleHomeActions(user, driver, availableTemplates = []) {
  const actions = [];
  if (hasPermission(user, 'route:list')) actions.push({ key: 'routes', title: '我的线路', icon: '线', url: '/pages/routes/index' });
  if (!isOutsourcedDriver(driver) && hasPermission(user, 'approval:create') && availableTemplates.length) {
    actions.push({ key: 'launchApproval', title: '发起审批', icon: '审', url: '/pages/expense-apply/index' });
  }
  if (!isOutsourcedDriver(driver) && hasAnyPermission(user, ['approval:history:view', 'approval:process', 'approval:return'])) {
    actions.push({ key: 'approvals', title: '审批中心', icon: '待', url: '/pages/approvals/index' });
  }
  if (isInternalDriver(driver) && hasPermission(user, 'salary:mine')) actions.push({ key: 'salary', title: '工资单', icon: '薪', url: '/pages/salary/index' });
  if (isInternalDriver(driver) && hasPermission(user, 'tire:request')) actions.push({ key: 'tire', title: '换胎申请', icon: '胎', url: '/pages/tire-request/index' });
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
