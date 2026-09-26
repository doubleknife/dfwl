const { request } = require('../../utils/request');
const { hasPermission, hasAnyPermission, isDriver, isOutsourcedDriver, visibleHomeActions } = require('../../utils/permissions');
const { currentTask } = require('../../utils/homePresentation');

Page({
  data: { user: null, driver: null, vehicle: null, displayName: '', initial: '', roleLabel: '', actions: [], currentTask: null, todoCount: 0, unreadCount: 0, loading: false, error: '' },
  onShow() { this.load(); },
  async load() {
    this.setData({ loading: true, error: '' });
    try {
      const user = await request({ url: '/me' });
      getApp().globalData.user = user;
      wx.setStorageSync('user', user);
      const driver = user.driverId ? {
        id: user.driverId,
        name: user.driverName,
        driverType: user.driverType,
        currentVehicleId: user.currentVehicleId,
        currentPlateNo: user.currentPlateNo
      } : null;
      const vehicle = user.currentVehicleId ? { id: user.currentVehicleId, plateNo: user.currentPlateNo } : null;
      const canProcess = !isDriver(user) && hasAnyPermission(user, ['approval:process', 'approval:return']);
      const [routePage, templates, todoPage] = await Promise.all([
        driver && hasPermission(user, 'route:list') ? request({ url: '/routes?pageNo=1&pageSize=50' }) : Promise.resolve({ records: [] }),
        !isOutsourcedDriver(driver) && hasPermission(user, 'approval:create') ? request({ url: '/approval-templates/available' }) : Promise.resolve([]),
        canProcess ? request({ url: '/approvals?scope=TODO&pageNo=1&pageSize=1' }) : Promise.resolve({ total: 0 })
      ]);
      const displayName = user.driverName || user.name || user.phone || '我的工作台';
      const roleLabel = driver ? (driver.driverType === 'INTERNAL' ? '自有司机' : '外协司机') : (user.roleName || '管理人员');
      this.setData({ user, driver, vehicle, displayName, initial: displayName.charAt(0), roleLabel,
        actions: visibleHomeActions(user, driver, Array.isArray(templates) ? templates : templates.records || [])
          .map((action) => action.key === 'approvals' && Number(todoPage.total || 0) > 0 ? { ...action, badge: Number(todoPage.total) } : action),
        currentTask: currentTask(routePage.records || []), todoCount: Number(todoPage.total || 0), error: '' });
    } catch (error) {
      this.setData({ error: error.message || '工作台加载失败，请重试' });
    } finally {
      this.setData({ loading: false });
    }
  },
  openProfile() { wx.navigateTo({ url: '/pages/profile/index' }); },
  openNotifications() { wx.showToast({ title: '站内通知尚未启用', icon: 'none' }); },
  openAction(e) { if (e.detail.url) wx.navigateTo({ url: e.detail.url }); },
  openTask(e) { wx.navigateTo({ url: `/pages/route-detail/index?id=${e.detail.id}` }); },
  openTodo() { wx.navigateTo({ url: '/pages/approvals/index' }); }
});
