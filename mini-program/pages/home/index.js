const { request } = require('../../utils/request');
const { visibleHomeCards } = require('../../utils/permissions');

Page({
  data: { user: null, driver: null, vehicle: null, bindingStatus: '不适用', cards: [], loading: false },
  onShow() { this.load(); },
  async load() {
    this.setData({ loading: true });
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
      const bindingStatus = driver ? (vehicle ? '已绑定' : '暂无绑定') : '不适用';
      this.setData({ user, driver, vehicle, bindingStatus, cards: visibleHomeCards(user, driver) });
    } finally {
      this.setData({ loading: false });
    }
  },
  open(e) {
    wx.navigateTo({ url: e.currentTarget.dataset.url });
  }
});
