const { request } = require('../../utils/request');
const { sanitizeRoute } = require('../../utils/business');

Page({
  data: { id: null, route: {} },
  onLoad(query) {
    this.setData({ id: query.id });
    this.load();
  },
  async load() {
    const route = await request({ url: `/routes/${this.data.id}` });
    this.setData({ route: sanitizeRoute(route) });
  },
  async depart() {
    wx.showModal({
      title: '确认发车',
      content: '确认车辆、司机、车挂信息无误后发车。',
      success: async (res) => {
        if (!res.confirm) return;
        await request({ url: `/routes/${this.data.id}/depart`, method: 'POST' });
        wx.showToast({ title: '已发车' });
        this.load();
      }
    });
  },
  weight() {
    wx.navigateTo({ url: `/pages/weight/index?id=${this.data.id}` });
  }
});
