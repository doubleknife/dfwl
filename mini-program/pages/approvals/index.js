const { request } = require('../../utils/request');
const { hasPermission, hasAnyPermission, isDriver, isOutsourcedDriver } = require('../../utils/permissions');

const scopeByTab = {
  pending: 'TODO',
  mine: 'MINE',
  history: 'DONE',
  all: 'ALL'
};

Page({
  data: { user: null, tab: 'mine', approvals: [], visible: [], canCreate: false,
    canTodo: false, canHistory: false, error: '' },
  onShow() { this.load(); },
  switchTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab });
    this.loadApprovals();
  },
  async load() {
    try {
      const user = await request({ url: '/me' });
      const canCreate = hasPermission(user, 'approval:create') && !isOutsourcedDriver(user);
      const canTodo = !isDriver(user) && hasAnyPermission(user, ['approval:process', 'approval:return']);
      const canHistory = hasPermission(user, 'approval:history:view') || canTodo;
      const tab = canCreate ? 'mine' : canTodo ? 'pending' : canHistory ? 'history' : 'mine';
      this.setData({ user, canCreate, canTodo, canHistory, tab, error: '' });
      await this.loadApprovals();
    } catch (error) {
      this.setData({ error: error.message || '审批列表加载失败' });
    }
  },
  async loadApprovals() {
    const scope = scopeByTab[this.data.tab] || 'TODO';
    try {
      const page = await request({ url: `/approvals?scope=${scope}&pageNo=1&pageSize=100` });
      const approvals = page.records || [];
      this.setData({ approvals, visible: approvals, error: '' });
    } catch (error) {
      this.setData({ error: error.message || '审批列表加载失败', approvals: [], visible: [] });
    }
  },
  create() { wx.navigateTo({ url: '/pages/expense-apply/index' }); },
  open(e) {
    wx.navigateTo({ url: `/pages/approval-detail/index?id=${e.currentTarget.dataset.id}` });
  }
});
