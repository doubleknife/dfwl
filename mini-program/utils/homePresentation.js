function routePresentation(route) {
  if (!route) return null;
  const states = {
    PUBLISHED: { statusLabel: '等待发车', nextAction: '确认发车', tone: 'warning' },
    TRANSFER_PENDING: { statusLabel: '等待接手发车', nextAction: '确认发车', tone: 'warning' },
    IN_TRANSIT: { statusLabel: '运输中', nextAction: '录入重量并卸货', tone: 'info' },
    COMPLETED: { statusLabel: '已完成', nextAction: '查看运输结果', tone: 'success' },
    UNPUBLISHED: { statusLabel: '未发布', nextAction: '等待调度发布', tone: 'neutral' },
    CANCELLED: { statusLabel: '已取消', nextAction: '无需操作', tone: 'neutral' }
  };
  return { ...route, routeNo: route.taskNo, currentPlateNo: route.vehiclePlate,
    ...(states[route.status] || { statusLabel: '状态待确认', nextAction: '联系调度确认', tone: 'warning' }) };
}

function currentTask(routes, driverId) {
  const records = Array.isArray(routes) ? routes : [];
  const mine = driverId ? records.filter((item) => item.currentParticipant) : records;
  const route = mine.find((item) => item.status === 'IN_TRANSIT')
    || mine.find((item) => item.status === 'TRANSFER_PENDING')
    || mine.find((item) => item.status === 'PUBLISHED');
  return routePresentation(route);
}

module.exports = { routePresentation, currentTask };
