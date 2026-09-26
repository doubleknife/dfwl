function routePresentation(route) {
  if (!route) return null;
  const states = {
    PUBLISHED: { statusLabel: '等待发车', nextAction: '确认发车', tone: 'warning' },
    IN_TRANSIT: { statusLabel: '运输中', nextAction: '录入重量并卸货', tone: 'info' },
    COMPLETED: { statusLabel: '已完成', nextAction: '查看运输结果', tone: 'success' },
    UNPUBLISHED: { statusLabel: '未发布', nextAction: '等待调度发布', tone: 'neutral' },
    VOIDED: { statusLabel: '已作废', nextAction: '无需操作', tone: 'neutral' },
    CANCELLED: { statusLabel: '已取消', nextAction: '无需操作', tone: 'neutral' }
  };
  return { ...route, ...(states[route.status] || { statusLabel: '状态待确认', nextAction: '联系调度确认', tone: 'warning' }) };
}

function currentTask(routes) {
  const records = Array.isArray(routes) ? routes : [];
  const route = records.find((item) => item.status === 'IN_TRANSIT') || records.find((item) => item.status === 'PUBLISHED');
  return routePresentation(route);
}

module.exports = { routePresentation, currentTask };
