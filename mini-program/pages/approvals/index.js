const { request } = require('../../utils/request');

const scopeByTab = {
  pending: 'TODO',
  mine: 'MINE',
  history: 'DONE',
  all: 'ALL'
};

Page({
  data: { user: null, tab: 'pending', approvals: [], visible: [] },
  onShow() { this.load(); },
  switchTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab });
    this.loadApprovals();
  },
  async load() {
    const user = await request({ url: '/me' });
    this.setData({ user });
    await this.loadApprovals();
  },
  async loadApprovals() {
    const scope = scopeByTab[this.data.tab] || 'TODO';
    const page = await request({ url: `/approvals?scope=${scope}&pageNo=1&pageSize=100` });
    const approvals = page.records || [];
    this.setData({ approvals, visible: approvals });
  },
  open(e) {
    wx.navigateTo({ url: `/pages/approval-detail/index?id=${e.currentTarget.dataset.id}` });
  }
});
