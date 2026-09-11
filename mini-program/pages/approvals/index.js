const { request } = require('../../utils/request');

Page({
  data: { user: null, tab: 'pending', approvals: [], visible: [] },
  onShow() { this.load(); },
  switchTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab });
    this.applyFilter();
  },
  async load() {
    const [user, page] = await Promise.all([
      request({ url: '/me' }),
      request({ url: '/approvals?pageNo=1&pageSize=100' })
    ]);
    this.setData({ user, approvals: page.records || [] });
    this.applyFilter();
  },
  applyFilter() {
    const userId = this.data.user && this.data.user.id;
    let visible = this.data.approvals;
    if (this.data.tab === 'mine') visible = visible.filter((item) => item.applicantUserId === userId);
    if (this.data.tab === 'pending') visible = visible.filter((item) => item.status === 'PENDING');
    if (this.data.tab === 'history') visible = visible.filter((item) => item.status !== 'PENDING');
    this.setData({ visible });
  },
  open(e) {
    wx.navigateTo({ url: `/pages/approval-detail/index?id=${e.currentTarget.dataset.id}` });
  }
});
