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
    const version = this._loadVersion = (this._loadVersion || 0) + 1;
    this.setData({ loading: true, error: '' });
    try {
      const [route, user] = await Promise.all([request({ url: `/routes/${this.data.id}` }), request({ url: '/me' })]);
      if (version !== this._loadVersion) return;
      const safe = routePresentation(sanitizeRoute(route));
      const sameDriver = user.driverId && user.driverId === safe.assignedDriverId;
      const sameVehicle = user.currentVehicleId && user.currentVehicleId === safe.departureVehicleId;
      this.setData({ route: safe, user,
        driverLabel: sameDriver ? (user.driverName || '本人') : (safe.assignedDriverId ? `司机 #${safe.assignedDriverId}` : '暂未分配'),
        vehicleLabel: safe.currentOutsourcedPlateNo || (sameVehicle && user.currentPlateNo ? user.currentPlateNo : (safe.departureVehicleId ? `车辆 #${safe.departureVehicleId}` : (user.currentPlateNo || '暂未绑定'))),
        canDepart: !!sameDriver && hasPermission(user, 'route:depart'),
        canUnload: !!sameDriver && hasPermission(user, 'route:unload') });
    } catch (error) {
      if (version === this._loadVersion) this.setData({ error: error.statusCode === 403 ? '当前账号不能查看这条线路，请联系调度' : (error.message || '线路详情加载失败，请重试') });
    } finally { if (version === this._loadVersion) this.setData({ loading: false }); }
  },
  depart() {
    if (this.data.busy || this.data.confirming || this.data.loading || this.data.error || !this.data.canDepart || !['PUBLISHED', 'TRANSFER_PENDING'].includes(this.data.route?.status)) return;
    this.setData({ confirming: true });
    wx.showModal({
      title: '确认发车',
      content: `请确认车辆 ${this.data.vehicleLabel}，从${this.data.route.loadingPlace || '装货地'}发往${this.data.route.unloadingPlace || '卸货地'}。确认后线路进入运输中。`,
      success: async (res) => {
        if (!res.confirm) { this.setData({ confirming: false }); return; }
        this.setData({ busy: true, error: '' });
        try {
          const departed = await request({ url: `/routes/${this.data.id}/depart`, method: 'POST' });
          this.setData({ route: routePresentation(sanitizeRoute(departed)) });
          wx.showToast({ title: '已发车' });
          await this.load();
        } catch (error) { this.setData({ error: '发车结果未确认，请先重新加载线路状态，勿重复提交。' }); }
        finally { this.setData({ busy: false, confirming: false }); }
      },
      fail: () => this.setData({ confirming: false })
    });
  },
  weight() {
    if (!this.data.canUnload || this.data.busy || this.data.loading || this.data.error || this.data.route?.status !== 'IN_TRANSIT') return;
    wx.navigateTo({ url: `/pages/weight/index?id=${this.data.id}` });
  }
});
