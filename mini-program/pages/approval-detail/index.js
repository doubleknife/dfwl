const { request, uploadFile } = require('../../utils/request');

Page({
  data: { id: null, detail: null, comment: '', targetNodeOrder: '', imagePath: '', attachmentIds: [], uploadStatus: '', loading: false },
  onLoad(query) {
    this.setData({ id: query.id });
    this.load();
  },
  async load() {
    const detail = await request({ url: `/approvals/${this.data.id}` });
    this.setData({ detail });
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
    await this.process(`/approvals/${this.data.id}/approve`, { comment: this.data.comment });
  },
  async returnApplicant() {
    await this.process(`/approvals/${this.data.id}/return-applicant`, { comment: this.data.comment });
  },
  async returnNode() {
    await this.process(`/approvals/${this.data.id}/return-node`, {
      comment: this.data.comment,
      targetNodeOrder: this.data.targetNodeOrder ? Number(this.data.targetNodeOrder) : null
    });
  },
  async process(url, data) {
    this.setData({ loading: true });
    try {
      const attachmentIds = this.data.imagePath ? await this.uploadActionImage() : [];
      await request({ url, method: 'POST', data: { ...data, attachmentIds } });
      wx.showToast({ title: '已处理' });
      this.setData({ imagePath: '', attachmentIds: [], uploadStatus: '', comment: '', targetNodeOrder: '' });
      await this.load();
    } finally {
      this.setData({ loading: false });
    }
  }
});
