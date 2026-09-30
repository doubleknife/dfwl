const { request, downloadAttachment } = require('../../utils/request');
const { hasPermission } = require('../../utils/permissions');

const labels = { PENDING: '待处理', APPROVED: '已通过', RETURNED_TO_APPLICANT: '已打回申请人',
  EXPENSE: '费用申请', GENERAL: '普通审批', TIRE_REPLACEMENT: '换胎申请',
  APPROVE: '通过', RETURN_STEP: '打回指定步骤', RETURN_APPLICANT: '打回申请人' };

Page({
  data: { scope: 'MINE', rows: [], detail: null, returnable: [], user: null, canTodo: false, loading: false,
    busy: false, error: '', pageNo: 1, hasMore: false, comment: '', targetFlowNodeId: '' },
  onShow() { this.bootstrap(); },
  async bootstrap() {
    this.setData({ loading: true, error: '' });
    try {
      const user = await request({ url: '/me' });
      if (user.driverType === 'OUTSOURCED' || !['approval:create', 'approval:process', 'approval:return']
        .some((code) => hasPermission(user, code))) {
        this.setData({ error: '当前账号无审批权限', rows: [], user }); return;
      }
      this.setData({ user, canTodo: hasPermission(user, 'approval:process') || hasPermission(user, 'approval:return') });
      await this.load();
    } catch (error) { this.setData({ error: error.message || '审批中心加载失败' }); }
    finally { this.setData({ loading: false }); }
  },
  async load(next = 1) {
    try {
      const result = await request({ url: `/approvals?scope=${this.data.scope}&pageNo=${next}&pageSize=20` });
      const rows = (result.records || []).map((row) => ({
        ...row, businessLabel: labels[row.businessType] || row.templateName,
        statusLabel: labels[row.status] || row.status
      }));
      this.setData({ rows: next === 1 ? rows : this.data.rows.concat(rows), pageNo: next,
        hasMore: next * 20 < Number(result.total || 0), error: '' });
    } catch (error) { this.setData({ error: error.message || '审批列表加载失败' }); }
  },
  changeScope(e) {
    const scope = e.currentTarget.dataset.scope;
    this.setData({ scope, rows: [], detail: null, pageNo: 1 }); this.load();
  },
  more() { if (this.data.hasMore && !this.data.loading) this.load(this.data.pageNo + 1); },
  async open(e) {
    this.setData({ loading: true, error: '' });
    try {
      const result = await request({ url: `/approvals/${e.currentTarget.dataset.id}` });
      const approval = result.approval;
      const task = (result.tasks || []).find((entry) => entry.status === 'PENDING' && !entry.invalidated);
      const items = result.expenseItems || [];
      const latest = Math.max(0, ...items.map((entry) => Number(entry.submission_version_id)));
      const actions = (result.actions || []).map((entry) => ({
        ...entry, label: labels[entry.action_type] || '审批处理'
      }));
      const latestSubmission = Math.max(0, ...(result.submissions || []).map((entry) => Number(entry.id)));
      const evidence = (result.attachments || []).filter((entry) => Number(entry.submission_version_id) === latestSubmission)
        .map((entry) => ({ id: entry.attachmentId, path: '', error: '' }));
      this.setData({ detail: { ...result, statusLabel: labels[approval.status] || approval.status,
        businessLabel: labels[approval.business_type] || approval.template_name, task,
        canProcess: task && Number(task.approver_user_id) === Number(this.data.user.id),
        canApprove: task && Number(task.approver_user_id) === Number(this.data.user.id)
          && hasPermission(this.data.user, 'approval:process'),
        canReturn: task && Number(task.approver_user_id) === Number(this.data.user.id)
          && hasPermission(this.data.user, 'approval:return'),
        canResubmitExpense: approval.status === 'RETURNED_TO_APPLICANT'
          && approval.business_type === 'EXPENSE'
          && Number(approval.applicant_user_id) === Number(this.data.user.id)
          && items.filter((entry) => Number(entry.submission_version_id) === latest).length === 1,
        items: items.filter((entry) => Number(entry.submission_version_id) === latest), actions, evidence },
        returnable: result.returnableSteps || [] });
      for (let index = 0; index < evidence.length; index++) {
        try {
          const path = await downloadAttachment(evidence[index].id);
          this.setData({ [`detail.evidence[${index}].path`]: path });
        } catch (cause) {
          this.setData({ [`detail.evidence[${index}].error`]: '图片加载失败，请返回重试' });
        }
      }
    } catch (error) { this.setData({ error: error.message || '审批详情加载失败' }); }
    finally { this.setData({ loading: false }); }
  },
  back() { this.setData({ detail: null, error: '' }); this.load(); },
  reapply() {
    wx.navigateTo({ url: `/pages/expense-apply/index?resubmitId=${this.data.detail.approval.id}` });
  },
  previewEvidence(e) {
    const selected = this.data.detail?.evidence?.[Number(e.currentTarget.dataset.index)];
    const urls = (this.data.detail?.evidence || []).map((entry) => entry.path).filter(Boolean);
    if (selected?.path) wx.previewImage({ current: selected.path, urls });
  },
  onComment(e) { this.setData({ comment: e.detail.value }); },
  onTarget(e) {
    const step = this.data.returnable[Number(e.detail.value)];
    this.setData({ targetFlowNodeId: step ? String(step.flowNodeId) : '' });
  },
  async act(e) {
    const kind = e.currentTarget.dataset.kind;
    if (this.data.busy || !this.data.detail?.canProcess) return;
    if (kind !== 'APPROVE' && !this.data.comment.trim()) {
      this.setData({ error: '请先填写打回原因' }); return;
    }
    if (kind === 'RETURN_STEP' && !this.data.targetFlowNodeId) {
      this.setData({ error: '请选择要返回的步骤' }); return;
    }
    const confirm = await new Promise((resolve) => wx.showModal({
      title: labels[kind], content: '确认处理这条审批？操作会记录在审批时间线中。',
      success: (result) => resolve(result.confirm), fail: () => resolve(false)
    }));
    if (!confirm) return;
    this.setData({ busy: true, error: '' });
    const id = this.data.detail.approval.id;
    const endpoint = { APPROVE: 'approve', RETURN_STEP: 'return-step', RETURN_APPLICANT: 'return-applicant' }[kind];
    try {
      await request({ url: `/approvals/${id}/${endpoint}`, method: 'POST',
        data: { comment: this.data.comment.trim(), expectedVersion: this.data.detail.approval.version,
          targetFlowNodeId: kind === 'RETURN_STEP' ? Number(this.data.targetFlowNodeId) : null } });
      this.setData({ comment: '', targetFlowNodeId: '' });
      await this.open({ currentTarget: { dataset: { id } } });
      await this.load();
    } catch (error) { this.setData({ error: error.message || '审批操作失败，请重试' }); }
    finally { this.setData({ busy: false }); }
  }
});
