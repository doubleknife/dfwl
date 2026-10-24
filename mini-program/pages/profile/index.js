const { request } = require('../../utils/request');

Page({
  data: { user: {}, driver: null, vehicle: null,
    loadingProfile: false, error: '', roleName: '', driverTypeLabel: '', bindingLabel: '', initial: '' },
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
    this.setData({ user, driver, vehicle, initial: (user.name || user.phone || '我').charAt(0),
      roleName: user.role?.name || user.roleName || '角色未配置',
      driverTypeLabel: user.driverType === 'INTERNAL' ? '自有司机' : user.driverType === 'OUTSOURCED' ? '外协司机'
        : (user.role?.name || user.roleName || '管理人员'),
      bindingLabel: vehicle ? vehicle.plateNo : '当前未绑定车辆' });
    } catch (error) {
      this.setData({ error: error.message || '个人信息加载失败，请重试' });
    } finally { this.setData({ loadingProfile: false }); }
  },
  openChangePassword() { wx.navigateTo({ url: '/pages/change-password/index' }); },
  logout() {
    wx.showModal({ title: '退出登录', content: '确认退出当前账号？', success: async (res) => {
      if (!res.confirm) return;
      let revoked = false;
      try {
        await request({ url: '/auth/logout', method: 'POST' });
        revoked = true;
      } catch {
        // Local logout must complete even when server revocation cannot be confirmed.
      } finally {
        getApp().clearSession();
        if (revoked) wx.redirectTo({ url: '/pages/login/index' });
        else wx.showModal({ title: '退出提示', content: '本机已退出，服务端会话撤销未确认',
          showCancel: false, complete: () => wx.redirectTo({ url: '/pages/login/index' }) });
      }
    } });
  }
});
