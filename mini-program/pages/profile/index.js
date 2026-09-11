const { request } = require('../../utils/request');

Page({
  data: { user: {}, driver: null, vehicle: null, oldPassword: '', newPassword: '', loading: false },
  onShow() { this.load(); },
  async load() {
    const user = await request({ url: '/me' });
    const driver = user.driverId ? {
      id: user.driverId,
      name: user.driverName,
      driverType: user.driverType,
      currentVehicleId: user.currentVehicleId,
      currentPlateNo: user.currentPlateNo
    } : null;
    const vehicle = user.currentVehicleId ? { id: user.currentVehicleId, plateNo: user.currentPlateNo } : null;
    this.setData({ user, driver, vehicle });
  },
  onOld(e) { this.setData({ oldPassword: e.detail.value }); },
  onNew(e) { this.setData({ newPassword: e.detail.value }); },
  async changePassword() {
    this.setData({ loading: true });
    try {
      await request({
        url: '/auth/password',
        method: 'PUT',
        data: { oldPassword: this.data.oldPassword, newPassword: this.data.newPassword }
      });
      wx.showToast({ title: '已修改' });
      this.setData({ oldPassword: '', newPassword: '' });
    } finally {
      this.setData({ loading: false });
    }
  },
  logout() {
    getApp().clearSession();
    wx.redirectTo({ url: '/pages/login/index' });
  }
});
