const { request } = require('../../utils/request');
const { sanitizeRoute } = require('../../utils/business');
const { hasPermission } = require('../../utils/permissions');
const { routePresentation } = require('../../utils/homePresentation');
const { formatDateTime } = require('../../utils/dateTime');

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
      const [route, user] = await Promise.all([request({ url: `/driver/transport-tasks/${this.data.id}` }), request({ url: '/me' })]);
      if (version !== this._loadVersion) return;
      const safe = routePresentation(sanitizeRoute(route));
      const sameDriver = !!safe.currentParticipant;
      const needsVehicle = sameDriver && user.driverType === 'INTERNAL'
        && ['PUBLISHED', 'TRANSFER_PENDING'].includes(safe.status) && user.currentVehicleId == null;
      this.setData({ route: {
          ...safe,
          departureLabel: formatDateTime(safe.departureTime) || '未发车',
          unloadLabel: formatDateTime(safe.unloadTime) || '未卸货',
          nextAction: !sameDriver && safe.status === 'TRANSFER_PENDING'
            ? '等待接手司机发车' : safe.nextAction
        }, user,
        driverLabel: safe.currentDriverName || (sameDriver ? user.driverName || '本人' : '司机姓名未提供'),
        vehicleLabel: needsVehicle ? '暂未绑定' : safe.currentPlateNo || user.currentPlateNo || '暂未绑定',
        canDepart: !!sameDriver && hasPermission(user, 'BUTTON_MINI_ROUTES_DEPART'),
        canUnload: !!sameDriver && hasPermission(user, 'BUTTON_MINI_ROUTES_UNLOAD'),
        error: needsVehicle ? '当前未绑定公司车辆，请联系调度处理；绑定完成后重新加载再发车。' : '' });
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
          this._departKey = this._departKey || `depart-${this.data.id}-${Date.now()}`;
          const departed = await request({ url: `/transport-tasks/${this.data.id}/depart`, method: 'POST',
            data: { expectedVersion: this.data.route.version, requestKey: this._departKey }, silentError: true });
          this._departKey = null;
          const updated = routePresentation(sanitizeRoute(departed));
          this.setData({ route: { ...updated,
            departureLabel: formatDateTime(updated.departureTime) || '未发车',
            unloadLabel: formatDateTime(updated.unloadTime) || '未卸货' } });
          wx.showToast({ title: '已发车' });
          await this.load();
        } catch (error) {
          const rejected = error.code === 'ROUTE_003' || error.code === 'ROUTE_002' || error.statusCode === 403;
          if (rejected) this._departKey = null;
          const message = error.code === 'ROUTE_003'
            ? '当前司机或车辆不满足发车条件，请联系调度核对车辆绑定和证照，处理后重新加载。'
            : error.code === 'ROUTE_002' ? '任务状态已变化，请重新加载后确认是否还需发车。'
              : error.statusCode === 403 ? '当前账号不能为这条任务发车，请联系调度。'
                : '发车结果未确认，请先重新加载线路状态，勿重复提交。';
          this.setData({ error: message });
        }
        finally { this.setData({ busy: false, confirming: false }); }
      },
      fail: () => this.setData({ confirming: false })
    });
  },
  weight() {
    if (!this.data.canUnload || this.data.busy || this.data.loading || this.data.error || this.data.route?.status !== 'IN_TRANSIT') return;
    wx.navigateTo({ url: `/pages/weight/index?id=${this.data.id}` });
  },
  viewTicket() {
    if (this.data.loading || this.data.error || this.data.route?.status !== 'COMPLETED') return;
    wx.navigateTo({ url: `/pages/weight/index?id=${this.data.id}` });
  }
});
