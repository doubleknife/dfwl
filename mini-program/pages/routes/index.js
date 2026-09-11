const { request } = require('../../utils/request');
const { sanitizeRoute } = require('../../utils/business');

Page({
  data: { records: [], status: '', statuses: ['全部', 'UNPUBLISHED', 'PUBLISHED', 'IN_TRANSIT', 'COMPLETED', 'VOIDED', 'CANCELLED'] },
  onShow() { this.load(); },
  onStatus(e) {
    const value = this.data.statuses[e.detail.value];
    this.setData({ status: value === '全部' ? '' : value });
    this.load();
  },
  async load() {
    const data = await request({ url: '/routes?pageNo=1&pageSize=50' });
    const records = (data.records || []).map(sanitizeRoute)
      .filter((item) => !this.data.status || item.status === this.data.status);
    this.setData({ records });
  },
  open(e) {
    wx.navigateTo({ url: `/pages/route-detail/index?id=${e.currentTarget.dataset.id}` });
  }
});
