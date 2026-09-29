const { request } = require('../../utils/request');
const { hasPermission, visibleHomeActions } = require('../../utils/permissions');
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
      const [routePage, unread] = await Promise.all([
        driver && hasPermission(user, 'task:view') ? request({ url: '/driver/transport-tasks?pageNo=1&pageSize=50' }) : Promise.resolve({ records: [] }),
        request({ url: '/notifications/unread-count' }).catch(() => ({ unreadCount: 0 }))
      ]);
      const displayName = user.driverName || user.name || user.phone || '我的工作台';
      const roleLabel = driver ? (driver.driverType === 'INTERNAL' ? '自有司机' : '外协司机') : (user.roleName || '管理人员');
      this.setData({ user, driver, vehicle, displayName, initial: displayName.charAt(0), roleLabel,
        actions: visibleHomeActions(user, driver),
        currentTask: currentTask(routePage.records || [], user.driverId), todoCount: 0,
        unreadCount: Number(unread.unreadCount || 0), error: '' });
    } catch (error) {
      this.setData({ error: error.message || '工作台加载失败，请重试' });
    } finally {
      this.setData({ loading: false });
    }
  },
  openProfile() { wx.navigateTo({ url: '/pages/profile/index' }); },
  openNotifications() { wx.navigateTo({ url: '/pages/notifications/index' }); },
  openAction(e) { if (e.detail.url) wx.navigateTo({ url: e.detail.url }); },
  openTask(e) { wx.navigateTo({ url: `/pages/route-detail/index?id=${e.detail.id}` }); },
});
