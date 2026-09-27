const { request } = require('../../utils/request');

Page({
  data: { user: {}, driver: null, vehicle: null, oldPassword: '', newPassword: '', loading: false,
    loadingProfile: false, securityOpen: false, error: '', passwordError: '', driverTypeLabel: '', bindingLabel: '', initial: '' },
  onShow() { this.load(); },
  async load() {
    this.setData({ loadingProfile: true, error: '' });
    try {
    const user = await request({ url: '/me' });
    const driver = user.driverId ? {
      id: user.driverId,
      name: user.driverName,
      driverType: user.driverType,
      currentVehicleId: user.currentVehicleId,
      currentPlateNo: user.currentPlateNo
    } : null;
    const vehicle = user.currentVehicleId ? { id: user.currentVehicleId, plateNo: user.currentPlateNo } : null;
    this.setData({ user, driver, vehicle, initial: (driver?.name || user.driverName || user.phone || '我').charAt(0),
      driverTypeLabel: user.driverType === 'INTERNAL' ? '自有司机' : user.driverType === 'OUTSOURCED' ? '外协司机' : '管理人员',
      bindingLabel: vehicle ? vehicle.plateNo : '当前未绑定车辆' });
    } catch (error) {
      this.setData({ error: error.message || '个人信息加载失败，请重试' });
    } finally { this.setData({ loadingProfile: false }); }
  },
  toggleSecurity() { this.setData({ securityOpen: !this.data.securityOpen, passwordError: '' }); },
  onOld(e) { this.setData({ oldPassword: e.detail.value }); },
  onNew(e) { this.setData({ newPassword: e.detail.value }); },
  async changePassword() {
    if (this.data.loading) return;
    if (!this.data.oldPassword || !this.data.newPassword) {
      this.setData({ passwordError: '请填写原密码和新密码' }); return;
    }
    this.setData({ loading: true, passwordError: '' });
    try {
      await request({
        url: '/auth/password',
        method: 'PUT',
        data: { oldPassword: this.data.oldPassword, newPassword: this.data.newPassword }
      });
      wx.showToast({ title: '已修改' });
      this.setData({ oldPassword: '', newPassword: '', passwordError: '' });
    } catch (error) {
      this.setData({ passwordError: error.message || '修改密码失败，请重试' });
    } finally {
      this.setData({ loading: false });
    }
  },
  logout() {
    wx.showModal({ title: '退出登录', content: '确认退出当前账号？', success: (res) => {
      if (res.confirm) { getApp().clearSession(); wx.redirectTo({ url: '/pages/login/index' }); }
    } });
  }
});
