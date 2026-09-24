const { request, uploadFile, downloadAttachment } = require('../../utils/request');
const { hasPermission, isDriver } = require('../../utils/permissions');

Page({
  data: { id: null, detail: null, comment: '', targetNodeOrder: '', imagePath: '',
    attachmentIds: [], uploadStatus: '', loading: false, canApprove: false, canReturn: false, error: '' },
  onLoad(query) {
    this.setData({ id: query.id });
    this.load();
  },
  async load() {
    try {
      const [user, detail] = await Promise.all([
        request({ url: '/me' }), request({ url: `/approvals/${this.data.id}` })
      ]);
      const myPendingTask = (detail.tasks || []).some((task) =>
        task.status === 'PENDING' && !task.invalidated && Number(task.approverUserId) === Number(user.id));
      const canProcess = !isDriver(user) && myPendingTask;
      this.setData({ detail, canApprove: canProcess && hasPermission(user, 'approval:process'),
        canReturn: canProcess && hasPermission(user, 'approval:return'), error: '' });
    } catch (error) {
      this.setData({ error: error.message || '审批详情加载失败' });
    }
  },
  async previewFile(e) {
    const id = Number(e.currentTarget.dataset.id);
    if (!id) return;
    try {
      const path = await downloadAttachment(id);
      wx.previewImage({ current: path, urls: [path] });
    } catch (error) {
      this.setData({ error: error.message || '图片预览失败' });
    }
  },
  onComment(e) { this.setData({ comment: e.detail.value }); },
  onTarget(e) { this.setData({ targetNodeOrder: e.detail.value }); },
  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => this.setData({ imagePath: res.tempFiles[0].tempFilePath, uploadStatus: '已选择图片' })
    });
  },
  async uploadActionImage() {
    if (!this.data.imagePath) return [];
    const user = getApp().globalData.user || wx.getStorageSync('user') || {};
    if (!user.id) throw new Error('当前账号信息缺失');
    this.setData({ uploadStatus: '上传中' });
    const file = await uploadFile({
      url: '/attachments',
      filePath: this.data.imagePath,
      formData: { ownerType: 'APPROVAL_UPLOAD', ownerId: user.id, purpose: 'APPROVAL_ACTION' }
    });
    const attachmentIds = [file.id];
    this.setData({ attachmentIds, uploadStatus: '上传完成' });
    return attachmentIds;
  },
  async approve() {
    if (!this.data.canApprove) return;
    await this.process(`/approvals/${this.data.id}/approve`, { comment: this.data.comment });
  },
  async returnApplicant() {
    if (!this.data.canReturn) return;
    await this.process(`/approvals/${this.data.id}/return-applicant`, { comment: this.data.comment });
  },
  async returnNode() {
    if (!this.data.canReturn) return;
    await this.process(`/approvals/${this.data.id}/return-node`, {
      comment: this.data.comment,
      targetNodeOrder: this.data.targetNodeOrder ? Number(this.data.targetNodeOrder) : null
    });
  },
  async process(url, data) {
    if (this.data.loading) return;
    this.setData({ loading: true });
    try {
      const attachmentIds = this.data.imagePath ? await this.uploadActionImage() : [];
      await request({ url, method: 'POST', data: { ...data, attachmentIds } });
      wx.showToast({ title: '已处理' });
      this.setData({ imagePath: '', attachmentIds: [], uploadStatus: '', comment: '', targetNodeOrder: '' });
      await this.load();
    } catch (error) {
      this.setData({ error: error.message || '审批处理失败' });
    } finally {
      this.setData({ loading: false });
    }
  }
});
