const { request } = require('../../utils/request');
const { loadStandardMet, netWeight, validateWeights } = require('../../utils/business');

Page({
  data: { id: null, route: {}, grossWeight: '', tareWeight: '', netWeight: '', standardText: '未计算', loading: false },
  onLoad(query) {
    this.setData({ id: query.id });
    this.load();
  },
  async load() {
    const route = await request({ url: `/routes/${this.data.id}` });
    this.setData({
      route,
      grossWeight: route.grossWeight || '',
      tareWeight: route.tareWeight || ''
    });
    this.recalculate();
  },
  onGross(e) { this.setData({ grossWeight: e.detail.value }); this.recalculate(); },
  onTare(e) { this.setData({ tareWeight: e.detail.value }); this.recalculate(); },
  recalculate() {
    const net = netWeight(this.data.grossWeight, this.data.tareWeight);
    const met = loadStandardMet(this.data.grossWeight, this.data.tareWeight, this.data.route.loadStandardThreshold);
    this.setData({
      netWeight: net,
      standardText: met === null ? '未计算' : (met ? '达标' : '不达标')
    });
  },
  async submit() {
    const validation = validateWeights(this.data.grossWeight, this.data.tareWeight);
    if (!validation.ok) {
      wx.showToast({ title: validation.message, icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    try {
      wx.showModal({
        title: '确认卸货',
        content: `${this.data.standardText}，仍可继续卸货。`,
        success: async (res) => {
          if (!res.confirm) return;
          await request({
            url: `/routes/${this.data.id}/unload`,
            method: 'POST',
            data: { grossWeight: this.data.grossWeight, tareWeight: this.data.tareWeight }
          });
          wx.navigateBack();
        }
      });
    } finally {
      this.setData({ loading: false });
    }
  }
});
