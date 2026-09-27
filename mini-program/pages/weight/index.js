const { request } = require('../../utils/request');
const { loadStandardMet, netWeight, validateWeights, sanitizeRoute } = require('../../utils/business');
const { hasPermission } = require('../../utils/permissions');
const { routePresentation } = require('../../utils/homePresentation');

Page({
  data: {
    id: null, route: null, grossWeight: '', tareWeight: '', netWeight: '',
    standardText: '填写重量后计算', loading: false, confirming: false, busy: false,
    canUnload: false, fieldError: '', error: '', resultUnknown: false
  },
  onLoad(query) {
    this.setData({ id: query.id });
    this.load();
  },
  async load() {
    if (!this.data.id || this.data.busy) return;
    this.setData({ loading: true, error: '' });
    try {
      const [route, user] = await Promise.all([
        request({ url: `/routes/${this.data.id}` }), request({ url: '/me' })
      ]);
      const safe = routePresentation(sanitizeRoute(route));
      this.setData({
        route: safe,
        canUnload: hasPermission(user, 'route:unload'),
        grossWeight: this._hasInput ? this.data.grossWeight : (safe.grossWeight || ''),
        tareWeight: this._hasInput ? this.data.tareWeight : (safe.tareWeight || ''),
        resultUnknown: false,
        fieldError: ''
      });
      this.recalculate();
    } catch (error) {
      this.setData({ error: error.statusCode === 403
        ? '当前账号不能查看或卸货这条线路，请联系调度'
        : (error.message || '线路加载失败，请检查网络后重试') });
    } finally { this.setData({ loading: false }); }
  },
  onGross(e) {
    this._hasInput = true;
    this.setData({ grossWeight: e.detail.value, fieldError: '' });
    this.recalculate();
  },
  onTare(e) {
    this._hasInput = true;
    this.setData({ tareWeight: e.detail.value, fieldError: '' });
    this.recalculate();
  },
  recalculate() {
    const validation = validateWeights(this.data.grossWeight, this.data.tareWeight);
    if (!validation.ok) {
      this.setData({ netWeight: '', standardText: '填写有效重量后计算' });
      return;
    }
    const net = netWeight(this.data.grossWeight, this.data.tareWeight);
    const met = loadStandardMet(this.data.grossWeight, this.data.tareWeight, this.data.route?.loadStandardThreshold);
    this.setData({ netWeight: net,
      standardText: met === null ? '装载标准将在卸货后确认' : (met ? '已达标' : '未达标，仍可卸货') });
  },
  submit() {
    if (this.data.busy || this.data.confirming || this.data.loading || this.data.resultUnknown
        || this.data.error || !this.data.canUnload || this.data.route?.status !== 'IN_TRANSIT') return;
    const validation = validateWeights(this.data.grossWeight, this.data.tareWeight);
    if (!validation.ok) {
      this.setData({ fieldError: validation.message });
      return;
    }
    const { grossWeight, tareWeight, netWeight, standardText } = this.data;
    this.setData({ confirming: true, fieldError: '' });
    wx.showModal({
      title: '确认重量并卸货',
      content: `毛重 ${Number(grossWeight).toFixed(3)} 吨，皮重 ${Number(tareWeight).toFixed(3)} 吨，实际载重 ${netWeight} 吨。${standardText}。卸货后司机不能自行修改重量，如需纠正请联系有权限的管理人员。`,
      success: async (res) => {
        if (!res.confirm) { this.setData({ confirming: false }); return; }
        this.setData({ busy: true, error: '' });
        try {
          const completed = await request({
            url: `/routes/${this.data.id}/unload`, method: 'POST',
            data: { grossWeight, tareWeight }
          });
          this.setData({ route: routePresentation(sanitizeRoute(completed)) });
          wx.showToast({ title: '卸货已完成' });
          wx.navigateBack();
        } catch (error) {
          this.setData({ resultUnknown: true,
            error: '卸货结果未确认。请先重新加载线路状态，确认尚未完成后再提交，勿重复点击。' });
        } finally { this.setData({ busy: false, confirming: false }); }
      },
      fail: () => this.setData({ confirming: false })
    });
  }
});
