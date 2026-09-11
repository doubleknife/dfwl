const { request } = require('../../utils/request');
const { visibleHomeCards } = require('../../utils/permissions');

Page({
  data: { user: null, driver: null, vehicle: null, cards: [], loading: false },
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
      this.setData({ user, driver, vehicle, cards: visibleHomeCards(user, driver) });
    } finally {
      this.setData({ loading: false });
    }
  },
  open(e) {
    wx.navigateTo({ url: e.currentTarget.dataset.url });
  }
});
