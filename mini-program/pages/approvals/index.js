const { request, downloadAttachment } = require('../../utils/request');
const { hasPermission } = require('../../utils/permissions');
const { formatDateTime } = require('../../utils/dateTime');
const { chooseApprovalImages } = require('../../utils/approvalImages');

const labels = { PENDING: '待处理', APPROVED: '已通过', RETURNED_TO_APPLICANT: '已打回申请人', WITHDRAWN: '已撤回',
  EXPENSE: '费用申请', GENERAL: '普通审批', TIRE_REPLACEMENT: '换胎申请',
  APPROVE: '通过', RETURN_STEP: '打回指定步骤', RETURN_APPLICANT: '打回申请人', RESUBMIT: '修改并重新提交' };
const statusTone = (status) => status === 'APPROVED' ? 'success'
  : ['RETURNED_TO_APPLICANT', 'WITHDRAWN', 'REJECTED'].includes(status) ? 'danger' : 'warning';

Page({
  data: { scope: 'MINE', rows: [], detail: null, returnable: [], user: null, canTodo: false, loading: false,
    busy: false, uploading: false, actionImages: [], error: '', pageNo: 1, hasMore: false,
    comment: '', targetFlowNodeId: '', targetStepName: '' },
  onLoad(options) { this._requestedApprovalId = Number(options?.id || 0); },
  onShow() { this.bootstrap(); },
  async bootstrap() {
    this.setData({ loading: true, error: '' });
    try {
      const user = await request({ url: '/me' });
      if (user.driverType === 'OUTSOURCED' || !['PAGE_MINI_APPROVALS', 'BUTTON_MINI_EXPENSE_APPLY_SUBMIT', 'BUTTON_MINI_TIRE_REQUEST_SUBMIT', 'BUTTON_MINI_APPROVALS_APPROVE', 'BUTTON_MINI_APPROVALS_RETURN']
        .some((code) => hasPermission(user, code))) {
        this.setData({ error: '当前账号无审批权限', rows: [], user }); return;
      }
      this.setData({ user, canTodo: hasPermission(user, 'BUTTON_MINI_APPROVALS_APPROVE') || hasPermission(user, 'BUTTON_MINI_APPROVALS_RETURN') });
      if (!user.driverId && (hasPermission(user, 'BUTTON_MINI_APPROVALS_APPROVE') || hasPermission(user, 'BUTTON_MINI_APPROVALS_RETURN'))
        && !this._scopeChosen) this.setData({ scope: 'TODO' });
      await this.load();
      if (this._requestedApprovalId) {
        const id = this._requestedApprovalId;
        this._requestedApprovalId = 0;
        await this.open({ currentTarget: { dataset: { id } } });
      }
    } catch (error) { this.setData({ error: error.message || '审批中心加载失败' }); }
    finally { this.setData({ loading: false }); }
  },
  async load(next = 1) {
    try {
      const result = await request({ url: `/approvals?scope=${this.data.scope}&pageNo=${next}&pageSize=20` });
      const rows = (result.records || []).map((row) => ({
        ...row, businessLabel: labels[row.businessType] || row.templateName,
        statusLabel: labels[row.status] || row.status, statusTone: statusTone(row.status),
        createdLabel: formatDateTime(row.createdAt),
        handledLabel: formatDateTime(row.lastHandledAt),
        returnLabel: formatDateTime(row.lastReturnAt)
      }));
      this.setData({ rows: next === 1 ? rows : this.data.rows.concat(rows), pageNo: next,
        hasMore: next * 20 < Number(result.total || 0), error: '' });
    } catch (error) { this.setData({ error: error.message || '审批列表加载失败' }); }
  },
  changeScope(e) {
    const scope = e.currentTarget.dataset.scope;
    this._scopeChosen = true;
    this.setData({ scope, rows: [], detail: null, actionImages: [], pageNo: 1 }); this.load();
  },
  more() { if (this.data.hasMore && !this.data.loading) this.load(this.data.pageNo + 1); },
  async open(e) {
    if (Number(this.data.detail?.approval?.id) !== Number(e.currentTarget.dataset.id))
      this.setData({ actionImages: [], comment: '', targetFlowNodeId: '', targetStepName: '' });
    this.setData({ loading: true, error: '' });
    try {
      const result = await request({ url: `/approvals/${e.currentTarget.dataset.id}` });
      const approval = result.approval;
      const task = (result.tasks || []).find((entry) => entry.status === 'PENDING' && !entry.invalidated);
      const items = result.expenseItems || [];
      const latest = Math.max(0, ...items.map((entry) => Number(entry.submission_version_id)));
      const actions = (result.actions || []).map((entry) => ({
        ...entry, label: labels[entry.action_type] || '审批处理',
        operatedLabel: formatDateTime(entry.operated_at),
        evidence: (result.actionAttachments || []).filter((file) => Number(file.actionId) === Number(entry.id))
          .map((file) => ({ id: file.attachmentId, path: '', error: '' }))
      }));
      const returns = actions.filter((entry) => entry.action_type === 'RETURN_APPLICANT');
      const returnAction = returns[returns.length - 1] || null;
      const latestSubmission = Math.max(0, ...(result.submissions || []).map((entry) => Number(entry.id)));
      const evidence = (result.attachments || []).filter((entry) => Number(entry.submission_version_id) === latestSubmission)
        .map((entry) => ({ id: entry.attachmentId, path: '', error: '' }));
      const earlierEvidence = (result.submissions || []).filter((entry) => Number(entry.id) !== latestSubmission)
        .map((version) => ({ id: version.id, number: version.version_no,
          images: (result.attachments || []).filter((file) => Number(file.submission_version_id) === Number(version.id))
            .map((file) => ({ id: file.attachmentId, path: '', error: '' })) }))
        .filter((version) => version.images.length);
      this.setData({ detail: { ...result, statusLabel: labels[approval.status] || approval.status,
        statusTone: statusTone(approval.status),
        businessLabel: labels[approval.business_type] || approval.template_name, task,
        canProcess: !!task && (result.canProcess || result.canReturn),
        canApprove: !!task && !!result.canProcess
          && hasPermission(this.data.user, 'BUTTON_MINI_APPROVALS_APPROVE'),
        canReturn: !!task && !!result.canReturn
          && hasPermission(this.data.user, 'BUTTON_MINI_APPROVALS_RETURN'),
        canResubmitExpense: approval.status === 'RETURNED_TO_APPLICANT'
          && approval.business_type === 'EXPENSE'
          && Number(approval.applicant_user_id) === Number(this.data.user.id)
          && items.filter((entry) => Number(entry.submission_version_id) === latest).length === 1,
        canResubmitTire: approval.status === 'RETURNED_TO_APPLICANT'
          && approval.business_type === 'TIRE_REPLACEMENT'
          && Number(approval.applicant_user_id) === Number(this.data.user.id),
        items: items.filter((entry) => Number(entry.submission_version_id) === latest),
        tireRequests: result.tireRequests || [], actions: actions.slice().reverse(), returnAction,
        evidence, earlierEvidence },
        returnable: result.returnableSteps || [] });
      for (let index = 0; index < evidence.length; index++) {
        try {
          const path = await downloadAttachment(evidence[index].id);
          this.setData({ [`detail.evidence[${index}].path`]: path });
        } catch (cause) {
          this.setData({ [`detail.evidence[${index}].error`]: '图片加载失败，请返回重试' });
        }
      }
      for (let versionIndex = 0; versionIndex < earlierEvidence.length; versionIndex++) {
        for (let index = 0; index < earlierEvidence[versionIndex].images.length; index++) {
          try {
            const path = await downloadAttachment(earlierEvidence[versionIndex].images[index].id);
            this.setData({ [`detail.earlierEvidence[${versionIndex}].images[${index}].path`]: path });
          } catch (cause) {
            this.setData({ [`detail.earlierEvidence[${versionIndex}].images[${index}].error`]: '图片加载失败' });
          }
        }
      }
      for (let actionIndex = 0; actionIndex < this.data.detail.actions.length; actionIndex++) {
        for (let index = 0; index < this.data.detail.actions[actionIndex].evidence.length; index++) {
          try {
            const path = await downloadAttachment(this.data.detail.actions[actionIndex].evidence[index].id);
            this.setData({ [`detail.actions[${actionIndex}].evidence[${index}].path`]: path });
          } catch (cause) {
            this.setData({ [`detail.actions[${actionIndex}].evidence[${index}].error`]: '图片加载失败' });
          }
        }
      }
    } catch (error) { this.setData({ error: error.message || '审批详情加载失败' }); }
    finally { this.setData({ loading: false }); }
  },
  back() { this.setData({ detail: null, actionImages: [], error: '' }); this.load(); },
  reapply() {
    wx.navigateTo({ url: `/pages/expense-apply/index?resubmitId=${this.data.detail.approval.id}` });
  },
  reapplyTire() {
    wx.navigateTo({ url: `/pages/tire-request/index?resubmitId=${this.data.detail.approval.id}` });
  },
  previewEvidence(e) {
    const selected = this.data.detail?.evidence?.[Number(e.currentTarget.dataset.index)];
    const urls = (this.data.detail?.evidence || []).map((entry) => entry.path).filter(Boolean);
    if (selected?.path) wx.previewImage({ current: selected.path, urls });
  },
  previewEarlierEvidence(e) {
    const images = this.data.detail?.earlierEvidence?.[Number(e.currentTarget.dataset.version)]?.images || [];
    const image = images[Number(e.currentTarget.dataset.index)];
    if (image?.path) wx.previewImage({ current: image.path, urls: images.map((entry) => entry.path).filter(Boolean) });
  },
  previewActionEvidence(e) {
    const images = this.data.detail?.actions?.[Number(e.currentTarget.dataset.action)]?.evidence || [];
    const image = images[Number(e.currentTarget.dataset.index)];
    if (image?.path) wx.previewImage({ current: image.path, urls: images.map((entry) => entry.path).filter(Boolean) });
  },
  async addActionImages() {
    if (this.data.busy || this.data.uploading) return;
    if (this.data.actionImages.length >= 20) { this.setData({ error: '一次最多选择 20 张图片' }); return; }
    this.setData({ uploading: true, error: '' });
    try {
      await chooseApprovalImages(this.data.actionImages, this.data.user.id, 'APPROVAL_ACTION',
        (actionImages) => this.setData({ actionImages }));
    } catch (error) { this.setData({ error: error.message || '图片上传失败，请重新选择' }); }
    finally { this.setData({ uploading: false }); }
  },
  removeActionImage(e) {
    if (this.data.busy || this.data.uploading) return;
    this.setData({ actionImages: this.data.actionImages.filter((_, index) => index !== Number(e.currentTarget.dataset.index)) });
  },
  previewActionDraft(e) {
    const images = this.data.actionImages;
    wx.previewImage({ current: images[Number(e.currentTarget.dataset.index)].path,
      urls: images.map((image) => image.path) });
  },
  onComment(e) { this.setData({ comment: e.detail.value }); },
  onTarget(e) {
    const step = this.data.returnable[Number(e.detail.index)];
    this.setData({ targetFlowNodeId: step ? String(step.flowNodeId) : '',
      targetStepName: step?.stepName || '' });
  },
  async act(e) {
    const kind = e.currentTarget.dataset.kind;
    if (this.data.busy || this.data.uploading || !this.data.detail?.canProcess) return;
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
          targetFlowNodeId: kind === 'RETURN_STEP' ? Number(this.data.targetFlowNodeId) : null,
          attachmentIds: this.data.actionImages.map((image) => image.id) } });
      this.setData({ comment: '', targetFlowNodeId: '', targetStepName: '', actionImages: [] });
      if (kind === 'APPROVE') {
        this.setData({ detail: null });
        await this.load(1);
      } else {
        await this.open({ currentTarget: { dataset: { id } } });
        await this.load();
      }
    } catch (error) { this.setData({ error: error.message || '审批操作失败，请重试' }); }
    finally { this.setData({ busy: false }); }
  }
});
