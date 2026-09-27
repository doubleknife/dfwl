const { request } = require('../../utils/request');
const { hasPermission, hasAnyPermission, isDriver, isOutsourcedDriver } = require('../../utils/permissions');
const { statusLabel, typeLabel } = require('../../utils/approvalPresentation');

const scopeByTab = { mine: 'MINE', pending: 'TODO', history: 'DONE' };

Page({
  data: { user: null, tab: 'mine', initialized: false, approvals: [], canMine: false, canCreate: false,
    canTodo: false, canHistory: false, loading: false, error: '' },
  onShow() { this.load(); },
  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (tab === this.data.tab) return;
    this.setData({ tab });
    this.loadApprovals();
  },
  async load() {
    this.setData({ loading: true, error: '' });
    try {
      const user = await request({ url: '/me' });
      const canTodo = !isDriver(user) && hasAnyPermission(user, ['approval:process', 'approval:return']);
      const canHistory = hasPermission(user, 'approval:history:view') || canTodo;
      const permittedToCreate = hasPermission(user, 'approval:create') && !isOutsourcedDriver(user);
      const templates = permittedToCreate
        ? await request({ url: '/approval-templates/available' }).catch(() => []) : [];
      const canCreate = (Array.isArray(templates) ? templates : templates.records || []).some((item) =>
        item.category === 'EXPENSE' && (item.allowedInputModes || []).includes('SINGLE'));
      const tab = this.data.initialized ? this.data.tab : (permittedToCreate ? 'mine' : canTodo ? 'pending' : canHistory ? 'history' : 'mine');
      this.setData({ user, canMine: permittedToCreate, canCreate, canTodo, canHistory, tab, initialized: true });
      await this.loadApprovals();
    } catch (error) {
      this.setData({ error: error.message || '审批列表加载失败，请重试', approvals: [] });
    } finally {
      this.setData({ loading: false });
    }
  },
  async loadApprovals() {
    this.setData({ loading: true, error: '' });
    try {
      const page = await request({ url: `/approvals?scope=${scopeByTab[this.data.tab]}&pageNo=1&pageSize=100` });
      this.setData({ approvals: (page.records || []).map((item) => ({ ...item,
        statusLabel: statusLabel(item.status), typeLabel: item.flowName || typeLabel(item.businessType) })) });
    } catch (error) {
      this.setData({ error: error.message || '审批列表加载失败，请重试', approvals: [] });
    } finally {
      this.setData({ loading: false });
    }
  },
  create() { wx.navigateTo({ url: '/pages/expense-apply/index' }); },
  open(e) { wx.navigateTo({ url: `/pages/approval-detail/index?id=${e.currentTarget.dataset.id}` }); }
});
