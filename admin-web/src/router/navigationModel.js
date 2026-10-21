export const navigation = [
  { key: 'workbench', label: '工作台', children: ['dashboard', 'my-todos'] },
  { key: 'transport', label: '运输管理', children: ['products', 'templates', { label: '调度工作台', children: ['dispatch', 'task-imports', 'routes'] }, { label: '车辆出勤', children: ['dispatch-internal', 'dispatch-outsourced'] }] },
  { key: 'master', label: '车辆与人员', children: ['drivers', 'vehicles', 'trailers', 'binding', { label: '轮胎管理', children: ['tires', 'tire-imports'] }] },
  { key: 'expense-domain', label: '费用管理', children: ['expense-apply', 'expense-ledger', 'expense-unassigned', 'charging-imports', 'charging-companies'] },
  { key: 'approvals', label: '审批中心', children: ['approvals-todo', 'approvals-mine', 'approvals-history', 'approvals-flows'] },
  { key: 'wages', label: '工资与考勤', children: ['attendance', 'salaries'] },
  { key: 'finance', label: '经营与结算', children: [{ label: '收入与利润', children: ['finance-company', 'finance-templates', 'finance-tasks'] }, { label: '支出', children: ['finance-vehicles', 'finance-drivers', 'finance-energy'] }, 'settlements'] },
  { key: 'system', label: '系统设置', children: ['users', 'roles', 'audit'] }
];

export function filterNavigation(groups, routes, allowed) {
  const leaves = new Map(routes.filter((route) => allowed(route)).map((route) => [route.key, route]));
  function permitted(children) {
    return children.map((item) => {
      if (typeof item === 'string') return leaves.get(item) || null;
      const nested = permitted(item.children || []);
      return nested.length ? { ...item, children: nested } : null;
    }).filter(Boolean);
  }
  return groups.map((group) => ({ ...group, children: permitted(group.children) }))
    .filter((group) => group.children.length);
}

export function findNavigationTrail(groups, key) {
  function find(items, parents) {
    for (const item of items) {
      const path = [...parents, item.label];
      if (item.key === key) return path;
      const nested = find(item.children || [], path);
      if (nested) return nested;
    }
    return null;
  }
  return find(groups, []) || [];
}
