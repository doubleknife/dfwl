const { request } = require('../../utils/request');
const { sanitizeRoute } = require('../../utils/business');
const { hasPermission } = require('../../utils/permissions');
const { routePresentation } = require('../../utils/homePresentation');

Page({
  data: { id: null, route: null, user: null, driverLabel: '', vehicleLabel: '', loading: false, busy: false, confirming: false, error: '', canDepart: false, canUnload: false },
  onLoad(query) {
    this.setData({ id: query.id });
  },
  onShow() { if (this.data.id) this.load(); },
  async load() {
    this.setData({ loading: true, error: '' });
    try {
      const [route, user] = await Promise.all([request({ url: `/routes/${this.data.id}` }), request({ url: '/me' })]);
      const safe = routePresentation(sanitizeRoute(route));
      const sameDriver = user.driverId && (user.driverId === safe.assignedDriverId || user.driverId === safe.departureDriverId);
      const sameVehicle = user.currentVehicleId && user.currentVehicleId === safe.departureVehicleId;
      this.setData({ route: safe, user,
        driverLabel: sameDriver ? (user.driverName || '本人') : (safe.assignedDriverId ? `司机 #${safe.assignedDriverId}` : '暂未分配'),
        vehicleLabel: sameVehicle && user.currentPlateNo ? user.currentPlateNo : (safe.departureVehicleId ? `车辆 #${safe.departureVehicleId}` : (user.currentPlateNo || '暂未绑定')),
        canDepart: hasPermission(user, 'route:depart'), canUnload: hasPermission(user, 'route:unload') });
    } catch (error) { this.setData({ error: error.message || '线路详情加载失败，请重试' }); }
    finally { this.setData({ loading: false }); }
  },
  depart() {
    if (this.data.busy || this.data.confirming || !this.data.canDepart) return;
    this.setData({ confirming: true });
    wx.showModal({
      title: '确认发车',
      content: `确认从${this.data.route?.loadingPlace || '装货地'}发往${this.data.route?.unloadingPlace || '卸货地'}？请核对当前车辆，发车后线路将进入运输中。`,
      success: async (res) => {
        if (!res.confirm) { this.setData({ confirming: false }); return; }
        this.setData({ busy: true, error: '' });
        try {
          await request({ url: `/routes/${this.data.id}/depart`, method: 'POST' });
          wx.showToast({ title: '已发车' });
          await this.load();
        } catch (error) { this.setData({ error: error.message || '发车失败，请重试' }); }
        finally { this.setData({ busy: false, confirming: false }); }
      },
      fail: () => this.setData({ confirming: false })
    });
  },
  weight() {
    if (!this.data.canUnload || this.data.busy) return;
    wx.navigateTo({ url: `/pages/weight/index?id=${this.data.id}` });
  }
});
